package com.glycemicgpt.mobile.data.remote

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Moshi adapter for converting between [Instant] and ISO-8601 strings.
 */
class InstantAdapter {

    @ToJson
    fun toJson(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant)

    @FromJson
    fun fromJson(value: String): Instant =
        Instant.parse(value)
}
