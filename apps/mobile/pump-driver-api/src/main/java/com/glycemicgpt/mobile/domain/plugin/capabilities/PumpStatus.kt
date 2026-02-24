package com.glycemicgpt.mobile.domain.plugin.capabilities

import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.BolusEvent
import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.HistoryLogRecord
import com.glycemicgpt.mobile.domain.model.PumpHardwareInfo
import com.glycemicgpt.mobile.domain.model.PumpSettings
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.domain.plugin.PluginCapabilityInterface
import com.glycemicgpt.mobile.domain.pump.SafetyLimits

/**
 * Capability for plugins that provide pump hardware status and history data (read-only).
 */
interface PumpStatus : PluginCapabilityInterface {
    suspend fun getBatteryStatus(): Result<BatteryStatus>
    suspend fun getReservoirLevel(): Result<ReservoirReading>
    suspend fun getPumpSettings(): Result<PumpSettings>
    suspend fun getPumpHardwareInfo(): Result<PumpHardwareInfo>
    suspend fun getHistoryLogs(sinceSequence: Int): Result<List<HistoryLogRecord>>

    fun extractCgmFromHistoryLogs(
        records: List<HistoryLogRecord>,
        limits: SafetyLimits,
    ): List<CgmReading>

    fun extractBolusesFromHistoryLogs(
        records: List<HistoryLogRecord>,
        limits: SafetyLimits,
    ): List<BolusEvent>

    fun extractBasalFromHistoryLogs(
        records: List<HistoryLogRecord>,
        limits: SafetyLimits,
    ): List<BasalReading>

    /** Clear pairing credentials and disconnect. */
    fun unpair()

    /** Attempt to reconnect to a previously paired device. */
    fun autoReconnectIfPaired()
}
