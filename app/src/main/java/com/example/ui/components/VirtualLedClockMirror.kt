package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClockDashboardData
import com.example.data.model.ColorConfig
import com.example.data.model.ColorPreset
import com.example.ui.theme.*

/**
 * Seven-segment bitmask mapping for 0-9 and special characters
 * Bit 0: a (top)
 * Bit 1: b (top-right)
 * Bit 2: c (bottom-right)
 * Bit 3: d (bottom)
 * Bit 4: e (bottom-left)
 * Bit 5: f (top-left)
 * Bit 6: g (middle)
 */
private val SEGMENT_PATTERNS = mapOf(
    '0' to 0b00111111,
    '1' to 0b00000110,
    '2' to 0b01011011,
    '3' to 0b01001111,
    '4' to 0b01100110,
    '5' to 0b01101101,
    '6' to 0b01111101,
    '7' to 0b00000111,
    '8' to 0b01111111,
    '9' to 0b01101111,
    '-' to 0b01000000,
    ' ' to 0b00000000
)

enum class MirrorViewMode {
    TIME,
    ENGLISH_DATE,
    BANGLA_DATE
}

/**
 * Virtual LED Clock Simulator (Live Display Mirroring)
 * Accurately models the physical ESP32 30-WS2812B NeoPixel 7-segment digital clock:
 * - 4 Digits x 7 segments = 28 LEDs
 * - 1 Colon x 2 dots = 2 LEDs
 * Total = 30 LEDs with glow effects, dynamic color animations, and live rotation!
 */
@Composable
fun VirtualLedClockMirror(
    dashboardData: ClockDashboardData,
    colorConfig: ColorConfig,
    onToggleDisplay: () -> Unit,
    onApplyPreset: (ColorPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(MirrorViewMode.TIME) }

    // Infinite animation for Rainbow, Fade, and Colon blinking
    val infiniteTransition = rememberInfiniteTransition(label = "LedClockAnim")
    
    // Rainbow hue rotation (0..360)
    val rainbowHue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RainbowHue"
    )

    // Smooth fade breathing alpha (0.4f .. 1f)
    val fadeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FadeAlpha"
    )

    // Colon blink (1 sec cycle)
    val colonOn by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ColonBlink"
    )

    // Determine primary segment color based on Clock's active color mode
    val activeBaseColor = when (colorConfig.mode) {
        0 -> Color(colorConfig.red, colorConfig.green, colorConfig.blue) // Static / RGB
        1 -> Color(colorConfig.red, colorConfig.green, colorConfig.blue).copy(alpha = fadeAlpha) // Smooth Fade
        2 -> Color.hsv(rainbowHue, 0.85f, 1f) // Rainbow Wave
        3 -> Color(colorConfig.red, colorConfig.green, colorConfig.blue) // Custom RGB
        4 -> Color.hsv((rainbowHue + 180) % 360, 0.9f, 1f) // Sweep
        else -> NeonGold
    }

    val displayColor = if (dashboardData.isDisplayOn) activeBaseColor else Color(0xFF1E2430)
    val unlitColor = Color(0xFF131822) // Dim unlit segment background

    // Parse characters to show (4 chars: d0, d1, colon, d2, d3)
    val (digit0, digit1, digit2, digit3, colonVisible) = remember(viewMode, dashboardData) {
        when (viewMode) {
            MirrorViewMode.TIME -> {
                val cleanTime = dashboardData.currentTimeStr.filter { it.isDigit() || it == ':' }
                val parts = cleanTime.split(":")
                val h = parts.getOrNull(0)?.padStart(2, '0') ?: "12"
                val m = parts.getOrNull(1)?.padStart(2, '0') ?: "00"
                listOf(h[0], h[1], m[0], m[1], true)
            }
            MirrorViewMode.ENGLISH_DATE -> {
                // e.g. "2026-08-20" -> Day 20, Month 08 -> "20-08"
                val parts = dashboardData.currentDateStr.split("-")
                val day = parts.getOrNull(2) ?: "20"
                val month = parts.getOrNull(1) ?: "08"
                listOf(day[0], day.getOrElse(1) { '0' }, month[0], month.getOrElse(1) { '0' }, false)
            }
            MirrorViewMode.BANGLA_DATE -> {
                // e.g. "24/05"
                val rawBangla = dashboardData.banglaDate?.filter { it.isDigit() || it == '/' } ?: "2405"
                val digits = rawBangla.filter { it.isDigit() }.padEnd(4, '0')
                listOf(digits[0], digits[1], digits[2], digits[3], false)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("virtual_led_clock_mirror")
            .shadow(
                elevation = if (dashboardData.isDisplayOn) 12.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = displayColor.copy(alpha = 0.5f)
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        displayColor.copy(alpha = if (dashboardData.isDisplayOn) 0.6f else 0.15f),
                        CardBorder.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1017))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Bar: Live Badge, Status, Power Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (dashboardData.isDisplayOn) EmeraldGreen else NeutralMuted)
                    )
                    Text(
                        text = "LIVE ESP32 MIRROR",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (dashboardData.isDisplayOn) EmeraldGreen else NeutralMuted,
                        letterSpacing = 1.2.sp
                    )
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = "30x WS2812B",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Display Power Toggle Button
                IconButton(
                    onClick = onToggleDisplay,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (dashboardData.isDisplayOn) PrimaryGreen.copy(alpha = 0.2f) else SurfaceDark)
                        .testTag("mirror_toggle_display_btn")
                ) {
                    Icon(
                        imageVector = if (dashboardData.isDisplayOn) Icons.Default.PowerSettingsNew else Icons.Default.PowerOff,
                        contentDescription = "Toggle Display Power",
                        tint = if (dashboardData.isDisplayOn) EmeraldGreen else NeutralMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Display Chassis (Acrylic Black Bezel with Neon Segments)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (dashboardData.isDisplayOn) displayColor.copy(alpha = 0.12f) else Color.Transparent,
                                Color(0xFF07090D)
                            )
                        )
                    )
                    .border(1.5.dp, Color(0xFF1E2638), RoundedCornerShape(16.dp))
                    .padding(vertical = 20.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!dashboardData.isDisplayOn) {
                    // Display Off Overlay
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = NeutralMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "DISPLAY ASLEEP (OFF)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeutralMuted,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "ক্লকের ডিসপ্লে বর্তমানে বন্ধ রয়েছে। অন করতে পাওয়ার বাটনে ট্যাপ করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralMuted.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Active 7-Segment Digit Row (4 Digits + Colon)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Hour Tens (Digit 0)
                        SevenSegmentDigit(
                            char = digit0 as Char,
                            litColor = displayColor,
                            unlitColor = unlitColor,
                            width = 50.dp,
                            height = 92.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Hour Ones (Digit 1)
                        SevenSegmentDigit(
                            char = digit1 as Char,
                            litColor = displayColor,
                            unlitColor = unlitColor,
                            width = 50.dp,
                            height = 92.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        // Colon (2 Dots, LEDs 14 & 15)
                        SevenSegmentColon(
                            visible = (colonVisible as Boolean) && (colonOn > 0.4f),
                            dotColor = displayColor,
                            unlitColor = unlitColor,
                            height = 92.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        // Minute Tens (Digit 2)
                        SevenSegmentDigit(
                            char = digit2 as Char,
                            litColor = displayColor,
                            unlitColor = unlitColor,
                            width = 50.dp,
                            height = 92.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Minute Ones (Digit 3)
                        SevenSegmentDigit(
                            char = digit3 as Char,
                            litColor = displayColor,
                            unlitColor = unlitColor,
                            width = 50.dp,
                            height = 92.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-metrics Bar (Live Temp, LDR Brightness, Color Mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Temperature
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeviceThermostat,
                            contentDescription = null,
                            tint = AmberOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${dashboardData.temperatureC}°C",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Brightness / LDR
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = null,
                            tint = NeonGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${dashboardData.appliedBrightness}/255",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // View Mode Selectors (Time, Date, Bangla)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MirrorModeChip(
                        label = "সময়",
                        selected = viewMode == MirrorViewMode.TIME,
                        onClick = { viewMode = MirrorViewMode.TIME }
                    )
                    MirrorModeChip(
                        label = "তারিখ",
                        selected = viewMode == MirrorViewMode.ENGLISH_DATE,
                        onClick = { viewMode = MirrorViewMode.ENGLISH_DATE }
                    )
                    MirrorModeChip(
                        label = "বাংলা",
                        selected = viewMode == MirrorViewMode.BANGLA_DATE,
                        onClick = { viewMode = MirrorViewMode.BANGLA_DATE }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontal Divider
            HorizontalDivider(color = CardBorder.copy(alpha = 0.4f), thickness = 0.8.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // One-Tap Ready-Made Color Presets Quick Row
            Text(
                text = "রেডিমেড কালার প্রিসেট (One-Tap Presets):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorPreset.PRESETS.take(4).forEach { preset ->
                    PresetPill(
                        preset = preset,
                        onClick = { onApplyPreset(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MirrorModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) PrimaryGreen.copy(alpha = 0.25f) else SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) PrimaryGreen else CardBorder
        ),
        modifier = Modifier.height(28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) EmeraldGreen else NeutralMuted
            )
        }
    }
}

@Composable
private fun PresetPill(
    preset: ColorPreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillColor = Color(preset.previewHex)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131822),
        border = androidx.compose.foundation.BorderStroke(1.dp, pillColor.copy(alpha = 0.5f)),
        modifier = modifier
            .height(38.dp)
            .testTag("preset_pill_${preset.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(pillColor)
                    .shadow(4.dp, CircleShape, spotColor = pillColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = preset.nameBn,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

/**
 * Renders a single 7-segment LED digit via Compose Canvas
 * Segments:
 * - a: top horizontal
 * - b: top-right vertical
 * - c: bottom-right vertical
 * - d: bottom horizontal
 * - e: bottom-left vertical
 * - f: top-left vertical
 * - g: middle horizontal
 */
@Composable
fun SevenSegmentDigit(
    char: Char,
    litColor: Color,
    unlitColor: Color,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val pattern = SEGMENT_PATTERNS[char] ?: SEGMENT_PATTERNS[' ']!!

    Canvas(
        modifier = modifier
            .size(width = width, height = height)
    ) {
        val w = size.width
        val h = size.height
        val t = w * 0.16f // segment thickness
        val halfH = h / 2f
        val pad = t * 0.25f

        fun isLit(bit: Int) = (pattern and (1 shl bit)) != 0

        // Segment A: Top horizontal
        drawRoundRect(
            color = if (isLit(0)) litColor else unlitColor,
            topLeft = Offset(t + pad, pad),
            size = Size(w - (2 * t) - (2 * pad), t),
            cornerRadius = CornerRadius(t / 2, t / 2)
        )

        // Segment B: Top-right vertical
        drawRoundRect(
            color = if (isLit(1)) litColor else unlitColor,
            topLeft = Offset(w - t - pad, t + pad),
            size = Size(t, halfH - t - (2 * pad)),
            cornerRadius = CornerRadius(t / 2, t / 2)
        )

        // Segment C: Bottom-right vertical
        drawRoundRect(
            color = if (isLit(2)) litColor else unlitColor,
            topLeft = Offset(w - t - pad, halfH + pad),
            size = Size(t, halfH - t - (2 * pad)),
            cornerRadius = CornerRadius(t / 2, t / 2)
        )

        // Segment D: Bottom horizontal
        drawRoundRect(
            color = if (isLit(3)) litColor else unlitColor,
            topLeft = Offset(t + pad, h - t - pad),
            size = Size(w - (2 * t) - (2 * pad), t),
            cornerRadius = CornerRadius(t / 2, t / 2)
        )

        // Segment E: Bottom-left vertical
        drawRoundRect(
            color = if (isLit(4)) litColor else unlitColor,
            topLeft = Offset(pad, halfH + pad),
            size = Size(t, halfH - t - (2 * pad)),
            cornerRadius = CornerRadius(t / 2, t / 2)
        )

        // Segment F: Top-left vertical
        drawRoundRect(
            color = if (isLit(5)) litColor else unlitColor,
            topLeft = Offset(pad, t + pad),
            size = Size(t, halfH - t - (2 * pad)),
            cornerRadius = CornerRadius(t / 2, t / 2)
        )

        // Segment G: Middle horizontal
        drawRoundRect(
            color = if (isLit(6)) litColor else unlitColor,
            topLeft = Offset(t + pad, halfH - (t / 2)),
            size = Size(w - (2 * t) - (2 * pad), t),
            cornerRadius = CornerRadius(t / 2, t / 2)
        )
    }
}

/**
 * Renders the Colon (2 round NeoPixel dots in between hours and minutes)
 */
@Composable
fun SevenSegmentColon(
    visible: Boolean,
    dotColor: Color,
    unlitColor: Color,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val activeColor = if (visible) dotColor else unlitColor

    Column(
        modifier = modifier.height(height),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(activeColor)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
}
