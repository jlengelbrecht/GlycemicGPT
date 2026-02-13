package com.glycemicgpt.mobile.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glycemicgpt.mobile.data.remote.GlycemicGptApi
import com.glycemicgpt.mobile.data.remote.dto.TandemUploadSettingsRequest
import com.glycemicgpt.mobile.data.remote.dto.TandemUploadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class TandemCloudSyncUiState(
    val isLoading: Boolean = true,
    val enabled: Boolean = false,
    val intervalMinutes: Int = 15,
    val lastUploadAt: String? = null,
    val lastUploadStatus: String? = null,
    val lastError: String? = null,
    val pendingEvents: Int = 0,
    val isSaving: Boolean = false,
    val isTriggering: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class TandemCloudSyncViewModel @Inject constructor(
    private val api: GlycemicGptApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TandemCloudSyncUiState())
    val uiState: StateFlow<TandemCloudSyncUiState> = _uiState.asStateFlow()

    init {
        loadStatus()
    }

    fun loadStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = api.getTandemUploadStatus()
                if (response.isSuccessful) {
                    response.body()?.let { applyStatus(it) }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load status: HTTP ${response.code()}",
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load Tandem upload status")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Network error",
                )
            }
        }
    }

    fun updateSettings(enabled: Boolean, intervalMinutes: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val response = api.updateTandemUploadSettings(
                    TandemUploadSettingsRequest(
                        enabled = enabled,
                        intervalMinutes = intervalMinutes,
                    ),
                )
                if (response.isSuccessful) {
                    response.body()?.let { applyStatus(it) }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = "Failed to save: HTTP ${response.code()}",
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to update Tandem upload settings")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Network error",
                )
            }
        }
    }

    fun triggerUploadNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTriggering = true, errorMessage = null)
            try {
                val response = api.triggerTandemUpload()
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isTriggering = false)
                    // Reload full status after trigger completes
                    loadStatus()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isTriggering = false,
                        errorMessage = "Upload failed: HTTP ${response.code()}",
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to trigger Tandem upload")
                _uiState.value = _uiState.value.copy(
                    isTriggering = false,
                    errorMessage = e.message ?: "Network error",
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun applyStatus(status: TandemUploadStatus) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isSaving = false,
            enabled = status.enabled,
            intervalMinutes = status.uploadIntervalMinutes,
            lastUploadAt = status.lastUploadAt,
            lastUploadStatus = status.lastUploadStatus,
            lastError = status.lastError,
            pendingEvents = status.pendingEvents,
        )
    }
}
