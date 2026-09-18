package com.example.ui.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ColorConfig
import com.example.data.model.ColorPreset
import com.example.ui.components.ActionFeedbackBanner
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel
import com.example.ui.viewmodel.SultanClockUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsScreen(
    viewModel: ClockViewModel,
    uiState: SultanClockUiState
) {
    var expandedColorModeDropdown by remember { mutableStateOf(false) }

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

        // --- BRIGHTNESS SECTION ---
        item {
            SectionHeader(
                title = "BRIGHTNESS CONTROL",
                icon = Icons.Default.Brightness6,
                accentColor = GoldPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                // Live Slider value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manual Brightness", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text = "${uiState.brightnessConfig.manualBrightness} / 255",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = uiState.brightnessConfig.manualBrightness.toFloat(),
                    onValueChange = { viewModel.updateManualBrightness(it.toInt()) },
                    valueRange = 1f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary,
                        inactiveTrackColor = CardBackgroundElevated
                    ),
                    modifier = Modifier.testTag("brightness_slider")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Auto LDR Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("LDR Auto Brightness", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text("Auto adjusts LED intensity via light sensor", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = uiState.brightnessConfig.autoLdr,
                        onCheckedChange = { viewModel.updateAutoLdr(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = CyanAccent.copy(alpha = 0.3f))
                    )
                }

                if (uiState.brightnessConfig.autoLdr) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("LDR Calibration Cuts:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Low Cut: ${uiState.brightnessConfig.ldrLowCut}", fontSize = 11.sp, color = TextSecondary)
                        Text("High Cut: ${uiState.brightnessConfig.ldrHighCut}", fontSize = 11.sp, color = TextSecondary)
                    }

                    RangeSlider(
                        value = uiState.brightnessConfig.ldrLowCut.toFloat()..uiState.brightnessConfig.ldrHighCut.toFloat(),
                        onValueChange = { range ->
                            viewModel.updateLdrCuts(range.start.toInt(), range.endInclusive.toInt())
                        },
                        valueRange = 0f..1023f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanAccent,
                            activeTrackColor = CyanAccent,
                            inactiveTrackColor = CardBackgroundElevated
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.saveBrightness() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_brightness_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Brightness", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- COLOR CONTROL SECTION ---
        item {
            SectionHeader(
                title = "COLOR ENGINE & RGB",
                icon = Icons.Default.Palette,
                accentColor = CyanAccent
            )

            Spacer(modifier = Modifier.height(8.dp))

            GlassCard {
                // Color Mode Selector
                Text("Select Animation Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedColorModeDropdown,
                    onExpandedChange = { expandedColorModeDropdown = !expandedColorModeDropdown }
                ) {
                    OutlinedTextField(
                        value = ColorConfig.MODES.getOrElse(uiState.colorConfig.mode) { "Mode ${uiState.colorConfig.mode}" },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedColorModeDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expandedColorModeDropdown,
                        onDismissRequest = { expandedColorModeDropdown = false },
                        modifier = Modifier.background(CardBackgroundElevated)
                    ) {
                        ColorConfig.MODES.forEachIndexed { index, modeName ->
                            DropdownMenuItem(
                                text = { Text(modeName, color = if (uiState.colorConfig.mode == index) CyanAccent else TextPrimary) },
                                onClick = {
                                    viewModel.updateColorMode(index)
                                    expandedColorModeDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // RGB Color Swatch & Preset Chips
                val currentColor = Color(
                    uiState.colorConfig.red,
                    uiState.colorConfig.green,
                    uiState.colorConfig.blue
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Swatch Preview
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentColor)
                            .border(2.dp, CardBorder, RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "RGB(${uiState.colorConfig.red}, ${uiState.colorConfig.green}, ${uiState.colorConfig.blue})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text("Hardware WS2812B LEDs", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Presets Row
                Text("One-Tap Theme Presets (রেডিমেড থিম):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorPreset.PRESETS.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val presetColor = Color(preset.red, preset.green, preset.blue)
                                Surface(
                                    onClick = { viewModel.applyColorPreset(preset) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = CardBackgroundElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(presetColor)
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                        )
                                        Column {
                                            Text(
                                                text = preset.nameBn,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = preset.nameEn,
                                                fontSize = 9.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Red Slider
                ColorChannelSlider(
                    name = "Red",
                    value = uiState.colorConfig.red,
                    color = Color(0xFFFF5252),
                    onValueChange = { viewModel.updateRgb(it, uiState.colorConfig.green, uiState.colorConfig.blue) }
                )

                // Green Slider
                ColorChannelSlider(
                    name = "Green",
                    value = uiState.colorConfig.green,
                    color = Color(0xFF69F0AE),
                    onValueChange = { viewModel.updateRgb(uiState.colorConfig.red, it, uiState.colorConfig.blue) }
                )

                // Blue Slider
                ColorChannelSlider(
                    name = "Blue",
                    value = uiState.colorConfig.blue,
                    color = Color(0xFF40C4FF),
                    onValueChange = { viewModel.updateRgb(uiState.colorConfig.red, uiState.colorConfig.green, it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.saveColor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_color_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color(0xFF00363D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Color Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- DISPLAY SCHEDULE SECTION ---
        item {
            SectionHeader(
                title = "DISPLAY SLEEP & SCHEDULE",
                icon = Icons.Default.AccessTime,
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
                        Text("Auto Display Schedule", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Turn OFF display during bedtime hours", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = uiState.displaySchedule.isScheduleEnabled,
                        onCheckedChange = {
                            viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(isScheduleEnabled = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha = 0.3f))
                    )
                }

                if (uiState.displaySchedule.isScheduleEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Start Hour
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Turn OFF At (Hour):", fontSize = 12.sp, color = TextSecondary)
                            OutlinedTextField(
                                value = uiState.displaySchedule.startHour.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { h ->
                                        viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(startHour = h.coerceIn(0, 23)))
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // End Hour
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Turn ON At (Hour):", fontSize = 12.sp, color = TextSecondary)
                            OutlinedTextField(
                                value = uiState.displaySchedule.endHour.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { h ->
                                        viewModel.updateDisplaySchedule(uiState.displaySchedule.copy(endHour = h.coerceIn(0, 23)))
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.saveDisplaySchedule() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_schedule_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color(0xFF1C1300)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Display Schedule", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ColorPresetChip(name: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.5.dp, CardBorder, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ColorChannelSlider(
    name: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        Text(
            text = value.toString(),
            fontSize = 12.sp,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0f..255f,
        colors = SliderDefaults.colors(
            thumbColor = color,
            activeTrackColor = color,
            inactiveTrackColor = CardBackgroundElevated
        )
    )
}
