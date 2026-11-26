package com.example.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportedClip
import com.example.domain.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

class VideoMediaProcessor(private val context: Context) {

    private val thumbnailCache = object : LruCache<String, List<Bitmap>>(12) {}

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
            retriever.setDataSource(uri.toString(), HashMap())
        } else {
            retriever.setDataSource(context, uri)
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

            val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(segment.startMs.coerceAtLeast(0L))
                .setEndPositionMs(segment.endMs.coerceAtLeast(segment.startMs + 500L))
                .build()

            val inputMediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(clippingConfig)
                .build()

            val overlayEffect = CaptionOverlayEffects.buildOverlayEffect(segment)
            val transitionEffects = VideoTransitionEffects.buildEffects(
                style = segment.transitionStyle,
                transitionDurationMs = segment.transitionDurationMs,
                clipDurationMs = segment.durationMs
            )
            val videoEffects: List<Effect> = buildAspectRatioEffects(segment.aspectRatio, segment.exportQuality) +
                VideoFilterEffects.buildEffects(segment.videoFilter, segment.brightness) +
                transitionEffects +
                listOfNotNull(overlayEffect)
            val editedMediaItem = EditedMediaItem.Builder(inputMediaItem)
                .setRemoveAudio(!segment.includeAudio)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

            try {
                if (resolvedMusicUri != null) {
                    val musicComposition = buildMusicMixComposition(
                        videoItem = editedMediaItem,
                        musicUri = resolvedMusicUri,
                        musicVolume = segment.musicVolume
                    )
                    try {
                        runTransformer(
                            outputFile = outputFile,
                            onProgress = onProgress,
                            onTransformerCreated = onTransformerCreated
                        ) { transformer ->
                            transformer.start(musicComposition, outputFile.absolutePath)
                        }
                    } catch (musicMixException: Throwable) {
                        if (musicMixException is kotlinx.coroutines.CancellationException) throw musicMixException
                        if (outputFile.exists()) outputFile.delete()
                        runTransformer(
                            outputFile = outputFile,
                            onProgress = onProgress,
                            onTransformerCreated = onTransformerCreated
                        ) { transformer ->
                            transformer.start(editedMediaItem, outputFile.absolutePath)
                        }
                    }
                } else {
                    runTransformer(
                        outputFile = outputFile,
                        onProgress = onProgress,
                        onTransformerCreated = onTransformerCreated
                    ) { transformer ->
                        transformer.start(editedMediaItem, outputFile.absolutePath)
                    }
                }

                val galleryUri = runCatching {
                    saveFileToGallery(outputFile, segment.durationMs)
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
                if (outputFile.exists()) outputFile.delete()
                throw t
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

            val editedItems = segments.map { seg ->
                val clipConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(seg.startMs.coerceAtLeast(0L))
                    .setEndPositionMs(seg.endMs.coerceAtLeast(seg.startMs + 500L))
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(clipConfig)
                    .build()
                val overlayEffect = CaptionOverlayEffects.buildOverlayEffect(seg)
                val transitionEffects = VideoTransitionEffects.buildEffects(
                    style = seg.transitionStyle,
                    transitionDurationMs = seg.transitionDurationMs,
                    clipDurationMs = seg.durationMs
                )
                val videoEffects: List<Effect> = buildAspectRatioEffects(lead.aspectRatio, lead.exportQuality) +
                    VideoFilterEffects.buildEffects(seg.videoFilter, seg.brightness) +
                    transitionEffects +
                    listOfNotNull(overlayEffect)
                EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(!seg.includeAudio)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
            }

            val videoSequence = EditedMediaItemSequence.Builder(editedItems).build()
            val sequences = if (resolvedMusicUri != null) {
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
                listOf(videoSequence, musicSeq)
            } else {
                listOf(videoSequence)
            }

            val composition = Composition.Builder(sequences).build()
            try {
                runTransformer(
                    outputFile = outputFile,
                    onProgress = onProgress,
                    onTransformerCreated = onTransformerCreated
                ) { transformer ->
                    transformer.start(composition, outputFile.absolutePath)
                }

                val galleryUri = runCatching {
                    saveFileToGallery(outputFile, totalDurationMs)
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
                if (outputFile.exists()) outputFile.delete()
                throw t
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

            val transformer = Transformer.Builder(context)
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

    suspend fun saveClipToGallery(exportedClip: ExportedClip): Uri? =
        saveFileToGallery(File(exportedClip.filePath), exportedClip.durationMs)

    private suspend fun saveFileToGallery(file: File, durationMs: Long): Uri? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        val nowSec = System.currentTimeMillis() / 1000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AIClipper")
                put(MediaStore.Video.Media.DATE_ADDED, nowSec)
                put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)
                if (durationMs > 0L) {
                    put(MediaStore.Video.Media.DURATION, durationMs)
                }
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
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
        val file = File(exportedClip.filePath)
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
}
