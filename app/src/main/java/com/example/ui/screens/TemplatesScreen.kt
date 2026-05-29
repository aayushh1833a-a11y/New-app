package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class VideoTemplate(
    val id: String,
    val title: String,
    val description: String,
    val ratio: String,
    val shotsCount: Int,
    val primaryColor: Color,
    val isPro: Boolean = false
)

val VideoTemplatesStock = listOf(
    VideoTemplate("1", "Cyberpunk Tokyo Beat", "Heavy glitch cuts, neon grids, and dramatic speed boosts.", "9:16", 3, PrimaryViolet, false),
    VideoTemplate("2", "Acoustic Forest Sunset", "Gentle sliding fades, warm sepia filters, and retro subtitles.", "16:9", 2, CaptionYellow, false),
    VideoTemplate("3", "VHS Vaporwave Glitch", "Cyan-magenta RGB splits, scanline hums, and dreamy synthesizers.", "9:16", 3, AccentCyan, true),
    VideoTemplate("4", "Cinematic Glacier Peak", "Sleek panning views, cinematic grades, and lofi mood music.", "16:9", 3, WaveformBlue, false),
    VideoTemplate("5", "Instagram Square Minimal", "Slight zoom-ins, pristine negative spaces, and clean body text.", "1:1", 2, Color.White, true)
)

@Composable
fun TemplatesScreen(
    viewModel: VideoEditorViewModel,
    onTemplateApplied: (Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackCarbon)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 80.dp)
        ) {
            // Title Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Aesthetic Templates",
                    color = LightText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }

            Text(
                text = "Skip complex editing. Load fully mapped pro timelines instantly in one tap.",
                color = MutedText,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(VideoTemplatesStock) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelGray),
                        border = BorderStroke(1.dp, BorderGray),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable {
                                // Create corresponding dynamic project structure
                                viewModel.createAndLoadNewProject("Template: ${item.title}", item.ratio)
                                // Auto seed corresponding template clips inside view model
                                coroutineScope.launch {
                                    // Simulated latency delay
                                    delay(600)
                                    // Trigger auto generation storyboard matching the theme
                                    viewModel.triggerAiTextToVideo(item.title + " " + item.description)
                                    onTemplateApplied(viewModel.activeProject.value?.id ?: 0)
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(item.primaryColor.copy(alpha = 0.4f), PanelGray)
                                    )
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        item.ratio,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (item.isPro) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "PRO",
                                        tint = CaptionYellow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = item.title,
                                    color = LightText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.description,
                                    color = MutedText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap to load ${item.shotsCount} scenes...",
                                    color = AccentCyan,
                                    fontSize = 10.sp,
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
