package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.VideoFilterPreset
import com.example.ui.theme.MonoTimecodeStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VideoFilterControlsCard(
    selectedFilter: VideoFilterPreset,
    brightness: Float,
    onFilterSelected: (VideoFilterPreset) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeBrightness = brightness.coerceIn(-0.5f, 0.5f)
    val brightnessPercent = (safeBrightness * 200f).roundToInt() // -100% .. +100% display scale
    val hasActiveModification = selectedFilter != VideoFilterPreset.NONE || abs(safeBrightness) >= 0.01f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_filter_controls_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.editor_filters_section),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (hasActiveModification) {
                    TextButton(
                        onClick = onResetFilters,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reset_video_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(stringResource(R.string.editor_filter_reset))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoFilterPreset.entries.forEach { preset ->
                    val selected = preset == selectedFilter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterSelected(preset) },
                        leadingIcon = {
                            FilterPresetSwatch(preset = preset)
                        },
                        label = { Text(preset.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("video_filter_chip_${preset.name.lowercase()}")
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            imageVector = Icons.Filled.Brightness6,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.editor_brightness_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        val sign = if (brightnessPercent > 0) "+" else ""
                        Text(
                            text = "$sign$brightnessPercent%",
                            style = MonoTimecodeStyle,
                            color = if (brightnessPercent == 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Slider(
                    value = safeBrightness,
                    onValueChange = { onBrightnessChanged(it) },
                    valueRange = -0.5f..0.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_brightness_slider")
                )
            }
        }
    }
}

@Composable
private fun FilterPresetSwatch(preset: VideoFilterPreset) {
    when (preset) {
        VideoFilterPreset.NONE -> Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF00E5FF), Color(0xFFFF4D6D))
                    )
                )
        )
        VideoFilterPreset.GRAYSCALE -> Icon(
            imageVector = Icons.Filled.FilterBAndW,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        VideoFilterPreset.SEPIA -> Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFF9C7148))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
        VideoFilterPreset.VIVID -> Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF2A85), Color(0xFFFFB703), Color(0xFF00E5FF))
                    )
                )
        )
        VideoFilterPreset.WARM -> Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF8C42))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
        VideoFilterPreset.COOL -> Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFF3FA9F5))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
    }
}
