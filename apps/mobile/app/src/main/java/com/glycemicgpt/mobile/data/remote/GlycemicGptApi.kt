package com.glycemicgpt.mobile.data.remote

import com.glycemicgpt.mobile.data.remote.dto.ChatRequest
import com.glycemicgpt.mobile.data.remote.dto.ChatResponse
import com.glycemicgpt.mobile.data.remote.dto.HealthResponse
import com.glycemicgpt.mobile.data.remote.dto.LoginRequest
import com.glycemicgpt.mobile.data.remote.dto.LoginResponse
import com.glycemicgpt.mobile.data.remote.dto.PumpPushRequest
import com.glycemicgpt.mobile.data.remote.dto.PumpPushResponse
import com.glycemicgpt.mobile.data.remote.dto.TandemUploadSettingsRequest
import com.glycemicgpt.mobile.data.remote.dto.TandemUploadStatus
import com.glycemicgpt.mobile.data.remote.dto.TandemUploadTriggerResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * Retrofit interface for the GlycemicGPT backend API.
 */
interface GlycemicGptApi {

    @GET("/health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("/api/auth/mobile/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/integrations/pump/push")
    suspend fun pushPumpEvents(@Body request: PumpPushRequest): Response<PumpPushResponse>

    @GET("/api/integrations/tandem/cloud-upload/status")
    suspend fun getTandemUploadStatus(): Response<TandemUploadStatus>

    @PUT("/api/integrations/tandem/cloud-upload/settings")
    suspend fun updateTandemUploadSettings(
        @Body request: TandemUploadSettingsRequest,
    ): Response<TandemUploadStatus>

    @POST("/api/integrations/tandem/cloud-upload/trigger")
    suspend fun triggerTandemUpload(): Response<TandemUploadTriggerResponse>

    @POST("/api/ai/chat")
    suspend fun sendChatMessage(@Body request: ChatRequest): Response<ChatResponse>
}
