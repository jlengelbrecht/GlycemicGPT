package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.local.dao.ChatDao
import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.ChatRequest
import com.glycemicgpt.mobile.data.remote.dto.ChatResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChatRepositoryTest {

    private val api = mockk<GlycemicGptApi>()
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val pumpContextBuilder = mockk<PumpContextBuilder>(relaxed = true)
    private val repository = ChatRepository(api, chatDao, pumpContextBuilder)

    @Test
    fun `sendMessage returns success on 200`() = runTest {
        val chatResponse = ChatResponse(
            response = "Your levels look stable.",
            disclaimer = "Not medical advice.",
        )
        coEvery { api.sendChatMessage(any()) } returns Response.success(chatResponse)

        val result = repository.sendMessage("How are my levels?")

        assertTrue(result.isSuccess)
        assertEquals("Your levels look stable.", result.getOrNull()!!.response)
        assertEquals("Not medical advice.", result.getOrNull()!!.disclaimer)
        coVerify { api.sendChatMessage(ChatRequest(message = "How are my levels?")) }
    }

    @Test
    fun `sendMessage returns failure on HTTP error`() = runTest {
        coEvery { api.sendChatMessage(any()) } returns Response.error(
            500,
            "Internal server error".toResponseBody(),
        )

        val result = repository.sendMessage("test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `sendMessage returns failure on null body`() = runTest {
        coEvery { api.sendChatMessage(any()) } returns Response.success(null)

        val result = repository.sendMessage("test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Empty response body"))
    }

    @Test
    fun `sendMessage returns failure on network exception`() = runTest {
        coEvery { api.sendChatMessage(any()) } throws java.io.IOException("No route to host")

        val result = repository.sendMessage("test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("No route to host"))
    }

    @Test
    fun `sendMessageWithContext prepends pump context and persists`() = runTest {
        val pumpContext = PumpContext(
            contextPrefix = "[Current pump context: IoB 2.50u]\n\n",
            snapshot = "[Current pump context: IoB 2.50u]",
        )
        coEvery { pumpContextBuilder.buildContext() } returns pumpContext
        coEvery { api.sendChatMessage(any()) } returns Response.success(
            ChatResponse(response = "Looking good", disclaimer = "Disclaimer"),
        )

        val result = repository.sendMessageWithContext("How am I doing?")

        assertTrue(result.isSuccess)
        assertEquals("Looking good", result.getOrNull()!!.aiResponse)
        assertEquals("How am I doing?", result.getOrNull()!!.userMessage)
        assertEquals("[Current pump context: IoB 2.50u]", result.getOrNull()!!.pumpContext)

        // Verify the API was called with the full prefixed message
        coVerify {
            api.sendChatMessage(
                ChatRequest(message = "[Current pump context: IoB 2.50u]\n\nHow am I doing?"),
            )
        }
        coVerify { chatDao.insert(any()) }
        coVerify { chatDao.trimToLimit() }
    }
}
