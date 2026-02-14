package com.glycemicgpt.mobile.data.remote

import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenRefreshInterceptorTest {

    private val authTokenStore = mockk<AuthTokenStore>(relaxed = true)
    private val baseUrlInterceptor = mockk<BaseUrlInterceptor>(relaxed = true)
    private val moshi = Moshi.Builder().build()

    private fun createInterceptor() =
        TokenRefreshInterceptor(authTokenStore, baseUrlInterceptor, moshi)

    private fun buildResponse(code: Int, request: Request): Response =
        Response.Builder()
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Unauthorized")
            .request(request)
            .body("".toResponseBody())
            .build()

    @Test
    fun `non-401 responses pass through unchanged`() {
        val interceptor = createInterceptor()
        val request = Request.Builder().url("http://localhost/api/test").build()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns buildResponse(200, request)
        }

        val response = interceptor.intercept(chain)
        assertEquals(200, response.code)
    }

    @Test
    fun `401 without refresh token passes through`() {
        every { authTokenStore.getRefreshToken() } returns null

        val interceptor = createInterceptor()
        val request = Request.Builder()
            .url("http://localhost/api/test")
            .header("Authorization", "Bearer expired-token")
            .build()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns buildResponse(401, request)
        }

        val response = interceptor.intercept(chain)
        assertEquals(401, response.code)
    }

    @Test
    fun `401 on refresh endpoint itself does not retry`() {
        every { authTokenStore.getRefreshToken() } returns "some-refresh-token"

        val interceptor = createInterceptor()
        val request = Request.Builder()
            .url("http://localhost/api/auth/mobile/refresh")
            .build()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns buildResponse(401, request)
        }

        val response = interceptor.intercept(chain)
        assertEquals(401, response.code)
        // Should NOT attempt refresh for the refresh endpoint itself
        verify(exactly = 0) { authTokenStore.clearToken() }
    }
}
