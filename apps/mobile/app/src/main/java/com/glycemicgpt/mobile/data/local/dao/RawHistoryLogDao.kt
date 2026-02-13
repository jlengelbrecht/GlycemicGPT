package com.glycemicgpt.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.glycemicgpt.mobile.data.local.entity.RawHistoryLogEntity

@Dao
interface RawHistoryLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<RawHistoryLogEntity>)

    @Query("SELECT * FROM raw_history_logs WHERE sentToBackend = 0 ORDER BY sequenceNumber LIMIT :limit")
    suspend fun getUnsent(limit: Int = 100): List<RawHistoryLogEntity>

    @Query("UPDATE raw_history_logs SET sentToBackend = 1 WHERE id IN (:ids)")
    suspend fun markSent(ids: List<Long>)

    @Query("DELETE FROM raw_history_logs WHERE sentToBackend = 1 AND createdAtMs < :cutoffMs")
    suspend fun cleanup(cutoffMs: Long)

    @Query("SELECT MAX(sequenceNumber) FROM raw_history_logs")
    suspend fun getMaxSequenceNumber(): Int?
}
