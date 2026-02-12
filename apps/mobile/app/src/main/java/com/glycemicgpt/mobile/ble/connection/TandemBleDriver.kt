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
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tandem t:slim X2 BLE driver implementing the PumpDriver interface.
 *
 * Delegates connection lifecycle to [BleConnectionManager].
 * Data read operations will be implemented in Story 16.3.
 */
@Singleton
class TandemBleDriver @Inject constructor(
    private val connectionManager: BleConnectionManager,
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

    override fun observeConnectionState(): Flow<ConnectionState> =
        connectionManager.connectionState
}
