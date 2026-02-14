package com.glycemicgpt.mobile.ble.crypto

import timber.log.Timber
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 utility matching pumpX2's HmacSha256 implementation.
 *
 * The mod255 byte sanitization matches pumpX2 exactly -- it ensures all bytes
 * are treated as unsigned values before feeding into the HMAC.
 */
object HmacSha256Util {

    fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        return try {
            val secretKey = SecretKeySpec(mod255(key.copyOf()), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            mac.doFinal(mod255(data.copyOf()))
        } catch (e: Exception) {
            Timber.e(e, "HMAC-SHA256 computation failed")
            ByteArray(0)
        }
    }

    private fun mod255(data: ByteArray): ByteArray {
        for (i in data.indices) {
            val b = data[i].toInt()
            if (b < 0) {
                data[i] = ((b + 255 + 1) and 255).toByte()
            }
        }
        return data
    }
}
