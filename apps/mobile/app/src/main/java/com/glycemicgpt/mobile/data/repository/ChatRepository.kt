package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.ChatRequest
import com.glycemicgpt.mobile.data.remote.dto.ChatResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: GlycemicGptApi,
) {
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
}
