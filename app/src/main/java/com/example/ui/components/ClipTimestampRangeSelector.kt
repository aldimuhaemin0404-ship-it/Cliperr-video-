package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.MonoTimecodeStyle
import kotlin.math.abs

/**
 * Slider-based UI component placed directly below the Media3 ExoPlayer
 * allowing users to visually select start and end timestamps on the filmstrip,
 * fine-tune by 0.5s increments, snap to the live playhead, and preview or export the clip.
 */
@Composable
fun ClipTimestampRangeSelector(
    thumbnails: List<Bitmap>,
    totalDurationMs: Long,
    startMs: Long,
    endMs: Long,
    playheadPositionMs: Long,
    isExporting: Boolean,
    onRangeChanged: (startMs: Long, endMs: Long) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onPreviewClipRange: () -> Unit,
    onExportClipRange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeTotalMs = totalDurationMs.coerceAtLeast(1_500L)
    val clampedStart = startMs.coerceIn(0L, (safeTotalMs - 1_000L).coerceAtLeast(0L))
    val clampedEnd = endMs.coerceIn((clampedStart + 1_000L).coerceAtMost(safeTotalMs), safeTotalMs)
    val clipDurationMs = (clampedEnd - clampedStart).coerceAtLeast(0L)

    var lastReportedStart by remember { mutableLongStateOf(clampedStart) }
    var lastReportedEnd by remember { mutableLongStateOf(clampedEnd) }

    val presetDurationsSec = remember(safeTotalMs) {
        listOf(15, 30, 45, 60).filter { it * 1000L < safeTotalMs }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("clip_timestamp_range_selector"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: Title + live selected duration badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.studio_range_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Durasi ${formatDurationShort(clipDurationMs)}",
                        style = MonoTimecodeStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("clip_range_duration_label")
                    )
                }
            }

            // Filmstrip timeline with active range bracket + live ExoPlayer playhead line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                FilmstripTimelineBar(
                    thumbnails = thumbnails,
                    totalDurationMs = safeTotalMs,
                    startMs = clampedStart,
                    endMs = clampedEnd,
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val playheadFrac = (playheadPositionMs.toFloat() / safeTotalMs.toFloat()).coerceIn(0f, 1f)
                    val x = size.width * playheadFrac
                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // Dual-handle RangeSlider for visual start & end selection
            RangeSlider(
                value = clampedStart.toFloat()..clampedEnd.toFloat(),
                onValueChange = { range ->
                    val rawStart = range.start.toLong().coerceIn(0L, (safeTotalMs - 1_000L).coerceAtLeast(0L))
                    val rawEnd = range.endInclusive.toLong().coerceIn(
                        (rawStart + 1_000L).coerceAtMost(safeTotalMs),
                        safeTotalMs
                    )
                    val startMoved = abs(rawStart - lastReportedStart) >= abs(rawEnd - lastReportedEnd)
                    lastReportedStart = rawStart
                    lastReportedEnd = rawEnd
                    onRangeChanged(rawStart, rawEnd)
                    onSeekPreview(if (startMoved) rawStart else rawEnd)
                },
                valueRange = 0f..safeTotalMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("studio_clip_range_slider")
            )

            // Quick duration preset chips
            if (presetDurationsSec.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetDurationsSec.forEach { sec ->
                        val targetEnd = (clampedStart + sec * 1000L).coerceAtMost(safeTotalMs)
                        val isSelected = abs((clampedEnd - clampedStart) - sec * 1000L) <= 500L
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onRangeChanged(clampedStart, targetEnd)
                                onSeekPreview(targetEnd)
                            },
                            label = { Text("${sec}d") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                    FilterChip(
                        selected = clampedStart == 0L && clampedEnd == safeTotalMs,
                        onClick = {
                            onRangeChanged(0L, safeTotalMs)
                            onSeekPreview(0L)
                        },
                        label = { Text("Penuh") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }

            // Start & End Timestamp Precision Panels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimestampHandleCard(
                    label = stringResource(R.string.studio_range_start_label),
                    timeMs = clampedStart,
                    onStepMinus = {
                        val next = (clampedStart - 500L).coerceAtLeast(0L)
                        onRangeChanged(next, clampedEnd)
                        onSeekPreview(next)
                    },
                    onStepPlus = {
                        val next = (clampedStart + 500L).coerceAtMost((clampedEnd - 1_000L).coerceAtLeast(0L))
                        onRangeChanged(next, clampedEnd)
                        onSeekPreview(next)
                    },
                    onSnapFromPlayhead = {
                        val next = playheadPositionMs.coerceIn(0L, (clampedEnd - 1_000L).coerceAtLeast(0L))
                        onRangeChanged(next, clampedEnd)
                    },
                    testTagPrefix = "start",
                    modifier = Modifier.weight(1f)
                )

                TimestampHandleCard(
                    label = stringResource(R.string.studio_range_end_label),
                    timeMs = clampedEnd,
                    onStepMinus = {
                        val next = (clampedEnd - 500L).coerceAtLeast((clampedStart + 1_000L).coerceAtMost(safeTotalMs))
                        onRangeChanged(clampedStart, next)
                        onSeekPreview(next)
                    },
                    onStepPlus = {
                        val next = (clampedEnd + 500L).coerceAtMost(safeTotalMs)
                        onRangeChanged(clampedStart, next)
                        onSeekPreview(next)
                    },
                    onSnapFromPlayhead = {
                        val next = playheadPositionMs.coerceIn((clampedStart + 1_000L).coerceAtMost(safeTotalMs), safeTotalMs)
                        onRangeChanged(clampedStart, next)
                    },
                    testTagPrefix = "end",
                    modifier = Modifier.weight(1f)
                )
            }

            // Direct Preview & Export actions for the chosen range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPreviewClipRange,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("preview_clip_range_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.studio_range_preview_clip))
                }

                Button(
                    onClick = onExportClipRange,
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("export_manual_range_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.studio_range_export_now),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimestampHandleCard(
    label: String,
    timeMs: Long,
    onStepMinus: () -> Unit,
    onStepPlus: () -> Unit,
    onSnapFromPlayhead: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimecode(timeMs),
                    style = MonoTimecodeStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("${testTagPrefix}_timecode_text")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onStepMinus,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_minus_button")
                ) {
                    Text("-0.5s", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onStepPlus,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_plus_button")
                ) {
                    Text("+0.5s", style = MaterialTheme.typography.labelMedium)
                }
            }

            TextButton(
                onClick = onSnapFromPlayhead,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("${testTagPrefix}_snap_button")
            ) {
                Text(
                    text = stringResource(R.string.studio_range_set_playhead),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
