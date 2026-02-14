package com.glycemicgpt.mobile.ble.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.glycemicgpt.mobile.ble.auth.JpakeAuthenticator
import com.glycemicgpt.mobile.ble.auth.TandemAuthenticator
import com.glycemicgpt.mobile.ble.protocol.PacketAssembler
import com.glycemicgpt.mobile.ble.protocol.Packetize
import com.glycemicgpt.mobile.ble.protocol.TandemProtocol
import com.glycemicgpt.mobile.data.local.PumpCredentialStore
import com.glycemicgpt.mobile.domain.model.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the BLE connection lifecycle to a Tandem pump.
 *
 * Responsibilities:
 * - Connect to a pump by address
 * - Negotiate MTU and enable GATT notifications (serialized via operation queue)
 * - Run the authentication handshake
 * - Auto-reconnect with exponential backoff
 * - Expose connection state as a Flow
 */
@Singleton
class BleConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authenticator: TandemAuthenticator,
    private val jpakeAuthenticator: JpakeAuthenticator,
    private val credentialStore: PumpCredentialStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val gattLock = Any()
    private val txId = AtomicInteger(0)
    private val assemblers = ConcurrentHashMap<UUID, PacketAssembler>()

    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    @Volatile
    private var autoReconnect = true

    // GATT operation queue -- only one GATT write at a time
    private val operationQueue = ConcurrentLinkedQueue<GattOperation>()
    private val operationInFlight = AtomicBoolean(false)

    // Pending notification subscriptions to serialize after service discovery
    private var pendingNotificationUuids = listOf<UUID>()

    // Pending pairing code provided by the user during initial pairing
    @Volatile
    private var pendingPairingCode: String? = null

    // Pending status read requests: txId -> deferred response cargo
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<ByteArray>>()

    private sealed class GattOperation {
        data class WriteCharacteristic(
            val characteristicUuid: UUID,
            val data: ByteArray,
        ) : GattOperation()

        data class WriteDescriptor(
            val characteristicUuid: UUID,
        ) : GattOperation()
    }

    /** Connect to the pump at [address]. */
    @SuppressLint("MissingPermission")
    fun connect(address: String, pairingCode: String? = null) {
        val adapter = bluetoothManager?.adapter ?: run {
            Timber.e("BluetoothAdapter not available")
            return
        }

        val device: BluetoothDevice = adapter.getRemoteDevice(address)
        _connectionState.value = ConnectionState.CONNECTING
        reconnectAttempt = 0
        pendingPairingCode = pairingCode
        authenticator.reset()
        jpakeAuthenticator.reset()
        operationQueue.clear()
        operationInFlight.set(false)

        Timber.d("Connecting to pump: %s", address)
        val newGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (newGatt == null) {
            Timber.e("Failed to create GATT connection")
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        synchronized(gattLock) {
            gatt = newGatt
        }
    }

    /** Disconnect from the pump. */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        autoReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        operationQueue.clear()
        operationInFlight.set(false)
        cancelPendingRequests()
        synchronized(gattLock) {
            gatt?.let {
                Timber.d("Disconnecting from pump")
                it.disconnect()
                it.close()
            }
            gatt = null
        }
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun cancelPendingRequests() {
        // Atomically drain: remove each entry individually to avoid
        // race with concurrent sendStatusRequest insertions
        val iter = pendingRequests.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            iter.remove()
            entry.value.cancel()
        }
    }

    /** Unpair: clear credentials and disconnect. */
    fun unpair() {
        disconnect()
        credentialStore.clearPairing()
        Timber.d("Pump unpaired and credentials cleared")
    }

    /** Attempt auto-reconnect to the previously paired pump. */
    fun autoReconnectIfPaired() {
        val address = credentialStore.getPairedAddress() ?: return
        val code = credentialStore.getPairingCode()
        autoReconnect = true
        connect(address, code)
    }

    /**
     * Send a status read request and wait for the response cargo.
     *
     * @param opcode The read-only status request opcode.
     * @param cargo Request payload (empty for most status reads).
     * @param timeoutMs Maximum time to wait for a response.
     * @return The response cargo bytes.
     * @throws TimeoutException if no response within [timeoutMs].
     * @throws IllegalStateException if not connected.
     */
    suspend fun sendStatusRequest(
        opcode: Int,
        cargo: ByteArray = ByteArray(0),
        timeoutMs: Long = TandemProtocol.STATUS_READ_TIMEOUT_MS,
    ): ByteArray {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            throw IllegalStateException("Not connected to pump")
        }

        val id = nextTxId()
        val deferred = CompletableDeferred<ByteArray>()

        // Evict any stale deferred with the same txId (wraps at 256)
        val evicted = pendingRequests.put(id, deferred)
        if (evicted != null) {
            Timber.w("Evicting stale pending request txId=%d", id)
            evicted.cancel()
        }

        val chunks = Packetize.encode(opcode, id, cargo, TandemProtocol.CHUNK_SIZE_SHORT)
        enqueueWrite(TandemProtocol.CURRENT_STATUS_UUID, chunks)

        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } finally {
            // Always clean up -- whether completed, timed out, or cancelled
            pendingRequests.remove(id, deferred)
        }
    }

    private fun nextTxId(): Int = txId.getAndIncrement() and 0xFF

    /** Enqueue a characteristic write for serialized GATT execution. */
    private fun enqueueWrite(characteristicUuid: UUID, chunks: List<ByteArray>) {
        for (chunk in chunks) {
            operationQueue.add(GattOperation.WriteCharacteristic(characteristicUuid, chunk))
        }
        drainOperationQueue()
    }

    /** Enqueue a descriptor write (notification enable) for serialized execution. */
    private fun enqueueNotificationEnable(characteristicUuid: UUID) {
        operationQueue.add(GattOperation.WriteDescriptor(characteristicUuid))
        drainOperationQueue()
    }

    @SuppressLint("MissingPermission")
    private fun drainOperationQueue() {
        if (!operationInFlight.compareAndSet(false, true)) return
        val op = operationQueue.poll()
        if (op == null) {
            operationInFlight.set(false)
            return
        }

        val currentGatt = synchronized(gattLock) { gatt }
        if (currentGatt == null) {
            Timber.w("GATT null, dropping operation")
            operationInFlight.set(false)
            operationQueue.clear()
            return
        }

        when (op) {
            is GattOperation.WriteCharacteristic -> {
                val service = currentGatt.getService(TandemProtocol.PUMP_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(op.characteristicUuid)
                if (characteristic == null) {
                    Timber.e("Characteristic %s not found", op.characteristicUuid)
                    operationInFlight.set(false)
                    drainOperationQueue()
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    currentGatt.writeCharacteristic(
                        characteristic,
                        op.data,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = op.data
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    currentGatt.writeCharacteristic(characteristic)
                }
            }
            is GattOperation.WriteDescriptor -> {
                val service = currentGatt.getService(TandemProtocol.PUMP_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(op.characteristicUuid)
                if (characteristic == null) {
                    operationInFlight.set(false)
                    drainOperationQueue()
                    return
                }
                currentGatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(TandemProtocol.CCCD_UUID)
                if (descriptor == null) {
                    operationInFlight.set(false)
                    drainOperationQueue()
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    currentGatt.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    currentGatt.writeDescriptor(descriptor)
                }
            }
        }
    }

    private fun onGattOperationComplete() {
        operationInFlight.set(false)
        drainOperationQueue()
    }

    private fun scheduleReconnect() {
        if (!autoReconnect) return
        val address = credentialStore.getPairedAddress() ?: return

        reconnectAttempt = minOf(reconnectAttempt + 1, 10)
        val delayMs = minOf(1000L * (1 shl minOf(reconnectAttempt, 5)), 32_000L)
        _connectionState.value = ConnectionState.RECONNECTING

        Timber.d("Scheduling reconnect attempt %d in %d ms", reconnectAttempt, delayMs)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (autoReconnect && _connectionState.value == ConnectionState.RECONNECTING) {
                connect(address, credentialStore.getPairingCode())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("GATT connected, requesting MTU %d", TandemProtocol.REQUIRED_MTU)
                    gatt.requestMtu(TandemProtocol.REQUIRED_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("GATT disconnected (status=%d)", status)
                    synchronized(gattLock) {
                        this@BleConnectionManager.gatt?.close()
                        this@BleConnectionManager.gatt = null
                    }
                    operationQueue.clear()
                    operationInFlight.set(false)
                    cancelPendingRequests()
                    _connectionState.value = ConnectionState.DISCONNECTED
                    if (autoReconnect) scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("MTU changed to %d, discovering services", mtu)
                gatt.discoverServices()
            } else {
                Timber.e("MTU request failed (status=%d)", status)
                synchronized(gattLock) {
                    gatt.close()
                    this@BleConnectionManager.gatt = null
                }
                operationQueue.clear()
                operationInFlight.set(false)
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Service discovery failed (status=%d)", status)
                return
            }

            Timber.d("Services discovered, enabling notifications")
            // Enqueue notification subscriptions for serialized GATT execution
            for (uuid in TandemProtocol.NOTIFICATION_CHARACTERISTICS) {
                enqueueNotificationEnable(uuid)
            }

            // Enqueue authentication start after all notifications are enabled
            _connectionState.value = ConnectionState.AUTHENTICATING
            startAuthentication()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Characteristic write failed (status=%d, uuid=%s)", status, characteristic.uuid)
            }
            onGattOperationComplete()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Descriptor write failed (status=%d)", status)
            }
            onGattOperationComplete()
        }

        // API < 33 callback
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: return
            handleCharacteristicChanged(characteristic.uuid, data)
        }

        // API 33+ callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }
    }

    private fun handleCharacteristicChanged(uuid: UUID, data: ByteArray) {
        when (uuid) {
            TandemProtocol.AUTHORIZATION_UUID -> handleAuthNotification(data)
            TandemProtocol.CURRENT_STATUS_UUID -> handleStatusNotification(data)
        }
    }

    // Per-txId assemblers for status responses to handle interleaved
    // multi-chunk notifications from concurrent requests
    private val statusAssemblers = ConcurrentHashMap<Int, PacketAssembler>()

    private fun handleStatusNotification(data: ByteArray) {
        // Peek at the txId from the chunk header (byte 1) to route to
        // the correct per-transaction assembler
        if (data.size < 2) return
        val chunkTxId = data[1].toInt() and 0xFF

        val assembler = statusAssemblers.getOrPut(chunkTxId) { PacketAssembler() }
        val raw: ByteArray
        synchronized(assembler) {
            if (!assembler.feed(data)) return
            raw = assembler.assemble()
            assembler.reset()
        }
        statusAssemblers.remove(chunkTxId)

        val parsed = Packetize.parseHeader(raw) ?: run {
            Timber.e("Failed to parse status notification txId=%d", chunkTxId)
            return
        }

        val (_, responseTxId, cargo) = parsed
        val deferred = pendingRequests.remove(responseTxId)
        if (deferred != null) {
            deferred.complete(cargo)
        } else {
            Timber.w("Received unsolicited status response txId=%d", responseTxId)
        }
    }

    private fun startAuthentication() {
        val code = pendingPairingCode ?: credentialStore.getPairingCode()
        if (code == null) {
            Timber.e("No pairing code available for authentication")
            _connectionState.value = ConnectionState.AUTH_FAILED
            return
        }

        // Use JPAKE auth for 6-digit codes (firmware v7.7+), V1 for 16-char codes
        if (code.length <= 10) {
            Timber.d("Starting JPAKE authentication (code length=%d)", code.length)
            val chunks = jpakeAuthenticator.buildJpake1aRequest(code, nextTxId())
            enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
        } else {
            Timber.d("Starting V1 authentication (code length=%d)", code.length)
            val chunks = authenticator.buildCentralChallengeRequest(nextTxId())
            enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
        }
    }

    private fun onAuthSuccess() {
        _connectionState.value = ConnectionState.CONNECTED
        reconnectAttempt = 0
        // Save credentials on first successful pairing
        val address = synchronized(gattLock) { gatt?.device?.address }
        val code = pendingPairingCode
        if (address != null && code != null) {
            credentialStore.savePairing(address, code)
            pendingPairingCode = null
        }
        Timber.d("Pump authenticated and connected")
    }

    private fun handleAuthNotification(data: ByteArray) {
        val assembler = assemblers.getOrPut(TandemProtocol.AUTHORIZATION_UUID) { PacketAssembler() }
        val raw: ByteArray
        synchronized(assembler) {
            if (!assembler.feed(data)) return
            raw = assembler.assemble()
            assembler.reset()
        }

        val parsed = Packetize.parseHeader(raw) ?: run {
            Timber.e("Failed to parse auth notification")
            return
        }

        val (opcode, _, cargo) = parsed

        when (opcode) {
            // -- V1 authentication opcodes --
            TandemProtocol.OPCODE_CENTRAL_CHALLENGE_RESP -> {
                val code = pendingPairingCode ?: credentialStore.getPairingCode() ?: run {
                    Timber.e("No pairing code available for challenge response")
                    _connectionState.value = ConnectionState.AUTH_FAILED
                    return
                }
                val chunks = authenticator.processChallengeResponse(cargo, code, nextTxId())
                if (chunks != null) {
                    enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
                } else {
                    Timber.e("Auth: failed to build PumpChallengeRequest (bad state or response)")
                    _connectionState.value = ConnectionState.AUTH_FAILED
                }
            }
            TandemProtocol.OPCODE_PUMP_CHALLENGE_RESP -> {
                val success = authenticator.processPumpChallengeResponse(cargo)
                if (success) {
                    onAuthSuccess()
                } else {
                    Timber.e("Pump authentication rejected by pump")
                    _connectionState.value = ConnectionState.AUTH_FAILED
                }
            }

            // -- JPAKE authentication opcodes --
            TandemProtocol.OPCODE_JPAKE_1A_RESP -> {
                if (!jpakeAuthenticator.processJpake1aResponse(cargo)) {
                    _connectionState.value = ConnectionState.AUTH_FAILED
                    return
                }
                val chunks = jpakeAuthenticator.buildJpake1bRequest(nextTxId())
                enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
            }
            TandemProtocol.OPCODE_JPAKE_1B_RESP -> {
                if (!jpakeAuthenticator.processJpake1bResponse(cargo)) {
                    _connectionState.value = ConnectionState.AUTH_FAILED
                    return
                }
                val chunks = jpakeAuthenticator.buildJpake2Request(nextTxId())
                if (chunks.isEmpty()) {
                    _connectionState.value = ConnectionState.AUTH_FAILED
                    return
                }
                enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
            }
            TandemProtocol.OPCODE_JPAKE_2_RESP -> {
                if (!jpakeAuthenticator.processJpake2Response(cargo)) {
                    _connectionState.value = ConnectionState.AUTH_FAILED
                    return
                }
                val chunks = jpakeAuthenticator.buildJpake3Request(nextTxId())
                if (chunks.isEmpty()) {
                    _connectionState.value = ConnectionState.AUTH_FAILED
                    return
                }
                enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
            }
            TandemProtocol.OPCODE_JPAKE_3_SESSION_KEY_RESP -> {
                if (!jpakeAuthenticator.processJpake3Response(cargo)) {
                    _connectionState.value = ConnectionState.AUTH_FAILED
                    return
                }
                val chunks = jpakeAuthenticator.buildJpake4Request(nextTxId())
                enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
            }
            TandemProtocol.OPCODE_JPAKE_4_KEY_CONFIRM_RESP -> {
                val success = jpakeAuthenticator.processJpake4Response(cargo)
                if (success) {
                    onAuthSuccess()
                } else {
                    Timber.e("JPAKE: Key confirmation failed")
                    _connectionState.value = ConnectionState.AUTH_FAILED
                }
            }

            else -> {
                Timber.w("Auth: unexpected opcode %d in auth notification", opcode)
            }
        }
    }
}
