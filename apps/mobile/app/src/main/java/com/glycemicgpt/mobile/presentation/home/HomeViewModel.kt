package com.glycemicgpt.mobile.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glycemicgpt.mobile.data.repository.PumpDataRepository
import timber.log.Timber
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * Manually trigger a pump data refresh. Reads are saved to the repository,
     * which will automatically update the observed StateFlows above.
     */
    fun refreshData() {
        viewModelScope.launch {
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
                }
            } catch (e: Exception) {
                Timber.w(e, "Error during manual data refresh")
            }
        }
    }
}
