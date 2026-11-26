package com.example.data.firebase

import androidx.annotation.Keep
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportQuality
import com.example.domain.model.ExportedClip
import com.example.domain.model.HookOverlayStyle
import com.example.domain.model.TargetPlatform
import com.example.domain.model.VideoFilterPreset
import com.example.domain.model.VideoTransitionStyle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

inline fun <reified T : Enum<T>> safeEnumValueOf(raw: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudClipProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localProjectId: Long = 0L,
    val title: String = "",
    val sourceUri: String = "",
    val durationMs: Long = 0L,
    val width: Long = 0L,
    val height: Long = 0L,
    val targetPlatform: String = TargetPlatform.REELS_TIKTOK.name,
    val focusTopic: String = "",
    val sourcePlatformName: String? = null,
    val authorName: String? = null,
    val thumbnailUrl: String? = null,
    val hasPlayableStream: Boolean = true,
    val placeholderAi: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toDomain(): ClipProject = ClipProject(
        id = localProjectId,
        title = title,
        sourceUri = sourceUri,
        durationMs = durationMs,
        width = width.toInt(),
        height = height.toInt(),
        targetPlatform = safeEnumValueOf(targetPlatform, TargetPlatform.REELS_TIKTOK),
        focusTopic = focusTopic,
        sourcePlatformName = sourcePlatformName,
        authorName = authorName,
        thumbnailUrl = thumbnailUrl,
        hasPlayableStream = hasPlayableStream,
        isPlaceholderAi = placeholderAi,
        createdAtMs = (createdAt?.seconds ?: 0L) * 1000L
    )
}

@Keep
@IgnoreExtraProperties
data class CloudClipSegment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectCloudId: String = "",
    val localProjectId: Long = 0L,
    val localSegmentId: Long = 0L,
    val title: String = "",
    val hookLine: String = "",
    val viralScore: Long = 90L,
    val rationale: String = "",
    val captionText: String = "",
    val hashtags: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val aspectRatio: String = AspectRatioPreset.VERTICAL_9_16.name,
    val tone: String = CaptionTone.ENERGETIC.name,
    val videoFilter: String = VideoFilterPreset.NONE.name,
    val brightness: Double = 0.0,
    val showSubtitles: Boolean = true,
    val showIntroHook: Boolean = true,
    val hookDurationMs: Long = 3_000L,
    val hookOverlayStyle: String = HookOverlayStyle.NEON_BADGE.name,
    val transitionStyle: String = VideoTransitionStyle.FADE.name,
    val transitionDurationMs: Long = 600L,
    val includeAudio: Boolean = true,
    val selectedMusicTrackId: String? = null,
    val selectedMusicTitle: String? = null,
    val selectedMusicUrl: String? = null,
    val musicVolume: Double = 0.35,
    val audioMoodLabel: String? = null,
    val audioBpm: Long? = null,
    val exportQuality: String = ExportQuality.HD_1080P.name,
    val favorite: Boolean = false,
    val placeholderAi: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toDomain(): ClipSegment = ClipSegment(
        id = localSegmentId,
        projectId = localProjectId,
        title = title,
        hookLine = hookLine,
        viralScore = viralScore.toInt().coerceIn(0, 100),
        rationale = rationale,
        captionText = captionText,
        hashtags = hashtags,
        startMs = startMs,
        endMs = endMs,
        aspectRatio = safeEnumValueOf(aspectRatio, AspectRatioPreset.VERTICAL_9_16),
        tone = safeEnumValueOf(tone, CaptionTone.ENERGETIC),
        videoFilter = safeEnumValueOf(videoFilter, VideoFilterPreset.NONE),
        brightness = brightness.toFloat().coerceIn(-0.5f, 0.5f),
        showSubtitles = showSubtitles,
        showIntroHook = showIntroHook,
        hookDurationMs = hookDurationMs.coerceIn(500L, 10_000L),
        hookOverlayStyle = safeEnumValueOf(hookOverlayStyle, HookOverlayStyle.NEON_BADGE),
        transitionStyle = safeEnumValueOf(transitionStyle, VideoTransitionStyle.FADE),
        transitionDurationMs = transitionDurationMs.coerceIn(100L, 3_000L),
        includeAudio = includeAudio,
        selectedMusicTrackId = selectedMusicTrackId,
        selectedMusicTitle = selectedMusicTitle,
        selectedMusicUrl = selectedMusicUrl,
        musicVolume = musicVolume.toFloat().coerceIn(0.05f, 1.0f),
        audioMoodLabel = audioMoodLabel,
        audioBpm = audioBpm?.toInt(),
        exportQuality = safeEnumValueOf(exportQuality, ExportQuality.HD_1080P),
        isFavorite = favorite,
        isPlaceholderAi = placeholderAi
    )
}

@Keep
@IgnoreExtraProperties
data class CloudExportedClip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectCloudId: String = "",
    val localExportId: Long = 0L,
    val localProjectId: Long = 0L,
    val localSegmentId: Long = 0L,
    val title: String = "",
    val filePath: String = "",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val aspectRatio: String = AspectRatioPreset.VERTICAL_9_16.name,
    val galleryUri: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toDomain(): ExportedClip = ExportedClip(
        id = localExportId,
        projectId = localProjectId,
        segmentId = localSegmentId,
        title = title,
        filePath = filePath,
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        aspectRatio = safeEnumValueOf(aspectRatio, AspectRatioPreset.VERTICAL_9_16),
        galleryUri = galleryUri,
        exportedAtMs = (createdAt?.seconds ?: 0L) * 1000L
    )
}

internal fun ClipProject.toFirestoreCreateMap(userId: String): Map<String, Any> =
    mapOf<String, Any?>(
        "userId" to userId,
        "localProjectId" to id.coerceAtLeast(0L),
        "title" to title.trim().ifEmpty { "Proyek Klip" }.take(200),
        "sourceUri" to sourceUri.trim().ifEmpty { "local://video" }.take(1000),
        "durationMs" to durationMs.coerceIn(0L, 86_400_000L),
        "width" to width.toLong().coerceIn(0L, 16_384L),
        "height" to height.toLong().coerceIn(0L, 16_384L),
        "targetPlatform" to targetPlatform.name,
        "focusTopic" to focusTopic.take(200),
        "sourcePlatformName" to sourcePlatformName?.take(80),
        "authorName" to authorName?.take(120),
        "thumbnailUrl" to thumbnailUrl?.take(1000),
        "hasPlayableStream" to hasPlayableStream,
        "placeholderAi" to isPlaceholderAi,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

internal fun ClipProject.toFirestoreUpdateMap(): Map<String, Any> =
    mapOf<String, Any?>(
        "title" to title.trim().ifEmpty { "Proyek Klip" }.take(200),
        "sourceUri" to sourceUri.trim().ifEmpty { "local://video" }.take(1000),
        "durationMs" to durationMs.coerceIn(0L, 86_400_000L),
        "width" to width.toLong().coerceIn(0L, 16_384L),
        "height" to height.toLong().coerceIn(0L, 16_384L),
        "targetPlatform" to targetPlatform.name,
        "focusTopic" to focusTopic.take(200),
        "sourcePlatformName" to sourcePlatformName?.take(80),
        "authorName" to authorName?.take(120),
        "thumbnailUrl" to thumbnailUrl?.take(1000),
        "hasPlayableStream" to hasPlayableStream,
        "placeholderAi" to isPlaceholderAi,
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

internal fun ClipSegment.toFirestoreCreateMap(
    userId: String,
    projectCloudId: String
): Map<String, Any> =
    mapOf<String, Any?>(
        "userId" to userId,
        "projectCloudId" to projectCloudId,
        "localProjectId" to projectId.coerceAtLeast(0L),
        "localSegmentId" to id.coerceAtLeast(0L),
        "title" to title.trim().ifEmpty { "Klip Sorotan" }.take(200),
        "hookLine" to hookLine.take(300),
        "viralScore" to viralScore.toLong().coerceIn(0L, 100L),
        "rationale" to rationale.take(800),
        "captionText" to captionText.take(1500),
        "hashtags" to hashtags.take(400),
        "startMs" to startMs.coerceIn(0L, 86_400_000L),
        "endMs" to endMs.coerceIn(0L, 86_400_000L),
        "aspectRatio" to aspectRatio.name,
        "tone" to tone.name,
        "videoFilter" to videoFilter.name,
        "brightness" to brightness.toDouble().coerceIn(-1.0, 1.0),
        "showSubtitles" to showSubtitles,
        "showIntroHook" to showIntroHook,
        "hookDurationMs" to hookDurationMs.coerceIn(500L, 10_000L),
        "hookOverlayStyle" to hookOverlayStyle.name,
        "transitionStyle" to transitionStyle.name,
        "transitionDurationMs" to transitionDurationMs.coerceIn(100L, 3_000L),
        "includeAudio" to includeAudio,
        "selectedMusicTrackId" to selectedMusicTrackId?.take(120),
        "selectedMusicTitle" to selectedMusicTitle?.take(200),
        "selectedMusicUrl" to selectedMusicUrl?.take(1000),
        "musicVolume" to musicVolume.toDouble().coerceIn(0.0, 1.0),
        "audioMoodLabel" to audioMoodLabel?.take(80),
        "audioBpm" to audioBpm?.toLong()?.coerceIn(30L, 300L),
        "exportQuality" to exportQuality.name,
        "favorite" to isFavorite,
        "placeholderAi" to isPlaceholderAi,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

internal fun ClipSegment.toFirestoreUpdateMap(): Map<String, Any> =
    mapOf<String, Any?>(
        "title" to title.trim().ifEmpty { "Klip Sorotan" }.take(200),
        "hookLine" to hookLine.take(300),
        "viralScore" to viralScore.toLong().coerceIn(0L, 100L),
        "rationale" to rationale.take(800),
        "captionText" to captionText.take(1500),
        "hashtags" to hashtags.take(400),
        "startMs" to startMs.coerceIn(0L, 86_400_000L),
        "endMs" to endMs.coerceIn(0L, 86_400_000L),
        "aspectRatio" to aspectRatio.name,
        "tone" to tone.name,
        "videoFilter" to videoFilter.name,
        "brightness" to brightness.toDouble().coerceIn(-1.0, 1.0),
        "showSubtitles" to showSubtitles,
        "showIntroHook" to showIntroHook,
        "hookDurationMs" to hookDurationMs.coerceIn(500L, 10_000L),
        "hookOverlayStyle" to hookOverlayStyle.name,
        "transitionStyle" to transitionStyle.name,
        "transitionDurationMs" to transitionDurationMs.coerceIn(100L, 3_000L),
        "includeAudio" to includeAudio,
        "selectedMusicTrackId" to selectedMusicTrackId?.take(120),
        "selectedMusicTitle" to selectedMusicTitle?.take(200),
        "selectedMusicUrl" to selectedMusicUrl?.take(1000),
        "musicVolume" to musicVolume.toDouble().coerceIn(0.0, 1.0),
        "audioMoodLabel" to audioMoodLabel?.take(80),
        "audioBpm" to audioBpm?.toLong()?.coerceIn(30L, 300L),
        "exportQuality" to exportQuality.name,
        "favorite" to isFavorite,
        "placeholderAi" to isPlaceholderAi,
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

internal fun ExportedClip.toFirestoreCreateMap(
    userId: String,
    projectCloudId: String
): Map<String, Any> =
    mapOf<String, Any?>(
        "userId" to userId,
        "projectCloudId" to projectCloudId,
        "localExportId" to id.coerceAtLeast(0L),
        "localProjectId" to projectId.coerceAtLeast(0L),
        "localSegmentId" to segmentId.coerceAtLeast(0L),
        "title" to title.trim().ifEmpty { "Klip Ekspor" }.take(200),
        "filePath" to filePath.trim().ifEmpty { "exports/clip.mp4" }.take(1000),
        "durationMs" to durationMs.coerceIn(0L, 86_400_000L),
        "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
        "aspectRatio" to aspectRatio.name,
        "galleryUri" to galleryUri?.take(1000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

internal fun ExportedClip.toFirestoreUpdateMap(): Map<String, Any> =
    mapOf<String, Any?>(
        "title" to title.trim().ifEmpty { "Klip Ekspor" }.take(200),
        "filePath" to filePath.trim().ifEmpty { "exports/clip.mp4" }.take(1000),
        "durationMs" to durationMs.coerceIn(0L, 86_400_000L),
        "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
        "aspectRatio" to aspectRatio.name,
        "galleryUri" to galleryUri?.take(1000),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
