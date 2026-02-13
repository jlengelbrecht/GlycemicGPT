package com.glycemicgpt.mobile.presentation.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onNavigateToPairing: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    tandemCloudSyncViewModel: TandemCloudSyncViewModel = hiltViewModel(),
) {
    val state by settingsViewModel.uiState.collectAsState()
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

        // -- Account Section --
        SectionHeader(title = "Account")
        AccountSection(
            state = state,
            onSaveUrl = settingsViewModel::saveBaseUrl,
            onTestConnection = settingsViewModel::testConnection,
            onLogin = settingsViewModel::login,
            onShowLogout = settingsViewModel::showLogoutConfirm,
            onClearConnectionResult = settingsViewModel::clearConnectionTestResult,
            onClearLoginError = settingsViewModel::clearLoginError,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // -- Pump Section --
        SectionHeader(title = "Pump")
        PumpSection(
            state = state,
            onNavigateToPairing = onNavigateToPairing,
            onShowUnpair = settingsViewModel::showUnpairConfirm,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // -- Sync Section --
        SectionHeader(title = "Sync")
        SyncSection(
            state = state,
            cloudSyncState = cloudSyncState,
            onBackendSyncToggle = settingsViewModel::setBackendSyncEnabled,
            onRetentionChange = settingsViewModel::setDataRetentionDays,
            onCloudSyncToggle = { enabled ->
                tandemCloudSyncViewModel.updateSettings(
                    enabled = enabled,
                    intervalMinutes = cloudSyncState.intervalMinutes,
                )
            },
            onCloudIntervalChange = { interval ->
                tandemCloudSyncViewModel.updateSettings(
                    enabled = cloudSyncState.enabled,
                    intervalMinutes = interval,
                )
            },
            onUploadNow = { tandemCloudSyncViewModel.triggerUploadNow() },
        )

        Spacer(modifier = Modifier.height(20.dp))

        // -- About Section --
        SectionHeader(title = "About")
        AboutSection(
            state = state,
            onCheckForUpdate = settingsViewModel::checkForUpdate,
            onDownloadUpdate = { url, size -> settingsViewModel.downloadAndInstallUpdate(url, size) },
            onGetInstallIntent = settingsViewModel::getInstallIntent,
            onDismissUpdate = settingsViewModel::dismissUpdateState,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Dialogs
    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = settingsViewModel::dismissLogoutConfirm,
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out? Pump data sync will pause until you sign in again.") },
            confirmButton = {
                TextButton(onClick = settingsViewModel::logout) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = settingsViewModel::dismissLogoutConfirm) {
                    Text("Cancel")
                }
            },
        )
    }

    if (state.showUnpairConfirm) {
        AlertDialog(
            onDismissRequest = settingsViewModel::dismissUnpairConfirm,
            title = { Text("Unpair Pump") },
            text = { Text("Are you sure you want to unpair this pump? You will need to re-pair to resume data collection.") },
            confirmButton = {
                TextButton(onClick = settingsViewModel::unpair) {
                    Text("Unpair", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = settingsViewModel::dismissUnpairConfirm) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun AccountSection(
    state: SettingsUiState,
    onSaveUrl: (String) -> Unit,
    onTestConnection: () -> Unit,
    onLogin: (String, String) -> Unit,
    onShowLogout: () -> Unit,
    onClearConnectionResult: () -> Unit,
    onClearLoginError: () -> Unit,
) {
    var urlInput by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Server Connection",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://your-server.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onSaveUrl(urlInput)
                        onTestConnection()
                    },
                    enabled = !state.isTestingConnection && urlInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (state.isTestingConnection) "Testing..." else "Test Connection")
                }
            }

            state.connectionTestResult?.let { result ->
                Spacer(modifier = Modifier.height(4.dp))
                val isSuccess = result.contains("successfully")
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Login / Auth section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Authentication",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoggedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = state.userEmail ?: "Signed in",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Authenticated",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onShowLogout,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Sign Out")
                    }
                }
            } else {
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        onClearLoginError()
                    },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearLoginError()
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                state.loginError?.let { error ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !state.isLoggingIn && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (state.isLoggingIn) "Signing in..." else "Sign In")
                }
            }
        }
    }
}

@Composable
private fun PumpSection(
    state: SettingsUiState,
    onNavigateToPairing: () -> Unit,
    onShowUnpair: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.isPumpPaired) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Tandem t:slim X2",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (state.isPumpPaired) {
                            "Paired: ${state.pairedPumpAddress}"
                        } else {
                            "Not paired"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.isPumpPaired) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onNavigateToPairing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Re-pair")
                    }
                    OutlinedButton(
                        onClick = onShowUnpair,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Unpair")
                    }
                }
            } else {
                Button(
                    onClick = onNavigateToPairing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pair Pump")
                }
            }
        }
    }
}

@Composable
private fun SyncSection(
    state: SettingsUiState,
    cloudSyncState: TandemCloudSyncUiState,
    onBackendSyncToggle: (Boolean) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onCloudSyncToggle: (Boolean) -> Unit,
    onCloudIntervalChange: (Int) -> Unit,
    onUploadNow: () -> Unit,
) {
    // Backend Sync toggle
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
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Backend Sync",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Push pump events to GlycemicGPT server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = state.backendSyncEnabled,
                    onCheckedChange = onBackendSyncToggle,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Tandem Cloud Sync card (existing)
    TandemCloudSyncCard(
        state = cloudSyncState,
        onToggle = onCloudSyncToggle,
        onIntervalChange = onCloudIntervalChange,
        onUploadNow = onUploadNow,
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Data Retention
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            var sliderValue by remember(state.dataRetentionDays) {
                mutableStateOf(state.dataRetentionDays.toFloat())
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Data Retention",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Keep local data for ${sliderValue.roundToInt()} days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onRetentionChange(sliderValue.roundToInt()) },
                valueRange = 1f..30f,
                steps = 28,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "1 day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "30 days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AboutSection(
    state: SettingsUiState,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: (String, Long) -> Unit,
    onGetInstallIntent: (File) -> android.content.Intent,
    onDismissUpdate: () -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "App Info",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(label = "Version", value = state.appVersion)
            InfoRow(label = "Build", value = state.buildType)
            InfoRow(label = "BLE Protocol", value = "pumpX2 v1.0")

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Open Source Licenses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // App Update
            when (val updateState = state.updateState) {
                is UpdateUiState.Idle -> {
                    OutlinedButton(
                        onClick = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Check for Updates")
                    }
                }

                is UpdateUiState.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Checking for updates...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is UpdateUiState.UpToDate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "You are on the latest version",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Check Again")
                    }
                }

                is UpdateUiState.Available -> {
                    Column {
                        Text(
                            text = "Update available: v${updateState.version}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val sizeMb = updateState.sizeBytes / (1024.0 * 1024.0)
                        Text(
                            text = "Size: ${"%.1f".format(sizeMb)} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        updateState.releaseNotes?.let { notes ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onDownloadUpdate(updateState.downloadUrl, updateState.sizeBytes) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Download & Install")
                            }
                            OutlinedButton(
                                onClick = onDismissUpdate,
                            ) {
                                Text("Later")
                            }
                        }
                    }
                }

                is UpdateUiState.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Downloading update...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is UpdateUiState.ReadyToInstall -> {
                    Button(
                        onClick = {
                            val intent = onGetInstallIntent(updateState.apkFile)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Install Update")
                    }
                }

                is UpdateUiState.Error -> {
                    Column {
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onCheckForUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
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
