package com.glycemicgpt.mobile.domain.model

import java.time.Instant

data class ChatMessage(
    val id: Long = 0,
    val userMessage: String,
    val aiResponse: String,
    val disclaimer: String,
    val pumpContext: String,
    val timestamp: Instant,
)
