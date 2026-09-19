package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ColorPreset
import com.example.data.model.ConnectionStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel
import com.example.ui.viewmodel.SultanClockUiState

/**
 * Dedicated ESP32 Clock Hub
 * Complete, independent control architecture for the ESP32 Dual-Core Edition:
 * - Dual-Core Live Telemetry (Temperature, LDR, Brightness, SSID)
 * - Live Virtual LED Clock Mirror
 * - DFPlayer Mini Master Audio Management
 * - 5-Waqt Prayer Azan Automation
 * - Dual Alarms with Custom Audio Tracks
 * - SD Card Track Catalog & Profile Backups
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32HubScreen(
    viewModel: ClockViewModel,
    uiState: SultanClockUiState,
    onSwitchToEsp8266: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTrackManagerOpen by remember { mutableStateOf(false) }
    var isProfileBackupOpen by remember { mutableStateOf(false) }
    var isWebConsoleOpen by remember { mutableStateOf(false) }

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
                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
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
                                    .background(CyanAccent.copy(alpha = 0.15f))
                                    .border(1.dp, CyanAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ESP32 CLOCK HUB",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = CyanAccent
                                )
                                Text(
                                    text = "Dual-Core Audio Edition • DFPlayer & Azan",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Switch to ESP8266 Button
                        OutlinedButton(
                            onClick = onSwitchToEsp8266,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberOrange),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("switch_to_esp8266_btn")
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ESP8266 Hub", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (uiState.connectionStatus) {
                                            ConnectionStatus.CONNECTED -> SuccessGreen
                                            ConnectionStatus.CONNECTING -> GoldPrimary
                                            else -> ErrorRed
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (uiState.connectionStatus) {
                                    ConnectionStatus.CONNECTED -> "Online: ${uiState.activeHost}"
                                    ConnectionStatus.CONNECTING -> "Connecting..."
                                    else -> "Offline (${uiState.activeHost})"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (uiState.connectionStatus) {
                                    ConnectionStatus.CONNECTED -> SuccessGreen
                                    ConnectionStatus.CONNECTING -> GoldPrimary
                                    else -> ErrorRed
                                }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Backup & Restore
                            IconButton(
                                onClick = { isProfileBackupOpen = true },
                                modifier = Modifier.size(30.dp).testTag("esp32_backup_btn")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Profiles", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            }

                            // Web Console
                            IconButton(
                                onClick = { isWebConsoleOpen = true },
                                modifier = Modifier.size(30.dp).testTag("esp32_web_console_btn")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Web UI", tint = CyanAccent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // --- 2. DUAL-CORE TELEMETRY GAUGES ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Firmware Version
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FIRMWARE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(uiState.dashboard.firmwareVersion.ifBlank { "v5.0-ESP32" }, fontSize = 11.sp, fontWeight = FontWeight.Black, color = CyanAccent)
                    }
                }

                // Temperature
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TEMP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(String.format("%.1f°C", uiState.dashboard.temperatureC), fontSize = 12.sp, fontWeight = FontWeight.Black, color = GoldPrimary)
                    }
                }

                // Raw LDR
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LDR RAW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${uiState.dashboard.ldrRaw}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = SuccessGreen)
                    }
                }

                // Wi-Fi SSID
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WI-FI SSID", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(uiState.dashboard.wifiSsid.take(8), fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                }
            }
        }

        // --- 3. LIVE VIRTUAL LED CLOCK MIRROR ---
        item {
            VirtualLedClockMirror(
                dashboardData = uiState.dashboard,
                colorConfig = uiState.colorConfig,
                onToggleDisplay = { viewModel.toggleDisplay() },
                onApplyPreset = { preset -> viewModel.applyColorPreset(preset) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // --- 4. DFPLAYER MINI AUDIO CONTROLS ---
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DFPLAYER MINI MASTER AUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        }

                        TextButton(
                            onClick = { isTrackManagerOpen = true },
                            modifier = Modifier.testTag("esp32_open_track_catalog")
                        ) {
                            Text("Track Names", fontSize = 11.sp, color = GoldPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Volume Slider (0 - 30)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Volume: ${uiState.dashboard.dfVolume} / 30", fontSize = 12.sp, color = TextPrimary)
                    }

                    Slider(
                        value = uiState.dashboard.dfVolume.toFloat(),
                        onValueChange = { viewModel.updateDfVolume(it.toInt()) },
                        valueRange = 0f..30f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanAccent,
                            activeTrackColor = CyanAccent,
                            inactiveTrackColor = CardBorder
                        ),
                        modifier = Modifier.testTag("esp32_volume_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Track Play Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testDfTrack(1) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            modifier = Modifier.weight(1f).testTag("esp32_play_azan_btn")
                        ) {
                            Text("Play Azan (T1)", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.testDfTrack(10) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GoldPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                            modifier = Modifier.weight(1f).testTag("esp32_play_bell_btn")
                        ) {
                            Text("Play Bell (T10)", fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = { viewModel.stopAudio() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ErrorRed.copy(alpha = 0.15f))
                                .border(1.dp, ErrorRed, RoundedCornerShape(8.dp))
                                .testTag("esp32_stop_audio_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = ErrorRed)
                        }
                    }
                }
            }
        }

        // --- 5. TIME & NTP SYNC ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncTimeFromPhone() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier.weight(1f).testTag("esp32_sync_phone_btn")
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Phone Time", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    OutlinedButton(
                        onClick = { viewModel.syncFromNtp() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                        modifier = Modifier.weight(1f).testTag("esp32_sync_ntp_btn")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync NTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 6. 5-WAQT PRAYER AZAN AUTOMATION SUMMARY ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "5-WAQT PRAYER AZAN AUTOMATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val prayers = listOf(
                        Triple("Fajr (ফজর)", uiState.prayerTimes.fajr, uiState.trackAssignments.fajrTrack),
                        Triple("Dhuhr (যোহর)", uiState.prayerTimes.dhuhr, uiState.trackAssignments.dhuhrTrack),
                        Triple("Asr (আসর)", uiState.prayerTimes.asr, uiState.trackAssignments.asrTrack),
                        Triple("Maghrib (মাগরিব)", uiState.prayerTimes.maghrib, uiState.trackAssignments.maghribTrack),
                        Triple("Isha (ইশা)", uiState.prayerTimes.isha, uiState.trackAssignments.ishaTrack)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        prayers.forEach { (name, time, track) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardBackgroundElevated)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Track $track", fontSize = 10.sp, color = CyanAccent)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 7. DUAL ALARMS ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "DUAL ALARMS WITH CUSTOM TRACKS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Alarm 1
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.alarmConfig.alarm1Enabled) CyanAccent.copy(alpha = 0.12f) else CardBackgroundElevated,
                            border = BorderStroke(1.dp, if (uiState.alarmConfig.alarm1Enabled) CyanAccent else CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Alarm 1", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Switch(
                                        checked = uiState.alarmConfig.alarm1Enabled,
                                        onCheckedChange = { checked ->
                                            viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm1Enabled = checked))
                                            viewModel.saveAlarmConfig()
                                        },
                                        modifier = Modifier.testTag("esp32_alarm1_switch")
                                    )
                                }
                                Text(
                                    text = String.format("%02d:%02d", uiState.alarmConfig.alarm1Hour, uiState.alarmConfig.alarm1Minute),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (uiState.alarmConfig.alarm1Enabled) CyanAccent else TextMuted
                                )
                                Text("Track: ${uiState.alarmConfig.alarm1Track}", fontSize = 10.sp, color = TextSecondary)
                            }
                        }

                        // Alarm 2
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.alarmConfig.alarm2Enabled) GoldPrimary.copy(alpha = 0.12f) else CardBackgroundElevated,
                            border = BorderStroke(1.dp, if (uiState.alarmConfig.alarm2Enabled) GoldPrimary else CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Alarm 2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Switch(
                                        checked = uiState.alarmConfig.alarm2Enabled,
                                        onCheckedChange = { checked ->
                                            viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm2Enabled = checked))
                                            viewModel.saveAlarmConfig()
                                        },
                                        modifier = Modifier.testTag("esp32_alarm2_switch")
                                    )
                                }
                                Text(
                                    text = String.format("%02d:%02d", uiState.alarmConfig.alarm2Hour, uiState.alarmConfig.alarm2Minute),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (uiState.alarmConfig.alarm2Enabled) GoldPrimary else TextMuted
                                )
                                Text("Track: ${uiState.alarmConfig.alarm2Track}", fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // DFPlayer Track Catalog Dialog
    if (isTrackManagerOpen) {
        TrackManagerDialog(
            trackNames = uiState.trackNames,
            onSaveTrackName = { id, name -> viewModel.saveTrackName(id, name) },
            onResetDefaults = { viewModel.resetTrackNames() },
            onTestTrack = { id -> viewModel.testDfTrack(id) },
            onDismiss = { isTrackManagerOpen = false }
        )
    }

    // Profile Backup & Restore Dialog
    if (isProfileBackupOpen) {
        ProfileBackupDialog(
            savedProfiles = uiState.savedProfiles,
            onSaveCurrentProfile = { name -> viewModel.saveCurrentProfile(name) },
            onRestoreProfile = { profile -> viewModel.restoreProfile(profile) },
            onDeleteProfile = { id -> viewModel.deleteProfile(id) },
            onDismiss = { isProfileBackupOpen = false }
        )
    }

    // ESP32 Web Console
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
}
