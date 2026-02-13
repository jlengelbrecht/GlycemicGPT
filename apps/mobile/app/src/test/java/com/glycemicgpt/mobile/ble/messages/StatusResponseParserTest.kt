package com.glycemicgpt.mobile.ble.messages

import com.glycemicgpt.mobile.domain.model.ControlIqMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

class StatusResponseParserTest {

    // -- IoB tests -----------------------------------------------------------

    @Test
    fun `parseIoBResponse with valid milliunits`() {
        // 2500 milliunits = 2.5 units
        val cargo = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(2500).array()
        val result = StatusResponseParser.parseIoBResponse(cargo)
        assertNotNull(result)
        assertEquals(2.5f, result!!.iob, 0.001f)
    }

    @Test
    fun `parseIoBResponse with zero`() {
        val cargo = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()
        val result = StatusResponseParser.parseIoBResponse(cargo)
        assertNotNull(result)
        assertEquals(0f, result!!.iob, 0.001f)
    }

    @Test
    fun `parseIoBResponse returns null for short cargo`() {
        assertNull(StatusResponseParser.parseIoBResponse(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun `parseIoBResponse returns null for empty cargo`() {
        assertNull(StatusResponseParser.parseIoBResponse(ByteArray(0)))
    }

    // -- Basal status tests --------------------------------------------------

    @Test
    fun `parseBasalStatusResponse standard mode automated`() {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(750) // 0.75 u/hr
        buf.put(0) // Standard mode
        buf.put(1) // Automated delivery active
        val result = StatusResponseParser.parseBasalStatusResponse(buf.array())
        assertNotNull(result)
        assertEquals(0.75f, result!!.rate, 0.001f)
        assertEquals(ControlIqMode.STANDARD, result.controlIqMode)
        assertTrue(result.isAutomated)
    }

    @Test
    fun `parseBasalStatusResponse sleep mode not automated`() {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(1200) // 1.2 u/hr
        buf.put(1) // Sleep mode
        buf.put(0) // Not automated
        val result = StatusResponseParser.parseBasalStatusResponse(buf.array())
        assertNotNull(result)
        assertEquals(1.2f, result!!.rate, 0.001f)
        assertEquals(ControlIqMode.SLEEP, result.controlIqMode)
        assertFalse(result.isAutomated)
    }

    @Test
    fun `parseBasalStatusResponse exercise mode`() {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(500) // 0.5 u/hr
        buf.put(2) // Exercise mode
        buf.put(1) // Automated
        val result = StatusResponseParser.parseBasalStatusResponse(buf.array())
        assertNotNull(result)
        assertEquals(ControlIqMode.EXERCISE, result!!.controlIqMode)
    }

    @Test
    fun `parseBasalStatusResponse returns null for short cargo`() {
        assertNull(StatusResponseParser.parseBasalStatusResponse(byteArrayOf(0x01, 0x02, 0x03)))
    }

    // -- Insulin status (reservoir) tests ------------------------------------

    @Test
    fun `parseInsulinStatusResponse with valid milliunits`() {
        val cargo = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(180_000).array()
        val result = StatusResponseParser.parseInsulinStatusResponse(cargo)
        assertNotNull(result)
        assertEquals(180f, result!!.unitsRemaining, 0.001f)
    }

    @Test
    fun `parseInsulinStatusResponse returns null for short cargo`() {
        assertNull(StatusResponseParser.parseInsulinStatusResponse(byteArrayOf(0x01)))
    }

    // -- Battery tests -------------------------------------------------------

    @Test
    fun `parseBatteryResponse with percentage and charging`() {
        val cargo = byteArrayOf(85.toByte(), 1)
        val result = StatusResponseParser.parseBatteryResponse(cargo)
        assertNotNull(result)
        assertEquals(85, result!!.percentage)
        assertTrue(result.isCharging)
    }

    @Test
    fun `parseBatteryResponse with percentage only`() {
        val cargo = byteArrayOf(42.toByte())
        val result = StatusResponseParser.parseBatteryResponse(cargo)
        assertNotNull(result)
        assertEquals(42, result!!.percentage)
        assertFalse(result.isCharging)
    }

    @Test
    fun `parseBatteryResponse clamps to 0-100`() {
        val cargo = byteArrayOf(0xFF.toByte(), 0)
        val result = StatusResponseParser.parseBatteryResponse(cargo)
        assertNotNull(result)
        assertEquals(100, result!!.percentage)
    }

    @Test
    fun `parseBatteryResponse returns null for empty cargo`() {
        assertNull(StatusResponseParser.parseBatteryResponse(ByteArray(0)))
    }

    // -- Pump settings tests -------------------------------------------------

    @Test
    fun `parsePumpSettingsResponse with valid length-prefixed strings`() {
        val fw = "7.8.1".toByteArray()
        val serial = "SN12345678".toByteArray()
        val model = "t:slim X2".toByteArray()

        val buf = ByteBuffer.allocate(4 + fw.size + 4 + serial.size + 4 + model.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(fw.size).put(fw)
        buf.putInt(serial.size).put(serial)
        buf.putInt(model.size).put(model)

        val result = StatusResponseParser.parsePumpSettingsResponse(buf.array())
        assertNotNull(result)
        assertEquals("7.8.1", result!!.firmwareVersion)
        assertEquals("SN12345678", result.serialNumber)
        assertEquals("t:slim X2", result.modelNumber)
    }

    @Test
    fun `parsePumpSettingsResponse returns null for truncated data`() {
        assertNull(StatusResponseParser.parsePumpSettingsResponse(byteArrayOf(0x05, 0x00)))
    }

    // -- Bolus history tests -------------------------------------------------

    @Test
    fun `parseBolusHistoryResponse with two records`() {
        val now = Instant.now()
        val sinceMs = now.minusSeconds(3600).toEpochMilli() // 1 hour ago

        val buf = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
        // Record 1: 3.5 units, automated correction, 30 min ago
        buf.putInt(3500)
        buf.put(0x03) // automated + correction
        buf.putLong(now.minusSeconds(1800).toEpochMilli())
        // Record 2: 1.0 unit, manual, 10 min ago
        buf.putInt(1000)
        buf.put(0x00)
        buf.putLong(now.minusSeconds(600).toEpochMilli())

        val since = now.minusSeconds(3600)
        val events = StatusResponseParser.parseBolusHistoryResponse(buf.array(), since)
        assertEquals(2, events.size)

        assertEquals(3.5f, events[0].units, 0.001f)
        assertTrue(events[0].isAutomated)
        assertTrue(events[0].isCorrection)

        assertEquals(1.0f, events[1].units, 0.001f)
        assertFalse(events[1].isAutomated)
        assertFalse(events[1].isCorrection)
    }

    @Test
    fun `parseBolusHistoryResponse filters by since timestamp`() {
        val now = Instant.now()
        val buf = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        // Record before the "since" cutoff
        buf.putInt(2000)
        buf.put(0x00)
        buf.putLong(now.minusSeconds(7200).toEpochMilli()) // 2 hours ago

        val since = now.minusSeconds(3600) // only want last hour
        val events = StatusResponseParser.parseBolusHistoryResponse(buf.array(), since)
        assertEquals(0, events.size)
    }

    @Test
    fun `parseBolusHistoryResponse returns empty for short cargo`() {
        val events = StatusResponseParser.parseBolusHistoryResponse(
            byteArrayOf(0x01, 0x02),
            Instant.now(),
        )
        assertTrue(events.isEmpty())
    }

    // -- CGM status tests ----------------------------------------------------

    @Test
    fun `parseCgmStatusResponse with valid glucose and trend`() {
        // 120 mg/dL = 0x78 0x00, trend = FLAT (4)
        val cargo = byteArrayOf(0x78, 0x00, 0x04)
        val result = StatusResponseParser.parseCgmStatusResponse(cargo)
        assertNotNull(result)
        assertEquals(120, result!!.glucoseMgDl)
        assertEquals(
            com.glycemicgpt.mobile.domain.model.CgmTrend.FLAT,
            result.trendArrow,
        )
    }

    @Test
    fun `parseCgmStatusResponse with high glucose`() {
        // 350 mg/dL = 0x5E 0x01 (little-endian), trend = SINGLE_UP (2)
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(350.toShort())
        buf.put(2)
        val result = StatusResponseParser.parseCgmStatusResponse(buf.array())
        assertNotNull(result)
        assertEquals(350, result!!.glucoseMgDl)
        assertEquals(
            com.glycemicgpt.mobile.domain.model.CgmTrend.SINGLE_UP,
            result.trendArrow,
        )
    }

    @Test
    fun `parseCgmStatusResponse returns null for zero glucose`() {
        val cargo = byteArrayOf(0x00, 0x00, 0x04)
        assertNull(StatusResponseParser.parseCgmStatusResponse(cargo))
    }

    @Test
    fun `parseCgmStatusResponse returns null for glucose over 500`() {
        // 501 = 0xF5 0x01
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(501.toShort())
        buf.put(4)
        assertNull(StatusResponseParser.parseCgmStatusResponse(buf.array()))
    }

    @Test
    fun `parseCgmStatusResponse returns null for short cargo`() {
        assertNull(StatusResponseParser.parseCgmStatusResponse(byteArrayOf(0x78, 0x00)))
    }

    @Test
    fun `parseCgmStatusResponse returns null for empty cargo`() {
        assertNull(StatusResponseParser.parseCgmStatusResponse(ByteArray(0)))
    }

    @Test
    fun `parseCgmStatusResponse maps all trend values`() {
        val expected = mapOf(
            1 to com.glycemicgpt.mobile.domain.model.CgmTrend.DOUBLE_UP,
            2 to com.glycemicgpt.mobile.domain.model.CgmTrend.SINGLE_UP,
            3 to com.glycemicgpt.mobile.domain.model.CgmTrend.FORTY_FIVE_UP,
            4 to com.glycemicgpt.mobile.domain.model.CgmTrend.FLAT,
            5 to com.glycemicgpt.mobile.domain.model.CgmTrend.FORTY_FIVE_DOWN,
            6 to com.glycemicgpt.mobile.domain.model.CgmTrend.SINGLE_DOWN,
            7 to com.glycemicgpt.mobile.domain.model.CgmTrend.DOUBLE_DOWN,
        )
        for ((trendByte, expectedTrend) in expected) {
            val cargo = byteArrayOf(0x64, 0x00, trendByte.toByte()) // 100 mg/dL
            val result = StatusResponseParser.parseCgmStatusResponse(cargo)
            assertNotNull("trend $trendByte should parse", result)
            assertEquals("trend $trendByte", expectedTrend, result!!.trendArrow)
        }
    }

    @Test
    fun `parseCgmStatusResponse maps unknown trend to UNKNOWN`() {
        val cargo = byteArrayOf(0x64, 0x00, 0x00) // trend byte 0
        val result = StatusResponseParser.parseCgmStatusResponse(cargo)
        assertNotNull(result)
        assertEquals(
            com.glycemicgpt.mobile.domain.model.CgmTrend.UNKNOWN,
            result!!.trendArrow,
        )

        // Also test value 255 (0xFF)
        val cargo2 = byteArrayOf(0x64, 0x00, 0xFF.toByte())
        val result2 = StatusResponseParser.parseCgmStatusResponse(cargo2)
        assertNotNull(result2)
        assertEquals(
            com.glycemicgpt.mobile.domain.model.CgmTrend.UNKNOWN,
            result2!!.trendArrow,
        )
    }

    @Test
    fun `parseCgmStatusResponse boundary glucose 500 is valid`() {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(500.toShort())
        buf.put(4)
        val result = StatusResponseParser.parseCgmStatusResponse(buf.array())
        assertNotNull(result)
        assertEquals(500, result!!.glucoseMgDl)
    }

    @Test
    fun `parseCgmStatusResponse boundary glucose 1 is valid`() {
        val cargo = byteArrayOf(0x01, 0x00, 0x04) // 1 mg/dL
        val result = StatusResponseParser.parseCgmStatusResponse(cargo)
        assertNotNull(result)
        assertEquals(1, result!!.glucoseMgDl)
    }
}
