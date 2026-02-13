package com.glycemicgpt.mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Int,
    val user: UserDto,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String,
    val role: String,
)

@JsonClass(generateAdapter = true)
data class PumpEventDto(
    @Json(name = "event_type") val eventType: String,
    @Json(name = "event_timestamp") val eventTimestamp: Instant,
    val units: Float? = null,
    @Json(name = "duration_minutes") val durationMinutes: Int? = null,
    @Json(name = "is_automated") val isAutomated: Boolean = false,
    @Json(name = "control_iq_mode") val controlIqMode: String? = null,
    @Json(name = "basal_adjustment_pct") val basalAdjustmentPct: Float? = null,
    @Json(name = "iob_at_event") val iobAtEvent: Float? = null,
    @Json(name = "bg_at_event") val bgAtEvent: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PumpPushRequest(
    val events: List<PumpEventDto>,
    val source: String = "mobile",
)

@JsonClass(generateAdapter = true)
data class PumpPushResponse(
    val accepted: Int,
    val duplicates: Int,
)
