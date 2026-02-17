package com.glycemicgpt.mobile.data.auth

import com.glycemicgpt.mobile.data.remote.BaseUrlInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a dedicated OkHttpClient for token refresh calls.
 * This client only has the BaseUrlInterceptor (no auth/token-refresh interceptors)
 * to avoid recursion during token refresh.
 */
@Singleton
class RefreshClientProvider @Inject constructor(
    private val baseUrlInterceptor: BaseUrlInterceptor,
) {
    val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
