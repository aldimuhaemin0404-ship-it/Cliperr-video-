package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportProgressState
import com.example.domain.model.VideoFilterPreset
import com.example.ui.components.AiDemoNoticeBanner
import com.example.ui.components.ExportProgressBanner
import com.example.ui.components.FilmstripTimelineBar
import com.example.ui.components.SocialLinkPreviewCard
import com.example.ui.components.VideoClipPlayerPreview
import com.example.ui.components.VideoFilterControlsCard
import com.example.ui.components.ViralScoreBadge
import com.example.ui.components.formatDurationShort
import com.example.ui.components.formatTimecode
import com.example.ui.state.EditorUiState
import com.example.ui.theme.ActionButtonGradient
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicOutline
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.NeonPurple

private enum class EditorTool(val label: String) {
    TRIM("Potong"),
    AUDIO("Audio"),
    HOOK("Hook"),
    SUBTITLE("Subtitle"),
    RATIO("Rasio"),
    FILTER("Filter"),
    BRIGHTNESS("Cahaya"),
    RESET("Reset")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipEditorScreen(
    editorState: EditorUiState,
    exportProgress: ExportProgressState,
    onBack: () -> Unit,
    onUpdateTrim: (Long, Long) -> Unit,
    onUpdateAspectRatio: (AspectRatioPreset) -> Unit,
    onUpdateFilter: (VideoFilterPreset) -> Unit,
    onUpdateBrightness: (Float) -> Unit,
    onResetFilters: () -> Unit,
    onToggleSubtitles: (Boolean) -> Unit,
    onToggleIncludeAudio: (Boolean) -> Unit,
    onUpdateHook: (String) -> Unit,
    onUpdateCaption: (String) -> Unit,
    onRegenerateCaption: (CaptionTone) -> Unit,
    onGenerateSubtitles: () -> Unit,
    onUpdateSubtitleCue: (Int, String) -> Unit,
    onSaveChanges: () -> Unit,
    onOpenExportSettings: () -> Unit,
    onAttachLocalVideo: (Long, Uri) -> Unit,
    onCancelExport: () -> Unit,
    onDismissExportBanner: () -> Unit,
    onOpenExports: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var activeTool by remember { mutableStateOf(EditorTool.TRIM) }

    val project = editorState.project
    val segment = editorState.segment

    val localVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && project != null) {
                onAttachLocalVideo(project.id, uri)
            }
        }
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .testTag("clip_editor_screen"),
        containerColor = CosmicBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.editor_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (segment != null) {
                            ViralScoreBadge(score = segment.viralScore)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSaveChanges,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_clip_changes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.editor_save_changes),
                            tint = FuturisticCyan
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
            if (editorState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonPurple)
                }
            } else if (segment == null || project == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = editorState.errorMessage ?: "Klip tidak ditemukan.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        if (project.hasPlayableStream) {
                            VideoClipPlayerPreview(
                                videoUri = Uri.parse(project.sourceUri),
                                aspectRatio = segment.aspectRatio,
                                startMs = segment.startMs,
                                endMs = segment.endMs,
                                videoFilter = segment.videoFilter,
                                brightness = segment.brightness,
                                overlayHook = segment.hookLine,
                                subtitleCues = segment.subtitleCues,
                                showCaptionOverlay = segment.showSubtitles
                            )
                        } else {
                            SocialLinkPreviewCard(
                                title = project.title,
                                platformName = project.sourcePlatformName ?: "Sosial Media",
                                authorName = project.authorName,
                                thumbnailUrl = project.thumbnailUrl,
                                statusNote = "Untuk merender MP4 di perangkat, tautkan file video lokal atau gunakan URL stream langsung (.mp4/.m3u8).",
                                onAttachLocalVideo = {
                                    localVideoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            )
                        }
                    }

                    item {
                        ExportProgressBanner(
                            state = exportProgress,
                            onCancel = onCancelExport,
                            onOpenExports = onOpenExports,
                            onDismiss = onDismissExportBanner
                        )
                    }

                    // 8-Tool Interactive Grid (Screen 6 concept)
                    item {
                        EditorToolsGridCard(
                            activeTool = activeTool,
                            onToolSelected = { tool ->
                                if (tool == EditorTool.RESET) {
                                    onResetFilters()
                                    activeTool = EditorTool.FILTER
                                } else {
                                    activeTool = tool
                                }
                            }
                        )
                    }

                    // Active tool contextual panel placed immediately below the grid
                    when (activeTool) {
                        EditorTool.TRIM -> {
                            item {
                                EditorTrimCard(
                                    segment = segment,
                                    totalDurationMs = project.durationMs.coerceAtLeast(2_000L),
                                    thumbnails = editorState.thumbnails,
                                    onUpdateTrim = onUpdateTrim
                                )
                            }
                        }

                        EditorTool.AUDIO, EditorTool.RATIO -> {
                            item {
                                EditorRatioAudioCard(
                                    segment = segment,
                                    onUpdateAspectRatio = onUpdateAspectRatio,
                                    onToggleIncludeAudio = onToggleIncludeAudio,
                                    onToggleSubtitles = onToggleSubtitles
                                )
                            }
                        }

                        EditorTool.FILTER, EditorTool.BRIGHTNESS, EditorTool.RESET -> {
                            item {
                                VideoFilterControlsCard(
                                    selectedFilter = segment.videoFilter,
                                    brightness = segment.brightness,
                                    onFilterSelected = onUpdateFilter,
                                    onBrightnessChanged = onUpdateBrightness,
                                    onResetFilters = onResetFilters
                                )
                            }
                        }

                        EditorTool.HOOK, EditorTool.SUBTITLE -> {
                            item {
                                EditorHookSubtitleCard(
                                    segment = segment,
                                    editorState = editorState,
                                    onUpdateHook = onUpdateHook,
                                    onUpdateCaption = onUpdateCaption,
                                    onRegenerateCaption = onRegenerateCaption,
                                    onGenerateSubtitles = onGenerateSubtitles,
                                    onUpdateSubtitleCue = onUpdateSubtitleCue
                                )
                            }
                        }
                    }

                    // Bottom primary purple gradient CTA -> Pengaturan Ekspor HD
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSaveChanges()
                                    onOpenExportSettings()
                                }
                                .testTag("editor_save_video_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ActionButtonGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.editor_save_video_cta),
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
}

@Composable
private fun EditorToolsGridCard(
    activeTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit
) {
    val row1 = listOf(
        EditorTool.TRIM to Icons.Filled.ContentCut,
        EditorTool.AUDIO to Icons.AutoMirrored.Filled.VolumeUp,
        EditorTool.HOOK to Icons.Filled.Title,
        EditorTool.SUBTITLE to Icons.Filled.Subtitles
    )
    val row2 = listOf(
        EditorTool.RATIO to Icons.Filled.AspectRatio,
        EditorTool.FILTER to Icons.Filled.Palette,
        EditorTool.BRIGHTNESS to Icons.Filled.Brightness6,
        EditorTool.RESET to Icons.Filled.RestartAlt
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(row1, row2).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTools.forEach { (tool, icon) ->
                    val selected = tool == activeTool
                    EditorToolTile(
                        label = tool.label,
                        icon = icon,
                        selected = selected,
                        onClick = { onToolSelected(tool) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_tool_${tool.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorToolTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) NeonPurple.copy(alpha = 0.24f) else CosmicSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) NeonPurple else CosmicOutline
        ),
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) FuturisticCyan else Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditorTrimCard(
    segment: ClipSegment,
    totalDurationMs: Long,
    thumbnails: List<android.graphics.Bitmap>,
    onUpdateTrim: (Long, Long) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, CosmicOutline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_trim_timeline),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeonPurple.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Durasi ${formatDurationShort(segment.durationMs)}",
                        style = MonoTimecodeStyle,
                        color = FuturisticCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            FilmstripTimelineBar(
                thumbnails = thumbnails,
                totalDurationMs = totalDurationMs,
                startMs = segment.startMs,
                endMs = segment.endMs
            )

            RangeSlider(
                value = segment.startMs.toFloat()..segment.endMs.toFloat(),
                onValueChange = { range ->
                    onUpdateTrim(range.start.toLong(), range.endInclusive.toLong())
                },
                valueRange = 0f..totalDurationMs.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trim_range_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Mulai: ${formatTimecode(segment.startMs)}",
                        style = MonoTimecodeStyle,
                        color = FuturisticCyan
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onUpdateTrim(segment.startMs - 500L, segment.endMs) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text("-0.5s") }
                        OutlinedButton(
                            onClick = { onUpdateTrim(segment.startMs + 500L, segment.endMs) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text("+0.5s") }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Akhir: ${formatTimecode(segment.endMs)}",
                        style = MonoTimecodeStyle,
                        color = FuturisticCyan
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onUpdateTrim(segment.startMs, segment.endMs - 500L) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text("-0.5s") }
                        OutlinedButton(
                            onClick = { onUpdateTrim(segment.startMs, segment.endMs + 500L) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text("+0.5s") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorRatioAudioCard(
    segment: ClipSegment,
    onUpdateAspectRatio: (AspectRatioPreset) -> Unit,
    onToggleIncludeAudio: (Boolean) -> Unit,
    onToggleSubtitles: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, CosmicOutline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.editor_aspect_ratio),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AspectRatioPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = segment.aspectRatio == preset,
                        onClick = { onUpdateAspectRatio(preset) },
                        label = { Text(preset.label) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }

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
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = FuturisticCyan
                    )
                    Text(
                        text = stringResource(R.string.editor_include_audio),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Switch(
                    checked = segment.includeAudio,
                    onCheckedChange = onToggleIncludeAudio,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPurple
                    ),
                    modifier = Modifier.testTag("editor_include_audio_switch")
                )
            }

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
                        imageVector = Icons.Filled.Subtitles,
                        contentDescription = null,
                        tint = NeonPurple
                    )
                    Text(
                        text = stringResource(R.string.editor_show_captions),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Switch(
                    checked = segment.showSubtitles,
                    onCheckedChange = onToggleSubtitles,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPurple
                    ),
                    modifier = Modifier.testTag("toggle_captions_switch")
                )
            }
        }
    }
}

@Composable
private fun EditorHookSubtitleCard(
    segment: ClipSegment,
    editorState: EditorUiState,
    onUpdateHook: (String) -> Unit,
    onUpdateCaption: (String) -> Unit,
    onRegenerateCaption: (CaptionTone) -> Unit,
    onGenerateSubtitles: () -> Unit,
    onUpdateSubtitleCue: (Int, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, CosmicOutline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.editor_hook_caption_section),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            AiDemoNoticeBanner(compact = true)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptionTone.entries.forEach { tone ->
                    FilterChip(
                        selected = segment.tone == tone,
                        onClick = { onRegenerateCaption(tone) },
                        label = { Text(tone.label) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }

            OutlinedTextField(
                value = segment.hookLine,
                onValueChange = onUpdateHook,
                label = { Text(stringResource(R.string.editor_hook_label)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_hook_input")
            )

            OutlinedTextField(
                value = segment.captionText,
                onValueChange = onUpdateCaption,
                label = { Text(stringResource(R.string.editor_caption_label)) },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_caption_input")
            )

            OutlinedButton(
                onClick = { onRegenerateCaption(segment.tone) },
                enabled = !editorState.isRegeneratingCaption,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("regenerate_caption_button")
            ) {
                if (editorState.isRegeneratingCaption) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.editor_regenerate_caption))
            }

            Button(
                onClick = onGenerateSubtitles,
                enabled = !editorState.isGeneratingSubtitles,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple.copy(alpha = 0.25f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, NeonPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("generate_subtitles_button")
            ) {
                if (editorState.isGeneratingSubtitles) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Subtitles,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (editorState.isGeneratingSubtitles) {
                        stringResource(R.string.editor_generating_subtitles)
                    } else {
                        stringResource(R.string.editor_generate_subtitles)
                    }
                )
            }

            if (segment.subtitleCues.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.editor_subtitles_list_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = FuturisticCyan
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_subtitles_list")
                ) {
                    segment.subtitleCues.forEachIndexed { index, cue ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CosmicSurfaceElevated
                            ) {
                                Text(
                                    text = "${formatTimecode(cue.startMs)} – ${formatTimecode(cue.endMs)}",
                                    style = MonoTimecodeStyle,
                                    color = FuturisticCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            OutlinedTextField(
                                value = cue.text,
                                onValueChange = { onUpdateSubtitleCue(index, it) },
                                shape = RoundedCornerShape(12.dp),
                                minLines = 1,
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("subtitle_cue_input_$index")
                            )
                        }
                    }
                }
            }
        }
    }
}
