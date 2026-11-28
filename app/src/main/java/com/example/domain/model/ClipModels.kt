package com.example.domain.model

enum class TargetPlatform(
    val displayName: String,
    val defaultRatio: AspectRatioPreset,
    val maxRecommendedSec: Int
) {
    REELS_TIKTOK("Reels / TikTok", AspectRatioPreset.VERTICAL_9_16, 45),
    YOUTUBE_SHORTS("YouTube Shorts", AspectRatioPreset.VERTICAL_9_16, 58),
    INSTAGRAM_FEED("Feed 4:5", AspectRatioPreset.PORTRAIT_4_5, 60),
    SQUARE_POST("Square 1:1", AspectRatioPreset.SQUARE_1_1, 60),
    HIGHLIGHT_WIDE("Highlight 16:9", AspectRatioPreset.LANDSCAPE_16_9, 90)
}

enum class AspectRatioPreset(
    val label: String,
    val ratioFloat: Float,
    val targetWidth: Int,
    val targetHeight: Int
) {
    VERTICAL_9_16("9:16", 9f / 16f, 1080, 1920),
    PORTRAIT_4_5("4:5", 4f / 5f, 1080, 1350),
    SQUARE_1_1("1:1", 1f, 1080, 1080),
    LANDSCAPE_16_9("16:9", 16f / 9f, 1920, 1080)
}

enum class CaptionTone(val label: String) {
    ENERGETIC("Enerjik"),
    PROFESSIONAL("Profesional"),
    HUMOROUS("Santai / Lucu"),
    EDUCATIONAL("Edukatif")
}

enum class VideoFilterPreset(val label: String) {
    NONE("Asli"),
    GRAYSCALE("Grayscale"),
    SEPIA("Sepia"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool")
}

enum class HookOverlayStyle(val label: String) {
    NEON_BADGE("Neon Badge"),
    CYBER_BANNER("Cyber Banner"),
    MINIMAL_BOLD("Minimal Bold")
}

enum class VideoTransitionStyle(
    val label: String,
    val description: String
) {
    NONE("Tanpa Transisi", "Potongan langsung (cut)"),
    FADE("Fade", "Masuk & keluar halus lewat gelap"),
    SLIDE("Slide", "Geser horizontal dinamis antar klip"),
    DISSOLVE("Dissolve", "Pudar silang lembut dengan efek kilau")
}

enum class MusicMood(val label: String) {
    UPBEAT("Upbeat / Enerjik"),
    CORPORATE("Fokus / Profesional"),
    LOFI_CHILL("Chill / Santai"),
    DRAMATIC("Epik / Klimaks")
}

data class AudioAnalysisSummary(
    val detectedMood: MusicMood,
    val estimatedBpm: Int,
    val energyPercent: Int,
    val paceLabel: String,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val hasOriginalAudio: Boolean = true,
    val analysisSummary: String
)

data class MusicTrackRecommendation(
    val id: String,
    val title: String,
    val artist: String,
    val mood: MusicMood,
    val bpm: Int,
    val durationSec: Int,
    val licenseLabel: String,
    val audioUrl: String,
    val isCustomUrl: Boolean = false,
    val matchScore: Int = 95,
    val rationale: String = ""
)

enum class ExportQuality(
    val label: String,
    val shortSidePx: Int
) {
    HD_1080P("HD (1080p)", 1080),
    HD_720P("720p", 720),
    SD_480P("480p", 480)
}

data class PopularVideoItem(
    val id: String,
    val title: String,
    val author: String,
    val viewsLabel: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val platform: SocialSourcePlatform,
    val streamUrl: String,
    val thumbnailUrl: String
)

enum class StudioImportSource {
    GALLERY,
    SOCIAL_LINK
}

enum class SocialSourcePlatform(val displayName: String) {
    YOUTUBE("YouTube"),
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    X_TWITTER("X / Twitter"),
    THREADS("Threads"),
    VIMEO("Vimeo"),
    DIRECT_LINK("URL Video"),
    OTHER_SOCIAL("Sosial Media")
}

data class ResolvedSocialVideo(
    val originalUrl: String,
    val resolvedPageUrl: String,
    val platform: SocialSourcePlatform,
    val title: String,
    val authorName: String? = null,
    val thumbnailUrl: String? = null,
    val playableStreamUrl: String? = null,
    val durationMs: Long = 60_000L,
    val width: Int = 1080,
    val height: Int = 1920,
    val statusNote: String = ""
)

data class VideoMetadata(
    val uriString: String,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sourcePlatform: SocialSourcePlatform? = null,
    val authorName: String? = null,
    val thumbnailUrl: String? = null,
    val originalShareUrl: String? = null,
    val hasPlayableStream: Boolean = true,
    val streamStatusNote: String? = null
)

data class ClipProject(
    val id: Long = 0L,
    val title: String,
    val sourceUri: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val targetPlatform: TargetPlatform,
    val focusTopic: String,
    val sourcePlatformName: String? = null,
    val authorName: String? = null,
    val thumbnailUrl: String? = null,
    val hasPlayableStream: Boolean = true,
    val isPlaceholderAi: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class ClipSegment(
    val id: Long = 0L,
    val projectId: Long,
    val title: String,
    val hookLine: String,
    val viralScore: Int,
    val rationale: String,
    val captionText: String,
    val hashtags: String,
    val startMs: Long,
    val endMs: Long,
    val aspectRatio: AspectRatioPreset,
    val tone: CaptionTone = CaptionTone.ENERGETIC,
    val videoFilter: VideoFilterPreset = VideoFilterPreset.NONE,
    val brightness: Float = 0f,
    val showSubtitles: Boolean = true,
    val showIntroHook: Boolean = true,
    val hookDurationMs: Long = 3_000L,
    val hookOverlayStyle: HookOverlayStyle = HookOverlayStyle.NEON_BADGE,
    val transitionStyle: VideoTransitionStyle = VideoTransitionStyle.FADE,
    val transitionDurationMs: Long = 600L,
    val includeAudio: Boolean = true,
    val selectedMusicTrackId: String? = null,
    val selectedMusicTitle: String? = null,
    val selectedMusicUrl: String? = null,
    val musicVolume: Float = 0.35f,
    val audioMoodLabel: String? = null,
    val audioBpm: Int? = null,
    val exportQuality: ExportQuality = ExportQuality.HD_1080P,
    val includeWatermark: Boolean = false,
    val isFavorite: Boolean = false,
    val subtitleCues: List<SubtitleCue> = emptyList(),
    val isPlaceholderAi: Boolean = true
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class ExportedClip(
    val id: Long = 0L,
    val projectId: Long,
    val segmentId: Long,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val aspectRatio: AspectRatioPreset,
    val galleryUri: String? = null,
    val exportedAtMs: Long = System.currentTimeMillis()
)

data class AiAnalysisResult(
    val isPlaceholderMode: Boolean,
    val noticeMessage: String,
    val detectedClips: List<ClipSegment>
)

data class AiCaptionVariation(
    val isPlaceholderMode: Boolean,
    val hookLine: String,
    val captionText: String,
    val hashtags: String
)

data class AiSubtitleResult(
    val isPlaceholderMode: Boolean,
    val cues: List<SubtitleCue>,
    val summaryCaption: String
)

data class AiMusicRecommendationResult(
    val isPlaceholderMode: Boolean,
    val audioAnalysis: AudioAnalysisSummary,
    val recommendedTracks: List<MusicTrackRecommendation>
)

data class AiVisualHookResult(
    val isPlaceholderMode: Boolean,
    val hookLine: String,
    val recommendedStyle: HookOverlayStyle,
    val rationale: String
)

sealed interface ExportProgressState {
    data object Idle : ExportProgressState
    data class Exporting(
        val segmentId: Long,
        val clipTitle: String,
        val progressPercent: Int
    ) : ExportProgressState
    data class Completed(
        val exportedClip: ExportedClip
    ) : ExportProgressState
    data class Error(
        val message: String
    ) : ExportProgressState
}
