package com.glycemicgpt.mobile.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glycemicgpt.mobile.data.repository.PumpDataRepository
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.CgmReading
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import com.glycemicgpt.mobile.service.BackendSyncManager
import com.glycemicgpt.mobile.service.SyncStatus
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Home screen.
 *
 * Observes the latest readings from the local Room database (populated by
 * PumpPollingOrchestrator). Also supports manual refresh via [refreshData].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pumpDriver: PumpDriver,
    private val repository: PumpDataRepository,
    private val backendSyncManager: BackendSyncManager,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = pumpDriver.observeConnectionState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val iob: StateFlow<IoBReading?> = repository.observeLatestIoB()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val basalRate: StateFlow<BasalReading?> = repository.observeLatestBasal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val battery: StateFlow<BatteryStatus?> = repository.observeLatestBattery()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val reservoir: StateFlow<ReservoirReading?> = repository.observeLatestReservoir()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cgm: StateFlow<CgmReading?> = repository.observeLatestCgm()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncStatus: StateFlow<SyncStatus> = backendSyncManager.syncStatus

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Manually trigger a pump data refresh. Reads are saved to the repository,
     * which will automatically update the observed StateFlows above.
     */
    fun refreshData() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                coroutineScope {
                    launch {
                        pumpDriver.getIoB().onSuccess { repository.saveIoB(it) }
                    }
                    launch {
                        pumpDriver.getBasalRate().onSuccess { repository.saveBasal(it) }
                    }
                    launch {
                        pumpDriver.getBatteryStatus().onSuccess { repository.saveBattery(it) }
                    }
                    launch {
                        pumpDriver.getReservoirLevel().onSuccess { repository.saveReservoir(it) }
                    }
                    launch {
                        pumpDriver.getCgmStatus().onSuccess { repository.saveCgm(it) }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error during manual data refresh")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
