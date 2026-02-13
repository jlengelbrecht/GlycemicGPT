package com.glycemicgpt.mobile.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glycemicgpt.mobile.domain.model.BasalReading
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.IoBReading
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pumpDriver: PumpDriver,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = pumpDriver.observeConnectionState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    private val _iob = MutableStateFlow<IoBReading?>(null)
    val iob: StateFlow<IoBReading?> = _iob.asStateFlow()

    private val _basalRate = MutableStateFlow<BasalReading?>(null)
    val basalRate: StateFlow<BasalReading?> = _basalRate.asStateFlow()

    private val _battery = MutableStateFlow<BatteryStatus?>(null)
    val battery: StateFlow<BatteryStatus?> = _battery.asStateFlow()

    private val _reservoir = MutableStateFlow<ReservoirReading?>(null)
    val reservoir: StateFlow<ReservoirReading?> = _reservoir.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshData() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                coroutineScope {
                    launch { pumpDriver.getIoB().onSuccess { _iob.value = it } }
                    launch { pumpDriver.getBasalRate().onSuccess { _basalRate.value = it } }
                    launch { pumpDriver.getBatteryStatus().onSuccess { _battery.value = it } }
                    launch { pumpDriver.getReservoirLevel().onSuccess { _reservoir.value = it } }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
