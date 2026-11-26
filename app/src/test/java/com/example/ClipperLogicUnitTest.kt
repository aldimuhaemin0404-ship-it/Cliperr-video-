package com.example

import com.example.data.ai.PlaceholderAiClipperRepository
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.network.SocialLinkResolver
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportQuality
import com.example.domain.model.HookOverlayStyle
import com.example.domain.model.MusicMood
import com.example.domain.model.SocialSourcePlatform
import com.example.domain.model.TargetPlatform
import com.example.domain.model.VideoTransitionStyle
import com.example.media.AudioMoodAnalyzer
import com.example.media.RoyaltyFreeMusicCatalog
import com.example.media.VideoTransitionEffects
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipperLogicUnitTest {

    @Test
    fun audioMoodAnalyzerDetectsMoodAndBpm() {
        val summary = AudioMoodAnalyzer.buildSummary(
            telemetry = AudioMoodAnalyzer.RawAudioTelemetry(
                hasAudioTrack = true,
                sampleRateHz = 44100,
                channelCount = 2,
                dynamicVariance = 0.55f
            ),
            clipDurationMs = 10_000L,
            tone = CaptionTone.ENERGETIC,
            targetPlatform = TargetPlatform.REELS_TIKTOK,
            focusTopic = "momen seru"
        )

        assertEquals(MusicMood.UPBEAT, summary.detectedMood)
        assertTrue(summary.estimatedBpm >= 112)
        assertTrue(summary.energyPercent >= 70)
        assertTrue(summary.analysisSummary.contains("44kHz"))
    }

    @Test
    fun royaltyFreeMusicCatalogRanksAndParsesCustomUrl() {
        val topUpbeat = RoyaltyFreeMusicCatalog.rankForMoodAndBpm(MusicMood.UPBEAT, 122).first()
        assertEquals(MusicMood.UPBEAT, topUpbeat.mood)

        val custom = RoyaltyFreeMusicCatalog.buildCustomUrlTrack("example.com/music/my_beat.mp3")
        assertNotNull(custom)
        assertTrue(custom!!.isCustomUrl)
        assertEquals("https://example.com/music/my_beat.mp3", custom.audioUrl)
    }

    @Test
    fun placeholderAiRecommendsMusicTracks() = runBlocking {
        val repo = PlaceholderAiClipperRepository()
        val audioSummary = AudioMoodAnalyzer.buildSummary(
            telemetry = AudioMoodAnalyzer.RawAudioTelemetry(hasAudioTrack = true, dynamicVariance = 0.2f),
            clipDurationMs = 20_000L,
            tone = CaptionTone.EDUCATIONAL,
            targetPlatform = TargetPlatform.YOUTUBE_SHORTS,
            focusTopic = "tips editing"
        )
        val musicResult = repo.recommendMusicForClip(
            audioAnalysis = audioSummary,
            clipTitle = "Tips Editing Cepat",
            focusTopic = "tips editing"
        )
        assertTrue(musicResult.isPlaceholderMode)
        assertTrue(musicResult.recommendedTracks.isNotEmpty())
        assertTrue(musicResult.recommendedTracks.first().rationale.contains("[Sampel AI]"))
    }

    @Test
    fun clipSegmentEntityRoundtripPreservesAllNewFields() {
        val segment = ClipSegment(
            id = 42L,
            projectId = 7L,
            title = "Klip Test",
            hookLine = "Hook 3 Detik Pertama!",
            viralScore = 96,
            rationale = "Top",
            captionText = "Caption",
            hashtags = "#test",
            startMs = 1_000L,
            endMs = 15_000L,
            aspectRatio = AspectRatioPreset.PORTRAIT_4_5,
            showIntroHook = true,
            hookDurationMs = 3_000L,
            hookOverlayStyle = HookOverlayStyle.CYBER_BANNER,
            transitionStyle = VideoTransitionStyle.SLIDE,
            transitionDurationMs = 1_000L,
            selectedMusicTrackId = "rf_neon_pulse",
            selectedMusicTitle = "Neon Horizon Pulse",
            selectedMusicUrl = "synth://rf_neon_pulse",
            musicVolume = 0.45f,
            audioMoodLabel = "Upbeat / Enerjik",
            audioBpm = 124,
            exportQuality = ExportQuality.HD_720P
        )

        val roundtrip = segment.toEntity().toDomain()
        assertEquals(AspectRatioPreset.PORTRAIT_4_5, roundtrip.aspectRatio)
        assertEquals(ExportQuality.HD_720P, roundtrip.exportQuality)
        assertEquals(segment.showIntroHook, roundtrip.showIntroHook)
        assertEquals(segment.hookDurationMs, roundtrip.hookDurationMs)
        assertEquals(segment.hookOverlayStyle, roundtrip.hookOverlayStyle)
        assertEquals(VideoTransitionStyle.SLIDE, roundtrip.transitionStyle)
        assertEquals(1_000L, roundtrip.transitionDurationMs)
        assertEquals(segment.selectedMusicTrackId, roundtrip.selectedMusicTrackId)
        assertEquals(segment.selectedMusicTitle, roundtrip.selectedMusicTitle)
        assertEquals(segment.selectedMusicUrl, roundtrip.selectedMusicUrl)
        assertEquals(segment.musicVolume, roundtrip.musicVolume, 0.001f)
        assertEquals(segment.audioMoodLabel, roundtrip.audioMoodLabel)
        assertEquals(segment.audioBpm, roundtrip.audioBpm)
    }

    @Test
    fun videoTransitionEffectsBuildsExpectedGpuEffects() {
        assertTrue(
            VideoTransitionEffects.buildEffects(
                style = VideoTransitionStyle.NONE,
                transitionDurationMs = 600L,
                clipDurationMs = 8_000L
            ).isEmpty()
        )
        assertEquals(
            1,
            VideoTransitionEffects.buildEffects(
                style = VideoTransitionStyle.FADE,
                transitionDurationMs = 600L,
                clipDurationMs = 8_000L
            ).size
        )
        assertEquals(
            2,
            VideoTransitionEffects.buildEffects(
                style = VideoTransitionStyle.SLIDE,
                transitionDurationMs = 600L,
                clipDurationMs = 8_000L
            ).size
        )
        assertEquals(
            2,
            VideoTransitionEffects.buildEffects(
                style = VideoTransitionStyle.DISSOLVE,
                transitionDurationMs = 600L,
                clipDurationMs = 8_000L
            ).size
        )
    }

    @Test
    fun verticalAspectRatiosAndResolutionsComputeValidDimensions() {
        assertEquals(1080, AspectRatioPreset.VERTICAL_9_16.targetWidth)
        assertEquals(1920, AspectRatioPreset.VERTICAL_9_16.targetHeight)
        assertEquals(1080, AspectRatioPreset.PORTRAIT_4_5.targetWidth)
        assertEquals(1350, AspectRatioPreset.PORTRAIT_4_5.targetHeight)
        assertEquals(1080, ExportQuality.HD_1080P.shortSidePx)
        assertEquals(720, ExportQuality.HD_720P.shortSidePx)
    }

    @Test
    fun socialLinkResolverExtractsUrlsCorrectly() {
        val resolver = SocialLinkResolver()
        val extracted = resolver.extractFirstUrl("Cek video keren ini https://vt.tiktok.com/ZSexample/ sekarang")
        assertEquals("https://vt.tiktok.com/ZSexample/", extracted)
        assertEquals(
            SocialSourcePlatform.YOUTUBE,
            resolver.detectPlatform("https://youtu.be/dQw4w9WgXcQ")
        )
        assertEquals(
            SocialSourcePlatform.DIRECT_LINK,
            resolver.detectPlatform("https://cdn.example.com/clip.mp4")
        )
    }
}
