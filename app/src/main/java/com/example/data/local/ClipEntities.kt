package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportQuality
import com.example.domain.model.ExportedClip
import com.example.domain.model.HookOverlayStyle
import com.example.domain.model.SubtitleCue
import com.example.domain.model.TargetPlatform
import com.example.domain.model.VideoFilterPreset
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "clip_projects")
data class ClipProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val sourceUri: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val targetPlatformName: String,
    val focusTopic: String,
    val sourcePlatformName: String? = null,
    val authorName: String? = null,
    val thumbnailUrl: String? = null,
    val hasPlayableStream: Boolean = true,
    val isPlaceholderAi: Boolean,
    val createdAtMs: Long
)

@Entity(
    tableName = "clip_segments",
    foreignKeys = [
        ForeignKey(
            entity = ClipProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class ClipSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val title: String,
    val hookLine: String,
    val viralScore: Int,
    val rationale: String,
    val captionText: String,
    val hashtags: String,
    val startMs: Long,
    val endMs: Long,
    val aspectRatioName: String,
    val toneName: String,
    val videoFilterName: String = VideoFilterPreset.NONE.name,
    val brightness: Float = 0f,
    val showSubtitles: Boolean,
    val showIntroHook: Boolean = true,
    val hookDurationMs: Long = 3_000L,
    val hookOverlayStyleName: String = HookOverlayStyle.NEON_BADGE.name,
    val includeAudio: Boolean = true,
    val selectedMusicTrackId: String? = null,
    val selectedMusicTitle: String? = null,
    val selectedMusicUrl: String? = null,
    val musicVolume: Float = 0.35f,
    val audioMoodLabel: String? = null,
    val audioBpm: Int? = null,
    val exportQualityName: String = ExportQuality.HD_1080P.name,
    val isFavorite: Boolean = false,
    val subtitleCuesJson: String = "",
    val isPlaceholderAi: Boolean
)

@Entity(tableName = "exported_clips")
data class ExportedClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val segmentId: Long,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val aspectRatioName: String,
    val galleryUri: String? = null,
    val exportedAtMs: Long
)

fun ClipProjectEntity.toDomain(): ClipProject = ClipProject(
    id = id,
    title = title,
    sourceUri = sourceUri,
    durationMs = durationMs,
    width = width,
    height = height,
    targetPlatform = runCatching { TargetPlatform.valueOf(targetPlatformName) }
        .getOrDefault(TargetPlatform.REELS_TIKTOK),
    focusTopic = focusTopic,
    sourcePlatformName = sourcePlatformName,
    authorName = authorName,
    thumbnailUrl = thumbnailUrl,
    hasPlayableStream = hasPlayableStream,
    isPlaceholderAi = isPlaceholderAi,
    createdAtMs = createdAtMs
)

fun ClipProject.toEntity(): ClipProjectEntity = ClipProjectEntity(
    id = id,
    title = title,
    sourceUri = sourceUri,
    durationMs = durationMs,
    width = width,
    height = height,
    targetPlatformName = targetPlatform.name,
    focusTopic = focusTopic,
    sourcePlatformName = sourcePlatformName,
    authorName = authorName,
    thumbnailUrl = thumbnailUrl,
    hasPlayableStream = hasPlayableStream,
    isPlaceholderAi = isPlaceholderAi,
    createdAtMs = createdAtMs
)

fun ClipSegmentEntity.toDomain(): ClipSegment = ClipSegment(
    id = id,
    projectId = projectId,
    title = title,
    hookLine = hookLine,
    viralScore = viralScore,
    rationale = rationale,
    captionText = captionText,
    hashtags = hashtags,
    startMs = startMs,
    endMs = endMs,
    aspectRatio = runCatching { AspectRatioPreset.valueOf(aspectRatioName) }
        .getOrDefault(AspectRatioPreset.VERTICAL_9_16),
    tone = runCatching { CaptionTone.valueOf(toneName) }
        .getOrDefault(CaptionTone.ENERGETIC),
    videoFilter = runCatching { VideoFilterPreset.valueOf(videoFilterName) }
        .getOrDefault(VideoFilterPreset.NONE),
    brightness = brightness.coerceIn(-0.5f, 0.5f),
    showSubtitles = showSubtitles,
    showIntroHook = showIntroHook,
    hookDurationMs = hookDurationMs.coerceIn(1_500L, 5_000L),
    hookOverlayStyle = runCatching { HookOverlayStyle.valueOf(hookOverlayStyleName) }
        .getOrDefault(HookOverlayStyle.NEON_BADGE),
    includeAudio = includeAudio,
    selectedMusicTrackId = selectedMusicTrackId,
    selectedMusicTitle = selectedMusicTitle,
    selectedMusicUrl = selectedMusicUrl,
    musicVolume = musicVolume.coerceIn(0.05f, 1.0f),
    audioMoodLabel = audioMoodLabel,
    audioBpm = audioBpm,
    exportQuality = runCatching { ExportQuality.valueOf(exportQualityName) }
        .getOrDefault(ExportQuality.HD_1080P),
    isFavorite = isFavorite,
    subtitleCues = decodeSubtitleCues(subtitleCuesJson),
    isPlaceholderAi = isPlaceholderAi
)

fun ClipSegment.toEntity(): ClipSegmentEntity = ClipSegmentEntity(
    id = id,
    projectId = projectId,
    title = title,
    hookLine = hookLine,
    viralScore = viralScore,
    rationale = rationale,
    captionText = captionText,
    hashtags = hashtags,
    startMs = startMs,
    endMs = endMs,
    aspectRatioName = aspectRatio.name,
    toneName = tone.name,
    videoFilterName = videoFilter.name,
    brightness = brightness.coerceIn(-0.5f, 0.5f),
    showSubtitles = showSubtitles,
    showIntroHook = showIntroHook,
    hookDurationMs = hookDurationMs.coerceIn(1_500L, 5_000L),
    hookOverlayStyleName = hookOverlayStyle.name,
    includeAudio = includeAudio,
    selectedMusicTrackId = selectedMusicTrackId,
    selectedMusicTitle = selectedMusicTitle,
    selectedMusicUrl = selectedMusicUrl,
    musicVolume = musicVolume.coerceIn(0.05f, 1.0f),
    audioMoodLabel = audioMoodLabel,
    audioBpm = audioBpm,
    exportQualityName = exportQuality.name,
    isFavorite = isFavorite,
    subtitleCuesJson = encodeSubtitleCues(subtitleCues),
    isPlaceholderAi = isPlaceholderAi
)

fun ExportedClipEntity.toDomain(): ExportedClip = ExportedClip(
    id = id,
    projectId = projectId,
    segmentId = segmentId,
    title = title,
    filePath = filePath,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    aspectRatio = runCatching { AspectRatioPreset.valueOf(aspectRatioName) }
        .getOrDefault(AspectRatioPreset.VERTICAL_9_16),
    galleryUri = galleryUri,
    exportedAtMs = exportedAtMs
)

fun ExportedClip.toEntity(): ExportedClipEntity = ExportedClipEntity(
    id = id,
    projectId = projectId,
    segmentId = segmentId,
    title = title,
    filePath = filePath,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    aspectRatioName = aspectRatio.name,
    galleryUri = galleryUri,
    exportedAtMs = exportedAtMs
)

private fun encodeSubtitleCues(cues: List<SubtitleCue>): String {
    if (cues.isEmpty()) return ""
    val array = JSONArray()
    cues.forEach { cue ->
        array.put(
            JSONObject().apply {
                put("startMs", cue.startMs)
                put("endMs", cue.endMs)
                put("text", cue.text)
            }
        )
    }
    return array.toString()
}

private fun decodeSubtitleCues(json: String): List<SubtitleCue> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { idx ->
            val obj = array.optJSONObject(idx) ?: return@mapNotNull null
            SubtitleCue(
                startMs = obj.optLong("startMs", 0L),
                endMs = obj.optLong("endMs", 0L),
                text = obj.optString("text", "")
            )
        }
    }.getOrDefault(emptyList())
}
