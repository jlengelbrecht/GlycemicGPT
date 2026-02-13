package com.glycemicgpt.mobile.data.remote

import com.glycemicgpt.mobile.data.local.AuthTokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches the Bearer token to every request.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authTokenStore: AuthTokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = authTokenStore.getToken()
            ?: return chain.proceed(original)

        val request = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
