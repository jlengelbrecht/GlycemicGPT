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
 * Byte layout is informed by studying jwoglom/pumpX2 (MIT). We own this code.
 *
 * All parsers return null on malformed cargo instead of throwing.
 */
object StatusResponseParser {

    /**
     * Parse ControlIQ IOB response (opcode 109).
     *
     * Cargo layout:
     *   bytes 0-3: IOB in milliunits (Int32 LE)
     *   bytes 4-7: pump time (UInt32 LE, seconds since pump epoch)
     */
    fun parseIoBResponse(cargo: ByteArray): IoBReading? {
        if (cargo.size < 4) return null
        val buf = ByteBuffer.wrap(cargo).order(ByteOrder.LITTLE_ENDIAN)
        val milliUnits = buf.int
        val iob = milliUnits / 1000f
        return IoBReading(iob = iob, timestamp = Instant.now())
    }

    /**
     * Parse CurrentBasalStatus response (opcode 115).
     *
     * Cargo layout:
     *   bytes 0-3: basal rate in milliunits/hr (Int32 LE)
     *   byte 4: Control-IQ mode (0=Standard, 1=Sleep, 2=Exercise)
     *   byte 5: automation flags (bit 0 = automated delivery active)
     */
    fun parseBasalStatusResponse(cargo: ByteArray): BasalReading? {
        if (cargo.size < 6) return null
        val buf = ByteBuffer.wrap(cargo).order(ByteOrder.LITTLE_ENDIAN)
        val milliUnitsPerHr = buf.int
        val rate = milliUnitsPerHr / 1000f
        val modeVal = cargo[4].toInt() and 0xFF
        val automationFlags = cargo[5].toInt() and 0xFF

        val mode = when (modeVal) {
            1 -> ControlIqMode.SLEEP
            2 -> ControlIqMode.EXERCISE
            else -> ControlIqMode.STANDARD
        }
        val isAutomated = (automationFlags and 0x01) != 0

        return BasalReading(
            rate = rate,
            isAutomated = isAutomated,
            controlIqMode = mode,
            timestamp = Instant.now(),
        )
    }

    /**
     * Parse InsulinStatus response (opcode 42).
     *
     * Cargo layout:
     *   bytes 0-3: reservoir units remaining in milliunits (Int32 LE)
     */
    fun parseInsulinStatusResponse(cargo: ByteArray): ReservoirReading? {
        if (cargo.size < 4) return null
        val buf = ByteBuffer.wrap(cargo).order(ByteOrder.LITTLE_ENDIAN)
        val milliUnits = buf.int
        val unitsRemaining = milliUnits / 1000f
        return ReservoirReading(unitsRemaining = unitsRemaining, timestamp = Instant.now())
    }

    /**
     * Parse CurrentBattery response (opcode 58).
     *
     * Cargo layout:
     *   byte 0: battery percentage (0-100)
     *   byte 1: charging flag (0=not charging, 1=charging)
     */
    fun parseBatteryResponse(cargo: ByteArray): BatteryStatus? {
        if (cargo.isEmpty()) return null
        val percentage = cargo[0].toInt() and 0xFF
        val isCharging = if (cargo.size > 1) (cargo[1].toInt() and 0xFF) != 0 else false
        return BatteryStatus(
            percentage = percentage.coerceIn(0, 100),
            isCharging = isCharging,
            timestamp = Instant.now(),
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
     * Parse CGM status response (opcode 101).
     *
     * Cargo layout:
     *   bytes 0-1: glucose value in mg/dL (UInt16 LE)
     *   byte 2: trend arrow enum (1-7, 0=unknown)
     *   byte 3: sensor status flags
     */
    fun parseCgmStatusResponse(cargo: ByteArray): CgmReading? {
        if (cargo.size < 3) return null
        val buf = ByteBuffer.wrap(cargo, 0, 2).order(ByteOrder.LITTLE_ENDIAN)
        val glucoseMgDl = buf.short.toInt() and 0xFFFF

        // Filter out sensor error / no-data values
        if (glucoseMgDl == 0 || glucoseMgDl > 500) return null

        val trendVal = cargo[2].toInt() and 0xFF
        val trend = when (trendVal) {
            1 -> CgmTrend.DOUBLE_UP
            2 -> CgmTrend.SINGLE_UP
            3 -> CgmTrend.FORTY_FIVE_UP
            4 -> CgmTrend.FLAT
            5 -> CgmTrend.FORTY_FIVE_DOWN
            6 -> CgmTrend.SINGLE_DOWN
            7 -> CgmTrend.DOUBLE_DOWN
            else -> CgmTrend.UNKNOWN
        }

        return CgmReading(glucoseMgDl = glucoseMgDl, trendArrow = trend, timestamp = Instant.now())
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
