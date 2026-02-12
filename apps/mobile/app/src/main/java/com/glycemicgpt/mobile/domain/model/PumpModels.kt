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

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
}
