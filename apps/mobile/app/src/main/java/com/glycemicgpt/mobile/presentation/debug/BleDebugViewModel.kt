package com.glycemicgpt.mobile.presentation.debug

import androidx.lifecycle.ViewModel
import com.glycemicgpt.mobile.ble.connection.BleConnectionManager
import com.glycemicgpt.mobile.data.local.BleDebugStore
import com.glycemicgpt.mobile.domain.model.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class BleDebugViewModel @Inject constructor(
    private val debugStore: BleDebugStore,
    private val connectionManager: BleConnectionManager,
) : ViewModel() {

    val entries: StateFlow<List<BleDebugStore.Entry>> = debugStore.entries
    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    fun clearEntries() {
        debugStore.clear()
    }
}
