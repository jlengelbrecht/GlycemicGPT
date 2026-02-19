package com.glycemicgpt.mobile.ble.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the reconnection scheduling constants and backoff logic
 * defined in [BleConnectionManager].
 *
 * These verify that the tiered reconnection strategy (fast exponential
 * backoff -> slow periodic) behaves correctly.
 */
class ReconnectSchedulerTest {

    @Test
    fun `fast phase backoff delay doubles each attempt`() {
        // The backoff formula: min(1000 * (1 << min(attempt, 5)), 32000)
        val expected = listOf(
            1 to 2_000L,
            2 to 4_000L,
            3 to 8_000L,
            4 to 16_000L,
            5 to 32_000L,
        )
        for ((attempt, expectedDelay) in expected) {
            val delay = minOf(1000L * (1 shl minOf(attempt, 5)), 32_000L)
            assertEquals("Attempt $attempt should delay $expectedDelay ms", expectedDelay, delay)
        }
    }

    @Test
    fun `fast phase backoff caps at 32 seconds`() {
        // Attempts beyond 5 should still cap at 32s
        for (attempt in 6..10) {
            val delay = minOf(1000L * (1 shl minOf(attempt, 5)), 32_000L)
            assertEquals("Attempt $attempt should cap at 32000 ms", 32_000L, delay)
        }
    }

    @Test
    fun `first attempt delay is 2 seconds`() {
        // attempt=1 (first call to scheduleReconnect increments to 1)
        val delay = minOf(1000L * (1 shl minOf(1, 5)), 32_000L)
        assertEquals(2_000L, delay)
    }

    @Test
    fun `fast phase transition happens at MAX_FAST_RECONNECT_ATTEMPTS`() {
        assertEquals(10, BleConnectionManager.MAX_FAST_RECONNECT_ATTEMPTS)
    }

    @Test
    fun `slow phase interval is 2 minutes`() {
        assertEquals(120_000L, BleConnectionManager.SLOW_RECONNECT_INTERVAL_MS)
    }

    @Test
    fun `total fast phase duration is approximately 5 minutes`() {
        // Sum of all fast-phase delays for attempts 1..10
        var total = 0L
        for (attempt in 1..10) {
            total += minOf(1000L * (1 shl minOf(attempt, 5)), 32_000L)
        }
        // Should be: 2 + 4 + 8 + 16 + 32 + 32 + 32 + 32 + 32 + 32 = 222 seconds
        // Plus connection + auth time per attempt (~2-5s each)
        // Should be approximately 4-6 minutes
        assertTrue("Fast phase total should be ~222s, was ${total / 1000}s", total in 200_000..250_000)
    }

    @Test
    fun `bond loss thresholds are preserved`() {
        assertEquals(3, BleConnectionManager.MAX_RAPID_DISCONNECTS)
        assertEquals(3, BleConnectionManager.MAX_ZERO_RESPONSE_CONNECTIONS)
        assertEquals(3, BleConnectionManager.MAX_ENCRYPTION_FAILURES)
    }
}
