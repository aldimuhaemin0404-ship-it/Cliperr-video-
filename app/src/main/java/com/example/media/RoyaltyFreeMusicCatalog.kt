package com.example.media

import android.content.Context
import android.net.Uri
import com.example.domain.model.MusicMood
import com.example.domain.model.MusicTrackRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

object RoyaltyFreeMusicCatalog {

    const val SYNTH_SCHEME = "synth://"

    val catalog: List<MusicTrackRecommendation> = listOf(
        MusicTrackRecommendation(
            id = "rf_neon_pulse",
            title = "Neon Horizon Pulse",
            artist = "Aldi Studio Synth (CC0)",
            mood = MusicMood.UPBEAT,
            bpm = 124,
            durationSec = 24,
            licenseLabel = "Bebas Royalti • CC0 Synth",
            audioUrl = "${SYNTH_SCHEME}rf_neon_pulse"
        ),
        MusicTrackRecommendation(
            id = "rf_viral_spark",
            title = "Viral Spark Beat",
            artist = "CreatorWave Audio (CC0)",
            mood = MusicMood.UPBEAT,
            bpm = 118,
            durationSec = 24,
            licenseLabel = "Bebas Royalti • CC0 Synth",
            audioUrl = "${SYNTH_SCHEME}rf_viral_spark"
        ),
        MusicTrackRecommendation(
            id = "rf_clarity_focus",
            title = "Digital Clarity Flow",
            artist = "StudioAmbient (CC0)",
            mood = MusicMood.CORPORATE,
            bpm = 104,
            durationSec = 24,
            licenseLabel = "Bebas Royalti • CC0 Synth",
            audioUrl = "${SYNTH_SCHEME}rf_clarity_focus"
        ),
        MusicTrackRecommendation(
            id = "rf_clean_tech",
            title = "Clean Tech Brief",
            artist = "MetroAudio Lab (CC0)",
            mood = MusicMood.CORPORATE,
            bpm = 98,
            durationSec = 24,
            licenseLabel = "Bebas Royalti • CC0 Synth",
            audioUrl = "${SYNTH_SCHEME}rf_clean_tech"
        ),
        MusicTrackRecommendation(
            id = "rf_cosmic_lofi",
            title = "Midnight Cosmic Lo-Fi",
            artist = "ChillOrbit (CC0)",
            mood = MusicMood.LOFI_CHILL,
            bpm = 82,
            durationSec = 24,
            licenseLabel = "Bebas Royalti • CC0 Synth",
            audioUrl = "${SYNTH_SCHEME}rf_cosmic_lofi"
        ),
        MusicTrackRecommendation(
            id = "rf_soft_breeze",
            title = "Velvet Sky Rhodes",
            artist = "DreamPad Collective (CC0)",
            mood = MusicMood.LOFI_CHILL,
            bpm = 76,
            durationSec = 24,
            licenseLabel = "Bebas Royalti • CC0 Synth",
            audioUrl = "${SYNTH_SCHEME}rf_soft_breeze"
        ),
        MusicTrackRecommendation(
            id = "rf_epic_climax",
            title = "Stadium Rising Climax",
            artist = "ApexTrailer Sound (CC0)",
            mood = MusicMood.DRAMATIC,
            bpm = 108,
            durationSec = 24,
            licenseLabel = "Bebas Royalti • CC0 Synth",
            audioUrl = "${SYNTH_SCHEME}rf_epic_climax"
        ),
        MusicTrackRecommendation(
            id = "rf_cyber_tension",
            title = "Cyber Arena Tension",
            artist = "SynthForge (CC0)",
            mood = MusicMood.DRAMATIC,
            bpm = 112,
            durationSec = 24,
            licenseLabel = "Bebas Royalti • CC0 Synth",
            audioUrl = "${SYNTH_SCHEME}rf_cyber_tension"
        )
    )

    fun rankForMoodAndBpm(
        mood: MusicMood,
        targetBpm: Int
    ): List<MusicTrackRecommendation> {
        return catalog.map { track ->
            val moodBonus = if (track.mood == mood) 35 else 10
            val bpmDiff = kotlin.math.abs(track.bpm - targetBpm)
            val bpmScore = (30 - bpmDiff).coerceIn(0, 30)
            val totalScore = (64 + moodBonus / 2 + bpmScore / 2).coerceIn(72, 99)
            val reason = if (track.mood == mood) {
                "Cocok dengan mood ${mood.label} & tempo ~${track.bpm} BPM"
            } else {
                "Alternatif ritme ${track.bpm} BPM (${track.mood.label})"
            }
            track.copy(matchScore = totalScore, rationale = reason)
        }.sortedByDescending { it.matchScore }
    }

    fun isBuiltInSynthUrl(url: String?): Boolean =
        url != null && url.startsWith(SYNTH_SCHEME)

    fun buildCustomUrlTrack(rawUrl: String): MusicTrackRecommendation? {
        val clean = rawUrl.trim()
        if (clean.isBlank()) return null
        val normalized = if (
            clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("content://", ignoreCase = true) ||
            clean.startsWith("file://", ignoreCase = true) ||
            clean.startsWith(SYNTH_SCHEME)
        ) {
            clean
        } else {
            "https://$clean"
        }

        val parsedUri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return null
        val path = parsedUri.path.orEmpty()
        val lastSeg = path.trimEnd('/').substringAfterLast('/').ifBlank { null }
        val fileName = lastSeg
            ?.substringBefore('?')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.ifBlank { null }
            ?: "Musik URL Kustom"

        return MusicTrackRecommendation(
            id = "custom_${normalized.hashCode()}",
            title = fileName.take(36),
            artist = parsedUri.host ?: "URL Audio Eksternal",
            mood = MusicMood.UPBEAT,
            bpm = 110,
            durationSec = 30,
            licenseLabel = "URL Kustom Pengguna",
            audioUrl = normalized,
            isCustomUrl = true,
            matchScore = 100,
            rationale = "Ditambahkan langsung lewat URL audio kustom"
        )
    }

    suspend fun resolvePlayableAudioUri(
        context: Context,
        audioUrl: String?,
        trackId: String?,
        minDurationMs: Long = 24_000L
    ): Uri? = withContext(Dispatchers.IO) {
        if (audioUrl.isNullOrBlank()) return@withContext null
        if (!isBuiltInSynthUrl(audioUrl)) {
            return@withContext Uri.parse(audioUrl)
        }

        val resolvedId = trackId ?: audioUrl.removePrefix(SYNTH_SCHEME)
        val track = catalog.firstOrNull { it.id == resolvedId } ?: catalog.first()
        val durationSec = max(track.durationSec, ((minDurationMs + 999L) / 1000L).toInt()).coerceIn(12, 95)

        val dir = File(context.filesDir, "royalty_free_music").apply {
            if (!exists()) mkdirs()
        }
        val targetFile = File(dir, "${track.id}_${durationSec}s.wav")
        if (!targetFile.exists() || targetFile.length() < 4096L) {
            synthesizeWavFile(
                file = targetFile,
                track = track,
                durationSec = durationSec
            )
        }
        Uri.fromFile(targetFile)
    }

    private fun synthesizeWavFile(
        file: File,
        track: MusicTrackRecommendation,
        durationSec: Int
    ) {
        val sampleRate = 22050
        val channels = 2
        val totalSamples = sampleRate * durationSec
        val dataSizeBytes = totalSamples * channels * 2

        val chords = when (track.mood) {
            MusicMood.UPBEAT -> arrayOf(
                doubleArrayOf(261.63, 329.63, 392.00, 523.25),
                doubleArrayOf(196.00, 246.94, 293.66, 392.00),
                doubleArrayOf(220.00, 261.63, 329.63, 440.00),
                doubleArrayOf(174.61, 220.00, 261.63, 349.23)
            )
            MusicMood.CORPORATE -> arrayOf(
                doubleArrayOf(220.00, 277.18, 329.63),
                doubleArrayOf(196.00, 246.94, 293.66),
                doubleArrayOf(164.81, 207.65, 246.94),
                doubleArrayOf(174.61, 220.00, 261.63)
            )
            MusicMood.LOFI_CHILL -> arrayOf(
                doubleArrayOf(220.00, 261.63, 329.63, 392.00),
                doubleArrayOf(174.61, 220.00, 261.63, 329.63),
                doubleArrayOf(146.83, 174.61, 220.00, 261.63),
                doubleArrayOf(164.81, 196.00, 246.94, 293.66)
            )
            MusicMood.DRAMATIC -> arrayOf(
                doubleArrayOf(146.83, 220.00, 293.66),
                doubleArrayOf(130.81, 196.00, 261.63),
                doubleArrayOf(116.54, 174.61, 233.08),
                doubleArrayOf(130.81, 196.00, 293.66)
            )
        }

        val beatDurationSec = 60.0 / track.bpm.coerceIn(70, 140)
        BufferedOutputStream(FileOutputStream(file)).use { out ->
            // RIFF WAV header (44 bytes)
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.writeIntLE(36 + dataSizeBytes)
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.writeIntLE(16)
            out.writeShortLE(1) // PCM
            out.writeShortLE(channels)
            out.writeIntLE(sampleRate)
            out.writeIntLE(sampleRate * channels * 2)
            out.writeShortLE(channels * 2)
            out.writeShortLE(16)
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.writeIntLE(dataSizeBytes)

            val chunkBuffer = ByteArray(4096)
            var bufIdx = 0
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val beatPos = (t / beatDurationSec)
                val beatIndex = beatPos.toInt()
                val beatFrac = beatPos - beatIndex
                val chord = chords[(beatIndex / 4) % chords.size]

                var pad = 0.0
                for (noteIdx in chord.indices) {
                    val freq = chord[noteIdx]
                    pad += sin(2.0 * PI * freq * t) * 0.22
                }

                // Soft rhythmic pulse matching BPM
                val arpNote = chord[beatIndex % chord.size] * if (track.mood == MusicMood.UPBEAT) 2.0 else 1.0
                val pluckEnv = exp(-beatFrac * 5.2)
                val pluck = sin(2.0 * PI * arpNote * t) * pluckEnv * 0.34

                // Soft sub bass pulse on beats 0 and 2
                val kickEnv = if (beatIndex % 2 == 0) exp(-beatFrac * 9.0) else 0.0
                val bass = sin(2.0 * PI * (chord[0] * 0.5) * t) * kickEnv * 0.30

                // Gentle fade-in / fade-out
                val fadeIn = (t / 0.6).coerceIn(0.0, 1.0)
                val fadeOut = ((durationSec - t) / 0.8).coerceIn(0.0, 1.0)
                val master = 0.48 * fadeIn * fadeOut

                val left = ((pad + pluck * 0.9 + bass) * master).coerceIn(-0.95, 0.95)
                val right = ((pad + pluck * 1.1 + bass) * master).coerceIn(-0.95, 0.95)

                val lPcm = (left * 32767.0).toInt().coerceIn(-32768, 32767)
                val rPcm = (right * 32767.0).toInt().coerceIn(-32768, 32767)

                chunkBuffer[bufIdx++] = (lPcm and 0xFF).toByte()
                chunkBuffer[bufIdx++] = ((lPcm ushr 8) and 0xFF).toByte()
                chunkBuffer[bufIdx++] = (rPcm and 0xFF).toByte()
                chunkBuffer[bufIdx++] = ((rPcm ushr 8) and 0xFF).toByte()

                if (bufIdx == chunkBuffer.size) {
                    out.write(chunkBuffer)
                    bufIdx = 0
                }
            }
            if (bufIdx > 0) {
                out.write(chunkBuffer, 0, bufIdx)
            }
            out.flush()
        }
    }

    private fun BufferedOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun BufferedOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }
}
