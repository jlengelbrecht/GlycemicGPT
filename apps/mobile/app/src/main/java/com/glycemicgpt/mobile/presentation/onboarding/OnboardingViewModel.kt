package com.glycemicgpt.mobile.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glycemicgpt.mobile.data.local.AppSettingsStore
import com.glycemicgpt.mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val baseUrl: String = "",
    val isTestingConnection: Boolean = false,
    val connectionTestResult: String? = null,
    val connectionTestSuccess: Boolean = false,
    val email: String = "",
    val password: String = "",
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val onboardingComplete: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appSettingsStore: AppSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Pre-fill server URL if returning after logout
        val savedUrl = authRepository.getBaseUrl()
        if (!savedUrl.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(baseUrl = savedUrl)
        }
    }

    fun updateBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            baseUrl = url,
            connectionTestResult = null,
            connectionTestSuccess = false,
        )
    }

    fun testConnection() {
        val url = _uiState.value.baseUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = "Enter a server URL first",
                connectionTestSuccess = false,
            )
            return
        }
        if (!authRepository.isValidUrl(url)) {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = "Invalid URL. HTTPS required.",
                connectionTestSuccess = false,
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingConnection = true,
                connectionTestResult = null,
                connectionTestSuccess = false,
            )
            authRepository.saveBaseUrl(url)
            val result = authRepository.testConnection()
            result.onSuccess { message ->
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = message,
                    connectionTestSuccess = true,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = "Connection failed: ${e.message}",
                    connectionTestSuccess = false,
                )
            }
        }
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, loginError = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, loginError = null)
    }

    fun login() {
        val snapshot = _uiState.value
        if (snapshot.email.isBlank() || snapshot.password.isBlank()) {
            _uiState.value = snapshot.copy(loginError = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingIn = true, loginError = null)
            // Read latest state inside coroutine to avoid stale captures
            val current = _uiState.value
            val result = authRepository.login(
                current.baseUrl.trim(),
                current.email.trim(),
                current.password,
                viewModelScope,
            )
            if (result.success) {
                appSettingsStore.onboardingComplete = true
                _uiState.value = _uiState.value.copy(
                    isLoggingIn = false,
                    onboardingComplete = true,
                    password = "",
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoggingIn = false,
                    loginError = result.error,
                    password = "",
                )
            }
        }
    }

    /** Returns the starting page index for fresh install vs returning after logout. */
    fun getStartPage(): Int {
        val savedUrl = authRepository.getBaseUrl()
        return if (!savedUrl.isNullOrBlank()) OnboardingPages.SERVER else OnboardingPages.WELCOME
    }
}
