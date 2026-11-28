package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BatchItemStatus
import com.example.domain.model.BatchQueueConfig
import com.example.domain.model.BatchQueueItem
import com.example.domain.model.ExportJobStatus
import com.example.domain.model.ExportRenderJob
import com.example.domain.model.TargetPlatform
import com.example.domain.model.VideoFilterPreset
import com.example.ui.components.AiDemoNoticeBanner
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.ActionButtonGradient
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicOutline
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchQueueScreen(
    queueItems: List<BatchQueueItem>,
    config: BatchQueueConfig,
    isBatchRunning: Boolean,
    exportRenderJobs: List<ExportRenderJob> = emptyList(),
    isRenderQueueRunning: Boolean = false,
    onCancelExportJob: (String) -> Unit = {},
    onRetryExportJob: (String) -> Unit = {},
    onRemoveExportJob: (String) -> Unit = {},
    onClearCompletedExports: () -> Unit = {},
    onOpenExportsGallery: () -> Unit = {},
    onBack: () -> Unit,
    onEnqueueGalleryVideos: (List<Uri>) -> Unit,
    onEnqueueSocialLinks: (String) -> Unit,
    onUpdatePlatform: (TargetPlatform) -> Unit,
    onUpdateFilter: (VideoFilterPreset) -> Unit,
    onUpdateAutoExport: (Boolean) -> Unit,
    onUpdateWatermark: (Boolean) -> Unit = {},
    onStartBatch: () -> Unit,
    onCancelBatch: () -> Unit,
    onRetryItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onShareExportedClip: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTab by rememberSaveable {
        mutableIntStateOf(if (exportRenderJobs.isEmpty() && queueItems.isNotEmpty()) 1 else 0)
    }
    var showLinksInput by remember { mutableStateOf(false) }
    var multiLinksText by remember { mutableStateOf("") }

    val multiVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onEnqueueGalleryVideos(uris)
                selectedTab = 1
            }
        }
    )

    val completedCount = remember(queueItems) {
        queueItems.count { it.status == BatchItemStatus.COMPLETED }
    }
    val pendingCount = remember(queueItems) {
        queueItems.count { it.status == BatchItemStatus.QUEUED }
    }
    val failedCount = remember(queueItems) {
        queueItems.count { it.status == BatchItemStatus.FAILED || it.status == BatchItemStatus.CANCELLED }
    }
    val overallProgress = remember(queueItems) {
        if (queueItems.isEmpty()) 0f
        else queueItems.sumOf { it.progressPercent }.toFloat() / (queueItems.size * 100f)
    }

    val renderCompletedCount = remember(exportRenderJobs) {
        exportRenderJobs.count { it.status == ExportJobStatus.COMPLETED }
    }
    val renderPendingCount = remember(exportRenderJobs) {
        exportRenderJobs.count { it.status == ExportJobStatus.QUEUED || it.status == ExportJobStatus.RENDERING }
    }
    val renderFailedCount = remember(exportRenderJobs) {
        exportRenderJobs.count { it.status == ExportJobStatus.FAILED || it.status == ExportJobStatus.CANCELLED }
    }
    val renderOverallProgress = remember(exportRenderJobs) {
        if (exportRenderJobs.isEmpty()) 0f
        else exportRenderJobs.sumOf { it.progressPercent }.toFloat() / (exportRenderJobs.size * 100f)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .testTag("batch_queue_screen"),
        containerColor = CosmicBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.batch_queue_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("batch_queue_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    val canClear = if (selectedTab == 0) renderCompletedCount > 0 else completedCount > 0
                    if (canClear) {
                        IconButton(
                            onClick = {
                                if (selectedTab == 0) onClearCompletedExports() else onClearCompleted()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("batch_clear_completed_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = if (selectedTab == 0) {
                                    stringResource(R.string.export_queue_clear_done)
                                } else {
                                    stringResource(R.string.batch_queue_clear_done)
                                },
                                tint = FuturisticCyan
                            )
                        }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = {
                                Text(
                                    text = "${stringResource(R.string.export_queue_tab_render)} (${exportRenderJobs.size})",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple.copy(alpha = 0.28f),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("queue_tab_render")
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = {
                                Text(
                                    text = "${stringResource(R.string.export_queue_tab_ai)} (${queueItems.size})",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FuturisticCyan.copy(alpha = 0.24f),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("queue_tab_ai")
                        )
                    }
                }

                if (selectedTab == 0) {
                    item {
                        ExportRenderSummaryCard(
                            totalCount = exportRenderJobs.size,
                            completedCount = renderCompletedCount,
                            pendingCount = renderPendingCount,
                            failedCount = renderFailedCount,
                            overallProgress = renderOverallProgress,
                            isRenderQueueRunning = isRenderQueueRunning,
                            onClearCompleted = onClearCompletedExports,
                            onOpenExportsGallery = onOpenExportsGallery
                        )
                    }

                    if (exportRenderJobs.isEmpty()) {
                        item {
                            EmptyExportRenderQueueCard(onBack = onBack)
                        }
                    } else {
                        itemsIndexed(exportRenderJobs, key = { _, job -> job.id }) { index, job ->
                            ExportRenderJobCard(
                                index = index + 1,
                                job = job,
                                onCancel = { onCancelExportJob(job.id) },
                                onRetry = { onRetryExportJob(job.id) },
                                onRemove = { onRemoveExportJob(job.id) },
                                onOpenExportsGallery = onOpenExportsGallery,
                                onShareExport = { exportId -> onShareExportedClip(exportId) }
                            )
                        }
                    }
                } else {
                    // Summary & Upload Controls Card
                    item {
                        BatchQueueControlsCard(
                            totalCount = queueItems.size,
                            completedCount = completedCount,
                            pendingCount = pendingCount,
                            failedCount = failedCount,
                            overallProgress = overallProgress,
                            isBatchRunning = isBatchRunning,
                            showLinksInput = showLinksInput,
                            multiLinksText = multiLinksText,
                            config = config,
                            onPickMultipleVideos = {
                                multiVideoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onToggleLinksInput = { showLinksInput = !showLinksInput },
                            onLinksTextChanged = { multiLinksText = it },
                            onSubmitLinks = {
                                onEnqueueSocialLinks(multiLinksText)
                                multiLinksText = ""
                                showLinksInput = false
                            },
                            onUpdatePlatform = onUpdatePlatform,
                            onUpdateFilter = onUpdateFilter,
                            onUpdateAutoExport = onUpdateAutoExport,
                            onUpdateWatermark = onUpdateWatermark,
                            onStartBatch = onStartBatch,
                            onCancelBatch = onCancelBatch
                        )
                    }

                    item {
                        AiDemoNoticeBanner(compact = true)
                    }

                    if (queueItems.isEmpty()) {
                        item {
                            EmptyBatchQueueCard(
                                onPickMultipleVideos = {
                                    multiVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            )
                        }
                    } else {
                        itemsIndexed(queueItems, key = { _, item -> item.id }) { index, item ->
                            BatchQueueItemCard(
                                index = index + 1,
                                item = item,
                                onRetry = { onRetryItem(item.id) },
                                onRemove = { onRemoveItem(item.id) },
                                onOpenProject = { projectId -> onOpenProject(projectId) },
                                onShareExport = { exportId -> onShareExportedClip(exportId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchQueueControlsCard(
    totalCount: Int,
    completedCount: Int,
    pendingCount: Int,
    failedCount: Int,
    overallProgress: Float,
    isBatchRunning: Boolean,
    showLinksInput: Boolean,
    multiLinksText: String,
    config: BatchQueueConfig,
    onPickMultipleVideos: () -> Unit,
    onToggleLinksInput: () -> Unit,
    onLinksTextChanged: (String) -> Unit,
    onSubmitLinks: () -> Unit,
    onUpdatePlatform: (TargetPlatform) -> Unit,
    onUpdateFilter: (VideoFilterPreset) -> Unit,
    onUpdateAutoExport: (Boolean) -> Unit,
    onUpdateWatermark: (Boolean) -> Unit,
    onStartBatch: () -> Unit,
    onCancelBatch: () -> Unit
) {
    val animatedOverall by animateFloatAsState(
        targetValue = overallProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "batchOverallProgress"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Queue Stats Header
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
                        imageVector = Icons.Filled.QueuePlayNext,
                        contentDescription = null,
                        tint = FuturisticCyan
                    )
                    Text(
                        text = "Ringkasan Antrean ($totalCount Video)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Text(
                    text = "${(animatedOverall * 100).toInt()}%",
                    style = MonoTimecodeStyle,
                    color = FuturisticCyan
                )
            }

            if (totalCount > 0) {
                LinearProgressIndicator(
                    progress = { animatedOverall },
                    color = FuturisticCyan,
                    trackColor = CosmicSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("batch_overall_progress_bar")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueStatChip("Selesai: $completedCount", StudioEmerald)
                    QueueStatChip("Menunggu: $pendingCount", StudioAmber)
                    if (failedCount > 0) {
                        QueueStatChip("Perlu Ulang: $failedCount", StudioAmber)
                    }
                }
            }

            // Add Videos / Links Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickMultipleVideos,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("batch_pick_multiple_videos_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.batch_queue_pick_multiple),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = onToggleLinksInput,
                    border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("batch_toggle_links_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddLink,
                        contentDescription = null,
                        tint = FuturisticCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.batch_queue_add_links),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = FuturisticCyan,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            AnimatedVisibility(visible = showLinksInput) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = multiLinksText,
                        onValueChange = onLinksTextChanged,
                        label = { Text(stringResource(R.string.batch_queue_links_hint)) },
                        placeholder = { Text("https://youtu.be/...\nhttps://vt.tiktok.com/...") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_multi_links_input")
                    )
                    Button(
                        onClick = onSubmitLinks,
                        enabled = multiLinksText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FuturisticCyan,
                            contentColor = Color(0xFF001F2A)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("batch_enqueue_links_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.batch_queue_enqueue_links),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Target Platform & Filter presets for newly queued items
            Text(
                text = "Pengaturan Default Antrean Baru:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TargetPlatform.entries.forEach { platform ->
                    val selected = platform == config.targetPlatform
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdatePlatform(platform) },
                        label = { Text(platform.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple.copy(alpha = 0.28f),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VideoFilterPreset.entries.forEach { filter ->
                    val selected = filter == config.videoFilter
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateFilter(filter) },
                        label = { Text("Filter: ${filter.label}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FuturisticCyan.copy(alpha = 0.22f),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !config.includeWatermark,
                    onClick = { onUpdateWatermark(false) },
                    label = { Text(stringResource(R.string.watermark_none_label)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FuturisticCyan.copy(alpha = 0.22f),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("batch_watermark_none_chip")
                )
                FilterChip(
                    selected = config.includeWatermark,
                    onClick = { onUpdateWatermark(true) },
                    label = { Text(stringResource(R.string.watermark_brand_label)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonPurple.copy(alpha = 0.28f),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("batch_watermark_brand_chip")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.batch_queue_auto_export_toggle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Switch(
                    checked = config.autoExportTopClip,
                    onCheckedChange = onUpdateAutoExport,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPurple
                    ),
                    modifier = Modifier.testTag("batch_auto_export_switch")
                )
            }

            // Start / Stop Batch CTA
            if (isBatchRunning) {
                Button(
                    onClick = onCancelBatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF421226),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, NeonMagenta),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("batch_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        tint = NeonMagenta,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.batch_queue_stop_all),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                val enabled = pendingCount > 0
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = enabled, onClick = onStartBatch)
                        .testTag("batch_start_all_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (enabled) ActionButtonGradient
                                else androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(CosmicSurfaceElevated, CosmicSurfaceElevated)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (pendingCount > 0) {
                                    "${stringResource(R.string.batch_queue_start_all)} ($pendingCount)"
                                } else {
                                    stringResource(R.string.batch_queue_start_all)
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueStatChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BatchQueueItemCard(
    index: Int,
    item: BatchQueueItem,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onShareExport: (Long) -> Unit
) {
    val statusColor = when (item.status) {
        BatchItemStatus.QUEUED -> StudioAmber
        BatchItemStatus.INSPECTING,
        BatchItemStatus.ANALYZING -> FuturisticCyan
        BatchItemStatus.RENDERING -> NeonPurple
        BatchItemStatus.COMPLETED -> StudioEmerald
        BatchItemStatus.FAILED,
        BatchItemStatus.CANCELLED -> NeonMagenta
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (item.progressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "itemProgress_${item.id}"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(
            width = if (item.isActive) 1.5.dp else 1.dp,
            color = if (item.isActive) statusColor else CosmicOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("batch_item_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (item.status) {
                        BatchItemStatus.COMPLETED -> Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = item.status.label,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        BatchItemStatus.FAILED -> Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = item.status.label,
                            tint = StudioAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        else -> Text(
                            text = "#$index",
                            style = MonoTimecodeStyle,
                            color = statusColor
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sourceLabel,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val durSuffix = if (item.durationMs > 0L) " • ${formatDurationShort(item.durationMs)}" else ""
                    val wmSuffix = if (item.includeWatermark) " • Watermark" else " • Tanpa Watermark"
                    Text(
                        text = "${item.targetPlatform.displayName} • Filter ${item.videoFilter.label}$wmSuffix$durSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = item.status.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (!item.isActive) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("batch_remove_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Hapus dari antrean",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Per-item Progress Bar + Percentage + Live Stage Detail
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.errorMessage ?: item.stageDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == BatchItemStatus.FAILED) StudioAmber else Color.White.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(
                        text = "${item.progressPercent}%",
                        style = MonoTimecodeStyle,
                        color = statusColor
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = statusColor,
                    trackColor = CosmicSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("batch_item_progress_${item.id}")
                )
            }

            // Item Actions when completed or failed
            if (item.status == BatchItemStatus.COMPLETED && item.createdProjectId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onOpenProject(item.createdProjectId) },
                        border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.65f)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("batch_view_clips_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = FuturisticCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.batch_queue_view_clips)} (${item.generatedClipsCount})",
                            color = FuturisticCyan
                        )
                    }

                    if (item.exportedClipId != null) {
                        Button(
                            onClick = { onShareExport(item.exportedClipId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("batch_share_clip_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.exports_share))
                        }
                    }
                }
            } else if (item.status == BatchItemStatus.FAILED || item.status == BatchItemStatus.CANCELLED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        border = BorderStroke(1.dp, StudioAmber),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("batch_retry_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = StudioAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.batch_queue_retry),
                            color = StudioAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBatchQueueCard(
    onPickMultipleVideos: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, CosmicOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = NeonPurple.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = FuturisticCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.batch_queue_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.batch_queue_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = onPickMultipleVideos,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(
                    text = stringResource(R.string.batch_queue_pick_multiple),
                    color = FuturisticCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExportRenderSummaryCard(
    totalCount: Int,
    completedCount: Int,
    pendingCount: Int,
    failedCount: Int,
    overallProgress: Float,
    isRenderQueueRunning: Boolean,
    onClearCompleted: () -> Unit,
    onOpenExportsGallery: () -> Unit
) {
    val animatedOverall by animateFloatAsState(
        targetValue = overallProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 280),
        label = "renderOverallProgress"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.export_queue_banner_title)} ($totalCount Klip)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = if (isRenderQueueRunning) {
                            "Merender berurutan di latar belakang… Anda bebas mengedit klip lain."
                        } else if (pendingCount > 0) {
                            "$pendingCount klip siap diproses berurutan."
                        } else {
                            "Antrekan ekspor dari Editor, Ekspor HD, atau Proyek."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(animatedOverall * 100).toInt()}%",
                    style = MonoTimecodeStyle,
                    color = FuturisticCyan
                )
            }

            if (totalCount > 0) {
                LinearProgressIndicator(
                    progress = { animatedOverall },
                    color = FuturisticCyan,
                    trackColor = CosmicSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("export_queue_overall_progress")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueStatChip("Selesai: $completedCount", StudioEmerald)
                    QueueStatChip("Menunggu/Render: $pendingCount", StudioAmber)
                    if (failedCount > 0) {
                        QueueStatChip("Perlu Ulang: $failedCount", StudioAmber)
                    }
                }
            }

            if (completedCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearCompleted,
                        border = BorderStroke(1.dp, CosmicOutline),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_queue_clear_done_button")
                    ) {
                        Text(
                            text = stringResource(R.string.export_queue_clear_done),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = onOpenExportsGallery,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_queue_open_gallery_button")
                    ) {
                        Text(
                            text = stringResource(R.string.export_queue_open_exports),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportRenderJobCard(
    index: Int,
    job: ExportRenderJob,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenExportsGallery: () -> Unit,
    onShareExport: (Long) -> Unit
) {
    val statusColor = when (job.status) {
        ExportJobStatus.QUEUED -> StudioAmber
        ExportJobStatus.RENDERING -> FuturisticCyan
        ExportJobStatus.COMPLETED -> StudioEmerald
        ExportJobStatus.FAILED,
        ExportJobStatus.CANCELLED -> NeonMagenta
    }
    val animatedProgress by animateFloatAsState(
        targetValue = (job.progressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250),
        label = "exportJob_${job.id}"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(
            width = if (job.isActive) 1.5.dp else 1.dp,
            color = if (job.isActive) statusColor else CosmicOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export_job_card_${job.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (job.status) {
                        ExportJobStatus.COMPLETED -> Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = job.status.label,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        ExportJobStatus.FAILED,
                        ExportJobStatus.CANCELLED -> Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = job.status.label,
                            tint = StudioAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        else -> Text(
                            text = "#$index",
                            style = MonoTimecodeStyle,
                            color = statusColor
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.segmentSnapshot.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${job.projectTitle} • ${job.segmentSnapshot.aspectRatio.label} • ${job.segmentSnapshot.exportQuality.label} • ${job.segmentSnapshot.videoFilter.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = job.status.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (!job.isPendingOrActive) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_job_remove_${job.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Hapus tugas ekspor",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = job.errorMessage ?: job.stageDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (job.status == ExportJobStatus.FAILED) StudioAmber else Color.White.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(
                        text = "${job.progressPercent}%",
                        style = MonoTimecodeStyle,
                        color = statusColor
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = statusColor,
                    trackColor = CosmicSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            if (job.isPendingOrActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.75f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_job_cancel_${job.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            tint = NeonMagenta,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.editor_cancel_export),
                            color = NeonMagenta
                        )
                    }
                }
            } else if (job.status == ExportJobStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenExportsGallery,
                        border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = stringResource(R.string.export_queue_open_exports),
                            color = FuturisticCyan
                        )
                    }
                    val exportedId = job.exportedClip?.id
                    if (exportedId != null && exportedId > 0L) {
                        Button(
                            onClick = { onShareExport(exportedId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_job_share_${job.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.exports_share))
                        }
                    }
                }
            } else if (job.status == ExportJobStatus.FAILED || job.status == ExportJobStatus.CANCELLED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        border = BorderStroke(1.dp, StudioAmber),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_job_retry_${job.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = StudioAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.batch_queue_retry),
                            color = StudioAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyExportRenderQueueCard(onBack: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, CosmicOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = FuturisticCyan.copy(alpha = 0.16f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.QueuePlayNext,
                        contentDescription = null,
                        tint = FuturisticCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.export_queue_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.export_queue_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(
                    text = "Kembali Mengedit",
                    color = FuturisticCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
