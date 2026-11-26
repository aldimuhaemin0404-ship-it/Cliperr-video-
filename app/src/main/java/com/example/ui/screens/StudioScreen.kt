package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.network.PopularVideosCatalog
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.PopularVideoItem
import com.example.domain.model.SocialSourcePlatform
import com.example.domain.model.StudioImportSource
import com.example.domain.model.TargetPlatform
import com.example.domain.model.VideoFilterPreset
import com.example.domain.model.VideoMetadata
import com.example.ui.components.AiDemoNoticeBanner
import com.example.ui.components.AiRobotMascot
import com.example.ui.components.AldiLogoClapperboard
import com.example.ui.components.ClipTimestampRangeSelector
import com.example.ui.components.MainVideoPlayer
import com.example.ui.components.SocialLinkPreviewCard
import com.example.ui.components.VideoFilterControlsCard
import com.example.ui.components.formatDurationShort
import com.example.ui.components.formatTimecode
import com.example.ui.state.CloudSyncUiState
import com.example.ui.state.StudioUiState
import com.example.ui.theme.ActionButtonGradient
import com.example.ui.theme.BrandNeonGradient
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicOutline
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticBlue
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioAmber

@Composable
fun StudioScreen(
    studioState: StudioUiState,
    recentProjects: List<ClipProject>,
    isExporting: Boolean,
    batchQueueCount: Int,
    isBatchRunning: Boolean,
    onImportSourceChanged: (StudioImportSource) -> Unit,
    onLinkInputChanged: (String) -> Unit,
    onResolveSocialLink: () -> Unit,
    onEnqueueSingleUrl: (String) -> Unit,
    onVideoPicked: (Uri) -> Unit,
    onEnqueueMultipleVideos: (List<Uri>) -> Unit,
    onOpenBatchQueue: () -> Unit,
    onStudioTrimChanged: (Long, Long) -> Unit,
    onFilterSelected: (VideoFilterPreset) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onResetFilters: () -> Unit,
    onSuggestTimestamps: () -> Unit,
    onExportManualClip: () -> Unit,
    onPlatformSelected: (TargetPlatform) -> Unit,
    onFocusTopicChanged: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onSelectPopularVideo: (PopularVideoItem) -> Unit,
    onSeeAllPopular: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onOpenWebStudio: () -> Unit = {},
    userDisplayName: String? = null,
    cloudSyncState: CloudSyncUiState = CloudSyncUiState.Synced(0),
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedLinkHint by remember { mutableStateOf("https://youtu.be/... atau https://vt.tiktok.com/...") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onVideoPicked(uri)
            }
        }
    )

    val multiVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onEnqueueMultipleVideos(uris)
            }
        }
    )

    val launchPhotoPicker = {
        onImportSourceChanged(StudioImportSource.GALLERY)
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val launchMultiPhotoPicker = {
        multiVideoLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeTopHeader(onOpenWebStudio = onOpenWebStudio)
            }

            if (userDisplayName != null) {
                item {
                    CloudAccountStatusBar(
                        displayName = userDisplayName,
                        cloudSyncState = cloudSyncState,
                        onSignOut = onSignOut
                    )
                }
            }

            item {
                StudioHeroCard(
                    hasSelectedVideo = studioState.selectedVideo != null,
                    isLoadingVideo = studioState.isLoadingVideo,
                    onPickVideo = launchPhotoPicker
                )
            }

            item {
                SourcePickerGrid(
                    activeSource = studioState.importSource,
                    onPickGallery = launchPhotoPicker,
                    onAddCustomUrl = {
                        selectedLinkHint = "https://example.com/video.mp4 atau link sosmed…"
                        onImportSourceChanged(StudioImportSource.SOCIAL_LINK)
                    },
                    onPickYouTube = {
                        selectedLinkHint = "https://youtu.be/... atau youtube.com/watch?v=..."
                        onImportSourceChanged(StudioImportSource.SOCIAL_LINK)
                    },
                    onPickTikTok = {
                        selectedLinkHint = "https://vt.tiktok.com/... atau tiktok.com/@user/video/..."
                        onImportSourceChanged(StudioImportSource.SOCIAL_LINK)
                    },
                    onPickInstagram = {
                        selectedLinkHint = "https://instagram.com/reel/... atau URL video lainnya"
                        onImportSourceChanged(StudioImportSource.SOCIAL_LINK)
                    }
                )
            }

            item {
                BatchQueueHomeCard(
                    queueCount = batchQueueCount,
                    isBatchRunning = isBatchRunning,
                    onPickMultiple = launchMultiPhotoPicker,
                    onOpenQueue = onOpenBatchQueue
                )
            }

            if (studioState.importSource == StudioImportSource.SOCIAL_LINK) {
                item {
                    SocialLinkImportCard(
                        linkInput = studioState.linkInput,
                        placeholderHint = selectedLinkHint,
                        isLoading = studioState.isLoadingVideo,
                        onLinkInputChanged = onLinkInputChanged,
                        onResolveClick = onResolveSocialLink,
                        onEnqueueUrl = { onEnqueueSingleUrl(studioState.linkInput) }
                    )
                }
            }

            item {
                AiDemoNoticeBanner(compact = true)
            }

            if (studioState.errorMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(
                            text = studioState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(visible = studioState.selectedVideo != null) {
                    val video = studioState.selectedVideo
                    if (video != null) {
                        SelectedVideoWorkspaceCard(
                            video = video,
                            thumbnails = studioState.filmstripThumbnails,
                            trimStartMs = studioState.trimStartMs,
                            trimEndMs = studioState.trimEndMs,
                            selectedFilter = studioState.selectedFilter,
                            brightness = studioState.brightness,
                            suggestedHighlights = studioState.suggestedHighlights,
                            isSuggestingTimestamps = studioState.isSuggestingTimestamps,
                            isExporting = isExporting,
                            selectedPlatform = studioState.selectedPlatform,
                            focusTopic = studioState.focusTopic,
                            isAnalyzing = studioState.isAnalyzing,
                            onAttachLocalVideo = launchPhotoPicker,
                            onTrimChanged = onStudioTrimChanged,
                            onFilterSelected = onFilterSelected,
                            onBrightnessChanged = onBrightnessChanged,
                            onResetFilters = onResetFilters,
                            onSuggestTimestamps = onSuggestTimestamps,
                            onExportManualClip = onExportManualClip,
                            onPlatformSelected = onPlatformSelected,
                            onFocusTopicChanged = onFocusTopicChanged,
                            onAnalyzeClick = onAnalyzeClick
                        )
                    }
                }
            }

            // Popular Today carousel
            item {
                PopularTodayCarousel(
                    items = PopularVideosCatalog.items,
                    onSelectPopularVideo = onSelectPopularVideo,
                    onSeeAll = onSeeAllPopular
                )
            }

            item {
                Text(
                    text = stringResource(R.string.studio_recent_projects),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (recentProjects.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MovieCreation,
                                contentDescription = null,
                                tint = NeonPurple
                            )
                            Text(
                                text = stringResource(R.string.studio_no_projects),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentProjects, key = { it.id }) { project ->
                    RecentProjectCard(
                        project = project,
                        onOpen = { onOpenProject(project.id) },
                        onDelete = { onDeleteProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopHeader(
    onOpenWebStudio: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AldiLogoClapperboard(modifier = Modifier.size(38.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${stringResource(R.string.splash_brand_prefix)} ",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.splash_brand_accent),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        brush = BrandNeonGradient
                    )
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = NeonPurple.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.65f)),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onOpenWebStudio)
                .minimumInteractiveComponentSize()
                .testTag("open_web_studio_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = stringResource(R.string.home_open_web_studio),
                    tint = FuturisticCyan,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.home_open_web_studio),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CloudAccountStatusBar(
    displayName: String,
    cloudSyncState: CloudSyncUiState,
    onSignOut: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CosmicSurface,
        border = BorderStroke(
            1.dp,
            when (cloudSyncState) {
                is CloudSyncUiState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                else -> CosmicOutline.copy(alpha = 0.75f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cloud_account_status_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = displayName,
                    tint = FuturisticCyan,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val (syncIcon, syncColor, syncText) = when (cloudSyncState) {
                            is CloudSyncUiState.Loading -> Triple(
                                Icons.Filled.CloudSync,
                                StudioAmber,
                                "Menyinkronkan Cloud Firestore…"
                            )
                            is CloudSyncUiState.Synced -> Triple(
                                Icons.Filled.CloudDone,
                                FuturisticCyan,
                                "${stringResource(R.string.cloud_sync_active)} • ${cloudSyncState.cloudProjectsCount} proyek"
                            )
                            is CloudSyncUiState.Error -> Triple(
                                Icons.Filled.CloudOff,
                                MaterialTheme.colorScheme.error,
                                cloudSyncState.message
                            )
                        }
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = null,
                            tint = syncColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = syncText,
                            style = MaterialTheme.typography.labelSmall,
                            color = syncColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            TextButton(
                onClick = onSignOut,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.account_sign_out),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.account_sign_out),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun StudioHeroCard(
    hasSelectedVideo: Boolean,
    isLoadingVideo: Boolean,
    onPickVideo: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF17103C),
                            Color(0xFF0C1532),
                            Color(0xFF1B0E3B)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = FuturisticCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = stringResource(R.string.studio_hero_badge),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = FuturisticCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.studio_hero_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )

                    Text(
                        text = stringResource(R.string.studio_hero_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.80f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Button(
                        onClick = onPickVideo,
                        enabled = !isLoadingVideo,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pick_video_button")
                    ) {
                        if (isLoadingVideo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (hasSelectedVideo) R.string.studio_change_video else R.string.studio_pick_video
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                AiRobotMascot(modifier = Modifier.size(112.dp))
            }
        }
    }
}

@Composable
private fun SourcePickerGrid(
    activeSource: StudioImportSource,
    onPickGallery: () -> Unit,
    onAddCustomUrl: () -> Unit,
    onPickYouTube: () -> Unit,
    onPickTikTok: () -> Unit,
    onPickInstagram: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_pick_source_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = if (activeSource == StudioImportSource.SOCIAL_LINK) {
                    FuturisticCyan.copy(alpha = 0.22f)
                } else {
                    NeonPurple.copy(alpha = 0.2f)
                },
                border = BorderStroke(
                    1.dp,
                    if (activeSource == StudioImportSource.SOCIAL_LINK) FuturisticCyan else NeonPurple
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAddCustomUrl)
                    .minimumInteractiveComponentSize()
                    .testTag("home_add_url_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddLink,
                        contentDescription = null,
                        tint = FuturisticCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_add_url_cta),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SourceActionTile(
                label = stringResource(R.string.home_source_gallery),
                icon = Icons.Filled.FolderOpen,
                gradientColors = listOf(Color(0xFF1D4ED8), Color(0xFF0284C7)),
                borderColor = if (activeSource == StudioImportSource.GALLERY) FuturisticCyan else CosmicOutline,
                onClick = onPickGallery,
                testTag = "source_tab_gallery",
                modifier = Modifier.weight(1f)
            )
            SourceActionTile(
                label = stringResource(R.string.home_source_youtube),
                icon = Icons.Filled.PlayCircleFilled,
                gradientColors = listOf(Color(0xFFDC2626), Color(0xFF991B1B)),
                borderColor = if (activeSource == StudioImportSource.SOCIAL_LINK) Color(0xFFF87171) else CosmicOutline,
                onClick = onPickYouTube,
                testTag = "source_card_youtube",
                modifier = Modifier.weight(1f)
            )
            SourceActionTile(
                label = stringResource(R.string.home_source_tiktok),
                icon = Icons.Filled.MusicNote,
                gradientColors = listOf(Color(0xFF111827), Color(0xFF1E293B)),
                borderColor = FuturisticCyan.copy(alpha = 0.65f),
                onClick = onPickTikTok,
                testTag = "source_tab_link",
                modifier = Modifier.weight(1f)
            )
            SourceActionTile(
                label = stringResource(R.string.home_source_instagram),
                icon = Icons.Filled.CameraAlt,
                gradientColors = listOf(NeonPurple, NeonMagenta),
                borderColor = NeonMagenta.copy(alpha = 0.7f),
                onClick = onPickInstagram,
                testTag = "source_card_instagram",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SourceActionTile(
    label: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    borderColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradientColors))
                .padding(horizontal = 6.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BatchQueueHomeCard(
    queueCount: Int,
    isBatchRunning: Boolean,
    onPickMultiple: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(
            width = if (isBatchRunning) 1.5.dp else 1.dp,
            color = if (isBatchRunning) FuturisticCyan else NeonPurple.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenQueue)
            .testTag("home_batch_queue_card")
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NeonPurple.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.QueuePlayNext,
                            contentDescription = null,
                            tint = FuturisticCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.batch_queue_home_card_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.batch_queue_home_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (queueCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isBatchRunning) FuturisticCyan.copy(alpha = 0.22f) else NeonPurple.copy(alpha = 0.24f)
                    ) {
                        Text(
                            text = if (isBatchRunning) "Aktif ($queueCount)" else "$queueCount item",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isBatchRunning) FuturisticCyan else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickMultiple,
                    border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("home_pick_multiple_videos_button")
                ) {
                    Text(
                        text = stringResource(R.string.batch_queue_pick_multiple),
                        color = FuturisticCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = onOpenQueue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("home_open_batch_queue_button")
                ) {
                    Text(
                        text = if (queueCount > 0) "Lihat Antrean ($queueCount)" else "Buka Antrean",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun PopularTodayCarousel(
    items: List<PopularVideoItem>,
    onSelectPopularVideo: (PopularVideoItem) -> Unit,
    onSeeAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = StudioAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.home_popular_today),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            TextButton(
                onClick = onSeeAll,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("see_all_popular_button")
            ) {
                Text(
                    text = stringResource(R.string.home_see_all),
                    color = FuturisticCyan,
                    style = MaterialTheme.typography.labelLarge
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = FuturisticCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                    border = BorderStroke(1.dp, CosmicOutline),
                    modifier = Modifier
                        .width(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectPopularVideo(item) }
                        .testTag("popular_carousel_card_${item.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 96.dp, height = 66.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CosmicSurfaceElevated)
                        ) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xCC070914),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = formatDurationShort(item.durationMs),
                                    style = MonoTimecodeStyle,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = platformAccentColor(item.platform).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = item.platform.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = platformAccentColor(item.platform),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.viewsLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = FuturisticBlue,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Muat video",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun platformAccentColor(platform: SocialSourcePlatform): Color = when (platform) {
    SocialSourcePlatform.YOUTUBE -> Color(0xFFFF6B6B)
    SocialSourcePlatform.TIKTOK -> FuturisticCyan
    SocialSourcePlatform.INSTAGRAM -> NeonMagenta
    else -> NeonPurple
}

@Composable
private fun SocialLinkImportCard(
    linkInput: String,
    placeholderHint: String,
    isLoading: Boolean,
    onLinkInputChanged: (String) -> Unit,
    onResolveClick: () -> Unit,
    onEnqueueUrl: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val supportedPlatforms = listOf("YouTube", "TikTok", "Instagram", "Facebook", "X / Twitter", "Threads", "Vimeo", "URL .mp4")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = FuturisticCyan
                )
                Text(
                    text = stringResource(R.string.studio_link_card_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Text(
                text = stringResource(R.string.studio_link_card_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                supportedPlatforms.forEach { label ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = CosmicSurfaceElevated,
                        border = BorderStroke(1.dp, CosmicOutline)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = FuturisticCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = linkInput,
                onValueChange = onLinkInputChanged,
                label = { Text(stringResource(R.string.studio_link_input_label)) },
                placeholder = { Text(placeholderHint) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (linkInput.isNotEmpty()) {
                            IconButton(
                                onClick = { onLinkInputChanged("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Hapus link"
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val pasted = clipboard.getText()?.text.orEmpty()
                                if (pasted.isNotBlank()) {
                                    onLinkInputChanged(pasted)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("paste_link_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.studio_link_paste),
                                tint = FuturisticCyan
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("social_link_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onResolveClick,
                    enabled = linkInput.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("fetch_social_link_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.studio_link_fetching), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.studio_link_fetch_button),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onEnqueueUrl,
                    enabled = linkInput.isNotBlank() && !isLoading,
                    border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .testTag("enqueue_single_url_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueuePlayNext,
                        contentDescription = null,
                        tint = FuturisticCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.home_quick_url_add_batch),
                        color = FuturisticCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedVideoWorkspaceCard(
    video: VideoMetadata,
    thumbnails: List<android.graphics.Bitmap>,
    trimStartMs: Long,
    trimEndMs: Long,
    selectedFilter: VideoFilterPreset,
    brightness: Float,
    suggestedHighlights: List<ClipSegment>,
    isSuggestingTimestamps: Boolean,
    isExporting: Boolean,
    selectedPlatform: TargetPlatform,
    focusTopic: String,
    isAnalyzing: Boolean,
    onAttachLocalVideo: () -> Unit,
    onTrimChanged: (Long, Long) -> Unit,
    onFilterSelected: (VideoFilterPreset) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onResetFilters: () -> Unit,
    onSuggestTimestamps: () -> Unit,
    onExportManualClip: () -> Unit,
    onPlatformSelected: (TargetPlatform) -> Unit,
    onFocusTopicChanged: (String) -> Unit,
    onAnalyzeClick: () -> Unit
) {
    var playheadPositionMs by remember(video.uriString) { mutableLongStateOf(0L) }
    var seekRequest by remember(video.uriString) { mutableStateOf<Pair<Long, Long>?>(null) }
    var playRangeRequest by remember(video.uriString) { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var triggerNonce by remember { mutableLongStateOf(0L) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val authorPrefix = if (!video.authorName.isNullOrBlank()) "${video.authorName} • " else ""
                    Text(
                        text = "$authorPrefix${video.width}x${video.height} • Durasi ${formatDurationShort(video.durationMs)}",
                        style = MonoTimecodeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeonPurple.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = video.sourcePlatform?.displayName?.uppercase() ?: "SIAP",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (video.hasPlayableStream) {
                MainVideoPlayer(
                    videoUri = Uri.parse(video.uriString),
                    videoWidth = video.width,
                    videoHeight = video.height,
                    videoFilter = selectedFilter,
                    brightness = brightness,
                    seekRequest = seekRequest,
                    playRangeRequest = playRangeRequest,
                    onPositionChanged = { pos -> playheadPositionMs = pos }
                )

                ClipTimestampRangeSelector(
                    thumbnails = thumbnails,
                    totalDurationMs = video.durationMs,
                    startMs = trimStartMs,
                    endMs = trimEndMs,
                    playheadPositionMs = playheadPositionMs,
                    isExporting = isExporting,
                    onRangeChanged = onTrimChanged,
                    onSeekPreview = { targetMs ->
                        triggerNonce += 1L
                        seekRequest = targetMs to triggerNonce
                    },
                    onPreviewClipRange = {
                        triggerNonce += 1L
                        playRangeRequest = Triple(trimStartMs, trimEndMs, triggerNonce)
                    },
                    onExportClipRange = onExportManualClip
                )

                VideoFilterControlsCard(
                    selectedFilter = selectedFilter,
                    brightness = brightness,
                    onFilterSelected = onFilterSelected,
                    onBrightnessChanged = onBrightnessChanged,
                    onResetFilters = onResetFilters
                )

                OutlinedButton(
                    onClick = onSuggestTimestamps,
                    enabled = !isSuggestingTimestamps,
                    border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("suggest_timestamps_button")
                ) {
                    if (isSuggestingTimestamps) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.studio_suggesting_timestamps))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = FuturisticCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.studio_suggest_timestamps))
                    }
                }

                if (suggestedHighlights.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.studio_suggested_ranges_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestedHighlights.forEachIndexed { index, clip ->
                                val isApplied = trimStartMs == clip.startMs && trimEndMs == clip.endMs
                                FilterChip(
                                    selected = isApplied,
                                    onClick = {
                                        onTrimChanged(clip.startMs, clip.endMs)
                                        triggerNonce += 1L
                                        seekRequest = clip.startMs to triggerNonce
                                    },
                                    label = {
                                        Text(
                                            text = "#${index + 1} (${clip.viralScore}) • ${formatTimecode(clip.startMs)}–${formatTimecode(clip.endMs)}",
                                            style = MonoTimecodeStyle
                                        )
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("suggested_range_chip_$index")
                                )
                            }
                        }
                    }
                }
            } else {
                SocialLinkPreviewCard(
                    title = video.title,
                    platformName = video.sourcePlatform?.displayName ?: "Sosial Media",
                    authorName = video.authorName,
                    thumbnailUrl = video.thumbnailUrl,
                    statusNote = video.streamStatusNote,
                    onAttachLocalVideo = onAttachLocalVideo
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.studio_target_platform),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TargetPlatform.entries.forEach { platform ->
                        val selected = platform == selectedPlatform
                        FilterChip(
                            selected = selected,
                            onClick = { onPlatformSelected(platform) },
                            label = { Text(platform.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }
            }

            OutlinedTextField(
                value = focusTopic,
                onValueChange = onFocusTopicChanged,
                label = { Text(stringResource(R.string.studio_focus_topic_label)) },
                placeholder = { Text(stringResource(R.string.studio_focus_topic_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("focus_topic_input")
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isAnalyzing, onClick = onAnalyzeClick)
                    .testTag("analyze_clips_button")
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
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.studio_analyzing_progress),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.studio_analyze_button),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectCard(
    project: ClipProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .testTag("recent_project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonPurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (project.sourcePlatformName != null) Icons.Filled.Link else Icons.Filled.MovieCreation,
                    contentDescription = null,
                    tint = FuturisticCyan
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sourcePrefix = if (!project.sourcePlatformName.isNullOrBlank()) "${project.sourcePlatformName} • " else ""
                Text(
                    text = "$sourcePrefix${project.targetPlatform.displayName} • ${formatDurationShort(project.durationMs)}",
                    style = MonoTimecodeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Hapus proyek",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Buka proyek",
                tint = FuturisticCyan
            )
        }
    }
}
