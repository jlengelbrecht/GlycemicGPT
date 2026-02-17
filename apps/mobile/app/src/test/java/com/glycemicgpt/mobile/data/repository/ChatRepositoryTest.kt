package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.ChatRequest
import com.glycemicgpt.mobile.data.remote.dto.ChatResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import com.glycemicgpt.mobile.data.remote.dto.AiProviderStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatRepositoryTest {

    private val api = mockk<GlycemicGptApi>()
    private val chatApi = mockk<GlycemicGptApi>()
    private val repository = ChatRepository(api, chatApi)

    @Test
    fun `sendMessage returns success on 200`() = runTest {
        val chatResponse = ChatResponse(
            response = "Your levels look stable.",
            disclaimer = "Not medical advice.",
        )
        coEvery { chatApi.sendChatMessage(any()) } returns Response.success(chatResponse)

        val result = repository.sendMessage("How are my levels?")

        assertTrue(result.isSuccess)
        assertEquals("Your levels look stable.", result.getOrNull()!!.response)
        assertEquals("Not medical advice.", result.getOrNull()!!.disclaimer)
        coVerify { chatApi.sendChatMessage(ChatRequest(message = "How are my levels?")) }
    }

    @Test
    fun `sendMessage returns failure on HTTP error`() = runTest {
        coEvery { chatApi.sendChatMessage(any()) } returns Response.error(
            500,
            "Internal server error".toResponseBody(),
        )

        val result = repository.sendMessage("test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `sendMessage returns failure on null body`() = runTest {
        coEvery { chatApi.sendChatMessage(any()) } returns Response.success(null)

        val result = repository.sendMessage("test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Empty response body"))
    }

    @Test
    fun `sendMessage returns failure on network exception`() = runTest {
        coEvery { chatApi.sendChatMessage(any()) } throws java.io.IOException("No route to host")

        val result = repository.sendMessage("test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("No route to host"))
    }

    // --- checkProviderConfigured tests ---

    @Test
    fun `checkProviderConfigured returns success on 200`() = runTest {
        val providerResponse = AiProviderStatusResponse(
            providerType = "openai",
            status = "active",
        )
        coEvery { api.getAiProvider() } returns Response.success(providerResponse)

        val result = repository.checkProviderConfigured()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `checkProviderConfigured returns failure on 404`() = runTest {
        coEvery { api.getAiProvider() } returns Response.error(
            404,
            "Not found".toResponseBody(),
        )

        val result = repository.checkProviderConfigured()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("No AI provider"))
    }

    @Test
    fun `checkProviderConfigured returns failure on 500`() = runTest {
        coEvery { api.getAiProvider() } returns Response.error(
            500,
            "Internal server error".toResponseBody(),
        )

        val result = repository.checkProviderConfigured()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `checkProviderConfigured returns failure on network exception`() = runTest {
        coEvery { api.getAiProvider() } throws java.io.IOException("Connection refused")

        val result = repository.checkProviderConfigured()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Connection refused"))
    }
}
