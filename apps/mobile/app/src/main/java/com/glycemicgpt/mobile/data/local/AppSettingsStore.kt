package com.glycemicgpt.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-sensitive app settings stored in plain SharedPreferences.
 *
 * For sensitive data (credentials, tokens), use [AuthTokenStore] or [PumpCredentialStore].
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var backendSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKEND_SYNC_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BACKEND_SYNC_ENABLED, value).apply()
        }

    var dataRetentionDays: Int
        get() = prefs.getInt(KEY_DATA_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        set(value) {
            prefs.edit().putInt(KEY_DATA_RETENTION_DAYS, value.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)).apply()
        }

    companion object {
        private const val KEY_BACKEND_SYNC_ENABLED = "backend_sync_enabled"
        private const val KEY_DATA_RETENTION_DAYS = "data_retention_days"
        const val DEFAULT_RETENTION_DAYS = 7
        const val MIN_RETENTION_DAYS = 1
        const val MAX_RETENTION_DAYS = 30
    }
}
