package com.glycemicgpt.mobile.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glycemicgpt.mobile.domain.model.BatteryStatus
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.ReservoirReading
import com.glycemicgpt.mobile.presentation.theme.GlucoseColors
import com.glycemicgpt.mobile.service.SyncStatus
import kotlinx.coroutines.delay
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val cgm by viewModel.cgm.collectAsState()
    val iob by viewModel.iob.collectAsState()
    val basalRate by viewModel.basalRate.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val reservoir by viewModel.reservoir.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val cgmHistory by viewModel.cgmHistory.collectAsState()
    val iobHistory by viewModel.iobHistory.collectAsState()
    val basalHistory by viewModel.basalHistory.collectAsState()
    val bolusHistory by viewModel.bolusHistory.collectAsState()
    val thresholds = viewModel.glucoseThresholds

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Connection + sync status
            ConnectionStatusBanner(connectionState)
            Spacer(modifier = Modifier.height(4.dp))
            SyncStatusBanner(syncStatus)

            Spacer(modifier = Modifier.height(16.dp))

            // Glucose hero: large current BG + trend + IoB + Basal
            GlucoseHero(
                cgm = cgm,
                iob = iob,
                basalRate = basalRate,
                thresholds = thresholds,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Glucose trend chart with IoB, basal, and bolus overlays
            GlucoseTrendChart(
                readings = cgmHistory,
                iobReadings = iobHistory,
                basalReadings = basalHistory,
                bolusEvents = bolusHistory,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { viewModel.onPeriodSelected(it) },
                thresholds = thresholds,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Compact pump status row
            PumpStatusRow(
                battery = battery,
                reservoir = reservoir,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (connectionState == ConnectionState.DISCONNECTED) {
                Text(
                    text = "Pair your pump in Settings to start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (connectionState == ConnectionState.CONNECTED && cgm == null) {
                Text(
                    text = "Loading pump data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun PumpStatusRow(
    battery: BatteryStatus?,
    reservoir: ReservoirReading?,
) {
    val batteryIcon = when {
        battery == null -> Icons.Default.Battery0Bar
        battery.percentage >= 95 -> Icons.Default.BatteryFull
        battery.percentage >= 70 -> Icons.Default.Battery5Bar
        battery.percentage >= 40 -> Icons.Default.Battery3Bar
        battery.percentage >= 15 -> Icons.Default.Battery1Bar
        else -> Icons.Default.Battery0Bar
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Battery indicator
        Icon(
            imageVector = batteryIcon,
            contentDescription = "Battery",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = battery?.let { "${it.percentage}%" } ?: "--%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("battery_card"),
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Reservoir indicator
        Icon(
            imageVector = Icons.Default.Opacity,
            contentDescription = "Reservoir",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = reservoir?.let { "%.0fu".format(it.unitsRemaining) } ?: "--u",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("reservoir_card"),
        )
    }
}

@Composable
private fun ConnectionStatusBanner(state: ConnectionState) {
    val (icon, text, color) = when (state) {
        ConnectionState.CONNECTED -> Triple(
            Icons.Default.BluetoothConnected,
            "Pump connected",
            MaterialTheme.colorScheme.primary,
        )
        ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> Triple(
            Icons.AutoMirrored.Filled.BluetoothSearching,
            "Connecting...",
            MaterialTheme.colorScheme.tertiary,
        )
        ConnectionState.RECONNECTING -> Triple(
            Icons.Default.Bluetooth,
            "Reconnecting...",
            MaterialTheme.colorScheme.tertiary,
        )
        ConnectionState.SCANNING -> Triple(
            Icons.AutoMirrored.Filled.BluetoothSearching,
            "Scanning...",
            MaterialTheme.colorScheme.tertiary,
        )
        ConnectionState.AUTH_FAILED -> Triple(
            Icons.Default.BluetoothDisabled,
            "Pairing failed",
            MaterialTheme.colorScheme.error,
        )
        ConnectionState.DISCONNECTED -> Triple(
            Icons.Default.BluetoothDisabled,
            "No pump connected",
            MaterialTheme.colorScheme.error,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connection_status"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun SyncStatusBanner(status: SyncStatus) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val (icon, text, color) = when {
        status.lastError != null -> Triple(
            Icons.Default.CloudOff,
            "Sync error",
            MaterialTheme.colorScheme.error,
        )
        status.pendingCount > 0 -> Triple(
            Icons.Default.CloudSync,
            "${status.pendingCount} pending",
            MaterialTheme.colorScheme.tertiary,
        )
        status.lastSyncAtMs > 0 -> {
            val ago = (now - status.lastSyncAtMs) / 1000
            val label = when {
                ago < 60 -> "just now"
                ago < 3600 -> "${ago / 60}m ago"
                else -> "${ago / 3600}h ago"
            }
            Triple(
                Icons.Default.CloudDone,
                "Synced $label",
                MaterialTheme.colorScheme.primary,
            )
        }
        else -> Triple(
            Icons.Default.CloudOff,
            "Not synced",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
internal fun FreshnessLabel(timestamp: Instant) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val ageSeconds = (now - timestamp.toEpochMilli()) / 1000
    val label = when {
        ageSeconds < 60 -> "just now"
        ageSeconds < 3600 -> "${ageSeconds / 60}m ago"
        ageSeconds < 86400 -> "${ageSeconds / 3600}h ago"
        else -> "stale"
    }

    val color = when {
        ageSeconds < 120 -> GlucoseColors.InRange
        ageSeconds < 600 -> GlucoseColors.High
        else -> GlucoseColors.UrgentHigh
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}
