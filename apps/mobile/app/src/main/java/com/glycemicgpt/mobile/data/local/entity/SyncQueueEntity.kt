package com.glycemicgpt.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local sync queue for pump events waiting to be pushed to the backend.
 *
 * Each row represents a single pump event that needs to be synced.
 * The queue processor picks up PENDING items in batches, marks them SENDING,
 * then either deletes (on success) or marks FAILED (on error).
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status", "createdAtMs"]),
    ],
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val eventTimestampMs: Long,
    /** JSON-serialized PumpEventDto payload. */
    val payload: String,
    val status: String = STATUS_PENDING,
    val retryCount: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastAttemptMs: Long = 0L,
    val errorMessage: String? = null,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENDING = "sending"
        const val STATUS_FAILED = "failed"
    }
}
