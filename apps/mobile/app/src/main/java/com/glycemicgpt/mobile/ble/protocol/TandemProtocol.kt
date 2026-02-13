package com.glycemicgpt.mobile.ble.protocol

import android.os.ParcelUuid
import java.util.UUID

/**
 * Tandem BLE protocol constants.
 *
 * UUIDs and message format derived from studying jwoglom/pumpX2 (MIT licensed).
 * We do not depend on pumpX2 at runtime.
 */
object TandemProtocol {

    // -- GATT Service UUIDs ------------------------------------------------

    val PUMP_SERVICE_UUID: UUID =
        UUID.fromString("0000fdfb-0000-1000-8000-00805f9b34fb")

    val PUMP_SERVICE_PARCEL: ParcelUuid = ParcelUuid(PUMP_SERVICE_UUID)

    val DIS_SERVICE_UUID: UUID =
        UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")

    // -- GATT Characteristic UUIDs -----------------------------------------

    /** Unsigned status reads (IoB, basal, battery, reservoir, etc.) */
    val CURRENT_STATUS_UUID: UUID =
        UUID.fromString("7B83FFF6-9F77-4E5C-8064-AAE2C24838B9")

    /** Qualifying events / alerts */
    val QUALIFYING_EVENTS_UUID: UUID =
        UUID.fromString("7B83FFF7-9F77-4E5C-8064-AAE2C24838B9")

    /** History log streaming */
    val HISTORY_LOG_UUID: UUID =
        UUID.fromString("7B83FFF8-9F77-4E5C-8064-AAE2C24838B9")

    /** Authentication handshake messages */
    val AUTHORIZATION_UUID: UUID =
        UUID.fromString("7B83FFF9-9F77-4E5C-8064-AAE2C24838B9")

    /** Signed control messages */
    val CONTROL_UUID: UUID =
        UUID.fromString("7B83FFFC-9F77-4E5C-8064-AAE2C24838B9")

    /** Signed control stream */
    val CONTROL_STREAM_UUID: UUID =
        UUID.fromString("7B83FFFD-9F77-4E5C-8064-AAE2C24838B9")

    /** Client Characteristic Configuration Descriptor */
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // -- Device Information Service characteristics ------------------------

    val MANUFACTURER_NAME_UUID: UUID =
        UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")

    val MODEL_NUMBER_UUID: UUID =
        UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")

    val SERIAL_NUMBER_UUID: UUID =
        UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")

    val SOFTWARE_REV_UUID: UUID =
        UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")

    // -- All characteristics that need notification subscription -----------

    val NOTIFICATION_CHARACTERISTICS: List<UUID> = listOf(
        CURRENT_STATUS_UUID,
        QUALIFYING_EVENTS_UUID,
        HISTORY_LOG_UUID,
        AUTHORIZATION_UUID,
        CONTROL_UUID,
        CONTROL_STREAM_UUID,
    )

    // -- Read-only status request opcodes ----------------------------------
    // Tandem response opcode = request opcode + 1

    const val OPCODE_CONTROL_IQ_IOB_REQ = 108
    const val OPCODE_CONTROL_IQ_IOB_RESP = 109
    const val OPCODE_CURRENT_BASAL_STATUS_REQ = 114
    const val OPCODE_CURRENT_BASAL_STATUS_RESP = 115
    const val OPCODE_INSULIN_STATUS_REQ = 41
    const val OPCODE_INSULIN_STATUS_RESP = 42
    const val OPCODE_CURRENT_BATTERY_REQ = 57
    const val OPCODE_CURRENT_BATTERY_RESP = 58
    const val OPCODE_PUMP_SETTINGS_REQ = 90
    const val OPCODE_PUMP_SETTINGS_RESP = 91
    const val OPCODE_BOLUS_CALC_DATA_REQ = 75
    const val OPCODE_BOLUS_CALC_DATA_RESP = 76

    /** Default timeout for a status read request (milliseconds). */
    const val STATUS_READ_TIMEOUT_MS = 5000L

    // -- Authentication opcodes --------------------------------------------

    const val OPCODE_CENTRAL_CHALLENGE_REQ = 16
    const val OPCODE_CENTRAL_CHALLENGE_RESP = 17
    const val OPCODE_PUMP_CHALLENGE_REQ = 18
    const val OPCODE_PUMP_CHALLENGE_RESP = 19

    // -- Post-auth baseline opcodes ----------------------------------------

    const val OPCODE_API_VERSION_REQ = 32
    const val OPCODE_API_VERSION_RESP = 33
    const val OPCODE_PUMP_VERSION_REQ = 78
    const val OPCODE_PUMP_VERSION_RESP = 79
    const val OPCODE_TIME_SINCE_RESET_REQ = 80
    const val OPCODE_TIME_SINCE_RESET_RESP = 81

    // -- History log / hardware info opcodes --------------------------------

    const val OPCODE_LOG_ENTRY_SEQ_REQ = 26
    const val OPCODE_LOG_ENTRY_SEQ_RESP = 27
    const val OPCODE_PUMP_GLOBALS_REQ = 88
    const val OPCODE_PUMP_GLOBALS_RESP = 89

    // -- Connection parameters ---------------------------------------------

    /** Minimum MTU for Tandem long packets */
    const val REQUIRED_MTU = 185

    /** Max chunk size for current status / auth characteristics */
    const val CHUNK_SIZE_SHORT = 18

    /** Max chunk size for control characteristics */
    const val CHUNK_SIZE_LONG = 40
}
