package com.example.data.network

import android.net.Uri
import com.example.domain.model.ResolvedSocialVideo
import com.example.domain.model.SocialSourcePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class SocialLinkResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private val URL_EXTRACT_REGEX = Regex("""https?://[^\s<>"'\]\)]+""", RegexOption.IGNORE_CASE)

        private val DIRECT_VIDEO_EXTENSIONS = listOf(".mp4", ".mov", ".webm", ".mkv", ".m3u8", ".mpd")
    }

    fun extractFirstUrl(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null
        val match = URL_EXTRACT_REGEX.find(trimmed)?.value
        if (match != null) {
            return match.trimEnd('.', ',', ';', '!', '?')
        }
        if (trimmed.contains('.') && !trimmed.contains(' ')) {
            return "https://$trimmed"
        }
        return null
    }

    fun detectPlatform(url: String): SocialSourcePlatform {
        val rawHost = runCatching { java.net.URI(url).host }.getOrNull()
            ?: runCatching { Uri.parse(url)?.host }.getOrNull()
            ?: ""
        val host = rawHost.lowercase(Locale.ROOT)
            .removePrefix("www.")
            .removePrefix("m.")

        val lowerUrl = url.lowercase(Locale.ROOT)
        return when {
            host.contains("youtube.com") || host.contains("youtu.be") -> SocialSourcePlatform.YOUTUBE
            host.contains("tiktok.com") -> SocialSourcePlatform.TIKTOK
            host.contains("instagram.com") || host.contains("instagr.am") -> SocialSourcePlatform.INSTAGRAM
            host.contains("facebook.com") || host.contains("fb.watch") || host.contains("fb.com") -> SocialSourcePlatform.FACEBOOK
            host == "x.com" || host.endsWith(".x.com") || host.contains("twitter.com") || host == "t.co" -> SocialSourcePlatform.X_TWITTER
            host.contains("threads.net") -> SocialSourcePlatform.THREADS
            host.contains("vimeo.com") -> SocialSourcePlatform.VIMEO
            DIRECT_VIDEO_EXTENSIONS.any { lowerUrl.substringBefore('?').endsWith(it) } -> SocialSourcePlatform.DIRECT_LINK
            else -> SocialSourcePlatform.OTHER_SOCIAL
        }
    }

    suspend fun resolveLink(rawInput: String): ResolvedSocialVideo = withContext(Dispatchers.IO) {
        val extractedUrl = extractFirstUrl(rawInput)
            ?: throw IllegalArgumentException("Masukkan tautan URL video yang valid (dimulai dengan https://).")

        val initialPlatform = detectPlatform(extractedUrl)

        // 1. Check if URL itself is a direct video stream via extension or Content-Type
        val headInfo = inspectUrlHeaders(extractedUrl)
        val finalUrl = headInfo.finalUrl.ifBlank { extractedUrl }
        val resolvedPlatform = detectPlatform(finalUrl).let {
            if (it == SocialSourcePlatform.OTHER_SOCIAL && initialPlatform != SocialSourcePlatform.OTHER_SOCIAL) {
                initialPlatform
            } else {
                it
            }
        }

        if (headInfo.isDirectVideo) {
            val fileName = Uri.parse(finalUrl).lastPathSegment
                ?.substringBefore('?')
                ?.substringBeforeLast('.')
                ?.ifBlank { null }
                ?: "Video_${resolvedPlatform.displayName}"
            return@withContext ResolvedSocialVideo(
                originalUrl = extractedUrl,
                resolvedPageUrl = finalUrl,
                platform = if (resolvedPlatform == SocialSourcePlatform.OTHER_SOCIAL) {
                    SocialSourcePlatform.DIRECT_LINK
                } else {
                    resolvedPlatform
                },
                title = fileName,
                playableStreamUrl = finalUrl,
                statusNote = "Stream video langsung terdeteksi dan siap diputar serta dipotong oleh Media3."
            )
        }

        // 2. Fetch official oEmbed metadata (YouTube, TikTok, Vimeo) + HTML OpenGraph/JSON-LD
        val oEmbed = fetchOEmbedIfSupported(finalUrl, resolvedPlatform)
        val htmlMeta = fetchHtmlMetadata(finalUrl)

        val candidateStream = htmlMeta.videoStreamUrl?.takeIf { candidate ->
            isLikelyPlayableVideoUrl(candidate) || inspectUrlHeaders(candidate).isDirectVideo
        }

        val resolvedTitle = oEmbed?.title?.takeIf { it.isNotBlank() }
            ?: htmlMeta.title?.takeIf { it.isNotBlank() }
            ?: "Video ${resolvedPlatform.displayName}"

        val resolvedAuthor = oEmbed?.authorName?.takeIf { it.isNotBlank() }
            ?: htmlMeta.authorName?.takeIf { it.isNotBlank() }

        val resolvedThumb = oEmbed?.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: htmlMeta.thumbnailUrl?.takeIf { it.isNotBlank() }

        val resolvedWidth = oEmbed?.width ?: htmlMeta.width ?: if (resolvedPlatform == SocialSourcePlatform.YOUTUBE) 1280 else 1080
        val resolvedHeight = oEmbed?.height ?: htmlMeta.height ?: if (resolvedPlatform == SocialSourcePlatform.YOUTUBE) 720 else 1920
        val resolvedDuration = (oEmbed?.durationSec ?: htmlMeta.durationSec)?.let { it * 1000L } ?: 60_000L

        val statusNote = if (candidateStream != null) {
            "Stream video dari ${resolvedPlatform.displayName} berhasil diambil dan siap diputar di ExoPlayer."
        } else {
            "Metadata & thumbnail ${resolvedPlatform.displayName} berhasil diambil. Platform ini melindungi stream MP4 langsung; Anda bisa membuat rencana klip AI sekarang atau menautkan file video lokal untuk render MP4."
        }

        ResolvedSocialVideo(
            originalUrl = extractedUrl,
            resolvedPageUrl = finalUrl,
            platform = resolvedPlatform,
            title = resolvedTitle,
            authorName = resolvedAuthor,
            thumbnailUrl = resolvedThumb,
            playableStreamUrl = candidateStream,
            durationMs = resolvedDuration,
            width = resolvedWidth,
            height = resolvedHeight,
            statusNote = statusNote
        )
    }

    private data class UrlHeaderInfo(
        val finalUrl: String,
        val contentType: String,
        val isDirectVideo: Boolean
    )

    private fun inspectUrlHeaders(url: String): UrlHeaderInfo {
        val lowerPath = url.lowercase(Locale.ROOT).substringBefore('?')
        if (DIRECT_VIDEO_EXTENSIONS.any { lowerPath.endsWith(it) }) {
            return UrlHeaderInfo(finalUrl = url, contentType = "video/mp4", isDirectVideo = true)
        }

        return runCatching {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", MOBILE_USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                val resolvedUrl = response.request.url.toString()
                val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                val isVideo = contentType.startsWith("video/") ||
                    contentType.contains("mpegurl") ||
                    DIRECT_VIDEO_EXTENSIONS.any {
                        resolvedUrl.lowercase(Locale.ROOT).substringBefore('?').endsWith(it)
                    }
                UrlHeaderInfo(resolvedUrl, contentType, isVideo)
            }
        }.getOrElse {
            UrlHeaderInfo(finalUrl = url, contentType = "", isDirectVideo = false)
        }
    }

    private data class OEmbedInfo(
        val title: String?,
        val authorName: String?,
        val thumbnailUrl: String?,
        val width: Int?,
        val height: Int?,
        val durationSec: Long?
    )

    private fun fetchOEmbedIfSupported(url: String, platform: SocialSourcePlatform): OEmbedInfo? {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val endpoint = when (platform) {
            SocialSourcePlatform.YOUTUBE -> "https://www.youtube.com/oembed?url=$encoded&format=json"
            SocialSourcePlatform.TIKTOK -> "https://www.tiktok.com/oembed?url=$encoded"
            SocialSourcePlatform.VIMEO -> "https://vimeo.com/api/oembed.json?url=$encoded"
            else -> return null
        }

        return runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("User-Agent", MOBILE_USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                OEmbedInfo(
                    title = json.optString("title").takeIf { it.isNotBlank() },
                    authorName = json.optString("author_name").takeIf { it.isNotBlank() },
                    thumbnailUrl = json.optString("thumbnail_url").takeIf { it.isNotBlank() },
                    width = json.optInt("width").takeIf { it > 0 },
                    height = json.optInt("height").takeIf { it > 0 },
                    durationSec = json.optLong("duration").takeIf { it > 0L }
                )
            }
        }.getOrNull()
    }

    private data class HtmlMetadata(
        val title: String? = null,
        val authorName: String? = null,
        val thumbnailUrl: String? = null,
        val videoStreamUrl: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val durationSec: Long? = null
    )

    private fun fetchHtmlMetadata(url: String): HtmlMetadata {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use HtmlMetadata()
                val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (!contentType.contains("text/html") && !contentType.contains("application/xhtml")) {
                    return@use HtmlMetadata()
                }
                val rawBytes = response.body?.byteStream()?.use { stream ->
                    val buffer = ByteArray(512 * 1024)
                    var total = 0
                    while (total < buffer.size) {
                        val read = stream.read(buffer, total, buffer.size - total)
                        if (read <= 0) break
                        total += read
                    }
                    String(buffer, 0, total, Charsets.UTF_8)
                } ?: return@use HtmlMetadata()

                val title = extractMetaContent(rawBytes, "og:title")
                    ?: extractMetaContent(rawBytes, "twitter:title")
                    ?: extractHtmlTitle(rawBytes)
                val author = extractMetaContent(rawBytes, "author")
                    ?: extractMetaContent(rawBytes, "og:site_name")
                val thumbnail = extractMetaContent(rawBytes, "og:image:secure_url")
                    ?: extractMetaContent(rawBytes, "og:image")
                    ?: extractMetaContent(rawBytes, "twitter:image")

                val ogVideo = extractMetaContent(rawBytes, "og:video:secure_url")
                    ?: extractMetaContent(rawBytes, "og:video:url")
                    ?: extractMetaContent(rawBytes, "og:video")
                    ?: extractMetaContent(rawBytes, "twitter:player:stream")
                    ?: extractJsonField(rawBytes, "contentUrl")
                    ?: extractVideoTagSrc(rawBytes)

                val width = extractMetaContent(rawBytes, "og:video:width")?.toIntOrNull()
                val height = extractMetaContent(rawBytes, "og:video:height")?.toIntOrNull()
                val duration = extractMetaContent(rawBytes, "video:duration")?.toLongOrNull()

                HtmlMetadata(
                    title = title?.let(::decodeHtmlEntities),
                    authorName = author?.let(::decodeHtmlEntities),
                    thumbnailUrl = thumbnail?.let(::decodeHtmlEntities),
                    videoStreamUrl = ogVideo?.let(::decodeHtmlEntities),
                    width = width,
                    height = height,
                    durationSec = duration
                )
            }
        }.getOrDefault(HtmlMetadata())
    }

    private fun extractMetaContent(html: String, key: String): String? {
        val escapedKey = Regex.escape(key)
        val p1 = Regex(
            """<meta[^>]+(?:property|name)\s*=\s*["']$escapedKey["'][^>]+content\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val p2 = Regex(
            """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["']$escapedKey["']""",
            RegexOption.IGNORE_CASE
        )
        return p1.find(html)?.groupValues?.getOrNull(1)
            ?: p2.find(html)?.groupValues?.getOrNull(1)
    }

    private fun extractHtmlTitle(html: String): String? {
        val regex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractJsonField(html: String, field: String): String? {
        val regex = Regex(""""$field"\s*:\s*"(https?:[^"]+)"""")
        return regex.find(html)?.groupValues?.getOrNull(1)?.replace("\\/", "/")
    }

    private fun extractVideoTagSrc(html: String): String? {
        val videoRegex = Regex("""<(?:video|source)[^>]+src\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        return videoRegex.find(html)?.groupValues?.getOrNull(1)
    }

    private fun isLikelyPlayableVideoUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT).substringBefore('?')
        if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.contains("/embed/")) {
            return false
        }
        return DIRECT_VIDEO_EXTENSIONS.any { lower.endsWith(it) } ||
            url.contains("mime=video", ignoreCase = true)
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\\u0026", "&")
            .trim()
    }
}
