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
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
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

    override suspend fun getHistoryLogs(sinceSequence: Int): Result<List<HistoryLogRecord>> = runStatusRequest(
        opcode = TandemProtocol.OPCODE_HISTORY_LOG_STATUS_REQ,
    ) { cargo ->
        // HistoryLogStatusResponse returns 8 bytes (firstSeq + lastSeq).
        // The record parser expects 18-byte records, so it safely returns
        // emptyList() for the status response. Full log fetching via
        // OPCODE_HISTORY_LOG_REQ (60) with 5-byte cargo will be added
        // when we implement incremental log download.
        StatusResponseParser.parseHistoryLogResponse(cargo, sinceSequence)
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

    /**
     * Send a status request and parse the response. Wraps the entire
     * send-receive-parse cycle in a Result for safe error handling.
     * Logs parsed values and errors to the debug store.
     */
    private suspend fun <T> runStatusRequest(
        opcode: Int,
        parser: (ByteArray) -> T,
    ): Result<T> {
        return try {
            val responseCargo = connectionManager.sendStatusRequest(opcode)
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
