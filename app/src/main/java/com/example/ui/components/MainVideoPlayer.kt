package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.domain.model.VideoFilterPreset
import com.example.media.VideoFilterEffects
import com.example.ui.theme.MonoTimecodeStyle
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Main-screen Media3 ExoPlayer component for displaying and playing
 * user-selected video files with transport controls, scrubbing, audio toggle,
 * and external seek/clip-preview hooks for the timestamp range slider.
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
    var isMuted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var activeClipEndMs by remember { mutableStateOf<Long?>(null) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    LaunchedEffect(videoUri) {
        activeClipEndMs = null
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
            exoPlayer.seekTo(startMs.coerceAtLeast(0L))
            exoPlayer.play()
            isPlaying = true
            currentPositionMs = startMs
            onPositionChanged(startMs)
        }
    }

    LaunchedEffect(exoPlayer, isScrubbing, activeClipEndMs) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            val reportedDuration = exoPlayer.duration
            if (reportedDuration > 0L) {
                durationMs = reportedDuration
            }
            if (!isScrubbing) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPositionMs = pos
                onPositionChanged(pos)
                val clipEnd = activeClipEndMs
                if (clipEnd != null && exoPlayer.isPlaying && pos >= clipEnd) {
                    exoPlayer.pause()
                    isPlaying = false
                    activeClipEndMs = null
                }
            }
            delay(150L)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val safeRatio = remember(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(9f / 16f, 16f / 9f)
        } else {
            16f / 9f
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_video_player"),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(safeRatio)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Black)
                    .clickable {
                        activeClipEndMs = null
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0L)
                            }
                            exoPlayer.play()
                        }
                        isPlaying = exoPlayer.isPlaying
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

                // Center Play / Pause overlay button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = if (isPlaying) 0.30f else 0.68f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda pemutaran" else "Putar video",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (videoFilter != VideoFilterPreset.NONE || abs(brightness) >= 0.01f) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.68f)
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
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Transport & Seek Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
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
                    valueRange = 0f..sliderMax,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("main_video_seek_slider")
                )

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
                                isPlaying = exoPlayer.isPlaying
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("main_video_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar",
                                tint = MaterialTheme.colorScheme.primary
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
                        Text(
                            text = "${formatTimecode(currentPositionMs)} / ${formatTimecode(durationMs)}",
                            style = MonoTimecodeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
