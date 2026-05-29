package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsScreen(
    viewModel: VideoEditorViewModel,
    onOpenTimeline: (Long) -> Unit
) {
    var textToVideoPrompt by remember { mutableStateOf("") }
    var selectedAspectRatio by remember { mutableStateOf("16:9") }
    var selectedStyle by remember { mutableStateOf("Realistic Style") }
    var videoLengthSeconds by remember { mutableStateOf(15f) } // Max 600s (10 minutes)
    val availableStyleReferencePics = remember {
        listOf(
            "Cinematic Tokyo Alley",
            "Glitch Wave Painting",
            "Glacial Mountains",
            "Gold Horizon Sky",
            "Neon Hologram Silhouette",
            "Vaporwave Wireframe Grid"
        )
    }
    var selectedReferencePics by remember { mutableStateOf(setOf<String>()) }
    var customUploadedPicsCount by remember { mutableStateOf(0) }

    var scriptTopic by remember { mutableStateOf("") }
    var hashtagTopic by remember { mutableStateOf("") }
    var thumbnailTitle by remember { mutableStateOf("") }

    var calculatedScript by remember { mutableStateOf("") }
    var calculatedHashtags by remember { mutableStateOf("") }
    var calculatedThumbnail by remember { mutableStateOf("") }

    val activeProject by viewModel.activeProject.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()

    // Automatically navigate to timeline on successful AI video generation!
    androidx.compose.runtime.LaunchedEffect(aiStatus) {
        if ((aiStatus == "AI Project Generated Successfully!" || aiStatus == "AI Project Generated via Intelligent Fallback!") && activeProject != null) {
            // Give a tiny delay for visual confirmation, then auto-route to timeline
            kotlinx.coroutines.delay(800)
            onOpenTimeline(activeProject!!.id)
        }
    }

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
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 80.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PrimaryViolet,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AI Creative Toolkit",
                    color = LightText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }

            // TOOL 1: TEXT TO VIDEO STORYBOARD GENERATOR
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MovieFilter, contentDescription = null, tint = PrimaryViolet, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AI Text to Video Storyboard",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Enter a prompt and customize aspect ratio, length (max 10 mins), and upload images to synthesize your dream clip sequences.",
                        fontSize = 11.sp,
                        color = MutedText,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = textToVideoPrompt,
                        onValueChange = { textToVideoPrompt = it },
                        placeholder = { Text("What do you want to generate? e.g., Futuristic car driving into cyberpunk sunrise", color = MutedText, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryViolet,
                            unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .testTag("ai_text_video_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // SETTING A: ASPECT RATIO CONFIG
                    Text(
                        text = "Select Aspect Ratio:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("16:9" to Icons.Default.AspectRatio, "9:16" to Icons.Default.StayCurrentPortrait, "1:1" to Icons.Default.Square, "4:3" to Icons.Default.Tv).forEach { (ratio, icon) ->
                            val isSelected = selectedAspectRatio == ratio
                            val textLabel = if (ratio == "9:16") "9:16 (Short)" else ratio
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAspectRatio = ratio },
                                label = { Text(textLabel, fontSize = 11.sp) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryViolet,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = TimelineGrid,
                                    labelColor = MutedText
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SETTING A.2: VISUAL STYLE SELECTOR
                    Text(
                        text = "Select Visual Aesthetic Style:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Realistic" to Icons.Default.CameraAlt,
                            "Cartoon" to Icons.Default.Palette,
                            "Anime" to Icons.Default.Face,
                            "Cinematic" to Icons.Default.Movie
                        ).forEach { (style, icon) ->
                            val isSelected = selectedStyle == style
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedStyle = style },
                                label = { Text(style, fontSize = 11.sp) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryViolet,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = TimelineGrid,
                                    labelColor = MutedText
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SETTING B: VIDEO LENGTH CONVERTER (MAX 10 MINUTES)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Video Duration:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                        val totalSecs = videoLengthSeconds.toInt()
                        val durationLabel = if (totalSecs >= 60) {
                            "${totalSecs / 60}m ${totalSecs % 60}s"
                        } else {
                            "${totalSecs}s"
                        }
                        Text(
                            text = "$durationLabel (Max 10m)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = videoLengthSeconds,
                        onValueChange = { videoLengthSeconds = it },
                        valueRange = 5f..600f, // Max 10 mins (600 seconds)
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = BorderGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // SETTING C: RELATED PICTURES REFERENCE UPLODAD
                    Text(
                        text = "Upload/Select Style Reference Pictures (Optional):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Quick-Grid of Reference Cards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Interactive Mock upload picker card
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TimelineGrid)
                                .border(1.5.dp, PrimaryViolet, RoundedCornerShape(8.dp))
                                .clickable {
                                    customUploadedPicsCount++
                                    val newPicName = "Uploaded_Photo_${customUploadedPicsCount}.png"
                                    selectedReferencePics = selectedReferencePics + newPicName
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PrimaryViolet, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Upload Pic", fontSize = 10.sp, color = LightText, fontWeight = FontWeight.Bold)
                            }
                        }

                        availableStyleReferencePics.forEach { pic ->
                            val isChosen = selectedReferencePics.contains(pic)
                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChosen) PrimaryViolet.copy(alpha = 0.2f) else BorderGray.copy(alpha = 0.4f))
                                    .border(
                                        1.5.dp,
                                        if (isChosen) PrimaryViolet else BorderGray,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isChosen) {
                                            selectedReferencePics = selectedReferencePics - pic
                                        } else {
                                            selectedReferencePics = selectedReferencePics + pic
                                        }
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (isChosen) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = if (isChosen) PrimaryViolet else MutedText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pic,
                                        fontSize = 9.sp,
                                        color = if (isChosen) LightText else MutedText,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (selectedReferencePics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Selected reference pics (${selectedReferencePics.size}): ${selectedReferencePics.joinToString(", ")}",
                            fontSize = 11.sp,
                            color = AccentCyan,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // TRIGGER GENERATE VIDEO BUTTON
                    Button(
                        onClick = {
                            if (textToVideoPrompt.isNotEmpty()) {
                                if (activeProject == null) {
                                    // Instantiate a target clip project container
                                    viewModel.createAndLoadNewProject("AI Storyboard: ${textToVideoPrompt.take(12)}")
                                }
                                viewModel.triggerAiTextToVideo(
                                    prompt = textToVideoPrompt,
                                    aspectRatio = selectedAspectRatio,
                                    lengthSeconds = videoLengthSeconds.toInt(),
                                    uploadedPictures = selectedReferencePics.toList(),
                                    style = selectedStyle
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_generate_video_button"),
                        enabled = textToVideoPrompt.isNotEmpty()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LAST STEP: GENERATE VIDEO", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }

                    if (activeProject != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onOpenTimeline(activeProject!!.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = TimelineGrid),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Editor Timeline", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // TOOL 2: AI SCRIPT GENERATOR
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AI Intelligent Script Writer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Input a social topic. Receive structural scene pacing and narrator hook narration.",
                        fontSize = 11.sp,
                        color = MutedText
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = scriptTopic,
                        onValueChange = { scriptTopic = it },
                        placeholder = { Text("Topic: e.g., 3 productivity hacks for coding faster", color = MutedText, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (calculatedScript.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TimelineGrid, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = calculatedScript,
                                    color = LightText,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (scriptTopic.isNotEmpty()) {
                                viewModel.triggerAiScriptGenerator(scriptTopic) { script ->
                                    calculatedScript = script
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = scriptTopic.isNotEmpty()
                    ) {
                        Text("Draft Professional Script Outline", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // TOOL 3: AI THUMBNAIL LAYOUT ARCHITECT
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CaptionYellow.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DesignServices, contentDescription = null, tint = CaptionYellow, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AI Cover & Thumbnail Generator",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Develop highly engaging thumbnail layouts and overlays optimized for conversion rates.",
                        fontSize = 11.sp,
                        color = MutedText
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = thumbnailTitle,
                        onValueChange = { thumbnailTitle = it },
                        placeholder = { Text("Cover Title e.g., Lofi Chill Coding Session", color = MutedText, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CaptionYellow,
                            unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (calculatedThumbnail.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TimelineGrid, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = calculatedThumbnail,
                                color = LightText,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (thumbnailTitle.isNotEmpty()) {
                                viewModel.triggerAiThumbnail(thumbnailTitle) { spec ->
                                    calculatedThumbnail = spec
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CaptionYellow, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = thumbnailTitle.isNotEmpty()
                    ) {
                        Text("Formulate Cover Thumbnail Blueprint", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // TOOL 4: VIRAL HASHTAG ARCHITECT
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE11D48).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AI Viral Hash Harvester",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = hashtagTopic,
                        onValueChange = { hashtagTopic = it },
                        placeholder = { Text("Topic e.g., Android Video Editing", color = MutedText, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE11D48),
                            unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (calculatedHashtags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TimelineGrid, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = calculatedHashtags,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (hashtagTopic.isNotEmpty()) {
                                viewModel.triggerAiHashtags(hashtagTopic) { tags ->
                                    calculatedHashtags = tags
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hashtagTopic.isNotEmpty()
                    ) {
                        Text("Harvest Viral Tag List", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Live API Activity indicators
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
                            text = aiStatus ?: "Executing Pipeline...",
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
