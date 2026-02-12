package com.glycemicgpt.mobile.ble.protocol

/**
 * Tandem BLE protocol constants and message framing.
 *
 * This is our own Kotlin implementation of the Tandem t:slim X2 BLE protocol,
 * informed by research from jwoglom/pumpX2 (MIT licensed). We do not import
 * or depend on pumpX2 at runtime.
 *
 * Protocol overview:
 * - Communication happens over BLE GATT characteristics
 * - Messages are framed with opcode, length, payload, and checksum
 * - Authentication uses a challenge-response handshake
 * - Only READ-ONLY status request opcodes are implemented here
 *
 * Implementation will be completed in Story 16.2 (pairing) and Story 16.3 (data reads).
 */
object TandemProtocol {
    // Tandem BLE service and characteristic UUIDs (to be populated from pumpX2 research)
    // const val SERVICE_UUID = "..."
    // const val CHARACTERISTIC_UUID = "..."

    // Read-only status request opcodes
    const val OPCODE_CONTROL_IQ_IOB = 108
    // Additional opcodes will be added in Story 16.3
}
