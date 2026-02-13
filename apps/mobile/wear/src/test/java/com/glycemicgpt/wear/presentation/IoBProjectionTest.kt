package com.glycemicgpt.wear.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class IoBProjectionTest {

    @Test
    fun `projectIoB at 0 minutes returns current value`() {
        assertEquals(2.45f, IoBProjection.projectIoB(2.45f, 0), 0.001f)
    }

    @Test
    fun `projectIoB at negative minutes returns current value`() {
        assertEquals(2.45f, IoBProjection.projectIoB(2.45f, -10), 0.001f)
    }

    @Test
    fun `projectIoB at DIA returns 0`() {
        assertEquals(0f, IoBProjection.projectIoB(2.45f, 300), 0.001f)
    }

    @Test
    fun `projectIoB beyond DIA returns 0`() {
        assertEquals(0f, IoBProjection.projectIoB(2.45f, 400), 0.001f)
    }

    @Test
    fun `projectIoB at 30 minutes`() {
        // 2.45 * (1 - 30/300) = 2.45 * 0.9 = 2.205
        assertEquals(2.205f, IoBProjection.projectIoB(2.45f, 30), 0.001f)
    }

    @Test
    fun `projectIoB at 60 minutes`() {
        // 2.45 * (1 - 60/300) = 2.45 * 0.8 = 1.96
        assertEquals(1.96f, IoBProjection.projectIoB(2.45f, 60), 0.001f)
    }

    @Test
    fun `projectIoB at 120 minutes`() {
        // 2.45 * (1 - 120/300) = 2.45 * 0.6 = 1.47
        assertEquals(1.47f, IoBProjection.projectIoB(2.45f, 120), 0.001f)
    }

    @Test
    fun `projectIoB at 150 minutes (half DIA)`() {
        // 2.45 * (1 - 150/300) = 2.45 * 0.5 = 1.225
        assertEquals(1.225f, IoBProjection.projectIoB(2.45f, 150), 0.001f)
    }

    @Test
    fun `projectIoB with zero IoB`() {
        assertEquals(0f, IoBProjection.projectIoB(0f, 30), 0.001f)
    }

    @Test
    fun `projectIoB with custom DIA`() {
        // 2.0 * (1 - 60/240) = 2.0 * 0.75 = 1.5
        assertEquals(1.5f, IoBProjection.projectIoB(2.0f, 60, diaMinutes = 240), 0.001f)
    }
}
