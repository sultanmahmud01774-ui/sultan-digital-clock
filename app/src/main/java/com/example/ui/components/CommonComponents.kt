package com.example.ui.components

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClockDashboardData
import com.example.data.model.ConnectionStatus
import com.example.ui.theme.*
import com.example.ui.viewmodel.ActionFeedback

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    glowEffect: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (glowEffect) {
                    Modifier.shadow(8.dp, RoundedCornerShape(20.dp), spotColor = GoldGlow)
                } else Modifier
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(borderColor.copy(alpha = 0.8f), borderColor.copy(alpha = 0.2f))
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground.copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            content = content
        )
    }
}

@Composable
fun DigitalClockCard(
    data: ClockDashboardData,
    isConnected: Boolean,
    onPhoneSync: () -> Unit,
    onNtpSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = GoldGlow)
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(GoldPrimary.copy(alpha = 0.6f), CyanAccent.copy(alpha = 0.6f))
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C101A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row: Status badge & Temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isConnected) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) SuccessGreen else ErrorRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "LIVE ESP32" else "OFFLINE",
                        color = if (isConnected) SuccessGreen else ErrorRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Temperature Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Temperature",
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${data.temperatureC}°C",
                        color = CyanAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Digital Clock Numbers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF06080E))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.currentTimeStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Date, Day & Time Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (data.dayOfWeekStr.isNotBlank()) "${data.dayOfWeekStr}, ${data.currentDateStr}" else data.currentDateStr,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!data.banglaDate.isNullOrBlank()) {
                        Text(
                            text = "বাংলা: ${data.banglaDate}",
                            color = GoldPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(start = 21.dp, top = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (data.is12Hour) "12-HOUR" else "24-HOUR",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPhoneSync,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("sync_phone_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary.copy(alpha = 0.15f),
                        contentColor = GoldPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Phone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onNtpSync,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("sync_ntp_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent.copy(alpha = 0.15f),
                        contentColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync NTP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(
    status: ConnectionStatus,
    host: String,
    connType: String,
    wifiConnected: Boolean = true,
    apMode: Boolean = false,
    wifiSsid: String = "",
    onReconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        color = CardBackgroundElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val (color, statusTitle) = when (status) {
                    ConnectionStatus.CONNECTED -> SuccessGreen to "Connected"
                    ConnectionStatus.CONNECTING -> WarningOrange to "Connecting..."
                    ConnectionStatus.AUTH_REQUIRED -> WarningOrange to "Auth Required"
                    ConnectionStatus.DISCONNECTED -> ErrorRed to "Disconnected"
                    ConnectionStatus.ERROR -> ErrorRed to "Error"
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val modeLabel = if (status == ConnectionStatus.CONNECTED) {
                        if (apMode) {
                            "Connected via Clock's Hotspot (AP mode)"
                        } else if (wifiConnected) {
                            if (wifiSsid.isNotBlank()) "Connected via Home WiFi: $wifiSsid" else "Connected via Home WiFi"
                        } else {
                            "WiFi Disconnected"
                        }
                    } else {
                        statusTitle
                    }

                    val labelColor = if (status == ConnectionStatus.CONNECTED && !apMode && !wifiConnected) {
                        WarningOrange
                    } else {
                        color
                    }

                    Text(
                        text = modeLabel,
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Host / IP: $host",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = onReconnectClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("reconnect_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reconnect",
                    tint = GoldPrimary
                )
            }
        }
    }
}

@Composable
fun ActionFeedbackBanner(
    feedback: ActionFeedback,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = feedback.inProgress || feedback.message.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val (bg, contentColor, icon) = when {
            feedback.inProgress -> Triple(CardBackgroundElevated, GoldPrimary, Icons.Default.Sync)
            feedback.isSuccess == true -> Triple(Color(0xFF082613), SuccessGreen, Icons.Default.CheckCircle)
            else -> Triple(Color(0xFF2E0D10), ErrorRed, Icons.Default.Error)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, contentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            color = bg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = feedback.message,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    activeColor: Color = GoldPrimary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isActive) activeColor.copy(alpha = 0.7f) else CardBorder,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) activeColor.copy(alpha = 0.12f) else CardBackgroundElevated
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) activeColor else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) activeColor else TextMuted.copy(alpha = 0.4f))
                )
            }

            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = if (isActive) activeColor else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    accentColor: Color = GoldPrimary,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        action?.invoke()
    }
}
