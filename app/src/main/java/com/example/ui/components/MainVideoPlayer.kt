package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.domain.model.VideoFilterPreset
import com.example.media.MediaPlaybackCache
import com.example.media.VideoFilterEffects
import com.example.ui.theme.CosmicOutline
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioEmerald
import kotlinx.coroutines.delay
import kotlin.math.abs

private val PLAYBACK_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f)

/**
 * Main-screen Media3 ExoPlayer component powered by [MediaPlaybackCache]
 * (SimpleCache + fast DefaultLoadControl + async MediaCodec queueing) for
 * instant startup, zero-stutter scrubbing, playback speed control, and filter preview.
 */
@OptIn(UnstableApi::class)
@Composable
fun MainVideoPlayer(
    videoUri: Uri,
    videoWidth: Int,
    videoHeight: Int,
    videoFilter: VideoFilterPreset = VideoFilterPreset.NONE,
    brightness: Float = 0f,
    seekRequest: Pair<Long, Long>? = null,
    playRangeRequest: Triple<Long, Long, Long>? = null,
    onPositionChanged: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var activeClipEndMs by remember { mutableStateOf<Long?>(null) }
    var speedIndex by remember { mutableStateOf(0) }
    var showOverlayOnPlay by remember { mutableStateOf(false) }

    val exoPlayer = remember(videoUri) {
        MediaPlaybackCache.buildFastExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                    val d = exoPlayer.duration
                    if (d > 0L) durationMs = d
                    bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    activeClipEndMs = null
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
                playbackError = error.localizedMessage ?: "Gagal memuat stream video."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(videoUri) {
        activeClipEndMs = null
        playbackError = null
        exoPlayer.setVideoEffects(VideoFilterEffects.buildEffects(videoFilter, brightness))
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
    }

    LaunchedEffect(exoPlayer, videoFilter, brightness) {
        exoPlayer.setVideoEffects(VideoFilterEffects.buildEffects(videoFilter, brightness))
    }

    LaunchedEffect(seekRequest) {
        if (seekRequest != null) {
            activeClipEndMs = null
            val targetMs = seekRequest.first.coerceAtLeast(0L)
            exoPlayer.seekTo(targetMs)
            currentPositionMs = targetMs
            onPositionChanged(targetMs)
        }
    }

    LaunchedEffect(playRangeRequest) {
        if (playRangeRequest != null) {
            val (startMs, endMs, _) = playRangeRequest
            activeClipEndMs = endMs
            playbackError = null
            exoPlayer.seekTo(startMs.coerceAtLeast(0L))
            exoPlayer.play()
            currentPositionMs = startMs
            onPositionChanged(startMs)
        }
    }

    // Auto-hide play/pause overlay 1.6s after tapping while playing
    LaunchedEffect(isPlaying, showOverlayOnPlay) {
        if (isPlaying && showOverlayOnPlay) {
            delay(1_600L)
            showOverlayOnPlay = false
        }
    }

    LaunchedEffect(exoPlayer, isScrubbing, activeClipEndMs) {
        while (true) {
            val reportedDuration = exoPlayer.duration
            if (reportedDuration > 0L) {
                durationMs = reportedDuration
            }
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            if (!isScrubbing) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPositionMs = pos
                onPositionChanged(pos)
                val clipEnd = activeClipEndMs
                if (clipEnd != null && exoPlayer.isPlaying && pos >= clipEnd) {
                    exoPlayer.pause()
                    activeClipEndMs = null
                }
            }
            delay(if (exoPlayer.isPlaying) 90L else 240L)
        }
    }

    val safeRatio = remember(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(9f / 16f, 16f / 9f)
        } else {
            16f / 9f
        }
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (!isPlaying || showOverlayOnPlay || isBuffering) 1f else 0f,
        label = "playerOverlayAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_video_player"),
        shape = RoundedCornerShape(18.dp),
        color = Color.Black,
        border = BorderStroke(1.2.dp, CosmicOutline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(safeRatio)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color.Black)
                    .clickable {
                        activeClipEndMs = null
                        if (playbackError != null) {
                            playbackError = null
                            exoPlayer.prepare()
                            exoPlayer.play()
                        } else if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            showOverlayOnPlay = true
                        } else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0L)
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
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            player = exoPlayer
                        }
                    },
                    update = { view ->
                        view.player = exoPlayer
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
                        color = Color(0xB8070914),
                        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.55f))
                    ) {
                        val badgeLabel = buildString {
                            if (videoFilter != VideoFilterPreset.NONE) {
                                append(videoFilter.label)
                            } else {
                                append("Kecerahan")
                            }
                            if (abs(brightness) >= 0.01f) {
                                val pct = (brightness * 200f).toInt()
                                val sign = if (pct > 0) "+" else ""
                                append(" • $sign$pct%")
                            }
                        }
                        Text(
                            text = badgeLabel,
                            style = MonoTimecodeStyle,
                            color = FuturisticCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Top-end anti-buff status chip
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(0xB8070914),
                    border = BorderStroke(1.dp, StudioEmerald.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = StudioEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isBuffering) "Buffering…" else "Anti-Buff",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isBuffering) FuturisticCyan else StudioEmerald
                        )
                    }
                }

                // Center Play/Pause or Buffering indicator
                if (playbackError != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xDC0E1328),
                        border = BorderStroke(1.dp, NeonPurple)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = playbackError ?: "Koneksi video terganggu",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
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
                                Text("Muat Ulang", color = FuturisticCyan)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .alpha(overlayAlpha)
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xD8151C38),
                                        Color(0xB0070914)
                                    )
                                )
                            )
                            .border(1.2.dp, FuturisticCyan.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = FuturisticCyan,
                                strokeWidth = 2.6.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda pemutaran" else "Putar video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Transport, Buffer Track & Seek Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val safeDuration = durationMs.coerceAtLeast(1L)
                val bufferedFrac = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    // Buffered cache indicator bar behind slider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CosmicSurfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bufferedFrac)
                                .fillMaxHeight()
                                .background(FuturisticCyan.copy(alpha = 0.35f))
                        )
                    }

                    Slider(
                        value = currentPositionMs.coerceIn(0L, safeDuration).toFloat(),
                        onValueChange = { pos ->
                            activeClipEndMs = null
                            isScrubbing = true
                            currentPositionMs = pos.toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(currentPositionMs)
                            onPositionChanged(currentPositionMs)
                            isScrubbing = false
                        },
                        valueRange = 0f..safeDuration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = FuturisticCyan,
                            activeTrackColor = NeonPurple,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("main_video_seek_slider")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                activeClipEndMs = null
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0L)
                                    }
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("main_video_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar",
                                tint = FuturisticCyan
                            )
                        }
                        IconButton(
                            onClick = {
                                activeClipEndMs = null
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                onPositionChanged(target)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Mundur 10 detik",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                activeClipEndMs = null
                                val maxDur = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                onPositionChanged(target)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Maju 10 detik",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${formatTimecode(currentPositionMs)} / ${formatTimecode(durationMs)}",
                            style = MonoTimecodeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val currentSpeed = PLAYBACK_SPEEDS[speedIndex]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CosmicSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (currentSpeed > 1f) FuturisticCyan.copy(alpha = 0.65f) else CosmicOutline
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    speedIndex = (speedIndex + 1) % PLAYBACK_SPEEDS.size
                                    exoPlayer.setPlaybackSpeed(PLAYBACK_SPEEDS[speedIndex])
                                }
                                .minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = if (currentSpeed == 1.0f) "1x" else "${currentSpeed}x",
                                style = MonoTimecodeStyle.copy(fontWeight = FontWeight.Bold),
                                color = if (currentSpeed > 1f) FuturisticCyan else Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("main_video_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (isMuted) "Bunyikan audio" else "Bisukan audio",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
