package com.glycemicgpt.mobile.ble.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TandemAuthenticatorTest {

    private val authenticator = TandemAuthenticator()

    @Test
    fun `initial state is IDLE`() = runTest {
        assertEquals(AuthState.IDLE, authenticator.authState.first())
    }

    @Test
    fun `buildCentralChallengeRequest transitions to CHALLENGE_SENT`() = runTest {
        val chunks = authenticator.buildCentralChallengeRequest(txId = 1)
        assertTrue(chunks.isNotEmpty())
        assertEquals(AuthState.CHALLENGE_SENT, authenticator.authState.first())
    }

    @Test
    fun `processChallengeResponse returns null when not in CHALLENGE_SENT state`() {
        // Still in IDLE state
        val result = authenticator.processChallengeResponse(
            responseCargo = byteArrayOf(0x01, 0x02, 0x03),
            code = "TESTCODE12345678",
            txId = 2,
        )
        assertNull(result)
    }

    @Test
    fun `processChallengeResponse transitions to RESPONSE_SENT`() = runTest {
        authenticator.buildCentralChallengeRequest(txId = 1)
        val chunks = authenticator.processChallengeResponse(
            responseCargo = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            code = "TESTCODE12345678",
            txId = 2,
        )
        assertNotNull(chunks)
        assertTrue(chunks!!.isNotEmpty())
        assertEquals(AuthState.RESPONSE_SENT, authenticator.authState.first())
    }

    @Test
    fun `processPumpChallengeResponse returns false when not in RESPONSE_SENT state`() = runTest {
        val result = authenticator.processPumpChallengeResponse(byteArrayOf(0x00))
        assertFalse(result)
        assertEquals(AuthState.FAILED, authenticator.authState.first())
    }

    @Test
    fun `processPumpChallengeResponse succeeds with zero status byte`() = runTest {
        // Walk through full handshake
        authenticator.buildCentralChallengeRequest(txId = 1)
        authenticator.processChallengeResponse(
            responseCargo = ByteArray(8) { 0x42 },
            code = "TESTCODE12345678",
            txId = 2,
        )

        val success = authenticator.processPumpChallengeResponse(byteArrayOf(0x00))
        assertTrue(success)
        assertEquals(AuthState.AUTHENTICATED, authenticator.authState.first())
    }

    @Test
    fun `processPumpChallengeResponse fails with non-zero status byte`() = runTest {
        authenticator.buildCentralChallengeRequest(txId = 1)
        authenticator.processChallengeResponse(
            responseCargo = ByteArray(8) { 0x42 },
            code = "TESTCODE12345678",
            txId = 2,
        )

        val success = authenticator.processPumpChallengeResponse(byteArrayOf(0x01))
        assertFalse(success)
        assertEquals(AuthState.FAILED, authenticator.authState.first())
    }

    @Test
    fun `processPumpChallengeResponse fails with empty cargo`() = runTest {
        authenticator.buildCentralChallengeRequest(txId = 1)
        authenticator.processChallengeResponse(
            responseCargo = ByteArray(8) { 0x42 },
            code = "TESTCODE12345678",
            txId = 2,
        )

        val success = authenticator.processPumpChallengeResponse(byteArrayOf())
        assertFalse(success)
        assertEquals(AuthState.FAILED, authenticator.authState.first())
    }

    @Test
    fun `reset returns to IDLE state`() = runTest {
        authenticator.buildCentralChallengeRequest(txId = 1)
        assertEquals(AuthState.CHALLENGE_SENT, authenticator.authState.first())

        authenticator.reset()
        assertEquals(AuthState.IDLE, authenticator.authState.first())
    }
}
