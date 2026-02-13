package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.local.dao.PumpDao
import com.glycemicgpt.mobile.data.local.entity.BolusEventEntity
import com.glycemicgpt.mobile.data.local.entity.CgmReadingEntity
import com.glycemicgpt.mobile.data.local.entity.IoBReadingEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpContextBuilderTest {

    private val pumpDao = mockk<PumpDao>(relaxed = true)
    private val builder = PumpContextBuilder(pumpDao)

    @Test
    fun `builds context with all data present`() = runTest {
        val now = System.currentTimeMillis()
        coEvery { pumpDao.getIoBSince(any()) } returns listOf(
            IoBReadingEntity(iob = 2.5f, timestampMs = now),
        )
        coEvery { pumpDao.getLatestCgm() } returns CgmReadingEntity(
            glucoseMgDl = 120,
            trendArrow = "FLAT",
            timestampMs = now,
        )
        coEvery { pumpDao.getBolusesSince(any()) } returns listOf(
            BolusEventEntity(
                units = 1.5f,
                isAutomated = false,
                isCorrection = false,
                timestampMs = now,
            ),
            BolusEventEntity(
                units = 2.0f,
                isAutomated = false,
                isCorrection = false,
                timestampMs = now - 1000,
            ),
        )

        val result = builder.buildContext()

        assertTrue(result.contextPrefix.contains("IoB 2.50u"))
        assertTrue(result.contextPrefix.contains("BG 120 mg/dL FLAT"))
        assertTrue(result.contextPrefix.contains("Recent boluses: 3.5u total (2 deliveries)"))
        assertTrue(result.snapshot.startsWith("[Current pump context:"))
    }

    @Test
    fun `returns empty prefix when no data available`() = runTest {
        coEvery { pumpDao.getIoBSince(any()) } returns emptyList()
        coEvery { pumpDao.getLatestCgm() } returns null
        coEvery { pumpDao.getBolusesSince(any()) } returns emptyList()

        val result = builder.buildContext()

        assertEquals("", result.contextPrefix)
        assertEquals("No pump data available", result.snapshot)
    }

    @Test
    fun `builds context with IoB only`() = runTest {
        val now = System.currentTimeMillis()
        coEvery { pumpDao.getIoBSince(any()) } returns listOf(
            IoBReadingEntity(iob = 1.2f, timestampMs = now),
        )
        coEvery { pumpDao.getLatestCgm() } returns null
        coEvery { pumpDao.getBolusesSince(any()) } returns emptyList()

        val result = builder.buildContext()

        assertTrue(result.contextPrefix.contains("IoB 1.20u"))
        assertTrue(!result.contextPrefix.contains("BG"))
        assertTrue(!result.contextPrefix.contains("boluses"))
    }

    @Test
    fun `builds context with CGM only`() = runTest {
        val now = System.currentTimeMillis()
        coEvery { pumpDao.getIoBSince(any()) } returns emptyList()
        coEvery { pumpDao.getLatestCgm() } returns CgmReadingEntity(
            glucoseMgDl = 85,
            trendArrow = "FORTY_FIVE_DOWN",
            timestampMs = now,
        )
        coEvery { pumpDao.getBolusesSince(any()) } returns emptyList()

        val result = builder.buildContext()

        assertTrue(result.contextPrefix.contains("BG 85 mg/dL FORTY_FIVE_DOWN"))
        assertTrue(!result.contextPrefix.contains("IoB"))
    }
}
