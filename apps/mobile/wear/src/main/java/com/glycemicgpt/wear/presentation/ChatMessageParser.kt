package com.glycemicgpt.wear.presentation

import org.json.JSONObject

object ChatMessageParser {

    data class ParsedResponse(
        val response: String,
        val disclaimer: String,
    )

    fun parse(bytes: ByteArray): ParsedResponse? {
        return try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            ParsedResponse(
                response = json.getString("response"),
                disclaimer = json.optString("disclaimer", ""),
            )
        } catch (_: Exception) {
            null
        }
    }
}
