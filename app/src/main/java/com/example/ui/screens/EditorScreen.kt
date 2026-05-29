package com.example.ui.screens

import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AudioTrackEntity
import com.example.data.database.ClipEntity
import com.example.data.database.TextOverlayEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.GalleryMediaItem
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: VideoEditorViewModel,
    onBackClicked: () -> Unit,
    onAddMediaClicked: () -> Unit,
    removeWatermark: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val activeProject by viewModel.activeProject.collectAsState()
    val clips by viewModel.clips.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val textOverlays by viewModel.textOverlays.collectAsState()

    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val selectedAudioTrackId by viewModel.selectedAudioTrackId.collectAsState()
    val selectedTextId by viewModel.selectedTextId.collectAsState()

    val playheadPositionMs by viewModel.playheadPositionMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val timelineZoom by viewModel.timelineZoom.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()

    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()

    // Sub-menus control variables
    var activePanelTab by remember { mutableStateOf("tools") } // "tools", "filters", "audio", "text", "stickers"
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedExportRes by remember { mutableStateOf("1080p") }
    var selectedExportFps by remember { mutableStateOf(30) }

    // Floating dialog text add builders
    var showTextDialog by remember { mutableStateOf(false) }
    var rawTextValue by remember { mutableStateOf("") }
    var rawTextColor by remember { mutableStateOf("#FFFFFF") }
    var rawTextFont by remember { mutableStateOf("Space Grotesk") }

    // Soundboard voiceover states
    var showVoiceModal by remember { mutableStateOf(false) }
    var isVoiceRecordingActive by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableStateOf(0) }

    val sortedClips = remember(clips) { clips.sortedBy { it.sequenceIndex } }
    val totalDurationMs = remember(clips) { viewModel.calculateTotalDurationMs() }

    // Render active properties
    val activeClipData = remember(playheadPositionMs, sortedClips) {
        findActiveClipAtPlayhead(playheadPositionMs, sortedClips)
    }

    val systemImageBitmap by produceState<ImageBitmap?>(initialValue = null, activeClipData) {
        value = null
        val resourceStr = activeClipData?.first?.resourceName
        if (resourceStr != null && (resourceStr.startsWith("content://") || resourceStr.startsWith("file://"))) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val uri = android.net.Uri.parse(resourceStr)
                    if (activeClipData.first.type == "video") {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        val timeUs = activeClipData.second * 1000L
                        val bmp = retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        retriever.release()
                        value = bmp?.asImageBitmap()
                    } else {
                        context.contentResolver.openInputStream(uri).use { stream ->
                            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                            value = bmp?.asImageBitmap()
                        }
                    }
                } catch (e: Exception) {
                    value = null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackCarbon)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOOLBAR TOP HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelGray)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.Default.ArrowBackIos, contentDescription = "Back", tint = LightText)
                }

                Text(
                    text = activeProject?.name ?: "Timeline Editor",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                // Undo / Redo controllers mapping
                IconButton(onClick = { viewModel.undo() }) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (viewModel.canUndo) LightText else MutedText)
                }

                IconButton(onClick = { viewModel.redo() }) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (viewModel.canRedo) LightText else MutedText)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = { showExportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp).testTag("export_active_project_button")
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }

            // SECTION 1: VISUAL PREVIEW PLAYER CANVAS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { viewModel.togglePlayback() }
                            )
                        }
                ) {
                    drawVideoPreviewCanvasFrame(
                        playheadMs = playheadPositionMs,
                        activeClipData = activeClipData,
                        textOverlays = textOverlays,
                        removeWatermark = removeWatermark,
                        systemImageBitmap = systemImageBitmap
                    )
                }

                // Aspect ratio bounding mask (vertical centering)
                val ratio = activeProject?.aspectRatio ?: "9:16"
                if (ratio == "9:16") {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(9f / 16f)
                            .border(1.dp, BorderGray.copy(alpha = 0.5f))
                    )
                } else if (ratio == "16:9") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .border(1.dp, BorderGray.copy(alpha = 0.5f))
                    )
                }

                // Overlapping Active Audio Sound indicators
                val speakingVoiceovers = audioTracks.filter {
                    it.type == "voiceover" && isAudioTrackActiveAtTime(playheadPositionMs, it)
                }
                if (speakingVoiceovers.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = PrimaryViolet, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Voiceover active...", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Play/Pause on-top indicator
                IconButton(
                    onClick = { viewModel.togglePlayback() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(54.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play Control",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Current time code overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${formatTimeCode(playheadPositionMs)} / ${formatTimeCode(totalDurationMs)}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SECTION 2: ZOOM TIMELINE WORKSPACE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .background(BackCarbon)
            ) {
                // Ruler ticks, zooms & indicators bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TimelineGrid)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.zoomTimeline(-0.25f) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = MutedText, modifier = Modifier.size(16.dp))
                        }
                        Text("Timeline Zoom", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { viewModel.zoomTimeline(0.25f) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = MutedText, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Aspect ratio quick settings
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = MutedText, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        listOf("9:16", "16:9", "1:1").forEach { r ->
                            val isSelected = activeProject?.aspectRatio == r
                            Text(
                                r,
                                fontSize = 11.sp,
                                color = if (isSelected) AccentCyan else MutedText,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { viewModel.saveActiveProjectSettings(r) }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                }

                // THE MULTI-TRACK INTERACTIVE SCROLL TIMELINE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val scrollState = rememberScrollState()

                    // Horizontal scrolling timeline track layer list
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(scrollState)
                    ) {
                        // Vertical column holding separate layers
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                        ) {
                            // Track 1 Ruler markers canvas drawing
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .width(calculateTimelineWidth(totalDurationMs, timelineZoom).toInt().dp)
                            ) {
                                drawTimelineRulerMarkers(totalDurationMs, timelineZoom)
                            }

                            // TRACK A: CLIPS LAYER
                            Row(
                                modifier = Modifier
                                    .height(58.dp)
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (sortedClips.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .width(200.dp)
                                            .fillMaxHeight()
                                            .background(PanelGray, RoundedCornerShape(8.dp))
                                            .border(1.dp, BorderGray)
                                            .clickable { onAddMediaClicked() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+ Click to Import Video", color = PrimaryViolet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    sortedClips.forEach { clip ->
                                        val clipWidth = calculateClipTimelineWidth(clip, timelineZoom)
                                        val isSelected = selectedClipId == clip.id

                                        Box(
                                            modifier = Modifier
                                                .width(clipWidth.toInt().dp)
                                                .fillMaxHeight()
                                                .padding(horizontal = 1.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = getClipColorGrads(clip.resourceName)
                                                    )
                                                )
                                                .border(
                                                    2.dp,
                                                    if (isSelected) PrimaryViolet else Color.Transparent,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { viewModel.selectClip(clip.id) }
                                                .padding(6.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                                                Text(
                                                    text = clip.title,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Bottom
                                                ) {
                                                    Text(
                                                        "${String.format("%.1f", clip.durationMs / 1000f)}s",
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )

                                                    if (clip.speed != 1f) {
                                                        Text(
                                                            "${clip.speed}x",
                                                            color = AccentCyan,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Add media block at end of clip track
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(TimelineGrid)
                                            .clickable { onAddMediaClicked() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Media", tint = LightText)
                                    }
                                }
                            }

                            // TRACK B: AUDIO LAYER
                            Row(
                                modifier = Modifier
                                    .height(38.dp)
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (audioTracks.isEmpty()) {
                                    Spacer(modifier = Modifier.width(100.dp))
                                } else {
                                    audioTracks.forEach { track ->
                                        val startWidth = calculateTimeMsToDp(track.startOffsetMs, timelineZoom)
                                        val trackWidth = calculateTimeMsToDp(track.durationMs, timelineZoom)
                                        val isSelected = selectedAudioTrackId == track.id

                                        Spacer(modifier = Modifier.width(startWidth.toInt().dp))

                                        Box(
                                            modifier = Modifier
                                                .width(trackWidth.toInt().dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (track.type == "music") WaveformBlue else AccentCyan)
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) Color.White else Color.Transparent,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable { viewModel.selectAudioTrack(track.id) }
                                                .padding(horizontal = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (track.type == "music") Icons.Default.MusicNote else Icons.Default.Feedback,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = track.title,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // TRACK C: TEXT OVERLAY SUBTITLES
                            Row(
                                modifier = Modifier
                                    .height(34.dp)
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val standardTexts = textOverlays.filter { !it.fontName.startsWith("Sticker") }
                                if (standardTexts.isEmpty()) {
                                    Spacer(modifier = Modifier.width(100.dp))
                                } else {
                                    standardTexts.forEach { text ->
                                        val startWidth = calculateTimeMsToDp(text.startOffsetMs, timelineZoom)
                                        val textWidth = calculateTimeMsToDp(text.durationMs, timelineZoom)
                                        val isSelected = selectedTextId == text.id

                                        Spacer(modifier = Modifier.width(startWidth.toInt().dp))

                                        Box(
                                            modifier = Modifier
                                                .width(textWidth.toInt().dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFEA580C))
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) Color.White else Color.Transparent,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable { viewModel.selectTextOverlay(text.id) }
                                                .padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = text.text,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // TRACK D: STICKER OVERLAYS
                            Row(
                                modifier = Modifier
                                    .height(34.dp)
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val stickerOverlays = textOverlays.filter { it.fontName.startsWith("Sticker") }
                                if (stickerOverlays.isEmpty()) {
                                    Spacer(modifier = Modifier.width(100.dp))
                                } else {
                                    stickerOverlays.forEach { text ->
                                        val startWidth = calculateTimeMsToDp(text.startOffsetMs, timelineZoom)
                                        val textWidth = calculateTimeMsToDp(text.durationMs, timelineZoom)
                                        val isSelected = selectedTextId == text.id

                                        Spacer(modifier = Modifier.width(startWidth.toInt().dp))

                                        Box(
                                            modifier = Modifier
                                                .width(textWidth.toInt().dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF8B5CF6))
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) Color.White else Color.Transparent,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable { viewModel.selectTextOverlay(text.id) }
                                                .padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Celebration,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = text.text,
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Solid CENTRAL PLAYHEAD LINE (Static in center of screen viewport, scrubbing slides timeline!)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(Color.Red)
                    )

                    // Coordinate scrolling state index with playback Head (Seek/Scrub syncing)
                    val timelineWidthPx = calculateTimelineWidth(totalDurationMs, timelineZoom)
                    LaunchedEffect(playheadPositionMs) {
                        if (totalDurationMs > 0L) {
                            val ratioPercent = playheadPositionMs.toFloat() / totalDurationMs
                            val targetScroll = (ratioPercent * timelineWidthPx * 1.5).toInt()
                            scrollState.scrollTo(targetScroll)
                        }
                    }
                    
                    // Allow dragging viewport to scrub seek
                    LaunchedEffect(scrollState.value) {
                        if (!isPlaying && totalDurationMs > 0L) {
                            val ratioPercent = scrollState.value.toFloat() / (scrollState.maxValue.coerceAtLeast(1))
                            val nextSeek = (ratioPercent * totalDurationMs).toLong()
                            viewModel.seekTo(nextSeek)
                        }
                    }
                }
            }

            // SECTION 3: EDITING SHORTCUT PANELS AND SHORTCUTS TOOLBAR
            Divider(color = BorderGray, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(134.dp)
                    .background(PanelGray)
            ) {
                // Secondary control selectors options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TimelineGrid)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        "tools" to "Timeline Ops",
                        "filters" to "Filters & Grades",
                        "audio" to "Beat & Sound",
                        "text" to "Text Subtitles",
                        "stickers" to "Stickers & Emojis"
                    ).forEach { (tab, text) ->
                        val isSelected = activePanelTab == tab
                        Text(
                            text,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            color = if (isSelected) PrimaryViolet else MutedText,
                            modifier = Modifier
                                .clickable { activePanelTab = tab }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Dynamic display based on tab
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp)
                ) {
                    when (activePanelTab) {
                        "tools" -> {
                            // SPLIT, TRIM, FLIP, DUP, DELETE
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Split
                                QuickToolShortcut(
                                    icon = Icons.Default.ContentCut,
                                    label = "Split",
                                    onClick = {
                                        viewModel.splitActiveClip()
                                        Toast.makeText(context, "Spliced Clip at current frame", Toast.LENGTH_SHORT).show()
                                    },
                                    color = PrimaryViolet,
                                    testTag = "split_clip_button"
                                )

                                // 2. Duplicate
                                QuickToolShortcut(
                                    icon = Icons.Default.ContentCopy,
                                    label = "Clone",
                                    onClick = { viewModel.duplicateSelectedClip() }
                                )

                                // 3. Speed selection controller
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Speed multiplier", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Row {
                                        listOf(0.5f, 1f, 2f, 5f).forEach { s ->
                                            Box(
                                                modifier = Modifier
                                                    .padding(2.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(TimelineGrid)
                                                    .clickable { viewModel.speedSelectedClip(s) }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text("${s}x", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // 4. Rotate
                                QuickToolShortcut(
                                    icon = Icons.Default.RotateRight,
                                    label = "Rotate",
                                    onClick = { viewModel.rotateSelectedClip() }
                                )

                                // 5. Flip
                                QuickToolShortcut(
                                    icon = Icons.Default.Flip,
                                    label = "Flip",
                                    onClick = { viewModel.flipSelectedClip() }
                                )

                                // 6. Delete active
                                QuickToolShortcut(
                                    icon = Icons.Default.Delete,
                                    label = "Delete",
                                    onClick = { viewModel.deleteSelectedClip() },
                                    color = Color(0xFFEF4444),
                                    testTag = "delete_clip_button"
                                )
                            }
                        }

                        "filters" -> {
                            // Presets list color matrices
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "none" to "Standard",
                                    "cyberpunk" to "Neon Cyber",
                                    "glitch" to "Anharmonic",
                                    "warm" to "Warm grading",
                                    "cinematic" to "Cinematic Studio",
                                    "vhs" to "VHS Glair",
                                    "black_white" to "Monochrome"
                                ).forEach { (id, label) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TimelineGrid)
                                            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                                            .clickable { viewModel.applyFilterToSelectedClip(id) }
                                            .padding(10.dp)
                                    ) {
                                        Text(label, color = LightText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        "audio" -> {
                            // Audio sound board, volume modifiers, fade toggles & speech recorders
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Add cinematic background track
                                QuickToolShortcut(
                                    icon = Icons.Default.LibraryMusic,
                                    label = "Cinema Beat",
                                    onClick = { viewModel.addAudioTrack("Cinematic Synth Lofi", "music", playheadPositionMs, 8000L) },
                                    color = WaveformBlue
                                )

                                // 2. Add whoosh effect
                                QuickToolShortcut(
                                    icon = Icons.Default.VolumeUp,
                                    label = "Whoosh FX",
                                    onClick = { viewModel.addAudioTrack("Whoosh Transition", "sound_effect", playheadPositionMs, 1200L) }
                                )

                                // 3. Vocal Narrator Recorder
                                QuickToolShortcut(
                                    icon = Icons.Default.Mic,
                                    label = "Record Mic",
                                    onClick = { showVoiceModal = true },
                                    color = Color(0xFF10B981)
                                )

                                // Volume adjuster panel
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                                    Text("Audio volume", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.updateSelectedAudioVolume(0.5f) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.VolumeDown, contentDescription = null, tint = LightText, modifier = Modifier.size(12.dp))
                                        }
                                        Text("Adjust", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { viewModel.updateSelectedAudioVolume(1.8f) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = LightText, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }

                        "text" -> {
                            // Subtitle generator cards, fonts parameters
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Add Text
                                Button(
                                    onClick = { showTextDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = TimelineGrid),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("add_custom_text_button")
                                ) {
                                    Icon(Icons.Default.TextFields, contentDescription = null, tint = LightText, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Text Overlay", color = LightText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // 2. Auto Captions Speech transcribing
                                Button(
                                    onClick = { viewModel.triggerAiCaptions() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("ai_captions_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Auto AI Captions", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // 3. Delete subtitle
                                QuickToolShortcut(
                                    icon = Icons.Default.TextFormat,
                                    label = "Delete subtitle",
                                    onClick = { viewModel.deleteSelectedText() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // DIALOG A: ADD TEXT OVERLAY SETTINGS
        if (showTextDialog) {
            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                containerColor = PanelGray,
                title = { Text("Design Text Subtitle", color = LightText, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = rawTextValue,
                            onValueChange = { rawTextValue = it },
                            placeholder = { Text("Type subtitle content here...", color = MutedText) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryViolet,
                                unfocusedBorderColor = BorderGray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("text_input_field")
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Text color theme preset", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("#FFFFFF", "#FFFF00", "#FF00FF", "#00FFFF", "#00FF00").forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(
                                            2.dp,
                                            if (rawTextColor == hex) Color.White else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { rawTextColor = hex }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Font typeface", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Space Grotesk", "Modern Sans", "Classic Bold", "Retro Cursive").forEach { font ->
                                val isSelected = rawTextFont == font
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) PrimaryViolet else TimelineGrid)
                                        .clickable { rawTextFont = font }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(font, color = if (isSelected) Color.White else MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (rawTextValue.isNotEmpty()) {
                                viewModel.addTextOverlay(rawTextValue, playheadPositionMs, 4000L)
                                // Apply customized styles straight of parameters
                                viewModel.updateSelectedTextParams(
                                    text = rawTextValue,
                                    colorHex = rawTextColor,
                                    fontName = rawTextFont
                                )
                                rawTextValue = ""
                                showTextDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet),
                        modifier = Modifier.testTag("submit_text_button")
                    ) {
                        Text("Add to timeline")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = false }) {
                        Text("Close", color = MutedText)
                    }
                }
            )
        }

        // DIALOG B: Mic Narrator Voice recorder
        if (showVoiceModal) {
            AlertDialog(
                onDismissRequest = { showVoiceModal = false },
                containerColor = PanelGray,
                title = { Text("Mic Audio Capture", color = LightText, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Record high-fidelity narration overlays", color = MutedText, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))

                        // Pulsing Waveform visualizer simulation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(12) { i ->
                                    val pulse = if (isVoiceRecordingActive) {
                                        sin(i + System.currentTimeMillis() * 0.1).toFloat().coerceIn(0.1f, 1f)
                                    } else {
                                        0.15f
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .fillMaxHeight(pulse)
                                            .clip(CircleShape)
                                            .background(if (isVoiceRecordingActive) PrimaryViolet else TimelineGrid)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = String.format("00:%02d", voiceRecordSeconds),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isVoiceRecordingActive) Color.Red else LightText
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isVoiceRecordingActive) {
                                isVoiceRecordingActive = false
                                showVoiceModal = false
                                viewModel.addAudioTrack(
                                    title = "Mic Narration Overlay #${System.currentTimeMillis() / 1000 % 1000}",
                                    type = "voiceover",
                                    startOffsetMs = playheadPositionMs,
                                    durationMs = voiceRecordSeconds * 1000L
                                )
                                voiceRecordSeconds = 0
                            } else {
                                isVoiceRecordingActive = true
                                coroutineScope.launch {
                                    while (isVoiceRecordingActive) {
                                        delay(1000)
                                        voiceRecordSeconds++
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVoiceRecordingActive) Color.Red else Color(0xFF10B981)
                        )
                    ) {
                        Text(if (isVoiceRecordingActive) "Stop & Add" else "Record Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        isVoiceRecordingActive = false
                        showVoiceModal = false
                        voiceRecordSeconds = 0
                    }) {
                        Text("Cancel", color = MutedText)
                    }
                }
            )
        }

        // DIALOG C: PRO EXPORT OPTIONS INTERFACE
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                containerColor = PanelGray,
                title = { Text("Export Project Configuration", color = LightText, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Output resolution", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            listOf("720p", "1080p", "4K (Pro)").forEach { res ->
                                val isSelected = selectedExportRes == res
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) PrimaryViolet else TimelineGrid)
                                        .clickable { selectedExportRes = res }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(res, color = if (isSelected) Color.White else MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Target frame rates (FPS)", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            listOf(24, 30, 60).forEach { fps ->
                                val isSelected = selectedExportFps == fps
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentCyan else TimelineGrid)
                                        .clickable { selectedExportFps = fps }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${fps} FPS", color = if (isSelected) Color.White else MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = removeWatermark,
                                onCheckedChange = null,
                                enabled = false,
                                colors = CheckboxDefaults.colors(disabledCheckedColor = PrimaryViolet)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (removeWatermark) "Watermark Removal Enabled" else "Watermark Included (Buy PRO in Profile to remove)",
                                color = if (removeWatermark) Color(0xFF10B981) else MutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExportDialog = false
                            viewModel.exportVideo(selectedExportRes, selectedExportFps, removeWatermark)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet),
                        modifier = Modifier.testTag("submit_export_button")
                    ) {
                        Text("Begin High Speed Render")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Cancel", color = MutedText)
                    }
                }
            )
        }

        // EXPORT RENDER RAMP LOADER OVERLAY
        AnimatedVisibility(
            visible = isExporting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelGray),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(300.dp)
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
                            text = "Rendering Video...",
                            color = LightText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Compiling transition frames, subtitle fonts, and syncing audio buffers (${(exportProgress * 100).toInt()}%)...",
                            color = MutedText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            color = AccentCyan,
                            trackColor = TimelineGrid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        if (exportProgress >= 1.0f) {
                            Text(
                                "Video saved to Local Gallery!",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live API Action state notifier
        AnimatedVisibility(
            visible = aiStatus != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelGray),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderGray)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = PrimaryViolet, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = aiStatus ?: "Configuring AI Tracks...",
                            color = LightText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickToolShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = LightText,
    testTag: String = ""
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .run { if (testTag.isNotEmpty()) testTag(testTag) else this }
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// --- CORE UTILITIES AND INTERACTIVE DRAWINGS COMPILER ---

fun formatTimeCode(timeMs: Long): String {
    val sec = (timeMs / 1000) % 60
    val min = (timeMs / 60000) % 60
    val ms = (timeMs % 1000) / 100
    return String.format("%02d:%02d.%d", min, sec, ms)
}

fun calculateTimelineWidth(totalDurationMs: Long, zoom: Float): Long {
    return (totalDurationMs * 0.15f * zoom).toLong().coerceAtLeast(1000L)
}

fun calculateClipTimelineWidth(clip: ClipEntity, zoom: Float): Long {
    val netDuration = (clip.durationMs - clip.startTrimMs - clip.endTrimMs) / clip.speed
    return (netDuration * 0.15f * zoom).toLong().coerceAtLeast(60L)
}

fun calculateTimeMsToDp(ms: Long, zoom: Float): Long {
    return (ms * 0.15f * zoom).toLong()
}

fun findActiveClipAtPlayhead(playheadMs: Long, clips: List<ClipEntity>): Pair<ClipEntity, Long>? {
    var accum = 0L
    clips.forEach { clip ->
        val net = ((clip.durationMs - clip.startTrimMs - clip.endTrimMs) / clip.speed).toLong()
        if (playheadMs >= accum && playheadMs < accum + net) {
            val relativeTimeMs = ((playheadMs - accum) * clip.speed).toLong() + clip.startTrimMs
            return Pair(clip, relativeTimeMs)
        }
        accum += net
    }
    // Return last clip as boundary hold
    if (clips.isNotEmpty() && playheadMs >= accum) {
        val last = clips.last()
        return Pair(last, last.durationMs)
    }
    return null
}

fun isAudioTrackActiveAtTime(playheadMs: Long, track: AudioTrackEntity): Boolean {
    return playheadMs >= track.startOffsetMs && playheadMs < track.startOffsetMs + track.durationMs
}

fun getClipColorGrads(resName: String): List<Color> {
    return when (resName) {
        "neon_city" -> listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
        "glitch_beach" -> listOf(Color(0xFFEC4899), Color(0xFFD946EF))
        "mountain_peak" -> listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))
        "retro_grid" -> listOf(Color(0xFF10B981), Color(0xFF059669))
        "abstract_waves" -> listOf(Color(0xFFFBBF24), Color(0xFFF59E0B))
        "cyber_portrait" -> listOf(Color(0xFFEF4444), Color(0xFFF43F5E))
        else -> listOf(Color(0xFF4B5563), Color(0xFF374151))
    }
}

// Draw timeline ruler ticks
fun DrawScope.drawTimelineRulerMarkers(totalMs: Long, zoom: Float) {
    val intervalMs = 2000L // 2 seconds tick
    val totalTicks = (totalMs / intervalMs).toInt() + 1
    val dpPerMs = 0.15f * zoom

    for (i in 0..totalTicks) {
        val msPosition = i * intervalMs
        val xPx = msPosition * dpPerMs * density
        
        // Major ticks
        drawLine(
            color = MutedText.copy(alpha = 0.6f),
            start = Offset(xPx, 0f),
            end = Offset(xPx, 12.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

// 100% WORKING INTEGRATED GRAPHICS CANVAS PREVIEW FRAME ASSEMBLY
fun DrawScope.drawVideoPreviewCanvasFrame(
    playheadMs: Long,
    activeClipData: Pair<ClipEntity, Long>?,
    textOverlays: List<TextOverlayEntity>,
    removeWatermark: Boolean,
    systemImageBitmap: ImageBitmap? = null
) {
    if (activeClipData == null) {
        // Draw empty screen card
        drawRect(color = Color(0xFF18181A))
        return
    }

    val clip = activeClipData.first
    val relMs = activeClipData.second

    // Apply color matrices grade values straight on Paint shader configurations
    val filterType = clip.filterType

    // Translate coordinates
    rotate(degrees = clip.rotationDegrees.toFloat(), pivot = center) {
        scale(
            scaleX = if (clip.isFlipped) -1f else 1f,
            scaleY = 1f,
            pivot = center
        ) {
            // HIGH FIDELITY COMPOSE CORE ANIMATED VECTOR GRAPHICS SCENE SECTIONS
            if (systemImageBitmap != null) {
                // Scale and center the device-loaded thumbnail/image on the dynamic scene background
                val canvasWidth = size.width
                val canvasHeight = size.height
                val imageWidth = systemImageBitmap.width.toFloat()
                val imageHeight = systemImageBitmap.height.toFloat()
                
                val scale = maxOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
                val dx = (canvasWidth - imageWidth * scale) / 2f
                val dy = (canvasHeight - imageHeight * scale) / 2f
                
                withTransform({
                    translate(left = dx, top = dy)
                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                }) {
                    drawImage(image = systemImageBitmap)
                }
            } else {
                when (clip.resourceName) {
                    "neon_city" -> {
                    // Tokyo skyscrapers moving
                    drawRect(color = getGracedColor(Color(0xFF03001e), filterType))

                    val scrollSpeedFactor = relMs * 0.15f
                    // Draw neon road horizontal stripe lines
                    drawLine(
                        color = getGracedColor(Color(0xFF2E0854), filterType),
                        start = Offset(0f, size.height * 0.7f),
                        end = Offset(size.width, size.height * 0.7f),
                        strokeWidth = 8.dp.toPx()
                    )

                    // Draw layered skyscrapers vector polygons
                    for (i in 0..6) {
                        val widthPx = 80.dp.toPx()
                        val heightPx = (140 + sin(i.toDouble() * 1.5) * 50).dp.toPx()
                        val rawXPx = (i * 100.dp.toPx() - scrollSpeedFactor) % (size.width + widthPx)
                        val xPx = if (rawXPx < -widthPx) rawXPx + size.width + widthPx else rawXPx

                        drawRect(
                            color = getGracedColor(Color(0xFF4C0519), filterType),
                            topLeft = Offset(xPx, size.height * 0.7f - heightPx),
                            size = Size(widthPx - 10.dp.toPx(), heightPx)
                        )

                        // Light trail windows
                        drawCircle(
                            color = getGracedColor(Color(0xFFFBBF24), filterType),
                            radius = 3.dp.toPx(),
                            center = Offset(xPx + widthPx * 0.3f, size.height * 0.7f - heightPx * 0.8f)
                        )
                        drawCircle(
                            color = getGracedColor(Color(0xFF38BDF8), filterType),
                            radius = 3.dp.toPx(),
                            center = Offset(xPx + widthPx * 0.6f, size.height * 0.7f - heightPx * 0.5f)
                        )
                    }

                    // Floating Cyber HUD concentric vectors
                    drawCircle(
                        color = getGracedColor(Color(0xFFEC4899).copy(alpha = 0.4f), filterType),
                        radius = 48.dp.toPx(),
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }

                "glitch_beach" -> {
                    // Retrowave moving sun and sinusoidal oceans
                    drawRect(color = getGracedColor(Color(0xFF2D1B4E), filterType))

                    // Pulsing Retrowave Sun vector
                    val scaleSun = (1.0f + sin(relMs * 0.005) * 0.08).toFloat()
                    drawCircle(
                        color = getGracedColor(Color(0xFFF43F5E), filterType),
                        radius = 64.dp.toPx() * scaleSun,
                        center = Offset(center.x, center.y - 30.dp.toPx())
                    )

                    // Ocean Sine Waves shifts
                    for (w in 0..2) {
                        val path = Path()
                        path.moveTo(0f, size.height)
                        path.lineTo(0f, size.height * 0.65f + w * 24.dp.toPx())
                        
                        // Sinus calculations
                        for (x in 0..size.width.toInt() step 10) {
                            val y = size.height * 0.65f + w * 24.dp.toPx() + sin(x * 0.01 + relMs * 0.004 + w) * 12.dp.toPx()
                            path.lineTo(x.toFloat(), y.toFloat())
                        }
                        path.lineTo(size.width, size.height)
                        path.close()

                        drawPath(
                            path = path,
                            color = getGracedColor(Color(0xFF1E1B4B).copy(alpha = 0.8f - w * 0.2f), filterType)
                        )
                    }
                }

                "mountain_peak" -> {
                    // Sharp mountains with floating clouds
                    drawRect(color = getGracedColor(Color(0xFF0F172A), filterType))

                    // Sun glow
                    drawCircle(
                        color = getGracedColor(Color(0xFFFEF08A), filterType),
                        radius = 80.dp.toPx(),
                        center = Offset(size.width * 0.8f, size.height * 0.4f)
                    )

                    // Draw mountains peaks
                    val m1 = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(size.width * 0.4f, size.height * 0.35f)
                        lineTo(size.width * 0.8f, size.height)
                        close()
                    }
                    drawPath(m1, color = getGracedColor(Color(0xFF1E293B), filterType))

                    val m2 = Path().apply {
                        moveTo(size.width * 0.3f, size.height)
                        lineTo(size.width * 0.7f, size.height * 0.45f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(m2, color = getGracedColor(Color(0xFF334155), filterType))

                    // Clouds layers panning
                    val cloudXPx = (relMs * 0.04f) % (size.width + 120.dp.toPx()) - 60.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = 30.dp.toPx(),
                        center = Offset(cloudXPx, size.height * 0.3f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = 40.dp.toPx(),
                        center = Offset(cloudXPx + 40.dp.toPx(), size.height * 0.3f)
                    )
                }

                "retro_grid" -> {
                    // Retro sci-fi vertical grids converging
                    drawRect(color = getGracedColor(Color(0xFF090514), filterType))

                    val speedDelta = (relMs * 0.02f) % 60.dp.toPx()
                    
                    // Draw horizontal lines expanding
                    for (h in 0..10) {
                        val y = size.height * 0.5f + (h * h * 6.dp.toPx() + speedDelta)
                        if (y < size.height) {
                            drawLine(
                                color = getGracedColor(Color(0xFF10B981).copy(alpha = 0.7f), filterType),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = (1 + h * 0.5f).dp.toPx()
                            )
                        }
                    }

                    // Converging vertical lines
                    val horizonY = size.height * 0.5f
                    for (v in -5..5) {
                        val rawX = center.x + v * 30.dp.toPx()
                        drawLine(
                            color = getGracedColor(Color(0xFF059669), filterType),
                            start = Offset(center.x, horizonY),
                            end = Offset(rawX * 2 - center.x, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                "abstract_waves" -> {
                    // Particle orbital arrays
                    drawRect(color = getGracedColor(Color(0xFF1A0B2E), filterType))

                    val orbitCenter = center
                    for (p in 0..12) {
                        val angle = p * (3.1415 * 2 / 12) + relMs * 0.0015
                        val distance = 60.dp.toPx() + sin(relMs * 0.003 + p) * 15.dp.toPx()
                        val x = orbitCenter.x + cos(angle).toFloat() * distance
                        val y = orbitCenter.y + sin(angle).toFloat() * distance

                        drawCircle(
                            color = getGracedColor(Color(0xFFEAB308), filterType),
                            radius = (4 + p % 4).dp.toPx(),
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }

                else -> {
                    drawRect(color = Color(0xFF1E1E24))
                }
            }
        }

            // DYNAMIC HIGH-QUALITY ILLUSTRATION OVERLAYS ACCORDING TO USER'S TEXT/PROMPT KEYWORDS
            val lowerTitle = clip.title.lowercase()
            if (lowerTitle.contains("space") || lowerTitle.contains("star") || lowerTitle.contains("ship") || lowerTitle.contains("galaxy") || lowerTitle.contains("alien") || lowerTitle.contains("astro")) {
                // Floating stars
                for (s in 1..4) {
                    val sx = (size.width * 0.15f * s + relMs * 0.05f) % size.width
                    val sy = (size.height * 0.25f + s * 40.dp.toPx()) % size.height
                    drawCircle(
                        color = getGracedColor(Color.White, filterType),
                        radius = 4.dp.toPx() * (1f + sin(relMs * 0.005 + s).toFloat() * 0.3f),
                        center = Offset(sx, sy)
                    )
                }
                // Space rocket/spaceship triangle vector
                val rx = center.x + cos(relMs * 0.003).toFloat() * 60.dp.toPx()
                val ry = center.y + sin(relMs * 0.002).toFloat() * 40.dp.toPx()
                val rPath = Path().apply {
                    moveTo(rx, ry - 15.dp.toPx())
                    lineTo(rx - 10.dp.toPx(), ry + 15.dp.toPx())
                    lineTo(rx + 10.dp.toPx(), ry + 15.dp.toPx())
                    close()
                }
                drawPath(rPath, color = getGracedColor(Color(0xFF38BDF8), filterType))
                // Rocket engine fire flare pulse
                drawCircle(
                    color = getGracedColor(Color(0xFFEF4444), filterType),
                    radius = 6.dp.toPx() * (1f + sin(relMs * 0.01).toFloat() * 0.2f),
                    center = Offset(rx, ry + 16.dp.toPx())
                )
            }

            if (lowerTitle.contains("robot") || lowerTitle.contains("ai") || lowerTitle.contains("cyborg") || lowerTitle.contains("mech") || lowerTitle.contains("synthetic")) {
                // Robot head outline mask
                drawRoundRect(
                    color = getGracedColor(Color(0xFF94A3B8), filterType),
                    topLeft = Offset(center.x - 45.dp.toPx(), center.y - 40.dp.toPx()),
                    size = Size(90.dp.toPx(), 80.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(15.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
                // Glowing laser eyes
                val eyePulse = (1f + sin(relMs * 0.01).toFloat() * 0.2f)
                drawCircle(
                    color = getGracedColor(Color(0xFF22C55E), filterType),
                    radius = 8.dp.toPx() * eyePulse,
                    center = Offset(center.x - 18.dp.toPx(), center.y - 10.dp.toPx())
                )
                drawCircle(
                    color = getGracedColor(Color(0xFF22C55E), filterType),
                    radius = 8.dp.toPx() * eyePulse,
                    center = Offset(center.x + 18.dp.toPx(), center.y - 10.dp.toPx())
                )
                // Scanner horizontal red/green bar panning
                val scanY = center.y - 40.dp.toPx() + ((relMs * 0.05f) % 80.dp.toPx())
                drawLine(
                    color = getGracedColor(Color(0xFFEF4444).copy(alpha = 0.8f), filterType),
                    start = Offset(center.x - 45.dp.toPx(), scanY),
                    end = Offset(center.x + 45.dp.toPx(), scanY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            if (lowerTitle.contains("car") || lowerTitle.contains("drive") || lowerTitle.contains("speed") || lowerTitle.contains("race") || lowerTitle.contains("hologram") || lowerTitle.contains("pursuit")) {
                // Side speed trail lines
                for (i in 1..5) {
                    val lx = (relMs * 0.12f * i) % size.width
                    val ly = size.height * 0.18f * i
                    drawLine(
                        color = getGracedColor(Color.White.copy(alpha = 0.35f), filterType),
                        start = Offset(lx, ly),
                        end = Offset(lx + 50.dp.toPx(), ly),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                // Central fast pursuit tail lights
                val flareLeftX = center.x - 40.dp.toPx()
                val flareRightX = center.x + 40.dp.toPx()
                drawCircle(
                    color = getGracedColor(Color(0xFFFF2E93).copy(alpha = 0.6f), filterType),
                    radius = 28.dp.toPx(),
                    center = Offset(flareLeftX, center.y + 15.dp.toPx())
                )
                drawCircle(
                    color = getGracedColor(Color(0xFFFF2E93).copy(alpha = 0.6f), filterType),
                    radius = 28.dp.toPx(),
                    center = Offset(flareRightX, center.y + 15.dp.toPx())
                )
            }

            if (lowerTitle.contains("tree") || lowerTitle.contains("forest") || lowerTitle.contains("nature") || lowerTitle.contains("jungle") || lowerTitle.contains("bloom") || lowerTitle.contains("flower") || lowerTitle.contains("plant")) {
                // Rows of glowing pine vector shapes
                for (i in 0..4) {
                    val tx = (i * 95.dp.toPx() + relMs * 0.02f) % (size.width + 60.dp.toPx()) - 30.dp.toPx()
                    val ty = size.height * 0.72f
                    val treePath = Path().apply {
                        moveTo(tx, ty - 45.dp.toPx())
                        lineTo(tx - 20.dp.toPx(), ty)
                        lineTo(tx + 20.dp.toPx(), ty)
                        close()
                    }
                    drawPath(treePath, color = getGracedColor(Color(0xFF10B981).copy(alpha = 0.8f), filterType))
                }
            }

            if (lowerTitle.contains("food") || lowerTitle.contains("cook") || lowerTitle.contains("kitchen") || lowerTitle.contains("cake") || lowerTitle.contains("culinary") || lowerTitle.contains("soup")) {
                // Chef boiling steaming pot vector
                drawRoundRect(
                    color = getGracedColor(Color(0xFFCBD5E1), filterType),
                    topLeft = Offset(center.x - 30.dp.toPx(), center.y + 10.dp.toPx()),
                    size = Size(60.dp.toPx(), 40.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                )
                // Thermal heat steam lines rising
                for (b in 1..3) {
                    val bx = center.x - 20.dp.toPx() + b * 10.dp.toPx()
                    val by = center.y + 10.dp.toPx() - ((relMs * 0.02f + b * 12) % 30).dp.toPx()
                    drawCircle(
                        color = getGracedColor(Color(0xFFFBBF24).copy(alpha = 0.5f), filterType),
                        radius = (3 + b).dp.toPx(),
                        center = Offset(bx, by)
                    )
                }
            }

            if (lowerTitle.contains("cat") || lowerTitle.contains("kitten") || lowerTitle.contains("dog") || lowerTitle.contains("pup") || lowerTitle.contains("feline") || lowerTitle.contains("pet") || lowerTitle.contains("animal") || lowerTitle.contains("whisker")) {
                // Cute responsive feline vector outline overlay
                drawCircle(
                    color = getGracedColor(Color(0xFFF1F5F9), filterType),
                    radius = 32.dp.toPx(),
                    center = center
                )
                // Pointy cat ears vectors
                val earL = Path().apply {
                    moveTo(center.x - 28.dp.toPx(), center.y - 12.dp.toPx())
                    lineTo(center.x - 38.dp.toPx(), center.y - 42.dp.toPx())
                    lineTo(center.x - 8.dp.toPx(), center.y - 28.dp.toPx())
                    close()
                }
                val earR = Path().apply {
                    moveTo(center.x + 28.dp.toPx(), center.y - 12.dp.toPx())
                    lineTo(center.x + 38.dp.toPx(), center.y - 42.dp.toPx())
                    lineTo(center.x + 8.dp.toPx(), center.y - 28.dp.toPx())
                    close()
                }
                drawPath(earL, color = getGracedColor(Color(0xFFE2E8F0), filterType))
                drawPath(earR, color = getGracedColor(Color(0xFFE2E8F0), filterType))
                // Whiskers outlines
                drawLine(getGracedColor(Color(0xFF64748B), filterType), Offset(center.x - 18.dp.toPx(), center.y + 4.dp.toPx()), Offset(center.x - 45.dp.toPx(), center.y + 1.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                drawLine(getGracedColor(Color(0xFF64748B), filterType), Offset(center.x - 18.dp.toPx(), center.y + 8.dp.toPx()), Offset(center.x - 42.dp.toPx(), center.y + 10.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                drawLine(getGracedColor(Color(0xFF64748B), filterType), Offset(center.x + 18.dp.toPx(), center.y + 4.dp.toPx()), Offset(center.x + 45.dp.toPx(), center.y + 1.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                drawLine(getGracedColor(Color(0xFF64748B), filterType), Offset(center.x + 18.dp.toPx(), center.y + 8.dp.toPx()), Offset(center.x + 42.dp.toPx(), center.y + 10.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            }
        }
    }

    // DRAW TEXT OVERLAYS SYNCHRONIZED TO THEIR OFFSET DURATION BRACKETS
    textOverlays.forEach { layer ->
        if (playheadMs >= layer.startOffsetMs && playheadMs < layer.startOffsetMs + layer.durationMs) {
            val isSticker = layer.fontName.startsWith("Sticker")
            val x = size.width * layer.posXPercent
            val y = size.height * layer.posYPercent

            withTransform({
                if (layer.rotateDegrees != 0f) {
                    rotate(layer.rotateDegrees, Offset(x, y))
                }
            }) {
                if (isSticker) {
                    val textPaint = android.text.TextPaint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = (22f * layer.scale * density)
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        if (layer.text.any { Character.isSurrogate(it) }) {
                            // Emojis don't need font pairings
                        } else {
                            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                        }
                    }

                    val isEmojiOnly = layer.text.length <= 4 && layer.text.all { Character.isSurrogate(it) || it.code > 10000 }
                    if (!isEmojiOnly) {
                        val textWidth = textPaint.measureText(layer.text)
                        val textHeight = textPaint.textSize
                        val pX = 12.dp.toPx()
                        val pY = 6.dp.toPx()
                        
                        // Draw badge background
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.85f),
                            topLeft = Offset(x - textWidth / 2f - pX, y - textHeight * 0.85f - pY),
                            size = androidx.compose.ui.geometry.Size(textWidth + pX * 2, textHeight + pY * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )

                        // Futuristic border glow based on sticker subtype
                        val borderColor = when {
                            layer.fontName.endsWith("Vlog") -> Color(0xFF00E5FF)
                            layer.fontName.endsWith("Cinematic") -> Color(0xFFFFD700)
                            layer.fontName.endsWith("Social") -> Color(0xFFEF4444)
                            else -> Color(0xFF8B5CF6)
                        }
                        drawRoundRect(
                            color = borderColor,
                            topLeft = Offset(x - textWidth / 2f - pX, y - textHeight * 0.85f - pY),
                            size = androidx.compose.ui.geometry.Size(textWidth + pX * 2, textHeight + pY * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }

                    drawContext.canvas.nativeCanvas.drawText(layer.text, x, y + textPaint.textSize * 0.15f, textPaint)
                } else {
                    // Overlay text centered
                    val textPaint = android.text.TextPaint().apply {
                        color = android.graphics.Color.parseColor(layer.colorHex)
                        textSize = (13f * layer.scale * density)
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    // High custom font typography pairing
                    textPaint.typeface = when (layer.fontName) {
                        "Space Grotesk" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
                        "Modern Sans" -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD_ITALIC)
                        "Retro Cursive" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC)
                        else -> android.graphics.Typeface.DEFAULT_BOLD
                    }

                    // Stroke outline shadow mappings
                    val strokePaint = android.text.TextPaint().apply {
                        color = android.graphics.Color.parseColor(layer.strokeColorHex)
                        textSize = textPaint.textSize
                        typeface = textPaint.typeface
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    drawContext.canvas.nativeCanvas.drawText(layer.text, x, y, strokePaint)
                    drawContext.canvas.nativeCanvas.drawText(layer.text, x, y, textPaint)
                }
            }
        }
    }

    // WATERMARK RAMP LABELS OVERLAY (Standard CapCut branding template)
    if (!removeWatermark) {
        val waterColor = Color.White.copy(alpha = 0.35f)
        drawLine(
            color = waterColor,
            start = Offset(12.dp.toPx(), 16.dp.toPx()),
            end = Offset(32.dp.toPx(), 16.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
        // Text water bounds
        val waterPaint = android.text.TextPaint().apply {
            color = android.graphics.Color.argb(90, 255, 255, 255)
            textSize = 10.dp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText("CapCut Studio", 38.dp.toPx(), 20.dp.toPx(), waterPaint)
    }
}

// Convert Color based on filter configurations
fun getGracedColor(base: Color, filter: String): Color {
    return when (filter) {
        "cyberpunk" -> {
            // Tilt color indices to magenta-cyan gradient colors
            val gray = (base.red + base.green + base.blue) / 3f
            Color(red = gray * 0.5f + 0.5f, green = 0.1f, blue = gray * 0.3f + 0.7f)
        }
        "glitch" -> {
            // Highly neon saturated split colors
            Color(red = base.red.coerceIn(0.2f, 1f), green = base.blue.coerceIn(0.1f, 1f), blue = base.green)
        }
        "warm" -> {
            // Warm sepia grading
            Color(red = (base.red * 1.2f).coerceAtMost(1f), green = (base.green * 1.05f).coerceAtMost(1f), blue = (base.blue * 0.8f).coerceAtMost(1f))
        }
        "cinematic" -> {
            // High metal steel contrasts
            Color(red = base.red * 0.8f, green = base.green * 0.95f, blue = (base.blue * 1.1f).coerceAtMost(1f))
        }
        "vhs" -> {
            // Glare color split with slight blue decay
            Color(red = base.red * 0.9f, green = base.green * 1.1f, blue = base.blue * 0.85f)
        }
        "black_white" -> {
            // Standard luminance grayscale
            val gray = 0.299f * base.red + 0.587f * base.green + 0.114f * base.blue
            Color(red = gray, green = gray, blue = gray)
        }
        else -> base
    }
}
