package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.AiAnalysisResult
import com.example.domain.model.AiCaptionVariation
import com.example.domain.model.AiMusicRecommendationResult
import com.example.domain.model.AiSubtitleResult
import com.example.domain.model.AiVisualHookResult
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioAnalysisSummary
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipSegment
import com.example.domain.model.HookOverlayStyle
import com.example.domain.model.MusicMood
import com.example.domain.model.SubtitleCue
import com.example.domain.model.TargetPlatform
import com.example.media.RoyaltyFreeMusicCatalog
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

private const val TAG = "FirebaseAiClipper"
private const val AI_TIMEOUT_MS = 60_000L
private const val MODEL_NAME = "gemini-3.8-flash"

class FirebaseAiClipperRepository : AiClipperRepository {

    private val analysisSchema = Schema.obj(
        properties = mapOf(
            "clips" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Judul klip singkat berbahasa Indonesia"),
                        "hookLine" to Schema.string(description = "Hook pembuka 3 detik yang memancing penonton"),
                        "viralScore" to Schema.integer(description = "Skor potensi viral 75-99"),
                        "rationale" to Schema.string(description = "Alasan singkat pemilihan momen klip"),
                        "captionText" to Schema.string(description = "Caption sosial media siap posting"),
                        "hashtags" to Schema.string(description = "3-5 hashtag relevan dipisah spasi"),
                        "startMs" to Schema.integer(description = "Waktu mulai klip dalam milidetik"),
                        "endMs" to Schema.integer(description = "Waktu akhir klip dalam milidetik")
                    )
                ),
                description = "Daftar 4 sampai 5 klip sorotan terbaik dari video"
            )
        )
    )

    private val captionSchema = Schema.obj(
        properties = mapOf(
            "hookLine" to Schema.string(description = "Judul hook pembuka baru yang sesuai nada"),
            "captionText" to Schema.string(description = "Caption menarik berbahasa Indonesia"),
            "hashtags" to Schema.string(description = "Hashtag relevan dipisah spasi")
        )
    )

    private val subtitleSchema = Schema.obj(
        properties = mapOf(
            "summaryCaption" to Schema.string(description = "Ringkasan singkat subtitle"),
            "cues" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Waktu mulai relatif terhadap awal klip (ms)"),
                        "endMs" to Schema.integer(description = "Waktu akhir relatif terhadap awal klip (ms)"),
                        "text" to Schema.string(description = "Teks subtitle singkat 4-10 kata")
                    )
                )
            )
        )
    )

    private val musicSchema = Schema.obj(
        properties = mapOf(
            "recommendedMood" to Schema.enumeration(
                values = MusicMood.entries.map { it.name },
                description = "Mood musik paling cocok"
            ),
            "summaryNote" to Schema.string(description = "Catatan analisis tempo dan energi musik"),
            "trackReasons" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "trackId" to Schema.string(description = "ID track dari katalog yang diberikan"),
                        "rationale" to Schema.string(description = "Alasan kecocokan musik dengan klip")
                    )
                )
            )
        )
    )

    private val hookSchema = Schema.obj(
        properties = mapOf(
            "hookLine" to Schema.string(description = "Teks overlay 3 detik pertama yang singkat dan kuat"),
            "recommendedStyle" to Schema.enumeration(
                values = HookOverlayStyle.entries.map { it.name },
                description = "Gaya overlay visual paling kontras"
            ),
            "rationale" to Schema.string(description = "Alasan pemilihan hook dan gaya visual")
        )
    )

    private val analysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
            }
        )
    }

    private val captionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = captionSchema
            }
        )
    }

    private val subtitleModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = subtitleSchema
            }
        )
    }

    private val musicModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = musicSchema
            }
        )
    }

    private val hookModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = hookSchema
            }
        )
    }

    override suspend fun analyzeVideoForClips(
        projectId: Long,
        videoTitle: String,
        durationMs: Long,
        width: Int,
        height: Int,
        focusTopic: String,
        targetPlatform: TargetPlatform,
        sampleFrames: List<Bitmap>
    ): AiAnalysisResult {
        val safeDuration = max(durationMs, 6_000L)
        val cleanTopic = focusTopic.trim().ifEmpty { "momen paling menarik dan bernilai tinggi" }
        val maxClipMs = targetPlatform.maxRecommendedSec * 1000L
        val frames = prepareCompactFrames(sampleFrames, maxCount = 4)

        return runAiCall("analyzeVideoForClips") {
            val response = analysisModel.generateContent(
                content {
                    frames.forEach { image(it) }
                    text(
                        """
                        Kamu adalah editor video vertikal profesional untuk ${targetPlatform.displayName}.
                        Analisis judul video "$videoTitle" (durasi total $safeDuration ms, resolusi ${width}x$height) serta frame visual yang dilampirkan.
                        Fokus topik pengguna: "$cleanTopic".
                        Buat 4 sampai 5 klip pendek terbaik berbahasa Indonesia yang tidak melewati batas 0..$safeDuration ms (durasi per klip 3000..$maxClipMs ms).
                        Pastikan startMs < endMs, hookLine padat memancing 3 detik pertama, dan viralScore antara 78 hingga 99.
                        """.trimIndent()
                    )
                }
            )
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini AI mengembalikan respons kosong.")
            val root = JSONObject(rawJson)
            val clipsArray = root.optJSONArray("clips")
                ?: throw IllegalStateException("Format daftar klip Gemini tidak valid.")

            val parsedSegments = mutableListOf<ClipSegment>()
            val count = clipsArray.length()
            for (i in 0 until count) {
                val obj = clipsArray.optJSONObject(i) ?: continue
                val rawStart = obj.optLong("startMs", 0L).coerceIn(0L, (safeDuration - 2_000L).coerceAtLeast(0L))
                val rawEnd = obj.optLong("endMs", min(safeDuration, rawStart + maxClipMs))
                val clampedEnd = rawEnd
                    .coerceAtLeast(min(safeDuration, rawStart + 2_500L))
                    .coerceAtMost(min(safeDuration, rawStart + maxClipMs))
                parsedSegments += ClipSegment(
                    projectId = projectId,
                    title = obj.optString("title").ifBlank { "Klip AI #${i + 1}" },
                    hookLine = obj.optString("hookLine").ifBlank { "Sorotan utama tentang $cleanTopic" },
                    viralScore = obj.optInt("viralScore", 90).coerceIn(60, 99),
                    rationale = obj.optString("rationale").ifBlank { "Dipilih oleh Gemini AI berdasarkan alur visual & topik." },
                    captionText = obj.optString("captionText").ifBlank { "Momen pilihan dari $videoTitle." },
                    hashtags = obj.optString("hashtags").ifBlank { "#AldiAIClipper #Shorts #Reels" },
                    startMs = rawStart,
                    endMs = clampedEnd,
                    aspectRatio = targetPlatform.defaultRatio,
                    tone = CaptionTone.ENERGETIC,
                    showSubtitles = true,
                    isPlaceholderAi = false
                )
            }

            if (parsedSegments.isEmpty()) {
                throw IllegalStateException("Gemini AI tidak menghasilkan segmen klip.")
            }

            AiAnalysisResult(
                isPlaceholderMode = false,
                noticeMessage = "Analisis Gemini 3.8 Flash aktif: ${parsedSegments.size} klip ditemukan.",
                detectedClips = parsedSegments.sortedByDescending { it.viralScore }
            )
        }
    }

    override suspend fun regenerateHookAndCaption(
        clipTitle: String,
        currentCaption: String,
        focusTopic: String,
        tone: CaptionTone
    ): AiCaptionVariation {
        val topic = focusTopic.trim().ifEmpty { clipTitle }
        return runAiCall("regenerateHookAndCaption") {
            val response = captionModel.generateContent(
                """
                Buat ulang hook pembuka (maks 12 kata), caption sosial media berbahasa Indonesia (maks 2 kalimat), dan 3-5 hashtag relevan.
                Judul klip: "$clipTitle"
                Topik fokus: "$topic"
                Caption saat ini: "$currentCaption"
                Gaya bahasa yang diminta: ${tone.label} (${tone.name}).
                """.trimIndent()
            )
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini AI mengembalikan respons kosong.")
            val json = JSONObject(rawJson)
            AiCaptionVariation(
                isPlaceholderMode = false,
                hookLine = json.optString("hookLine").ifBlank { "Sorotan penting: $topic" },
                captionText = json.optString("captionText").ifBlank { currentCaption },
                hashtags = json.optString("hashtags").ifBlank { "#AldiAIClipper #ViralClip" }
            )
        }
    }

    override suspend fun generateTimedSubtitles(
        clipTitle: String,
        startMs: Long,
        endMs: Long,
        focusTopic: String,
        tone: CaptionTone,
        clipFrames: List<Bitmap>
    ): AiSubtitleResult {
        val clipDurationMs = (endMs - startMs).coerceAtLeast(2_000L)
        val topic = focusTopic.trim().ifEmpty { clipTitle }
        val frames = prepareCompactFrames(clipFrames, maxCount = 4)

        return runAiCall("generateTimedSubtitles") {
            val response = subtitleModel.generateContent(
                content {
                    frames.forEach { image(it) }
                    text(
                        """
                        Buat subtitle tersinkronisasi berbahasa Indonesia untuk klip "$clipTitle" (durasi $clipDurationMs ms).
                        Topik: "$topic", gaya bahasa: ${tone.label}.
                        Buat 3 sampai 5 baris cue berurutan dalam rentang waktu relatif 0 hingga $clipDurationMs ms (startMs < endMs).
                        """.trimIndent()
                    )
                }
            )
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini AI mengembalikan subtitle kosong.")
            val json = JSONObject(rawJson)
            val cuesArray = json.optJSONArray("cues")
                ?: throw IllegalStateException("Format subtitle Gemini tidak valid.")

            val cues = mutableListOf<SubtitleCue>()
            for (i in 0 until cuesArray.length()) {
                val obj = cuesArray.optJSONObject(i) ?: continue
                val cStart = obj.optLong("startMs", 0L).coerceIn(0L, (clipDurationMs - 600L).coerceAtLeast(0L))
                val cEnd = obj.optLong("endMs", clipDurationMs)
                    .coerceAtLeast(min(clipDurationMs, cStart + 700L))
                    .coerceAtMost(clipDurationMs)
                val line = obj.optString("text").trim()
                if (line.isNotEmpty()) {
                    cues += SubtitleCue(startMs = cStart, endMs = cEnd, text = line)
                }
            }

            if (cues.isEmpty()) {
                throw IllegalStateException("Gemini AI tidak menghasilkan baris subtitle.")
            }

            AiSubtitleResult(
                isPlaceholderMode = false,
                cues = cues,
                summaryCaption = json.optString("summaryCaption").ifBlank {
                    "${cues.size} baris subtitle Gemini AI siap tampil."
                }
            )
        }
    }

    override suspend fun recommendMusicForClip(
        audioAnalysis: AudioAnalysisSummary,
        clipTitle: String,
        focusTopic: String
    ): AiMusicRecommendationResult {
        val catalogIds = RoyaltyFreeMusicCatalog.catalog.joinToString(", ") {
            "${it.id} (${it.title}, ${it.mood.name}, ${it.bpm} BPM)"
        }
        return runAiCall("recommendMusicForClip") {
            val response = musicModel.generateContent(
                """
                Analisis klip "$clipTitle" (topik: "${focusTopic.ifBlank { clipTitle }}").
                Telemetri audio: mood=${audioAnalysis.detectedMood.name}, estimasiBPM=${audioAnalysis.estimatedBpm}, energi=${audioAnalysis.energyPercent}%.
                Katalog musik bebas royalti tersedia: $catalogIds.
                Pilih mood paling cocok dan beri alasan singkat berbahasa Indonesia untuk 3 track terbaik dari katalog.
                """.trimIndent()
            )
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini AI mengembalikan rekomendasi musik kosong.")
            val json = JSONObject(rawJson)
            val chosenMood = enumValues<MusicMood>()
                .firstOrNull { it.name == json.optString("recommendedMood") }
                ?: audioAnalysis.detectedMood

            val reasonsById = mutableMapOf<String, String>()
            val reasonsArr = json.optJSONArray("trackReasons")
            if (reasonsArr != null) {
                for (i in 0 until reasonsArr.length()) {
                    val item = reasonsArr.optJSONObject(i) ?: continue
                    val id = item.optString("trackId")
                    val reason = item.optString("rationale")
                    if (id.isNotBlank() && reason.isNotBlank()) {
                        reasonsById[id] = reason
                    }
                }
            }

            val ranked = RoyaltyFreeMusicCatalog.rankForMoodAndBpm(
                mood = chosenMood,
                targetBpm = audioAnalysis.estimatedBpm
            ).map { track ->
                val geminiNote = reasonsById[track.id]
                if (geminiNote != null) track.copy(rationale = geminiNote) else track
            }

            val summaryNote = json.optString("summaryNote")
                .ifBlank { "${audioAnalysis.analysisSummary} • Mood ${chosenMood.label}" }

            AiMusicRecommendationResult(
                isPlaceholderMode = false,
                audioAnalysis = audioAnalysis.copy(
                    detectedMood = chosenMood,
                    analysisSummary = summaryNote
                ),
                recommendedTracks = ranked
            )
        }
    }

    override suspend fun generateFirst3SecVisualHook(
        clipTitle: String,
        focusTopic: String,
        tone: CaptionTone,
        aspectRatio: AspectRatioPreset,
        introFrames: List<Bitmap>
    ): AiVisualHookResult {
        val topic = focusTopic.trim().ifEmpty { clipTitle }
        val frames = prepareCompactFrames(introFrames, maxCount = 3)

        return runAiCall("generateFirst3SecVisualHook") {
            val response = hookModel.generateContent(
                content {
                    frames.forEach { image(it) }
                    text(
                        """
                        Analisis 3 detik pembuka dari klip "$clipTitle" (rasio ${aspectRatio.label}, topik "$topic", nada ${tone.label}).
                        Berdasarkan kontras dan komposisi frame pembuka, buat teks visual hook maksimal 9 kata berbahasa Indonesia yang langsung menahan perhatian penonton, pilih gaya overlay paling kontras (${HookOverlayStyle.entries.joinToString { it.name }}), dan jelaskan alasan singkatnya.
                        """.trimIndent()
                    )
                }
            )
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini AI mengembalikan hook visual kosong.")
            val json = JSONObject(rawJson)
            val style = enumValues<HookOverlayStyle>()
                .firstOrNull { it.name == json.optString("recommendedStyle") }
                ?: HookOverlayStyle.NEON_BADGE

            AiVisualHookResult(
                isPlaceholderMode = false,
                hookLine = json.optString("hookLine").ifBlank { "Tunggu! Lihat bagian penting $topic ini" },
                recommendedStyle = style,
                rationale = json.optString("rationale").ifBlank {
                    "Dioptimalkan oleh Gemini 3.8 Flash untuk rasio ${aspectRatio.label}."
                }
            )
        }
    }

    private suspend inline fun <T> runAiCall(
        operation: String,
        crossinline block: suspend () -> T
    ): T {
        return try {
            withTimeout(AI_TIMEOUT_MS) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$operation timed out after ${AI_TIMEOUT_MS}ms", e)
            throw IllegalStateException("Permintaan Gemini AI melebihi batas waktu 60 detik. Coba lagi.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$operation failed", e)
            throw IllegalStateException(
                "Layanan Gemini AI sedang sibuk. Mencoba ulang secara otomatis…",
                e
            )
        }
    }

    private fun prepareCompactFrames(frames: List<Bitmap>, maxCount: Int): List<Bitmap> {
        if (frames.isEmpty()) return emptyList()
        return frames.take(maxCount).map { bmp ->
            val maxDim = max(bmp.width, bmp.height)
            if (maxDim <= 480) {
                bmp
            } else {
                val scale = 480f / maxDim.toFloat()
                val targetW = (bmp.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bmp.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
            }
        }
    }
}
