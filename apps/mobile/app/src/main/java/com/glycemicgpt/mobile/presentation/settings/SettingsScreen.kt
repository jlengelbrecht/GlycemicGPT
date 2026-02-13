package com.glycemicgpt.mobile.presentation.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onNavigateToPairing: () -> Unit = {},
    tandemCloudSyncViewModel: TandemCloudSyncViewModel = hiltViewModel(),
) {
    val cloudSyncState by tandemCloudSyncViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pump Pairing card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToPairing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Pump Pairing",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Connect to your Tandem t:slim X2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Navigate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tandem Cloud Sync card
        TandemCloudSyncCard(
            state = cloudSyncState,
            onToggle = { enabled ->
                tandemCloudSyncViewModel.updateSettings(
                    enabled = enabled,
                    intervalMinutes = cloudSyncState.intervalMinutes,
                )
            },
            onIntervalChange = { interval ->
                tandemCloudSyncViewModel.updateSettings(
                    enabled = cloudSyncState.enabled,
                    intervalMinutes = interval,
                )
            },
            onUploadNow = { tandemCloudSyncViewModel.triggerUploadNow() },
        )
    }
}

@Composable
private fun TandemCloudSyncCard(
    state: TandemCloudSyncUiState,
    onToggle: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onUploadNow: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tandem Cloud Sync",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Upload data to t:connect for your endo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onToggle,
                        enabled = !state.isSaving,
                    )
                }
            }

            if (state.enabled && !state.isLoading) {
                Spacer(modifier = Modifier.height(12.dp))

                // Interval selector
                Text(
                    text = "Upload interval",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { mins ->
                        val selected = state.intervalMinutes == mins
                        TextButton(
                            onClick = { onIntervalChange(mins) },
                            enabled = !state.isSaving,
                        ) {
                            Text(
                                text = "${mins}m",
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = if (selected) {
                                    MaterialTheme.typography.labelLarge
                                } else {
                                    MaterialTheme.typography.labelMedium
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusIcon = when (state.lastUploadStatus) {
                        "success" -> Icons.Default.CheckCircle
                        "error" -> Icons.Default.Error
                        else -> Icons.Default.Schedule
                    }
                    val statusColor = when (state.lastUploadStatus) {
                        "success" -> MaterialTheme.colorScheme.primary
                        "error" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val statusText = when {
                        state.lastUploadAt != null -> "Last upload: ${state.lastUploadAt}"
                        else -> "No uploads yet"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.pendingEvents > 0) {
                    Text(
                        text = "${state.pendingEvents} events pending upload",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.lastError != null) {
                    Text(
                        text = state.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Upload Now button
                Button(
                    onClick = onUploadNow,
                    enabled = !state.isTriggering && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isTriggering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = if (state.isTriggering) "Uploading..." else "Upload Now")
                }
            }

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
