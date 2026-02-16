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
import com.glycemicgpt.mobile.data.local.BleDebugStore
import com.glycemicgpt.mobile.data.local.PumpCredentialStore
import com.glycemicgpt.mobile.data.local.toHexString
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
import java.time.Instant
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
    private val debugStore: BleDebugStore,
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

    // Tracks rapid disconnections after connect (bond loss detection).
    // If the pump disconnects us multiple times before we ever reach CONNECTED,
    // it likely means the BLE bond was lost and re-pairing is required.
    @Volatile
    private var rapidDisconnectCount = 0

    // Tracks connections where we reach CONNECTED but receive zero notifications.
    // If this happens repeatedly, the pump is not responding (likely needs re-pairing).
    @Volatile
    private var zeroResponseConnectionCount = 0
    @Volatile
    private var receivedAnyNotification = false

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

    // Handshake timeout: fail if authentication doesn't complete within this window.
    // JPAKE has 5 round trips; 30s is generous even on slow BLE connections.
    private var authTimeoutJob: Job? = null

    // Post-auth settle: delayed job that sets CONNECTED after the pump stabilizes.
    // Must be cancelled on disconnect to prevent stale CONNECTED state.
    private var settleJob: Job? = null

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
        val bondState = device.bondState
        Timber.d("Bond state for %s: %s", address, when (bondState) {
            BluetoothDevice.BOND_NONE -> "NONE"
            BluetoothDevice.BOND_BONDING -> "BONDING"
            BluetoothDevice.BOND_BONDED -> "BONDED"
            else -> "UNKNOWN($bondState)"
        })
        _connectionState.value = ConnectionState.CONNECTING
        autoReconnect = true
        reconnectAttempt = 0
        rapidDisconnectCount = 0
        receivedAnyNotification = false
        pendingPairingCode = pairingCode
        authenticator.reset()
        jpakeAuthenticator.reset()
        operationQueue.clear()
        operationInFlight.set(false)
        // Clear stale packet assemblers from previous connection
        assemblers.clear()
        statusAssemblers.clear()
        cancelPendingRequests()

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
        authTimeoutJob?.cancel()
        authTimeoutJob = null
        settleJob?.cancel()
        settleJob = null
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

    /** Unpair: clear credentials, remove BLE bond, and disconnect. */
    @SuppressLint("MissingPermission")
    fun unpair() {
        val address = credentialStore.getPairedAddress()
        disconnect()
        credentialStore.clearPairing()
        // Remove the BLE bond so stale encryption keys don't block future connections.
        if (address != null) {
            removeBond(address)
        }
        Timber.d("Pump unpaired, credentials + JPAKE session cleared, bond removed")
    }

    /**
     * Remove the BLE bond for a device by address.
     * Uses reflection because BluetoothDevice.removeBond() is a hidden API.
     */
    @SuppressLint("MissingPermission")
    private fun removeBond(address: String) {
        try {
            val adapter = bluetoothManager?.adapter ?: return
            val device = adapter.getRemoteDevice(address)
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                val method = device.javaClass.getMethod("removeBond")
                val result = method.invoke(device) as? Boolean ?: false
                Timber.d("removeBond(%s) = %b", address, result)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to remove BLE bond for %s", address)
        }
    }

    /**
     * Clear Android's GATT service cache via reflection.
     * This forces a fresh service discovery, preventing stale cached
     * characteristics from silently dropping notifications.
     */
    private fun refreshGattCache(gatt: BluetoothGatt) {
        try {
            val method = gatt.javaClass.getMethod("refresh")
            val result = method.invoke(gatt) as? Boolean ?: false
            Timber.d("GATT cache refresh = %b", result)
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh GATT cache")
        }
    }

    /** Attempt auto-reconnect to the previously paired pump. */
    fun autoReconnectIfPaired() {
        val address = credentialStore.getPairedAddress() ?: return
        // Pass null for pairingCode -- reconnects use JPAKE confirmation mode
        // with the saved derived secret from initial pairing.
        connect(address, pairingCode = null)
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

        Timber.d("BLE_RAW TX opcode=0x%02x txId=%d cargoLen=%d", opcode, id, cargo.size)
        debugStore.add(BleDebugStore.Entry(
            timestamp = Instant.now(),
            direction = BleDebugStore.Direction.TX,
            opcode = opcode,
            opcodeName = TandemProtocol.opcodeName(opcode),
            txId = id,
            cargoHex = cargo.toHexString(),
            cargoSize = cargo.size,
        ))

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
                // Pass null for pairingCode -- reconnects use JPAKE confirmation mode
                // with the saved derived secret (or bootstrap if no secret saved)
                connect(address, pairingCode = null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Clear GATT service cache on reconnect. Android caches GATT
                    // services after bonding; stale caches can cause notifications
                    // to silently fail on subsequent connections.
                    refreshGattCache(gatt)
                    // Request HIGH connection priority (7.5-15ms interval) to match
                    // pumpX2 behavior. The pump may drop connections with slower
                    // default intervals (~30-50ms).
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    Timber.d("GATT connected, requesting MTU %d (priority=HIGH)", TandemProtocol.REQUIRED_MTU)
                    gatt.requestMtu(TandemProtocol.REQUIRED_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.w("BLE_RAW GATT disconnected status=%d (%s)",
                        status, gattStatusName(status))
                    settleJob?.cancel()
                    settleJob = null
                    synchronized(gattLock) {
                        this@BleConnectionManager.gatt?.close()
                        this@BleConnectionManager.gatt = null
                    }
                    operationQueue.clear()
                    operationInFlight.set(false)
                    cancelPendingRequests()

                    // Bond-loss detection: if the pump keeps disconnecting us
                    // before we ever reach CONNECTED (e.g., status 5 or 19),
                    // the BLE bond is likely lost and re-pairing is needed.
                    val wasConnected = _connectionState.value == ConnectionState.CONNECTED ||
                        _connectionState.value == ConnectionState.AUTHENTICATING
                    if (!wasConnected && status == GATT_CONN_TERMINATE_PEER_USER) {
                        rapidDisconnectCount++
                        if (rapidDisconnectCount >= MAX_RAPID_DISCONNECTS) {
                            Timber.e("Pump rejected %d consecutive connections -- BLE bond likely lost, re-pairing required",
                                rapidDisconnectCount)
                            autoReconnect = false
                            authTimeoutJob?.cancel()
                            reconnectJob?.cancel()
                            _connectionState.value = ConnectionState.AUTH_FAILED
                            return
                        }
                    }
                    // Insufficient authentication before CONNECTED = bond definitely lost.
                    // During an active session, status 0x05 can be transient (key rotation),
                    // so only treat it as bond loss if we haven't reached CONNECTED yet.
                    if (!wasConnected && status == GATT_INSUFFICIENT_AUTHENTICATION) {
                        Timber.e("Pump reports insufficient authentication -- BLE bond lost, re-pairing required")
                        val addr = credentialStore.getPairedAddress()
                        if (addr != null) removeBond(addr)
                        autoReconnect = false
                        rapidDisconnectCount = 0
                        authTimeoutJob?.cancel()
                        reconnectJob?.cancel()
                        _connectionState.value = ConnectionState.AUTH_FAILED
                        return
                    }
                    // Insufficient encryption = stale bond encryption keys.
                    // The BLE bond exists but the keys are no longer valid.
                    // Remove the bond so the next connection triggers fresh bonding.
                    if (status == GATT_INSUFFICIENT_ENCRYPTION) {
                        Timber.e("Pump reports insufficient encryption -- bond keys stale, removing bond for re-pairing")
                        val addr = credentialStore.getPairedAddress()
                        if (addr != null) removeBond(addr)
                        autoReconnect = false
                        authTimeoutJob?.cancel()
                        reconnectJob?.cancel()
                        _connectionState.value = ConnectionState.AUTH_FAILED
                        return
                    }

                    // Track connections where we reached CONNECTED but got zero
                    // notifications. This means the pump ignored all our requests,
                    // likely because it requires re-pairing.
                    if (wasConnected && !receivedAnyNotification) {
                        zeroResponseConnectionCount++
                        Timber.w("Zero-response connection #%d (pump ignoring requests)",
                            zeroResponseConnectionCount)
                        if (zeroResponseConnectionCount >= MAX_ZERO_RESPONSE_CONNECTIONS) {
                            Timber.e("Pump has not responded in %d consecutive connections -- re-pairing required",
                                zeroResponseConnectionCount)
                            val addr = credentialStore.getPairedAddress()
                            if (addr != null) removeBond(addr)
                            autoReconnect = false
                            authTimeoutJob?.cancel()
                            reconnectJob?.cancel()
                            _connectionState.value = ConnectionState.AUTH_FAILED
                            return
                        }
                    } else if (receivedAnyNotification) {
                        zeroResponseConnectionCount = 0
                    }

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
            // Enable AUTHORIZATION CCCD first so the pump can respond to
            // JPAKE immediately. Then enqueue auth, then the remaining CCCDs.
            // This matches the pumpX2 approach: auth first, then status CCCDs.
            enqueueNotificationEnable(TandemProtocol.AUTHORIZATION_UUID)
            _connectionState.value = ConnectionState.AUTHENTICATING
            startAuthentication()
            // Enable remaining notification CCCDs after auth is queued.
            // They'll execute after JPAKE chunks in the serialized queue.
            for (uuid in TandemProtocol.NOTIFICATION_CHARACTERISTICS) {
                if (uuid != TandemProtocol.AUTHORIZATION_UUID) {
                    enqueueNotificationEnable(uuid)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("BLE_RAW WRITE FAILED status=%d (%s) char=%s",
                    status, gattStatusName(status), characteristic.uuid)
            } else {
                Timber.d("BLE_RAW WRITE OK char=%s", characteristic.uuid)
            }
            onGattOperationComplete()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            val charUuid = descriptor.characteristic?.uuid
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Descriptor write failed (status=%d, char=%s)", status, charUuid)
            } else {
                Timber.d("CCCD enabled for %s", charUuid)
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
        receivedAnyNotification = true
        Timber.d("BLE_RAW NOTIFY char=%s len=%d", uuid, data.size)
        when (uuid) {
            TandemProtocol.AUTHORIZATION_UUID -> handleAuthNotification(data)
            TandemProtocol.CURRENT_STATUS_UUID -> handleStatusNotification(data)
            else -> Timber.d("BLE_RAW NOTIFY unhandled char=%s", uuid)
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
            Timber.e("Failed to parse status notification txId=%d len=%d",
                chunkTxId, raw.size)
            debugStore.add(BleDebugStore.Entry(
                timestamp = Instant.now(),
                direction = BleDebugStore.Direction.RX,
                opcode = if (raw.isNotEmpty()) raw[0].toInt() and 0xFF else -1,
                opcodeName = "PARSE_FAIL",
                txId = chunkTxId,
                cargoHex = raw.toHexString(),
                cargoSize = raw.size,
                error = "CRC or header parse failed",
            ))
            return
        }

        val (opcode, responseTxId, cargo) = parsed
        val cargoHex = cargo.toHexString()
        Timber.d("BLE_RAW RX opcode=0x%02x txId=%d cargoLen=%d hex=%s",
            opcode, responseTxId, cargo.size, cargoHex)
        debugStore.add(BleDebugStore.Entry(
            timestamp = Instant.now(),
            direction = BleDebugStore.Direction.RX,
            opcode = opcode,
            opcodeName = TandemProtocol.opcodeName(opcode),
            txId = responseTxId,
            cargoHex = cargoHex,
            cargoSize = cargo.size,
        ))

        val deferred = pendingRequests.remove(responseTxId)
        if (deferred != null) {
            deferred.complete(cargo)
        } else {
            Timber.w("Received unsolicited status response txId=%d opcode=0x%02x",
                responseTxId, opcode)
        }
    }

    private fun startAuthentication() {
        // The pump requires app-level auth (JPAKE) on EVERY connection,
        // including reconnects. Without it, the pump silently ignores all
        // status requests even with a valid BLE bond.
        //
        // JPAKE has two modes:
        // - BOOTSTRAP: Full 5-round-trip handshake. Requires pump in pairing mode.
        // - CONFIRMATION: 2-round-trip handshake using saved derived secret.
        //   Works on reconnect without pairing mode.
        val isReconnect = pendingPairingCode == null && credentialStore.isPaired()
        val code = pendingPairingCode ?: credentialStore.getPairingCode()
        if (code == null) {
            Timber.e("No pairing code available for authentication")
            _connectionState.value = ConnectionState.AUTH_FAILED
            return
        }

        // Start handshake timeout -- fail if auth doesn't complete in time
        authTimeoutJob?.cancel()
        authTimeoutJob = scope.launch {
            delay(AUTH_TIMEOUT_MS)
            if (_connectionState.value == ConnectionState.AUTHENTICATING) {
                Timber.e("Authentication handshake timed out after %d ms", AUTH_TIMEOUT_MS)
                _connectionState.value = ConnectionState.AUTH_FAILED
            }
        }

        // Use JPAKE for 6-digit codes (firmware v7.7+), V1 for 16-char codes.
        if (code.length <= 10) {
            jpakeAuthenticator.reset()
            val savedSecretHex = credentialStore.getJpakeDerivedSecret()
            if (isReconnect && savedSecretHex != null) {
                // CONFIRMATION mode: skip rounds 1-2, jump to round 3 using
                // the derived secret saved from the initial bootstrap pairing.
                Timber.d("Starting JPAKE CONFIRMATION mode (reconnect, saved secret available)")
                val secretBytes = hexStringToByteArray(savedSecretHex)
                val chunks = jpakeAuthenticator.buildJpake3RequestFromDerivedSecret(
                    secretBytes, nextTxId(),
                )
                enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
            } else {
                // BOOTSTRAP mode: full 5-round-trip handshake.
                // Pump must be in pairing mode (showing code on screen).
                Timber.d("Starting JPAKE BOOTSTRAP mode (initial pair, code length=%d)", code.length)
                val chunks = jpakeAuthenticator.buildJpake1aRequest(code, nextTxId())
                enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
            }
        } else {
            Timber.d("Starting V1 authentication (code length=%d)", code.length)
            authenticator.reset()
            val chunks = authenticator.buildCentralChallengeRequest(nextTxId())
            enqueueWrite(TandemProtocol.AUTHORIZATION_UUID, chunks)
        }
    }

    /** Convert a hex string (no separators) to a ByteArray. */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun onAuthSuccess() {
        authTimeoutJob?.cancel()
        authTimeoutJob = null
        reconnectAttempt = 0
        rapidDisconnectCount = 0
        // Save credentials on first successful pairing
        val address = synchronized(gattLock) { gatt?.device?.address }
        val code = pendingPairingCode
        if (address != null && code != null) {
            credentialStore.savePairing(address, code)
            pendingPairingCode = null
        }
        // Save JPAKE derived credentials for confirmation mode reconnect.
        // After initial bootstrap, the derived secret allows reconnection
        // without the pump being in pairing mode.
        val secret = jpakeAuthenticator.getDerivedSecret()
        val nonce = jpakeAuthenticator.getServerNonce()
        if (secret != null && nonce != null) {
            credentialStore.saveJpakeCredentials(
                derivedSecretHex = secret.joinToString("") { "%02x".format(it) },
                serverNonceHex = nonce.joinToString("") { "%02x".format(it) },
            )
            Timber.d("JPAKE credentials saved for confirmation mode reconnect")
        }
        // Send initialization sequence (API version, pump version, time since reset)
        // before allowing status reads. pumpX2 sends these after auth to fully
        // establish the session -- without them the pump may drop the connection
        // after a few status responses.
        Timber.d("Pump authenticated, sending initialization sequence")
        sendInitSequence()

        // Post-auth settle: give the pump time to process the init messages
        // before we flood it with status requests.
        Timber.d("Settling for %d ms before allowing reads", POST_AUTH_SETTLE_MS)
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(POST_AUTH_SETTLE_MS)
            // Guard: only transition if we haven't been disconnected during the delay
            if (_connectionState.value == ConnectionState.AUTHENTICATING) {
                _connectionState.value = ConnectionState.CONNECTED
                Timber.d("Pump connected and ready for status requests")
            }
        }
    }

    /**
     * Send post-auth initialization requests that pumpX2 sends to establish
     * the session. These are fire-and-forget -- we don't need the responses,
     * but the pump needs to see them before accepting status reads.
     */
    private fun sendInitSequence() {
        val initOpcodes = intArrayOf(
            TandemProtocol.OPCODE_API_VERSION_REQ,
            TandemProtocol.OPCODE_PUMP_VERSION_REQ,
            TandemProtocol.OPCODE_TIME_SINCE_RESET_REQ,
        )
        for (opcode in initOpcodes) {
            val id = nextTxId()
            Timber.d("Init TX opcode=0x%02x (%s) txId=%d",
                opcode, TandemProtocol.opcodeName(opcode), id)
            val chunks = Packetize.encode(opcode, id, ByteArray(0), TandemProtocol.CHUNK_SIZE_SHORT)
            enqueueWrite(TandemProtocol.CURRENT_STATUS_UUID, chunks)
        }
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
            Timber.e("Failed to parse auth notification len=%d", raw.size)
            debugStore.add(BleDebugStore.Entry(
                timestamp = Instant.now(),
                direction = BleDebugStore.Direction.RX,
                opcode = if (raw.isNotEmpty()) raw[0].toInt() and 0xFF else -1,
                opcodeName = "AUTH_PARSE_FAIL",
                txId = -1,
                cargoHex = raw.toHexString(),
                cargoSize = raw.size,
                error = "Auth CRC or header parse failed",
            ))
            return
        }

        val (opcode, authTxId, cargo) = parsed
        Timber.d("BLE_RAW AUTH opcode=0x%02x txId=%d cargoLen=%d",
            opcode, authTxId, cargo.size)
        debugStore.add(BleDebugStore.Entry(
            timestamp = Instant.now(),
            direction = BleDebugStore.Direction.RX,
            opcode = opcode,
            opcodeName = TandemProtocol.opcodeName(opcode),
            txId = authTxId,
            cargoHex = cargo.toHexString(),
            cargoSize = cargo.size,
        ))

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
                    // If confirmation mode was rejected, the derived secret may
                    // be stale (e.g., pump was factory reset). Clear saved
                    // credentials so the next attempt uses bootstrap mode.
                    Timber.w("JPAKE round 3 failed, clearing saved JPAKE credentials")
                    credentialStore.clearJpakeCredentials()
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
                    Timber.e("JPAKE: Key confirmation failed, clearing saved JPAKE credentials")
                    credentialStore.clearJpakeCredentials()
                    _connectionState.value = ConnectionState.AUTH_FAILED
                }
            }

            else -> {
                Timber.w("Auth: unexpected opcode %d in auth notification", opcode)
            }
        }
    }

    companion object {
        /** Post-auth settle delay (ms) before setting CONNECTED state.
         *  After JPAKE completes and init sequence is sent, this gives the pump
         *  time to process initialization before we send status requests.
         *  Keep short to avoid idle-timeout disconnects from the pump.
         */
        const val POST_AUTH_SETTLE_MS = 500L

        /** Auth handshake timeout. JPAKE has 5 round trips; 30s is generous. */
        const val AUTH_TIMEOUT_MS = 30_000L

        /** Max consecutive rapid disconnections before declaring bond lost. */
        const val MAX_RAPID_DISCONNECTS = 3

        /** Max connections with zero pump responses before giving up. */
        const val MAX_ZERO_RESPONSE_CONNECTIONS = 3

        // Named GATT status codes used in bond-loss detection
        private const val GATT_INSUFFICIENT_AUTHENTICATION = 0x05
        private const val GATT_INSUFFICIENT_ENCRYPTION = 0x08
        private const val GATT_CONN_TERMINATE_PEER_USER = 0x13

        /** Convert a GATT status code to a human-readable name. */
        fun gattStatusName(status: Int): String = when (status) {
            0 -> "SUCCESS"
            2 -> "READ_NOT_PERMITTED"
            5 -> "INSUFFICIENT_AUTHENTICATION"
            6 -> "REQUEST_NOT_SUPPORTED"
            7 -> "INVALID_OFFSET"
            8 -> "INSUFFICIENT_ENCRYPTION"
            0x0D -> "INVALID_ATTRIBUTE_LENGTH"
            0x13 -> "CONN_TERMINATE_PEER_USER"
            0x16 -> "CONN_TERMINATE_LOCAL_HOST"
            0x22 -> "CONN_FAILED_ESTABLISHMENT"
            0x3B -> "UNSPECIFIED_ERROR"
            0x3E -> "CONN_TIMEOUT"
            0x85 -> "GATT_ERROR"
            0x101 -> "GATT_FAILURE"
            else -> "UNKNOWN_0x${status.toString(16)}"
        }
    }
}
