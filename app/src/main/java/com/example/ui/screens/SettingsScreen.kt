package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ActionFeedbackBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.ProfileBackupDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.TrackManagerDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel
import com.example.ui.viewmodel.SultanClockUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ClockViewModel,
    uiState: SultanClockUiState
) {
    var selectedOtaUri by remember { mutableStateOf<Uri?>(null) }
    var testTrackInput by remember { mutableStateOf("1") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedOtaUri = uri
            viewModel.onOtaFileSelected(uri, uri.lastPathSegment ?: "firmware.bin")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(14.dp))
            ActionFeedbackBanner(feedback = uiState.feedback)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- DFPLAYER MP3 & TEST PLAYER ---
        item {
            SectionHeader(
                title = "DFPLAYER MP3 AUDIO",
                icon = Icons.Default.Speaker,
                accentColor = CyanAccent
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                // Volume Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Master Audio Volume", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text = "${uiState.dashboard.dfVolume} / 30",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = uiState.dashboard.dfVolume.toFloat(),
                    onValueChange = { viewModel.updateDfVolume(it.toInt()) },
                    valueRange = 0f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanAccent,
                        activeTrackColor = CyanAccent,
                        inactiveTrackColor = CardBackgroundElevated
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Track Test Player
                Text("Test Play MicroSD Track:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testTrackInput,
                        onValueChange = { testTrackInput = it },
                        label = { Text("Track #") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            testTrackInput.toIntOrNull()?.let { viewModel.testDfTrack(it) }
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("test_track_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color(0xFF00363D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PLAY TEST", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = viewModel.getTrackDisplayName(testTrackInput.toIntOrNull() ?: 1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldGreen
                )

                Spacer(modifier = Modifier.height(10.dp))

                // SD Card Track Catalog Button
                OutlinedButton(
                    onClick = { viewModel.openTrackManager(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SD Card Track Manager (কাস্টম ট্র্যাক নাম)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- DATE & REGIONAL SETTINGS ---
        item {
            SectionHeader(
                title = "DATE & REGIONAL SETTINGS",
                icon = Icons.Default.CalendarToday,
                accentColor = GoldPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Display Date on Clock", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Toggle date display cycle on LED panel", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = uiState.dateSettings.isDateEnabled,
                        onCheckedChange = {
                            viewModel.updateDateSettings(uiState.dateSettings.copy(isDateEnabled = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bangla Date Display", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text("Display Bengali numerals / language format", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = uiState.dateSettings.isBanglaDate,
                        onCheckedChange = {
                            viewModel.updateDateSettings(uiState.dateSettings.copy(isBanglaDate = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = CyanAccent.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.saveDateSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_date_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Date Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- WI-FI CONFIGURATION SECTION ---
        item {
            SectionHeader(
                title = "WI-FI CONFIGURATION",
                icon = Icons.Default.Wifi,
                accentColor = CyanAccent
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Network:", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = uiState.dashboard.wifiSsid,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Button(
                        onClick = { viewModel.scanWifiNetworks() },
                        enabled = !uiState.isWifiScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (uiState.isWifiScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanAccent)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SCAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (uiState.wifiScanList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Nearby Networks (Tap to select):", fontSize = 11.sp, color = CyanAccent)
                    Spacer(modifier = Modifier.height(4.dp))

                    uiState.wifiScanList.forEach { net ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBackgroundElevated)
                                .clickable {
                                    viewModel.updateWifiConfig(uiState.wifiConfig.copy(ssid = net.ssid))
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(net.ssid, fontSize = 13.sp, color = TextPrimary)
                            }
                            Text("${net.rssi} dBm", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.wifiConfig.ssid,
                    onValueChange = { viewModel.updateWifiConfig(uiState.wifiConfig.copy(ssid = it)) },
                    label = { Text("Network SSID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.wifiConfig.password,
                    onValueChange = { viewModel.updateWifiConfig(uiState.wifiConfig.copy(password = it)) },
                    label = { Text("Wi-Fi Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.saveWifiConfig() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_wifi_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color(0xFF00363D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.WifiProtectedSetup, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connect Clock to Wi-Fi", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- SECURITY & PASSWORDS SECTION ---
        item {
            SectionHeader(
                title = "SECURITY & ACCESS PASSWORDS",
                icon = Icons.Default.Security,
                accentColor = WarningOrange
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                // Hotspot AP Password
                Text("Hotspot AP Password (Min 8 chars):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.apPasswordInput,
                        onValueChange = { viewModel.updateApPasswordInput(it) },
                        label = { Text("New AP Password") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.saveApPassword() },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningOrange, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Web UI Password
                Text("Web UI Admin Password:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = uiState.oldAdminPasswordInput,
                    onValueChange = { viewModel.updateOldAdminPassword(it) },
                    label = { Text("Old Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.newAdminPasswordInput,
                        onValueChange = { viewModel.updateNewAdminPassword(it) },
                        label = { Text("New Admin Password") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.saveWebPassword() },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- OTA FIRMWARE UPDATE SECTION ---
        item {
            SectionHeader(
                title = "OTA FIRMWARE UPDATE",
                icon = Icons.Default.SystemUpdate,
                accentColor = GoldPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                Text(
                    text = "Flash new firmware directly over Wi-Fi without USB connection.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // File Selector
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            filePickerLauncher.launch("*/*")
                        },
                    color = CardBackgroundElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = GoldPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.selectedOtaFileName ?: "Select .bin firmware file",
                                fontSize = 13.sp,
                                color = if (uiState.selectedOtaFileName != null) GoldPrimary else TextSecondary
                            )
                        }
                        Text("BROWSE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                    }
                }

                if (uiState.isOtaUploading) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Uploading firmware: ${uiState.otaProgress}%", fontSize = 12.sp, color = GoldPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { uiState.otaProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = GoldPrimary,
                        trackColor = CardBackgroundElevated
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Safety Warning
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2B1B04))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Do NOT power off or disconnect the clock during firmware flashing.",
                        fontSize = 11.sp,
                        color = Color(0xFFFFD599)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        selectedOtaUri?.let { viewModel.uploadOtaFirmware(it) }
                    },
                    enabled = selectedOtaUri != null && !uiState.isOtaUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("upload_ota_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("OPEN OTA UPDATE", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- PROFILE BACKUP & RESTORE SECTION ---
        item {
            SectionHeader(
                title = "PROFILE BACKUP & RESTORE",
                icon = Icons.Default.CloudSync,
                accentColor = AmberOrange
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard(borderColor = AmberOrange.copy(alpha = 0.4f)) {
                Text(
                    text = "ক্লক সেটিংস ব্যাকআপ ও দ্রুত রিস্টোর",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "সমস্ত অ্যালার্ম, ঘণ্টা চিম, আযান ও ডিসপ্লে কালার একটি প্রোফাইলে সেভ রাখুন এবং পরবর্তীতে এক ক্লিকে ক্লকে পাঠিয়ে দিন।",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "সংরক্ষিত প্রোফাইল: ${uiState.savedProfiles.size}টি",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberOrange
                    )

                    Button(
                        onClick = { viewModel.openProfileBackup(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ব্যাকআপ সেন্টার", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- ABOUT SULTAN DIGITAL CLOCK ---
        item {
            SectionHeader(
                title = "ABOUT SYSTEM",
                icon = Icons.Default.Info,
                accentColor = CyanAccent
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                Text(
                    text = "SULTAN DIGITAL CLOCK",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "ESP32 IoT Multi-Function Smart Controller",
                    fontSize = 12.sp,
                    color = CyanAccent
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                AboutRow("Developer", "MD: SULTAN MAHAMUD")
                AboutRow("Email", "sultanmahamud5497@gmail.com")
                AboutRow("Mobile", "01740-236384")
                AboutRow("Firmware", "ESP32 Firmware v4/v5 Compatible")
                AboutRow("Hardware", "ESP32-WROOM-32 + DS3231 RTC")
                AboutRow("Peripherals", "WS2812B LEDs + DFPlayer Mini + LDR")
                AboutRow("Calculations", "Islamic Karachi Method (Dhaka)")
                AboutRow("Android App", "v1.0.0 Native Jetpack Compose")
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    // Dialogs
    if (uiState.isTrackManagerOpen) {
        TrackManagerDialog(
            trackNames = uiState.trackNames,
            onSaveTrackName = { num, name -> viewModel.saveTrackName(num, name) },
            onResetDefaults = { viewModel.resetTrackNames() },
            onTestTrack = { num -> viewModel.testDfTrack(num) },
            onDismiss = { viewModel.openTrackManager(false) }
        )
    }

    if (uiState.isProfileBackupOpen) {
        ProfileBackupDialog(
            savedProfiles = uiState.savedProfiles,
            onSaveCurrentProfile = { name -> viewModel.saveCurrentProfile(name) },
            onRestoreProfile = { profile -> viewModel.restoreProfile(profile) },
            onDeleteProfile = { profileId -> viewModel.deleteProfile(profileId) },
            onDismiss = { viewModel.openProfileBackup(false) }
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
