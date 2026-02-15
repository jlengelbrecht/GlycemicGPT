package com.glycemicgpt.mobile.ble.messages

import android.util.Base64
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.BolusEvent
import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.CgmTrend
import com.glycemicgpt.mobile.domain.model.ControlIqMode
import com.glycemicgpt.mobile.domain.model.HistoryLogRecord
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.PumpHardwareInfo
import com.glycemicgpt.mobile.domain.model.PumpSettings
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

/**
 * Parses response cargo bytes from Tandem pump BLE status responses into
 * domain model objects.
 *
 * Byte layouts verified against jwoglom/pumpX2 (MIT) source and test vectors.
 * All parsers return null on malformed cargo instead of throwing.
 */
object StatusResponseParser {

    /** Tandem pump epoch: January 1, 2008 00:00:00 UTC */
    private const val TANDEM_EPOCH_OFFSET = 1199145600L

    /**
     * Parse ControlIQIOBResponse (opcode 109).
     *
     * Cargo layout (17 bytes, all little-endian unsigned):
     *   bytes  0- 3: mudaliarIOB (uint32 LE, milliunits)
     *   bytes  4- 7: timeRemainingSeconds (uint32 LE)
     *   bytes  8-11: mudaliarTotalIOB (uint32 LE, milliunits)
     *   bytes 12-15: swan6hrIOB (uint32 LE, milliunits)
     *   byte  16:    iobType (0=MUDALIAR/CIQ off, 1=SWAN_6HR/CIQ on)
     *
     * The displayed IOB depends on iobType:
     *   - iobType 0: use mudaliarIOB
     *   - iobType 1: use swan6hrIOB
     */
    fun parseIoBResponse(cargo: ByteArray): IoBReading? {
        if (cargo.size < 17) return null
        val buf = ByteBuffer.wrap(cargo).order(ByteOrder.LITTLE_ENDIAN)
        val mudaliarIOB = buf.int.toLong() and 0xFFFFFFFFL
        buf.int // skip timeRemainingSeconds
        buf.int // skip mudaliarTotalIOB
        val swan6hrIOB = buf.int.toLong() and 0xFFFFFFFFL
        // Read iobType directly from cargo array (not buf) to avoid
        // signed-byte issues and buffer position dependence.
        val iobType = cargo[16].toInt() and 0xFF

        val milliUnits = if (iobType == 1) swan6hrIOB else mudaliarIOB
        val iob = milliUnits / 1000f
        return IoBReading(iob = iob, timestamp = Instant.now())
    }

    /**
     * Parse CurrentBasalStatusResponse (opcode 41).
     *
     * Cargo layout (9 bytes, all little-endian unsigned):
     *   bytes 0-3: profileBasalRate (uint32 LE, milliunits/hr)
     *   bytes 4-7: currentBasalRate (uint32 LE, milliunits/hr)
     *   byte  8:   basalModifiedBitmask (0=normal, 1=modified/suspended)
     *
     * Control-IQ mode is NOT in this response; it comes from
     * HomeScreenMirrorResponse (opcode 57).
     */
    fun parseBasalStatusResponse(cargo: ByteArray): BasalReading? {
        if (cargo.size < 9) return null
        val buf = ByteBuffer.wrap(cargo).order(ByteOrder.LITTLE_ENDIAN)
        buf.int // skip profileBasalRate
        val currentRate = buf.int.toLong() and 0xFFFFFFFFL
        val modified = cargo[8].toInt() and 0xFF
        val rate = currentRate / 1000f
        val isAutomated = modified != 0

        return BasalReading(
            rate = rate,
            isAutomated = isAutomated,
            controlIqMode = ControlIqMode.STANDARD, // updated by HomeScreenMirror
            timestamp = Instant.now(),
        )
    }

    /**
     * Parse InsulinStatusResponse (opcode 37).
     *
     * Cargo layout (4 bytes, little-endian):
     *   bytes 0-1: currentInsulinAmount (uint16 LE, WHOLE units, not milliunits)
     *   byte  2:   isEstimate (0=exact, non-zero=estimate)
     *   byte  3:   insulinLowAmount (threshold in units for low-insulin alert)
     */
    fun parseInsulinStatusResponse(cargo: ByteArray): ReservoirReading? {
        if (cargo.size < 2) return null
        val buf = ByteBuffer.wrap(cargo, 0, 2).order(ByteOrder.LITTLE_ENDIAN)
        val units = buf.short.toInt() and 0xFFFF
        return ReservoirReading(unitsRemaining = units.toFloat(), timestamp = Instant.now())
    }

    /**
     * Parse CurrentBatteryV1Response (opcode 53).
     *
     * Cargo layout (2 bytes):
     *   byte 0: currentBatteryAbc (internal absolute charge metric)
     *   byte 1: currentBatteryIbc (battery percentage 0-100, display value)
     */
    fun parseBatteryV1Response(cargo: ByteArray): BatteryStatus? {
        if (cargo.size < 2) return null
        val percentage = cargo[1].toInt() and 0xFF
        return BatteryStatus(
            percentage = percentage.coerceIn(0, 100),
            isCharging = false, // V1 has no charging flag
            timestamp = Instant.now(),
        )
    }

    /**
     * Parse CurrentBatteryV2Response (opcode 145 / 0x91).
     *
     * Cargo layout (11 bytes):
     *   byte 0:    currentBatteryAbc (internal)
     *   byte 1:    currentBatteryIbc (battery percentage 0-100)
     *   byte 2:    chargingStatus (0=not charging, 1=charging)
     *   bytes 3-10: unknown fields (4x uint16 LE, always 0 in test data)
     */
    fun parseBatteryV2Response(cargo: ByteArray): BatteryStatus? {
        if (cargo.size < 3) return null
        val percentage = cargo[1].toInt() and 0xFF
        val isCharging = (cargo[2].toInt() and 0xFF) != 0
        return BatteryStatus(
            percentage = percentage.coerceIn(0, 100),
            isCharging = isCharging,
            timestamp = Instant.now(),
        )
    }

    /**
     * Parse CurrentEGVGuiDataResponse (opcode 35).
     *
     * Cargo layout (8 bytes, little-endian):
     *   bytes 0-3: bgReadingTimestampSeconds (uint32 LE, seconds since Tandem epoch Jan 1, 2008)
     *   bytes 4-5: cgmReading (uint16 LE, mg/dL)
     *   byte  6:   egvStatusId (0=INVALID, 1=VALID, 2=LOW, 3=HIGH, 4=UNAVAILABLE)
     *   byte  7:   trendRate (signed byte, rate of change -- NOT a trend arrow enum)
     *
     * Trend arrow icons come from HomeScreenMirrorResponse (opcode 57) byte 0.
     */
    fun parseCgmEgvResponse(cargo: ByteArray): CgmReading? {
        if (cargo.size < 8) return null
        val buf = ByteBuffer.wrap(cargo).order(ByteOrder.LITTLE_ENDIAN)
        val pumpTimeSec = buf.int.toLong() and 0xFFFFFFFFL
        val glucoseMgDl = buf.short.toInt() and 0xFFFF
        val egvStatus = cargo[6].toInt() and 0xFF

        // Accept VALID (1), LOW (2), and HIGH (3) readings -- all carry a glucose value.
        // Reject INVALID (0) and UNAVAILABLE (4+) which have no usable glucose data.
        if (egvStatus !in 1..3) return null
        if (glucoseMgDl == 0 || glucoseMgDl > 500) return null

        val timestamp = Instant.ofEpochSecond(pumpTimeSec + TANDEM_EPOCH_OFFSET)

        // trendRate at byte 7 is a signed rate-of-change, not an arrow enum.
        // Trend arrow is set separately via HomeScreenMirror. Default to UNKNOWN here.
        return CgmReading(
            glucoseMgDl = glucoseMgDl,
            trendArrow = CgmTrend.UNKNOWN,
            timestamp = timestamp,
        )
    }

    /**
     * Parse HomeScreenMirrorResponse (opcode 57) for trend arrow and CIQ state.
     *
     * Cargo layout (9 bytes):
     *   byte 0: cgmTrendIconId (0=NO_ARROW, 1=DOUBLE_UP, 2=UP, 3=UP_RIGHT,
     *           4=FLAT, 5=DOWN_RIGHT, 6=DOWN, 7=DOUBLE_DOWN)
     *   byte 1: cgmStatusIconId
     *   byte 2: tempRateIconId
     *   byte 3: tempRatePercentage (signed)
     *   byte 4: basalStatusDurationRemaining
     *   byte 5: basalStatusIconId (0=basal, 1=zero_basal, 5=hypo_suspend, 6=increase, 7=attenuated)
     *   byte 6: apControlStateIconId (0=gray, 1=suspended/red, 2=increase_basal/blue, 3=attenuation/orange)
     *   byte 7: cgmHighAlertIconId
     *   byte 8: cgmLowAlertIconId
     */
    fun parseHomeScreenMirrorResponse(cargo: ByteArray): HomeScreenMirrorData? {
        if (cargo.size < 7) return null
        val trendIconId = cargo[0].toInt() and 0xFF
        val basalStatusIconId = if (cargo.size > 5) cargo[5].toInt() and 0xFF else 0
        val apControlStateIconId = if (cargo.size > 6) cargo[6].toInt() and 0xFF else 0

        val trendArrow = when (trendIconId) {
            1 -> CgmTrend.DOUBLE_UP
            2 -> CgmTrend.SINGLE_UP
            3 -> CgmTrend.FORTY_FIVE_UP
            4 -> CgmTrend.FLAT
            5 -> CgmTrend.FORTY_FIVE_DOWN
            6 -> CgmTrend.SINGLE_DOWN
            7 -> CgmTrend.DOUBLE_DOWN
            else -> CgmTrend.UNKNOWN
        }

        return HomeScreenMirrorData(
            trendArrow = trendArrow,
            basalStatusIconId = basalStatusIconId,
            apControlStateIconId = apControlStateIconId,
        )
    }

    /**
     * Parse PumpSettings response (opcode 91).
     *
     * Cargo layout:
     *   bytes 0-3: firmware version length (Int32 LE), then string bytes
     *   followed by serial number and model number in the same pattern.
     *
     * Simplified: firmware(4+len) + serial(4+len) + model(4+len)
     */
    fun parsePumpSettingsResponse(cargo: ByteArray): PumpSettings? {
        if (cargo.size < 12) return null // minimum: 3 x (4-byte length prefix + 0 bytes)
        try {
            var offset = 0
            val (firmware, fwByteLen) = readLengthPrefixedString(cargo, offset) ?: return null
            offset += 4 + fwByteLen
            val (serial, serialByteLen) = readLengthPrefixedString(cargo, offset) ?: return null
            offset += 4 + serialByteLen
            val (model, _) = readLengthPrefixedString(cargo, offset) ?: return null
            return PumpSettings(
                firmwareVersion = firmware,
                serialNumber = serial,
                modelNumber = model,
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Parse BolusCalcDataSnapshot response (opcode 76).
     *
     * Cargo layout: repeated bolus records, each 13 bytes:
     *   bytes 0-3: units in milliunits (Int32 LE)
     *   byte 4: flags (bit 0 = automated, bit 1 = correction)
     *   bytes 5-12: timestamp (Int64 LE, millis since epoch)
     */
    fun parseBolusHistoryResponse(cargo: ByteArray, since: Instant): List<BolusEvent> {
        val recordSize = 13
        if (cargo.size < recordSize) return emptyList()

        val events = mutableListOf<BolusEvent>()
        var offset = 0
        while (offset + recordSize <= cargo.size) {
            val buf = ByteBuffer.wrap(cargo, offset, recordSize).order(ByteOrder.LITTLE_ENDIAN)
            val milliUnits = buf.int
            val flags = buf.get().toInt() and 0xFF
            val timestampMs = buf.long
            val timestamp = Instant.ofEpochMilli(timestampMs)

            if (!timestamp.isBefore(since)) {
                events.add(
                    BolusEvent(
                        units = milliUnits / 1000f,
                        isAutomated = (flags and 0x01) != 0,
                        isCorrection = (flags and 0x02) != 0,
                        timestamp = timestamp,
                    ),
                )
            }
            offset += recordSize
        }
        return events
    }

    /**
     * Parse history log response (opcode 27).
     *
     * Cargo layout: repeated records, each 18 bytes:
     *   bytes 0-3: sequence number (Int32 LE)
     *   bytes 4-5: event type ID (UInt16 LE)
     *   bytes 6-9: pump time in seconds (Int32 LE)
     *   bytes 10-17: raw event payload (8 bytes, base64-encoded for storage)
     *
     * Returns only records with sequence > [sinceSequence].
     */
    fun parseHistoryLogResponse(cargo: ByteArray, sinceSequence: Int): List<HistoryLogRecord> {
        val recordSize = 18
        if (cargo.size < recordSize) return emptyList()

        val records = mutableListOf<HistoryLogRecord>()
        var offset = 0
        while (offset + recordSize <= cargo.size) {
            val buf = ByteBuffer.wrap(cargo, offset, recordSize).order(ByteOrder.LITTLE_ENDIAN)
            val seqNum = buf.int
            val eventTypeId = buf.short.toInt() and 0xFFFF
            val pumpTimeSec = buf.int.toLong() and 0xFFFFFFFFL

            if (seqNum > sinceSequence) {
                // Encode the entire raw record as base64 for cloud upload
                val rawBytes = cargo.copyOfRange(offset, offset + recordSize)
                val rawB64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                records.add(
                    HistoryLogRecord(
                        sequenceNumber = seqNum,
                        rawBytesB64 = rawB64,
                        eventTypeId = eventTypeId,
                        pumpTimeSeconds = pumpTimeSec,
                    ),
                )
            }
            offset += recordSize
        }
        return records
    }

    /**
     * Parse PumpGlobals response (opcode 89).
     *
     * Cargo layout (packed, little-endian):
     *   bytes 0-7: serial number (Int64 LE)
     *   bytes 8-15: model number (Int64 LE)
     *   bytes 16-23: part number (Int64 LE)
     *   bytes 24-27: pump_rev string length, then string bytes
     *   followed by: arm_sw_ver (Int64), msp_sw_ver (Int64),
     *   config_a_bits (Int64), config_b_bits (Int64),
     *   pcba_sn (Int64), pcba_rev string (length-prefixed),
     *   pump_features bitmap (1 byte)
     */
    fun parsePumpGlobalsResponse(cargo: ByteArray): PumpHardwareInfo? {
        if (cargo.size < 24) return null
        try {
            val buf = ByteBuffer.wrap(cargo).order(ByteOrder.LITTLE_ENDIAN)
            val serialNumber = buf.long
            val modelNumber = buf.long
            val partNumber = buf.long

            var offset = 24
            val (pumpRev, pumpRevLen) = readLengthPrefixedString(cargo, offset) ?: return null
            offset += 4 + pumpRevLen

            if (offset + 40 > cargo.size) return null
            val buf2 = ByteBuffer.wrap(cargo, offset, 40).order(ByteOrder.LITTLE_ENDIAN)
            val armSwVer = buf2.long
            val mspSwVer = buf2.long
            val configABits = buf2.long
            val configBBits = buf2.long
            val pcbaSn = buf2.long
            offset += 40

            val (pcbaRev, pcbaRevLen) = readLengthPrefixedString(cargo, offset) ?: ("A" to 0)
            offset += 4 + pcbaRevLen

            // Parse pump features bitmap if present
            val featureNames = listOf("dexcomG5", "basalIQ", "dexcomG6", "controlIQ", "dexcomG7", "abbottFsl2")
            val features = mutableMapOf<String, Boolean>()
            if (offset < cargo.size) {
                val bitmap = cargo[offset].toInt() and 0xFF
                featureNames.forEachIndexed { index, name ->
                    features[name] = (bitmap and (1 shl index)) != 0
                }
            } else {
                featureNames.forEach { features[it] = false }
            }

            return PumpHardwareInfo(
                serialNumber = serialNumber,
                modelNumber = modelNumber,
                partNumber = partNumber,
                pumpRev = pumpRev,
                armSwVer = armSwVer,
                mspSwVer = mspSwVer,
                configABits = configABits,
                configBBits = configBBits,
                pcbaSn = pcbaSn,
                pcbaRev = pcbaRev,
                pumpFeatures = features,
            )
        } catch (_: Exception) {
            return null
        }
    }

    /** Returns Pair(string, byteLength) or null on malformed data. */
    private fun readLengthPrefixedString(data: ByteArray, offset: Int): Pair<String, Int>? {
        if (offset + 4 > data.size) return null
        val buf = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN)
        val len = buf.int
        if (len < 0 || offset + 4 + len > data.size) return null
        val str = String(data, offset + 4, len, Charsets.UTF_8)
        return Pair(str, len)
    }
}

/**
 * Parsed data from HomeScreenMirrorResponse for trend arrow and CIQ state.
 */
data class HomeScreenMirrorData(
    val trendArrow: CgmTrend,
    val basalStatusIconId: Int,
    val apControlStateIconId: Int,
)
