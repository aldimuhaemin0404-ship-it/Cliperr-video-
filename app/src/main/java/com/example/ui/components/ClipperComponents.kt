package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ExportProgressState
import com.example.domain.model.HookOverlayStyle
import com.example.domain.model.SubtitleCue
import com.example.domain.model.VideoFilterPreset
import com.example.domain.model.VideoTransitionStyle
import com.example.media.MediaPlaybackCache
import com.example.media.VideoFilterEffects
import com.example.media.VideoTransitionEffects
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberContainer
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioEmeraldContainer
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun SocialLinkPreviewCard(
    title: String,
    platformName: String,
    authorName: String?,
    thumbnailUrl: String?,
    statusNote: String?,
    onAttachLocalVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("social_link_preview_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(Color(0xFF0E131D)),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x33000000),
                                        Color(0xCC0B0E14)
                                    )
                                )
                            )
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = platformName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!authorName.isNullOrBlank()) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!statusNote.isNullOrBlank()) {
                    Text(
                        text = statusNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onAttachLocalVideo,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("attach_local_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.studio_link_attach_local))
                }
            }
        }
    }
}

@Composable
fun AiDemoNoticeBanner(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_demo_notice_banner"),
        shape = RoundedCornerShape(14.dp),
        color = StudioEmeraldContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, StudioEmerald.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.ai_demo_notice_title),
                tint = StudioEmerald,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.ai_demo_notice_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioEmerald
                )
                Text(
                    text = stringResource(R.string.ai_demo_notice_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun ViralScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val (accent, bg) = when {
        score >= 90 -> StudioEmerald to StudioEmeraldContainer
        score >= 82 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
        else -> StudioAmber to StudioAmberContainer
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$score/100",
                style = MonoTimecodeStyle.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }
}

@Composable
fun FilmstripTimelineBar(
    thumbnails: List<Bitmap>,
    totalDurationMs: Long,
    startMs: Long? = null,
    endMs: Long? = null,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalDurationMs.coerceAtLeast(1L).toFloat()
    val highlightColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        if (thumbnails.isEmpty()) {
            val shimmerTransition = rememberInfiniteTransition(label = "filmstripShimmer")
            val shimmerX by shimmerTransition.animateFloat(
                initialValue = -0.35f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "filmstripShimmerX"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                (shimmerX - 0.35f).coerceIn(0f, 1f) to CosmicSurface,
                                shimmerX.coerceIn(0f, 1f) to NeonPurple.copy(alpha = 0.25f),
                                (shimmerX + 0.35f).coerceIn(0f, 1f) to CosmicSurface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Menyiapkan filmstrip HD…",
                    style = MaterialTheme.typography.labelMedium,
                    color = FuturisticCyan.copy(alpha = 0.8f)
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        if (startMs != null && endMs != null) {
            val startFrac = (startMs / safeTotal).coerceIn(0f, 1f)
            val endFrac = (endMs / safeTotal).coerceIn(startFrac, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val leftPx = size.width * startFrac
                val rightPx = size.width * endFrac
                val dimColor = Color.Black.copy(alpha = 0.56f)

                if (leftPx > 0f) {
                    drawRect(
                        color = dimColor,
                        topLeft = Offset.Zero,
                        size = Size(leftPx, size.height)
                    )
                }
                if (rightPx < size.width) {
                    drawRect(
                        color = dimColor,
                        topLeft = Offset(rightPx, 0f),
                        size = Size(size.width - rightPx, size.height)
                    )
                }
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(leftPx, 1.5.dp.toPx()),
                    size = Size((rightPx - leftPx).coerceAtLeast(4.dp.toPx()), size.height - 3.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoClipPlayerPreview(
    videoUri: Uri,
    aspectRatio: AspectRatioPreset,
    startMs: Long = 0L,
    endMs: Long? = null,
    videoFilter: VideoFilterPreset = VideoFilterPreset.NONE,
    brightness: Float = 0f,
    overlayHook: String? = null,
    subtitleCues: List<SubtitleCue> = emptyList(),
    showCaptionOverlay: Boolean = false,
    showIntroHook: Boolean = true,
    hookDurationMs: Long = 3_000L,
    hookOverlayStyle: HookOverlayStyle = HookOverlayStyle.NEON_BADGE,
    transitionStyle: VideoTransitionStyle = VideoTransitionStyle.FADE,
    transitionDurationMs: Long = 600L,
    selectedMusicTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var showOverlayOnPlay by remember { mutableStateOf(false) }

    val latestStartMs by rememberUpdatedState(startMs.coerceAtLeast(0L))
    val latestEndMs by rememberUpdatedState(endMs)
    val clipDurationMs = remember(startMs, endMs) {
        ((endMs ?: 10_000L) - startMs).coerceAtLeast(1_500L)
    }

    val exoPlayer = remember(videoUri) {
        MediaPlaybackCache.buildFastExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    showOverlayOnPlay = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isPlaying = false
                playbackError = error.localizedMessage ?: "Gagal memutar pratinjau."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Prepare media ONLY when videoUri changes so trim drags never reset buffer!
    LaunchedEffect(videoUri) {
        playbackError = null
        val effects = VideoFilterEffects.buildEffects(videoFilter, brightness) +
            VideoTransitionEffects.buildEffects(transitionStyle, transitionDurationMs, clipDurationMs)
        exoPlayer.setVideoEffects(effects)
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        if (latestStartMs > 0L) {
            exoPlayer.seekTo(latestStartMs)
        }
    }

    // Smooth debounced seek on trim handle adjustment without re-preparing ExoPlayer
    LaunchedEffect(startMs) {
        delay(90L)
        val safeStart = startMs.coerceAtLeast(0L)
        if (!exoPlayer.isPlaying || abs(exoPlayer.currentPosition - safeStart) > 400L) {
            exoPlayer.seekTo(safeStart)
            currentPosMs = 0L
        }
    }

    LaunchedEffect(exoPlayer, videoFilter, brightness, transitionStyle, transitionDurationMs, clipDurationMs) {
        val effects = VideoFilterEffects.buildEffects(videoFilter, brightness) +
            VideoTransitionEffects.buildEffects(transitionStyle, transitionDurationMs, clipDurationMs)
        exoPlayer.setVideoEffects(effects)
    }

    LaunchedEffect(isPlaying, showOverlayOnPlay) {
        if (isPlaying && showOverlayOnPlay) {
            delay(1_500L)
            showOverlayOnPlay = false
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            val rawPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val startLimit = latestStartMs
            val endLimit = latestEndMs
            if (endLimit != null && endLimit > startLimit + 500L && exoPlayer.isPlaying) {
                if (rawPos >= endLimit || rawPos + 250L < startLimit) {
                    exoPlayer.seekTo(startLimit)
                }
            }
            currentPosMs = (rawPos - startLimit).coerceAtLeast(0L)
            delay(if (exoPlayer.isPlaying) 100L else 220L)
        }
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (!isPlaying || showOverlayOnPlay || isBuffering) 1f else 0f,
        label = "clipPreviewOverlayAlpha"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val maxFrameHeight = 360.dp
        val ratio = aspectRatio.ratioFloat
        val frameModifier = if (ratio < 1f) {
            Modifier
                .heightIn(max = maxFrameHeight)
                .aspectRatio(ratio)
        } else {
            Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .aspectRatio(ratio)
        }

        Box(
            modifier = frameModifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                .clickable {
                    if (playbackError != null) {
                        playbackError = null
                        exoPlayer.prepare()
                        exoPlayer.play()
                    } else if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        showOverlayOnPlay = true
                    } else {
                        if (exoPlayer.currentPosition < latestStartMs ||
                            (latestEndMs != null && exoPlayer.currentPosition >= latestEndMs!!)
                        ) {
                            exoPlayer.seekTo(latestStartMs)
                        }
                        exoPlayer.play()
                        showOverlayOnPlay = true
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top-start active filter badge
            if (videoFilter != VideoFilterPreset.NONE || abs(brightness) >= 0.01f) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    val filterTag = buildString {
                        if (videoFilter != VideoFilterPreset.NONE) append(videoFilter.label) else append("Terang")
                        if (abs(brightness) >= 0.01f) {
                            val pct = (brightness * 200f).toInt()
                            val sign = if (pct > 0) "+" else ""
                            append(" $sign$pct%")
                        }
                    }
                    Text(
                        text = filterTag,
                        style = MonoTimecodeStyle,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Top-right aspect ratio, anti-buff & active transition badges
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = aspectRatio.label,
                        style = MonoTimecodeStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (transitionStyle != VideoTransitionStyle.NONE) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "${transitionStyle.label} ${transitionDurationMs}ms",
                            style = MonoTimecodeStyle,
                            color = FuturisticCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Center Play/Pause or Buffering/Retry overlay
            if (playbackError != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xDC0E1328),
                    border = BorderStroke(1.dp, NeonPurple)
                ) {
                    TextButton(
                        onClick = {
                            playbackError = null
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = FuturisticCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Muat Ulang Video", color = FuturisticCyan)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(overlayAlpha)
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = if (isPlaying) 0.42f else 0.68f))
                        .border(1.dp, FuturisticCyan.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = FuturisticCyan,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda video" else "Putar video",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // First 3-seconds AI Visual Hook Intro Overlay
            val showHookNow = showIntroHook &&
                !overlayHook.isNullOrBlank() &&
                currentPosMs in 0L..hookDurationMs

            AnimatedVisibility(
                visible = showHookNow,
                enter = fadeIn() + scaleIn(initialScale = 0.92f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 14.dp, end = 14.dp)
            ) {
                val (hookBg, hookBorder, hookAccent) = when (hookOverlayStyle) {
                    HookOverlayStyle.NEON_BADGE -> Triple(
                        Color(0xE0180C36),
                        NeonPurple,
                        FuturisticCyan
                    )
                    HookOverlayStyle.CYBER_BANNER -> Triple(
                        Color(0xE0081C38),
                        FuturisticCyan,
                        StudioAmber
                    )
                    HookOverlayStyle.MINIMAL_BOLD -> Triple(
                        Color(0xDC0C0E16),
                        NeonMagenta,
                        NeonMagenta
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = hookBg,
                    border = BorderStroke(1.5.dp, hookBorder),
                    modifier = Modifier.testTag("preview_intro_hook_overlay")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "HOOK 0-${(hookDurationMs / 1000L).coerceAtLeast(1L)} DETIK",
                            style = MonoTimecodeStyle.copy(fontWeight = FontWeight.Bold),
                            color = hookAccent
                        )
                        Text(
                            text = overlayHook.orEmpty(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Selected background music pill (bottom-top above subtitles when selected)
            if (!selectedMusicTitle.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .testTag("preview_music_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = FuturisticCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = selectedMusicTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    }
                }
            }

            // Optional Timed Subtitle preview overlay
            val activeCue = remember(currentPosMs, subtitleCues) {
                subtitleCues.firstOrNull { cue ->
                    currentPosMs in cue.startMs..cue.endMs
                } ?: subtitleCues.firstOrNull()
            }
            val displayOverlayText = activeCue?.text?.takeIf { it.isNotBlank() }
                ?: if (!showIntroHook) overlayHook else null

            if (showCaptionOverlay && !displayOverlayText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (activeCue != null) {
                                Text(
                                    text = "${formatTimecode(activeCue.startMs)} – ${formatTimecode(activeCue.endMs)}",
                                    style = MonoTimecodeStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = displayOverlayText,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportProgressBanner(
    state: ExportProgressState,
    onCancel: () -> Unit,
    onOpenExports: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is ExportProgressState.Idle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            when (state) {
                is ExportProgressState.Exporting -> {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Merender MP4: ${state.clipTitle}",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${state.progressPercent}%",
                                style = MonoTimecodeStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onCancel,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(stringResource(R.string.editor_cancel_export))
                            }
                        }
                    }
                }

                is ExportProgressState.Completed -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = StudioEmerald
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.exportedClip.galleryUri != null) {
                                    "Tersimpan di Galeri (Movies/AIClipper)"
                                } else {
                                    "Ekspor MP4 Selesai"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioEmerald
                            )
                            Text(
                                text = "${state.exportedClip.title} • ${formatFileSize(state.exportedClip.fileSizeBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenExports()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Lihat")
                        }
                    }
                }

                is ExportProgressState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Tutup"
                            )
                        }
                    }
                }

                ExportProgressState.Idle -> Unit
            }
        }
    }
}
