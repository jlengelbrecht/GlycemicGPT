package com.glycemicgpt.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Securely stores backend authentication credentials using EncryptedSharedPreferences.
 *
 * Stores the GlycemicGPT server base URL, JWT token, and expiration.
 */
@Singleton
class AuthTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "auth_credentials",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveCredentials(baseUrl: String, token: String, expiresAtMs: Long, email: String? = null) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trimEnd('/'))
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES_AT, expiresAtMs)
            .apply {
                if (email != null) putString(KEY_USER_EMAIL, email)
            }
            .apply()
    }

    fun saveBaseUrl(url: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, url.trimEnd('/'))
            .apply()
    }

    fun getBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun getToken(): String? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
            clearToken()
            return null
        }
        return token
    }

    fun isLoggedIn(): Boolean = getToken() != null

    fun clearToken() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EXPIRES_AT = "expires_at_ms"
        private const val KEY_USER_EMAIL = "user_email"
    }
}
