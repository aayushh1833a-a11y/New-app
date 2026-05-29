package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userEmail: String = "aayushh1833a@gmail.com",
    isPremiumState: MutableState<Boolean>,
    removeWatermark: MutableState<Boolean>
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedLanguage by remember { mutableStateOf("English (US)") }
    var isLanguageMenuExpanded by remember { mutableStateOf(false) }
    var isCloudBackingUp by remember { mutableStateOf(false) }
    var lastBackupTime by remember { mutableStateOf("May 29, 2026, 08:10 UTC") }

    val languages = listOf("English (US)", "Español", "日本語", "Deutsch", "Français", "Português")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackCarbon)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .padding(bottom = 80.dp)
        ) {
            // Profile Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PrimaryViolet, AccentCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userEmail.take(1).uppercase(Locale.getDefault()),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Aayushh (Editor)",
                        color = LightText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userEmail,
                        color = MutedText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // SECTION 1: PRO SUBSCRIPTION PLAN
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelGray),
                border = BorderStroke(1.dp, if (isPremiumState.value) Color(0xFFFBBF24) else BorderGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "PRO Membership Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = LightText
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isPremiumState.value) Color(0xFFFBBF24) else TimelineGrid)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isPremiumState.value) "ACTIVE" else "LITE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPremiumState.value) Color.Black else MutedText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isPremiumState.value)
                            "Unlock complete access! You are enjoying 4K resolution exports, zero watermarks, and high speed hardware acceleration synthesis rendering buffers."
                        else
                            "Upgrade to CapCut Studio PRO to remove timeline watermarks, enable pristine 4K rendering exports, and unlock automated AI tools.",
                        fontSize = 11.sp,
                        color = MutedText,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            isPremiumState.value = !isPremiumState.value
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPremiumState.value) TimelineGrid else Color(0xFFFBBF24),
                            contentColor = if (isPremiumState.value) Color(0xFFFBBF24) else Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isPremiumState.value) "Revert to Free Lite Mode" else "Upgrade to CapCut Pro ($9.99/mo)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // SECTION 2: VIDEO SETTINGS
            Text(
                "Export Preferences",
                color = LightText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = PanelGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Switch: Remove Watermark
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Waves, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Remove Video Watermark", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LightText)
                                Text("Do not append logo in exported drafts", fontSize = 10.sp, color = MutedText)
                            }
                        }

                        Switch(
                            checked = removeWatermark.value,
                            onCheckedChange = {
                                if (!isPremiumState.value && it) {
                                    // Trigger buy sheet warning implicitly
                                    removeWatermark.value = false
                                } else {
                                    removeWatermark.value = it
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryViolet,
                                uncheckedThumbColor = MutedText,
                                uncheckedTrackColor = TimelineGrid
                            ),
                            enabled = isPremiumState.value // Standard Pro feature restriction
                        )
                    }

                    if (!isPremiumState.value) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "*Watermark removal requires active Premium PRO status",
                            color = Color(0xFFEC4899),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // SECTION 3: SYSTEM AND LOCALES
            Text(
                "System Preferences",
                color = LightText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = PanelGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Dropdown list language selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Language", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LightText)
                        }

                        Box {
                            Text(
                                text = selectedLanguage,
                                color = AccentCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { isLanguageMenuExpanded = true }
                                    .border(1.dp, BorderGray, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            DropdownMenu(
                                expanded = isLanguageMenuExpanded,
                                onDismissRequest = { isLanguageMenuExpanded = false },
                                modifier = Modifier.background(PanelGray)
                            ) {
                                languages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang, color = LightText) },
                                        onClick = {
                                            selectedLanguage = lang
                                            isLanguageMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = BorderGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 14.dp))

                    // Cloud backup coordination
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cloud project backups", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LightText)
                            Text("Last Sync: $lastBackupTime", fontSize = 10.sp, color = MutedText)
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCloudBackingUp = true
                                    delay(1600)
                                    lastBackupTime = "Just Now"
                                    isCloudBackingUp = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TimelineGrid),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            enabled = !isCloudBackingUp
                        ) {
                            if (isCloudBackingUp) {
                                CircularProgressIndicator(color = PrimaryViolet, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = LightText, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", fontSize = 11.sp, color = LightText)
                                }
                            }
                        }
                    }

                    Divider(color = BorderGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 14.dp))

                    // Offline status indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.OfflineBolt, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Offline Mode Active", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LightText)
                            Text("All active project sequences cache locally. Unlimited offline timeline editing.", fontSize = 10.sp, color = MutedText)
                        }
                    }
                }
            }
        }
    }
}
