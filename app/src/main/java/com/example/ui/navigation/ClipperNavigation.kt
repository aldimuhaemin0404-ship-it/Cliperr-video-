package com.example.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.domain.model.ExportProgressState
import com.example.domain.model.ExportedClip
import com.example.media.SocialShareHelper
import com.example.ui.components.ExportProgressBanner
import com.example.ui.components.SocialShareBottomSheet
import com.example.ui.screens.AiAnalysisProgressScreen
import com.example.ui.screens.BatchQueueScreen
import com.example.ui.screens.ClipEditorScreen
import com.example.ui.screens.ExportSettingsScreen
import com.example.ui.screens.ExportSuccessScreen
import com.example.ui.screens.ExportsScreen
import com.example.ui.screens.PopularVideosScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.state.ClipperViewModel
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicOutline
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.FuturisticBlue
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple

const val ROUTE_SPLASH = "splash"
const val ROUTE_STUDIO = "studio"
const val ROUTE_EXPLORE = "explore"
const val ROUTE_ANALYZING = "analyzing"
const val ROUTE_PROJECTS = "projects"
const val ROUTE_EXPORTS = "exports"
const val ROUTE_EDITOR = "editor/{segmentId}"
const val ROUTE_EXPORT_SETTINGS = "export_settings/{segmentId}"
const val ROUTE_EXPORT_SUCCESS = "export_success"
const val ROUTE_BATCH_QUEUE = "batch_queue"

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = ROUTE_STUDIO,
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_studio"
    ),
    TopLevelDestination(
        route = ROUTE_EXPLORE,
        labelRes = R.string.nav_explore,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "nav_explore"
    ),
    TopLevelDestination(
        route = ROUTE_PROJECTS,
        labelRes = R.string.nav_clips,
        selectedIcon = Icons.Filled.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder,
        testTag = "nav_clips"
    ),
    TopLevelDestination(
        route = ROUTE_EXPORTS,
        labelRes = R.string.nav_exports,
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_exports"
    )
)

@Composable
fun AiClipperApp(
    sharedLinkText: String? = null,
    onSharedLinkConsumed: () -> Unit = {},
    viewModel: ClipperViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val activeSegments by viewModel.activeSegments.collectAsStateWithLifecycle()
    val exportedClips by viewModel.exportedClips.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val shareSheetState by viewModel.shareSheetState.collectAsStateWithLifecycle()
    val autoOpenShareSheet by viewModel.autoOpenShareSheet.collectAsStateWithLifecycle()
    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val batchConfig by viewModel.batchConfig.collectAsStateWithLifecycle()
    val isBatchRunning by viewModel.isBatchRunning.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var lastCompletedExport by remember { mutableStateOf<ExportedClip?>(null) }

    LaunchedEffect(sharedLinkText) {
        if (!sharedLinkText.isNullOrBlank()) {
            viewModel.onSharedLinkReceived(sharedLinkText)
            onSharedLinkConsumed()
            navController.navigate(ROUTE_STUDIO) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(statusBanner) {
        val message = statusBanner
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusBanner()
        }
    }

    // Automatically route back from Analysis Progress screen if analysis stops/errors without completion
    LaunchedEffect(currentRoute, studioState.isAnalyzing, studioState.errorMessage) {
        if (currentRoute == ROUTE_ANALYZING && !studioState.isAnalyzing && studioState.errorMessage != null) {
            navController.popBackStack()
        }
    }

    val startAnalysisFlow = {
        if (studioState.selectedVideo != null) {
            navController.navigate(ROUTE_ANALYZING) {
                launchSingleTop = true
            }
            viewModel.analyzeSelectedVideo {
                navController.navigate(ROUTE_PROJECTS) {
                    popUpTo(ROUTE_ANALYZING) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val topLevelRoutes = remember { topLevelDestinations.map { it.route }.toSet() }
    val isTopLevel = currentRoute in topLevelRoutes

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicBackground)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = CosmicBackground,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevel && !useNavRail) {
                    AldiBottomBar(
                        currentRoute = currentRoute,
                        exportedCount = exportedClips.size,
                        onSelectRoute = { route ->
                            navController.navigate(route) {
                                popUpTo(ROUTE_STUDIO) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onCenterClipClick = {
                            if (currentRoute == ROUTE_STUDIO && studioState.selectedVideo != null) {
                                startAnalysisFlow()
                            } else {
                                navController.navigate(ROUTE_STUDIO) {
                                    popUpTo(ROUTE_STUDIO) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useNavRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding(),
                        containerColor = CosmicSurface
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = if (dest.route == ROUTE_EXPORTS) exportedClips.size else 0
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(ROUTE_STUDIO) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (isTopLevel) {
                        ExportProgressBanner(
                            state = exportProgress,
                            onCancel = viewModel::cancelExport,
                            onOpenExports = {
                                navController.navigate(ROUTE_EXPORTS) {
                                    popUpTo(ROUTE_STUDIO) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onDismiss = viewModel::resetExportState
                        )
                        if (isBatchRunning && batchQueue.isNotEmpty()) {
                            val activeItem = batchQueue.firstOrNull { it.isActive }
                            val doneCount = batchQueue.count { it.status == com.example.domain.model.BatchItemStatus.COMPLETED }
                            val overallPct = batchQueue.sumOf { it.progressPercent }.toFloat() / (batchQueue.size * 100f)
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                color = CosmicSurface,
                                border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clickable {
                                        navController.navigate(ROUTE_BATCH_QUEUE) {
                                            launchSingleTop = true
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Batch (${doneCount}/${batchQueue.size}): ${activeItem?.sourceLabel ?: "Memproses…"}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = {
                                                navController.navigate(ROUTE_BATCH_QUEUE) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("Lihat", color = FuturisticCyan)
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { overallPct.coerceIn(0f, 1f) },
                                        color = FuturisticCyan,
                                        modifier = Modifier.fillMaxWidth().height(5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_SPLASH
                        ) {
                            composable(ROUTE_SPLASH) {
                                SplashScreen(
                                    onFinished = {
                                        navController.navigate(ROUTE_STUDIO) {
                                            popUpTo(ROUTE_SPLASH) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(ROUTE_STUDIO) {
                                StudioScreen(
                                    studioState = studioState,
                                    recentProjects = projects,
                                    isExporting = exportProgress is ExportProgressState.Exporting,
                                    batchQueueCount = batchQueue.size,
                                    isBatchRunning = isBatchRunning,
                                    onImportSourceChanged = viewModel::updateImportSource,
                                    onLinkInputChanged = viewModel::updateLinkInput,
                                    onResolveSocialLink = { viewModel.resolveSocialLink() },
                                    onEnqueueSingleUrl = { url ->
                                        viewModel.enqueueSocialLinks(url)
                                        navController.navigate(ROUTE_BATCH_QUEUE) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onVideoPicked = viewModel::onVideoSelected,
                                    onEnqueueMultipleVideos = { uris ->
                                        viewModel.enqueueGalleryVideos(uris)
                                        navController.navigate(ROUTE_BATCH_QUEUE) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenBatchQueue = {
                                        navController.navigate(ROUTE_BATCH_QUEUE) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onStudioTrimChanged = viewModel::updateStudioTrim,
                                    onFilterSelected = viewModel::updateStudioFilter,
                                    onBrightnessChanged = viewModel::updateStudioBrightness,
                                    onResetFilters = viewModel::resetStudioFilters,
                                    onSuggestTimestamps = viewModel::suggestTimestampsForStudio,
                                    onExportManualClip = viewModel::exportManualStudioClip,
                                    onPlatformSelected = viewModel::updateTargetPlatform,
                                    onFocusTopicChanged = viewModel::updateFocusTopic,
                                    onAnalyzeClick = startAnalysisFlow,
                                    onSelectPopularVideo = { item ->
                                        viewModel.loadPopularVideo(item)
                                    },
                                    onSeeAllPopular = {
                                        navController.navigate(ROUTE_EXPLORE) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenProject = { projectId ->
                                        viewModel.selectProject(projectId)
                                        navController.navigate(ROUTE_PROJECTS) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onDeleteProject = viewModel::deleteProject
                                )
                            }

                            composable(ROUTE_BATCH_QUEUE) {
                                BatchQueueScreen(
                                    queueItems = batchQueue,
                                    config = batchConfig,
                                    isBatchRunning = isBatchRunning,
                                    onBack = { navController.popBackStack() },
                                    onEnqueueGalleryVideos = { uris ->
                                        viewModel.enqueueGalleryVideos(uris)
                                    },
                                    onEnqueueSocialLinks = { linksText ->
                                        viewModel.enqueueSocialLinks(linksText)
                                    },
                                    onUpdatePlatform = viewModel::updateBatchPlatform,
                                    onUpdateFilter = viewModel::updateBatchFilter,
                                    onUpdateAutoExport = viewModel::updateBatchAutoExport,
                                    onStartBatch = viewModel::startBatchProcessing,
                                    onCancelBatch = viewModel::cancelBatchProcessing,
                                    onRetryItem = viewModel::retryBatchItem,
                                    onRemoveItem = viewModel::removeBatchItem,
                                    onClearCompleted = viewModel::clearCompletedBatchItems,
                                    onOpenProject = { projectId ->
                                        viewModel.selectProject(projectId)
                                        navController.navigate(ROUTE_PROJECTS) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onShareExportedClip = viewModel::openShareSheetByExportedId
                                )
                            }

                            composable(ROUTE_EXPLORE) {
                                PopularVideosScreen(
                                    onBack = {
                                        navController.navigate(ROUTE_STUDIO) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onSelectPopularVideo = { item ->
                                        viewModel.loadPopularVideo(item)
                                        navController.navigate(ROUTE_STUDIO) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onImportCustomLink = { url ->
                                        viewModel.onSharedLinkReceived(url)
                                        navController.navigate(ROUTE_STUDIO) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onQuickAiPick = { item ->
                                        viewModel.loadPopularVideo(item)
                                        navController.navigate(ROUTE_STUDIO) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(ROUTE_ANALYZING) {
                                AiAnalysisProgressScreen(
                                    progress = studioState.analysisProgress,
                                    stepIndex = studioState.analysisStepIndex,
                                    onCancel = {
                                        viewModel.cancelAnalysis()
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable(ROUTE_PROJECTS) {
                                ProjectsScreen(
                                    projects = projects,
                                    selectedProjectId = selectedProjectId,
                                    segments = activeSegments,
                                    isExporting = exportProgress is ExportProgressState.Exporting,
                                    onSelectProject = viewModel::selectProject,
                                    onEditSegment = { segmentId ->
                                        viewModel.loadSegmentForEditor(segmentId)
                                        navController.navigate("editor/$segmentId")
                                    },
                                    onExportSegment = { segment ->
                                        viewModel.loadSegmentForEditor(segment.id)
                                        navController.navigate("export_settings/${segment.id}")
                                    },
                                    onToggleFavorite = viewModel::toggleSegmentFavorite,
                                    onBookmarkAllFavorites = viewModel::bookmarkTopClipsFavorite,
                                    onGoToStudio = {
                                        navController.navigate(ROUTE_STUDIO) {
                                            popUpTo(ROUTE_STUDIO) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(ROUTE_EXPORTS) {
                                ExportsScreen(
                                    exportedClips = exportedClips,
                                    onSaveToGallery = viewModel::saveExportToGallery,
                                    onOpenShareSheet = viewModel::openShareSheetForClip,
                                    onDeleteExport = viewModel::deleteExportedClip
                                )
                            }

                            composable(
                                route = ROUTE_EDITOR,
                                arguments = listOf(
                                    navArgument("segmentId") { type = NavType.LongType }
                                )
                            ) { backStack ->
                                val segmentId = backStack.arguments?.getLong("segmentId") ?: 0L
                                LaunchedEffect(segmentId) {
                                    if (editorState.segment?.id != segmentId) {
                                        viewModel.loadSegmentForEditor(segmentId)
                                    }
                                }
                                ClipEditorScreen(
                                    editorState = editorState,
                                    exportProgress = exportProgress,
                                    onBack = { navController.popBackStack() },
                                    onUpdateTrim = viewModel::updateEditorTrim,
                                    onUpdateAspectRatio = viewModel::updateEditorAspectRatio,
                                    onUpdateFilter = viewModel::updateEditorFilter,
                                    onUpdateBrightness = viewModel::updateEditorBrightness,
                                    onResetFilters = viewModel::resetEditorFilters,
                                    onToggleSubtitles = viewModel::toggleEditorSubtitles,
                                    onToggleIncludeAudio = viewModel::toggleEditorIncludeAudio,
                                    onToggleIntroHook = viewModel::toggleEditorIntroHook,
                                    onUpdateHookDuration = viewModel::updateEditorHookDuration,
                                    onUpdateHookStyle = viewModel::updateEditorHookStyle,
                                    onGenerateVisualHook = viewModel::generateVisualHookForActiveSegment,
                                    onAnalyzeAudioMusic = viewModel::analyzeAudioAndRecommendMusicForActiveSegment,
                                    onSelectMusicTrack = viewModel::selectMusicTrackForEditor,
                                    onSelectCustomMusicUrl = viewModel::selectCustomMusicUrlForEditor,
                                    onToggleMusicPreview = viewModel::toggleMusicPreview,
                                    onStopMusicPreview = viewModel::stopMusicPreview,
                                    onUpdateMusicVolume = viewModel::updateEditorMusicVolume,
                                    onUpdateHook = viewModel::updateEditorHook,
                                    onUpdateCaption = viewModel::updateEditorCaption,
                                    onRegenerateCaption = viewModel::regenerateCaptionWithTone,
                                    onGenerateSubtitles = viewModel::generateSubtitlesForActiveSegment,
                                    onUpdateSubtitleCue = viewModel::updateSubtitleCueText,
                                    onSaveChanges = viewModel::saveEditorChanges,
                                    onOpenExportSettings = {
                                        val segId = editorState.segment?.id ?: segmentId
                                        navController.navigate("export_settings/$segId")
                                    },
                                    onAttachLocalVideo = viewModel::attachLocalVideoToProject,
                                    onCancelExport = viewModel::cancelExport,
                                    onDismissExportBanner = viewModel::resetExportState,
                                    onOpenExports = {
                                        navController.navigate(ROUTE_EXPORTS) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(
                                route = ROUTE_EXPORT_SETTINGS,
                                arguments = listOf(
                                    navArgument("segmentId") { type = NavType.LongType }
                                )
                            ) { backStack ->
                                val segmentId = backStack.arguments?.getLong("segmentId") ?: 0L
                                LaunchedEffect(segmentId) {
                                    if (editorState.segment?.id != segmentId) {
                                        viewModel.loadSegmentForEditor(segmentId)
                                    }
                                }
                                ExportSettingsScreen(
                                    editorState = editorState,
                                    exportProgress = exportProgress,
                                    autoOpenShareSheet = autoOpenShareSheet,
                                    onBack = { navController.popBackStack() },
                                    onSelectAspectRatio = viewModel::updateEditorAspectRatio,
                                    onSelectQuality = viewModel::updateEditorExportQuality,
                                    onToggleSubtitles = viewModel::toggleEditorSubtitles,
                                    onToggleIncludeAudio = viewModel::toggleEditorIncludeAudio,
                                    onToggleMusic = viewModel::toggleAutoMusicOnExportSettings,
                                    onToggleAutoOpenShareSheet = viewModel::setAutoOpenShareSheet,
                                    onStartExport = { seg, sourceUri ->
                                        viewModel.exportSegment(seg, sourceUri)
                                    },
                                    onCancelExport = viewModel::cancelExport,
                                    onExportCompleted = { clip ->
                                        lastCompletedExport = clip
                                        viewModel.resetExportState()
                                        navController.navigate(ROUTE_EXPORT_SUCCESS) {
                                            popUpTo(ROUTE_EXPORT_SETTINGS) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                        if (autoOpenShareSheet) {
                                            viewModel.openShareSheetForClip(clip)
                                        }
                                    }
                                )
                            }

                            composable(ROUTE_EXPORT_SUCCESS) {
                                val fallbackClip = lastCompletedExport ?: exportedClips.firstOrNull()
                                val matchingSegment = remember(fallbackClip?.segmentId, activeSegments, editorState.segment) {
                                    activeSegments.firstOrNull { it.id == fallbackClip?.segmentId }
                                        ?: editorState.segment?.takeIf { it.id == fallbackClip?.segmentId }
                                }
                                val resolvedCaption = remember(fallbackClip, matchingSegment) {
                                    fallbackClip?.let { SocialShareHelper.buildDefaultCaption(it, matchingSegment) }
                                }
                                ExportSuccessScreen(
                                    exportedClip = fallbackClip,
                                    shareCaption = resolvedCaption,
                                    onGetShareUri = viewModel::getShareUri,
                                    onOpenShareSheet = viewModel::openShareSheetForClip,
                                    onShareFeedback = viewModel::postStatusMessage,
                                    onEditAgain = {
                                        val targetSegId = fallbackClip?.segmentId ?: editorState.segment?.id
                                        if (targetSegId != null && targetSegId > 0L) {
                                            viewModel.loadSegmentForEditor(targetSegId)
                                            navController.navigate("editor/$targetSegId") {
                                                popUpTo(ROUTE_EXPORT_SUCCESS) { inclusive = true }
                                            }
                                        } else {
                                            navController.popBackStack()
                                        }
                                    },
                                    onOpenExportsHistory = {
                                        navController.navigate(ROUTE_EXPORTS) {
                                            popUpTo(ROUTE_STUDIO) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onBackToHome = {
                                        navController.navigate(ROUTE_STUDIO) {
                                            popUpTo(ROUTE_STUDIO) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        shareSheetState?.let { activeShare ->
            SocialShareBottomSheet(
                state = activeShare,
                onDismiss = viewModel::dismissShareSheet,
                onSaveToGallery = viewModel::saveExportToGallery,
                onShareLaunched = viewModel::postStatusMessage
            )
        }
    }
}

@Composable
private fun AldiBottomBar(
    currentRoute: String?,
    exportedCount: Int,
    onSelectRoute: (String) -> Unit,
    onCenterClipClick: () -> Unit
) {
    val leftItems = topLevelDestinations.take(2)
    val rightItems = topLevelDestinations.drop(2)

    Surface(
        color = CosmicSurface,
        border = BorderStroke(1.dp, CosmicOutline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(74.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            leftItems.forEach { dest ->
                BottomNavItemView(
                    dest = dest,
                    selected = currentRoute == dest.route,
                    badgeCount = 0,
                    onClick = { onSelectRoute(dest.route) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Center glowing purple scissors quick-action button
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(54.dp)
                    .shadow(10.dp, CircleShape, ambientColor = NeonPurple, spotColor = NeonMagenta)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NeonPurple, FuturisticBlue, NeonMagenta)
                        )
                    )
                    .clickable(onClick = onCenterClipClick)
                    .testTag("nav_center_clip_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCut,
                    contentDescription = "Buat Klip AI",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }

            rightItems.forEach { dest ->
                val badge = if (dest.route == ROUTE_EXPORTS) exportedCount else 0
                BottomNavItemView(
                    dest = dest,
                    selected = currentRoute == dest.route,
                    badgeCount = badge,
                    onClick = { onSelectRoute(dest.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItemView(
    dest: TopLevelDestination,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .testTag(dest.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge { Text(badgeCount.toString()) }
                }
            }
        ) {
            Icon(
                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                contentDescription = stringResource(dest.labelRes),
                tint = if (selected) FuturisticCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = stringResource(dest.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
