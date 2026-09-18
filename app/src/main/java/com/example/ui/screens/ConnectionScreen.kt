package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel
import com.example.ui.viewmodel.SultanClockUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    viewModel: ClockViewModel,
    uiState: SultanClockUiState,
    onNavigateToDashboard: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.connectionStatus) {
        if (uiState.connectionStatus == ConnectionStatus.CONNECTED) {
            onNavigateToDashboard()
        }
    }

    Scaffold(
        containerColor = DeepBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(28.dp))

                // App Hero Icon & Title
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(GoldPrimary.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                        .border(1.5.dp, GoldPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Sultan Clock",
                        tint = GoldPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SULTAN DIGITAL CLOCK",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "ESP32 Smart Clock Controller",
                    fontSize = 13.sp,
                    color = CyanAccent,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Connection Status Banner
            item {
                GlassCard(
                    borderColor = when (uiState.connectionStatus) {
                        ConnectionStatus.CONNECTED -> SuccessGreen
                        ConnectionStatus.CONNECTING -> GoldPrimary
                        ConnectionStatus.AUTH_REQUIRED -> WarningOrange
                        ConnectionStatus.ERROR, ConnectionStatus.DISCONNECTED -> ErrorRed
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.connectionStatus == ConnectionStatus.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = GoldPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            val icon = when (uiState.connectionStatus) {
                                ConnectionStatus.CONNECTED -> Icons.Default.CheckCircle
                                ConnectionStatus.AUTH_REQUIRED -> Icons.Default.Lock
                                else -> Icons.Default.WifiTetheringError
                            }
                            val tint = when (uiState.connectionStatus) {
                                ConnectionStatus.CONNECTED -> SuccessGreen
                                ConnectionStatus.AUTH_REQUIRED -> WarningOrange
                                else -> ErrorRed
                            }
                            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            val statusLabel = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                                if (uiState.dashboard.apMode) {
                                    "Connected via Clock's Hotspot (AP mode)"
                                } else if (uiState.dashboard.wifiConnected) {
                                    if (uiState.dashboard.wifiSsid.isNotBlank()) "Connected via Home WiFi: ${uiState.dashboard.wifiSsid}" else "Connected via Home WiFi"
                                } else {
                                    "WiFi Disconnected"
                                }
                            } else {
                                "Status: ${uiState.connectionStatus.name}"
                            }

                            val labelColor = if (uiState.connectionStatus == ConnectionStatus.CONNECTED && !uiState.dashboard.apMode && !uiState.dashboard.wifiConnected) {
                                WarningOrange
                            } else {
                                TextPrimary
                            }

                            Text(
                                text = statusLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = labelColor
                            )
                            Text(
                                text = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) "Target IP / Host: ${uiState.activeHost}" else uiState.connectionMessage,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontFamily = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) FontFamily.Monospace else FontFamily.Default
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Authentication & IP Configuration Card
            item {
                GlassCard {
                    Text(
                        text = "ESP32 Web UI Authentication",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                    Text(
                        text = "Enter the HTTP Basic Auth credentials configured on your ESP32.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Target Host / IP Selector
                    OutlinedTextField(
                        value = uiState.activeHost,
                        onValueChange = { viewModel.setHost(it) },
                        label = { Text("Target Host / IP Address") },
                        leadingIcon = { Icon(Icons.Default.Router, contentDescription = null, tint = CyanAccent) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.toggleManualIpDialog(true) }) {
                                Icon(Icons.Default.List, contentDescription = "Saved IPs", tint = GoldPrimary)
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("host_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Username field (fixed admin by default)
                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = { viewModel.setUsername(it) },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password field
                    OutlinedTextField(
                        value = uiState.passwordInput,
                        onValueChange = { viewModel.setPassword(it) },
                        label = { Text("Web UI Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = TextSecondary
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.connectToClock() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Switches: Remember Password & Auto Connect
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Remember Password", color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = uiState.rememberPassword,
                            onCheckedChange = { viewModel.setRememberPassword(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldPrimary,
                                checkedTrackColor = GoldPrimary.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Connect on Launch", color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = uiState.autoConnect,
                            onCheckedChange = { viewModel.setAutoConnect(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyanAccent,
                                checkedTrackColor = CyanAccent.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Connect Button
                    Button(
                        onClick = { viewModel.connectToClock() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("connect_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300))
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.connectionStatus == ConnectionStatus.CONNECTING) "CONNECTING..." else "CONNECT TO CLOCK",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Quick Connection Presets (mDNS vs AP vs LAN)
            item {
                GlassCard {
                    Text(
                        text = "Connection Methods",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Method A: mDNS
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setHost("sultanclock.local")
                                viewModel.connectToClock("sultanclock.local")
                            },
                        color = CardBackgroundElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = GoldPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Method A — mDNS Host", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("http://sultanclock.local", fontSize = 12.sp, color = CyanAccent, fontFamily = FontFamily.Monospace)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Method B: ESP32 Access Point
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setHost("192.168.4.1")
                                viewModel.connectToClock("192.168.4.1")
                            },
                        color = CardBackgroundElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = CyanAccent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Method B — Clock Hotspot AP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("http://192.168.4.1 (Connect phone to clock Wi-Fi)", fontSize = 12.sp, color = TextSecondary)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Manual IP / Saved IPs Dialog
    if (uiState.isManualIpDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleManualIpDialog(false) },
            title = {
                Text(
                    text = "Saved Clock Devices & IPs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = uiState.manualIpInput,
                        onValueChange = { viewModel.updateManualIpInput(it) },
                        label = { Text("Enter IP or Hostname") },
                        placeholder = { Text("e.g. 192.168.1.120") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Saved Targets:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    uiState.savedIps.forEach { ip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBackgroundElevated)
                                .clickable {
                                    viewModel.updateManualIpInput(ip)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = ip, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            IconButton(
                                onClick = { viewModel.removeSavedIp(ip) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveManualIp() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300))
                ) {
                    Text("Select & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleManualIpDialog(false) }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardBackground
        )
    }
}
