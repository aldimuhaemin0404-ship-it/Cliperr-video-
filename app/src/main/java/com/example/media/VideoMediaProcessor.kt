package com.example.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.network.UrlSecurityValidator
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportQuality
import com.example.domain.model.ExportedClip
import com.example.domain.model.VideoMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

class VideoMediaProcessor(private val context: Context) {

    private val thumbnailCache = object : LruCache<String, List<Bitmap>>(12) {}

    private val sourceDownloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .dns(UrlSecurityValidator.safeDns)
            .addInterceptor(UrlSecurityValidator.safeApplicationInterceptor)
            .addNetworkInterceptor(UrlSecurityValidator.safeNetworkInterceptor)
            .build()
    }

    suspend fun resolveDurableLocalUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "file" || scheme == "android.resource") return@withContext uri
        if (scheme != "content") return@withContext uri

        val persistableGranted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        }.getOrDefault(false)

        val authority = uri.authority.orEmpty().lowercase()
        val isTempPickerUri = authority.contains("picker") || authority.contains("photopicker")
        if (persistableGranted && !isTempPickerUri) {
            return@withContext uri
        }

        val reportedSize = queryOpenableSize(context.contentResolver, uri)
        val maxImportBytes = 450L * 1024L * 1024L
        if (reportedSize != null && reportedSize > maxImportBytes) {
            return@withContext uri
        }

        val importsDir = File(context.filesDir, "imports").apply {
            if (!exists()) mkdirs()
        }.canonicalFile
        pruneDirectoryBySize(importsDir, maxTotalBytes = 600L * 1024L * 1024L)

        val rawName = queryDisplayName(context.contentResolver, uri) ?: "video_${System.currentTimeMillis()}.mp4"
        val safeName = rawName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(48).ifBlank { "video.mp4" }
        val ext = if (safeName.contains('.')) "" else ".mp4"
        val destFile = File(importsDir, "${System.currentTimeMillis()}_${safeName}$ext").canonicalFile
        if (!destFile.path.startsWith(importsDir.path + File.separator)) {
            return@withContext uri
        }

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxImportBytes) {
                            throw IllegalStateException("File video melebihi batas impor.")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            destFile.exists() && destFile.length() > 1024L
        }.getOrDefault(false)

        if (copied) {
            Uri.fromFile(destFile)
        } else {
            runCatching { if (destFile.exists()) destFile.delete() }
            uri
        }
    }

    private suspend fun downloadRemoteVideoForExport(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        val safeUrl = UrlSecurityValidator.normalizeAndValidateExternalHttpUrl(uri.toString())
            ?: return@withContext null
        val cacheDir = File(context.cacheDir, "export_sources").apply {
            if (!exists()) mkdirs()
        }.canonicalFile
        pruneDirectoryBySize(cacheDir, maxTotalBytes = 180L * 1024L * 1024L)

        val targetFile = File(cacheDir, "src_${safeUrl.hashCode().toUInt()}.mp4").canonicalFile
        if (!targetFile.path.startsWith(cacheDir.path + File.separator)) return@withContext null
        if (targetFile.exists() && targetFile.length() > 8192L) {
            return@withContext Uri.fromFile(targetFile)
        }

        val maxRemoteBytes = 120L * 1024L * 1024L
        val success = runCatching {
            val request = Request.Builder()
                .url(safeUrl)
                .get()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .build()
            sourceDownloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxRemoteBytes) {
                                throw IllegalStateException("Ukuran stream remote melebihi 120MB")
                            }
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
                targetFile.exists() && targetFile.length() > 4096L
            }
        }.getOrDefault(false)

        if (success) {
            Uri.fromFile(targetFile)
        } else {
            runCatching { if (targetFile.exists()) targetFile.delete() }
            null
        }
    }

    private fun pruneDirectoryBySize(dir: File, maxTotalBytes: Long) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxTotalBytes) break
            val size = file.length()
            if (file.delete()) {
                total -= size
            }
        }
    }

    fun getCachedThumbnails(uri: Uri, count: Int = 8): List<Bitmap>? =
        thumbnailCache.get("${uri}|$count")

    suspend fun extractMetadata(uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context.contentResolver, uri)
            ?: uri.lastPathSegment?.substringBefore('?')?.ifBlank { null }
            ?: "Video_Klip"
        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverSource(retriever, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 1080
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 1920
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0

            val (effectiveW, effectiveH) = if (rotation == 90 || rotation == 270) {
                height to width
            } else {
                width to height
            }

            VideoMetadata(
                uriString = uri.toString(),
                title = displayName.substringBeforeLast('.').ifBlank { displayName },
                durationMs = max(durationMs, 1_000L),
                width = effectiveW,
                height = effectiveH
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun extractThumbnails(
        uri: Uri,
        durationMs: Long,
        count: Int = 8
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (count <= 0 || durationMs <= 0L) return@withContext emptyList()
        val cacheKey = "${uri}|$count"
        thumbnailCache.get(cacheKey)?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached
        }
        val retriever = MediaMetadataRetriever()
        val bitmaps = mutableListOf<Bitmap>()
        try {
            setRetrieverSource(retriever, uri)
            val stepUs = (durationMs * 1000L) / (count + 1)
            for (i in 1..count) {
                val timeUs = stepUs * i
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        160,
                        160
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (frame != null) {
                    bitmaps.add(frame)
                }
            }
        } catch (_: Exception) {
            // Gracefully return any frames extracted so far
        } finally {
            runCatching { retriever.release() }
        }
        if (bitmaps.isNotEmpty()) {
            thumbnailCache.put(cacheKey, bitmaps.toList())
        }
        bitmaps
    }

    suspend fun extractClipWindowFrames(
        uri: Uri,
        startMs: Long,
        endMs: Long,
        count: Int = 4
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val windowDuration = (endMs - startMs).coerceAtLeast(1_000L)
        if (count <= 0) return@withContext emptyList()
        val retriever = MediaMetadataRetriever()
        val bitmaps = mutableListOf<Bitmap>()
        try {
            setRetrieverSource(retriever, uri)
            val stepUs = (windowDuration * 1000L) / (count + 1)
            val startUs = startMs.coerceAtLeast(0L) * 1000L
            for (i in 1..count) {
                val timeUs = startUs + stepUs * i
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        240,
                        240
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (frame != null) {
                    bitmaps.add(frame)
                }
            }
        } catch (_: Exception) {
            // Gracefully return any extracted frames
        } finally {
            runCatching { retriever.release() }
        }
        bitmaps
    }

    private fun setRetrieverSource(retriever: MediaMetadataRetriever, uri: Uri) {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            val safeUrl = UrlSecurityValidator.normalizeAndValidateExternalHttpUrl(uri.toString())
                ?: throw SecurityException("URL stream tidak valid atau mengarah ke jaringan lokal.")
            retriever.setDataSource(safeUrl, HashMap())
        } else {
            retriever.setDataSource(context, uri)
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildClipEditedMediaItem(
        targetUri: Uri,
        segment: ClipSegment,
        qualityOverride: ExportQuality = segment.exportQuality,
        includeFiltersAndTransitions: Boolean = true,
        includeOverlay: Boolean = true
    ): EditedMediaItem {
        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(segment.startMs.coerceAtLeast(0L))
            .setEndPositionMs(segment.endMs.coerceAtLeast(segment.startMs + 500L))
            .build()

        val inputMediaItem = MediaItem.Builder()
            .setUri(targetUri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val overlayEffect = if (includeOverlay) CaptionOverlayEffects.buildOverlayEffect(segment) else null
        val transitionEffects = if (includeFiltersAndTransitions) {
            VideoTransitionEffects.buildEffects(
                style = segment.transitionStyle,
                transitionDurationMs = segment.transitionDurationMs,
                clipDurationMs = segment.durationMs
            )
        } else {
            emptyList()
        }
        val filterEffects = if (includeFiltersAndTransitions) {
            VideoFilterEffects.buildEffects(segment.videoFilter, segment.brightness)
        } else {
            emptyList()
        }

        val videoEffects: List<Effect> = buildAspectRatioEffects(segment.aspectRatio, qualityOverride) +
            filterEffects +
            transitionEffects +
            listOfNotNull(overlayEffect)

        return EditedMediaItem.Builder(inputMediaItem)
            .setRemoveAudio(!segment.includeAudio)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
    }

    @OptIn(UnstableApi::class)
    private suspend fun executeSingleClipRenderTiers(
        targetUri: Uri,
        segment: ClipSegment,
        resolvedMusicUri: Uri?,
        outputFile: File,
        onProgress: (Int) -> Unit,
        onTransformerCreated: (Transformer) -> Unit
    ) {
        val fullItem = buildClipEditedMediaItem(
            targetUri = targetUri,
            segment = segment,
            qualityOverride = segment.exportQuality,
            includeFiltersAndTransitions = true,
            includeOverlay = true
        )

        var lastException: Throwable? = null

        // Tier 1: Full effects + music mix
        if (resolvedMusicUri != null) {
            val musicComposition = buildMusicMixComposition(
                videoItem = fullItem,
                musicUri = resolvedMusicUri,
                musicVolume = segment.musicVolume
            )
            try {
                runTransformer(outputFile, onProgress, onTransformerCreated) { transformer ->
                    transformer.start(musicComposition, outputFile.absolutePath)
                }
                return
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                lastException = t
                if (outputFile.exists()) outputFile.delete()
            }
        }

        // Tier 2: Full effects without background music mix
        try {
            runTransformer(outputFile, onProgress, onTransformerCreated) { transformer ->
                transformer.start(fullItem, outputFile.absolutePath)
            }
            return
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            lastException = t
            if (outputFile.exists()) outputFile.delete()
        }

        // Tier 3: Safe 720p + overlay without heavy GL filters/transitions
        val safeOverlayItem = buildClipEditedMediaItem(
            targetUri = targetUri,
            segment = segment,
            qualityOverride = if (segment.exportQuality == ExportQuality.HD_1080P) {
                ExportQuality.HD_720P
            } else {
                segment.exportQuality
            },
            includeFiltersAndTransitions = false,
            includeOverlay = true
        )
        try {
            runTransformer(outputFile, onProgress, onTransformerCreated) { transformer ->
                transformer.start(safeOverlayItem, outputFile.absolutePath)
            }
            return
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            lastException = t
            if (outputFile.exists()) outputFile.delete()
        }

        // Tier 4: Bare minimum trim + crop without overlays or GL shaders
        val basicItem = buildClipEditedMediaItem(
            targetUri = targetUri,
            segment = segment,
            qualityOverride = ExportQuality.HD_720P,
            includeFiltersAndTransitions = false,
            includeOverlay = false
        )
        try {
            runTransformer(outputFile, onProgress, onTransformerCreated) { transformer ->
                transformer.start(basicItem, outputFile.absolutePath)
            }
            return
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (outputFile.exists()) outputFile.delete()
            throw lastException ?: t
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun exportTrimmedClip(
        sourceUri: Uri,
        segment: ClipSegment,
        onProgress: (Int) -> Unit,
        onTransformerCreated: (Transformer) -> Unit = {}
    ): ExportedClip {
        val resolvedMusicUri = if (!segment.selectedMusicUrl.isNullOrBlank()) {
            runCatching {
                RoyaltyFreeMusicCatalog.resolvePlayableAudioUri(
                    context = context,
                    audioUrl = segment.selectedMusicUrl,
                    trackId = segment.selectedMusicTrackId,
                    minDurationMs = segment.durationMs.coerceAtLeast(5_000L)
                )
            }.getOrNull()
        } else {
            null
        }

        return withContext(Dispatchers.Main) {
            val clipsDir = File(context.filesDir, "clips").apply {
                if (!exists()) mkdirs()
            }
            val safeTitle = segment.title
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .take(28)
                .ifBlank { "clip" }
            val outputFile = File(clipsDir, "${safeTitle}_${System.currentTimeMillis()}.mp4")

            try {
                try {
                    executeSingleClipRenderTiers(
                        targetUri = sourceUri,
                        segment = segment,
                        resolvedMusicUri = resolvedMusicUri,
                        outputFile = outputFile,
                        onProgress = onProgress,
                        onTransformerCreated = onTransformerCreated
                    )
                } catch (primaryThrowable: Throwable) {
                    if (primaryThrowable is CancellationException) throw primaryThrowable
                    val scheme = sourceUri.scheme?.lowercase()
                    val downloadedLocalUri = if (scheme == "http" || scheme == "https") {
                        downloadRemoteVideoForExport(sourceUri)
                    } else {
                        null
                    }
                    if (downloadedLocalUri != null) {
                        if (outputFile.exists()) outputFile.delete()
                        executeSingleClipRenderTiers(
                            targetUri = downloadedLocalUri,
                            segment = segment,
                            resolvedMusicUri = resolvedMusicUri,
                            outputFile = outputFile,
                            onProgress = onProgress,
                            onTransformerCreated = onTransformerCreated
                        )
                    } else {
                        throw primaryThrowable
                    }
                }

                val galleryUri = runCatching {
                    saveFileToGallery(outputFile, segment.durationMs)
                }.onFailure { err ->
                    Log.e("VideoMediaProcessor", "Gallery save skipped or needs retry", err)
                }.getOrNull()

                ExportedClip(
                    projectId = segment.projectId,
                    segmentId = segment.id,
                    title = segment.title,
                    filePath = outputFile.absolutePath,
                    durationMs = segment.durationMs,
                    fileSizeBytes = outputFile.length(),
                    aspectRatio = segment.aspectRatio,
                    galleryUri = galleryUri?.toString(),
                    exportedAtMs = System.currentTimeMillis()
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (outputFile.exists()) outputFile.delete()
                val codeInfo = (t as? ExportException)?.errorCodeName ?: t.javaClass.simpleName
                Log.e("VideoMediaProcessor", "Export clipped render halted ($codeInfo)", t)
                throw IllegalStateException(
                    "Ekspor klip MP4 perlu disesuaikan ($codeInfo). Coba pilih kualitas 720p atau file video MP4 lain.",
                    t
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun exportCombinedClipsWithTransitions(
        sourceUri: Uri,
        projectTitle: String,
        segments: List<ClipSegment>,
        onProgress: (Int) -> Unit,
        onTransformerCreated: (Transformer) -> Unit = {}
    ): ExportedClip {
        require(segments.isNotEmpty()) { "Minimal 1 klip diperlukan untuk menggabungkan transisi." }
        val lead = segments.first()
        val totalDurationMs = segments.sumOf { it.durationMs }.coerceAtLeast(1_000L)

        val resolvedMusicUri = if (!lead.selectedMusicUrl.isNullOrBlank()) {
            runCatching {
                RoyaltyFreeMusicCatalog.resolvePlayableAudioUri(
                    context = context,
                    audioUrl = lead.selectedMusicUrl,
                    trackId = lead.selectedMusicTrackId,
                    minDurationMs = totalDurationMs
                )
            }.getOrNull()
        } else {
            null
        }

        return withContext(Dispatchers.Main) {
            val clipsDir = File(context.filesDir, "clips").apply {
                if (!exists()) mkdirs()
            }
            val safeTitle = projectTitle
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .take(24)
                .ifBlank { "kompilasi" }
            val outputFile = File(clipsDir, "${safeTitle}_reel_${System.currentTimeMillis()}.mp4")

            suspend fun runCombinedForUri(targetUri: Uri) {
                val editedItems = segments.map { seg ->
                    buildClipEditedMediaItem(
                        targetUri = targetUri,
                        segment = seg,
                        qualityOverride = lead.exportQuality,
                        includeFiltersAndTransitions = true,
                        includeOverlay = true
                    )
                }

                val videoSequence = EditedMediaItemSequence.Builder(editedItems).build()
                if (resolvedMusicUri != null) {
                    val vol = lead.musicVolume.coerceIn(0.05f, 1.0f)
                    val volumeProcessor = ChannelMixingAudioProcessor().apply {
                        putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(vol))
                        putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2).scaleBy(vol))
                    }
                    val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(resolvedMusicUri))
                        .setRemoveVideo(true)
                        .setEffects(Effects(listOf(volumeProcessor), emptyList()))
                        .build()
                    val musicSeq = EditedMediaItemSequence.Builder(listOf(musicItem))
                        .setIsLooping(true)
                        .build()
                    val compositionWithMusic = Composition.Builder(listOf(videoSequence, musicSeq)).build()
                    try {
                        runTransformer(outputFile, onProgress, onTransformerCreated) { transformer ->
                            transformer.start(compositionWithMusic, outputFile.absolutePath)
                        }
                        return
                    } catch (musicEx: Throwable) {
                        if (musicEx is CancellationException) throw musicEx
                        if (outputFile.exists()) outputFile.delete()
                    }
                }

                val videoOnlyComp = Composition.Builder(listOf(videoSequence)).build()
                try {
                    runTransformer(outputFile, onProgress, onTransformerCreated) { transformer ->
                        transformer.start(videoOnlyComp, outputFile.absolutePath)
                    }
                    return
                } catch (fullEx: Throwable) {
                    if (fullEx is CancellationException) throw fullEx
                    if (outputFile.exists()) outputFile.delete()
                }

                val safeItems = segments.map { seg ->
                    buildClipEditedMediaItem(
                        targetUri = targetUri,
                        segment = seg,
                        qualityOverride = ExportQuality.HD_720P,
                        includeFiltersAndTransitions = false,
                        includeOverlay = false
                    )
                }
                val fallbackComp = Composition.Builder(
                    listOf(EditedMediaItemSequence.Builder(safeItems).build())
                ).build()
                runTransformer(outputFile, onProgress, onTransformerCreated) { transformer ->
                    transformer.start(fallbackComp, outputFile.absolutePath)
                }
            }

            try {
                try {
                    runCombinedForUri(sourceUri)
                } catch (primaryThrowable: Throwable) {
                    if (primaryThrowable is CancellationException) throw primaryThrowable
                    val scheme = sourceUri.scheme?.lowercase()
                    val downloadedLocalUri = if (scheme == "http" || scheme == "https") {
                        downloadRemoteVideoForExport(sourceUri)
                    } else {
                        null
                    }
                    if (downloadedLocalUri != null) {
                        if (outputFile.exists()) outputFile.delete()
                        runCombinedForUri(downloadedLocalUri)
                    } else {
                        throw primaryThrowable
                    }
                }

                val galleryUri = runCatching {
                    saveFileToGallery(outputFile, totalDurationMs)
                }.onFailure { err ->
                    Log.e("VideoMediaProcessor", "Combined gallery save skipped or needs retry", err)
                }.getOrNull()

                ExportedClip(
                    projectId = lead.projectId,
                    segmentId = lead.id,
                    title = "$projectTitle (Gabungan ${segments.size} Klip)",
                    filePath = outputFile.absolutePath,
                    durationMs = totalDurationMs,
                    fileSizeBytes = outputFile.length(),
                    aspectRatio = lead.aspectRatio,
                    galleryUri = galleryUri?.toString(),
                    exportedAtMs = System.currentTimeMillis()
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (outputFile.exists()) outputFile.delete()
                val codeInfo = (t as? ExportException)?.errorCodeName ?: t.javaClass.simpleName
                Log.e("VideoMediaProcessor", "Combined export halted ($codeInfo)", t)
                throw IllegalStateException(
                    "Penggabungan klip MP4 perlu disesuaikan ($codeInfo). Coba kualitas 720p atau file video MP4 lain.",
                    t
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildMusicMixComposition(
        videoItem: EditedMediaItem,
        musicUri: Uri,
        musicVolume: Float
    ): Composition {
        val vol = musicVolume.coerceIn(0.05f, 1.0f)
        val volumeProcessor = ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(vol))
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2).scaleBy(vol))
        }
        val audioProcessors: List<AudioProcessor> = listOf(volumeProcessor)
        val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(musicUri))
            .setRemoveVideo(true)
            .setEffects(Effects(audioProcessors, emptyList()))
            .build()

        val videoSequence = EditedMediaItemSequence.Builder(listOf(videoItem)).build()
        val musicSequence = EditedMediaItemSequence.Builder(listOf(musicItem))
            .setIsLooping(true)
            .build()

        return Composition.Builder(listOf(videoSequence, musicSequence)).build()
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformer(
        outputFile: File,
        onProgress: (Int) -> Unit,
        onTransformerCreated: (Transformer) -> Unit,
        startBlock: (Transformer) -> Unit
    ): ExportResult {
        val mainHandler = Handler(Looper.getMainLooper())
        var progressRunnable: Runnable? = null

        return suspendCancellableCoroutine { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    progressRunnable?.let { mainHandler.removeCallbacks(it) }
                    onProgress(100)
                    if (continuation.isActive) continuation.resume(exportResult)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    progressRunnable?.let { mainHandler.removeCallbacks(it) }
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }

            val assetLoaderFactory = ExoPlayerAssetLoader.Factory(
                context,
                DefaultDecoderFactory.Builder(context)
                    .setEnableDecoderFallback(true)
                    .build(),
                Clock.DEFAULT,
                MediaPlaybackCache.buildDirectMediaSourceFactory(context)
            )

            val transformer = Transformer.Builder(context)
                .setAssetLoaderFactory(assetLoaderFactory)
                .addListener(listener)
                .build()

            onTransformerCreated(transformer)
            startBlock(transformer)

            val progressHolder = ProgressHolder()
            val runnable = object : Runnable {
                override fun run() {
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 99))
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED && continuation.isActive) {
                        mainHandler.postDelayed(this, 250L)
                    }
                }
            }
            progressRunnable = runnable
            mainHandler.post(runnable)

            continuation.invokeOnCancellation {
                progressRunnable?.let { mainHandler.removeCallbacks(it) }
                transformer.cancel()
                if (outputFile.exists()) outputFile.delete()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildAspectRatioEffects(
        aspectRatio: AspectRatioPreset,
        quality: com.example.domain.model.ExportQuality
    ): List<Effect> {
        val shortSide = quality.shortSidePx
        val (targetW, targetH) = when (aspectRatio) {
            AspectRatioPreset.VERTICAL_9_16 -> shortSide to ((shortSide * 16f / 9f).toInt() and 0xFFFE)
            AspectRatioPreset.PORTRAIT_4_5 -> shortSide to ((shortSide * 5f / 4f).toInt() and 0xFFFE)
            AspectRatioPreset.SQUARE_1_1 -> shortSide to shortSide
            AspectRatioPreset.LANDSCAPE_16_9 -> ((shortSide * 16f / 9f).toInt() and 0xFFFE) to shortSide
        }
        val presentation = Presentation.createForWidthAndHeight(
            targetW,
            targetH,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )
        return listOf(presentation)
    }

    private fun resolveOwnedExportFile(rawPath: String): File? {
        if (rawPath.isBlank()) return null
        val candidate = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return null
        val clipsDir = File(context.filesDir, "clips").canonicalFile
        val exportsDir = File(context.filesDir, "exports").canonicalFile
        val insideClips = candidate.path.startsWith(clipsDir.path + File.separator)
        val insideExports = candidate.path.startsWith(exportsDir.path + File.separator)
        return if (insideClips || insideExports) candidate else null
    }

    fun deleteExportFileIfOwned(exportedClip: ExportedClip): Boolean {
        val ownedFile = resolveOwnedExportFile(exportedClip.filePath) ?: return false
        return runCatching { ownedFile.exists() && ownedFile.delete() }.getOrDefault(false)
    }

    suspend fun saveClipToGallery(exportedClip: ExportedClip): Uri? {
        val ownedFile = resolveOwnedExportFile(exportedClip.filePath) ?: return null
        return saveFileToGallery(ownedFile, exportedClip.durationMs)
    }

    private suspend fun saveFileToGallery(file: File, durationMs: Long): Uri? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) return@withContext null

        val nowSec = System.currentTimeMillis() / 1000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            fun buildValues(displayName: String): ContentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AIClipper")
                put(MediaStore.Video.Media.DATE_ADDED, nowSec)
                put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)
                if (durationMs > 0L) {
                    put(MediaStore.Video.Media.DURATION, durationMs)
                }
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val primaryValues = buildValues(file.name)
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, primaryValues)
                ?: resolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    buildValues("${file.nameWithoutExtension}_${System.currentTimeMillis()}.mp4")
                )
                ?: return@withContext null

            try {
                val outStream = resolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Penyimpanan Galeri sedang tidak tersedia. Coba lagi.")
                outStream.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                }
                val finishValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(uri, finishValues, null, null)
                uri
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        } else {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: return@withContext null
            if (!moviesDir.exists()) moviesDir.mkdirs()
            val dest = File(moviesDir, file.name)
            file.copyTo(dest, overwrite = true)
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(dest.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            Uri.fromFile(dest)
        }
    }

    fun getShareableUri(exportedClip: ExportedClip): Uri? {
        val file = resolveOwnedExportFile(exportedClip.filePath) ?: return null
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun resolveDisplayName(uri: Uri): String {
        val raw = queryDisplayName(context.contentResolver, uri)
            ?: uri.lastPathSegment?.substringBefore('?')?.ifBlank { null }
            ?: "Video_Klip"
        return raw.substringBeforeLast('.').ifBlank { raw }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }

    private fun queryOpenableSize(resolver: ContentResolver, uri: Uri): Long? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else null
            }
        }.getOrNull()?.takeIf { it > 0L }
    }
}
