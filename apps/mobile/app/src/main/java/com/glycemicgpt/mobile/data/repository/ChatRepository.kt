package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.ChatRequest
import com.glycemicgpt.mobile.data.remote.dto.ChatResponse
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

class NoProviderException(message: String) : Exception(message)

@Singleton
class ChatRepository @Inject constructor(
    private val api: GlycemicGptApi,
    @Named("chat") private val chatApi: GlycemicGptApi,
) {
    suspend fun sendMessage(message: String): Result<ChatResponse> {
        return try {
            // Use chatApi which has a 90s read timeout for LLM inference
            val response = chatApi.sendChatMessage(ChatRequest(message = message))
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkProviderConfigured(): Result<Unit> {
        return try {
            val response = api.getAiProvider()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else if (response.code() == 404) {
                Result.failure(NoProviderException("No AI provider configured"))
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
