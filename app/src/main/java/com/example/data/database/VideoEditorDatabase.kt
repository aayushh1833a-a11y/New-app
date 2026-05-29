package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val aspectRatio: String = "9:16", // "9:16", "16:9", "1:1"
    val exportResolution: String = "1080p",
    val exportFps: Int = 30
)

@Entity(
    tableName = "clips",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val type: String, // "video" , "photo"
    val durationMs: Long,
    val startTrimMs: Long = 0,
    val endTrimMs: Long = 0,
    val speed: Float = 1.0f,
    val rotationDegrees: Int = 0,
    val isFlipped: Boolean = false,
    val filterType: String = "none", // "none", "cyberpunk", "glitch", "warm", "cinematic", "vhs", "black_white"
    val volume: Float = 1.0f,
    val sequenceIndex: Int,
    val resourceName: String // Sample resource identifier (e.g. "neon_city")
)

@Entity(
    tableName = "audio_tracks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class AudioTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val type: String, // "music", "sound_effect", "voiceover"
    val startOffsetMs: Long,
    val durationMs: Long,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val hasNoiseReduction: Boolean = false,
    val beatsSynced: Boolean = false
)

@Entity(
    tableName = "text_overlays",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class TextOverlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val text: String,
    val startOffsetMs: Long,
    val durationMs: Long,
    val fontName: String = "Classic Bold", // "Classic Bold", "Space Grotesk", "Modern Sans", "Retro Cursive"
    val colorHex: String = "#FFFFFF",
    val strokeColorHex: String = "#000000",
    val hasShadow: Boolean = true,
    val scale: Float = 1.0f,
    val rotateDegrees: Float = 0f,
    val posXPercent: Float = 0.5f,
    val posYPercent: Float = 0.5f,
    val animationType: String = "fade", // "none", "fade", "zoom", "typewriter", "bounce"
    val isAutoCaption: Boolean = false
)

// Main Database DAO
@Dao
interface VideoEditorDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    // --- Clips DAO operations ---
    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY sequenceIndex ASC")
    fun getClipsForProject(projectId: Long): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY sequenceIndex ASC")
    suspend fun getClipsForProjectSync(projectId: Long): List<ClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Query("DELETE FROM clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: Long)

    // --- Audio Dao ---
    @Query("SELECT * FROM audio_tracks WHERE projectId = :projectId")
    fun getAudioTracksForProject(projectId: Long): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE projectId = :projectId")
    suspend fun getAudioTracksForProjectSync(projectId: Long): List<AudioTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioTracks(tracks: List<AudioTrackEntity>)

    @Query("DELETE FROM audio_tracks WHERE projectId = :projectId")
    suspend fun deleteAudioTracksForProject(projectId: Long)

    // --- Text Dao ---
    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId")
    fun getTextOverlaysForProject(projectId: Long): Flow<List<TextOverlayEntity>>

    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId")
    suspend fun getTextOverlaysForProjectSync(projectId: Long): List<TextOverlayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTextOverlays(overlays: List<TextOverlayEntity>)

    @Query("DELETE FROM text_overlays WHERE projectId = :projectId")
    suspend fun deleteTextOverlaysForProject(projectId: Long)
}

@Database(
    entities = [
        ProjectEntity::class,
        ClipEntity::class,
        AudioTrackEntity::class,
        TextOverlayEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VideoEditorDatabase : RoomDatabase() {
    abstract fun videoEditorDao(): VideoEditorDao

    companion object {
        @Volatile
        private var INSTANCE: VideoEditorDatabase? = null

        fun getDatabase(context: android.content.Context): VideoEditorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VideoEditorDatabase::class.java,
                    "video_editor_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
