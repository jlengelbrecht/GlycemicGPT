package com.glycemicgpt.mobile.data.remote

import com.glycemicgpt.mobile.data.remote.dto.LoginRequest
import com.glycemicgpt.mobile.data.remote.dto.LoginResponse
import com.glycemicgpt.mobile.data.remote.dto.PumpPushRequest
import com.glycemicgpt.mobile.data.remote.dto.PumpPushResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the GlycemicGPT backend API.
 */
interface GlycemicGptApi {

    @POST("/api/auth/mobile/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/integrations/pump/push")
    suspend fun pushPumpEvents(@Body request: PumpPushRequest): Response<PumpPushResponse>
}
