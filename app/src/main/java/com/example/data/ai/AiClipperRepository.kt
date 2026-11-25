package com.example.data.ai

import android.graphics.Bitmap
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
import com.example.domain.model.SubtitleCue
import com.example.domain.model.TargetPlatform
import com.example.media.RoyaltyFreeMusicCatalog
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

interface AiClipperRepository {
    suspend fun analyzeVideoForClips(
        projectId: Long,
        videoTitle: String,
        durationMs: Long,
        width: Int,
        height: Int,
        focusTopic: String,
        targetPlatform: TargetPlatform,
        sampleFrames: List<Bitmap>
    ): AiAnalysisResult

    suspend fun regenerateHookAndCaption(
        clipTitle: String,
        currentCaption: String,
        focusTopic: String,
        tone: CaptionTone
    ): AiCaptionVariation

    suspend fun generateTimedSubtitles(
        clipTitle: String,
        startMs: Long,
        endMs: Long,
        focusTopic: String,
        tone: CaptionTone,
        clipFrames: List<Bitmap>
    ): AiSubtitleResult

    suspend fun recommendMusicForClip(
        audioAnalysis: AudioAnalysisSummary,
        clipTitle: String,
        focusTopic: String
    ): AiMusicRecommendationResult

    suspend fun generateFirst3SecVisualHook(
        clipTitle: String,
        focusTopic: String,
        tone: CaptionTone,
        aspectRatio: AspectRatioPreset,
        introFrames: List<Bitmap>
    ): AiVisualHookResult
}

/**
 * Placeholder implementation used when Firebase AI Logic is not enabled.
 * Generates clearly labeled sample AI clip recommendations scaled to the
 * selected video's actual duration.
 */
class PlaceholderAiClipperRepository : AiClipperRepository {

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
        delay(900L)

        val safeDuration = max(durationMs, 6_000L)
        val cleanTopic = focusTopic.trim().ifEmpty { "momen utama" }
        val ratio = targetPlatform.defaultRatio

        val windows = buildProportionalWindows(
            totalMs = safeDuration,
            maxClipSec = targetPlatform.maxRecommendedSec
        )

        val templates = listOf(
            SampleClipTemplate(
                title = "[Sampel AI] Hook Pembuka Paling Kuat",
                hookLine = "Jangan lewatkan bagian penting soal $cleanTopic ini!",
                viralScore = 98,
                rationale = "Sampel analisis: Puncak energi visual dan hook awal cocok untuk menahan perhatian 3 detik pertama di ${targetPlatform.displayName}.",
                caption = "Bagian paling krusial dari \"$videoTitle\" tentang $cleanTopic. Simpan dulu biar gak lupa!",
                hashtags = "#AldiAIClipper #SampelAI #ReelsIndonesia #ShortsViral"
            ),
            SampleClipTemplate(
                title = "[Sampel AI] Insight & Poin Inti",
                hookLine = "Ternyata ini alasan di balik $cleanTopic yang jarang dibahas",
                viralScore = 95,
                rationale = "Sampel analisis: Ritme penyampaian padat dan informatif dengan potensi tinggi untuk di-share ulang.",
                caption = "Ringkasan padat seputar $cleanTopic dalam durasi singkat siap upload ke ${targetPlatform.displayName}.",
                hashtags = "#TipsKreator #KontenPendek #SampelAI #FYP"
            ),
            SampleClipTemplate(
                title = "[Sampel AI] Momen Klimaks & Reaksi",
                hookLine = "Lihat detik-detik perubahan paling drastis di video ini!",
                viralScore = 93,
                rationale = "Sampel analisis: Kontras visual antar frame (${sampleFrames.size} frame sampel dipindai) menandakan transisi menarik.",
                caption = "Potongan klimaks terbaik dari $videoTitle. Menurut kamu gimana? Tulis di komentar!",
                hashtags = "#HighlightVideo #CreatorStudio #SampelAI"
            ),
            SampleClipTemplate(
                title = "[Sampel AI] Aksi Paling Ikonik",
                hookLine = "Detik yang bikin penonton langsung replay berkali-kali!",
                viralScore = 91,
                rationale = "Sampel analisis: Komposisi gerakan cepat dan fokus subjek ideal untuk feed vertikal ${ratio.label}.",
                caption = "Momen favorit penonton dari $videoTitle tentang $cleanTopic.",
                hashtags = "#ViralMoment #KlipTerbaik #SampelAI"
            ),
            SampleClipTemplate(
                title = "[Sampel AI] Penutup & Call-to-Action",
                hookLine = "Kesimpulan cepat yang langsung bisa kamu praktikkan hari ini",
                viralScore = 89,
                rationale = "Sampel analisis: Penutup ringkas berdurasi ideal untuk loop otomatis pada format ${ratio.label}.",
                caption = "Penutup singkat tapi mengena soal $cleanTopic. Follow untuk bagian berikutnya!",
                hashtags = "#VideoShorts #EditingCepat #SampelAI"
            )
        )

        val segments = windows.mapIndexed { index, (start, end) ->
            val template = templates[index % templates.size]
            ClipSegment(
                projectId = projectId,
                title = template.title,
                hookLine = template.hookLine,
                viralScore = template.viralScore,
                rationale = template.rationale,
                captionText = template.caption,
                hashtags = template.hashtags,
                startMs = start,
                endMs = end,
                aspectRatio = ratio,
                tone = CaptionTone.ENERGETIC,
                showSubtitles = true,
                isPlaceholderAi = true
            )
        }

        return AiAnalysisResult(
            isPlaceholderMode = true,
            noticeMessage = "Mode Sampel AI: Rekomendasi klip dihasilkan secara lokal sebagai contoh. Aktifkan Firebase AI Logic untuk analisis video Gemini yang sesungguhnya.",
            detectedClips = segments
        )
    }

    override suspend fun regenerateHookAndCaption(
        clipTitle: String,
        currentCaption: String,
        focusTopic: String,
        tone: CaptionTone
    ): AiCaptionVariation {
        delay(450L)
        val topic = focusTopic.trim().ifEmpty { "momen ini" }
        val (hook, caption, tags) = when (tone) {
            CaptionTone.ENERGETIC -> Triple(
                "[Sampel AI] Stop scroll! Ini bagian terbaik soal $topic!",
                "Energi penuh dari \"$clipTitle\" — wajib tonton sampai detik terakhir!",
                "#Enerjik #ViralClip #SampelAI #Reels"
            )
            CaptionTone.PROFESSIONAL -> Triple(
                "[Sampel AI] Poin strategis utama mengenai $topic",
                "Ringkasan terstruktur dan jelas untuk audiens profesional: sorotan inti dari $topic.",
                "#InsightBisnis #Profesional #SampelAI"
            )
            CaptionTone.HUMOROUS -> Triple(
                "[Sampel AI] Jujur, bagian $topic ini relate banget sih",
                "Baru sadar ternyata begini rasanya pas bahas $topic. Tag teman kamu yang persis begini!",
                "#RelateBanget #Santai #SampelAI #FYP"
            )
            CaptionTone.EDUCATIONAL -> Triple(
                "[Sampel AI] 1 pelajaran cepat tentang $topic dalam hitungan detik",
                "Simpan klip edukatif ini supaya kamu bisa langsung menerapkan poin utama tentang $topic.",
                "#BelajarCepat #Edukasi #SampelAI #Shorts"
            )
        }

        return AiCaptionVariation(
            isPlaceholderMode = true,
            hookLine = hook,
            captionText = caption,
            hashtags = tags
        )
    }

    override suspend fun generateTimedSubtitles(
        clipTitle: String,
        startMs: Long,
        endMs: Long,
        focusTopic: String,
        tone: CaptionTone,
        clipFrames: List<Bitmap>
    ): AiSubtitleResult {
        delay(650L)
        val clipDurationMs = (endMs - startMs).coerceAtLeast(2_000L)
        val cleanTopic = focusTopic.trim().ifEmpty {
            clipTitle.removePrefix("[Sampel AI]").trim().ifEmpty { "momen ini" }
        }
        val frameHint = if (clipFrames.isNotEmpty()) "${clipFrames.size} frame" else "metadata klip"

        val cueTemplates = when (tone) {
            CaptionTone.ENERGETIC -> listOf(
                "[Sampel AI] Jangan skip! Ini bagian paling seru soal $cleanTopic.",
                "[Sampel AI] Perhatikan detail pentingnya di $frameHint ini!",
                "[Sampel AI] Perubahan besarnya langsung kelihatan di momen ini.",
                "[Sampel AI] Simpan klipnya biar gak ketinggalan poin intinya!",
                "[Sampel AI] Follow buat lihat lanjutan lengkapnya ya!"
            )
            CaptionTone.PROFESSIONAL -> listOf(
                "[Sampel AI] Berikut ringkasan inti mengenai $cleanTopic.",
                "[Sampel AI] Data visual dari $frameHint menunjukkan fokus utama.",
                "[Sampel AI] Langkah ini membantu meningkatkan hasil secara konsisten.",
                "[Sampel AI] Pastikan poin utama ini diterapkan pada alur kerja Anda.",
                "[Sampel AI] Bagikan klip ini kepada rekan tim Anda."
            )
            CaptionTone.HUMOROUS -> listOf(
                "[Sampel AI] Jujur, pas bahas $cleanTopic tuh selalu kejadian begini.",
                "[Sampel AI] Coba lihat ekspresi dan detail di $frameHint ini deh.",
                "[Sampel AI] Bagian paling relate yang sering bikin senyum sendiri.",
                "[Sampel AI] Kalian tim yang setuju atau masih mikir dua kali?",
                "[Sampel AI] Tag teman kamu yang paling butuh lihat klip ini!"
            )
            CaptionTone.EDUCATIONAL -> listOf(
                "[Sampel AI] Poin pertama tentang $cleanTopic yang wajib dicatat.",
                "[Sampel AI] Dari $frameHint terlihat alur penjelasannya bertahap.",
                "[Sampel AI] Tips ini bikin prosesnya jauh lebih singkat dan jelas.",
                "[Sampel AI] Ulangi bagian ini kalau mau langsung praktik.",
                "[Sampel AI] Simpan untuk referensi belajar cepat berikutnya."
            )
        }

        val targetCueCount = when {
            clipDurationMs <= 5_000L -> 2
            clipDurationMs <= 10_000L -> 3
            clipDurationMs <= 18_000L -> 4
            else -> 5
        }
        val stepMs = (clipDurationMs / targetCueCount).coerceAtLeast(1_000L)
        val cues = (0 until targetCueCount).map { idx ->
            val cueStart = idx * stepMs
            val cueEnd = if (idx == targetCueCount - 1) clipDurationMs else ((idx + 1) * stepMs - 100L).coerceAtLeast(cueStart + 800L)
            SubtitleCue(
                startMs = cueStart,
                endMs = cueEnd,
                text = cueTemplates[idx % cueTemplates.size]
            )
        }

        return AiSubtitleResult(
            isPlaceholderMode = true,
            cues = cues,
            summaryCaption = "[Sampel AI] ${cues.size} baris subtitle otomatis siap tampil."
        )
    }

    override suspend fun recommendMusicForClip(
        audioAnalysis: AudioAnalysisSummary,
        clipTitle: String,
        focusTopic: String
    ): AiMusicRecommendationResult {
        delay(550L)
        val topicHint = focusTopic.trim().ifEmpty {
            clipTitle.removePrefix("[Sampel AI]").trim().ifEmpty { "klip ini" }
        }
        val ranked = RoyaltyFreeMusicCatalog.rankForMoodAndBpm(
            mood = audioAnalysis.detectedMood,
            targetBpm = audioAnalysis.estimatedBpm
        ).mapIndexed { idx, track ->
            val aiNote = if (idx == 0) {
                "[Sampel AI] Pilihan utama: tempo ${track.bpm} BPM selaras dengan ritme \"$topicHint\"."
            } else {
                "[Sampel AI] ${track.rationale}."
            }
            track.copy(rationale = aiNote)
        }

        val enrichedAnalysis = audioAnalysis.copy(
            analysisSummary = "[Sampel AI] ${audioAnalysis.analysisSummary} • Mood ${audioAnalysis.detectedMood.label}"
        )

        return AiMusicRecommendationResult(
            isPlaceholderMode = true,
            audioAnalysis = enrichedAnalysis,
            recommendedTracks = ranked
        )
    }

    override suspend fun generateFirst3SecVisualHook(
        clipTitle: String,
        focusTopic: String,
        tone: CaptionTone,
        aspectRatio: AspectRatioPreset,
        introFrames: List<Bitmap>
    ): AiVisualHookResult {
        delay(500L)
        val cleanTopic = focusTopic.trim().ifEmpty {
            clipTitle.removePrefix("[Sampel AI]").trim().ifEmpty { "momen ini" }
        }
        val avgLuma = computeAverageLuminance(introFrames)
        val recommendedStyle = when {
            avgLuma >= 145 -> HookOverlayStyle.CYBER_BANNER
            tone == CaptionTone.ENERGETIC || tone == CaptionTone.HUMOROUS -> HookOverlayStyle.NEON_BADGE
            else -> HookOverlayStyle.MINIMAL_BOLD
        }

        val hookText = when (tone) {
            CaptionTone.ENERGETIC -> "[Sampel AI] TUNGGU! Lihat $cleanTopic dalam 3 detik ini 🔥"
            CaptionTone.PROFESSIONAL -> "[Sampel AI] Poin Penting: $cleanTopic (${aspectRatio.label})"
            CaptionTone.HUMOROUS -> "[Sampel AI] 3 Detik Pertama Langsung Relate: $cleanTopic!"
            CaptionTone.EDUCATIONAL -> "[Sampel AI] Wajib Tahu Soal $cleanTopic 💡"
        }

        val frameDesc = if (introFrames.isNotEmpty()) {
            "${introFrames.size} frame awal dianalisis (luminansi $avgLuma/255)"
        } else {
            "komposisi visual ${aspectRatio.label}"
        }

        return AiVisualHookResult(
            isPlaceholderMode = true,
            hookLine = hookText,
            recommendedStyle = recommendedStyle,
            rationale = "[Sampel AI] Overlay hook 3 detik pertama dioptimalkan dari $frameDesc dengan gaya ${recommendedStyle.label}."
        )
    }

    private fun computeAverageLuminance(frames: List<Bitmap>): Int {
        if (frames.isEmpty()) return 110
        var total = 0L
        var count = 0
        frames.forEach { bmp ->
            val w = bmp.width.coerceAtLeast(1)
            val h = bmp.height.coerceAtLeast(1)
            val stepX = max(1, w / 8)
            val stepY = max(1, h / 8)
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val px = bmp.getPixel(x, y)
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    total += (299 * r + 587 * g + 114 * b) / 1000
                    count++
                    x += stepX
                }
                y += stepY
            }
        }
        return if (count > 0) (total / count).toInt().coerceIn(0, 255) else 110
    }

    private fun buildProportionalWindows(
        totalMs: Long,
        maxClipSec: Int
    ): List<Pair<Long, Long>> {
        val maxWindowMs = maxClipSec * 1000L
        if (totalMs <= 12_000L) {
            val step = (totalMs / 5L).coerceAtLeast(1_500L)
            return (0 until 5).map { idx ->
                val s = (idx * step).coerceAtMost((totalMs - 2_000L).coerceAtLeast(0L))
                val e = min(totalMs, s + max(2_500L, step + 1_200L))
                s to e
            }
        }

        val fractions = listOf(
            0.02f to 0.22f,
            0.20f to 0.42f,
            0.40f to 0.62f,
            0.60f to 0.82f,
            0.78f to 0.98f
        )

        return fractions.mapNotNull { (startFrac, endFrac) ->
            val rawStart = (totalMs * startFrac).toLong()
            val rawEnd = (totalMs * endFrac).toLong()
            val clampedEnd = min(totalMs, min(rawEnd, rawStart + maxWindowMs))
            if (clampedEnd - rawStart >= 2_000L) {
                rawStart to clampedEnd
            } else {
                null
            }
        }.ifEmpty {
            listOf(0L to min(totalMs, maxWindowMs))
        }
    }

    private data class SampleClipTemplate(
        val title: String,
        val hookLine: String,
        val viralScore: Int,
        val rationale: String,
        val caption: String,
        val hashtags: String
    )
}
