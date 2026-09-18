package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel
import com.example.ui.viewmodel.SultanClockUiState

@Composable
fun DashboardScreen(
    viewModel: ClockViewModel,
    uiState: SultanClockUiState,
    onNavigateToConnection: () -> Unit
) {
    val isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(14.dp))

            // Connection Status Bar
            ConnectionStatusBar(
                status = uiState.connectionStatus,
                host = uiState.activeHost,
                connType = uiState.dashboard.connectionType,
                wifiConnected = uiState.dashboard.wifiConnected,
                apMode = uiState.dashboard.apMode,
                wifiSsid = uiState.dashboard.wifiSsid,
                onReconnectClick = {
                    if (isConnected) {
                        viewModel.connectToClock()
                    } else {
                        onNavigateToConnection()
                    }
                }
            )

            // Live Action Feedback Banner
            ActionFeedbackBanner(feedback = uiState.feedback)

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Live Virtual LED Clock Simulator (Live Mirroring)
        item {
            VirtualLedClockMirror(
                dashboardData = uiState.dashboard,
                colorConfig = uiState.colorConfig,
                onToggleDisplay = { viewModel.toggleDisplay() },
                onApplyPreset = { viewModel.applyColorPreset(it) },
                modifier = Modifier.testTag("virtual_led_clock_mirror")
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Live Main Digital Clock Card & Controls
        item {
            DigitalClockCard(
                data = uiState.dashboard,
                isConnected = isConnected,
                onPhoneSync = { viewModel.syncTimeFromPhone() },
                onNtpSync = { viewModel.syncFromNtp() },
                modifier = Modifier.testTag("live_clock_card")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Smart Tools Row (Track Manager & Backup Restore)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = { viewModel.openTrackManager(true) },
                    shape = RoundedCornerShape(14.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f).height(62.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(PrimaryGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Track Manager",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "SD কার্ড অডিও নাম",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldGreen
                            )
                        }
                    }
                }

                Surface(
                    onClick = { viewModel.openProfileBackup(true) },
                    shape = RoundedCornerShape(14.dp),
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f).height(62.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(AmberOrange.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = AmberOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Backup Profile",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "সেটিংস ব্যাকআপ",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberOrange
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        // Quick Controls Section
        item {
            SectionHeader(
                title = "QUICK ACTIONS",
                icon = Icons.Default.Bolt,
                accentColor = GoldPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2x3 Action Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    title = "Display",
                    subtitle = if (uiState.dashboard.isDisplayOn) "Status: ON" else "Status: OFF",
                    icon = Icons.Default.Tv,
                    isActive = uiState.dashboard.isDisplayOn,
                    onClick = { viewModel.toggleDisplay() },
                    activeColor = CyanAccent,
                    modifier = Modifier.weight(1f).testTag("toggle_display_btn")
                )

                QuickActionButton(
                    title = "Light / LEDs",
                    subtitle = if (uiState.dashboard.isLightOn) "Status: ON" else "Status: OFF",
                    icon = Icons.Default.Lightbulb,
                    isActive = uiState.dashboard.isLightOn,
                    onClick = { viewModel.toggleLight() },
                    activeColor = GoldPrimary,
                    modifier = Modifier.weight(1f).testTag("toggle_light_btn")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    title = "Prayer / Azan",
                    subtitle = if (uiState.dashboard.isPrayerAlarmOn) "Alarm: ACTIVE" else "Alarm: MUTED",
                    icon = Icons.Default.Mosque,
                    isActive = uiState.dashboard.isPrayerAlarmOn,
                    onClick = { viewModel.togglePrayerAlarm() },
                    activeColor = SuccessGreen,
                    modifier = Modifier.weight(1f).testTag("toggle_prayer_btn")
                )

                QuickActionButton(
                    title = "Temp Sensor",
                    subtitle = if (uiState.dashboard.isTempSensorOn) "Display: ON" else "Display: OFF",
                    icon = Icons.Default.Thermostat,
                    isActive = uiState.dashboard.isTempSensorOn,
                    onClick = { viewModel.toggleTempSensor() },
                    activeColor = WarningOrange,
                    modifier = Modifier.weight(1f).testTag("toggle_temp_btn")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Islamic Prayer / Namaz Schedule Card
        item {
            SectionHeader(
                title = "PRAYER / NAMAZ TIMES",
                icon = Icons.Default.Mosque,
                accentColor = SuccessGreen
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard(borderColor = SuccessGreen.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dhaka, Bangladesh",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Method: Karachi Calculation",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (uiState.dashboard.isPrayerAlarmOn) SuccessGreen.copy(alpha = 0.15f) else CardBackgroundElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (uiState.dashboard.isPrayerAlarmOn) SuccessGreen else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.dashboard.isPrayerAlarmOn) "Azan ON" else "Azan OFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.dashboard.isPrayerAlarmOn) SuccessGreen else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6 Prayer Cards (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrayerBadge(name = "Fajr", time = uiState.prayerTimes.fajr, icon = "🌙", modifier = Modifier.weight(1f))
                        PrayerBadge(name = "Sunrise", time = uiState.prayerTimes.sunrise, icon = "🌅", modifier = Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrayerBadge(name = "Dhuhr", time = uiState.prayerTimes.dhuhr, icon = "☀️", modifier = Modifier.weight(1f))
                        PrayerBadge(name = "Asr", time = uiState.prayerTimes.asr, icon = "🌇", modifier = Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrayerBadge(name = "Maghrib", time = uiState.prayerTimes.maghrib, icon = "🌆", modifier = Modifier.weight(1f))
                        PrayerBadge(name = "Isha", time = uiState.prayerTimes.isha, icon = "🌃", modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Live Telemetry Details (Brightness & DFPlayer Mini)
        item {
            SectionHeader(
                title = "HARDWARE TELEMETRY",
                icon = Icons.Default.Memory,
                accentColor = CyanAccent
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Brightness / LDR Card
                GlassCard(
                    modifier = Modifier.weight(1f),
                    borderColor = GoldPrimary.copy(alpha = 0.4f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Brightness6, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LDR Light", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Raw: ${uiState.dashboard.ldrRaw}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Level: ${uiState.dashboard.appliedBrightness}/255",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                }

                // DFPlayer Mini Status Card
                GlassCard(
                    modifier = Modifier.weight(1f),
                    borderColor = CyanAccent.copy(alpha = 0.4f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speaker, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DFPlayer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.dashboard.dfConnected) "Ready (SD Card)" else "Disconnected",
                        fontSize = 12.sp,
                        color = if (uiState.dashboard.dfConnected) SuccessGreen else ErrorRed
                    )
                    Text(
                        text = "Volume: ${uiState.dashboard.dfVolume}/30",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // DFPlayer SD Card Track Manager Dialog
    if (uiState.isTrackManagerOpen) {
        TrackManagerDialog(
            trackNames = uiState.trackNames,
            onSaveTrackName = { trackNum, name -> viewModel.saveTrackName(trackNum, name) },
            onResetDefaults = { viewModel.resetTrackNames() },
            onTestTrack = { trackNum -> viewModel.testDfTrack(trackNum) },
            onDismiss = { viewModel.openTrackManager(false) }
        )
    }

    // Profile Backup & Restore Dialog
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
private fun PrayerBadge(
    name: String,
    time: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        color = CardBackgroundElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            Text(
                text = time,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
