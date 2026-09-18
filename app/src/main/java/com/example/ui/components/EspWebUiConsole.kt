package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ColorPlaylistStep
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel
import com.example.ui.viewmodel.SultanClockUiState

/**
 * High-fidelity representation of the Sultan Digital Clock ESP8266
 * Web UI Console, precisely matching the user's Arduino code and uploaded image:
 *
 * 1. AI Elite Matrix branding & 3D Live Clock mirror
 * 2. 🖥️ Display Control (ON/OFF) & Auto Schedule (Off/On times)
 * 3. 💡 D7 Light Control (ON/OFF)
 * 4. 🕐 Time Sync (Phone & NTP UTC+6)
 * 5. 📅 Smart Date Display (English & Bangla checkboxes)
 * 6. 🎵 Hourly Tone (Active Range, Random/Sequential/Fixed modes, and 13 RTTTL test buttons)
 * 7. 🕌 Prayer (Azan) Settings (Methods & 10 Waqt Golden Grid)
 * 8. 📶 WiFi Settings (Scan, SSID, Password, Static IP)
 * 9. ⚙️ Display Settings (12/24H, Colon Blink, Show Date, Bangla Digits)
 * 10. ⏰ Alarms (Alarm 1 & 2 with Ringtone dropdown & Test button)
 * 11. 🔆 Brightness (Manual, LDR Auto, Night/Day Low/High Cut calibration)
 * 12. 🎨 Color Studio (Static, Smooth Fade, Rainbow, Custom RGB, Sweep Random + 12 Palettes)
 * 13. 🎬 Smart Color Playlist (1-8 Scenes, Quick presets: Energy/Ocean/Sunset/Spectrum)
 * 14. ✦ Alhamdulillah ✦ Footer & Developer Sultan Mahamud info
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspWebUiConsole(
    viewModel: ClockViewModel,
    uiState: SultanClockUiState,
    modifier: Modifier = Modifier
) {
    // 13 RTTTL Tone names from Arduino code
    val toneNames = remember {
        listOf(
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
    }

    // 12 Palette Colors from Arduino code
    val paletteColors = remember {
        listOf(
            Color(255, 0, 0),     // Red
            Color(255, 255, 0),   // Yellow
            Color(255, 165, 0),   // Orange
            Color(255, 192, 64),  // Gold
            Color(0, 255, 255),   // Cyan
            Color(0, 255, 0),     // Green
            Color(0, 200, 140),   // Teal
            Color(128, 0, 255),   // Purple
            Color(255, 0, 255),   // Magenta
            Color(0, 128, 255),   // Blue
            Color(0, 40, 255),    // Deep Blue
            Color(255, 255, 255)  // White
        )
    }
    val colorNames = remember {
        listOf(
            "Red", "Yellow", "Orange", "Gold", "Cyan", "Green",
            "Teal", "Purple", "Magenta", "Blue", "Deep Blue", "White"
        )
    }

    // Prayer waqt list from uploaded screenshot
    val waqtList = remember {
        listOf(
            Pair("FAJR WAQT", "ফজর"),
            Pair("SUNRISE", "সূর্যোদয়"),
            Pair("DHUHR WAQT", "যোহর"),
            Pair("ASR WAQT", "আসর"),
            Pair("SUNSET", "সূর্যাস্ত"),
            Pair("MAGHRIB WAQT", "মাগরিব"),
            Pair("ISHA WAQT", "ইশা"),
            Pair("MIDNIGHT", "মধ্যরাত"),
            Pair("TAHAJJUD", "তাহাজ্জুদ"),
            Pair("SEHRI END", "সাহরি শেষ")
        )
    }

    var calcMethod by remember { mutableIntStateOf(0) }
    val calcMethodOptions = listOf(
        "Karachi - ইসলামিক রিসার্চ (ডিফল্ট)",
        "Univ. of Islamic Sciences, Karachi",
        "ISNA - ইসলামিক সোসাইটি উত্তর আমেরিকা",
        "Muslim World League (MWL)"
    )

    var juristicMethod by remember { mutableStateOf("Hanafi (হানাফী)") }
    var expandedJuristicDropdown by remember { mutableStateOf(false) }

    var expandedColorDropdown by remember { mutableStateOf(false) }
    var expandedStartingColorDropdown by remember { mutableStateOf(false) }
    var expandedFixedToneDropdown by remember { mutableStateOf(false) }
    var expandedAlarm1ToneDropdown by remember { mutableStateOf(false) }
    var expandedAlarm2ToneDropdown by remember { mutableStateOf(false) }
    var expandedPlaylistCountDropdown by remember { mutableStateOf(false) }

    var wifiScanExpanded by remember { mutableStateOf(false) }

    // Local states for tone settings
    var toneModeLocal by remember(uiState.hourlyChime.mode) { mutableIntStateOf(uiState.hourlyChime.mode) }
    var toneIndexLocal by remember(uiState.hourlyChime.fixedTrack) { mutableIntStateOf(uiState.hourlyChime.fixedTrack.coerceIn(0, 12)) }
    var activeTonePlaying by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 0. BRANDING & HEADER (AI ELITE MATRIX)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AmberOrange.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberOrange.copy(alpha = 0.5f)),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "AI ELITE MATRIX",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberOrange,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "SULTAN DIGITAL CLOCK",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = GoldPrimary,
                letterSpacing = 1.sp
            )

            Text(
                text = "TIME IS VERY IMPORTANT IN OUR LIFE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Profile Avatar Badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CardBackgroundElevated)
                    .border(2.dp, Brush.linearGradient(listOf(GoldPrimary, AmberOrange, CyanAccent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mosque,
                    contentDescription = "Sultan Clock Avatar",
                    tint = GoldPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Live Virtual LED Clock Simulator (3D card with Live digits)
        VirtualLedClockMirror(
            dashboardData = uiState.dashboard,
            colorConfig = uiState.colorConfig,
            onToggleDisplay = { viewModel.toggleDisplay() },
            onApplyPreset = { viewModel.applyColorPreset(it) },
            modifier = Modifier.fillMaxWidth()
        )

        // ==========================================
        // SECTION 1: DISPLAY CONTROL & AUTO SCHEDULE
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISPLAY CONTROL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISPLAY STATUS: ${if (uiState.dashboard.isDisplayOn) "ON" else "OFF"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (uiState.dashboard.isDisplayOn) EmeraldGreen else ErrorRed
                )

                Button(
                    onClick = { viewModel.toggleDisplay() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.dashboard.isDisplayOn) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp).testTag("display_toggle_webui_btn")
                ) {
                    Icon(
                        imageVector = if (uiState.dashboard.isDisplayOn) Icons.Default.PowerOff else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.dashboard.isDisplayOn) "TURN OFF" else "TURN ON",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = CardBorder)

            Text(
                text = "⏰ AUTO SCHEDULE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "অলসতা মুমিনের জন্য নয়,সময়কে কাজে লাগানোই ইমানের পরিচয়।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Auto Schedule", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Checkbox(
                    checked = uiState.displaySchedule.isScheduleEnabled,
                    onCheckedChange = {
                        viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(isScheduleEnabled = it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Display OFF Time:", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = String.format("%02d:%02d", uiState.displaySchedule.startHour, uiState.displaySchedule.startMinute),
                        onValueChange = {
                            val parts = it.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: uiState.displaySchedule.startHour
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: uiState.displaySchedule.startMinute
                            viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(startHour = h.coerceIn(0, 23), startMinute = m.coerceIn(0, 59)))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Display ON Time:", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = String.format("%02d:%02d", uiState.displaySchedule.endHour, uiState.displaySchedule.endMinute),
                        onValueChange = {
                            val parts = it.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: uiState.displaySchedule.endHour
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: uiState.displaySchedule.endMinute
                            viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(endHour = h.coerceIn(0, 23), endMinute = m.coerceIn(0, 59)))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { viewModel.saveDisplaySchedule() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("SET UP SCHEDULE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ==========================================
        // SECTION 2: 💡 D7 LIGHT CONTROL
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "D7 LIGHT CONTROL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIGHT STATUS: ${if (uiState.dashboard.isLightOn) "ON" else "OFF"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (uiState.dashboard.isLightOn) EmeraldGreen else ErrorRed
                )

                Button(
                    onClick = { viewModel.toggleLight() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.dashboard.isLightOn) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp).testTag("light_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (uiState.dashboard.isLightOn) Icons.Default.PowerOff else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.dashboard.isLightOn) "TURN OFF" else "TURN ON",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ==========================================
        // SECTION 3: 🕐 TIME SYNC
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TIME SYNC",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { viewModel.syncTimeFromPhone() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("sync_phone_btn")
            ) {
                Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("📱 SYNC FROM PHONE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { viewModel.syncFromNtp() },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("sync_ntp_btn")
            ) {
                Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("🌐 SYNC NTP INTERNET TIME & RTC", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ==========================================
        // SECTION 4: 📅 SMART DATE DISPLAY
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SMART DATE DISPLAY",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "সততাই মানুষের সবচেয়ে বড় শক্তি।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("English Date", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = uiState.dateSettings.isDateEnabled,
                    onCheckedChange = {
                        viewModel.updateDateSettings(uiState.dateSettings.copy(isDateEnabled = it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("বাংলা তারিখ", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = uiState.dateSettings.isBanglaDate,
                    onCheckedChange = {
                        viewModel.updateDateSettings(uiState.dateSettings.copy(isBanglaDate = it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.saveDateSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("SAVE DATE SETTINGS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ==========================================
        // SECTION 5: 🎵 HOURLY TONE (13 RTTTL TONES & RANGE)
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HOURLY TONE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "মানুষের সাথে অহংকার করে কথা বলো না,আল্লাহ অহংকারীকে পছন্দ করেন না।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hourly Tone চালু রাখো", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Checkbox(
                    checked = uiState.hourlyChime.enabled,
                    onCheckedChange = {
                        viewModel.updateHourlyChime(uiState.hourlyChime.copy(enabled = it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CardBorder)

            Text(
                text = "🌙 Active Time Range",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary
            )
            Text(
                text = "ধৈর্য ধারণ করো বিপদের সময়ে,নিশ্চয়ই আল্লাহ ধৈর্যশীলদের ভালোবাসেন।।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Time Range চালু করো", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = uiState.hourlyChime.toneRangeEnabled,
                    onCheckedChange = {
                        viewModel.updateHourlyChime(uiState.hourlyChime.copy(toneRangeEnabled = it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            if (uiState.hourlyChime.toneRangeEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tone শুরুর সময়:", fontSize = 11.sp, color = TextSecondary)
                        OutlinedTextField(
                            value = String.format("%02d:00", uiState.hourlyChime.toneStartHour),
                            onValueChange = {
                                val h = it.split(":").getOrNull(0)?.toIntOrNull() ?: uiState.hourlyChime.toneStartHour
                                viewModel.updateHourlyChime(uiState.hourlyChime.copy(toneStartHour = h.coerceIn(0, 23)))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tone বন্ধের সময়:", fontSize = 11.sp, color = TextSecondary)
                        OutlinedTextField(
                            value = String.format("%02d:00", uiState.hourlyChime.toneEndHour),
                            onValueChange = {
                                val h = it.split(":").getOrNull(0)?.toIntOrNull() ?: uiState.hourlyChime.toneEndHour
                                viewModel.updateHourlyChime(uiState.hourlyChime.copy(toneEndHour = h.coerceIn(0, 23)))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Text(
                text = "সত্য কথা বলতে ভয় পেও না,কারণ সত্যই মানুষকে মুক্তি দেয়।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            Button(
                onClick = {
                    viewModel.saveToneRange(
                        enabled = uiState.hourlyChime.toneRangeEnabled,
                        startHour = uiState.hourlyChime.toneStartHour,
                        endHour = uiState.hourlyChime.toneEndHour
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text("💾 SAVE TIME RANGE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = CardBorder)

            Text(
                text = "🎲 Tone Mode",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary
            )

            // Radio Options: Random, Sequential, Fixed
            val toneModeOptions = listOf(
                Pair(1, "🎲 Random — প্রতি ঘণ্টায় এলোমেলো tone"),
                Pair(2, "🔢 Sequential — ১→২→...→১৩→১ ক্রমে"),
                Pair(0, "📌 Fixed — সবসময় একটাই tone")
            )

            toneModeOptions.forEach { (modeVal, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toneModeLocal = modeVal }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = toneModeLocal == modeVal,
                        onClick = { toneModeLocal = modeVal },
                        colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = label, fontSize = 12.sp, color = TextPrimary)
                }
            }

            if (toneModeLocal == 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Tone নির্বাচন করো:", fontSize = 11.sp, color = TextSecondary)
                Box {
                    OutlinedButton(
                        onClick = { expandedFixedToneDropdown = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = toneNames.getOrElse(toneIndexLocal) { "Tone 1" },
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DropdownMenu(
                        expanded = expandedFixedToneDropdown,
                        onDismissRequest = { expandedFixedToneDropdown = false }
                    ) {
                        toneNames.forEachIndexed { idx, name ->
                            DropdownMenuItem(
                                text = { Text("$idx: $name") },
                                onClick = {
                                    toneIndexLocal = idx
                                    expandedFixedToneDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CardBorder)

            Text(
                text = "🔊 Test Tones (RTTTL Melody)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary
            )
            Text(
                text = "জ্ঞান অর্জন করো এবং তা কাজে লাগাও,জ্ঞান ছাড়া জীবন অন্ধকার।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // 2-column grid of 13 tone buttons
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until (toneNames.size + 1) / 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val idx1 = row * 2
                        val idx2 = row * 2 + 1

                        Button(
                            onClick = {
                                activeTonePlaying = idx1
                                viewModel.testTone(idx1)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTonePlaying == idx1) AmberOrange else CardBackgroundElevated,
                                contentColor = TextPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text(
                                text = "▶ ${toneNames[idx1]}",
                                fontSize = 10.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (idx2 < toneNames.size) {
                            Button(
                                onClick = {
                                    activeTonePlaying = idx2
                                    viewModel.testTone(idx2)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeTonePlaying == idx2) AmberOrange else CardBackgroundElevated,
                                    contentColor = TextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text(
                                    text = "▶ ${toneNames[idx2]}",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { viewModel.saveTone(toneModeLocal, toneIndexLocal) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("💾 SAVE TONE SETTINGS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ==========================================
        // SECTION 6: 🕌 PRAYER (AZAN) SETTINGS / আযান ওয়াক্ত
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Mosque, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PRAYER (AZAN) SETTINGS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "সঠিক সময়ে নামাজ আদায় করো,নামাজ মুমিনের মেরুদণ্ড।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            Text("Calculation Method (হিসাবের পদ্ধতি):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

            calcMethodOptions.forEachIndexed { index, name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { calcMethod = index }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = calcMethod == index,
                        onClick = { calcMethod = index },
                        colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = name, fontSize = 11.sp, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Asr Juristic Method:", fontSize = 11.sp, color = TextSecondary)
            Box {
                OutlinedButton(
                    onClick = { expandedJuristicDropdown = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(juristicMethod, color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = expandedJuristicDropdown,
                    onDismissRequest = { expandedJuristicDropdown = false }
                ) {
                    listOf("Hanafi (হানাফী)", "Shafi/Standard (শাফেঈ/সাধারণ)").forEach { j ->
                        DropdownMenuItem(
                            text = { Text(j) },
                            onClick = {
                                juristicMethod = j
                                expandedJuristicDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Waqt Grid (ওয়াক্ত সময়সূচী):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)

            Spacer(modifier = Modifier.height(6.dp))

            // 10 Golden Waqt Buttons grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until (waqtList.size + 1) / 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val item1 = waqtList[row * 2]
                        val item2 = waqtList.getOrNull(row * 2 + 1)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(item1.first, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                Text(item1.second, fontSize = 10.sp, color = TextSecondary)
                            }
                        }

                        if (item2 != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = GoldPrimary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(item2.first, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                    Text(item2.second, fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { viewModel.saveTrackAssignments() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("SET UP PRAYER SETTINGS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ==========================================
        // SECTION 7: 📶 WIFI SETTINGS
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WIFI SETTINGS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    wifiScanExpanded = true
                    viewModel.scanWifiNetworks()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("🔍 SCAN WiFi NETWORKS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            if (wifiScanExpanded && uiState.wifiScanList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Available Networks (ক্লিক করে নির্বাচন করুন):", fontSize = 11.sp, color = TextSecondary)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.wifiScanList.forEach { net ->
                        Surface(
                            onClick = {
                                viewModel.updateWifiConfig(uiState.wifiConfig.copy(ssid = net.ssid))
                                wifiScanExpanded = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = CardBackgroundElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(net.ssid, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${net.rssi} dBm", fontSize = 10.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (net.isSecure) Icons.Default.Lock else Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = if (net.isSecure) AmberOrange else EmeraldGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CardBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable WiFi Connection", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = uiState.wifiConfig.enabled,
                    onCheckedChange = {
                        viewModel.updateWifiConfig(uiState.wifiConfig.copy(enabled = it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text("WiFi Name (SSID):", fontSize = 11.sp, color = TextSecondary)
            OutlinedTextField(
                value = uiState.wifiConfig.ssid,
                onValueChange = { viewModel.updateWifiConfig(uiState.wifiConfig.copy(ssid = it)) },
                singleLine = true,
                placeholder = { Text("নেটওয়ার্ক নাম লিখুন বা উপরে scan করুন") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WiFi Has Password", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = !uiState.wifiConfig.isOpen,
                    onCheckedChange = {
                        viewModel.updateWifiConfig(uiState.wifiConfig.copy(isOpen = !it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            if (!uiState.wifiConfig.isOpen) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Password:", fontSize = 11.sp, color = TextSecondary)
                OutlinedTextField(
                    value = uiState.wifiConfig.password,
                    onValueChange = { viewModel.updateWifiConfig(uiState.wifiConfig.copy(password = it)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CardBorder)

            Text("🔒 STATIC IP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use Static IP", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = uiState.wifiConfig.isStatic,
                    onCheckedChange = {
                        viewModel.updateWifiConfig(uiState.wifiConfig.copy(isStatic = it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            if (uiState.wifiConfig.isStatic) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.wifiConfig.ip,
                    onValueChange = { viewModel.updateWifiConfig(uiState.wifiConfig.copy(ip = it)) },
                    label = { Text("Static IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.wifiConfig.gateway,
                    onValueChange = { viewModel.updateWifiConfig(uiState.wifiConfig.copy(gateway = it)) },
                    label = { Text("Gateway") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.wifiConfig.subnet,
                    onValueChange = { viewModel.updateWifiConfig(uiState.wifiConfig.copy(subnet = it)) },
                    label = { Text("Subnet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "⚠️ Gateway = Router IP (যেমন: 192.168.0.1)",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { viewModel.saveWifiConfig() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("SAVE & REBOOT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Connection status pill
            if (uiState.dashboard.wifiConnected && uiState.dashboard.wifiSsid.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldGreen.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✅ Connected: ${uiState.dashboard.wifiSsid} | IP: ${uiState.dashboard.ipAddress}",
                        fontSize = 12.sp,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // ==========================================
        // SECTION 8: ⚙️ DISPLAY SETTINGS
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISPLAY SETTINGS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 12/24 Hour format radio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        viewModel.updateDisplaySettings(is12Hour = false)
                    }
                ) {
                    RadioButton(
                        selected = !uiState.dashboard.is12Hour,
                        onClick = { viewModel.updateDisplaySettings(is12Hour = false) },
                        colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                    )
                    Text("24-Hour", fontSize = 13.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        viewModel.updateDisplaySettings(is12Hour = true)
                    }
                ) {
                    RadioButton(
                        selected = uiState.dashboard.is12Hour,
                        onClick = { viewModel.updateDisplaySettings(is12Hour = true) },
                        colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                    )
                    Text("12-Hour", fontSize = 13.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Colon Blink (কোলন ব্লিঙ্ক)", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = uiState.dashboard.isLightOn,
                    onCheckedChange = {
                        viewModel.updateDisplaySettings(colonBlink = it)
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Date (তারিখ প্রদর্শন)", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = uiState.dateSettings.isDateEnabled,
                    onCheckedChange = {
                        viewModel.updateDisplaySettings(showDate = it)
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            Text(
                text = "পিতা-মাতার সাথে সদ্ব্যবহার করো,তাদের কষ্টের কথা স্মরণ রাখো।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            Button(
                onClick = { viewModel.saveDisplaySettings() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("SET UP DISPLAY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ==========================================
        // SECTION 9: ⏰ ALARMS
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ALARMS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "শিরক করো না কখনো আল্লাহর সাথে,এটি সবচেয়ে বড় জুলুম।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // ALARM 1
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackgroundElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏰ ALARM 1", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("চালু", fontSize = 12.sp, color = TextPrimary)
                            Checkbox(
                                checked = uiState.alarmConfig.alarm1Enabled,
                                onCheckedChange = {
                                    viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm1Enabled = it))
                                },
                                colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = String.format("%02d:%02d", uiState.alarmConfig.alarm1Hour, uiState.alarmConfig.alarm1Minute),
                        onValueChange = {
                            val parts = it.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: uiState.alarmConfig.alarm1Hour
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: uiState.alarmConfig.alarm1Minute
                            viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm1Hour = h.coerceIn(0, 23), alarm1Minute = m.coerceIn(0, 59)))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("🎵 Ringtone:", fontSize = 11.sp, color = TextSecondary)
                    Box {
                        OutlinedButton(
                            onClick = { expandedAlarm1ToneDropdown = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(toneNames.getOrElse(uiState.alarmConfig.alarm1Track.coerceIn(0, 12)) { "Tone 1" }, color = GoldPrimary)
                        }
                        DropdownMenu(
                            expanded = expandedAlarm1ToneDropdown,
                            onDismissRequest = { expandedAlarm1ToneDropdown = false }
                        ) {
                            toneNames.forEachIndexed { idx, name ->
                                DropdownMenuItem(
                                    text = { Text("$idx: $name") },
                                    onClick = {
                                        viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm1Track = idx))
                                        expandedAlarm1ToneDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { viewModel.testTone(uiState.alarmConfig.alarm1Track) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("▶ Test This Alarm Tone", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ALARM 2
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackgroundElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏰ ALARM 2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("চালু", fontSize = 12.sp, color = TextPrimary)
                            Checkbox(
                                checked = uiState.alarmConfig.alarm2Enabled,
                                onCheckedChange = {
                                    viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm2Enabled = it))
                                },
                                colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = String.format("%02d:%02d", uiState.alarmConfig.alarm2Hour, uiState.alarmConfig.alarm2Minute),
                        onValueChange = {
                            val parts = it.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: uiState.alarmConfig.alarm2Hour
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: uiState.alarmConfig.alarm2Minute
                            viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm2Hour = h.coerceIn(0, 23), alarm2Minute = m.coerceIn(0, 59)))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("🎵 Ringtone:", fontSize = 11.sp, color = TextSecondary)
                    Box {
                        OutlinedButton(
                            onClick = { expandedAlarm2ToneDropdown = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(toneNames.getOrElse(uiState.alarmConfig.alarm2Track.coerceIn(0, 12)) { "Tone 2" }, color = GoldPrimary)
                        }
                        DropdownMenu(
                            expanded = expandedAlarm2ToneDropdown,
                            onDismissRequest = { expandedAlarm2ToneDropdown = false }
                        ) {
                            toneNames.forEachIndexed { idx, name ->
                                DropdownMenuItem(
                                    text = { Text("$idx: $name") },
                                    onClick = {
                                        viewModel.updateAlarmConfig(uiState.alarmConfig.copy(alarm2Track = idx))
                                        expandedAlarm2ToneDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { viewModel.testTone(uiState.alarmConfig.alarm2Track) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("▶ Test This Alarm Tone", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { viewModel.saveAlarmConfig() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("💾 SAVE ALARMS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.stopAlarmAudio() },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text("STOP ACTIVE ALARM", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // ==========================================
        // SECTION 10: 🔆 BRIGHTNESS
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Brightness6, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BRIGHTNESS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "আল্লাহ কাউকে তার সাধ্যের বাইরে কষ্ট দেন না,তাই সব পরিস্থিতিতে ধৈর্য রাখো।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto Brightness (LDR Sensor)", fontSize = 13.sp, color = TextPrimary)
                Checkbox(
                    checked = uiState.brightnessConfig.autoLdr,
                    onCheckedChange = {
                        viewModel.updateBrightnessConfig(uiState.brightnessConfig.copy(autoLdr = it))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary, checkmarkColor = Color.Black)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Manual:", fontSize = 12.sp, color = TextSecondary)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = GoldPrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${uiState.brightnessConfig.manualBrightness}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Slider(
                value = uiState.brightnessConfig.manualBrightness.toFloat(),
                onValueChange = { viewModel.updateBrightnessConfig(uiState.brightnessConfig.copy(manualBrightness = it.toInt())) },
                valueRange = 1f..255f,
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EmeraldGreen.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Live LDR Raw: ${uiState.dashboard.ldrRaw} / 1023 | Applied Brightness: ${uiState.dashboard.appliedBrightness}",
                    fontSize = 11.sp,
                    color = EmeraldGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CardBorder)

            Text("Night/Day Calibration:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Low Cut (Min):", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = uiState.brightnessConfig.ldrLowCut.toString(),
                        onValueChange = {
                            viewModel.updateBrightnessConfig(uiState.brightnessConfig.copy(ldrLowCut = it.toIntOrNull() ?: 150))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("High Cut (Max):", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = uiState.brightnessConfig.ldrHighCut.toString(),
                        onValueChange = {
                            viewModel.updateBrightnessConfig(uiState.brightnessConfig.copy(ldrHighCut = it.toIntOrNull() ?: 900))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text(
                text = "তোমরা হতাশ হয়ো না আল্লাহর রহমত থেকে, সবসময় তওবা করে ফিরে আসো।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            Button(
                onClick = { viewModel.saveBrightness() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("SET UP BRIGHTNESS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ==========================================
        // SECTION 11: 🎨 COLOR MODES / COLOR STUDIO
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COLOR MODES",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "● LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Glowing Color Preview Box
            val previewColor = when (uiState.colorConfig.mode) {
                0, 1 -> paletteColors.getOrElse(uiState.colorConfig.staticColorIndex.coerceIn(0, 11)) { Color.Red }
                2 -> Color(0xFFFF5252)
                3 -> Color(uiState.colorConfig.red, uiState.colorConfig.green, uiState.colorConfig.blue)
                4 -> Color(0xFF00E5FF)
                else -> Color.Red
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(previewColor, previewColor.copy(alpha = 0.6f), Color(0xFF1A1A2E))
                        )
                    )
                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("#%02X%02X%02X", (previewColor.red * 255).toInt(), (previewColor.green * 255).toInt(), (previewColor.blue * 255).toInt()),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5 Mode Selection Chips
            val modeLabels = listOf(
                Pair(0, "◈ STATIC"),
                Pair(1, "◌ SMOOTH FADE"),
                Pair(2, "✦ RAINBOW"),
                Pair(3, "RGB CUSTOM"),
                Pair(4, "✧ SWEEP RANDOM")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                modeLabels.take(3).forEach { (modeVal, label) ->
                    val isSel = uiState.colorConfig.mode == modeVal
                    Surface(
                        onClick = { viewModel.updateColorConfig(uiState.colorConfig.copy(mode = modeVal)) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else CardBackgroundElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) GoldPrimary else CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) GoldPrimary else TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modeLabels.drop(3).forEach { (modeVal, label) ->
                    val isSel = uiState.colorConfig.mode == modeVal
                    Surface(
                        onClick = { viewModel.updateColorConfig(uiState.colorConfig.copy(mode = modeVal)) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else CardBackgroundElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) GoldPrimary else CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) GoldPrimary else TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode dropdown
            Text("Mode:", fontSize = 11.sp, color = TextSecondary)
            Box {
                OutlinedButton(
                    onClick = { expandedColorDropdown = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modeName = when (uiState.colorConfig.mode) {
                        0 -> "Static Color"
                        1 -> "Smooth Fade"
                        2 -> "Rainbow"
                        3 -> "Custom RGB"
                        4 -> "Sweep Random"
                        else -> "Static Color"
                    }
                    Text(modeName, color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = expandedColorDropdown,
                    onDismissRequest = { expandedColorDropdown = false }
                ) {
                    listOf("Static Color" to 0, "Smooth Fade" to 1, "Rainbow" to 2, "Custom RGB" to 3, "Sweep Random" to 4).forEach { (name, m) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.updateColorConfig(uiState.colorConfig.copy(mode = m))
                                expandedColorDropdown = false
                            }
                        )
                    }
                }
            }

            // 12 Palette Swatches Grid
            if (uiState.colorConfig.mode == 0 || uiState.colorConfig.mode == 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("PALETTE (12 Premium Colors):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0..5) {
                                val idx = row * 6 + col
                                val isSelected = uiState.colorConfig.staticColorIndex == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(paletteColors[idx])
                                        .border(
                                            if (isSelected) 2.dp else 1.dp,
                                            if (isSelected) Color.White else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            viewModel.updateColorConfig(uiState.colorConfig.copy(staticColorIndex = idx))
                                        }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Starting Color:", fontSize = 11.sp, color = TextSecondary)
                Box {
                    OutlinedButton(
                        onClick = { expandedStartingColorDropdown = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(colorNames.getOrElse(uiState.colorConfig.staticColorIndex.coerceIn(0, 11)) { "Red" }, color = GoldPrimary)
                    }
                    DropdownMenu(
                        expanded = expandedStartingColorDropdown,
                        onDismissRequest = { expandedStartingColorDropdown = false }
                    ) {
                        colorNames.forEachIndexed { idx, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.updateColorConfig(uiState.colorConfig.copy(staticColorIndex = idx))
                                    expandedStartingColorDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Color Change Interval (sec):", fontSize = 11.sp, color = TextSecondary)
                OutlinedTextField(
                    value = uiState.colorConfig.colorIntervalSec.toString(),
                    onValueChange = {
                        viewModel.updateColorConfig(uiState.colorConfig.copy(colorIntervalSec = it.toIntOrNull() ?: 5))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Rainbow Speed slider
            if (uiState.colorConfig.mode == 2) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("🌈 RAINBOW SPEED (1=দ্রুত .. 10=ধীর):", fontSize = 11.sp, color = TextSecondary)
                Slider(
                    value = uiState.colorConfig.animSpeed.toFloat(),
                    onValueChange = { viewModel.updateColorConfig(uiState.colorConfig.copy(animSpeed = it.toInt())) },
                    valueRange = 1f..10f,
                    colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                )
            }

            // Sweep Speed slider
            if (uiState.colorConfig.mode == 4) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("✨ SWEEP SPEED (1=সবচেয়ে দ্রুত .. 10=সবচেয়ে ধীর):", fontSize = 11.sp, color = TextSecondary)
                Slider(
                    value = uiState.colorConfig.animSpeed.toFloat(),
                    onValueChange = { viewModel.updateColorConfig(uiState.colorConfig.copy(animSpeed = it.toInt())) },
                    valueRange = 1f..10f,
                    colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                )
            }

            // Custom RGB sliders
            if (uiState.colorConfig.mode == 3) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("R: ${uiState.colorConfig.red}", fontSize = 11.sp, color = Color(0xFFFF6666))
                Slider(
                    value = uiState.colorConfig.red.toFloat(),
                    onValueChange = { viewModel.updateColorConfig(uiState.colorConfig.copy(red = it.toInt())) },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
                )
                Text("G: ${uiState.colorConfig.green}", fontSize = 11.sp, color = Color(0xFF66FF66))
                Slider(
                    value = uiState.colorConfig.green.toFloat(),
                    onValueChange = { viewModel.updateColorConfig(uiState.colorConfig.copy(green = it.toInt())) },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green)
                )
                Text("B: ${uiState.colorConfig.blue}", fontSize = 11.sp, color = Color(0xFF6699FF))
                Slider(
                    value = uiState.colorConfig.blue.toFloat(),
                    onValueChange = { viewModel.updateColorConfig(uiState.colorConfig.copy(blue = it.toInt())) },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
                )
            }

            Text(
                text = "সময় আল্লাহর অমূল্য আমানত,প্রতিটি মুহূর্তের হিসাব একদিন দিতে হবে।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )

            Button(
                onClick = { viewModel.saveColor() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("SAVE COLOR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ==========================================
        // SECTION 12: 🎬 SMART COLOR PLAYLIST
        // ==========================================
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SMART COLOR PLAYLIST",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyanAccent.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "AUTO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "মানুষের সাথে সুন্দরভাবে কথা বলো,উত্তম আচরণই মুমিনের পরিচয়।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playlist: ${if (uiState.colorPlaylist.enabled) "ON" else "OFF"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.colorPlaylist.enabled) EmeraldGreen else ErrorRed
                )

                Button(
                    onClick = { viewModel.toggleColorPlaylist(!uiState.colorPlaylist.enabled) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.colorPlaylist.enabled) ErrorRed else EmeraldGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(if (uiState.colorPlaylist.enabled) "DISABLE PLAYLIST" else "ENABLE PLAYLIST", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Step count selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("কতটা Step (1-8):", fontSize = 12.sp, color = TextPrimary)
                Box {
                    OutlinedButton(
                        onClick = { expandedPlaylistCountDropdown = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("${uiState.colorPlaylist.steps.size} টা", color = GoldPrimary)
                    }
                    DropdownMenu(
                        expanded = expandedPlaylistCountDropdown,
                        onDismissRequest = { expandedPlaylistCountDropdown = false }
                    ) {
                        (1..8).forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count টা") },
                                onClick = {
                                    val currentSteps = uiState.colorPlaylist.steps.toMutableList()
                                    while (currentSteps.size < count) {
                                        currentSteps.add(ColorPlaylistStep(mode = 0, colorIndex = currentSteps.size % 12, durationSec = 30, speed = 5))
                                    }
                                    while (currentSteps.size > count) {
                                        currentSteps.removeAt(currentSteps.size - 1)
                                    }
                                    viewModel.updateColorPlaylist(uiState.colorPlaylist.copy(steps = currentSteps))
                                    expandedPlaylistCountDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Presets
            Text("QUICK PRESETS:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("🔥 ENERGY", "🌊 OCEAN", "🌅 SUNSET", "🌈 SPECTRUM").forEachIndexed { pIdx, pLabel ->
                    Button(
                        onClick = {
                            val presetSteps = when (pIdx) {
                                0 -> listOf(ColorPlaylistStep(0, 0, 20), ColorPlaylistStep(1, 2, 20), ColorPlaylistStep(0, 3, 20))
                                1 -> listOf(ColorPlaylistStep(1, 4, 25), ColorPlaylistStep(0, 9, 25), ColorPlaylistStep(1, 10, 25))
                                2 -> listOf(ColorPlaylistStep(0, 2, 20), ColorPlaylistStep(1, 3, 20), ColorPlaylistStep(0, 8, 20))
                                else -> listOf(ColorPlaylistStep(2, 0, 30, speed = 4), ColorPlaylistStep(4, 0, 30, speed = 5))
                            }
                            viewModel.updateColorPlaylist(uiState.colorPlaylist.copy(steps = presetSteps))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = GoldPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(pLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Step Scenes Cards
            uiState.colorPlaylist.steps.forEachIndexed { sIdx, step ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardBackgroundElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SCENE ${sIdx + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                            Text("${step.durationSec}s", fontSize = 11.sp, color = CyanAccent, fontFamily = FontFamily.Monospace)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mode:", fontSize = 10.sp, color = TextSecondary)
                                OutlinedTextField(
                                    value = when (step.mode) {
                                        0 -> "Static"
                                        1 -> "Smooth Fade"
                                        2 -> "Rainbow"
                                        3 -> "Custom RGB"
                                        4 -> "Sweep"
                                        else -> "Static"
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Duration (s):", fontSize = 10.sp, color = TextSecondary)
                                OutlinedTextField(
                                    value = step.durationSec.toString(),
                                    onValueChange = {
                                        val newD = it.toIntOrNull() ?: step.durationSec
                                        val updated = uiState.colorPlaylist.steps.toMutableList()
                                        updated[sIdx] = step.copy(durationSec = newD)
                                        viewModel.updateColorPlaylist(uiState.colorPlaylist.copy(steps = updated))
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleColorPlaylist(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("▶ PREVIEW SEQUENCE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.saveColorPlaylist() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("💾 SAVE PLAYLIST", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        // ==========================================
        // SECTION 13: ✦ FOOTER & DEVELOPER INFO ✦
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackgroundElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "✦ ALHAMDULILLAH ✦ ALHAMDULILLAH ✦ ALHAMDULILLAH ✦",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "MD: SULTAN MAHAMUD",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = GoldPrimary,
                letterSpacing = 1.sp
            )

            Text(
                text = "📞 01740-236384   |   ✉️ sultanmahamud5497@gmail.com",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "ESP8266 + DS3231 + WS2812B + LDR • v3.2 FIXED",
                fontSize = 10.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
