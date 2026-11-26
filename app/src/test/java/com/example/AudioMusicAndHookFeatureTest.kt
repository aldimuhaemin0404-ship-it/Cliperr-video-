package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.PlaceholderAiClipperRepository
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipSegment
import com.example.domain.model.HookOverlayStyle
import com.example.domain.model.MusicMood
import com.example.domain.model.TargetPlatform
import com.example.media.AudioMoodAnalyzer
import com.example.media.RoyaltyFreeMusicCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioMusicAndHookFeatureTest {

    @Test
    fun `AudioMoodAnalyzer detects mood and BPM from clip telemetry`() {
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
    fun `RoyaltyFreeMusicCatalog synthesizes real WAV file and supports custom audio URL`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val topUpbeat = RoyaltyFreeMusicCatalog.rankForMoodAndBpm(MusicMood.UPBEAT, 122).first()
        assertEquals(MusicMood.UPBEAT, topUpbeat.mood)

        val uri = RoyaltyFreeMusicCatalog.resolvePlayableAudioUri(
            context = app,
            audioUrl = topUpbeat.audioUrl,
            trackId = topUpbeat.id,
            minDurationMs = 12_000L
        )
        assertNotNull(uri)
        val wavFile = File(uri!!.path!!)
        assertTrue(wavFile.exists())
        assertTrue(wavFile.length() > 4096L)

        val custom = RoyaltyFreeMusicCatalog.buildCustomUrlTrack("example.com/music/my_beat.mp3")
        assertNotNull(custom)
        assertTrue(custom!!.isCustomUrl)
        assertEquals("https://example.com/music/my_beat.mp3", custom.audioUrl)
    }

    @Test
    fun `PlaceholderAiClipperRepository recommends music and generates 3s visual hook`() = runBlocking {
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

        val brightFrame = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val hookResult = repo.generateFirst3SecVisualHook(
            clipTitle = "Tips Editing Cepat",
            focusTopic = "tips editing",
            tone = CaptionTone.ENERGETIC,
            aspectRatio = AspectRatioPreset.VERTICAL_9_16,
            introFrames = listOf(brightFrame)
        )
        assertTrue(hookResult.isPlaceholderMode)
        assertTrue(hookResult.hookLine.contains("[Sampel AI]"))
        assertEquals(HookOverlayStyle.CYBER_BANNER, hookResult.recommendedStyle)
    }

    @Test
    fun `ClipSegmentEntity roundtrip preserves hook and music metadata`() {
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
            aspectRatio = AspectRatioPreset.VERTICAL_9_16,
            showIntroHook = true,
            hookDurationMs = 3_000L,
            hookOverlayStyle = HookOverlayStyle.CYBER_BANNER,
            transitionStyle = com.example.domain.model.VideoTransitionStyle.SLIDE,
            transitionDurationMs = 1_000L,
            selectedMusicTrackId = "rf_neon_pulse",
            selectedMusicTitle = "Neon Horizon Pulse",
            selectedMusicUrl = "synth://rf_neon_pulse",
            musicVolume = 0.45f,
            audioMoodLabel = "Upbeat / Enerjik",
            audioBpm = 124
        )

        val roundtrip = segment.toEntity().toDomain()
        assertEquals(segment.showIntroHook, roundtrip.showIntroHook)
        assertEquals(segment.hookDurationMs, roundtrip.hookDurationMs)
        assertEquals(segment.hookOverlayStyle, roundtrip.hookOverlayStyle)
        assertEquals(segment.selectedMusicTrackId, roundtrip.selectedMusicTrackId)
        assertEquals(segment.selectedMusicTitle, roundtrip.selectedMusicTitle)
        assertEquals(segment.selectedMusicUrl, roundtrip.selectedMusicUrl)
        assertEquals(segment.musicVolume, roundtrip.musicVolume, 0.001f)
        assertEquals(segment.audioMoodLabel, roundtrip.audioMoodLabel)
        assertEquals(segment.audioBpm, roundtrip.audioBpm)
        assertEquals(segment.transitionStyle, roundtrip.transitionStyle)
        assertEquals(segment.transitionDurationMs, roundtrip.transitionDurationMs)
    }

    @Test
    fun `VideoTransitionEffects builds GPU effects for Fade, Slide, and Dissolve`() {
        assertTrue(
            com.example.media.VideoTransitionEffects.buildEffects(
                style = com.example.domain.model.VideoTransitionStyle.NONE,
                transitionDurationMs = 600L,
                clipDurationMs = 8_000L
            ).isEmpty()
        )
        assertEquals(
            1,
            com.example.media.VideoTransitionEffects.buildEffects(
                style = com.example.domain.model.VideoTransitionStyle.FADE,
                transitionDurationMs = 600L,
                clipDurationMs = 8_000L
            ).size
        )
        assertEquals(
            2,
            com.example.media.VideoTransitionEffects.buildEffects(
                style = com.example.domain.model.VideoTransitionStyle.SLIDE,
                transitionDurationMs = 600L,
                clipDurationMs = 8_000L
            ).size
        )
        assertEquals(
            2,
            com.example.media.VideoTransitionEffects.buildEffects(
                style = com.example.domain.model.VideoTransitionStyle.DISSOLVE,
                transitionDurationMs = 600L,
                clipDurationMs = 8_000L
            ).size
        )
    }

    @Test
    fun `Vertical aspect ratios 9-16 and 4-5 with 1080p and 720p compute valid target dimensions`() {
        assertEquals(1080, AspectRatioPreset.VERTICAL_9_16.targetWidth)
        assertEquals(1920, AspectRatioPreset.VERTICAL_9_16.targetHeight)
        assertEquals(1080, AspectRatioPreset.PORTRAIT_4_5.targetWidth)
        assertEquals(1350, AspectRatioPreset.PORTRAIT_4_5.targetHeight)
        assertEquals(1080, com.example.domain.model.ExportQuality.HD_1080P.shortSidePx)
        assertEquals(720, com.example.domain.model.ExportQuality.HD_720P.shortSidePx)
    }
}
