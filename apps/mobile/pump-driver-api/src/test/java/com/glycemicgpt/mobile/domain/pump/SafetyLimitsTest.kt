package com.glycemicgpt.mobile.domain.pump

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyLimitsTest {

    @Test
    fun `default values are within absolute bounds`() {
        val limits = SafetyLimits()
        assertEquals(20, limits.minGlucoseMgDl)
        assertEquals(500, limits.maxGlucoseMgDl)
        assertEquals(25_000, limits.maxBasalRateMilliunits)
        assertEquals(25_000, limits.maxBolusDoseMilliunits)
    }

    @Test
    fun `accepts valid custom limits`() {
        val limits = SafetyLimits(
            minGlucoseMgDl = 40,
            maxGlucoseMgDl = 400,
            maxBasalRateMilliunits = 10_000,
            maxBolusDoseMilliunits = 15_000,
        )
        assertEquals(40, limits.minGlucoseMgDl)
        assertEquals(400, limits.maxGlucoseMgDl)
        assertEquals(10_000, limits.maxBasalRateMilliunits)
        assertEquals(15_000, limits.maxBolusDoseMilliunits)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects minGlucose below absolute floor`() {
        SafetyLimits(minGlucoseMgDl = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects maxGlucose above absolute ceiling`() {
        SafetyLimits(maxGlucoseMgDl = 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects min greater than or equal to max glucose`() {
        SafetyLimits(minGlucoseMgDl = 300, maxGlucoseMgDl = 300)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero basal rate`() {
        SafetyLimits(maxBasalRateMilliunits = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects basal above absolute ceiling`() {
        SafetyLimits(maxBasalRateMilliunits = 50_001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero bolus dose`() {
        SafetyLimits(maxBolusDoseMilliunits = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects bolus above absolute ceiling`() {
        SafetyLimits(maxBolusDoseMilliunits = 25_001)
    }

    // -- safeOf() factory tests ------------------------------------------------

    @Test
    fun `safeOf clamps minGlucose below absolute floor`() {
        val limits = SafetyLimits.safeOf(minGlucoseMgDl = -10)
        assertEquals(SafetyLimits.ABSOLUTE_MIN_GLUCOSE, limits.minGlucoseMgDl)
    }

    @Test
    fun `safeOf clamps maxGlucose above absolute ceiling`() {
        val limits = SafetyLimits.safeOf(maxGlucoseMgDl = 2000)
        assertEquals(SafetyLimits.ABSOLUTE_MAX_GLUCOSE, limits.maxGlucoseMgDl)
    }

    @Test
    fun `safeOf clamps basal rate to absolute bounds`() {
        val limits = SafetyLimits.safeOf(maxBasalRateMilliunits = 100_000)
        assertEquals(SafetyLimits.ABSOLUTE_MAX_BASAL_MILLIUNITS, limits.maxBasalRateMilliunits)
    }

    @Test
    fun `safeOf clamps bolus dose to absolute bounds`() {
        val limits = SafetyLimits.safeOf(maxBolusDoseMilliunits = 0)
        assertEquals(1, limits.maxBolusDoseMilliunits)
    }

    @Test
    fun `safeOf ensures min is less than max glucose`() {
        val limits = SafetyLimits.safeOf(minGlucoseMgDl = 500, maxGlucoseMgDl = 300)
        // min gets clamped to ABSOLUTE_MAX - 1 = 998, max gets clamped to min+1 = 999
        assertTrue(
            "minGlucoseMgDl (${limits.minGlucoseMgDl}) must be less than maxGlucoseMgDl (${limits.maxGlucoseMgDl})",
            limits.minGlucoseMgDl < limits.maxGlucoseMgDl,
        )
    }

    @Test
    fun `safeOf with valid values passes through unchanged`() {
        val limits = SafetyLimits.safeOf(
            minGlucoseMgDl = 40,
            maxGlucoseMgDl = 400,
            maxBasalRateMilliunits = 10_000,
            maxBolusDoseMilliunits = 15_000,
        )
        assertEquals(40, limits.minGlucoseMgDl)
        assertEquals(400, limits.maxGlucoseMgDl)
        assertEquals(10_000, limits.maxBasalRateMilliunits)
        assertEquals(15_000, limits.maxBolusDoseMilliunits)
    }

    // -- boundary tests -------------------------------------------------------

    @Test
    fun `accepts boundary values at absolute limits`() {
        val limits = SafetyLimits(
            minGlucoseMgDl = SafetyLimits.ABSOLUTE_MIN_GLUCOSE,
            maxGlucoseMgDl = SafetyLimits.ABSOLUTE_MAX_GLUCOSE,
            maxBasalRateMilliunits = SafetyLimits.ABSOLUTE_MAX_BASAL_MILLIUNITS,
            maxBolusDoseMilliunits = SafetyLimits.ABSOLUTE_MAX_BOLUS_MILLIUNITS,
        )
        assertEquals(1, limits.minGlucoseMgDl)
        assertEquals(999, limits.maxGlucoseMgDl)
        assertEquals(50_000, limits.maxBasalRateMilliunits)
        assertEquals(25_000, limits.maxBolusDoseMilliunits)
    }
}
