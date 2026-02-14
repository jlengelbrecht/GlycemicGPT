package com.glycemicgpt.mobile.data.remote

import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.glycemicgpt.mobile.data.remote.dto.RefreshTokenRequest
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that transparently refreshes expired access tokens.
 *
 * When a request receives a 401 response and a refresh token is available,
 * this interceptor:
 * 1. Calls the /api/auth/mobile/refresh endpoint (using a separate OkHttpClient)
 * 2. On success: saves new tokens and retries the original request
 * 3. On failure: clears all tokens (user must re-login)
 *
 * Uses synchronized block to prevent concurrent refresh attempts.
 */
@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val authTokenStore: AuthTokenStore,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    private val moshi: Moshi,
) : Interceptor {

    private val lock = Any()

    // Separate OkHttpClient for refresh calls to avoid interceptor recursion
    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val response = chain.proceed(original)

        // Only attempt refresh on 401 responses
        if (response.code != 401) return response

        // Don't try to refresh the refresh endpoint itself
        if (original.url.encodedPath.contains("/api/auth/mobile/refresh")) return response

        val refreshToken = authTokenStore.getRefreshToken() ?: return response

        synchronized(lock) {
            // Double-check: another thread may have already refreshed
            val currentToken = authTokenStore.getRawToken()
            val originalAuth = original.header("Authorization")
            if (currentToken != null && originalAuth != "Bearer $currentToken") {
                // Token was already refreshed by another thread; retry with new token
                response.close()
                val retryRequest = original.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
                return chain.proceed(retryRequest)
            }

            // Attempt token refresh
            val newAccessToken = performRefresh(refreshToken)
            if (newAccessToken != null) {
                response.close()
                val retryRequest = original.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                return chain.proceed(retryRequest)
            }
        }

        return response
    }

    /**
     * Calls the refresh endpoint and saves new tokens on success.
     * Returns the new access token, or null on failure.
     */
    private fun performRefresh(refreshToken: String): String? {
        return try {
            val body = RefreshTokenRequest(refreshToken = refreshToken)
            val adapter = moshi.adapter(RefreshTokenRequest::class.java)
            val json = adapter.toJson(body)

            val request = Request.Builder()
                .url("http://localhost/api/auth/mobile/refresh") // BaseUrlInterceptor rewrites this
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val refreshResponse = refreshClient.newCall(request).execute()

            if (refreshResponse.isSuccessful) {
                val responseBody = refreshResponse.body?.string() ?: return null
                val loginAdapter = moshi.adapter(
                    com.glycemicgpt.mobile.data.remote.dto.LoginResponse::class.java,
                )
                val loginResponse = loginAdapter.fromJson(responseBody) ?: return null

                val expiresAtMs = System.currentTimeMillis() + (loginResponse.expiresIn * 1000L)
                authTokenStore.saveCredentials(
                    authTokenStore.getBaseUrl() ?: "",
                    loginResponse.accessToken,
                    expiresAtMs,
                    loginResponse.user.email,
                )
                authTokenStore.saveRefreshToken(loginResponse.refreshToken)

                Timber.d("Token refreshed successfully")
                loginResponse.accessToken
            } else {
                Timber.w("Token refresh failed with HTTP ${refreshResponse.code}")
                authTokenStore.clearToken()
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Token refresh failed")
            authTokenStore.clearToken()
            null
        }
    }
}
