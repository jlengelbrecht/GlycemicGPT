package com.glycemicgpt.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Securely stores pump pairing credentials using EncryptedSharedPreferences.
 */
@Singleton
class PumpCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "pump_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Save the paired pump address and pairing code. */
    fun savePairing(address: String, pairingCode: String) {
        prefs.edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_PAIRING_CODE, pairingCode)
            .putLong(KEY_PAIRED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Get the saved pump Bluetooth address, or null if not paired. */
    fun getPairedAddress(): String? = prefs.getString(KEY_ADDRESS, null)

    /** Get the saved pairing code, or null if not paired. */
    fun getPairingCode(): String? = prefs.getString(KEY_PAIRING_CODE, null)

    /** Whether a pump is currently paired. */
    fun isPaired(): Boolean = getPairedAddress() != null

    /** Clear all pairing data (unpair). */
    fun clearPairing() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ADDRESS = "paired_pump_address"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_PAIRED_AT = "paired_at"
    }
}
