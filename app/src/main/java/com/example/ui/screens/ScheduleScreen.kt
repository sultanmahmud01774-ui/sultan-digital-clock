package com.example.ui.screens

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
import com.example.data.model.HourlyChimeConfig
import com.example.data.model.WeeklyPlaylistSlot
import com.example.ui.components.ActionFeedbackBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TrackManagerDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel
import com.example.ui.viewmodel.SultanClockUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ClockViewModel,
    uiState: SultanClockUiState
) {
    var expandedHourlyMode by remember { mutableStateOf(false) }

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

            // SD Card Track Catalog Quick Action Card
            Surface(
                onClick = { viewModel.openTrackManager(true) },
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(PrimaryGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "DFPlayer SD Card Track Manager",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "কাস্টম ট্র্যাক নাম ও অডিও ক্যাটালগ এডিট করুন",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = EmeraldGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // --- DUAL ALARMS SECTION ---
        item {
            SectionHeader(
                title = "CLOCK ALARMS (1 & 2)",
                icon = Icons.Default.Alarm,
                accentColor = GoldPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                // Alarm 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Alarm 1", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                        Text(
                            text = String.format("%02d:%02d", uiState.alarmConfig.alarm1Hour, uiState.alarmConfig.alarm1Minute),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                    Switch(
                        checked = uiState.alarmConfig.alarm1Enabled,
                        onCheckedChange = {
                            viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm1Enabled = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha = 0.3f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.alarmConfig.alarm1Hour.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { h ->
                                viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm1Hour = h.coerceIn(0, 23)))
                            }
                        },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.alarmConfig.alarm1Minute.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { m ->
                                viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm1Minute = m.coerceIn(0, 59)))
                            }
                        },
                        label = { Text("Min (0-59)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.alarmConfig.alarm1Track.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { t ->
                                viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm1Track = t.coerceIn(1, 99)))
                            }
                        },
                        label = { Text("MP3 Track") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Show Friendly Track Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewModel.getTrackDisplayName(uiState.alarmConfig.alarm1Track),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldGreen
                    )
                    IconButton(
                        onClick = { viewModel.testDfTrack(uiState.alarmConfig.alarm1Track) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Test Play",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Alarm 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Alarm 2", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Text(
                            text = String.format("%02d:%02d", uiState.alarmConfig.alarm2Hour, uiState.alarmConfig.alarm2Minute),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                    Switch(
                        checked = uiState.alarmConfig.alarm2Enabled,
                        onCheckedChange = {
                            viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm2Enabled = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = CyanAccent.copy(alpha = 0.3f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.alarmConfig.alarm2Hour.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { h ->
                                viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm2Hour = h.coerceIn(0, 23)))
                            }
                        },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.alarmConfig.alarm2Minute.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { m ->
                                viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm2Minute = m.coerceIn(0, 59)))
                            }
                        },
                        label = { Text("Min (0-59)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.alarmConfig.alarm2Track.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { t ->
                                viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm2Track = t.coerceIn(1, 99)))
                            }
                        },
                        label = { Text("MP3 Track") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Show Friendly Track Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewModel.getTrackDisplayName(uiState.alarmConfig.alarm2Track),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                    IconButton(
                        onClick = { viewModel.testDfTrack(uiState.alarmConfig.alarm2Track) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Test Play",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.saveAlarmConfig() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_alarms_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Alarms", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- HOURLY CHIME SECTION ---
        item {
            SectionHeader(
                title = "HOURLY CHIME & TONES",
                icon = Icons.Default.Notifications,
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
                        Text("Hourly Chime", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Play tone every hour on the clock", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = uiState.hourlyChime.enabled,
                        onCheckedChange = {
                            viewModel.updateHourlyChime(uiState.hourlyChime.copy(enabled = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = CyanAccent.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Hours
                Text("Active Hours Range:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.hourlyChime.startHour.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { h ->
                                viewModel.updateHourlyChime(uiState.hourlyChime.copy(startHour = h.coerceIn(0, 23)))
                            }
                        },
                        label = { Text("Start Hour (e.g. 6 AM)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.hourlyChime.endHour.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { h ->
                                viewModel.updateHourlyChime(uiState.hourlyChime.copy(endHour = h.coerceIn(0, 23)))
                            }
                        },
                        label = { Text("End Hour (e.g. 22 PM)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hourly Mode Dropdown
                Text("Playback Mode:", fontSize = 12.sp, color = TextSecondary)
                ExposedDropdownMenuBox(
                    expanded = expandedHourlyMode,
                    onExpandedChange = { expandedHourlyMode = !expandedHourlyMode }
                ) {
                    OutlinedTextField(
                        value = HourlyChimeConfig.MODES.getOrElse(uiState.hourlyChime.mode) { "Mode ${uiState.hourlyChime.mode}" },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedHourlyMode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedHourlyMode,
                        onDismissRequest = { expandedHourlyMode = false },
                        modifier = Modifier.background(CardBackgroundElevated)
                    ) {
                        HourlyChimeConfig.MODES.forEachIndexed { index, modeName ->
                            DropdownMenuItem(
                                text = { Text(modeName) },
                                onClick = {
                                    viewModel.updateHourlyChime(uiState.hourlyChime.copy(mode = index))
                                    expandedHourlyMode = false
                                }
                            )
                        }
                    }
                }

                if (uiState.hourlyChime.mode == 0) {
                    // Fixed Track
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.hourlyChime.fixedTrack.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { t ->
                                viewModel.updateHourlyChime(uiState.hourlyChime.copy(fixedTrack = t.coerceIn(1, 30)))
                            }
                        },
                        label = { Text("Fixed MP3 Track Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.getTrackDisplayName(uiState.hourlyChime.fixedTrack),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldGreen
                        )
                        IconButton(
                            onClick = { viewModel.testDfTrack(uiState.hourlyChime.fixedTrack) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Test Play",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveToneRange() },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyanAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Range", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.saveHourlyChime() },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("save_chime_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color(0xFF00363D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Chime Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- PRAYER / AZAN TRACK ASSIGNMENTS ---
        item {
            SectionHeader(
                title = "AZAN TRACK ASSIGNMENTS",
                icon = Icons.Default.QueueMusic,
                accentColor = SuccessGreen
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard(borderColor = SuccessGreen.copy(alpha = 0.4f)) {
                Text("Select DFPlayer MP3 track for each Prayer Azan:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(10.dp))

                PrayerTrackRow(
                    prayerName = "Fajr Azan",
                    track = uiState.trackAssignments.fajrTrack,
                    trackName = viewModel.getTrackDisplayName(uiState.trackAssignments.fajrTrack),
                    enabled = uiState.trackAssignments.fajrEnabled,
                    onTrackChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(fajrTrack = it)) },
                    onEnabledChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(fajrEnabled = it)) },
                    onTestPlay = { viewModel.testDfTrack(uiState.trackAssignments.fajrTrack) }
                )

                PrayerTrackRow(
                    prayerName = "Dhuhr Azan",
                    track = uiState.trackAssignments.dhuhrTrack,
                    trackName = viewModel.getTrackDisplayName(uiState.trackAssignments.dhuhrTrack),
                    enabled = uiState.trackAssignments.dhuhrEnabled,
                    onTrackChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(dhuhrTrack = it)) },
                    onEnabledChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(dhuhrEnabled = it)) },
                    onTestPlay = { viewModel.testDfTrack(uiState.trackAssignments.dhuhrTrack) }
                )

                PrayerTrackRow(
                    prayerName = "Asr Azan",
                    track = uiState.trackAssignments.asrTrack,
                    trackName = viewModel.getTrackDisplayName(uiState.trackAssignments.asrTrack),
                    enabled = uiState.trackAssignments.asrEnabled,
                    onTrackChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(asrTrack = it)) },
                    onEnabledChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(asrEnabled = it)) },
                    onTestPlay = { viewModel.testDfTrack(uiState.trackAssignments.asrTrack) }
                )

                PrayerTrackRow(
                    prayerName = "Maghrib Azan",
                    track = uiState.trackAssignments.maghribTrack,
                    trackName = viewModel.getTrackDisplayName(uiState.trackAssignments.maghribTrack),
                    enabled = uiState.trackAssignments.maghribEnabled,
                    onTrackChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(maghribTrack = it)) },
                    onEnabledChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(maghribEnabled = it)) },
                    onTestPlay = { viewModel.testDfTrack(uiState.trackAssignments.maghribTrack) }
                )

                PrayerTrackRow(
                    prayerName = "Isha Azan",
                    track = uiState.trackAssignments.ishaTrack,
                    trackName = viewModel.getTrackDisplayName(uiState.trackAssignments.ishaTrack),
                    enabled = uiState.trackAssignments.ishaEnabled,
                    onTrackChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(ishaTrack = it)) },
                    onEnabledChange = { viewModel.updateTrackAssignments(uiState.trackAssignments.copy(ishaEnabled = it)) },
                    onTestPlay = { viewModel.testDfTrack(uiState.trackAssignments.ishaTrack) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.saveTrackAssignments() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_azan_tracks_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color(0xFF003915)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Azan Tracks", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- WEEKLY TIME PLAYLIST ---
        item {
            SectionHeader(
                title = "WEEKLY TIME PLAYLIST",
                icon = Icons.Default.CalendarViewWeek,
                accentColor = GoldPrimary,
                action = {
                    IconButton(onClick = { viewModel.addWeeklySlot() }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add Slot", tint = GoldPrimary)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                Text(
                    text = "Configure weekly recurring audio triggers:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                uiState.weeklyPlaylist.forEach { slot ->
                    WeeklySlotCard(
                        slot = slot,
                        onUpdate = { updated -> viewModel.updateWeeklySlot(updated) },
                        onDelete = { viewModel.removeWeeklySlot(slot.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.saveWeeklyPlaylist() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_weekly_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Weekly Playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (uiState.isTrackManagerOpen) {
        TrackManagerDialog(
            trackNames = uiState.trackNames,
            onSaveTrackName = { num, name -> viewModel.saveTrackName(num, name) },
            onResetDefaults = { viewModel.resetTrackNames() },
            onTestTrack = { num -> viewModel.testDfTrack(num) },
            onDismiss = { viewModel.openTrackManager(false) }
        )
    }
}

@Composable
private fun PrayerTrackRow(
    prayerName: String,
    track: Int,
    trackName: String? = null,
    enabled: Boolean,
    onTrackChange: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTestPlay: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CardBackgroundElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = CheckboxDefaults.colors(checkedColor = SuccessGreen)
                )
                Column {
                    Text(prayerName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    trackName?.let {
                        Text(it, fontSize = 11.sp, color = EmeraldGreen)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = track.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { t -> onTrackChange(t.coerceIn(1, 255)) }
                    },
                    modifier = Modifier.width(62.dp),
                    singleLine = true
                )
                IconButton(
                    onClick = onTestPlay,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Test Play",
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklySlotCard(
    slot: WeeklyPlaylistSlot,
    onUpdate: (WeeklyPlaylistSlot) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        color = CardBackgroundElevated
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = slot.enabled,
                        onCheckedChange = { onUpdate(slot.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Slot ${slot.id}: ${String.format("%02d:%02d", slot.hour, slot.minute)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time config
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = slot.hour.toString(),
                    onValueChange = { it.toIntOrNull()?.let { h -> onUpdate(slot.copy(hour = h.coerceIn(0, 23))) } },
                    label = { Text("Hour") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = slot.minute.toString(),
                    onValueChange = { it.toIntOrNull()?.let { m -> onUpdate(slot.copy(minute = m.coerceIn(0, 59))) } },
                    label = { Text("Min") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 7 Days Daily Tracks (Sun .. Sat)
            Text(
                text = "Daily MP3 Tracks (Sun - Sat):",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val days = listOf(
                    Triple("Sun", slot.sunTrack) { t: Int -> slot.copy(sunTrack = t) },
                    Triple("Mon", slot.monTrack) { t: Int -> slot.copy(monTrack = t) },
                    Triple("Tue", slot.tueTrack) { t: Int -> slot.copy(tueTrack = t) },
                    Triple("Wed", slot.wedTrack) { t: Int -> slot.copy(wedTrack = t) },
                    Triple("Thu", slot.thuTrack) { t: Int -> slot.copy(thuTrack = t) },
                    Triple("Fri", slot.friTrack) { t: Int -> slot.copy(friTrack = t) },
                    Triple("Sat", slot.satTrack) { t: Int -> slot.copy(satTrack = t) }
                )

                days.forEach { (name, trackVal, copyFunc) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(name, fontSize = 10.sp, color = GoldPrimary, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = trackVal.toString(),
                            onValueChange = { input ->
                                input.toIntOrNull()?.let { t ->
                                    onUpdate(copyFunc(t.coerceIn(1, 255)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
    }
}
