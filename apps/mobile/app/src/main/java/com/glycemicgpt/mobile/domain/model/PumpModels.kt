package com.glycemicgpt.mobile.domain.model

import java.time.Instant

data class IoBReading(
    val iob: Float,
    val timestamp: Instant,
)

data class BasalReading(
    val rate: Float,
    val isAutomated: Boolean,
    val controlIqMode: ControlIqMode,
    val timestamp: Instant,
)

enum class ControlIqMode {
    STANDARD,
    SLEEP,
    EXERCISE,
}

data class BolusEvent(
    val units: Float,
    val isAutomated: Boolean,
    val isCorrection: Boolean,
    val timestamp: Instant,
)

data class PumpSettings(
    val firmwareVersion: String,
    val serialNumber: String,
    val modelNumber: String,
)

data class BatteryStatus(
    val percentage: Int,
    val isCharging: Boolean,
    val timestamp: Instant,
)

data class ReservoirReading(
    val unitsRemaining: Float,
    val timestamp: Instant,
)

data class HistoryLogRecord(
    val sequenceNumber: Int,
    val rawBytesB64: String,
    val eventTypeId: Int,
    val pumpTimeSeconds: Long,
)

data class PumpHardwareInfo(
    val serialNumber: Long,
    val modelNumber: Long,
    val partNumber: Long,
    val pumpRev: String,
    val armSwVer: Long,
    val mspSwVer: Long,
    val configABits: Long,
    val configBBits: Long,
    val pcbaSn: Long,
    val pcbaRev: String,
    val pumpFeatures: Map<String, Boolean>,
)

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
}
