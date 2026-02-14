package com.glycemicgpt.mobile.logging

import android.util.Log
import timber.log.Timber

/**
 * Timber Tree for release builds that:
 * - Only logs WARN and ERROR levels (drops DEBUG/INFO)
 * - Scrubs sensitive patterns (BG values, tokens, emails) from messages
 */
class ReleaseTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.WARN
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val scrubbed = scrubSensitiveData(message)
        Log.println(priority, tag ?: "GlycemicGPT", scrubbed)
    }

    companion object {
        private val BG_MG_PATTERN = Regex("""\b\d{2,3}\s*mg/dL\b""")
        private val BG_MMOL_PATTERN = Regex("""\b\d{1,2}\.\d\s*mmol/L\b""")
        private val TOKEN_PATTERN = Regex("""\b[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\b""")
        private val EMAIL_PATTERN = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")

        fun scrubSensitiveData(message: String): String {
            var result = message
            result = TOKEN_PATTERN.replace(result, "[TOKEN]")
            result = EMAIL_PATTERN.replace(result, "[EMAIL]")
            result = BG_MG_PATTERN.replace(result, "[BG]")
            result = BG_MMOL_PATTERN.replace(result, "[BG]")
            return result
        }
    }
}
