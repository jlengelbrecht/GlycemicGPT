package com.glycemicgpt.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores raw BLE history log bytes received from the Tandem pump.
 *
 * These raw bytes are sent to the backend for Tandem cloud upload,
 * preserving the exact binary format required by Tandem's upload API.
 * The [sequenceNumber] is unique per pump and used to deduplicate.
 */
@Entity(
    tableName = "raw_history_logs",
    indices = [Index(value = ["sequenceNumber"], unique = true)],
)
data class RawHistoryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sequenceNumber: Int,
    val rawBytesB64: String,
    val eventTypeId: Int,
    val pumpTimeSeconds: Long,
    val sentToBackend: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
)
