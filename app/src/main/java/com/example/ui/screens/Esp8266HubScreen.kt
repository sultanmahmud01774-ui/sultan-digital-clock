package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel
import com.example.ui.viewmodel.SultanClockUiState

// 13 RTTTL Built-in Tones from the ESP8266 Firmware
val ESP8266_TONE_NAMES = listOf(
    "Smooth Criminal",
    "Gorilla Clint",
    "Place 4 My Head",
    "Mambo Italiano",
    "Bad Boys",
    "NSync Pop",
    "Groovy Blue",
    "Jingle Bells",
    "Nokia Ring",
    "Retro Groove",
    "Doom Level 1",
    "Contra",
    "Mario Real"
)

// 12 Built-in Color Palette Swatches from the ESP8266 Firmware
data class Esp8266ColorItem(val index: Int, val name: String, val hex: Long, val r: Int, val g: Int, val b: Int)
val ESP8266_PALETTE = listOf(
    Esp8266ColorItem(0, "Red", 0xFFFF0000, 255, 0, 0),
    Esp8266ColorItem(1, "Yellow", 0xFFFFFF00, 255, 255, 0),
    Esp8266ColorItem(2, "Orange", 0xFFFFA500, 255, 165, 0),
    Esp8266ColorItem(3, "Gold", 0xFFFFC040, 255, 192, 64),
    Esp8266ColorItem(4, "Cyan", 0xFF00FFFF, 0, 255, 255),
    Esp8266ColorItem(5, "Green", 0xFF00FF00, 0, 255, 0),
    Esp8266ColorItem(6, "Teal", 0xFF00C88C, 0, 200, 140),
    Esp8266ColorItem(7, "Purple", 0xFF8000FF, 128, 0, 255),
    Esp8266ColorItem(8, "Magenta", 0xFFFF00FF, 255, 0, 255),
    Esp8266ColorItem(9, "Blue", 0xFF0080FF, 0, 128, 255),
    Esp8266ColorItem(10, "Deep Blue", 0xFF0028FF, 0, 40, 255),
    Esp8266ColorItem(11, "White", 0xFFFFFFFF, 255, 255, 255)
)

/**
 * Dedicated ESP8266 Clock Hub
 * Complete, independent control architecture matching 100% of the ESP8266 Arduino Firmware:
 * 1. Setup Wizard (Hotspot AP 192.168.4.1, Wi-Fi Scan with RSSI, Static IP 192.168.0.108, Safe 15s Reboot)
 * 2. 3D Live Virtual LED Clock Mirror (Live display status, color preview)
 * 3. Display Control & Auto Schedule (OFF Time, ON Time)
 * 4. D7 Night Light Toggle
 * 5. Time Synchronization (Phone Time Sync, NTP UTC+6 BD)
 * 6. Smart Date Display (English Date & Bangla Date checkboxes)
 * 7. Display Settings (12/24-Hour format, Colon Blink, Show Date)
 * 8. Hourly Tone (13 RTTTL Tones, Tone Range with Start/End hour, Mode: Random, Sequential, Fixed)
 * 9. Alarms (2 Alarms with tone selection & live tone test)
 * 10. Brightness & Night/Day LDR Sensor Calibration (Manual slider, Low Cut, High Cut, Live telemetry)
 * 11. Color Studio (5 Modes: Static, Smooth Fade, Rainbow, Custom RGB, Sweep Random; 12 Color swatches)
 * 12. Smart Color Playlist (Up to 8 Scenes with quick presets: Energy, Ocean, Sunset, Spectrum)
 * 13. Web UI Password Management & sultan88 default reset
 * 14. Developer info: MD: SULTAN MAHAMUD, 01740-236384, sultanmahamud5497@gmail.com
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp8266HubScreen(
    viewModel: ClockViewModel,
    uiState: SultanClockUiState,
    onSwitchToEsp32: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSetupWizardExpanded by remember { mutableStateOf(uiState.activeHost == "192.168.4.1" || uiState.isRebooting) }
    var routerPasswordVisible by remember { mutableStateOf(false) }
    var isWebConsoleOpen by remember { mutableStateOf(false) }
    var isPasswordResetDialogOpen by remember { mutableStateOf(false) }

    // Wi-Fi setup form state
    var selectedSsid by remember(uiState.esp8266RouterSsid) { mutableStateOf(uiState.esp8266RouterSsid) }
    var routerPassword by remember(uiState.esp8266RouterPass) { mutableStateOf(uiState.esp8266RouterPass) }
    var useStaticIp by remember(uiState.esp8266UseStaticIp) { mutableStateOf(uiState.esp8266UseStaticIp) }
    var staticIp by remember(uiState.esp8266StaticIp) { mutableStateOf(uiState.esp8266StaticIp.ifBlank { "192.168.0.108" }) }
    var gatewayIp by remember(uiState.esp8266Gateway) { mutableStateOf(uiState.esp8266Gateway.ifBlank { "192.168.0.1" }) }
    var subnetMask by remember(uiState.esp8266Subnet) { mutableStateOf(uiState.esp8266Subnet.ifBlank { "255.255.255.0" }) }

    // Web Password change state
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmNewPass by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. HUB HEADER & SWITCHER ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AmberOrange.copy(alpha = 0.15f))
                                    .border(1.dp, AmberOrange, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = AmberOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SULTAN DIGITAL CLOCK",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = AmberOrange
                                )
                                Text(
                                    text = "ESP8266 Smart Edition • Piezo & D7 Light",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Switch to ESP32 Button
                        OutlinedButton(
                            onClick = onSwitchToEsp32,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("switch_to_esp32_btn")
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ESP32 Hub", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Connection status badge & Host info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) SuccessGreen else ErrorRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "কানেক্টেড: ${uiState.activeHost}" else "অফলাইন (${uiState.activeHost})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isConnected) SuccessGreen else TextSecondary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Quick AP 192.168.4.1 button
                            if (uiState.activeHost != "192.168.4.1") {
                                Surface(
                                    onClick = { viewModel.connectToEsp8266Ap() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = AmberOrange.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "AP: 192.168.4.1",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberOrange,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Web Console Quick Launcher
                            Surface(
                                onClick = { isWebConsoleOpen = true },
                                shape = RoundedCornerShape(6.dp),
                                color = CardBackgroundElevated,
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Web UI", fontSize = 10.sp, color = TextPrimary)
                                }
                            }
                        }
                    }

                    // Safe Rebooting Banner with 15s Countdown
                    if (uiState.isRebooting) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AmberOrange.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AmberOrange),
                            modifier = Modifier.fillMaxWidth().testTag("esp8266_reboot_countdown_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    progress = { uiState.rebootCountdownSec / 15f },
                                    modifier = Modifier.size(24.dp),
                                    color = AmberOrange,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "ঘড়ি রিস্টার্ট হচ্ছে... (${uiState.rebootCountdownSec} সেকেন্ড বাকি)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberOrange
                                    )
                                    Text(
                                        text = "নেটওয়ার্ক সকেট ক্র্যাশ এড়াতে স্বয়ংক্রিয় ব্যাকগ্রাউন্ড পোলিং সাময়িকভাবে স্থগিত রাখা হয়েছে।",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. STEP-BY-STEP REAL-WORLD ESP8266 SETUP WIZARD ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSetupWizardExpanded = !isSetupWizardExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = AmberOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "ESP8266 সেটআপ ও কানেকশন উইজার্ড",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "AP মোড • রাউটার স্ক্যান • স্থায়ী Static IP কনফিগারেশন",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        IconButton(
                            onClick = { isSetupWizardExpanded = !isSetupWizardExpanded },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isSetupWizardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Setup",
                                tint = AmberOrange
                            )
                        }
                    }

                    AnimatedVisibility(visible = isSetupWizardExpanded) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            // --- Step 1: Hotspot AP Mode ---
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardBackgroundElevated,
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(AmberOrange),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("১", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ঘড়ির Hotspot AP তে যুক্ত হওয়া",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberOrange
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "মোবাইলের Wi-Fi অন করে \"SULTAN DIGITAL CLOCK\" হটস্পটে যুক্ত হন। ডিফল্ট AP IP: 192.168.4.1",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.connectToEsp8266Ap() },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, AmberOrange),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberOrange),
                                        modifier = Modifier.fillMaxWidth().testTag("esp8266_step1_ap_connect")
                                    ) {
                                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("হটস্পটে যুক্ত হয়েছি (Set 192.168.4.1)", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // --- Step 2: Wi-Fi Scan & Selection ---
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardBackgroundElevated,
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(AmberOrange),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("২", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "রাউটার Wi-Fi স্ক্যান ও নির্বাচন",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberOrange
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Scan Button
                                    Button(
                                        onClick = { viewModel.scanWifiNetworks() },
                                        enabled = !uiState.isWifiScanning,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                                        modifier = Modifier.fillMaxWidth().testTag("esp8266_scan_wifi_btn")
                                    ) {
                                        if (uiState.isWifiScanning) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("আশেপাশের Wi-Fi খোঁজা হচ্ছে...", fontSize = 12.sp, color = Color.Black)
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("আশেপাশের Wi-Fi স্ক্যান করুন", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Scanned Wi-Fi List Chips
                                    if (uiState.wifiScanList.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("পাওয়া গেছে (${uiState.wifiScanList.size}টি নেটওয়ার্ক):", fontSize = 10.sp, color = TextMuted)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            uiState.wifiScanList.take(6).forEach { net ->
                                                Surface(
                                                    onClick = { selectedSsid = net.ssid },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (selectedSsid == net.ssid) AmberOrange.copy(alpha = 0.2f) else CardBackground,
                                                    border = BorderStroke(1.dp, if (selectedSsid == net.ssid) AmberOrange else CardBorder),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Wifi, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(net.ssid, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                                        }
                                                        Text("${net.rssi} dBm", fontSize = 10.sp, color = TextMuted)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // SSID Input
                                    OutlinedTextField(
                                        value = selectedSsid,
                                        onValueChange = { selectedSsid = it },
                                        label = { Text("রাউটার Wi-Fi নাম (SSID)") },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = AmberOrange) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AmberOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedLabelColor = AmberOrange
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("esp8266_ssid_input")
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Password Input
                                    OutlinedTextField(
                                        value = routerPassword,
                                        onValueChange = { routerPassword = it },
                                        label = { Text("রাউটার Wi-Fi পাসওয়ার্ড") },
                                        singleLine = true,
                                        visualTransformation = if (routerPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AmberOrange) },
                                        trailingIcon = {
                                            IconButton(onClick = { routerPasswordVisible = !routerPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (routerPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle password visibility",
                                                    tint = TextMuted
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AmberOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedLabelColor = AmberOrange
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("esp8266_password_input")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // --- Step 3: Custom Static IP Assignment ---
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardBackgroundElevated,
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                .clip(CircleShape)
                                                .background(AmberOrange),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("৩", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "স্থায়ী আইপি (Static IP) কনফিগারেশন",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberOrange
                                            )
                                        }
                                        Switch(
                                            checked = useStaticIp,
                                            onCheckedChange = { useStaticIp = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = AmberOrange
                                            ),
                                            modifier = Modifier.testTag("esp8266_static_ip_switch")
                                        )
                                    }

                                    Text(
                                        text = if (useStaticIp)
                                            "স্থায়ী IP সেট করলে রাউটার রিস্টার্ট হলেও ঘড়ির IP কখনো পরিবর্তন হবে না (রিকমেন্ডেড: 192.168.0.108)।"
                                        else
                                            "DHCP মোড সক্রিয় (রাউটার স্বয়ংক্রিয়ভাবে যেকোনো IP দিতে পারে)।",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )

                                    if (useStaticIp) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = staticIp,
                                            onValueChange = { staticIp = it },
                                            label = { Text("স্থায়ী Clock IP (যেমন: 192.168.0.108)") },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AmberOrange,
                                                unfocusedBorderColor = CardBorder,
                                                focusedLabelColor = AmberOrange
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("esp8266_static_ip_input")
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = gatewayIp,
                                                onValueChange = { gatewayIp = it },
                                                label = { Text("Gateway") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("esp8266_gateway_input")
                                            )
                                            OutlinedTextField(
                                                value = subnetMask,
                                                onValueChange = { subnetMask = it },
                                                label = { Text("Subnet") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("esp8266_subnet_input")
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // --- Step 4: Save & Trigger Safe Reboot ---
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AmberOrange.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(AmberOrange),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("৪", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "স্থায়ী IP সংরক্ষণ ও নিরাপদ রিবুট",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberOrange
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "একবার সেভ করলে অ্যাপ এই IP টি আজীবনের জন্য SharedPreferences এ মনে রাখবে—আর কখনো AP মোডে যেতে হবে না। সেভ করার পর ১৫ সেকেন্ড নিরাপদ বিরতি দেওয়া হবে যাতে নেটওয়ার্ক সকেট ড্রপ না করে।",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            viewModel.saveEsp8266WifiAndReboot(
                                                ssid = selectedSsid,
                                                pass = routerPassword,
                                                useStaticIp = useStaticIp,
                                                staticIp = staticIp,
                                                gateway = gatewayIp,
                                                subnet = subnetMask
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                                        modifier = Modifier.fillMaxWidth().testTag("esp8266_save_wifi_reboot_btn")
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Wi-Fi ও স্থায়ী IP সেভ করুন",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. 3D LIVE VIRTUAL LED CLOCK MIRROR ---
        item {
            VirtualLedClockMirror(
                dashboardData = uiState.dashboard,
                colorConfig = uiState.colorConfig,
                onToggleDisplay = { viewModel.toggleDisplay() },
                onApplyPreset = { preset -> viewModel.applyColorPreset(preset) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // --- 4. DISPLAY CONTROL & AUTO SCHEDULE ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "DISPLAY CONTROL / ডিসপ্লে নিয়ন্ত্রণ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberOrange,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Row: Display Power & D7 Night Light
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Display Power Toggle
                        Surface(
                            onClick = { viewModel.toggleDisplay() },
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.dashboard.isDisplayOn) SuccessGreen.copy(alpha = 0.15f) else CardBackgroundElevated,
                            border = BorderStroke(1.dp, if (uiState.dashboard.isDisplayOn) SuccessGreen else CardBorder),
                            modifier = Modifier.weight(1f).testTag("esp8266_toggle_display_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = if (uiState.dashboard.isDisplayOn) SuccessGreen else TextMuted
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("ডিসপ্লে পাওয়ার", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(if (uiState.dashboard.isDisplayOn) "চালু (ON)" else "বন্ধ (OFF)", fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        }

                        // D7 Night Light Toggle
                        Surface(
                            onClick = { viewModel.toggleLight() },
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.dashboard.isLightOn) AmberOrange.copy(alpha = 0.2f) else CardBackgroundElevated,
                            border = BorderStroke(1.dp, if (uiState.dashboard.isLightOn) AmberOrange else CardBorder),
                            modifier = Modifier.weight(1f).testTag("esp8266_toggle_light_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = if (uiState.dashboard.isLightOn) AmberOrange else TextMuted
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("D7 নাইট লাইট", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(if (uiState.dashboard.isLightOn) "জ্বলছে (ON)" else "বন্ধ (OFF)", fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto Schedule Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Auto Schedule (স্বয়ংক্রিয় শিডিউল)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Switch(
                                    checked = uiState.displaySchedule.isScheduleEnabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(isScheduleEnabled = enabled))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = AmberOrange
                                    ),
                                    modifier = Modifier.testTag("esp8266_auto_schedule_switch")
                                )
                            }

                            if (uiState.displaySchedule.isScheduleEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // OFF Time
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("বন্ধের সময় (OFF)", fontSize = 10.sp, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedTextField(
                                            value = String.format("%02d:%02d", uiState.displaySchedule.startHour, uiState.displaySchedule.startMinute),
                                            onValueChange = { str ->
                                                val parts = str.split(":")
                                                if (parts.size == 2) {
                                                    val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: uiState.displaySchedule.startHour
                                                    val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: uiState.displaySchedule.startMinute
                                                    viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(startHour = h, startMinute = m))
                                                }
                                            },
                                            label = { Text("OFF Time") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // ON Time
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("চালুর সময় (ON)", fontSize = 10.sp, color = TextSecondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedTextField(
                                            value = String.format("%02d:%02d", uiState.displaySchedule.endHour, uiState.displaySchedule.endMinute),
                                            onValueChange = { str ->
                                                val parts = str.split(":")
                                                if (parts.size == 2) {
                                                    val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: uiState.displaySchedule.endHour
                                                    val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: uiState.displaySchedule.endMinute
                                                    viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(endHour = h, endMinute = m))
                                                }
                                            },
                                            label = { Text("ON Time") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.saveDisplaySchedule() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                                    modifier = Modifier.fillMaxWidth().testTag("esp8266_save_schedule_btn")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SAVE SCHEDULE (শিডিউল সেভ)", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. TIME SYNCHRONIZATION (NTP & PHONE) ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "TIME SYNCHRONIZATION / সময় সিঙ্ক্রোনাইজেশন",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberOrange,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.syncTimeFromPhone() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                            modifier = Modifier.weight(1f).testTag("esp8266_sync_phone_time_btn")
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📱 ফোন সময় সিঙ্ক", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        OutlinedButton(
                            onClick = { viewModel.syncFromNtp() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberOrange),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberOrange),
                            modifier = Modifier.weight(1f).testTag("esp8266_sync_ntp_btn")
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🌐 NTP (UTC+6 BD)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 6. SMART DATE & DISPLAY SETTINGS ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "SMART DATE & DISPLAY SETTINGS / তারিখ ও প্রদর্শন",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberOrange,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Date display settings with quote
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "“সততাই মানুষের সবচেয়ে বড় শক্তি।”",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = GoldPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = uiState.dateSettings.isDateEnabled,
                                        onCheckedChange = { checked ->
                                            viewModel.saveEsp8266DateDisplay(checked, uiState.dateSettings.isBanglaDate)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = AmberOrange),
                                        modifier = Modifier.testTag("esp8266_english_date_cb")
                                    )
                                    Text("English Date (ইংরেজি তারিখ)", fontSize = 12.sp, color = TextPrimary)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = uiState.dateSettings.isBanglaDate,
                                        onCheckedChange = { checked ->
                                            viewModel.saveEsp8266DateDisplay(uiState.dateSettings.isDateEnabled, checked)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = AmberOrange),
                                        modifier = Modifier.testTag("esp8266_bangla_date_cb")
                                    )
                                    Text("বাংলা তারিখ (মৌসুমি)", fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Display Format & Blink settings with quote
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "“পিতা-মাতার সাথে সদ্ব্যবহার করো, তাদের কষ্টের কথা স্মরণ রাখো।”",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = GoldPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // 12-Hour vs 24-Hour
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("সময় ফরম্যাট:", fontSize = 12.sp, color = TextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !uiState.dashboard.is12Hour,
                                        onClick = { viewModel.set12HourFormat(false) },
                                        label = { Text("24-Hour") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AmberOrange,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                    FilterChip(
                                        selected = uiState.dashboard.is12Hour,
                                        onClick = { viewModel.set12HourFormat(true) },
                                        label = { Text("12-Hour (AM/PM)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AmberOrange,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Colon Blink & Show Date Checkboxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = uiState.dashboard.isLightOn,
                                        onCheckedChange = { checked ->
                                            viewModel.updateDisplaySettings(colonBlink = checked)
                                            viewModel.saveDisplaySettings()
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = AmberOrange),
                                        modifier = Modifier.testTag("esp8266_colon_blink_cb")
                                    )
                                    Text("Colon Blink (কোলন ব্লিঙ্ক)", fontSize = 12.sp, color = TextPrimary)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = uiState.dashboard.enableEnglishDate,
                                        onCheckedChange = { checked ->
                                            viewModel.updateDisplaySettings(showDate = checked)
                                            viewModel.saveDisplaySettings()
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = AmberOrange),
                                        modifier = Modifier.testTag("esp8266_show_date_cb")
                                    )
                                    Text("Show Date", fontSize = 12.sp, color = TextPrimary)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.saveDisplaySettings() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                                modifier = Modifier.fillMaxWidth().testTag("esp8266_save_display_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SAVE DISPLAY (ডিসপ্লে সেভ)", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- 7. HOURLY TONE (13 RTTTL TONES & ACTIVE TIME RANGE) ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "HOURLY TONE & RTTTL MELODIES / ঘণ্টার টোন",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberOrange,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "“মানুষের সাথে অহংকার করে কথা বলো না, আল্লাহ অহংকারীকে পছন্দ করেন না।”",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Master Hourly Tone Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Alarm, contentDescription = null, tint = AmberOrange)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Hourly Tone চালু রাখো", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(if (uiState.hourlyChime.enabled) "সক্রিয় (বিপ হবে)" else "নিষ্ক্রিয় (শব্দ হবে না)", fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                            Switch(
                                checked = uiState.hourlyChime.enabled,
                                onCheckedChange = { viewModel.toggleHourlyChime() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = AmberOrange
                                ),
                                modifier = Modifier.testTag("esp8266_hourly_tone_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Active Time Range Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "“ধৈর্য ধারণ করো বিপদের সময়ে, নিশ্চয়ই আল্লাহ ধৈর্যশীলদের ভালোবাসেন।”",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = GoldPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Time Range চালু করো", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Switch(
                                    checked = uiState.hourlyChime.toneRangeEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.saveToneRange(
                                            enabled = checked,
                                            startHour = uiState.hourlyChime.toneStartHour,
                                            endHour = uiState.hourlyChime.toneEndHour
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = AmberOrange
                                    )
                                )
                            }

                            if (uiState.hourlyChime.toneRangeEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("শুরুর সময় (Start Hour)", fontSize = 10.sp, color = TextSecondary)
                                        OutlinedTextField(
                                            value = "${uiState.hourlyChime.toneStartHour}:00",
                                            onValueChange = { str ->
                                                val h = str.replace(":00", "").toIntOrNull()?.coerceIn(0, 23)
                                                if (h != null) {
                                                    viewModel.saveToneRange(
                                                        enabled = true,
                                                        startHour = h,
                                                        endHour = uiState.hourlyChime.toneEndHour
                                                    )
                                                }
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("বন্ধের সময় (End Hour)", fontSize = 10.sp, color = TextSecondary)
                                        OutlinedTextField(
                                            value = "${uiState.hourlyChime.toneEndHour}:00",
                                            onValueChange = { str ->
                                                val h = str.replace(":00", "").toIntOrNull()?.coerceIn(0, 23)
                                                if (h != null) {
                                                    viewModel.saveToneRange(
                                                        enabled = true,
                                                        startHour = uiState.hourlyChime.toneStartHour,
                                                        endHour = h
                                                    )
                                                }
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "“সত্য কথা বলতে ভয় পেও না, কারণ সত্যই মানুষকে মুক্তি দেয়।”",
                                    fontSize = 10.sp,
                                    color = GoldPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tone Modes (Random, Sequential, Fixed)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("টোন মোড নির্বাচন:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))

                            val toneModes = listOf(
                                Triple(1, "🎲 Random", "প্রতি ঘণ্টায় এলোমেলো"),
                                Triple(2, "🔢 Sequential", "১→২→...→১৩ ক্রমে"),
                                Triple(0, "📌 Fixed", "সবসময় একটাই tone")
                            )

                            toneModes.forEach { (modeVal, label, desc) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.saveTone(modeVal, uiState.hourlyChime.fixedTrack) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = uiState.hourlyChime.mode == modeVal,
                                        onClick = { viewModel.saveTone(modeVal, uiState.hourlyChime.fixedTrack) },
                                        colors = RadioButtonDefaults.colors(selectedColor = AmberOrange)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(desc, fontSize = 10.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 13 Built-in Tones Test Grid with Islamic Quote
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "“জ্ঞান অর্জন করো এবং তা কাজে লাগাও, জ্ঞান ছাড়া জীবন অন্ধকার।”",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = GoldPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("১৩টি বিল্ট-ইন RTTTL টোন বাজিয়ে দেখুন (Test Tones):", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Grid of 13 Tone Buttons
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ESP8266_TONE_NAMES.chunked(2).forEachIndexed { rowIdx, chunk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        chunk.forEachIndexed { colIdx, toneName ->
                                            val toneIdx = rowIdx * 2 + colIdx
                                            val isSelected = uiState.hourlyChime.fixedTrack == toneIdx
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.testTone(toneIdx)
                                                    if (uiState.hourlyChime.mode == 0) {
                                                        viewModel.saveTone(0, toneIdx)
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (isSelected) AmberOrange else CardBorder),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (isSelected) AmberOrange.copy(alpha = 0.15f) else Color.Transparent,
                                                    contentColor = if (isSelected) AmberOrange else TextPrimary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f).testTag("esp8266_test_tone_$toneIdx")
                                            ) {
                                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${toneIdx + 1}. $toneName",
                                                    fontSize = 10.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (chunk.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 8. ALARMS (2 ALARMS WITH TONE SELECTION & LIVE TEST) ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ALARM SETTINGS / অ্যালার্ম সেটিংস (২টি অ্যালার্ম)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberOrange,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "“শিরক করো না কখনো আল্লাহর সাথে, এটি সবচেয়ে বড় জুলুম।”",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Alarm 1 Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ALARM 1 (অ্যালার্ম ১)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Switch(
                                    checked = uiState.alarmConfig.alarm1Enabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.saveSingleAlarm(
                                            index = 0,
                                            hour = uiState.alarmConfig.alarm1Hour,
                                            minute = uiState.alarmConfig.alarm1Minute,
                                            enabled = enabled,
                                            toneIndex = uiState.alarmConfig.alarm1Track
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = AmberOrange
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = String.format("%02d:%02d", uiState.alarmConfig.alarm1Hour, uiState.alarmConfig.alarm1Minute),
                                    onValueChange = { str ->
                                        val parts = str.split(":")
                                        if (parts.size == 2) {
                                            val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: uiState.alarmConfig.alarm1Hour
                                            val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: uiState.alarmConfig.alarm1Minute
                                            viewModel.saveSingleAlarm(0, h, m, uiState.alarmConfig.alarm1Enabled, uiState.alarmConfig.alarm1Track)
                                        }
                                    },
                                    label = { Text("Time (HH:MM)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = { viewModel.testTone(uiState.alarmConfig.alarm1Track) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AmberOrange),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberOrange),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("টোন টেস্ট", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Alarm 2 Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ALARM 2 (অ্যালার্ম ২)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Switch(
                                    checked = uiState.alarmConfig.alarm2Enabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.saveSingleAlarm(
                                            index = 1,
                                            hour = uiState.alarmConfig.alarm2Hour,
                                            minute = uiState.alarmConfig.alarm2Minute,
                                            enabled = enabled,
                                            toneIndex = uiState.alarmConfig.alarm2Track
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = AmberOrange
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = String.format("%02d:%02d", uiState.alarmConfig.alarm2Hour, uiState.alarmConfig.alarm2Minute),
                                    onValueChange = { str ->
                                        val parts = str.split(":")
                                        if (parts.size == 2) {
                                            val h = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: uiState.alarmConfig.alarm2Hour
                                            val m = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: uiState.alarmConfig.alarm2Minute
                                            viewModel.saveSingleAlarm(1, h, m, uiState.alarmConfig.alarm2Enabled, uiState.alarmConfig.alarm2Track)
                                        }
                                    },
                                    label = { Text("Time (HH:MM)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = { viewModel.testTone(uiState.alarmConfig.alarm2Track) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AmberOrange),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberOrange),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("টোন টেস্ট", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 9. BRIGHTNESS & NIGHT/DAY LDR CALIBRATION ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "BRIGHTNESS & LDR CALIBRATION / উজ্জ্বলতা ও এলডিআর",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberOrange,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "“আল্লাহ কাউকে তার সাধ্যের বাইরে কষ্ট দেন না, তাই সব পরিস্থিতিতে ধৈর্য রাখো।”",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto LDR Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardBackgroundElevated,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Brightness6, contentDescription = null, tint = AmberOrange)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Auto Brightness (LDR Sensor)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("আশেপাশের আলোর সাথে স্বয়ংক্রিয় অ্যাডজাস্ট", fontSize = 10.sp, color = TextSecondary)
                                    }
                                }
                                Switch(
                                    checked = uiState.brightnessConfig.autoLdr,
                                    onCheckedChange = { auto ->
                                        viewModel.updateAutoLdr(auto)
                                        viewModel.saveBrightness()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = AmberOrange
                                    ),
                                    modifier = Modifier.testTag("esp8266_auto_ldr_switch")
                                )
                            }

                            // Live LDR Telemetry Banner
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CardBackground,
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Live LDR Raw: ${uiState.dashboard.ldrRaw} / 1023", fontSize = 11.sp, color = TextSecondary)
                                    Text("Applied: ${uiState.dashboard.appliedBrightness}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberOrange)
                                }
                            }

                            // Manual Brightness Slider
                            if (!uiState.brightnessConfig.autoLdr) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("ম্যানুয়াল উজ্জ্বলতা: ${uiState.brightnessConfig.manualBrightness}", fontSize = 11.sp, color = TextSecondary)
                                Slider(
                                    value = uiState.brightnessConfig.manualBrightness.toFloat(),
                                    onValueChange = { viewModel.updateManualBrightness(it.toInt()) },
                                    onValueChangeFinished = { viewModel.saveBrightness() },
                                    valueRange = 1f..255f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AmberOrange,
                                        activeTrackColor = AmberOrange,
                                        inactiveTrackColor = CardBorder
                                    ),
                                    modifier = Modifier.testTag("esp8266_brightness_slider")
                                )
                            }

                            // Night / Day LDR Calibration (Low Cut, High Cut)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Night / Day LDR Calibration (ক্যালিব্রেশন):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Low Cut (নিচে হলে min)", fontSize = 10.sp, color = TextSecondary)
                                    OutlinedTextField(
                                        value = uiState.brightnessConfig.ldrLowCut.toString(),
                                        onValueChange = { str ->
                                            val v = str.toIntOrNull()?.coerceIn(0, 1023) ?: uiState.brightnessConfig.ldrLowCut
                                            viewModel.updateLdrCuts(v, uiState.brightnessConfig.ldrHighCut)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("High Cut (উপরে হলে max)", fontSize = 10.sp, color = TextSecondary)
                                    OutlinedTextField(
                                        value = uiState.brightnessConfig.ldrHighCut.toString(),
                                        onValueChange = { str ->
                                            val v = str.toIntOrNull()?.coerceIn(0, 1023) ?: uiState.brightnessConfig.ldrHighCut
                                            viewModel.updateLdrCuts(uiState.brightnessConfig.ldrLowCut, v)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.saveBrightness() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                                modifier = Modifier.fillMaxWidth().testTag("esp8266_save_brightness_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SAVE BRIGHTNESS (উজ্জ্বলতা সেভ)", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- 10. COLOR STUDIO (5 MODES & 12 COLOR SWATCHES & SWEEP RANDOM) ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COLOR STUDIO / কালার স্টুডিও (৫টি মোড)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberOrange,
                            letterSpacing = 0.5.sp
                        )
                        // Mode Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberOrange.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = when (uiState.colorConfig.mode) {
                                    0 -> "Static Color"
                                    1 -> "Smooth Fade"
                                    2 -> "Rainbow Wave"
                                    3 -> "Custom RGB"
                                    4 -> "Sweep Random"
                                    else -> "Custom"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5 Mode Buttons
                    val modes = listOf("Static", "Fade", "Rainbow", "RGB", "Sweep")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modes.forEachIndexed { index, modeName ->
                            val isSelected = uiState.colorConfig.mode == index
                            Surface(
                                onClick = {
                                    viewModel.updateColorMode(index)
                                    viewModel.saveColor()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AmberOrange else CardBackgroundElevated,
                                border = BorderStroke(1.dp, if (isSelected) AmberOrange else CardBorder),
                                modifier = Modifier.weight(1f).testTag("esp8266_color_mode_$index")
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = modeName,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.Black else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 12 Preset Palette Swatches
                    Text("রঙের প্যালেট (১২টি প্রিমিয়াম কালার):", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ESP8266_PALETTE) { colorItem ->
                            val isSelected = uiState.colorConfig.staticColorIndex == colorItem.index && uiState.colorConfig.mode == 0
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    viewModel.updateStaticColorIndex(colorItem.index)
                                    viewModel.updateRgb(colorItem.r, colorItem.g, colorItem.b)
                                    viewModel.saveColor()
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorItem.hex))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) Color.White else CardBorder,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(colorItem.name, fontSize = 9.sp, color = TextSecondary)
                            }
                        }
                    }

                    // Mode Specific Sliders
                    when (uiState.colorConfig.mode) {
                        1 -> { // Smooth Fade Interval
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "“সময় আল্লাহর অমূল্য আমানত, প্রতিটি মুহূর্তের হিসাব একদিন দিতে হবে।”",
                                fontSize = 10.sp,
                                color = GoldPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Color Change Interval: ${uiState.colorConfig.colorIntervalSec} সেকেন্ড", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = uiState.colorConfig.colorIntervalSec.toFloat(),
                                onValueChange = { viewModel.updateColorIntervalSec(it.toInt()) },
                                onValueChangeFinished = { viewModel.saveColor() },
                                valueRange = 1f..120f,
                                colors = SliderDefaults.colors(thumbColor = AmberOrange, activeTrackColor = AmberOrange)
                            )
                        }
                        2, 4 -> { // Rainbow & Sweep Random Speed
                            Spacer(modifier = Modifier.height(10.dp))
                            if (uiState.colorConfig.mode == 4) {
                                Text(
                                    text = "“আল্লাহ সবকিছুই দেখেন ও জানেন, তাই প্রতিটি কাজে সতর্ক থাকো। আল্লাহ অহংকারী ও গর্বিতদের পছন্দ করেন না।”",
                                    fontSize = 10.sp,
                                    color = GoldPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text("অ্যানিমেশন স্পিড: ${uiState.colorConfig.animSpeed} (১=দ্রুত, ১০=ধীর)", fontSize = 11.sp, color = TextSecondary)
                            Slider(
                                value = uiState.colorConfig.animSpeed.toFloat(),
                                onValueChange = { viewModel.updateAnimSpeed(it.toInt()) },
                                onValueChangeFinished = { viewModel.saveColor() },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(thumbColor = AmberOrange, activeTrackColor = AmberOrange)
                            )
                        }
                        3 -> { // Custom RGB Sliders
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Precision RGB Controls (0-255):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Red: ${uiState.colorConfig.red}", fontSize = 10.sp, color = ErrorRed)
                            Slider(
                                value = uiState.colorConfig.red.toFloat(),
                                onValueChange = { r -> viewModel.updateRgb(r.toInt(), uiState.colorConfig.green, uiState.colorConfig.blue) },
                                onValueChangeFinished = { viewModel.saveColor() },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(thumbColor = ErrorRed, activeTrackColor = ErrorRed)
                            )
                            Text("Green: ${uiState.colorConfig.green}", fontSize = 10.sp, color = SuccessGreen)
                            Slider(
                                value = uiState.colorConfig.green.toFloat(),
                                onValueChange = { g -> viewModel.updateRgb(uiState.colorConfig.red, g.toInt(), uiState.colorConfig.blue) },
                                onValueChangeFinished = { viewModel.saveColor() },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(thumbColor = SuccessGreen, activeTrackColor = SuccessGreen)
                            )
                            Text("Blue: ${uiState.colorConfig.blue}", fontSize = 10.sp, color = CyanAccent)
                            Slider(
                                value = uiState.colorConfig.blue.toFloat(),
                                onValueChange = { b -> viewModel.updateRgb(uiState.colorConfig.red, uiState.colorConfig.green, b.toInt()) },
                                onValueChangeFinished = { viewModel.saveColor() },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.saveColor() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                        modifier = Modifier.fillMaxWidth().testTag("esp8266_save_color_btn")
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE COLOR (কালার সেভ করুন)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        // --- 11. SMART COLOR PLAYLIST (UP TO 8 SCENES) ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COLOR PLAYLIST / কালার প্লেলিস্ট (৮ সিন)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberOrange,
                            letterSpacing = 0.5.sp
                        )
                        Switch(
                            checked = uiState.colorPlaylist.enabled,
                            onCheckedChange = { enabled -> viewModel.toggleColorPlaylist(enabled) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AmberOrange
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "“মানুষের সাথে সুন্দরভাবে কথা বলো, উত্তম আচরণই মুমিনের পরিচয়।”",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Presets (Energy, Ocean, Sunset, Spectrum)
                    Text("কুইক প্রিসেটস:", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("🔥 ENERGY", "🌊 OCEAN", "🌅 SUNSET", "🌈 SPECTRUM").forEach { presetName ->
                            Surface(
                                onClick = {
                                    val newSteps = when (presetName) {
                                        "🔥 ENERGY" -> listOf(
                                            ColorPlaylistStep(1, 0, 0, 255, 0, 0, 10, 5),
                                            ColorPlaylistStep(2, 0, 2, 255, 165, 0, 10, 5),
                                            ColorPlaylistStep(3, 0, 1, 255, 255, 0, 10, 5)
                                        )
                                        "🌊 OCEAN" -> listOf(
                                            ColorPlaylistStep(1, 0, 4, 0, 255, 255, 12, 5),
                                            ColorPlaylistStep(2, 0, 6, 0, 200, 140, 12, 5),
                                            ColorPlaylistStep(3, 0, 9, 0, 128, 255, 12, 5)
                                        )
                                        "🌅 SUNSET" -> listOf(
                                            ColorPlaylistStep(1, 1, 8, 255, 0, 255, 15, 5),
                                            ColorPlaylistStep(2, 1, 2, 255, 165, 0, 15, 5),
                                            ColorPlaylistStep(3, 1, 3, 255, 192, 64, 15, 5)
                                        )
                                        else -> listOf(
                                            ColorPlaylistStep(1, 2, 0, 255, 0, 0, 20, 4),
                                            ColorPlaylistStep(2, 4, 0, 0, 255, 0, 20, 4)
                                        )
                                    }
                                    viewModel.updateColorPlaylist(uiState.colorPlaylist.copy(enabled = true, steps = newSteps))
                                    viewModel.saveColorPlaylist()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = CardBackgroundElevated,
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(presetName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AmberOrange)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.saveColorPlaylist() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                        modifier = Modifier.fillMaxWidth().testTag("esp8266_save_playlist_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE PLAYLIST (প্লেলিস্ট সেভ করুন)", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 12. SECURITY, PASSWORD & TOOLS ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "SECURITY & TOOLS / নিরাপত্তা ও টুলস",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberOrange,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Reset Password Button (sultan88)
                        OutlinedButton(
                            onClick = { isPasswordResetDialogOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            modifier = Modifier.weight(1f).testTag("esp8266_reset_pass_btn")
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পাসওয়ার্ড রিসেট", fontSize = 11.sp)
                        }

                        // Open Web Console Button
                        OutlinedButton(
                            onClick = { isWebConsoleOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberOrange),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberOrange),
                            modifier = Modifier.weight(1f).testTag("esp8266_launch_web_console")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ESP8266 Web UI", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // --- 13. DEVELOPER INFORMATION CARD ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✦ ALHAMDULILLAH ✦",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldPrimary,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Developed by: MD: SULTAN MAHAMUD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Mobile: 01740-236384 • Email: sultanmahamud5497@gmail.com",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ESP8266 Hardware: NodeMCU + DS3231 + WS2812B + LDR Sensor + Piezo Buzzer",
                        fontSize = 9.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Web Console Sheet
    if (isWebConsoleOpen) {
        ModalBottomSheet(
            onDismissRequest = { isWebConsoleOpen = false }
        ) {
            EspWebUiConsole(
                viewModel = viewModel,
                uiState = uiState
            )
        }
    }

    // Password Reset Confirmation Dialog
    if (isPasswordResetDialogOpen) {
        AlertDialog(
            onDismissRequest = { isPasswordResetDialogOpen = false },
            title = { Text("ডিফল্ট পাসওয়ার্ড রিসেট করবেন?", color = AmberOrange, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "ঘড়ির ওয়েব পাসওয়ার্ড ডিফল্ট মান \"sultan88\" এ রিসেট হবে। আপনি কি নিশ্চিত?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetEsp8266DefaultPassword()
                        isPasswordResetDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("হ্যাঁ, রিসেট করুন", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { isPasswordResetDialogOpen = false }) {
                    Text("বাতিল", color = TextMuted)
                }
            },
            containerColor = CardBackgroundElevated
        )
    }
}
