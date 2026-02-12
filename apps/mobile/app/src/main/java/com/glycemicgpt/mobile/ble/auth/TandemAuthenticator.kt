package com.glycemicgpt.mobile.ble.auth

import com.glycemicgpt.mobile.ble.protocol.Packetize
import com.glycemicgpt.mobile.ble.protocol.TandemProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Manages the Tandem BLE authentication handshake.
 *
 * Legacy flow (API <= 3.0):
 * 1. Send CentralChallengeRequest with app ID + random challenge
 * 2. Receive CentralChallengeResponse with HMAC key from pump
 * 3. Compute HMAC-SHA1(pairingCode, challengeKey)
 * 4. Send PumpChallengeRequest with the HMAC result
 * 5. Receive PumpChallengeResponse confirming success
 *
 * The pairing code must be obtained from the user (displayed on pump screen).
 */
class TandemAuthenticator @Inject constructor() {

    private val _authState = MutableStateFlow(AuthState.IDLE)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val random = SecureRandom()
    private var appInstanceId: ByteArray = ByteArray(0)
    private var challengeBytes: ByteArray = ByteArray(0)
    private var pairingCode: String = ""

    /** Reset authenticator for a new attempt. Zeroes sensitive material. */
    fun reset() {
        _authState.value = AuthState.IDLE
        appInstanceId.fill(0)
        challengeBytes.fill(0)
        appInstanceId = ByteArray(0)
        challengeBytes = ByteArray(0)
        pairingCode = ""
    }

    /**
     * Build the initial CentralChallengeRequest payload.
     * Returns the encoded BLE chunks to write to the AUTHORIZATION characteristic.
     */
    fun buildCentralChallengeRequest(txId: Int): List<ByteArray> {
        appInstanceId = ByteArray(8).also { random.nextBytes(it) }
        challengeBytes = ByteArray(8).also { random.nextBytes(it) }

        val cargo = ByteArray(appInstanceId.size + challengeBytes.size)
        appInstanceId.copyInto(cargo, 0)
        challengeBytes.copyInto(cargo, appInstanceId.size)

        _authState.value = AuthState.CHALLENGE_SENT
        Timber.d("Auth: sending CentralChallengeRequest (txId=%d)", txId)

        return Packetize.encode(
            TandemProtocol.OPCODE_CENTRAL_CHALLENGE_REQ,
            txId,
            cargo,
            TandemProtocol.CHUNK_SIZE_SHORT,
        )
    }

    /**
     * Process the CentralChallengeResponse from the pump.
     *
     * @param responseCargo The cargo bytes from the pump's response
     * @param code The pairing code entered by the user
     * @param txId Transaction ID for the outbound PumpChallengeRequest
     * @return BLE chunks for the PumpChallengeRequest, or null if state is invalid
     */
    fun processChallengeResponse(
        responseCargo: ByteArray,
        code: String,
        txId: Int,
    ): List<ByteArray>? {
        if (_authState.value != AuthState.CHALLENGE_SENT) {
            Timber.w("Auth: unexpected CentralChallengeResponse in state %s", _authState.value)
            return null
        }

        pairingCode = code

        // The response cargo contains the HMAC challenge key from the pump
        val hmacResult = HmacHelper.buildChallengeResponse(pairingCode, responseCargo)

        _authState.value = AuthState.RESPONSE_SENT
        Timber.d("Auth: sending PumpChallengeRequest (txId=%d)", txId)

        // PumpChallengeRequest cargo = appInstanceId + HMAC result
        val cargo = ByteArray(appInstanceId.size + hmacResult.size)
        appInstanceId.copyInto(cargo, 0)
        hmacResult.copyInto(cargo, appInstanceId.size)

        return Packetize.encode(
            TandemProtocol.OPCODE_PUMP_CHALLENGE_REQ,
            txId,
            cargo,
            TandemProtocol.CHUNK_SIZE_SHORT,
        )
    }

    /**
     * Process the PumpChallengeResponse confirming auth success/failure.
     *
     * @param responseCargo Cargo bytes from the pump's response
     * @return true if authentication succeeded
     */
    fun processPumpChallengeResponse(responseCargo: ByteArray): Boolean {
        if (_authState.value != AuthState.RESPONSE_SENT) {
            Timber.w("Auth: unexpected PumpChallengeResponse in state %s", _authState.value)
            _authState.value = AuthState.FAILED
            return false
        }

        // Success indicated by non-empty response without error flag
        val success = responseCargo.isNotEmpty() && (responseCargo[0].toInt() and 0xFF) == 0
        _authState.value = if (success) AuthState.AUTHENTICATED else AuthState.FAILED

        Timber.d("Auth: PumpChallengeResponse success=%b", success)
        return success
    }
}
