package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportProgressState
import com.example.domain.model.ExportQuality
import com.example.domain.model.ExportedClip
import com.example.ui.state.EditorUiState
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSettingsScreen(
    editorState: EditorUiState,
    exportProgress: ExportProgressState,
    autoOpenShareSheet: Boolean,
    onBack: () -> Unit,
    onSelectAspectRatio: (AspectRatioPreset) -> Unit,
    onSelectQuality: (ExportQuality) -> Unit,
    onToggleSubtitles: (Boolean) -> Unit,
    onToggleIncludeAudio: (Boolean) -> Unit,
    onToggleAutoOpenShareSheet: (Boolean) -> Unit,
    onStartExport: (ClipSegment, String) -> Unit,
    onCancelExport: () -> Unit,
    onExportCompleted: (ExportedClip) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val project = editorState.project
    val segment = editorState.segment

    LaunchedEffect(exportProgress) {
        if (exportProgress is ExportProgressState.Completed) {
            onExportCompleted(exportProgress.exportedClip)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("export_settings_screen"),
        containerColor = CosmicBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.export_settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CosmicBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (segment == null || project == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 620.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Format Video 3-card row matching design
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = stringResource(R.string.export_settings_format),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            val formatOptions = listOf(
                                Triple(AspectRatioPreset.VERTICAL_9_16, "9:16\n(TikTok)", Icons.Filled.CropPortrait),
                                Triple(AspectRatioPreset.SQUARE_1_1, "1:1\n(IG)", Icons.Filled.CropSquare),
                                Triple(AspectRatioPreset.LANDSCAPE_16_9, "16:9\n(YouTube)", Icons.Filled.Movie)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                formatOptions.forEach { (preset, label, icon) ->
                                    FormatOptionCard(
                                        label = label,
                                        icon = icon,
                                        selected = segment.aspectRatio == preset,
                                        onClick = { onSelectAspectRatio(preset) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("export_format_${preset.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }

                    // Kualitas 3-pill row matching HD (1080p), 720p, 480p
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = stringResource(R.string.export_settings_quality),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ExportQuality.entries.forEach { quality ->
                                    val selected = segment.exportQuality == quality
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onSelectQuality(quality) }
                                            .testTag("export_quality_${quality.name.lowercase()}"),
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) NeonPurple else CosmicSurface,
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (selected) FuturisticCyan.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = quality.label,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Toggle switches card
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExportSettingToggleRow(
                                icon = Icons.Filled.Subtitles,
                                title = stringResource(R.string.export_settings_subtitle_toggle),
                                checked = segment.showSubtitles,
                                onCheckedChange = onToggleSubtitles,
                                testTag = "export_subtitle_switch"
                            )
                            ExportSettingToggleRow(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                title = stringResource(R.string.export_settings_audio_toggle),
                                checked = segment.includeAudio,
                                onCheckedChange = onToggleIncludeAudio,
                                testTag = "export_audio_switch"
                            )
                            ExportSettingToggleRow(
                                icon = Icons.Filled.Share,
                                title = stringResource(R.string.export_settings_auto_share_toggle),
                                checked = autoOpenShareSheet,
                                onCheckedChange = onToggleAutoOpenShareSheet,
                                testTag = "export_auto_share_switch"
                            )
                        }
                    }

                    if (exportProgress is ExportProgressState.Exporting) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CosmicSurfaceElevated,
                                border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Merender ${segment.exportQuality.label} MP4…",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${exportProgress.progressPercent}%",
                                            style = MonoTimecodeStyle,
                                            color = FuturisticCyan
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { exportProgress.progressPercent / 100f },
                                        color = FuturisticCyan,
                                        trackColor = CosmicSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = onCancelExport,
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text(stringResource(R.string.editor_cancel_export))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (exportProgress is ExportProgressState.Error) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF421226),
                                border = BorderStroke(1.dp, NeonMagenta),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = exportProgress.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    // Primary gradient CTA button: Ekspor Video 🚀
                    item {
                        val isExporting = exportProgress is ExportProgressState.Exporting
                        val enabled = project.hasPlayableStream && !isExporting
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 16.dp)
                                .height(58.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    if (enabled) {
                                        Brush.horizontalGradient(
                                            colors = listOf(FuturisticCyan, NeonPurple, NeonMagenta)
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(CosmicSurfaceElevated, CosmicSurfaceElevated)
                                        )
                                    }
                                )
                                .clickable(enabled = enabled) {
                                    onStartExport(segment, project.sourceUri)
                                }
                                .testTag("confirm_export_video_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.RocketLaunch,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.export_settings_start_cta),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatOptionCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) NeonPurple.copy(alpha = 0.25f) else CosmicSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) NeonPurple else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExportSettingToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CosmicSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CosmicSurfaceElevated
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = FuturisticCyan,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeonPurple
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
