package com.glycemicgpt.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["timestampMs"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userMessage: String,
    val aiResponse: String,
    val disclaimer: String,
    val pumpContext: String,
    val timestampMs: Long,
)
