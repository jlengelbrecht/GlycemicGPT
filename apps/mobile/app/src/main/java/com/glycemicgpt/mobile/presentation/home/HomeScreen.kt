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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glycemicgpt.mobile.domain.model.ConnectionState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val iob by viewModel.iob.collectAsState()
    val basalRate by viewModel.basalRate.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val reservoir by viewModel.reservoir.collectAsState()

    // Auto-refresh when newly connected (debounced to avoid rapid-fire
    // during fast CONNECTING -> CONNECTED -> DISCONNECTED transitions)
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            kotlinx.coroutines.delay(300)
            viewModel.refreshData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Connection status banner
        ConnectionStatusBanner(connectionState)

        Spacer(modifier = Modifier.height(24.dp))

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
                )
                Text(
                    text = "units",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                modifier = Modifier.weight(1f),
            )
            StatusCard(
                title = "Reservoir",
                value = reservoir?.let { "%.0f".format(it.unitsRemaining) } ?: "--",
                unit = "units",
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.weight(1f),
            )
            StatusCard(
                title = "Last BG",
                value = "--",
                unit = "mg/dL",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (connectionState == ConnectionState.DISCONNECTED) {
            Text(
                text = "Pair your pump in Settings to start",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        ConnectionState.DISCONNECTED -> Triple(
            Icons.Default.BluetoothDisabled,
            "No pump connected",
            MaterialTheme.colorScheme.error,
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
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "  $text",
            style = MaterialTheme.typography.bodyMedium,
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
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        }
    }
}
