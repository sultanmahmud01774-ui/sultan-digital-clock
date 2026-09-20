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
import com.example.data.model.ClockModel
import com.example.data.model.ConnectionStatus
import com.example.data.storage.DevicePreferences
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
                    text = "ESP32 & ESP8266 Dual Smart Clock Controller",
                    fontSize = 13.sp,
                    color = CyanAccent,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Hardware Module Selector Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SELECT CLOCK HARDWARE / মডিউল নির্বাচন:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ESP32 Chip Option
                            val isEsp32 = uiState.selectedModel == ClockModel.ESP32
                            Surface(
                                onClick = {
                                    viewModel.setClockModel(ClockModel.ESP32)
                                    viewModel.setHost(DevicePreferences.DEFAULT_MDNS_HOST)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isEsp32) CyanAccent.copy(alpha = 0.15f) else CardBackgroundElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isEsp32) CyanAccent else CardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("select_esp32_tab")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = if (isEsp32) CyanAccent else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "ESP32 Edition",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isEsp32) CyanAccent else TextPrimary
                                        )
                                        Text(
                                            text = "Audio & DFPlayer",
                                            fontSize = 9.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            // ESP8266 Chip Option
                            val isEsp8266 = uiState.selectedModel == ClockModel.ESP8266
                            Surface(
                                onClick = {
                                    viewModel.setClockModel(ClockModel.ESP8266)
                                    viewModel.setHost(DevicePreferences.DEFAULT_AP_IP)
                                    onNavigateToDashboard()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isEsp8266) AmberOrange.copy(alpha = 0.15f) else CardBackgroundElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isEsp8266) AmberOrange else CardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("select_esp8266_tab")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                     Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = if (isEsp8266) AmberOrange else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "ESP8266 Edition",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isEsp8266) AmberOrange else TextPrimary
                                        )
                                        Text(
                                            text = "Clock & Buzzer",
                                            fontSize = 9.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Open Controller / Direct Access Button
                    OutlinedButton(
                        onClick = { onNavigateToDashboard() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_dashboard_direct_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OPEN CONTROLLER (ড্যাশবোর্ড খুলুন)",
                            fontSize = 13.sp,
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

                    // Method B: Clock Hotspot Access Point
                    val apName = if (uiState.selectedModel == ClockModel.ESP8266) {
                        DevicePreferences.DEFAULT_ESP8266_AP_SSID
                    } else {
                        DevicePreferences.DEFAULT_ESP32_AP_SSID
                    }
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
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = if (uiState.selectedModel == ClockModel.ESP8266) AmberOrange else CyanAccent
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.selectedModel == ClockModel.ESP8266) {
                                        "Method B-Hotspot AP(SULTAN CLOCK -ESP8266)"
                                    } else {
                                        "Method B-Hotspot AP(SULTAN CLOCK_AP)"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "http://192.168.4.1 (Connect phone to clock Wi-Fi)",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }

                    if (uiState.selectedModel == ClockModel.ESP8266) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.resetDefaultAdminPassword()
                                },
                            color = CardBackgroundElevated
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LockReset, contentDescription = null, tint = WarningOrange)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Forgot Password? Reset to sultan88", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarningOrange)
                                    Text("Resets clock password back to factory sultan88", fontSize = 11.sp, color = TextSecondary)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                            }
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
