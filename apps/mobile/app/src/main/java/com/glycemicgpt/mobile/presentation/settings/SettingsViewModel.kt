package com.glycemicgpt.mobile.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glycemicgpt.mobile.BuildConfig
import com.glycemicgpt.mobile.data.local.AppSettingsStore
import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.glycemicgpt.mobile.data.local.PumpCredentialStore
import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.LoginRequest
import com.glycemicgpt.mobile.data.repository.DeviceRepository
import com.glycemicgpt.mobile.data.update.AppUpdateChecker
import com.glycemicgpt.mobile.service.AlertStreamService
import com.glycemicgpt.mobile.data.update.DownloadResult
import com.glycemicgpt.mobile.data.update.UpdateCheckResult
import com.glycemicgpt.mobile.wear.WearDataContract
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.io.File
import javax.inject.Inject

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class Available(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String?,
        val sizeBytes: Long,
    ) : UpdateUiState()
    object UpToDate : UpdateUiState()
    object Downloading : UpdateUiState()
    data class ReadyToInstall(val apkFile: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

data class SettingsUiState(
    // Account
    val baseUrl: String = "",
    val isLoggedIn: Boolean = false,
    val userEmail: String? = null,
    // Pump
    val isPumpPaired: Boolean = false,
    val pairedPumpAddress: String? = null,
    // Sync
    val backendSyncEnabled: Boolean = true,
    val dataRetentionDays: Int = 7,
    // About
    val appVersion: String = "",
    val buildType: String = "",
    // Connection test
    val isTestingConnection: Boolean = false,
    val connectionTestResult: String? = null,
    // Login
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    // Confirmation dialogs
    val showLogoutConfirm: Boolean = false,
    val showUnpairConfirm: Boolean = false,
    // App update
    val updateState: UpdateUiState = UpdateUiState.Idle,
    // Watch
    val watchAppInstalled: Boolean? = null,
    val watchConnected: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authTokenStore: AuthTokenStore,
    private val pumpCredentialStore: PumpCredentialStore,
    private val appSettingsStore: AppSettingsStore,
    private val api: GlycemicGptApi,
    private val deviceRepository: DeviceRepository,
    private val appUpdateChecker: AppUpdateChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadState()
    }

    fun loadState() {
        val loggedIn = authTokenStore.isLoggedIn()
        _uiState.value = _uiState.value.copy(
            baseUrl = authTokenStore.getBaseUrl() ?: "",
            isLoggedIn = loggedIn,
            userEmail = if (loggedIn) authTokenStore.getUserEmail() else null,
            isPumpPaired = pumpCredentialStore.isPaired(),
            pairedPumpAddress = pumpCredentialStore.getPairedAddress(),
            backendSyncEnabled = appSettingsStore.backendSyncEnabled,
            dataRetentionDays = appSettingsStore.dataRetentionDays,
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
        )

        // Start alert stream on app startup if logged in
        if (loggedIn) {
            AlertStreamService.start(appContext)
            viewModelScope.launch {
                deviceRepository.registerDevice()
                    .onFailure { e -> Timber.w(e, "Device re-registration failed") }
            }
        }
    }

    fun saveBaseUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(baseUrl = trimmed, connectionTestResult = null)
            return
        }
        if (!isValidUrl(trimmed)) {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = "Invalid URL. HTTPS required.",
            )
            return
        }
        authTokenStore.saveBaseUrl(trimmed)
        _uiState.value = _uiState.value.copy(
            baseUrl = trimmed,
            connectionTestResult = null,
        )
    }

    fun testConnection() {
        val url = _uiState.value.baseUrl
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = "Enter a server URL first",
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingConnection = true,
                connectionTestResult = null,
            )
            try {
                val response = api.healthCheck()
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isTestingConnection = false,
                        connectionTestResult = "Connected successfully",
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isTestingConnection = false,
                        connectionTestResult = "Server responded with HTTP ${response.code()}",
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Connection test failed")
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = "Connection failed: ${e.message}",
                )
            }
        }
    }

    fun login(email: String, password: String) {
        val url = _uiState.value.baseUrl
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(loginError = "Configure server URL first")
            return
        }
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(loginError = "Email and password are required")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingIn = true, loginError = null)
            try {
                val response = api.login(LoginRequest(email = email, password = password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoggingIn = false,
                            loginError = "Login failed: empty response from server",
                        )
                        return@launch
                    }
                    val expiresAtMs = System.currentTimeMillis() + (body.expiresIn * 1000L)
                    authTokenStore.saveCredentials(url, body.accessToken, expiresAtMs, body.user.email)
                    authTokenStore.saveRefreshToken(body.refreshToken)
                    _uiState.value = _uiState.value.copy(
                        isLoggingIn = false,
                        isLoggedIn = true,
                        userEmail = body.user.email,
                    )
                    // Register device and start alert stream
                    launch {
                        deviceRepository.registerDevice()
                            .onFailure { e -> Timber.w(e, "Device registration failed") }
                    }
                    AlertStreamService.start(appContext)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoggingIn = false,
                        loginError = when (response.code()) {
                            401 -> "Invalid email or password"
                            else -> "Login failed: HTTP ${response.code()}"
                        },
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Login failed")
                _uiState.value = _uiState.value.copy(
                    isLoggingIn = false,
                    loginError = "Network error: ${e.message}",
                )
            }
        }
    }

    fun showLogoutConfirm() {
        _uiState.value = _uiState.value.copy(showLogoutConfirm = true)
    }

    fun dismissLogoutConfirm() {
        _uiState.value = _uiState.value.copy(showLogoutConfirm = false)
    }

    fun logout() {
        // Stop alert stream and unregister device
        AlertStreamService.stop(appContext)
        viewModelScope.launch {
            deviceRepository.unregisterDevice()
                .onFailure { e -> Timber.w(e, "Device unregistration failed") }
        }
        authTokenStore.clearToken()
        _uiState.value = _uiState.value.copy(
            isLoggedIn = false,
            userEmail = null,
            showLogoutConfirm = false,
        )
    }

    fun showUnpairConfirm() {
        _uiState.value = _uiState.value.copy(showUnpairConfirm = true)
    }

    fun dismissUnpairConfirm() {
        _uiState.value = _uiState.value.copy(showUnpairConfirm = false)
    }

    fun unpair() {
        pumpCredentialStore.clearPairing()
        _uiState.value = _uiState.value.copy(
            isPumpPaired = false,
            pairedPumpAddress = null,
            showUnpairConfirm = false,
        )
    }

    fun setBackendSyncEnabled(enabled: Boolean) {
        appSettingsStore.backendSyncEnabled = enabled
        _uiState.value = _uiState.value.copy(backendSyncEnabled = enabled)
    }

    fun setDataRetentionDays(days: Int) {
        val clamped = days.coerceIn(AppSettingsStore.MIN_RETENTION_DAYS, AppSettingsStore.MAX_RETENTION_DAYS)
        appSettingsStore.dataRetentionDays = clamped
        _uiState.value = _uiState.value.copy(dataRetentionDays = clamped)
    }

    fun clearConnectionTestResult() {
        _uiState.value = _uiState.value.copy(connectionTestResult = null)
    }

    fun clearLoginError() {
        _uiState.value = _uiState.value.copy(loginError = null)
    }

    fun checkForUpdate() {
        if (_uiState.value.updateState is UpdateUiState.Checking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(updateState = UpdateUiState.Checking)
            when (val result = appUpdateChecker.check(BuildConfig.VERSION_CODE)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateUiState.Available(
                            version = result.info.latestVersion,
                            downloadUrl = result.info.downloadUrl,
                            releaseNotes = result.info.releaseNotes,
                            sizeBytes = result.info.apkSizeBytes,
                        ),
                    )
                }
                is UpdateCheckResult.UpToDate -> {
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateUiState.UpToDate,
                    )
                }
                is UpdateCheckResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateUiState.Error(result.message),
                    )
                }
            }
        }
    }

    fun downloadAndInstallUpdate(url: String, expectedSize: Long) {
        if (_uiState.value.updateState is UpdateUiState.Downloading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(updateState = UpdateUiState.Downloading)
            val fileName = url.substringAfterLast("/")
            when (val result = appUpdateChecker.downloadApk(url, fileName, expectedSize)) {
                is DownloadResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateUiState.ReadyToInstall(result.apkFile),
                    )
                }
                is DownloadResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        updateState = UpdateUiState.Error(result.message),
                    )
                }
            }
        }
    }

    fun getInstallIntent(apkFile: File): android.content.Intent {
        return appUpdateChecker.createInstallIntent(apkFile)
    }

    fun dismissUpdateState() {
        _uiState.value = _uiState.value.copy(updateState = UpdateUiState.Idle)
    }

    fun checkWatchStatus() {
        viewModelScope.launch {
            try {
                val capInfo = Wearable.getCapabilityClient(appContext)
                    .getCapability(
                        WearDataContract.WATCH_APP_CAPABILITY,
                        CapabilityClient.FILTER_ALL,
                    )
                    .await()
                val reachable = capInfo.nodes.any { it.isNearby }
                _uiState.value = _uiState.value.copy(
                    watchAppInstalled = capInfo.nodes.isNotEmpty(),
                    watchConnected = reachable,
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to check watch status")
                _uiState.value = _uiState.value.copy(
                    watchAppInstalled = null,
                    watchConnected = false,
                )
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (BuildConfig.DEBUG && parsed.scheme == "http") return true
        return parsed.scheme == "https"
    }
}
