package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.database.*
import com.example.data.repository.FullProjectData
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Representation of gallery selections
data class GalleryMediaItem(
    val id: String,
    val title: String,
    val type: String, // "video", "photo"
    val durationMs: Long,
    val resourceName: String
)

// Snapshots for Undo/Redo stack
data class ProjectStateSnapshot(
    val clips: List<ClipEntity>,
    val audioTracks: List<AudioTrackEntity>,
    val textOverlays: List<TextOverlayEntity>
)

class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VideoEditorDatabase.getDatabase(application)
    private val dao = database.videoEditorDao()
    private val repository = ProjectRepository(dao)

    // Flow of recent projects lists
    val recentProjects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active project editor tracks
    private val _activeProject = MutableStateFlow<ProjectEntity?>(null)
    val activeProject: StateFlow<ProjectEntity?> = _activeProject.asStateFlow()

    private val _clips = MutableStateFlow<List<ClipEntity>>(emptyList())
    val clips: StateFlow<List<ClipEntity>> = _clips.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackEntity>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackEntity>> = _audioTracks.asStateFlow()

    private val _textOverlays = MutableStateFlow<List<TextOverlayEntity>>(emptyList())
    val textOverlays: StateFlow<List<TextOverlayEntity>> = _textOverlays.asStateFlow()

    // Interactive timeline configurations
    private val _selectedClipId = MutableStateFlow<Long?>(null)
    val selectedClipId: StateFlow<Long?> = _selectedClipId.asStateFlow()

    private val _selectedAudioTrackId = MutableStateFlow<Long?>(null)
    val selectedAudioTrackId: StateFlow<Long?> = _selectedAudioTrackId.asStateFlow()

    private val _selectedTextId = MutableStateFlow<Long?>(null)
    val selectedTextId: StateFlow<Long?> = _selectedTextId.asStateFlow()

    private val _playheadPositionMs = MutableStateFlow<Long>(0L)
    val playheadPositionMs: StateFlow<Long> = _playheadPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _timelineZoom = MutableStateFlow(1.0f) // 0.5f to 3.0f width scaling
    val timelineZoom: StateFlow<Float> = _timelineZoom.asStateFlow()

    private val _aiStatus = MutableStateFlow<String?>(null)
    val aiStatus: StateFlow<String?> = _aiStatus.asStateFlow()

    // Export indicators
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0.0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportedFiles = MutableStateFlow<List<String>>(emptyList())
    val exportedFiles: StateFlow<List<String>> = _exportedFiles.asStateFlow()

    // Undo / Redo tracking
    private val undoStack = java.util.Stack<ProjectStateSnapshot>()
    private val redoStack = java.util.Stack<ProjectStateSnapshot>()

    // Playback loop controller logic
    private var playbackJob: Job? = null

    init {
        // Simple timeline monitoring console
        viewModelScope.launch {
            combine(clips, audioTracks, textOverlays) { c, a, t ->
                Triple(c, a, t)
            }.collectLatest { (c, a, t) ->
                val project = _activeProject.value
                if (project != null) {
                    saveActiveStateToDb(project, c, a, t)
                }
            }
        }
    }

    // Capture states before critical edits to support undo triggers
    private fun pushUndoState() {
        undoStack.push(
            ProjectStateSnapshot(
                clips = _clips.value,
                audioTracks = _audioTracks.value,
                textOverlays = _textOverlays.value
            )
        )
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = ProjectStateSnapshot(
                clips = _clips.value,
                audioTracks = _audioTracks.value,
                textOverlays = _textOverlays.value
            )
            redoStack.push(current)

            val back = undoStack.pop()
            _clips.value = back.clips
            _audioTracks.value = back.audioTracks
            _textOverlays.value = back.textOverlays
            
            // Re-eval selected ids if they reference deleted elements
            if (_selectedClipId.value != null && back.clips.none { it.id == _selectedClipId.value }) {
                _selectedClipId.value = null
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = ProjectStateSnapshot(
                clips = _clips.value,
                audioTracks = _audioTracks.value,
                textOverlays = _textOverlays.value
            )
            undoStack.push(current)

            val next = redoStack.pop()
            _clips.value = next.clips
            _audioTracks.value = next.audioTracks
            _textOverlays.value = next.textOverlays
        }
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    // Load active working timeline project
    fun loadProject(projectId: Long) {
        viewModelScope.launch {
            _isPlaying.value = false
            playbackJob?.cancel()

            val data = repository.getProjectSync(projectId)
            if (data != null) {
                _activeProject.value = data.project
                _clips.value = data.clips
                _audioTracks.value = data.audioTracks
                _textOverlays.value = data.textOverlays
                _playheadPositionMs.value = 0L
                _selectedClipId.value = data.clips.firstOrNull()?.id
                undoStack.clear()
                redoStack.clear()
            }
        }
    }

    fun createAndLoadNewProject(name: String, aspectRatio: String = "9:16") {
        viewModelScope.launch {
            val newProjId = repository.createNewProject(name, aspectRatio)
            loadProject(newProjId)
        }
    }

    fun saveActiveProjectSettings(aspectRatio: String) {
        val proj = _activeProject.value ?: return
        pushUndoState()
        val updated = proj.copy(aspectRatio = aspectRatio)
        _activeProject.value = updated
        viewModelScope.launch {
            dao.updateProject(updated)
        }
    }

    private suspend fun saveActiveStateToDb(
        proj: ProjectEntity,
        c: List<ClipEntity>,
        a: List<AudioTrackEntity>,
        t: List<TextOverlayEntity>
    ) {
        withContext(Dispatchers.IO) {
            repository.saveProjectState(proj, c, a, t)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            if (_activeProject.value?.id == projectId) {
                _isPlaying.value = false
                playbackJob?.cancel()
                _activeProject.value = null
                _clips.value = emptyList()
                _audioTracks.value = emptyList()
                _textOverlays.value = emptyList()
            }
            repository.deleteProject(projectId)
        }
    }

    fun duplicateProject(projectId: Long) {
        viewModelScope.launch {
            repository.duplicateProject(projectId)
        }
    }

    fun zoomTimeline(scale: Float) {
        _timelineZoom.value = (_timelineZoom.value + scale).coerceIn(0.5f, 4.0f)
    }

    // Playback coordination
    fun togglePlayback() {
        if (_isPlaying.value) {
            _isPlaying.value = false
            playbackJob?.cancel()
        } else {
            val totalDuration = calculateTotalDurationMs()
            if (totalDuration == 0L) return

            if (_playheadPositionMs.value >= totalDuration) {
                _playheadPositionMs.value = 0L
            }

            _isPlaying.value = true
            playbackJob = viewModelScope.launch {
                val stepMs = 40L
                var lastTime = System.currentTimeMillis()
                while (_isPlaying.value) {
                    delay(stepMs)
                    val now = System.currentTimeMillis()
                    val delta = now - lastTime
                    lastTime = now

                    val nextPos = _playheadPositionMs.value + delta
                    if (nextPos >= totalDuration) {
                        _playheadPositionMs.value = totalDuration
                        _isPlaying.value = false
                        break
                    } else {
                        _playheadPositionMs.value = nextPos
                    }
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val maxDuration = calculateTotalDurationMs()
        _playheadPositionMs.value = positionMs.coerceIn(0L, maxDuration)
    }

    fun calculateTotalDurationMs(): Long {
        return _clips.value.sumOf {
            val netClipDur = (it.durationMs - it.startTrimMs - it.endTrimMs) / it.speed
            netClipDur.toLong().coerceAtLeast(0L)
        }
    }

    // Media importer handler
    fun importSelectedMedia(items: List<GalleryMediaItem>) {
        val proj = _activeProject.value ?: return
        pushUndoState()

        val activeClips = _clips.value.toMutableList()
        var lastSeq = activeClips.maxOfOrNull { it.sequenceIndex } ?: -1

        items.forEach { item ->
            lastSeq++
            activeClips.add(
                ClipEntity(
                    projectId = proj.id,
                    title = item.title,
                    type = item.type,
                    durationMs = item.durationMs,
                    startTrimMs = 0L,
                    endTrimMs = 0L,
                    speed = 1.0f,
                    sequenceIndex = lastSeq,
                    resourceName = item.resourceName
                )
            )
        }
        _clips.value = activeClips
        _selectedClipId.value = activeClips.lastOrNull()?.id
    }

    // Selection indicators
    fun selectClip(id: Long) {
        _selectedClipId.value = id
        _selectedAudioTrackId.value = null
        _selectedTextId.value = null
    }

    fun selectAudioTrack(id: Long) {
        _selectedAudioTrackId.value = id
        _selectedClipId.value = null
        _selectedTextId.value = null
    }

    fun selectTextOverlay(id: Long) {
        _selectedTextId.value = id
        _selectedClipId.value = null
        _selectedAudioTrackId.value = null
    }

    fun clearSelections() {
        _selectedClipId.value = null
        _selectedAudioTrackId.value = null
        _selectedTextId.value = null
    }

    // TIMELINE EDITING OPERATORS

    // 1. SPLIT CLIP: Slice active clip at active playhead timestamp
    fun splitActiveClip() {
        val activeId = _selectedClipId.value ?: return
        val currentClips = _clips.value.sortedBy { it.sequenceIndex }
        val targetIndex = currentClips.indexOfFirst { it.id == activeId }
        if (targetIndex == -1) return

        pushUndoState()

        val target = currentClips[targetIndex]
        
        // Find local relative timestamp inside this specific clip
        var accumulatedTime = 0L
        for (i in 0 until targetIndex) {
            val c = currentClips[i]
            accumulatedTime += ((c.durationMs - c.startTrimMs - c.endTrimMs) / c.speed).toLong()
        }
        
        val relativePlayhead = _playheadPositionMs.value - accumulatedTime
        val relativePlayheadUnscaled = (relativePlayhead * target.speed).toLong()
        val targetClipStartMs = target.startTrimMs + relativePlayheadUnscaled

        // Only allow split if it divides nicely
        val clipNetDur = target.durationMs - target.startTrimMs - target.endTrimMs
        if (relativePlayheadUnscaled <= 200L || relativePlayheadUnscaled >= clipNetDur - 200L) {
            Log.w("Editor", "Playhead split index is too close to boundaries.")
            return
        }

        val clip1 = target.copy(
            id = target.id, // Keep ID for first half
            title = "${target.title} (Part 1)",
            endTrimMs = target.durationMs - targetClipStartMs
        )

        val clip2 = target.copy(
            id = System.nanoTime() % 1000000 + 4000, // Generate distinct key for split segment
            title = "${target.title} (Part 2)",
            startTrimMs = targetClipStartMs,
            sequenceIndex = target.sequenceIndex + 1
        )

        val updatedList = mutableListOf<ClipEntity>()
        currentClips.forEachIndexed { idx, c ->
            if (idx == targetIndex) {
                updatedList.add(clip1)
                updatedList.add(clip2)
            } else {
                val newSeq = if (idx > targetIndex) c.sequenceIndex + 1 else c.sequenceIndex
                updatedList.add(c.copy(sequenceIndex = newSeq))
            }
        }

        _clips.value = updatedList
        _selectedClipId.value = clip2.id // Select second split portion automatically
    }

    // 2. TRIM: Adjust active clip offsets
    fun trimSelectedClip(startMs: Long, endMs: Long) {
        val activeId = _selectedClipId.value ?: return
        val current = _clips.value.toMutableList()
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            pushUndoState()
            val item = current[index]
            current[index] = item.copy(
                startTrimMs = startMs.coerceIn(0, item.durationMs - 200),
                endTrimMs = endMs.coerceIn(0, item.durationMs - startMs - 200)
            )
            _clips.value = current
        }
    }

    // 3. DUPLICATE: clone selected elements
    fun duplicateSelectedClip() {
        val activeId = _selectedClipId.value ?: return
        val current = _clips.value.sortedBy { it.sequenceIndex }
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            pushUndoState()
            val target = current[index]
            val newClip = target.copy(
                id = System.nanoTime() % 1000000 + 10000,
                title = "${target.title} (Dup)",
                sequenceIndex = target.sequenceIndex + 1
            )
            
            val newList = mutableListOf<ClipEntity>()
            current.forEachIndexed { idx, item ->
                if (idx == index) {
                    newList.add(item)
                    newList.add(newClip)
                } else if (idx > index) {
                    newList.add(item.copy(sequenceIndex = item.sequenceIndex + 1))
                } else {
                    newList.add(item)
                }
            }
            _clips.value = newList
            _selectedClipId.value = newClip.id
        }
    }

    // 4. CUT / DELETE
    fun deleteSelectedClip() {
        val activeId = _selectedClipId.value ?: return
        val current = _clips.value.sortedBy { it.sequenceIndex }
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            pushUndoState()
            val remaining = current.filter { it.id != activeId }.mapIndexed { idx, item ->
                item.copy(sequenceIndex = idx)
            }
            _clips.value = remaining
            _selectedClipId.value = remaining.getOrNull(index.coerceAtMost(remaining.size - 1))?.id
            seekTo(_playheadPositionMs.value.coerceAtMost(calculateTotalDurationMs()))
        }
    }

    // 5. SPEED CONTROL
    fun speedSelectedClip(speed: Float) {
        val activeId = _selectedClipId.value ?: return
        val current = _clips.value.toMutableList()
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            pushUndoState()
            val target = current[index]
            current[index] = target.copy(speed = speed.coerceIn(0.1f, 10.0f))
            _clips.value = current
        }
    }

    // 6. ROTATION AND FLIP
    fun rotateSelectedClip() {
        val activeId = _selectedClipId.value ?: return
        val current = _clips.value.toMutableList()
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            pushUndoState()
            val target = current[index]
            current[index] = target.copy(rotationDegrees = (target.rotationDegrees + 90) % 360)
            _clips.value = current
        }
    }

    fun flipSelectedClip() {
        val activeId = _selectedClipId.value ?: return
        val current = _clips.value.toMutableList()
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            pushUndoState()
            val target = current[index]
            current[index] = target.copy(isFlipped = !target.isFlipped)
            _clips.value = current
        }
    }

    // 7. COLOR MATRIX FILTERS
    fun applyFilterToSelectedClip(filterName: String) {
        val activeId = _selectedClipId.value ?: return
        val current = _clips.value.toMutableList()
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            pushUndoState()
            current[index] = current[index].copy(filterType = filterName)
            _clips.value = current
        }
    }

    // 8. CLIP VOLUME
    fun changeVolumeForSelectedClip(volume: Float) {
        val activeId = _selectedClipId.value ?: return
        val current = _clips.value.toMutableList()
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            pushUndoState()
            current[index] = current[index].copy(volume = volume.coerceIn(0.0f, 2.0f))
            _clips.value = current
        }
    }

    // REARRANGE CLIPS (Draggable timeline swapping)
    fun moveClipSequence(fromIdx: Int, toIdx: Int) {
        if (fromIdx == toIdx) return
        val current = _clips.value.sortedBy { it.sequenceIndex }.toMutableList()
        if (fromIdx in current.indices && toIdx in current.indices) {
            pushUndoState()
            val item = current.removeAt(fromIdx)
            current.add(toIdx, item)
            val updated = current.mapIndexed { index, clipEntity ->
                clipEntity.copy(sequenceIndex = index)
            }
            _clips.value = updated
        }
    }

    // --- AUDIO CONTROLLER ACTIONS ---
    fun addAudioTrack(title: String, type: String, startOffsetMs: Long, durationMs: Long) {
        val proj = _activeProject.value ?: return
        pushUndoState()
        val id = System.nanoTime() % 1000000 + 20000
        val track = AudioTrackEntity(
            id = id,
            projectId = proj.id,
            title = title,
            type = type,
            startOffsetMs = startOffsetMs,
            durationMs = durationMs
        )
        _audioTracks.value = _audioTracks.value + track
        _selectedAudioTrackId.value = track.id
    }

    fun deleteSelectedAudio() {
        val activeId = _selectedAudioTrackId.value ?: return
        pushUndoState()
        _audioTracks.value = _audioTracks.value.filter { it.id != activeId }
        _selectedAudioTrackId.value = null
    }

    fun updateSelectedAudioVolume(volume: Float) {
        val activeId = _selectedAudioTrackId.value ?: return
        pushUndoState()
        _audioTracks.value = _audioTracks.value.map {
            if (it.id == activeId) it.copy(volume = volume) else it
        }
    }

    fun toggleSelectedAudioMute() {
        val activeId = _selectedAudioTrackId.value ?: return
        pushUndoState()
        _audioTracks.value = _audioTracks.value.map {
            if (it.id == activeId) it.copy(isMuted = !it.isMuted) else it
        }
    }

    fun updateSelectedAudioFades(fadeInMs: Long, fadeOutMs: Long) {
        val activeId = _selectedAudioTrackId.value ?: return
        pushUndoState()
        _audioTracks.value = _audioTracks.value.map {
            if (it.id == activeId) it.copy(fadeInMs = fadeInMs, fadeOutMs = fadeOutMs) else it
        }
    }

    fun toggleSelectedAudioNoiseReduction() {
        val activeId = _selectedAudioTrackId.value ?: return
        pushUndoState()
        _audioTracks.value = _audioTracks.value.map {
            if (it.id == activeId) it.copy(hasNoiseReduction = !it.hasNoiseReduction) else it
        }
    }

    // --- TEXT OVERLAYS AND TITLE LAYOUT BUILDERS ---
    fun addTextOverlay(text: String, startOffsetMs: Long, durationMs: Long) {
        val proj = _activeProject.value ?: return
        pushUndoState()
        val id = System.nanoTime() % 1000000 + 30000
        val textLayer = TextOverlayEntity(
            id = id,
            projectId = proj.id,
            text = text,
            startOffsetMs = startOffsetMs,
            durationMs = durationMs
        )
        _textOverlays.value = _textOverlays.value + textLayer
        _selectedTextId.value = textLayer.id
    }

    fun addStickerOverlay(text: String, category: String, startOffsetMs: Long, durationMs: Long) {
        val proj = _activeProject.value ?: return
        pushUndoState()
        val id = System.nanoTime() % 1000000 + 40000
        val textLayer = TextOverlayEntity(
            id = id,
            projectId = proj.id,
            text = text,
            startOffsetMs = startOffsetMs,
            durationMs = durationMs,
            fontName = "Sticker:$category",
            scale = 1.6f,
            posXPercent = 0.5f,
            posYPercent = 0.4f
        )
        _textOverlays.value = _textOverlays.value + textLayer
        _selectedTextId.value = textLayer.id
    }

    fun adjustSelectedStickerParam(deltaScale: Float, deltaPosX: Float, deltaPosY: Float, deltaRotate: Float) {
        val activeId = _selectedTextId.value ?: return
        pushUndoState()
        _textOverlays.value = _textOverlays.value.map {
            if (it.id == activeId) {
                it.copy(
                    scale = (it.scale + deltaScale).coerceIn(0.2f, 5.0f),
                    posXPercent = (it.posXPercent + deltaPosX).coerceIn(0.05f, 0.95f),
                    posYPercent = (it.posYPercent + deltaPosY).coerceIn(0.05f, 0.95f),
                    rotateDegrees = (it.rotateDegrees + deltaRotate) % 360f
                )
            } else {
                it
            }
        }
    }

    fun deleteSelectedText() {
        val activeId = _selectedTextId.value ?: return
        pushUndoState()
        _textOverlays.value = _textOverlays.value.filter { it.id != activeId }
        _selectedTextId.value = null
    }

    fun updateSelectedTextParams(
        text: String,
        colorHex: String,
        fontName: String,
        scale: Float = 1.0f,
        posX: Float = 0.5f,
        posY: Float = 0.5f,
        anim: String = "fade"
    ) {
        val activeId = _selectedTextId.value ?: return
        pushUndoState()
        _textOverlays.value = _textOverlays.value.map {
            if (it.id == activeId) {
                it.copy(
                    text = text,
                    colorHex = colorHex,
                    fontName = fontName,
                    scale = scale,
                    posXPercent = posX,
                    posYPercent = posY,
                    animationType = anim
                )
            } else {
                it
            }
        }
    }

    fun moveSelectedTextPosition(deltaX: Float, deltaY: Float) {
        val activeId = _selectedTextId.value ?: return
        _textOverlays.value = _textOverlays.value.map {
            if (it.id == activeId) {
                it.copy(
                    posXPercent = (it.posXPercent + deltaX).coerceIn(0.1f, 0.9f),
                    posYPercent = (it.posYPercent + deltaY).coerceIn(0.1f, 0.9f)
                )
            } else {
                it
            }
        }
    }

    // --- PRO AI WORKFLOW PIPELINES VIA GEMINI DIRECT REST PORTALS ---

    // 1. TEXT TO VIDEO STORYBOARD GENERATOR
    fun triggerAiTextToVideo(
        prompt: String,
        aspectRatio: String = "9:16",
        lengthSeconds: Int = 15,
        uploadedPictures: List<String> = emptyList(),
        style: String = "Realistic"
    ) {
        val activeProj = _activeProject.value ?: return
        
        // Save targeted export aspect ratio straight into the active project settings!
        saveActiveProjectSettings(aspectRatio)

        viewModelScope.launch {
            _aiStatus.value = "Consulting Gemini AI pipeline..."
            val picsInfo = if (uploadedPictures.isNotEmpty()) {
                "Guided by reference uploads: ${uploadedPictures.joinToString(", ")}."
            } else {
                ""
            }
            val robustPrompt = "Read this prompt: '$prompt'. The style aesthetic of the video is '$style'. The target video duration is $lengthSeconds seconds. The aspect ratio matches '$aspectRatio'. $picsInfo Output exactly three cinematic video storyboard blocks/shots formatted in style: '$style'. Divide the total duration of $lengthSeconds seconds among the 3 shots so that their durations sum up exactly to $lengthSeconds seconds. Output exactly three lines in the format 'shot_key | title | duration_seconds | subtitle_text'. Choose shot_key randomly from inside: neon_city, glitch_beach, mountain_peak, retro_grid, abstract_waves."
            
            val result = GeminiApiClient.generateText(robustPrompt)
            
            _aiStatus.value = "Compiling AI elements into editor layers..."
            delay(1200)

            try {
                val lines = result.split("\n")
                    .map { it.replace("\"", "").trim() }
                    .filter { it.contains("|") }
                
                if (lines.isNotEmpty()) {
                    pushUndoState()
                    val generatedClips = mutableListOf<ClipEntity>()
                    val generatedTexts = mutableListOf<TextOverlayEntity>()
                    var timePointerMs = 0L

                    lines.forEachIndexed { i, line ->
                        val parts = line.split("|").map { it.trim() }
                        if (parts.size >= 4) {
                            val cleanPart0 = parts[0].lowercase()
                            val validKey = when {
                                cleanPart0.contains("neon_city") -> "neon_city"
                                cleanPart0.contains("glitch_beach") -> "glitch_beach"
                                cleanPart0.contains("mountain_peak") -> "mountain_peak"
                                cleanPart0.contains("retro_grid") -> "retro_grid"
                                cleanPart0.contains("abstract_waves") -> "abstract_waves"
                                else -> "neon_city"
                            }
                            val title = parts[1]
                            val cleanDuration = parts[2].replace(Regex("[^0-9.]"), "")
                            val durationSec = cleanDuration.toDoubleOrNull()?.coerceIn(2.0, 300.0) ?: (lengthSeconds / 3.0)
                            val textContent = parts[3]

                            val durationMs = (durationSec * 1000).toLong()

                            generatedClips.add(
                                ClipEntity(
                                    projectId = activeProj.id,
                                    title = "AI: $title",
                                    type = "video",
                                    durationMs = durationMs,
                                    sequenceIndex = i,
                                    resourceName = validKey
                                )
                            )

                            generatedTexts.add(
                                TextOverlayEntity(
                                    projectId = activeProj.id,
                                    text = textContent,
                                    startOffsetMs = timePointerMs + 500,
                                    durationMs = if (durationMs > 1000) durationMs - 1000 else durationMs,
                                    isAutoCaption = true,
                                    fontName = "Space Grotesk",
                                    colorHex = "#FFDD00"
                                )
                            )

                            timePointerMs += durationMs
                        }
                    }

                    if (generatedClips.isNotEmpty()) {
                        _clips.value = generatedClips
                        _textOverlays.value = generatedTexts
                        seekTo(0)
                        _aiStatus.value = "AI Project Generated Successfully!"
                        _selectedClipId.value = generatedClips.firstOrNull()?.id
                    } else {
                        throw Exception("Formatting mismatch, fallback synthesis applied.")
                    }
                } else {
                    throw Exception("No valid rows compiled.")
                }
            } catch (e: Exception) {
                // Formatting fallback generator that perfectly matches requested length
                pushUndoState()
                val chunkMs = (lengthSeconds * 1000L) / 3
                _clips.value = listOf(
                    ClipEntity(projectId = activeProj.id, title = "AI: Cyber Scene", type = "video", durationMs = chunkMs, sequenceIndex = 0, resourceName = "neon_city"),
                    ClipEntity(projectId = activeProj.id, title = "AI: Wave Motion", type = "video", durationMs = chunkMs, sequenceIndex = 1, resourceName = "abstract_waves"),
                    ClipEntity(projectId = activeProj.id, title = "AI: Glacier Core", type = "video", durationMs = chunkMs, sequenceIndex = 2, resourceName = "mountain_peak")
                )
                _textOverlays.value = listOf(
                    TextOverlayEntity(projectId = activeProj.id, text = "AI Dream State Initiated...", startOffsetMs = 500L, durationMs = chunkMs - 1000L, isAutoCaption = true),
                    TextOverlayEntity(projectId = activeProj.id, text = "Synthesizing style guide references.", startOffsetMs = chunkMs + 500L, durationMs = chunkMs - 1000L, isAutoCaption = true),
                    TextOverlayEntity(projectId = activeProj.id, text = "Generation active coordinates.", startOffsetMs = (chunkMs * 2) + 500L, durationMs = chunkMs - 1000L, isAutoCaption = true)
                )
                seekTo(0)
                _aiStatus.value = "AI Project Generated via Intelligent Fallback!"
            }
            delay(2000)
            _aiStatus.value = null
        }
    }

    // 2. AUTO-CAPTIONS TRANSCRIPTION SYSTEM (Analyze audio/scene text)
    fun triggerAiCaptions() {
        val proj = _activeProject.value ?: return
        val currentClips = _clips.value
        if (currentClips.isEmpty()) {
            _aiStatus.value = "Add video clips to generate captions first!"
            viewModelScope.launch {
                delay(2000)
                _aiStatus.value = null
            }
            return
        }

        viewModelScope.launch {
            _aiStatus.value = "Gemini Transcribing timelines... 🎧"
            
            // Ask Gemini for funny/engaging dialogue subtitles synced and matching the visual keys
            val visualNames = currentClips.map { it.title }.joinToString(", ")
            val prompt = "Generate exactly ${currentClips.size} conversational short dialogue lines to match these sequentially listed video clips: $visualNames. Output only the sentences, separated by ';'."
            
            val result = GeminiApiClient.generateText(prompt)
            _aiStatus.value = "Synthesizing dynamic SRT layers... ⏱️"
            delay(1000)

            val sentenceList = result.split(";").map { it.trim().replace("\"", "") }
            
            pushUndoState()
            val textLayers = mutableListOf<TextOverlayEntity>()
            var timePointerMs = 0L

            currentClips.forEachIndexed { idx, clip ->
                val clipDur = ((clip.durationMs - clip.startTrimMs - clip.endTrimMs) / clip.speed).toLong()
                val rawDialogue = sentenceList.getOrNull(idx) ?: "Exploring the cosmic timeline."

                textLayers.add(
                    TextOverlayEntity(
                        projectId = proj.id,
                        text = rawDialogue,
                        startOffsetMs = timePointerMs + 500,
                        durationMs = clipDur - 1000,
                        isAutoCaption = true,
                        fontName = "Classic Bold",
                        colorHex = "#ffffff"
                    )
                )
                timePointerMs += clipDur
            }

            _textOverlays.value = _textOverlays.value + textLayers
            _aiStatus.value = "Generated ${textLayers.size} Captions!"
            delay(2000)
            _aiStatus.value = null
        }
    }

    // 3. AI VOICE GENERATOR (TTS Overlays)
    fun triggerAiVoiceover(narrationText: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _aiStatus.value = "Converting Text to AI Narration... 🎙️"
            // Call Gemini to generate a stylized speech model script
            val speechOutput = GeminiApiClient.generateText(
                "You are professional voiceover talent. Read this text cleanly: '$narrationText'. Output only the refined reading statement."
            )
            delay(1500)

            pushUndoState()
            // Add custom voice track in timeline at current playhead position
            val estimatedDurationMs = (speechOutput.length * 80L).coerceIn(2000L, 10000L)
            val id = System.nanoTime() % 1000000 + 40000
            val voiceTrack = AudioTrackEntity(
                id = id,
                projectId = proj.id,
                title = "AI Voiceover: \"${speechOutput.take(15)}...\"",
                type = "voiceover",
                startOffsetMs = _playheadPositionMs.value,
                durationMs = estimatedDurationMs,
                volume = 1.6f
            )
            _audioTracks.value = _audioTracks.value + voiceTrack
            _selectedAudioTrackId.value = voiceTrack.id
            
            _aiStatus.value = "AI Voiceover added to timeline!"
            delay(2000)
            _aiStatus.value = null
        }
    }

    // 4. PHOTO TO VIDEO SYNTHESIS
    fun triggerPhotoToVideo(items: List<GalleryMediaItem>) {
        val proj = _activeProject.value ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            _aiStatus.value = "Creating fluid dynamic slideshow..."
            delay(1000)
            pushUndoState()

            val activeClips = _clips.value.toMutableList()
            var index = activeClips.maxOfOrNull { it.sequenceIndex } ?: -1

            items.forEach { item ->
                index++
                activeClips.add(
                    ClipEntity(
                        projectId = proj.id,
                        title = "Photo: ${item.title}",
                        type = "photo",
                        durationMs = 4000L, // Photo default duration
                        sequenceIndex = index,
                        resourceName = item.resourceName,
                        filterType = "cinematic" // Auto apply gorgeous grading to photo slides
                    )
                )
            }
            _clips.value = activeClips
            // Auto inject trending audio backdrop
            addAudioTrack(
                title = "Acoustic Sunset Mood",
                type = "music",
                startOffsetMs = 0L,
                durationMs = activeClips.size * 4000L
            )
            
            _aiStatus.value = "Slideshow layout synthesized!"
            delay(2000)
            _aiStatus.value = null
        }
    }

    // Interactive helper prompts
    fun triggerAiScriptGenerator(topic: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _aiStatus.value = "Gemini writing script outline..."
            val prompt = "Write a fast-paced 15-second TikTok video script about: '$topic'. Structure it with visual directions and narrator dialogue."
            val script = GeminiApiClient.generateText(prompt)
            onComplete(script)
            _aiStatus.value = null
        }
    }

    fun triggerAiThumbnail(title: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _aiStatus.value = "AI Thumbnail layout formulating..."
            val prompt = "Create a high-CTR YouTube thumbnail design composition for a video titled: '$title'. Outline exact focal points, color choices, text size, and placement recommendations."
            val thumbnailSpec = GeminiApiClient.generateText(prompt)
            onComplete(thumbnailSpec)
            _aiStatus.value = null
        }
    }

    fun triggerAiHashtags(topic: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _aiStatus.value = "Harvesting viral tags..."
            val prompt = "Generate 10 viral top trending TikTok and Instagram hashtags for video topic: '$topic'. Just list the hashtags."
            val tags = GeminiApiClient.generateText(prompt)
            onComplete(tags)
            _aiStatus.value = null
        }
    }

    // --- RENDER EXPORT PIPELINE ENGINE ---
    fun exportVideo(resolution: String, fps: Int, removeWatermark: Boolean) {
        viewModelScope.launch {
            _isPlaying.value = false
            playbackJob?.cancel()
            _isExporting.value = true
            _exportProgress.value = 0.0f
            
            val duration = calculateTotalDurationMs()
            val totalFrames = (duration / 1000f * fps).toInt().coerceAtLeast(30)
            
            Log.d("Exporter", "Beginning render export: res=$resolution, fps=$fps, frames=$totalFrames")
            
            // Loop through frame assembly mimicking raw compositing pipeline
            for (f in 1..totalFrames) {
                delay((1000L / fps).coerceIn(10L, 50L)) // Fast responsive render loop
                _exportProgress.value = f.toFloat() / totalFrames
                // Set playhead position incrementally to showcase real preview render composition
                val currentRenderMs = (f.toFloat() / totalFrames * duration).toLong()
                _playheadPositionMs.value = currentRenderMs
            }

            // Mark completed export assets
            val fileName = "CapCut_Studio_${System.currentTimeMillis() / 1000}_${resolution}.mp4"
            _exportedFiles.value = _exportedFiles.value + fileName
            _isExporting.value = false
            _exportProgress.value = 1.0f
        }
    }
}
