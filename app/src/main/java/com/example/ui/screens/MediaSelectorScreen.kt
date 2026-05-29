package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.GalleryMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// High-Fidelity Preconfigured Stock Assets for the Virtual Gallery
val VirtualGalleryStock = listOf(
    GalleryMediaItem("1", "Neon Cyberpunk Tokyo", "video", 15000L, "neon_city"),
    GalleryMediaItem("2", "Glitch Beach Sunset", "video", 12000L, "glitch_beach"),
    GalleryMediaItem("3", "Cinematic Mountain Peak", "video", 20000L, "mountain_peak"),
    GalleryMediaItem("4", "Retro Grid Pulse", "video", 10000L, "retro_grid"),
    GalleryMediaItem("5", "Vaporwave Abstract Waves", "video", 8000L, "abstract_waves"),
    GalleryMediaItem("6", "Cyber Portrait Glare", "photo", 4000L, "cyber_portrait"),
    GalleryMediaItem("7", "Aesthetic Forest Fog", "photo", 4000L, "forest_photo")
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectorScreen(
    onDismiss: () -> Unit,
    onMediaImported: (List<GalleryMediaItem>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedItems by remember { mutableStateOf(emptyList<GalleryMediaItem>()) }
    var activeTab by remember { mutableStateOf("all") } // "all", "video", "photo"
    var isImportingLoading by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0f) }

    val filteredGallery = remember(activeTab) {
        when (activeTab) {
            "video" -> VirtualGalleryStock.filter { it.type == "video" }
            "photo" -> VirtualGalleryStock.filter { it.type == "photo" }
            else -> VirtualGalleryStock
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackCarbon)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(TimelineGrid, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = LightText)
                }

                Text(
                    text = "Select Media Assets",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )

                Button(
                    onClick = {
                        if (selectedItems.isNotEmpty()) {
                            coroutineScope.launch {
                                isImportingLoading = true
                                // Simulated high-definition ingestion loading
                                for (progress in 1..10) {
                                    delay(180)
                                    importProgress = progress / 10f
                                }
                                onMediaImported(selectedItems)
                                isImportingLoading = false
                                onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedItems.isNotEmpty()) PrimaryViolet else TimelineGrid,
                        contentColor = if (selectedItems.isNotEmpty()) Color.White else MutedText
                    ),
                    shape = RoundedCornerShape(20.dp),
                    enabled = selectedItems.isNotEmpty(),
                    modifier = Modifier.testTag("import_media_button")
                ) {
                    Text(
                        text = if (selectedItems.isEmpty()) "Import" else "Import (${selectedItems.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Quick Category Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                listOf(
                    "all" to "All Assets",
                    "video" to "Videos Only",
                    "photo" to "Photos Only"
                ).forEach { (key, label) ->
                    val isSelected = activeTab == key
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (isSelected) PrimaryViolet else TimelineGrid)
                            .border(
                                1.dp,
                                if (isSelected) PrimaryViolet else BorderGray,
                                RoundedCornerShape(30.dp)
                            )
                            .clickable { activeTab = key }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else MutedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Media Grid List
            if (filteredGallery.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No media files found.", color = MutedText)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredGallery) { item ->
                        val selectedIndex = selectedItems.indexOfFirst { it.id == item.id }
                        val isSelected = selectedIndex != -1

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PanelGray)
                                .border(
                                    2.dp,
                                    if (isSelected) PrimaryViolet else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedItems = if (isSelected) {
                                        selectedItems.filter { it.id != item.id }
                                    } else {
                                        selectedItems + item
                                    }
                                }
                        ) {
                            // High Fidelity Aesthetic Asset Icon Preview Background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = when (item.resourceName) {
                                                "neon_city" -> listOf(
                                                    Color(0xFF8B5CF6),
                                                    Color(0xFF0F0F12)
                                                )
                                                "glitch_beach" -> listOf(
                                                    Color(0xFFEC4899),
                                                    Color(0xFF1E1B4B)
                                                )
                                                "mountain_peak" -> listOf(
                                                    Color(0xFF3B82F6),
                                                    Color(0xFF0F172A)
                                                )
                                                "retro_grid" -> listOf(
                                                    Color(0xFF10B981),
                                                    Color(0xFF022C22)
                                                )
                                                "abstract_waves" -> listOf(
                                                    Color(0xFFFBBF24),
                                                    Color(0xFF451A03)
                                                )
                                                "cyber_portrait" -> listOf(
                                                    Color(0xFF84CC16),
                                                    Color(0xFF1A1A1A)
                                                )
                                                else -> listOf(
                                                    Color(0xFF6B7280),
                                                    Color(0xFF1F2937)
                                                )
                                            }
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = if (item.type == "video") Icons.Default.Movie else Icons.Default.Photo,
                                        contentDescription = null,
                                        tint = LightText.copy(alpha = 0.8f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = item.title,
                                        fontSize = 11.sp,
                                        color = LightText,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                }
                            }

                            // Selection tag on the top-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) PrimaryViolet else Color.Black.copy(alpha = 0.5f))
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Text(
                                        text = (selectedIndex + 1).toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Video length overlay if video
                            if (item.type == "video") {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${item.durationMs / 1000}s",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Importing Loading Overlay Screen
        AnimatedVisibility(
            visible = isImportingLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelGray),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryViolet,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Importing assets...",
                            color = LightText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Optimizing frames & transcribing audio tracks (${(importProgress * 100).toInt()}%)...",
                            color = MutedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { importProgress },
                            color = AccentCyan,
                            trackColor = TimelineGrid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}
