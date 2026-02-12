package com.glycemicgpt.mobile.ble.messages

import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.BolusEvent
import com.glycemicgpt.mobile.domain.model.ControlIqMode
import com.glycemicgpt.mobile.domain.model.IoBReading
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
