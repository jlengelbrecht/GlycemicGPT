package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.local.dao.ChatDao
import com.glycemicgpt.mobile.data.local.entity.ChatMessageEntity
import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.ChatRequest
import com.glycemicgpt.mobile.data.remote.dto.ChatResponse
import com.glycemicgpt.mobile.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: GlycemicGptApi,
    private val chatDao: ChatDao,
    private val pumpContextBuilder: PumpContextBuilder,
) {
    /**
     * Raw API call without persistence -- used by WearChatRelayService.
     */
    suspend fun sendMessage(message: String): Result<ChatResponse> {
        return try {
            val response = api.sendChatMessage(ChatRequest(message = message))
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a message with pump context prepended, persists the exchange to Room,
     * and trims history to 50 messages.
     */
    suspend fun sendMessageWithContext(userMessage: String): Result<ChatMessage> {
        val pumpContext = pumpContextBuilder.buildContext()
        val fullMessage = "${pumpContext.contextPrefix}$userMessage"

        val apiResult = sendMessage(fullMessage)
        if (apiResult.isFailure) {
            return Result.failure(apiResult.exceptionOrNull()!!)
        }
        val response = apiResult.getOrThrow()

        return try {
            val now = System.currentTimeMillis()
            val entity = ChatMessageEntity(
                userMessage = userMessage,
                aiResponse = response.response,
                disclaimer = response.disclaimer,
                pumpContext = pumpContext.snapshot,
                timestampMs = now,
            )
            val insertedId = chatDao.insert(entity)
            chatDao.trimToLimit()

            Result.success(
                ChatMessage(
                    id = insertedId,
                    userMessage = userMessage,
                    aiResponse = response.response,
                    disclaimer = response.disclaimer,
                    pumpContext = pumpContext.snapshot,
                    timestamp = Instant.ofEpochMilli(now),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRecentMessages(): Flow<List<ChatMessage>> {
        return chatDao.observeRecentMessages().map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    userMessage = entity.userMessage,
                    aiResponse = entity.aiResponse,
                    disclaimer = entity.disclaimer,
                    pumpContext = entity.pumpContext,
                    timestamp = Instant.ofEpochMilli(entity.timestampMs),
                )
            }.reversed() // DB returns DESC, UI wants chronological
        }
    }
}
