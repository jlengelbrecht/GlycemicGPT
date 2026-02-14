package com.glycemicgpt.mobile.ble.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * Simplified HKDF implementation matching pumpX2's Hkdf class.
 *
 * Used in JPAKE key confirmation step to derive the session key
 * from the server nonce and the derived secret.
 */
object Hkdf {

    /** Build a 32-byte key from nonce and key material. */
    fun build(nonce: ByteArray, keyMaterial: ByteArray): ByteArray {
        val extracted = extract(nonce, keyMaterial)
        return expand(extracted, ByteArray(0), 32)
    }

    private fun extract(keyMaterial: ByteArray, data: ByteArray): ByteArray {
        val mac = initHmacSha256(keyMaterial) ?: return ByteArray(0)
        mac.update(data)
        val result = mac.doFinal()
        mac.reset()
        return result
    }

    private fun expand(keyMaterial: ByteArray, info: ByteArray, keyLength: Int): ByteArray {
        val mac = initHmacSha256(keyMaterial) ?: return ByteArray(0)
        var previousBlock = ByteArray(0)
        var resultKey = ByteArray(0)
        val numBlocks = ceil(keyLength / 32.0).toInt()

        for (blockIndex in 0 until numBlocks) {
            val input = previousBlock + info + decodeHex(Integer.toHexString(blockIndex + 1))
            mac.update(input)
            previousBlock = mac.doFinal()
            mac.reset()
            resultKey += previousBlock
        }
        return resultKey.copyOfRange(0, keyLength)
    }

    private fun initHmacSha256(keyMaterial: ByteArray): Mac? {
        return try {
            val key = if (keyMaterial.isEmpty()) ByteArray(32) else keyMaterial
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            mac
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeHex(hex: String): ByteArray {
        val padded = if (hex.length % 2 == 1) "0$hex" else hex
        val length = padded.length / 2
        val out = ByteArray(length)
        for (i in 0 until length) {
            out[i] = padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }
}
