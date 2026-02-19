package com.glycemicgpt.mobile.ble.messages

import android.util.Base64
import com.glycemicgpt.mobile.domain.model.CgmTrend
import com.glycemicgpt.mobile.domain.model.HistoryLogRecord
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CgmHistoryParserTest {

    @Before
    fun setUp() {
        // Mock Android's Base64 since it's not available in unit tests
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    /**
     * Build an 18-byte raw history log record (opcode 61 format):
     *   seq(4) + eventTypeId(2) + pumpTimeSec(4) + payload(8)
     */
    private fun buildRawRecord18(
        seq: Int,
        eventTypeId: Int,
        pumpTimeSec: Long,
        payload: ByteArray,
    ): ByteArray {
        val buf = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(seq)
        buf.putShort(eventTypeId.toShort())
        buf.putInt(pumpTimeSec.toInt())
        buf.put(payload.copyOf(8)) // pad/truncate to 8 bytes
        return buf.array()
    }

    /**
     * Build a 26-byte raw history log record (FFF8 stream format):
     *   seq(4) + pumpTimeSec(4) + eventTypeId(2) + data(16)
     */
    private fun buildStreamRecord(
        seq: Int,
        pumpTimeSec: Long,
        eventTypeId: Int,
        data: ByteArray = ByteArray(16),
    ): ByteArray {
        val buf = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(seq)
        buf.putInt(pumpTimeSec.toInt())
        buf.putShort(eventTypeId.toShort())
        buf.put(data.copyOf(16)) // pad/truncate to 16 bytes
        return buf.array()
    }

    private fun buildCgmPayload(glucoseMgDl: Int): ByteArray {
        val payload = ByteArray(8)
        val buf = ByteBuffer.wrap(payload, 0, 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(glucoseMgDl.toShort())
        return payload
    }

    private fun buildCgmData16(glucoseMgDl: Int): ByteArray {
        val data = ByteArray(16)
        val buf = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(glucoseMgDl.toShort())
        return data
    }

    private fun toHistoryLogRecord(raw: ByteArray, seq: Int, eventTypeId: Int, pumpTimeSec: Long): HistoryLogRecord {
        val b64 = java.util.Base64.getEncoder().encodeToString(raw)
        return HistoryLogRecord(
            sequenceNumber = seq,
            rawBytesB64 = b64,
            eventTypeId = eventTypeId,
            pumpTimeSeconds = pumpTimeSec,
        )
    }

    // -- parseCgmEventPayload tests -------------------------------------------

    @Test
    fun `parseCgmEventPayload returns reading for valid glucose`() {
        val payload = buildCgmPayload(120)
        val result = StatusResponseParser.parseCgmEventPayload(payload, 500_000_000L)
        assertNotNull(result)
        assertEquals(120, result!!.glucoseMgDl)
        assertEquals(CgmTrend.UNKNOWN, result.trendArrow)
    }

    @Test
    fun `parseCgmEventPayload returns null for zero glucose`() {
        val payload = buildCgmPayload(0)
        val result = StatusResponseParser.parseCgmEventPayload(payload, 500_000_000L)
        assertNull(result)
    }

    @Test
    fun `parseCgmEventPayload returns null for glucose above 500`() {
        val payload = buildCgmPayload(501)
        val result = StatusResponseParser.parseCgmEventPayload(payload, 500_000_000L)
        assertNull(result)
    }

    @Test
    fun `parseCgmEventPayload returns reading for boundary glucose 1`() {
        val payload = buildCgmPayload(1)
        val result = StatusResponseParser.parseCgmEventPayload(payload, 500_000_000L)
        assertNotNull(result)
        assertEquals(1, result!!.glucoseMgDl)
    }

    @Test
    fun `parseCgmEventPayload returns reading for boundary glucose 500`() {
        val payload = buildCgmPayload(500)
        val result = StatusResponseParser.parseCgmEventPayload(payload, 500_000_000L)
        assertNotNull(result)
        assertEquals(500, result!!.glucoseMgDl)
    }

    @Test
    fun `parseCgmEventPayload returns null for short payload`() {
        val result = StatusResponseParser.parseCgmEventPayload(ByteArray(1), 500_000_000L)
        assertNull(result)
    }

    // -- extractCgmFromHistoryLogs tests (18-byte format) ---------------------

    @Test
    fun `extractCgmFromHistoryLogs filters only event type 16 from 18-byte records`() {
        val pumpTime = 500_000_000L
        val cgmPayload = buildCgmPayload(150)
        val nonCgmPayload = ByteArray(8) // event type 20 (bolus)

        val cgmRaw = buildRawRecord18(100, 16, pumpTime, cgmPayload)
        val nonCgmRaw = buildRawRecord18(101, 20, pumpTime, nonCgmPayload)

        val records = listOf(
            toHistoryLogRecord(cgmRaw, 100, 16, pumpTime),
            toHistoryLogRecord(nonCgmRaw, 101, 20, pumpTime),
        )

        val result = StatusResponseParser.extractCgmFromHistoryLogs(records)
        assertEquals(1, result.size)
        assertEquals(150, result[0].glucoseMgDl)
    }

    @Test
    fun `extractCgmFromHistoryLogs returns empty for no CGM events`() {
        val pumpTime = 500_000_000L
        val raw = buildRawRecord18(100, 280, pumpTime, ByteArray(8))
        val records = listOf(toHistoryLogRecord(raw, 100, 280, pumpTime))

        val result = StatusResponseParser.extractCgmFromHistoryLogs(records)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractCgmFromHistoryLogs returns sorted by timestamp`() {
        val earlyTime = 500_000_000L
        val lateTime = 500_000_300L // 5 minutes later

        val early = buildRawRecord18(100, 16, earlyTime, buildCgmPayload(100))
        val late = buildRawRecord18(101, 16, lateTime, buildCgmPayload(110))

        // Insert in reverse order
        val records = listOf(
            toHistoryLogRecord(late, 101, 16, lateTime),
            toHistoryLogRecord(early, 100, 16, earlyTime),
        )

        val result = StatusResponseParser.extractCgmFromHistoryLogs(records)
        assertEquals(2, result.size)
        assertTrue(result[0].timestamp.isBefore(result[1].timestamp))
        assertEquals(100, result[0].glucoseMgDl)
        assertEquals(110, result[1].glucoseMgDl)
    }

    @Test
    fun `extractCgmFromHistoryLogs skips invalid glucose in CGM events`() {
        val pumpTime = 500_000_000L
        val validRaw = buildRawRecord18(100, 16, pumpTime, buildCgmPayload(120))
        val invalidRaw = buildRawRecord18(101, 16, pumpTime, buildCgmPayload(0))

        val records = listOf(
            toHistoryLogRecord(validRaw, 100, 16, pumpTime),
            toHistoryLogRecord(invalidRaw, 101, 16, pumpTime),
        )

        val result = StatusResponseParser.extractCgmFromHistoryLogs(records)
        assertEquals(1, result.size)
        assertEquals(120, result[0].glucoseMgDl)
    }

    // -- extractCgmFromHistoryLogs tests (26-byte stream format) --------------

    @Test
    fun `extractCgmFromHistoryLogs handles 26-byte stream records`() {
        val pumpTime = 500_000_000L
        val streamRaw = buildStreamRecord(100, pumpTime, 16, buildCgmData16(180))

        val records = listOf(toHistoryLogRecord(streamRaw, 100, 16, pumpTime))
        val result = StatusResponseParser.extractCgmFromHistoryLogs(records)
        assertEquals(1, result.size)
        assertEquals(180, result[0].glucoseMgDl)
    }

    @Test
    fun `extractCgmFromHistoryLogs handles mixed 18 and 26-byte records`() {
        val pumpTime = 500_000_000L
        val record18 = buildRawRecord18(100, 16, pumpTime, buildCgmPayload(120))
        val record26 = buildStreamRecord(101, pumpTime + 300, 16, buildCgmData16(150))

        val records = listOf(
            toHistoryLogRecord(record18, 100, 16, pumpTime),
            toHistoryLogRecord(record26, 101, 16, pumpTime + 300),
        )

        val result = StatusResponseParser.extractCgmFromHistoryLogs(records)
        assertEquals(2, result.size)
        assertEquals(120, result[0].glucoseMgDl)
        assertEquals(150, result[1].glucoseMgDl)
    }

    // -- parseHistoryLogStreamCargo tests -------------------------------------

    @Test
    fun `parseHistoryLogStreamCargo parses single 26-byte record without header`() {
        val record = buildStreamRecord(1000, 500_000_000L, 16, buildCgmData16(140))
        // Cargo = just the 26-byte record (no header)
        val result = StatusResponseParser.parseHistoryLogStreamCargo(record, sinceSequence = 999)
        assertEquals(1, result.size)
        assertEquals(1000, result[0].sequenceNumber)
        assertEquals(16, result[0].eventTypeId)
    }

    @Test
    fun `parseHistoryLogStreamCargo parses single record with 2-byte header`() {
        val record = buildStreamRecord(1000, 500_000_000L, 280, ByteArray(16))
        // Cargo = 2-byte header + 26-byte record
        val cargo = ByteArray(2 + 26)
        cargo[0] = 1 // record count
        cargo[1] = 0 // flags
        System.arraycopy(record, 0, cargo, 2, 26)

        val result = StatusResponseParser.parseHistoryLogStreamCargo(cargo, sinceSequence = 999)
        assertEquals(1, result.size)
        assertEquals(1000, result[0].sequenceNumber)
        assertEquals(280, result[0].eventTypeId)
    }

    @Test
    fun `parseHistoryLogStreamCargo parses multiple records with header`() {
        val record1 = buildStreamRecord(1000, 500_000_000L, 16, buildCgmData16(120))
        val record2 = buildStreamRecord(1001, 500_000_300L, 280, ByteArray(16))
        // Cargo = 2-byte header + 2 x 26-byte records
        val cargo = ByteArray(2 + 52)
        cargo[0] = 2 // record count
        System.arraycopy(record1, 0, cargo, 2, 26)
        System.arraycopy(record2, 0, cargo, 28, 26)

        val result = StatusResponseParser.parseHistoryLogStreamCargo(cargo, sinceSequence = 999)
        assertEquals(2, result.size)
        assertEquals(1000, result[0].sequenceNumber)
        assertEquals(1001, result[1].sequenceNumber)
    }

    @Test
    fun `parseHistoryLogStreamCargo filters by sinceSequence`() {
        val record = buildStreamRecord(1000, 500_000_000L, 16, buildCgmData16(120))
        val result = StatusResponseParser.parseHistoryLogStreamCargo(record, sinceSequence = 1000)
        // seq 1000 is NOT > sinceSequence 1000, so filtered out
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseHistoryLogStreamCargo returns empty for too-short cargo`() {
        val result = StatusResponseParser.parseHistoryLogStreamCargo(ByteArray(10), sinceSequence = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseHistoryLogStreamCargo falls back to 18-byte format`() {
        // Build a valid 18-byte record
        val record = buildRawRecord18(500, 16, 500_000_000L, buildCgmPayload(100))
        val result = StatusResponseParser.parseHistoryLogStreamCargo(record, sinceSequence = 499)
        assertEquals(1, result.size)
        assertEquals(500, result[0].sequenceNumber)
    }

    @Test
    fun `parseHistoryLogStreamCargo preserves full 26-byte rawBytesB64`() {
        val data = buildCgmData16(200)
        val record = buildStreamRecord(1000, 500_000_000L, 16, data)
        val result = StatusResponseParser.parseHistoryLogStreamCargo(record, sinceSequence = 999)
        assertEquals(1, result.size)
        // Decode the stored base64 and verify it's the full 26 bytes
        val decoded = java.util.Base64.getDecoder().decode(result[0].rawBytesB64)
        assertEquals(26, decoded.size)
    }

    @Test
    fun `parseHistoryLogStreamCargo rejects records with invalid event types`() {
        // Build a record with an impossibly high event type (> MAX_KNOWN_EVENT_TYPE)
        val record = buildStreamRecord(1000, 500_000_000L, 60000, ByteArray(16))
        val result = StatusResponseParser.parseHistoryLogStreamCargo(record, sinceSequence = 999)
        assertTrue(result.isEmpty())
    }
}
