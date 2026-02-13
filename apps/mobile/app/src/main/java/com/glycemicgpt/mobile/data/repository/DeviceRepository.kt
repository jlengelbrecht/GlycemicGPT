package com.glycemicgpt.mobile.data.repository

import android.os.Build
import com.glycemicgpt.mobile.data.local.AppSettingsStore
import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.DeviceRegistrationRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val api: GlycemicGptApi,
    private val appSettingsStore: AppSettingsStore,
) {

    suspend fun registerDevice(): Result<String> = runCatching {
        val token = appSettingsStore.deviceToken ?: UUID.randomUUID().toString().also {
            appSettingsStore.deviceToken = it
        }
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val response = api.registerDevice(
            DeviceRegistrationRequest(
                deviceToken = token,
                deviceName = deviceName,
                platform = "android",
            ),
        )
        if (response.isSuccessful) {
            Timber.d("Device registered: %s", token.take(8))
            token
        } else {
            throw RuntimeException("Device registration failed: HTTP ${response.code()}")
        }
    }

    suspend fun unregisterDevice(): Result<Unit> = runCatching {
        val token = appSettingsStore.deviceToken ?: return@runCatching
        api.unregisterDevice(token)
        appSettingsStore.deviceToken = null
        Timber.d("Device unregistered")
    }
}
