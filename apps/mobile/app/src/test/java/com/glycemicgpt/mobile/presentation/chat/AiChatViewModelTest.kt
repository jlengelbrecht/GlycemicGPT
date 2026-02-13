package com.glycemicgpt.mobile.presentation.chat

import com.glycemicgpt.mobile.data.local.AppSettingsStore
import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.glycemicgpt.mobile.data.repository.ChatRepository
import com.glycemicgpt.mobile.domain.model.ChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val chatRepository = mockk<ChatRepository>(relaxed = true) {
        every { observeRecentMessages() } returns messagesFlow
    }

    private val appSettingsStore = mockk<AppSettingsStore>(relaxed = true) {
        every { ttsEnabled } returns true
    }

    private val authTokenStore = mockk<AuthTokenStore>(relaxed = true) {
        every { isLoggedIn() } returns true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AiChatViewModel(chatRepository, appSettingsStore, authTokenStore)

    @Test
    fun `initial state has empty text input and not sending`() = runTest {
        val vm = createViewModel()

        assertEquals("", vm.uiState.value.textInput)
        assertFalse(vm.uiState.value.isSending)
        assertNull(vm.uiState.value.error)
        assertEquals(ListeningState.IDLE, vm.uiState.value.listeningState)
        assertEquals(SpeakingState.IDLE, vm.uiState.value.speakingState)
        assertTrue(vm.uiState.value.ttsEnabled)
    }

    @Test
    fun `updateTextInput updates state`() = runTest {
        val vm = createViewModel()

        vm.updateTextInput("hello")
        assertEquals("hello", vm.uiState.value.textInput)
    }

    @Test
    fun `sendTextMessage calls repository and clears input`() = runTest {
        val chatMessage = ChatMessage(
            id = 1,
            userMessage = "hello",
            aiResponse = "response",
            disclaimer = "disclaimer",
            pumpContext = "context",
            timestamp = Instant.now(),
        )
        coEvery { chatRepository.sendMessageWithContext("hello") } returns Result.success(chatMessage)

        val vm = createViewModel()
        vm.updateTextInput("hello")
        vm.sendTextMessage()
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.textInput)
        assertFalse(vm.uiState.value.isSending)
        assertNull(vm.uiState.value.error)
        coVerify { chatRepository.sendMessageWithContext("hello") }
    }

    @Test
    fun `sendTextMessage sets error on failure`() = runTest {
        coEvery { chatRepository.sendMessageWithContext("fail") } returns
            Result.failure(Exception("Network error"))

        val vm = createViewModel()
        vm.updateTextInput("fail")
        vm.sendTextMessage()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSending)
        assertEquals("Network error", vm.uiState.value.error)
    }

    @Test
    fun `sendTextMessage ignores empty input`() = runTest {
        val vm = createViewModel()

        vm.updateTextInput("   ")
        vm.sendTextMessage()
        advanceUntilIdle()

        coVerify(exactly = 0) { chatRepository.sendMessageWithContext(any()) }
    }

    @Test
    fun `sendTextMessage rejects message exceeding max length`() = runTest {
        val vm = createViewModel()
        val longText = "a".repeat(501)
        vm.updateTextInput(longText)
        vm.sendTextMessage()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.error!!.contains("too long"))
        coVerify(exactly = 0) { chatRepository.sendMessageWithContext(any()) }
    }

    @Test
    fun `sendTextMessage errors when not logged in`() = runTest {
        every { authTokenStore.isLoggedIn() } returns false

        val vm = createViewModel()
        vm.updateTextInput("hello")
        vm.sendTextMessage()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.error!!.contains("Sign in"))
        coVerify(exactly = 0) { chatRepository.sendMessageWithContext(any()) }
    }

    @Test
    fun `onSpeechResult triggers send`() = runTest {
        val chatMessage = ChatMessage(
            id = 1,
            userMessage = "voice input",
            aiResponse = "response",
            disclaimer = "disclaimer",
            pumpContext = "",
            timestamp = Instant.now(),
        )
        coEvery { chatRepository.sendMessageWithContext("voice input") } returns Result.success(chatMessage)

        val vm = createViewModel()
        vm.onSpeechResult("voice input")
        advanceUntilIdle()

        coVerify { chatRepository.sendMessageWithContext("voice input") }
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun `onSpeechResult ignores blank text`() = runTest {
        val vm = createViewModel()
        vm.onSpeechResult("  ")
        advanceUntilIdle()

        coVerify(exactly = 0) { chatRepository.sendMessageWithContext(any()) }
    }

    @Test
    fun `onSpeechResult truncates to max length`() = runTest {
        val chatMessage = ChatMessage(
            id = 1,
            userMessage = "a".repeat(500),
            aiResponse = "response",
            disclaimer = "",
            pumpContext = "",
            timestamp = Instant.now(),
        )
        coEvery { chatRepository.sendMessageWithContext(any()) } returns Result.success(chatMessage)

        val vm = createViewModel()
        vm.onSpeechResult("a".repeat(600))
        advanceUntilIdle()

        coVerify { chatRepository.sendMessageWithContext("a".repeat(500)) }
    }

    @Test
    fun `TTS enabled from settings store`() = runTest {
        every { appSettingsStore.ttsEnabled } returns false
        val vm = createViewModel()

        assertFalse(vm.uiState.value.ttsEnabled)
    }

    @Test
    fun `messages flow emits from repository`() = runTest {
        val vm = createViewModel()

        val collected = mutableListOf<List<ChatMessage>>()
        val job = backgroundScope.launch(testDispatcher) {
            vm.messages.collect { collected.add(it) }
        }

        assertTrue(vm.messages.value.isEmpty())

        val msg = ChatMessage(
            id = 1,
            userMessage = "test",
            aiResponse = "reply",
            disclaimer = "",
            pumpContext = "",
            timestamp = Instant.now(),
        )
        messagesFlow.value = listOf(msg)

        assertEquals(1, vm.messages.value.size)
        assertEquals("test", vm.messages.value[0].userMessage)

        job.cancel()
    }

    @Test
    fun `clearError resets error to null`() = runTest {
        coEvery { chatRepository.sendMessageWithContext("x") } returns
            Result.failure(Exception("err"))

        val vm = createViewModel()
        vm.updateTextInput("x")
        vm.sendTextMessage()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        vm.clearError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `latestAiResponse set on successful send with TTS enabled`() = runTest {
        val chatMessage = ChatMessage(
            id = 1,
            userMessage = "hi",
            aiResponse = "AI says hello",
            disclaimer = "",
            pumpContext = "",
            timestamp = Instant.now(),
        )
        coEvery { chatRepository.sendMessageWithContext("hi") } returns Result.success(chatMessage)

        val vm = createViewModel()
        vm.updateTextInput("hi")
        vm.sendTextMessage()
        advanceUntilIdle()

        assertEquals("AI says hello", vm.latestAiResponse.value)

        vm.consumeLatestAiResponse()
        assertNull(vm.latestAiResponse.value)
    }

    @Test
    fun `latestAiResponse not set when TTS disabled`() = runTest {
        every { appSettingsStore.ttsEnabled } returns false
        val chatMessage = ChatMessage(
            id = 1,
            userMessage = "hi",
            aiResponse = "AI says hello",
            disclaimer = "",
            pumpContext = "",
            timestamp = Instant.now(),
        )
        coEvery { chatRepository.sendMessageWithContext("hi") } returns Result.success(chatMessage)

        val vm = createViewModel()
        vm.updateTextInput("hi")
        vm.sendTextMessage()
        advanceUntilIdle()

        assertNull(vm.latestAiResponse.value)
    }
}
