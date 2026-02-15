package com.glycemicgpt.mobile.presentation.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glycemicgpt.mobile.domain.model.CgmTrend
import com.glycemicgpt.mobile.domain.model.ConnectionState
import com.glycemicgpt.mobile.domain.model.ControlIqMode
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
    val iob by viewModel.iob.collectAsState()
    val basalRate by viewModel.basalRate.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val reservoir by viewModel.reservoir.collectAsState()
    val cgm by viewModel.cgm.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Data refresh is handled by PumpPollingOrchestrator (staggered reads
    // with initial delay). Manual refresh is available via pull-to-refresh.
    // HomeViewModel observes Room, so data appears as the orchestrator writes it.

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
            // Connection status banner
            ConnectionStatusBanner(connectionState)

            Spacer(modifier = Modifier.height(4.dp))

            // Sync status banner
            SyncStatusBanner(syncStatus)

            Spacer(modifier = Modifier.height(20.dp))

            // IoB card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Insulin on Board",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = iob?.let { "%.2f".format(it.iob) } ?: "--",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("iob_value"),
                    )
                    Text(
                        text = "units",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    iob?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        FreshnessLabel(it.timestamp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status cards row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(
                    title = "Basal Rate",
                    value = basalRate?.let { "%.2f".format(it.rate) } ?: "--",
                    unit = "u/hr",
                    modifier = Modifier.weight(1f).testTag("basal_card"),
                    timestamp = basalRate?.timestamp,
                    badge = basalRate?.let {
                        when (it.controlIqMode) {
                            ControlIqMode.SLEEP -> "Sleep"
                            ControlIqMode.EXERCISE -> "Exercise"
                            ControlIqMode.STANDARD -> if (it.isAutomated) "Auto" else null
                        }
                    },
                    badgeColor = when (basalRate?.controlIqMode) {
                        ControlIqMode.SLEEP -> MaterialTheme.colorScheme.secondary
                        ControlIqMode.EXERCISE -> GlucoseColors.High
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                StatusCard(
                    title = "Reservoir",
                    value = reservoir?.let { "%.0f".format(it.unitsRemaining) } ?: "--",
                    unit = "units",
                    modifier = Modifier.weight(1f).testTag("reservoir_card"),
                    timestamp = reservoir?.timestamp,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status cards row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(
                    title = "Battery",
                    value = battery?.let { "${it.percentage}" } ?: "--",
                    unit = "%",
                    modifier = Modifier.weight(1f).testTag("battery_card"),
                    timestamp = battery?.timestamp,
                )
                StatusCard(
                    title = "Last BG",
                    value = cgm?.let { "${it.glucoseMgDl}" } ?: "--",
                    unit = "mg/dL",
                    modifier = Modifier.weight(1f).testTag("cgm_card"),
                    timestamp = cgm?.timestamp,
                    badge = cgm?.let { trendArrowSymbol(it.trendArrow) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (connectionState == ConnectionState.DISCONNECTED) {
                Text(
                    text = "Pair your pump in Settings to start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (connectionState == ConnectionState.CONNECTED && iob == null) {
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
            val ago = (System.currentTimeMillis() - status.lastSyncAtMs) / 1000
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
private fun StatusCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    timestamp: Instant? = null,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title row with optional Control-IQ mode badge
            if (badge != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier
                            .background(
                                badgeColor.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Data freshness indicator
            if (timestamp != null) {
                Spacer(modifier = Modifier.height(4.dp))
                FreshnessLabel(timestamp)
            }
        }
    }
}

private fun trendArrowSymbol(trend: CgmTrend): String = when (trend) {
    CgmTrend.DOUBLE_UP -> "\u21C8"
    CgmTrend.SINGLE_UP -> "\u2191"
    CgmTrend.FORTY_FIVE_UP -> "\u2197"
    CgmTrend.FLAT -> "\u2192"
    CgmTrend.FORTY_FIVE_DOWN -> "\u2198"
    CgmTrend.SINGLE_DOWN -> "\u2193"
    CgmTrend.DOUBLE_DOWN -> "\u21CA"
    CgmTrend.UNKNOWN -> "?"
}

@Composable
private fun FreshnessLabel(timestamp: Instant) {
    // Re-compose every 30 seconds to keep "Xm ago" text up to date
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
