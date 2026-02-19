package com.glycemicgpt.mobile.ble.connection

import com.glycemicgpt.mobile.ble.messages.StatusResponseParser
import com.glycemicgpt.mobile.ble.protocol.TandemProtocol
import com.glycemicgpt.mobile.data.local.BleDebugStore
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.BolusEvent
import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.CgmTrend
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.HistoryLogRecord
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.PumpHardwareInfo
import com.glycemicgpt.mobile.domain.model.PumpSettings
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tandem t:slim X2 BLE driver implementing the PumpDriver interface.
 *
 * Delegates connection lifecycle to [BleConnectionManager] and uses
 * [StatusResponseParser] to decode pump responses into domain models.
 *
 * All data read methods are READ-ONLY status queries. No control
 * operations are implemented or invocable through this class.
 */
@Singleton
class TandemBleDriver @Inject constructor(
    private val connectionManager: BleConnectionManager,
    private val debugStore: BleDebugStore,
) : PumpDriver {

    override suspend fun connect(deviceAddress: String): Result<Unit> {
        return try {
            connectionManager.connect(deviceAddress)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect(): Result<Unit> {
        return try {
            connectionManager.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getIoB(): Result<IoBReading> = runStatusRequest(
        opcode = TandemProtocol.OPCODE_CONTROL_IQ_IOB_REQ,
    ) { cargo ->
        StatusResponseParser.parseIoBResponse(cargo)
            ?: throw IllegalStateException("Failed to parse IoB response")
    }

    override suspend fun getBasalRate(): Result<BasalReading> = runStatusRequest(
        opcode = TandemProtocol.OPCODE_CURRENT_BASAL_STATUS_REQ,
    ) { cargo ->
        StatusResponseParser.parseBasalStatusResponse(cargo)
            ?: throw IllegalStateException("Failed to parse basal status response")
    }

    override suspend fun getBolusHistory(since: Instant): Result<List<BolusEvent>> = runStatusRequest(
        opcode = TandemProtocol.OPCODE_LAST_BOLUS_STATUS_REQ,
    ) { cargo ->
        StatusResponseParser.parseLastBolusStatusResponse(cargo, since)
    }

    override suspend fun getPumpSettings(): Result<PumpSettings> = runStatusRequest(
        opcode = TandemProtocol.OPCODE_PUMP_SETTINGS_REQ,
    ) { cargo ->
        StatusResponseParser.parsePumpSettingsResponse(cargo)
            ?: throw IllegalStateException("Failed to parse pump settings response")
    }

    override suspend fun getBatteryStatus(): Result<BatteryStatus> {
        // Use V1 first (universally supported). V2 (opcode 144) is only for
        // Mobi pumps and causes GATT_ERROR (133) on tslim X2, killing the
        // connection before fallback can execute.
        val v1Result = runStatusRequest(
            opcode = TandemProtocol.OPCODE_CURRENT_BATTERY_V1_REQ,
        ) { cargo ->
            StatusResponseParser.parseBatteryV1Response(cargo)
                ?: throw IllegalStateException("Failed to parse battery V1 response")
        }
        if (v1Result.isSuccess) return v1Result
        // Fall back to V2 only if V1 fails (e.g., on Mobi pumps)
        return runStatusRequest(
            opcode = TandemProtocol.OPCODE_CURRENT_BATTERY_V2_REQ,
        ) { cargo ->
            StatusResponseParser.parseBatteryV2Response(cargo)
                ?: throw IllegalStateException("Failed to parse battery V2 response")
        }
    }

    override suspend fun getReservoirLevel(): Result<ReservoirReading> = runStatusRequest(
        opcode = TandemProtocol.OPCODE_INSULIN_STATUS_REQ,
    ) { cargo ->
        StatusResponseParser.parseInsulinStatusResponse(cargo)
            ?: throw IllegalStateException("Failed to parse insulin status response")
    }

    override suspend fun getCgmStatus(): Result<CgmReading> {
        // Get glucose value from CurrentEGVGuiData
        val egvResult = runStatusRequest(
            opcode = TandemProtocol.OPCODE_CGM_EGV_REQ,
        ) { cargo ->
            StatusResponseParser.parseCgmEgvResponse(cargo)
                ?: throw IllegalStateException("Failed to parse CGM EGV response")
        }
        if (egvResult.isFailure) return egvResult

        // Get trend arrow from HomeScreenMirror (failure degrades to UNKNOWN)
        val mirrorResult = runStatusRequest(
            opcode = TandemProtocol.OPCODE_HOME_SCREEN_MIRROR_REQ,
        ) { cargo ->
            StatusResponseParser.parseHomeScreenMirrorResponse(cargo)
                ?: throw IllegalStateException("Failed to parse HomeScreenMirror response")
        }

        val egv = egvResult.getOrThrow()
        val trend = mirrorResult.getOrNull()?.trendArrow ?: CgmTrend.UNKNOWN

        return Result.success(egv.copy(trendArrow = trend))
    }

    override suspend fun getHistoryLogs(sinceSequence: Int): Result<List<HistoryLogRecord>> {
        // Step 1: Get the available sequence range from the pump (opcode 58).
        val rangeResult = runStatusRequest(
            opcode = TandemProtocol.OPCODE_HISTORY_LOG_STATUS_REQ,
        ) { cargo ->
            StatusResponseParser.parseHistoryLogStatusResponse(cargo)
                ?: throw IllegalStateException("Failed to parse history log status (need 12 bytes, got ${cargo.size})")
        }
        if (rangeResult.isFailure) return Result.failure(rangeResult.exceptionOrNull()!!)

        val range = rangeResult.getOrThrow()
        val startSeq = maxOf(sinceSequence + 1, range.firstSeq)
        Timber.d("History log range: firstSeq=%d lastSeq=%d sinceSequence=%d startSeq=%d",
            range.firstSeq, range.lastSeq, sinceSequence, startSeq)

        if (startSeq > range.lastSeq) return Result.success(emptyList())

        // Step 2: Fetch records in batches via opcode 60.
        // Opcode 60 sends a 2-byte ACK on FFF6 and streams records on FFF8.
        // Cap total time to avoid blocking the slow poll loop (pump drops
        // idle connections at ~30s; other reads need time to execute too).
        val allRecords = mutableListOf<HistoryLogRecord>()
        var currentSeq = startSeq
        val deadline = System.currentTimeMillis() + MAX_HISTORY_FETCH_DURATION_MS

        while (currentSeq <= range.lastSeq &&
            allRecords.size < MAX_HISTORY_RECORDS_PER_POLL &&
            System.currentTimeMillis() < deadline
        ) {
            val batchSize = minOf(HISTORY_BATCH_SIZE, range.lastSeq - currentSeq + 1)
            val cargo = buildHistoryLogCargo(currentSeq, batchSize)

            val fff8Cargo = try {
                connectionManager.requestHistoryLogStream(
                    cargo = cargo,
                    timeoutMs = TandemProtocol.HISTORY_LOG_TIMEOUT_MS,
                )
            } catch (e: Exception) {
                Timber.w(e, "History log stream request failed at seq=%d", currentSeq)
                break
            }

            val records = StatusResponseParser.parseHistoryLogStreamCargo(
                fff8Cargo, sinceSequence,
            )
            if (records.isEmpty()) {
                Timber.d("No records parsed from FFF8 cargo (%d bytes) at seq=%d",
                    fff8Cargo.size, currentSeq)
                break
            }
            allRecords.addAll(records)
            currentSeq = records.maxOf { it.sequenceNumber } + 1
            delay(HISTORY_BATCH_STAGGER_MS)
        }

        Timber.d("Fetched %d history log records (startSeq=%d)", allRecords.size, startSeq)
        return Result.success(allRecords)
    }

    override suspend fun getPumpHardwareInfo(): Result<PumpHardwareInfo> {
        val versionResult = runStatusRequest(
            opcode = TandemProtocol.OPCODE_PUMP_VERSION_REQ,
        ) { cargo ->
            StatusResponseParser.parsePumpVersionResponse(cargo)
                ?: throw IllegalStateException("Failed to parse pump version response")
        }
        if (versionResult.isFailure) return versionResult

        // Fetch features separately; degrade gracefully to empty map on failure
        val featuresResult = runStatusRequest(
            opcode = TandemProtocol.OPCODE_PUMP_FEATURES_V1_REQ,
        ) { cargo ->
            StatusResponseParser.parsePumpFeaturesResponse(cargo)
        }
        val features = featuresResult.getOrDefault(emptyMap())

        return Result.success(versionResult.getOrThrow().copy(pumpFeatures = features))
    }

    override fun observeConnectionState(): Flow<ConnectionState> =
        connectionManager.connectionState

    companion object {
        /** Max records to request per opcode 60 batch. */
        const val HISTORY_BATCH_SIZE = 20

        /** Delay between consecutive batch requests to avoid BLE congestion. */
        const val HISTORY_BATCH_STAGGER_MS = 200L

        /** Safety cap on total records fetched per poll cycle. */
        const val MAX_HISTORY_RECORDS_PER_POLL = 200

        /** Max time (ms) to spend fetching history logs per poll cycle.
         *  Keeps the slow loop responsive and avoids pump idle-timeout. */
        const val MAX_HISTORY_FETCH_DURATION_MS = 15_000L

        /**
         * Build the 5-byte cargo for opcode 60 (HistoryLogRequest).
         * Layout: uint32 LE startSequence + uint8 count.
         */
        fun buildHistoryLogCargo(startSequence: Int, count: Int): ByteArray {
            val buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(startSequence)
            buf.put(count.coerceIn(1, 255).toByte())
            return buf.array()
        }
    }

    /**
     * Send a status request and parse the response. Wraps the entire
     * send-receive-parse cycle in a Result for safe error handling.
     * Logs parsed values and errors to the debug store.
     */
    private suspend fun <T> runStatusRequest(
        opcode: Int,
        cargo: ByteArray = ByteArray(0),
        timeoutMs: Long = TandemProtocol.STATUS_READ_TIMEOUT_MS,
        parser: (ByteArray) -> T,
    ): Result<T> {
        return try {
            val responseCargo = connectionManager.sendStatusRequest(opcode, cargo, timeoutMs)
            val result = parser(responseCargo)
            val parsedStr = result.toString()
            Timber.d("BLE_RAW PARSED opcode=0x%02x result=%s", opcode, parsedStr)
            debugStore.updateLast(opcode, BleDebugStore.Direction.RX, parsedValue = parsedStr)
            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "BLE_RAW PARSE_ERROR opcode=0x%02x", opcode)
            debugStore.updateLast(opcode, BleDebugStore.Direction.RX, error = e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }
}
