package com.glycemicgpt.mobile.ble.connection

import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.BolusEvent
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.PumpSettings
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import javax.inject.Inject

/**
 * Tandem t:slim X2 BLE driver implementing the PumpDriver interface.
 *
 * Uses our own BLE protocol implementation (not pumpX2) to communicate
 * with the pump. Only read-only status requests are implemented.
 *
 * Full implementation in Story 16.2 (pairing/connection) and Story 16.3 (data reads).
 */
class TandemBleDriver @Inject constructor() : PumpDriver {

    private val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    override suspend fun connect(deviceAddress: String): Result<Unit> {
        // Story 16.2: BLE pairing and connection
        return Result.failure(NotImplementedError("BLE connection - Story 16.2"))
    }

    override suspend fun disconnect(): Result<Unit> {
        connectionState.value = ConnectionState.DISCONNECTED
        return Result.success(Unit)
    }

    override suspend fun getIoB(): Result<IoBReading> {
        // Story 16.3: ControlIQIOBRequest (opcode 108)
        return Result.failure(NotImplementedError("IoB read - Story 16.3"))
    }

    override suspend fun getBasalRate(): Result<BasalReading> {
        // Story 16.3: CurrentBasalStatusRequest
        return Result.failure(NotImplementedError("Basal read - Story 16.3"))
    }

    override suspend fun getBolusHistory(since: Instant): Result<List<BolusEvent>> {
        // Story 16.3: Bolus history request
        return Result.failure(NotImplementedError("Bolus history - Story 16.3"))
    }

    override suspend fun getPumpSettings(): Result<PumpSettings> {
        // Story 16.3: Pump settings request
        return Result.failure(NotImplementedError("Pump settings - Story 16.3"))
    }

    override suspend fun getBatteryStatus(): Result<BatteryStatus> {
        // Story 16.3: CurrentBatteryRequest
        return Result.failure(NotImplementedError("Battery status - Story 16.3"))
    }

    override suspend fun getReservoirLevel(): Result<ReservoirReading> {
        // Story 16.3: InsulinStatusRequest
        return Result.failure(NotImplementedError("Reservoir level - Story 16.3"))
    }

    override fun observeConnectionState(): Flow<ConnectionState> = connectionState
}
