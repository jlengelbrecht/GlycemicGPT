package com.glycemicgpt.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.glycemicgpt.mobile.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    @Insert
    suspend fun enqueue(entity: SyncQueueEntity)

    @Query(
        """
        SELECT * FROM sync_queue
        WHERE status = 'pending'
           OR (status = 'failed' AND retryCount < :maxRetries
               AND lastAttemptMs < :nowMs - (2000 * (1 << retryCount)))
        ORDER BY createdAtMs ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingBatch(
        limit: Int = 50,
        maxRetries: Int = 5,
        nowMs: Long = System.currentTimeMillis(),
    ): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'sending', lastAttemptMs = :nowMs WHERE id IN (:ids)")
    suspend fun markSending(ids: List<Long>, nowMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteSent(ids: List<Long>)

    @Query(
        """
        UPDATE sync_queue
        SET status = 'failed', retryCount = retryCount + 1, lastAttemptMs = :nowMs, errorMessage = :error
        WHERE id IN (:ids)
        """
    )
    suspend fun markFailed(
        ids: List<Long>,
        error: String?,
        nowMs: Long = System.currentTimeMillis(),
    )

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status != 'sending'")
    fun observePendingCount(): Flow<Int>

    /** Count all items in the sync queue. */
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun countAll(): Int

    /** Delete the oldest pending items to keep the queue under the given limit. */
    @Query(
        """
        DELETE FROM sync_queue
        WHERE id IN (
            SELECT id FROM sync_queue
            WHERE status = 'pending'
            ORDER BY createdAtMs ASC
            LIMIT :excess
        )
        """
    )
    suspend fun pruneOldest(excess: Int)

    /** Delete items that have exceeded max retries or are older than the given cutoff. */
    @Query("DELETE FROM sync_queue WHERE retryCount >= :maxRetries OR createdAtMs < :cutoffMs")
    suspend fun cleanup(maxRetries: Int = 5, cutoffMs: Long)

    /** Reset orphaned 'sending' items back to 'pending' (e.g. after crash/restart). */
    @Query("UPDATE sync_queue SET status = 'pending' WHERE status = 'sending' AND lastAttemptMs < :staleCutoffMs")
    suspend fun resetStaleSending(staleCutoffMs: Long)
}
