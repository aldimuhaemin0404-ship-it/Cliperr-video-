package com.example.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.domain.model.AudioAnalysisSummary
import com.example.domain.model.CaptionTone
import com.example.domain.model.MusicMood
import com.example.domain.model.TargetPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object AudioMoodAnalyzer {

    data class RawAudioTelemetry(
        val hasAudioTrack: Boolean,
        val sampleRateHz: Int? = null,
        val channelCount: Int? = null,
        val bitrateBps: Int? = null,
        val dynamicVariance: Float = 0f,
        val sampleCount: Int = 0
    )

    suspend fun analyzeClipAudio(
        context: Context,
        sourceUri: Uri?,
        startMs: Long,
        endMs: Long,
        tone: CaptionTone,
        targetPlatform: TargetPlatform,
        focusTopic: String
    ): AudioAnalysisSummary = withContext(Dispatchers.IO) {
        val telemetry = if (sourceUri != null) {
            extractTelemetry(context, sourceUri, startMs, endMs)
        } else {
            RawAudioTelemetry(hasAudioTrack = true)
        }
        buildSummary(
            telemetry = telemetry,
            clipDurationMs = (endMs - startMs).coerceAtLeast(1_000L),
            tone = tone,
            targetPlatform = targetPlatform,
            focusTopic = focusTopic
        )
    }

    internal fun buildSummary(
        telemetry: RawAudioTelemetry,
        clipDurationMs: Long,
        tone: CaptionTone,
        targetPlatform: TargetPlatform,
        focusTopic: String
    ): AudioAnalysisSummary {
        val durationFactor = when {
            clipDurationMs <= 12_000L -> 12
            clipDurationMs <= 28_000L -> 6
            else -> 0
        }
        val varianceBoost = (telemetry.dynamicVariance * 28f).toInt().coerceIn(0, 24)

        val baseMood = when {
            telemetry.dynamicVariance >= 0.42f && tone == CaptionTone.ENERGETIC -> MusicMood.UPBEAT
            tone == CaptionTone.ENERGETIC -> MusicMood.UPBEAT
            tone == CaptionTone.PROFESSIONAL -> MusicMood.CORPORATE
            tone == CaptionTone.EDUCATIONAL -> if (targetPlatform == TargetPlatform.REELS_TIKTOK) {
                MusicMood.LOFI_CHILL
            } else {
                MusicMood.CORPORATE
            }
            tone == CaptionTone.HUMOROUS -> MusicMood.UPBEAT
            else -> MusicMood.DRAMATIC
        }

        val finalMood = if (
            focusTopic.contains("klimaks", ignoreCase = true) ||
            focusTopic.contains("drama", ignoreCase = true) ||
            focusTopic.contains("gol", ignoreCase = true) ||
            focusTopic.contains("match", ignoreCase = true)
        ) {
            MusicMood.DRAMATIC
        } else if (
            focusTopic.contains("santai", ignoreCase = true) ||
            focusTopic.contains("vlog", ignoreCase = true) ||
            focusTopic.contains("cerita", ignoreCase = true)
        ) {
            MusicMood.LOFI_CHILL
        } else {
            baseMood
        }

        val energy = when (finalMood) {
            MusicMood.UPBEAT -> (72 + durationFactor + varianceBoost).coerceIn(68, 96)
            MusicMood.DRAMATIC -> (68 + durationFactor + varianceBoost).coerceIn(64, 94)
            MusicMood.CORPORATE -> (52 + varianceBoost / 2 + durationFactor / 2).coerceIn(48, 76)
            MusicMood.LOFI_CHILL -> (38 + varianceBoost / 3).coerceIn(34, 62)
        }

        val estimatedBpm = when (finalMood) {
            MusicMood.UPBEAT -> (116 + (energy - 70) / 2).coerceIn(112, 132)
            MusicMood.DRAMATIC -> (98 + (energy - 64) / 2).coerceIn(92, 118)
            MusicMood.CORPORATE -> (102 + (energy - 50) / 3).coerceIn(96, 114)
            MusicMood.LOFI_CHILL -> (78 + (energy - 35) / 3).coerceIn(72, 90)
        }

        val paceLabel = when {
            estimatedBpm >= 114 -> "Cepat & Dinamis"
            estimatedBpm >= 95 -> "Stabil & Terarah"
            else -> "Santai & Mengalir"
        }

        val techNote = buildString {
            if (telemetry.hasAudioTrack && telemetry.sampleRateHz != null) {
                append("Audio ${telemetry.sampleRateHz / 1000}kHz")
                if (telemetry.channelCount != null) append(" ${telemetry.channelCount}ch")
                append(" • ")
            } else if (!telemetry.hasAudioTrack) {
                append("Video tanpa audio asli • ")
            }
            append("Energi $energy% • $paceLabel (~$estimatedBpm BPM)")
        }

        return AudioAnalysisSummary(
            detectedMood = finalMood,
            estimatedBpm = estimatedBpm,
            energyPercent = energy,
            paceLabel = paceLabel,
            sampleRateHz = telemetry.sampleRateHz,
            channelCount = telemetry.channelCount,
            hasOriginalAudio = telemetry.hasAudioTrack,
            analysisSummary = techNote
        )
    }

    private fun extractTelemetry(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long
    ): RawAudioTelemetry {
        val extractor = MediaExtractor()
        return try {
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                extractor.setDataSource(uri.toString(), emptyMap())
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                return RawAudioTelemetry(hasAudioTrack = false)
            }

            extractor.selectTrack(audioTrackIndex)
            val sampleRate = runCatching { audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull()
            val channels = runCatching { audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrNull()
            val bitrate = runCatching { audioFormat.getInteger(MediaFormat.KEY_BIT_RATE) }.getOrNull()

            val startUs = max(0L, startMs) * 1000L
            val endUs = max(startUs + 1_000_000L, endMs * 1000L)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buffer = ByteBuffer.allocate(16 * 1024)
            val sizes = mutableListOf<Int>()
            var iterations = 0
            while (iterations < 90) {
                buffer.clear()
                val read = extractor.readSampleData(buffer, 0)
                if (read <= 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                sizes.add(read)
                iterations++
                if (!extractor.advance()) break
            }

            val variance = if (sizes.size >= 4) {
                val avg = sizes.average().toFloat().coerceAtLeast(1f)
                val sq = sizes.map { (it - avg) * (it - avg) }.average().toFloat()
                (sqrt(sq) / avg).coerceIn(0f, 1f)
            } else {
                0.22f
            }

            RawAudioTelemetry(
                hasAudioTrack = true,
                sampleRateHz = sampleRate,
                channelCount = channels,
                bitrateBps = bitrate,
                dynamicVariance = abs(variance),
                sampleCount = sizes.size
            )
        } catch (_: Exception) {
            RawAudioTelemetry(hasAudioTrack = true, dynamicVariance = 0.25f)
        } finally {
            runCatching { extractor.release() }
        }
    }
}
