package com.example.data.repository

import com.example.data.database.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class FullProjectData(
    val project: ProjectEntity,
    val clips: List<ClipEntity> = emptyList(),
    val audioTracks: List<AudioTrackEntity> = emptyList(),
    val textOverlays: List<TextOverlayEntity> = emptyList()
)

class ProjectRepository(private val dao: VideoEditorDao) {

    val allProjects: Flow<List<ProjectEntity>> = dao.getAllProjects()

    fun getClipsForProject(projectId: Long): Flow<List<ClipEntity>> = dao.getClipsForProject(projectId)
    fun getAudioTracksForProject(projectId: Long): Flow<List<AudioTrackEntity>> = dao.getAudioTracksForProject(projectId)
    fun getTextOverlaysForProject(projectId: Long): Flow<List<TextOverlayEntity>> = dao.getTextOverlaysForProject(projectId)

    fun getFullProjectData(projectId: Long): Flow<FullProjectData?> {
        return combine(
            dao.getClipsForProject(projectId),
            dao.getAudioTracksForProject(projectId),
            dao.getTextOverlaysForProject(projectId)
        ) { clips, audioTracks, textOverlays ->
            val project = dao.getProjectById(projectId)
            if (project != null) {
                FullProjectData(project, clips, audioTracks, textOverlays)
            } else {
                null
            }
        }
    }

    suspend fun createNewProject(name: String, aspectRatio: String = "9:16"): Long {
        val project = ProjectEntity(
            name = name,
            aspectRatio = aspectRatio
        )
        return dao.insertProject(project)
    }

    suspend fun saveProjectState(
        project: ProjectEntity,
        clips: List<ClipEntity>,
        audioTracks: List<AudioTrackEntity>,
        textOverlays: List<TextOverlayEntity>
    ) {
        // Calculate dynamic total duration from clips
        val totalDurationMs = clips.sumOf {
            val duration = (it.durationMs - it.startTrimMs - it.endTrimMs) / it.speed
            duration.toLong().coerceAtLeast(0)
        }
        val updatedProject = project.copy(durationMs = totalDurationMs)
        
        dao.updateProject(updatedProject)
        
        // Overwrite and update related list items
        dao.deleteClipsForProject(project.id)
        if (clips.isNotEmpty()) {
            dao.insertClips(clips.map { it.copy(id = 0, projectId = project.id) })
        }
        
        dao.deleteAudioTracksForProject(project.id)
        if (audioTracks.isNotEmpty()) {
            dao.insertAudioTracks(audioTracks.map { it.copy(id = 0, projectId = project.id) })
        }
        
        dao.deleteTextOverlaysForProject(project.id)
        if (textOverlays.isNotEmpty()) {
            dao.insertTextOverlays(textOverlays.map { it.copy(id = 0, projectId = project.id) })
        }
    }

    suspend fun getProjectSync(projectId: Long): FullProjectData? {
        val project = dao.getProjectById(projectId) ?: return null
        val clips = dao.getClipsForProjectSync(projectId)
        val audioTracks = dao.getAudioTracksForProjectSync(projectId)
        val textOverlays = dao.getTextOverlaysForProjectSync(projectId)
        return FullProjectData(project, clips, audioTracks, textOverlays)
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deleteProjectById(projectId)
    }

    suspend fun duplicateProject(projectId: Long): Long? {
        val existing = getProjectSync(projectId) ?: return null
        val newProjId = dao.insertProject(
            ProjectEntity(
                name = "${existing.project.name} (Copy)",
                aspectRatio = existing.project.aspectRatio,
                exportResolution = existing.project.exportResolution,
                exportFps = existing.project.exportFps
            )
        )
        
        val duplicatedClips = existing.clips.map {
            it.copy(id = 0, projectId = newProjId)
        }
        dao.insertClips(duplicatedClips)
        
        val duplicatedAudio = existing.audioTracks.map {
            it.copy(id = 0, projectId = newProjId)
        }
        dao.insertAudioTracks(duplicatedAudio)
        
        val duplicatedText = existing.textOverlays.map {
            it.copy(id = 0, projectId = newProjId)
        }
        dao.insertTextOverlays(duplicatedText)
        
        return newProjId
    }
}
