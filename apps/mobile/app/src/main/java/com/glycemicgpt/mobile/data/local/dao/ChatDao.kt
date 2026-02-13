package com.glycemicgpt.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.glycemicgpt.mobile.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY timestampMs DESC LIMIT 50")
    fun observeRecentMessages(): Flow<List<ChatMessageEntity>>

    @Query(
        """
        DELETE FROM chat_messages WHERE id NOT IN (
            SELECT id FROM chat_messages ORDER BY timestampMs DESC LIMIT 50
        )
        """,
    )
    suspend fun trimToLimit()

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
