package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ClockProfileBackup
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Profile Backup & Restore Dialog
 * Allows saving, restoring, exporting, and importing complete configuration profiles
 * including Alarms, Hourly Chime, DFPlayer Tracks, Color & Brightness settings.
 */
@Composable
fun ProfileBackupDialog(
    savedProfiles: List<ClockProfileBackup>,
    onSaveCurrentProfile: (String) -> Unit,
    onRestoreProfile: (ClockProfileBackup) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var confirmingRestoreProfile by remember { mutableStateOf<ClockProfileBackup?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var copiedMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBackground,
            border = androidx.compose.foundation.BorderStroke(1.2.dp, CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("profile_backup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                .clip(CircleShape)
                                .background(AmberOrange.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = AmberOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Profile Backup & Restore",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "কনফিগারেশন ব্যাকআপ ও রিস্টোর",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NeutralMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Card: Create New Backup
                Surface(
                    onClick = {
                        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                        newProfileName = "সুলতান প্রোফাইল (${sdf.format(Date())})"
                        showCreateDialog = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "বর্তমান সেটিংস ব্যাকআপ করুন",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                            Text(
                                text = "অ্যালার্ম, চিম, আযান ও কালার সেটিংস সেভ হবে",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "সংরক্ষিত প্রোফাইল সমূহ (${savedProfiles.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(savedProfiles, key = { it.id }) { profile ->
                        ProfileItemCard(
                            profile = profile,
                            onRestore = { confirmingRestoreProfile = profile },
                            onDelete = { onDeleteProfile(profile.id) },
                            onCopyJson = {
                                val jsonStr = "{\"id\":\"${profile.id}\",\"name\":\"${profile.name}\"}"
                                clipboardManager.setText(AnnotatedString(jsonStr))
                                copiedMessage = "'${profile.name}' কপি করা হয়েছে"
                            }
                        )
                    }
                }

                copiedMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldGreen,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("বন্ধ করুন", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Create New Backup Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "নতুন প্রোফাইল তৈরি করুন",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "প্রোফাইলের একটি নাম দিন:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            onSaveCurrentProfile(newProfileName.trim())
                        }
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("ব্যাকআপ সংরক্ষণ", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("বাতিল", color = NeutralMuted)
                }
            },
            containerColor = CardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirm Restore Dialog
    confirmingRestoreProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { confirmingRestoreProfile = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.SettingsBackupRestore,
                    contentDescription = null,
                    tint = AmberOrange,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "প্রোফাইল রিস্টোর করতে চান?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "'${profile.name}' প্রোফাইলের সমস্ত অ্যালার্ম, চিম, ওয়াক্ত এবং কালার সেটিংস আপনার সুলতান ডিজিটাল ক্লকে পাঠানো হবে।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreProfile(profile)
                        confirmingRestoreProfile = null
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("হ্যাঁ, রিস্টোর করুন", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRestoreProfile = null }) {
                    Text("বাতিল", color = NeutralMuted)
                }
            },
            containerColor = CardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ProfileItemCard(
    profile: ClockProfileBackup,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onCopyJson: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "তৈরি: ${profile.createdAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                if (!profile.id.startsWith("default_")) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Snapshot Pills (Chime, Color, Alarms)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF1B2332),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (profile.hourlyChime.enabled) "চিম: সক্রিয় (${profile.hourlyChime.startHour}-${profile.hourlyChime.endHour}h)" else "চিম: বন্ধ",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (profile.hourlyChime.enabled) EmeraldGreen else NeutralMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = Color(0xFF1B2332),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ব্রাইটনেস: ${profile.brightness.manualBrightness}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Restore Action Button
            Button(
                onClick = onRestore,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen.copy(alpha = 0.2f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ক্লকে রিস্টোর করুন (Apply)",
                    color = EmeraldGreen,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
