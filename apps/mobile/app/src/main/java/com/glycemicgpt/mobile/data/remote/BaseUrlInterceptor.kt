package com.glycemicgpt.mobile.data.remote

import com.glycemicgpt.mobile.data.local.AuthTokenStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that rewrites the request URL to use the dynamic
 * base URL from [AuthTokenStore]. This allows the server address to be
 * configured at runtime (self-hosted deployments).
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val authTokenStore: AuthTokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val baseUrl = authTokenStore.getBaseUrl()
            ?: return chain.proceed(original)

        val parsed = baseUrl.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val newUrl = original.url.newBuilder()
            .scheme(parsed.scheme)
            .host(parsed.host)
            .port(parsed.port)
            .build()

        val request = original.newBuilder()
            .url(newUrl)
            .build()
        return chain.proceed(request)
    }
}
