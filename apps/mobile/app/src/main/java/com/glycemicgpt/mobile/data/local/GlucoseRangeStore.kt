package com.glycemicgpt.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's configured glucose range thresholds locally.
 *
 * Values are fetched from the backend on connect and cached here.
 * The app always uses whatever was last received from the backend.
 */
@Singleton
class GlucoseRangeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("glucose_range", Context.MODE_PRIVATE)

    var urgentLow: Int
        get() = prefs.getInt(KEY_URGENT_LOW, DEFAULT_URGENT_LOW)
        set(value) { prefs.edit().putInt(KEY_URGENT_LOW, value).apply() }

    var low: Int
        get() = prefs.getInt(KEY_LOW, DEFAULT_LOW)
        set(value) { prefs.edit().putInt(KEY_LOW, value).apply() }

    var high: Int
        get() = prefs.getInt(KEY_HIGH, DEFAULT_HIGH)
        set(value) { prefs.edit().putInt(KEY_HIGH, value).apply() }

    var urgentHigh: Int
        get() = prefs.getInt(KEY_URGENT_HIGH, DEFAULT_URGENT_HIGH)
        set(value) { prefs.edit().putInt(KEY_URGENT_HIGH, value).apply() }

    /** Timestamp of the last successful fetch from the backend. */
    var lastFetchedMs: Long
        get() = prefs.getLong(KEY_LAST_FETCHED, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_FETCHED, value).apply() }

    /**
     * Atomically update all four thresholds plus the fetch timestamp in a
     * single SharedPreferences transaction. Prevents inconsistent state if
     * the app is killed mid-write.
     */
    fun updateAll(urgentLow: Int, low: Int, high: Int, urgentHigh: Int) {
        prefs.edit()
            .putInt(KEY_URGENT_LOW, urgentLow)
            .putInt(KEY_LOW, low)
            .putInt(KEY_HIGH, high)
            .putInt(KEY_URGENT_HIGH, urgentHigh)
            .putLong(KEY_LAST_FETCHED, System.currentTimeMillis())
            .apply()
    }

    /** Returns true if the cached values are older than the given max age. */
    fun isStale(maxAgeMs: Long = STALE_THRESHOLD_MS): Boolean {
        val age = System.currentTimeMillis() - lastFetchedMs
        return age > maxAgeMs
    }

    companion object {
        const val DEFAULT_URGENT_LOW = 55
        const val DEFAULT_LOW = 70
        const val DEFAULT_HIGH = 180
        const val DEFAULT_URGENT_HIGH = 250
        const val STALE_THRESHOLD_MS = 3_600_000L // 1 hour

        private const val KEY_URGENT_LOW = "urgent_low"
        private const val KEY_LOW = "low"
        private const val KEY_HIGH = "high"
        private const val KEY_URGENT_HIGH = "urgent_high"
        private const val KEY_LAST_FETCHED = "last_fetched_ms"
    }
}
