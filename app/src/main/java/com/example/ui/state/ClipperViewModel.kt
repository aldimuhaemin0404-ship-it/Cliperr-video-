package com.example.ui.state

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.transformer.Transformer
import com.example.data.ai.AiClipperRepository
import com.example.data.ai.FirebaseAiClipperRepository
import com.example.data.firebase.CloudClipProject
import com.example.data.firebase.FirestoreClipRepository
import com.example.data.local.AppDatabase
import com.example.data.local.ClipProjectRepository
import com.example.data.network.SocialLinkResolver
import com.example.data.network.UrlSecurityValidator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioAnalysisSummary
import com.example.domain.model.BatchItemStatus
import com.example.domain.model.BatchQueueConfig
import com.example.domain.model.BatchQueueItem
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportJobStatus
import com.example.domain.model.ExportProgressState
import com.example.domain.model.ExportQuality
import com.example.domain.model.ExportRenderJob
import com.example.domain.model.ExportedClip
import com.example.domain.model.HookOverlayStyle
import com.example.domain.model.MusicMood
import com.example.domain.model.MusicTrackRecommendation
import com.example.domain.model.PopularVideoItem
import com.example.domain.model.SocialSourcePlatform
import com.example.domain.model.StudioImportSource
import com.example.domain.model.StudioWorkspaceStateSnapshot
import com.example.domain.model.TargetPlatform
import com.example.domain.model.UndoHistoryUiState
import com.example.domain.model.VideoFilterPreset
import com.example.domain.model.VideoMetadata
import com.example.domain.model.VideoTransitionStyle
import com.example.domain.model.WorkspaceHistoryTarget
import com.example.domain.model.WorkspaceUndoEntry
import com.example.media.AudioMoodAnalyzer
import com.example.media.MediaPlaybackCache
import com.example.media.RoyaltyFreeMusicCatalog
import com.example.media.SocialShareHelper
import com.example.media.VideoMediaProcessor
import com.example.ui.components.ShareSheetUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val TAG = "ClipperViewModel"

data class StudioUiState(
    val importSource: StudioImportSource = StudioImportSource.GALLERY,
    val linkInput: String = "",
    val selectedVideo: VideoMetadata? = null,
    val filmstripThumbnails: List<Bitmap> = emptyList(),
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val selectedFilter: VideoFilterPreset = VideoFilterPreset.NONE,
    val brightness: Float = 0f,
    val suggestedHighlights: List<ClipSegment> = emptyList(),
    val isSuggestingTimestamps: Boolean = false,
    val isLoadingVideo: Boolean = false,
    val selectedPlatform: TargetPlatform = TargetPlatform.REELS_TIKTOK,
    val focusTopic: String = "",
    val includeWatermark: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisProgress: Float = 0f,
    val analysisStepIndex: Int = 0,
    val errorMessage: String? = null
)

data class EditorUiState(
    val project: ClipProject? = null,
    val segment: ClipSegment? = null,
    val thumbnails: List<Bitmap> = emptyList(),
    val isLoading: Boolean = false,
    val isRegeneratingCaption: Boolean = false,
    val isGeneratingSubtitles: Boolean = false,
    val isAnalyzingAudio: Boolean = false,
    val isGeneratingHook: Boolean = false,
    val audioAnalysis: AudioAnalysisSummary? = null,
    val recommendedTracks: List<MusicTrackRecommendation> = RoyaltyFreeMusicCatalog.catalog,
    val hookRationale: String? = null,
    val previewingTrackId: String? = null,
    val previewTrackUri: Uri? = null,
    val isPlaceholderAi: Boolean = false,
    val errorMessage: String? = null
)

sealed interface CloudSyncUiState {
    data object Loading : CloudSyncUiState
    data class Synced(val cloudProjectsCount: Int) : CloudSyncUiState
    data class Error(val message: String) : CloudSyncUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClipperViewModel(
    application: Application,
    private val currentUserId: String? = null,
    private val firestoreRepository: FirestoreClipRepository? = null,
    private val aiRepository: AiClipperRepository = FirebaseAiClipperRepository()
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val projectRepository = ClipProjectRepository(database.clipDao())
    private val mediaProcessor = VideoMediaProcessor(application)
    private val socialLinkResolver = SocialLinkResolver()

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId.asStateFlow()

    val projects: StateFlow<List<ClipProject>> = projectRepository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val activeSegments: StateFlow<List<ClipSegment>> = _selectedProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else projectRepository.observeSegments(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val exportedClips: StateFlow<List<ExportedClip>> = projectRepository.allExportedClips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _exportProgress = MutableStateFlow<ExportProgressState>(ExportProgressState.Idle)
    val exportProgress: StateFlow<ExportProgressState> = _exportProgress.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _shareSheetState = MutableStateFlow<ShareSheetUiState?>(null)
    val shareSheetState: StateFlow<ShareSheetUiState?> = _shareSheetState.asStateFlow()

    private val _autoOpenShareSheet = MutableStateFlow(false)
    val autoOpenShareSheet: StateFlow<Boolean> = _autoOpenShareSheet.asStateFlow()

    private val _batchQueue = MutableStateFlow<List<BatchQueueItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchQueueItem>> = _batchQueue.asStateFlow()

    private val _batchConfig = MutableStateFlow(BatchQueueConfig())
    val batchConfig: StateFlow<BatchQueueConfig> = _batchConfig.asStateFlow()

    private val _isBatchRunning = MutableStateFlow(false)
    val isBatchRunning: StateFlow<Boolean> = _isBatchRunning.asStateFlow()

    private val _exportRenderQueue = MutableStateFlow<List<ExportRenderJob>>(emptyList())
    val exportRenderQueue: StateFlow<List<ExportRenderJob>> = _exportRenderQueue.asStateFlow()

    private val _isRenderQueueRunning = MutableStateFlow(false)
    val isRenderQueueRunning: StateFlow<Boolean> = _isRenderQueueRunning.asStateFlow()

    private val undoStack = ArrayDeque<WorkspaceUndoEntry>()
    private val redoStack = ArrayDeque<WorkspaceUndoEntry>()
    private var isApplyingUndoRedo = false
    private val _undoHistoryState = MutableStateFlow(UndoHistoryUiState())
    val undoHistoryState: StateFlow<UndoHistoryUiState> = _undoHistoryState.asStateFlow()

    private var activeExportJob: Job? = null
    private var activeAnalysisJob: Job? = null
    private var activeBatchJob: Job? = null
    private var activeRenderQueueJob: Job? = null
    private var activeRenderQueueJobId: String? = null
    private var studioThumbnailJob: Job? = null
    private var editorThumbnailJob: Job? = null
    private var cloudSyncParentJob: Job? = null
    private var activeTransformer: Transformer? = null
    private var activeBatchTransformer: Transformer? = null
    private var activeRenderQueueTransformer: Transformer? = null

    val cloudSyncState: StateFlow<CloudSyncUiState> =
        if (currentUserId != null && firestoreRepository != null) {
            firestoreRepository.observeCloudProjects(currentUserId)
                .map<List<CloudClipProject>, CloudSyncUiState> { list ->
                    CloudSyncUiState.Synced(list.size)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing cloud projects", rootError)
                    emit(CloudSyncUiState.Error("Mode Lokal Aktif • Cloud Sync tertunda"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000L),
                    initialValue = CloudSyncUiState.Loading
                )
        } else {
            MutableStateFlow<CloudSyncUiState>(
                CloudSyncUiState.Error("Mode Studio Lokal Aktif • Penyimpanan Perangkat")
            ).asStateFlow()
        }

    init {
        viewModelScope.launch {
            projects.collect { list ->
                if (_selectedProjectId.value == null && list.isNotEmpty()) {
                    _selectedProjectId.value = list.first().id
                } else if (_selectedProjectId.value != null && list.none { it.id == _selectedProjectId.value }) {
                    _selectedProjectId.value = list.firstOrNull()?.id
                }
            }
        }
        startCloudSyncPipeline()
    }

    fun ensureCloudSyncActive() {
        if (cloudSyncParentJob?.isActive != true) {
            startCloudSyncPipeline()
        }
    }

    private fun startCloudSyncPipeline() {
        val uid = currentUserId ?: return
        val repo = firestoreRepository ?: return
        cloudSyncParentJob?.cancel()
        val syncJob = Job()
        cloudSyncParentJob = syncJob

        viewModelScope.launch(syncJob) {
            Firebase.auth.currentUser?.takeIf { it.uid == uid }?.let { user ->
                repo.syncUserProfile(user)
            }
        }
        viewModelScope.launch(syncJob) {
            runCatching {
                combine(
                    repo.observeCloudProjects(uid),
                    repo.observeCloudSegments(uid)
                ) { cloudProjects, cloudSegments ->
                    cloudProjects to cloudSegments
                }.collect { (cloudProjects, cloudSegments) ->
                    cloudProjects.forEach { cloud ->
                        projectRepository.restoreProjectFromCloud(cloud.toDomain())
                    }
                    cloudSegments.forEach { cloudSeg ->
                        projectRepository.restoreSegmentFromCloud(cloudSeg.toDomain())
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Cloud project/segment restore error", err)
            }
        }
        viewModelScope.launch(syncJob) {
            runCatching {
                repo.observeCloudExports(uid).collect { cloudExports ->
                    val existingIds = exportedClips.value.map { it.id }.toSet()
                    cloudExports.forEach { cloudExp ->
                        projectRepository.restoreExportFromCloud(cloudExp.toDomain(), existingIds)
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Cloud export restore error", err)
            }
        }
    }

    private fun syncProjectSnapshotToCloud(projectId: Long) {
        val repo = firestoreRepository ?: return
        viewModelScope.launch {
            val savedProj = projectRepository.getProject(projectId) ?: return@launch
            val savedSegs = projectRepository.getSegmentsForProject(projectId)
            repo.syncProjectWithSegments(savedProj, savedSegs)
        }
    }

    private fun syncSegmentUpdateToCloud(segment: ClipSegment) {
        val repo = firestoreRepository ?: return
        viewModelScope.launch {
            val parentProj = projectRepository.getProject(segment.projectId)
            repo.upsertSegment(segment, parentProj)
        }
    }

    fun prepareForSignOut() {
        cloudSyncParentJob?.cancel()
        cloudSyncParentJob = null
        activeTransformer?.cancel()
        activeBatchTransformer?.cancel()
        activeRenderQueueTransformer?.cancel()
        activeExportJob?.cancel()
        activeAnalysisJob?.cancel()
        activeBatchJob?.cancel()
        activeRenderQueueJob?.cancel()
        studioThumbnailJob?.cancel()
        editorThumbnailJob?.cancel()
        activeTransformer = null
        activeBatchTransformer = null
        activeRenderQueueTransformer = null
        activeRenderQueueJobId = null
        _isRenderQueueRunning.value = false
        clearUndoHistory()
    }

    private fun captureStudioSnapshot(state: StudioUiState = _studioState.value): StudioWorkspaceStateSnapshot {
        return StudioWorkspaceStateSnapshot(
            trimStartMs = state.trimStartMs,
            trimEndMs = state.trimEndMs,
            selectedFilter = state.selectedFilter,
            brightness = state.brightness,
            selectedPlatform = state.selectedPlatform,
            focusTopic = state.focusTopic,
            includeWatermark = state.includeWatermark
        )
    }

    private fun refreshUndoHistoryState() {
        _undoHistoryState.value = UndoHistoryUiState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            undoLabel = undoStack.lastOrNull()?.label,
            redoLabel = redoStack.lastOrNull()?.label,
            undoCount = undoStack.size,
            redoCount = redoStack.size
        )
    }

    private fun recordStudioHistory(
        label: String,
        actionCategory: String,
        before: StudioWorkspaceStateSnapshot,
        after: StudioWorkspaceStateSnapshot,
        coalesce: Boolean = false
    ) {
        if (isApplyingUndoRedo || before == after) return
        val now = System.currentTimeMillis()
        redoStack.clear()
        val last = undoStack.lastOrNull()
        if (coalesce &&
            last is WorkspaceUndoEntry.StudioEdit &&
            last.actionCategory == actionCategory &&
            (now - last.timestampMs) <= 750L
        ) {
            undoStack.removeLast()
            if (last.before != after) {
                undoStack.addLast(
                    last.copy(
                        after = after,
                        timestampMs = now
                    )
                )
            }
        } else {
            undoStack.addLast(
                WorkspaceUndoEntry.StudioEdit(
                    label = label,
                    actionCategory = actionCategory,
                    before = before,
                    after = after,
                    timestampMs = now
                )
            )
            while (undoStack.size > 40) {
                undoStack.removeFirst()
            }
        }
        refreshUndoHistoryState()
    }

    private fun recordClipHistory(
        label: String,
        actionCategory: String,
        before: ClipSegment,
        after: ClipSegment,
        coalesce: Boolean = false
    ) {
        if (isApplyingUndoRedo || before == after) return
        val now = System.currentTimeMillis()
        redoStack.clear()
        val last = undoStack.lastOrNull()
        if (coalesce &&
            last is WorkspaceUndoEntry.ClipEdit &&
            last.target == WorkspaceHistoryTarget.ClipSegmentTarget(after.id) &&
            last.actionCategory == actionCategory &&
            (now - last.timestampMs) <= 750L
        ) {
            undoStack.removeLast()
            if (last.before != after) {
                undoStack.addLast(
                    last.copy(
                        after = after,
                        timestampMs = now
                    )
                )
            }
        } else {
            undoStack.addLast(
                WorkspaceUndoEntry.ClipEdit(
                    label = label,
                    actionCategory = actionCategory,
                    before = before,
                    after = after,
                    timestampMs = now
                )
            )
            while (undoStack.size > 40) {
                undoStack.removeFirst()
            }
        }
        refreshUndoHistoryState()
    }

    fun undoGlobal() {
        val entry = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(entry)
        applyUndoRedoEntry(entry, isUndo = true)
        refreshUndoHistoryState()
        _statusBannerMessage.value = "Undo: ${entry.label}"
    }

    fun redoGlobal() {
        val entry = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(entry)
        applyUndoRedoEntry(entry, isUndo = false)
        refreshUndoHistoryState()
        _statusBannerMessage.value = "Redo: ${entry.label}"
    }

    fun clearUndoHistory() {
        undoStack.clear()
        redoStack.clear()
        refreshUndoHistoryState()
    }

    private fun applyUndoRedoEntry(entry: WorkspaceUndoEntry, isUndo: Boolean) {
        isApplyingUndoRedo = true
        try {
            when (entry) {
                is WorkspaceUndoEntry.StudioEdit -> {
                    val target = if (isUndo) entry.before else entry.after
                    _studioState.update { state ->
                        val maxDuration = state.selectedVideo?.durationMs
                        val safeStart = if (maxDuration != null) {
                            target.trimStartMs.coerceIn(0L, (maxDuration - 1_000L).coerceAtLeast(0L))
                        } else {
                            target.trimStartMs.coerceAtLeast(0L)
                        }
                        val safeEnd = if (maxDuration != null) {
                            target.trimEndMs.coerceIn((safeStart + 1_000L).coerceAtMost(maxDuration), maxDuration)
                        } else {
                            target.trimEndMs.coerceAtLeast(0L)
                        }
                        state.copy(
                            trimStartMs = safeStart,
                            trimEndMs = safeEnd,
                            selectedFilter = target.selectedFilter,
                            brightness = target.brightness,
                            selectedPlatform = target.selectedPlatform,
                            focusTopic = target.focusTopic,
                            includeWatermark = target.includeWatermark
                        )
                    }
                }

                is WorkspaceUndoEntry.ClipEdit -> {
                    val targetSeg = if (isUndo) entry.before else entry.after
                    _editorState.update { state ->
                        if (state.segment?.id == targetSeg.id) {
                            state.copy(segment = targetSeg)
                        } else {
                            state
                        }
                    }
                    viewModelScope.launch {
                        projectRepository.updateSegment(targetSeg)
                        syncSegmentUpdateToCloud(targetSeg)
                    }
                }
            }
        } finally {
            isApplyingUndoRedo = false
        }
    }

    fun updateImportSource(source: StudioImportSource) {
        _studioState.update { it.copy(importSource = source, errorMessage = null) }
    }

    fun updateLinkInput(url: String) {
        _studioState.update { it.copy(linkInput = url, errorMessage = null) }
    }

    fun onSharedLinkReceived(sharedText: String) {
        val extracted = socialLinkResolver.extractFirstUrl(sharedText) ?: sharedText.trim()
        _studioState.update {
            it.copy(
                importSource = StudioImportSource.SOCIAL_LINK,
                linkInput = extracted,
                errorMessage = null
            )
        }
        resolveSocialLink(extracted)
    }

    fun resolveSocialLink(rawInput: String = _studioState.value.linkInput) {
        studioThumbnailJob?.cancel()
        viewModelScope.launch {
            _studioState.update {
                it.copy(isLoadingVideo = true, errorMessage = null)
            }
            try {
                val resolved = socialLinkResolver.resolveLink(rawInput)
                val streamUrl = resolved.playableStreamUrl
                if (streamUrl != null) {
                    val streamUri = Uri.parse(streamUrl)
                    val cachedThumbs = mediaProcessor.getCachedThumbnails(streamUri, 8).orEmpty()

                    // Tampilkan pemutar seketika tanpa menunggu ekstraksi thumbnail selesai
                    _studioState.update { state ->
                        val initialEnd = minOf(resolved.durationMs, state.selectedPlatform.maxRecommendedSec * 1000L)
                            .coerceAtLeast(minOf(resolved.durationMs, 1_000L))
                        state.copy(
                            selectedVideo = VideoMetadata(
                                uriString = streamUrl,
                                title = resolved.title,
                                durationMs = resolved.durationMs,
                                width = resolved.width,
                                height = resolved.height,
                                sourcePlatform = resolved.platform,
                                authorName = resolved.authorName,
                                thumbnailUrl = resolved.thumbnailUrl,
                                originalShareUrl = resolved.originalUrl,
                                hasPlayableStream = true,
                                streamStatusNote = resolved.statusNote
                            ),
                            filmstripThumbnails = cachedThumbs,
                            trimStartMs = 0L,
                            trimEndMs = initialEnd,
                            suggestedHighlights = emptyList(),
                            isLoadingVideo = false
                        )
                    }
                    _statusBannerMessage.value = "Stream ${resolved.platform.displayName} siap diputar lancar!"

                    studioThumbnailJob = viewModelScope.launch {
                        val inspected = runCatching { mediaProcessor.extractMetadata(streamUri) }.getOrNull()
                        val finalDuration = inspected?.durationMs?.takeIf { it > 1_000L } ?: resolved.durationMs
                        val finalWidth = inspected?.width?.takeIf { it > 0 } ?: resolved.width
                        val finalHeight = inspected?.height?.takeIf { it > 0 } ?: resolved.height

                        val thumbs = if (cachedThumbs.isNotEmpty()) {
                            cachedThumbs
                        } else {
                            runCatching {
                                mediaProcessor.extractThumbnails(streamUri, finalDuration, count = 8)
                            }.getOrDefault(emptyList())
                        }

                        _studioState.update { state ->
                            val currentMeta = state.selectedVideo
                            if (currentMeta?.uriString != streamUrl) return@update state
                            val updatedEnd = if (state.trimEndMs <= 1_000L || state.trimEndMs > finalDuration) {
                                minOf(finalDuration, state.selectedPlatform.maxRecommendedSec * 1000L)
                                    .coerceAtLeast(minOf(finalDuration, 1_000L))
                            } else {
                                state.trimEndMs
                            }
                            state.copy(
                                selectedVideo = currentMeta.copy(
                                    durationMs = finalDuration,
                                    width = finalWidth,
                                    height = finalHeight
                                ),
                                filmstripThumbnails = if (thumbs.isNotEmpty()) thumbs else state.filmstripThumbnails,
                                trimEndMs = updatedEnd
                            )
                        }
                    }
                } else {
                    _studioState.update { state ->
                        val initialEnd = minOf(resolved.durationMs, state.selectedPlatform.maxRecommendedSec * 1000L)
                            .coerceAtLeast(minOf(resolved.durationMs, 1_000L))
                        state.copy(
                            selectedVideo = VideoMetadata(
                                uriString = resolved.resolvedPageUrl,
                                title = resolved.title,
                                durationMs = resolved.durationMs,
                                width = resolved.width,
                                height = resolved.height,
                                sourcePlatform = resolved.platform,
                                authorName = resolved.authorName,
                                thumbnailUrl = resolved.thumbnailUrl,
                                originalShareUrl = resolved.originalUrl,
                                hasPlayableStream = false,
                                streamStatusNote = resolved.statusNote
                            ),
                            filmstripThumbnails = emptyList(),
                            trimStartMs = 0L,
                            trimEndMs = initialEnd,
                            suggestedHighlights = emptyList(),
                            isLoadingVideo = false
                        )
                    }
                    _statusBannerMessage.value = "Info ${resolved.platform.displayName} berhasil diambil."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve social link", e)
                val fallbackUrl = socialLinkResolver.extractFirstUrl(rawInput)
                if (fallbackUrl != null) {
                    val platform = socialLinkResolver.detectPlatform(fallbackUrl)
                    _studioState.update { state ->
                        val initialEnd = minOf(60_000L, state.selectedPlatform.maxRecommendedSec * 1000L)
                        state.copy(
                            selectedVideo = VideoMetadata(
                                uriString = fallbackUrl,
                                title = "Video ${platform.displayName}",
                                durationMs = 60_000L,
                                width = if (platform == SocialSourcePlatform.YOUTUBE) 1280 else 1080,
                                height = if (platform == SocialSourcePlatform.YOUTUBE) 720 else 1920,
                                sourcePlatform = platform,
                                originalShareUrl = fallbackUrl,
                                hasPlayableStream = false,
                                streamStatusNote = "Tautan ${platform.displayName} siap dibuka. Ketuk 'Buka Video' untuk menonton, 'Analisis & Buat Klip AI', atau tautkan file video lokal."
                            ),
                            filmstripThumbnails = emptyList(),
                            trimStartMs = 0L,
                            trimEndMs = initialEnd,
                            suggestedHighlights = emptyList(),
                            isLoadingVideo = false,
                            errorMessage = null
                        )
                    }
                    _statusBannerMessage.value = "Tautan ${platform.displayName} dimuat ke Studio."
                } else {
                    _studioState.update {
                        it.copy(
                            isLoadingVideo = false,
                            errorMessage = "Masukkan link video yang valid (YouTube, TikTok, Instagram, atau URL .mp4)."
                        )
                    }
                }
            }
        }
    }

    fun directExportFromLink(
        rawInput: String = _studioState.value.linkInput,
        onExported: (ExportedClip) -> Unit = {}
    ) {
        if (_exportProgress.value is ExportProgressState.Exporting) return

        activeExportJob = viewModelScope.launch {
            _studioState.update { it.copy(isLoadingVideo = true, errorMessage = null) }
            try {
                val resolved = socialLinkResolver.resolveLink(rawInput)
                val streamUrl = resolved.playableStreamUrl
                if (streamUrl == null) {
                    val fallbackEnd = minOf(resolved.durationMs, _studioState.value.selectedPlatform.maxRecommendedSec * 1000L)
                        .coerceAtLeast(minOf(resolved.durationMs, 1_000L))
                    _studioState.update { state ->
                        state.copy(
                            selectedVideo = VideoMetadata(
                                uriString = resolved.resolvedPageUrl,
                                title = resolved.title,
                                durationMs = resolved.durationMs,
                                width = resolved.width,
                                height = resolved.height,
                                sourcePlatform = resolved.platform,
                                authorName = resolved.authorName,
                                thumbnailUrl = resolved.thumbnailUrl,
                                originalShareUrl = resolved.originalUrl,
                                hasPlayableStream = false,
                                streamStatusNote = resolved.statusNote
                            ),
                            filmstripThumbnails = emptyList(),
                            trimStartMs = 0L,
                            trimEndMs = fallbackEnd,
                            suggestedHighlights = emptyList(),
                            isLoadingVideo = false,
                            errorMessage = "Tautan ${resolved.platform.displayName} membatasi unduhan MP4 langsung sesuai kebijakan platform. Tempel URL video (.mp4 / .m3u8) atau pilih video dari galeri HP untuk langsung diekspor & disimpan ke Galeri."
                        )
                    }
                    _statusBannerMessage.value = "Tautan ${resolved.platform.displayName} dimuat. Pilih file MP4 HP atau tempel link .mp4 langsung untuk simpan ke Galeri."
                    return@launch
                }

                val streamUri = Uri.parse(streamUrl)
                val inspected = runCatching { mediaProcessor.extractMetadata(streamUri) }.getOrNull()
                val finalDuration = inspected?.durationMs?.takeIf { it > 1_000L } ?: resolved.durationMs
                val finalWidth = inspected?.width?.takeIf { it > 0 } ?: resolved.width
                val finalHeight = inspected?.height?.takeIf { it > 0 } ?: resolved.height

                val currentStudio = _studioState.value
                val initialEnd = minOf(finalDuration, currentStudio.selectedPlatform.maxRecommendedSec * 1000L)
                    .coerceAtLeast(minOf(finalDuration, 1_000L))

                _studioState.update { state ->
                    state.copy(
                        selectedVideo = VideoMetadata(
                            uriString = streamUrl,
                            title = resolved.title,
                            durationMs = finalDuration,
                            width = finalWidth,
                            height = finalHeight,
                            sourcePlatform = resolved.platform,
                            authorName = resolved.authorName,
                            thumbnailUrl = resolved.thumbnailUrl,
                            originalShareUrl = resolved.originalUrl,
                            hasPlayableStream = true,
                            streamStatusNote = resolved.statusNote
                        ),
                        trimStartMs = 0L,
                        trimEndMs = initialEnd,
                        isLoadingVideo = false,
                        errorMessage = null
                    )
                }

                val project = ClipProject(
                    title = resolved.title,
                    sourceUri = streamUrl,
                    durationMs = finalDuration,
                    width = finalWidth,
                    height = finalHeight,
                    targetPlatform = currentStudio.selectedPlatform,
                    focusTopic = currentStudio.focusTopic,
                    sourcePlatformName = resolved.platform.displayName,
                    authorName = resolved.authorName,
                    thumbnailUrl = resolved.thumbnailUrl,
                    hasPlayableStream = true,
                    isPlaceholderAi = false
                )
                val directSegment = ClipSegment(
                    projectId = 0L,
                    title = resolved.title.take(36),
                    hookLine = "Sorotan dari ${resolved.platform.displayName}",
                    viralScore = 90,
                    rationale = "Diekspor langsung dari tautan stream video.",
                    captionText = "${resolved.title} • ${currentStudio.selectedPlatform.displayName}",
                    hashtags = "#AIClipper #Shorts #Reels",
                    startMs = 0L,
                    endMs = initialEnd,
                    aspectRatio = currentStudio.selectedPlatform.defaultRatio,
                    tone = CaptionTone.ENERGETIC,
                    videoFilter = currentStudio.selectedFilter,
                    brightness = currentStudio.brightness,
                    showSubtitles = false,
                    includeWatermark = currentStudio.includeWatermark,
                    isPlaceholderAi = false
                )

                val savedProjectId = projectRepository.saveProjectWithSegments(project) { projId ->
                    listOf(directSegment.copy(projectId = projId))
                }
                val persistedSegment = projectRepository.getSegmentsForProject(savedProjectId).firstOrNull()
                    ?: directSegment.copy(projectId = savedProjectId)
                syncProjectSnapshotToCloud(savedProjectId)
                _selectedProjectId.value = savedProjectId

                _exportProgress.value = ExportProgressState.Exporting(
                    segmentId = persistedSegment.id,
                    clipTitle = persistedSegment.title,
                    progressPercent = 0
                )

                val exported = mediaProcessor.exportTrimmedClip(
                    sourceUri = streamUri,
                    segment = persistedSegment,
                    onProgress = { pct ->
                        _exportProgress.value = ExportProgressState.Exporting(
                            segmentId = persistedSegment.id,
                            clipTitle = persistedSegment.title,
                            progressPercent = pct
                        )
                    },
                    onTransformerCreated = { transformer ->
                        activeTransformer = transformer
                    }
                )

                val savedId = projectRepository.saveExportedClip(exported)
                val finalClip = exported.copy(id = savedId)
                val savedProject = projectRepository.getProject(savedProjectId)
                firestoreRepository?.upsertExportedClip(finalClip, savedProject)

                _exportProgress.value = ExportProgressState.Completed(finalClip)
                _statusBannerMessage.value = if (finalClip.galleryUri != null) {
                    "Video link selesai diekspor & tersimpan ke Galeri (Movies/AIClipper)!"
                } else {
                    "Video link selesai diekspor: ${finalClip.title}"
                }
                onExported(finalClip)
            } catch (e: CancellationException) {
                _studioState.update { it.copy(isLoadingVideo = false) }
                _exportProgress.value = ExportProgressState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Direct export from link halted", e)
                _studioState.update {
                    it.copy(
                        isLoadingVideo = false,
                        errorMessage = "Link belum menghasilkan stream MP4 langsung. Pastikan URL valid (.mp4/.m3u8) atau pilih video dari galeri HP."
                    )
                }
                _exportProgress.value = ExportProgressState.Idle
            } finally {
                activeTransformer = null
            }
        }
    }

    fun loadPopularVideo(item: PopularVideoItem) {
        studioThumbnailJob?.cancel()
        val streamUri = Uri.parse(item.streamUrl)
        val cachedThumbs = mediaProcessor.getCachedThumbnails(streamUri, 8).orEmpty()

        // Tampilkan workspace dan mulai player secara instan tanpa blocking
        _studioState.update { state ->
            val initialEnd = minOf(item.durationMs, state.selectedPlatform.maxRecommendedSec * 1000L)
                .coerceAtLeast(minOf(item.durationMs, 1_000L))
            state.copy(
                importSource = StudioImportSource.SOCIAL_LINK,
                linkInput = item.streamUrl,
                selectedVideo = VideoMetadata(
                    uriString = item.streamUrl,
                    title = item.title,
                    durationMs = item.durationMs,
                    width = item.width,
                    height = item.height,
                    sourcePlatform = item.platform,
                    authorName = item.author,
                    thumbnailUrl = item.thumbnailUrl,
                    originalShareUrl = item.streamUrl,
                    hasPlayableStream = true,
                    streamStatusNote = "Video populer ${item.platform.displayName} siap diputar lancar!"
                ),
                filmstripThumbnails = cachedThumbs,
                trimStartMs = 0L,
                trimEndMs = initialEnd,
                suggestedHighlights = emptyList(),
                isLoadingVideo = false,
                errorMessage = null
            )
        }
        _statusBannerMessage.value = "Memutar \"${item.title}\" sekarang!"

        studioThumbnailJob = viewModelScope.launch {
            val inspected = runCatching { mediaProcessor.extractMetadata(streamUri) }.getOrNull()
            val finalDuration = inspected?.durationMs?.takeIf { it > 1_000L } ?: item.durationMs
            val finalWidth = inspected?.width?.takeIf { it > 0 } ?: item.width
            val finalHeight = inspected?.height?.takeIf { it > 0 } ?: item.height
            val thumbs = if (cachedThumbs.isNotEmpty()) {
                cachedThumbs
            } else {
                runCatching {
                    mediaProcessor.extractThumbnails(streamUri, finalDuration, count = 8)
                }.getOrDefault(emptyList())
            }

            _studioState.update { state ->
                val currentMeta = state.selectedVideo
                if (currentMeta?.uriString != item.streamUrl) return@update state
                state.copy(
                    selectedVideo = currentMeta.copy(
                        durationMs = finalDuration,
                        width = finalWidth,
                        height = finalHeight
                    ),
                    filmstripThumbnails = if (thumbs.isNotEmpty()) thumbs else state.filmstripThumbnails
                )
            }
        }
    }

    fun onVideoSelected(uri: Uri) {
        studioThumbnailJob?.cancel()
        viewModelScope.launch {
            _studioState.update {
                it.copy(isLoadingVideo = true, errorMessage = null)
            }
            try {
                val durableUri = mediaProcessor.resolveDurableLocalUri(uri)
                val metadata = mediaProcessor.extractMetadata(durableUri)
                val cachedThumbs = mediaProcessor.getCachedThumbnails(durableUri, 8).orEmpty()
                val currentMeta = _studioState.value.selectedVideo
                val mergedMeta = if (currentMeta != null && !currentMeta.hasPlayableStream) {
                    metadata.copy(
                        title = currentMeta.title.ifBlank { metadata.title },
                        sourcePlatform = currentMeta.sourcePlatform,
                        authorName = currentMeta.authorName,
                        thumbnailUrl = currentMeta.thumbnailUrl,
                        originalShareUrl = currentMeta.originalShareUrl,
                        hasPlayableStream = true,
                        streamStatusNote = "File video lokal berhasil ditautkan ke proyek ${currentMeta.sourcePlatform?.displayName ?: "link"}."
                    )
                } else {
                    metadata
                }
                _studioState.update { state ->
                    val initialEnd = minOf(mergedMeta.durationMs, state.selectedPlatform.maxRecommendedSec * 1000L)
                        .coerceAtLeast(minOf(mergedMeta.durationMs, 1_000L))
                    state.copy(
                        selectedVideo = mergedMeta,
                        filmstripThumbnails = cachedThumbs,
                        trimStartMs = 0L,
                        trimEndMs = initialEnd,
                        suggestedHighlights = emptyList(),
                        isLoadingVideo = false
                    )
                }

                if (cachedThumbs.isEmpty()) {
                    studioThumbnailJob = viewModelScope.launch {
                        val thumbs = runCatching {
                            mediaProcessor.extractThumbnails(durableUri, mergedMeta.durationMs, count = 8)
                        }.getOrDefault(emptyList())
                        if (thumbs.isNotEmpty()) {
                            _studioState.update { state ->
                                if (state.selectedVideo?.uriString == mergedMeta.uriString) {
                                    state.copy(filmstripThumbnails = thumbs)
                                } else {
                                    state
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect selected video", e)
                _studioState.update {
                    it.copy(
                        isLoadingVideo = false,
                        errorMessage = "Format video ini belum didukung. Silakan pilih file video MP4/MOV lain dari galeri."
                    )
                }
            }
        }
    }

    fun attachLocalVideoToProject(projectId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                val durableUri = mediaProcessor.resolveDurableLocalUri(uri)
                val existing = projectRepository.getProject(projectId) ?: return@launch
                val metadata = mediaProcessor.extractMetadata(durableUri)
                val thumbs = runCatching {
                    mediaProcessor.extractThumbnails(durableUri, metadata.durationMs, count = 8)
                }.getOrDefault(emptyList())
                val updatedProject = existing.copy(
                    sourceUri = metadata.uriString,
                    durationMs = metadata.durationMs,
                    width = metadata.width,
                    height = metadata.height,
                    hasPlayableStream = true
                )
                projectRepository.updateProject(updatedProject)
                firestoreRepository?.let { repo ->
                    viewModelScope.launch { repo.upsertProject(updatedProject) }
                }

                if (_editorState.value.project?.id == projectId) {
                    var updatedSegForSync: ClipSegment? = null
                    _editorState.update { state ->
                        val seg = state.segment
                        val clampedSeg = seg?.let {
                            val safeEnd = it.endMs.coerceIn(1_000L, metadata.durationMs)
                            val safeStart = it.startMs.coerceIn(0L, (safeEnd - 500L).coerceAtLeast(0L))
                            it.copy(startMs = safeStart, endMs = safeEnd)
                        }
                        updatedSegForSync = clampedSeg
                        state.copy(
                            project = updatedProject,
                            segment = clampedSeg,
                            thumbnails = if (thumbs.isNotEmpty()) thumbs else state.thumbnails
                        )
                    }
                    updatedSegForSync?.let { clampedSeg ->
                        viewModelScope.launch { projectRepository.updateSegment(clampedSeg) }
                        syncSegmentUpdateToCloud(clampedSeg)
                    }
                }
                _statusBannerMessage.value = "File video lokal ditautkan. Sekarang siap diputar dan diekspor ke MP4!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach local video to project", e)
                _statusBannerMessage.value = "Video lokal belum terbaca. Silakan pilih file MP4 lain."
            }
        }
    }

    fun attachStreamUrlToProject(
        projectId: Long,
        rawUrlInput: String,
        autoExportSegment: ClipSegment? = null
    ) {
        viewModelScope.launch {
            try {
                val resolved = socialLinkResolver.resolveLink(rawUrlInput)
                val streamUrl = resolved.playableStreamUrl
                if (streamUrl == null) {
                    val hint = "Tautan ${resolved.platform.displayName} membatasi unduhan file MP4 langsung sesuai kebijakan platform. Tempel URL video (.mp4 / .m3u8) atau pilih video dari galeri HP."
                    _statusBannerMessage.value = hint
                    _exportProgress.value = ExportProgressState.Error(hint)
                    return@launch
                }

                val existing = projectRepository.getProject(projectId) ?: return@launch
                val streamUri = Uri.parse(streamUrl)
                val inspected = runCatching { mediaProcessor.extractMetadata(streamUri) }.getOrNull()
                val finalDuration = inspected?.durationMs?.takeIf { it > 1_000L } ?: existing.durationMs
                val finalWidth = inspected?.width?.takeIf { it > 0 } ?: resolved.width
                val finalHeight = inspected?.height?.takeIf { it > 0 } ?: resolved.height

                val updatedProject = existing.copy(
                    sourceUri = streamUrl,
                    durationMs = finalDuration,
                    width = finalWidth,
                    height = finalHeight,
                    sourcePlatformName = resolved.platform.displayName,
                    hasPlayableStream = true
                )
                projectRepository.updateProject(updatedProject)
                firestoreRepository?.let { repo ->
                    viewModelScope.launch { repo.upsertProject(updatedProject) }
                }

                var updatedSegForExport = autoExportSegment
                if (_editorState.value.project?.id == projectId) {
                    _editorState.update { state ->
                        val seg = state.segment
                        val clampedSeg = seg?.let {
                            val safeEnd = it.endMs.coerceIn(1_000L, finalDuration)
                            val safeStart = it.startMs.coerceIn(0L, (safeEnd - 500L).coerceAtLeast(0L))
                            it.copy(startMs = safeStart, endMs = safeEnd)
                        }
                        if (autoExportSegment != null && clampedSeg != null) {
                            updatedSegForExport = clampedSeg
                        }
                        state.copy(
                            project = updatedProject,
                            segment = clampedSeg
                        )
                    }
                    updatedSegForExport?.let { seg ->
                        projectRepository.updateSegment(seg)
                        syncSegmentUpdateToCloud(seg)
                    }
                }

                if (updatedSegForExport != null) {
                    exportSegment(updatedSegForExport!!, streamUrl)
                } else {
                    _statusBannerMessage.value = "Link stream MP4 berhasil ditautkan! Ketuk Ekspor untuk simpan ke Galeri."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach stream URL to project", e)
                val hint = "Masukkan link video langsung yang valid (.mp4 / .m3u8) atau pilih video dari galeri HP."
                _statusBannerMessage.value = hint
                _exportProgress.value = ExportProgressState.Error(hint)
            }
        }
    }

    fun updateTargetPlatform(platform: TargetPlatform) {
        val before = captureStudioSnapshot()
        _studioState.update { it.copy(selectedPlatform = platform) }
        val after = captureStudioSnapshot()
        recordStudioHistory(
            label = "Platform ${platform.displayName}",
            actionCategory = "studio_platform",
            before = before,
            after = after
        )
    }

    fun updateFocusTopic(topic: String) {
        val before = captureStudioSnapshot()
        _studioState.update { it.copy(focusTopic = topic) }
        val after = captureStudioSnapshot()
        recordStudioHistory(
            label = "Topik Fokus Studio",
            actionCategory = "studio_topic",
            before = before,
            after = after,
            coalesce = true
        )
    }

    fun updateStudioTrim(startMs: Long, endMs: Long) {
        val before = captureStudioSnapshot()
        _studioState.update { state ->
            val maxDuration = state.selectedVideo?.durationMs ?: Long.MAX_VALUE
            val safeStart = startMs.coerceIn(0L, (maxDuration - 1_000L).coerceAtLeast(0L))
            val safeEnd = endMs.coerceIn((safeStart + 1_000L).coerceAtMost(maxDuration), maxDuration)
            state.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
        }
        val after = captureStudioSnapshot()
        recordStudioHistory(
            label = "Potong Timeline Studio",
            actionCategory = "studio_trim",
            before = before,
            after = after,
            coalesce = true
        )
    }

    fun updateStudioFilter(filter: VideoFilterPreset) {
        val before = captureStudioSnapshot()
        _studioState.update { it.copy(selectedFilter = filter) }
        val after = captureStudioSnapshot()
        recordStudioHistory(
            label = "Filter Studio ${filter.label}",
            actionCategory = "studio_filter",
            before = before,
            after = after
        )
    }

    fun updateStudioBrightness(brightness: Float) {
        val before = captureStudioSnapshot()
        _studioState.update { it.copy(brightness = brightness.coerceIn(-0.5f, 0.5f)) }
        val after = captureStudioSnapshot()
        recordStudioHistory(
            label = "Kecerahan Studio",
            actionCategory = "studio_brightness",
            before = before,
            after = after,
            coalesce = true
        )
    }

    fun resetStudioFilters() {
        val before = captureStudioSnapshot()
        _studioState.update { it.copy(selectedFilter = VideoFilterPreset.NONE, brightness = 0f) }
        val after = captureStudioSnapshot()
        recordStudioHistory(
            label = "Reset Filter Studio",
            actionCategory = "studio_filter_reset",
            before = before,
            after = after
        )
    }

    fun updateStudioWatermark(include: Boolean) {
        val before = captureStudioSnapshot()
        _studioState.update { it.copy(includeWatermark = include) }
        val after = captureStudioSnapshot()
        recordStudioHistory(
            label = if (include) "Watermark Aktif" else "Tanpa Watermark",
            actionCategory = "studio_watermark",
            before = before,
            after = after
        )
    }

    fun suggestTimestampsForStudio() {
        val current = _studioState.value
        val video = current.selectedVideo ?: return
        if (current.isSuggestingTimestamps) return

        viewModelScope.launch {
            _studioState.update { it.copy(isSuggestingTimestamps = true, errorMessage = null) }
            try {
                val aiResult = aiRepository.analyzeVideoForClips(
                    projectId = 0L,
                    videoTitle = video.title,
                    durationMs = video.durationMs,
                    width = video.width,
                    height = video.height,
                    focusTopic = current.focusTopic,
                    targetPlatform = current.selectedPlatform,
                    sampleFrames = current.filmstripThumbnails
                )
                val topClip = aiResult.detectedClips.maxByOrNull { it.viralScore }
                val before = captureStudioSnapshot()
                _studioState.update { state ->
                    state.copy(
                        isSuggestingTimestamps = false,
                        suggestedHighlights = aiResult.detectedClips,
                        trimStartMs = topClip?.startMs ?: state.trimStartMs,
                        trimEndMs = topClip?.endMs ?: state.trimEndMs
                    )
                }
                val after = captureStudioSnapshot()
                recordStudioHistory(
                    label = "Rekomendasi Timestamp AI",
                    actionCategory = "studio_ai_trim",
                    before = before,
                    after = after
                )
                _statusBannerMessage.value = if (aiResult.isPlaceholderMode) {
                    "Rekomendasi timestamp [Sampel AI] diterapkan ke slider."
                } else {
                    "Rekomendasi timestamp Gemini AI diterapkan ke slider."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Timestamp suggestion failed", e)
                _studioState.update {
                    it.copy(
                        isSuggestingTimestamps = false,
                        errorMessage = "Rekomendasi timestamp sedang disiapkan. Silakan ketuk tombol sekali lagi."
                    )
                }
            }
        }
    }

    fun exportManualStudioClip() {
        val current = _studioState.value
        val video = current.selectedVideo ?: return
        if (!video.hasPlayableStream) {
            _statusBannerMessage.value = "Tautkan file video lokal atau URL stream video langsung untuk merender MP4."
            return
        }
        if (_exportProgress.value is ExportProgressState.Exporting) return

        viewModelScope.launch {
            try {
                val cleanTopic = current.focusTopic.trim().ifEmpty { video.title }
                val project = ClipProject(
                    title = video.title,
                    sourceUri = video.uriString,
                    durationMs = video.durationMs,
                    width = video.width,
                    height = video.height,
                    targetPlatform = current.selectedPlatform,
                    focusTopic = current.focusTopic,
                    sourcePlatformName = video.sourcePlatform?.displayName,
                    authorName = video.authorName,
                    thumbnailUrl = video.thumbnailUrl,
                    hasPlayableStream = true,
                    isPlaceholderAi = false
                )
                val manualSegment = ClipSegment(
                    projectId = 0L,
                    title = "Klip Pilihan (${video.title.take(18)})",
                    hookLine = "Sorotan pilihan dari $cleanTopic",
                    viralScore = 90,
                    rationale = "Rentang waktu dipilih langsung melalui slider timeline Studio.",
                    captionText = "Klip pilihan dari ${video.title} untuk ${current.selectedPlatform.displayName}.",
                    hashtags = "#AIClipper #ClipManual #Shorts",
                    startMs = current.trimStartMs,
                    endMs = current.trimEndMs,
                    aspectRatio = current.selectedPlatform.defaultRatio,
                    tone = CaptionTone.ENERGETIC,
                    videoFilter = current.selectedFilter,
                    brightness = current.brightness,
                    showSubtitles = true,
                    includeWatermark = current.includeWatermark,
                    isPlaceholderAi = false
                )
                val savedProjectId = projectRepository.saveProjectWithSegments(project) { projId ->
                    listOf(manualSegment.copy(projectId = projId))
                }
                val persistedSegment = projectRepository.getSegmentsForProject(savedProjectId).firstOrNull()
                    ?: manualSegment.copy(projectId = savedProjectId)
                syncProjectSnapshotToCloud(savedProjectId)
                _selectedProjectId.value = savedProjectId
                exportSegment(persistedSegment, video.uriString)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Manual studio export failed", e)
                _statusBannerMessage.value = "Ekspor klip perlu dicoba ulang. Pastikan video siap diputar."
            }
        }
    }

    fun prepareManualClipForExportSettings(onReady: (Long) -> Unit) {
        val current = _studioState.value
        val video = current.selectedVideo ?: return
        if (!video.hasPlayableStream) {
            _statusBannerMessage.value = "Tautkan file video lokal atau URL stream video langsung untuk memilih rasio & ekspor."
            return
        }
        viewModelScope.launch {
            try {
                val cleanTopic = current.focusTopic.trim().ifEmpty { video.title }
                val project = ClipProject(
                    title = video.title,
                    sourceUri = video.uriString,
                    durationMs = video.durationMs,
                    width = video.width,
                    height = video.height,
                    targetPlatform = current.selectedPlatform,
                    focusTopic = current.focusTopic,
                    sourcePlatformName = video.sourcePlatform?.displayName,
                    authorName = video.authorName,
                    thumbnailUrl = video.thumbnailUrl,
                    hasPlayableStream = true,
                    isPlaceholderAi = false
                )
                val manualSegment = ClipSegment(
                    projectId = 0L,
                    title = "Klip Pilihan (${video.title.take(18)})",
                    hookLine = "Sorotan pilihan dari $cleanTopic",
                    viralScore = 90,
                    rationale = "Rentang waktu dipilih langsung melalui slider timeline Studio.",
                    captionText = "Klip pilihan dari ${video.title} untuk ${current.selectedPlatform.displayName}.",
                    hashtags = "#AIClipper #ClipManual #Shorts",
                    startMs = current.trimStartMs,
                    endMs = current.trimEndMs,
                    aspectRatio = current.selectedPlatform.defaultRatio,
                    tone = CaptionTone.ENERGETIC,
                    videoFilter = current.selectedFilter,
                    brightness = current.brightness,
                    showSubtitles = true,
                    includeWatermark = current.includeWatermark,
                    isPlaceholderAi = false
                )
                val savedProjectId = projectRepository.saveProjectWithSegments(project) { projId ->
                    listOf(manualSegment.copy(projectId = projId))
                }
                val persistedSegment = projectRepository.getSegmentsForProject(savedProjectId).firstOrNull()
                    ?: manualSegment.copy(projectId = savedProjectId)
                syncProjectSnapshotToCloud(savedProjectId)
                _selectedProjectId.value = savedProjectId
                loadSegmentForEditor(persistedSegment.id)
                onReady(persistedSegment.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare manual clip for export settings", e)
                _statusBannerMessage.value = "Pengaturan ekspor belum siap. Silakan coba sekali lagi."
            }
        }
    }

    fun analyzeSelectedVideo(onSuccess: () -> Unit) {
        val current = _studioState.value
        val video = current.selectedVideo ?: return

        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            _studioState.update {
                it.copy(
                    isAnalyzing = true,
                    analysisProgress = 0.15f,
                    analysisStepIndex = 0,
                    errorMessage = null
                )
            }
            try {
                kotlinx.coroutines.delay(350L)
                _studioState.update { it.copy(analysisProgress = 0.42f, analysisStepIndex = 1) }

                val aiResult = aiRepository.analyzeVideoForClips(
                    projectId = 0L,
                    videoTitle = video.title,
                    durationMs = video.durationMs,
                    width = video.width,
                    height = video.height,
                    focusTopic = current.focusTopic,
                    targetPlatform = current.selectedPlatform,
                    sampleFrames = current.filmstripThumbnails
                )

                _studioState.update { it.copy(analysisProgress = 0.75f, analysisStepIndex = 2) }
                kotlinx.coroutines.delay(350L)

                val project = ClipProject(
                    title = video.title,
                    sourceUri = video.uriString,
                    durationMs = video.durationMs,
                    width = video.width,
                    height = video.height,
                    targetPlatform = current.selectedPlatform,
                    focusTopic = current.focusTopic,
                    sourcePlatformName = video.sourcePlatform?.displayName,
                    authorName = video.authorName,
                    thumbnailUrl = video.thumbnailUrl,
                    hasPlayableStream = video.hasPlayableStream,
                    isPlaceholderAi = aiResult.isPlaceholderMode
                )

                val savedProjectId = projectRepository.saveProjectWithSegments(project) { projectId ->
                    aiResult.detectedClips.map {
                        it.copy(
                            projectId = projectId,
                            videoFilter = current.selectedFilter,
                            brightness = current.brightness,
                            includeWatermark = current.includeWatermark
                        )
                    }
                }

                syncProjectSnapshotToCloud(savedProjectId)
                _studioState.update { it.copy(analysisProgress = 1f, analysisStepIndex = 3) }
                kotlinx.coroutines.delay(300L)

                _selectedProjectId.value = savedProjectId
                _studioState.update { it.copy(isAnalyzing = false) }
                _statusBannerMessage.value = "Ditemukan ${aiResult.detectedClips.size} klip terbaik!"
                onSuccess()
            } catch (e: CancellationException) {
                _studioState.update { it.copy(isAnalyzing = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI analysis failed", e)
                _studioState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = "Analisis AI membutuhkan koneksi stabil. Silakan ketuk Analisis & Buat Klip AI lagi."
                    )
                }
            }
        }
    }

    fun cancelAnalysis() {
        activeAnalysisJob?.cancel()
        activeAnalysisJob = null
        _studioState.update { it.copy(isAnalyzing = false, analysisProgress = 0f, analysisStepIndex = 0) }
    }

    fun selectProject(projectId: Long) {
        _selectedProjectId.value = projectId
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
            firestoreRepository?.deleteProjectAndChildren(projectId)
            _statusBannerMessage.value = "Proyek dihapus."
        }
    }

    fun toggleSegmentFavorite(segment: ClipSegment) {
        viewModelScope.launch {
            val updated = segment.copy(isFavorite = !segment.isFavorite)
            recordClipHistory(
                label = if (updated.isFavorite) "Tandai Favorit" else "Hapus Favorit",
                actionCategory = "clip_favorite",
                before = segment,
                after = updated
            )
            projectRepository.updateSegment(updated)
            syncSegmentUpdateToCloud(updated)
            if (_editorState.value.segment?.id == segment.id) {
                _editorState.update { it.copy(segment = updated) }
            }
            _statusBannerMessage.value = if (updated.isFavorite) {
                "Ditambahkan ke Favorit."
            } else {
                "Dihapus dari Favorit."
            }
        }
    }

    fun bookmarkTopClipsFavorite() {
        viewModelScope.launch {
            val list = activeSegments.value
            if (list.isEmpty()) return@launch
            list.forEach { seg ->
                if (!seg.isFavorite) {
                    val updated = seg.copy(isFavorite = true)
                    projectRepository.updateSegment(updated)
                    syncSegmentUpdateToCloud(updated)
                }
            }
            _statusBannerMessage.value = "Semua ${list.size} klip terbaik disimpan ke Favorit!"
        }
    }

    fun loadSegmentForEditor(segmentId: Long) {
        editorThumbnailJob?.cancel()
        viewModelScope.launch {
            _editorState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val segment = projectRepository.getSegment(segmentId)
                val project = segment?.let { projectRepository.getProject(it.projectId) }
                if (segment == null || project == null) {
                    _editorState.update {
                        it.copy(isLoading = false, errorMessage = "Klip tidak ditemukan.")
                    }
                    return@launch
                }
                val sourceUri = Uri.parse(project.sourceUri)
                val cachedThumbs = if (project.hasPlayableStream) {
                    mediaProcessor.getCachedThumbnails(sourceUri, 8).orEmpty()
                } else {
                    emptyList()
                }

                // Langsung buka Editor tanpa memblokir UI oleh ekstraksi frame
                _editorState.value = EditorUiState(
                    project = project,
                    segment = segment,
                    thumbnails = cachedThumbs,
                    isLoading = false,
                    isPlaceholderAi = segment.isPlaceholderAi
                )

                if (project.hasPlayableStream && cachedThumbs.isEmpty()) {
                    editorThumbnailJob = viewModelScope.launch {
                        val thumbs = runCatching {
                            mediaProcessor.extractThumbnails(
                                sourceUri,
                                project.durationMs,
                                count = 8
                            )
                        }.getOrDefault(emptyList())
                        if (thumbs.isNotEmpty()) {
                            _editorState.update { state ->
                                if (state.segment?.id == segmentId) {
                                    state.copy(thumbnails = thumbs)
                                } else {
                                    state
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load clip editor", e)
                _editorState.update {
                    it.copy(isLoading = false, errorMessage = "Editor klip belum dapat dibuka. Silakan pilih klip lagi.")
                }
            }
        }
    }

    private inline fun mutateEditorSegmentWithHistory(
        label: String,
        actionCategory: String,
        coalesce: Boolean = false,
        transform: (EditorUiState, ClipSegment) -> ClipSegment
    ): ClipSegment? {
        var beforeSeg: ClipSegment? = null
        var afterSeg: ClipSegment? = null
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            val next = transform(state, seg)
            beforeSeg = seg
            afterSeg = next
            state.copy(segment = next)
        }
        val b = beforeSeg
        val a = afterSeg
        if (b != null && a != null) {
            recordClipHistory(
                label = label,
                actionCategory = actionCategory,
                before = b,
                after = a,
                coalesce = coalesce
            )
        }
        return a
    }

    fun updateEditorTrim(startMs: Long, endMs: Long) {
        mutateEditorSegmentWithHistory(
            label = "Potong Klip",
            actionCategory = "clip_trim",
            coalesce = true
        ) { state, seg ->
            val maxDuration = state.project?.durationMs ?: Long.MAX_VALUE
            val safeStart = startMs.coerceIn(0L, (maxDuration - 1000L).coerceAtLeast(0L))
            val safeEnd = endMs.coerceIn((safeStart + 1000L).coerceAtMost(maxDuration), maxDuration)
            seg.copy(startMs = safeStart, endMs = safeEnd)
        }
    }

    fun updateEditorAspectRatio(ratio: AspectRatioPreset) {
        mutateEditorSegmentWithHistory(
            label = "Rasio ${ratio.label}",
            actionCategory = "clip_ratio"
        ) { _, seg ->
            seg.copy(aspectRatio = ratio)
        }
    }

    fun updateEditorFilter(filter: VideoFilterPreset) {
        mutateEditorSegmentWithHistory(
            label = "Filter ${filter.label}",
            actionCategory = "clip_filter"
        ) { _, seg ->
            seg.copy(videoFilter = filter)
        }
    }

    fun updateEditorBrightness(brightness: Float) {
        mutateEditorSegmentWithHistory(
            label = "Kecerahan Klip",
            actionCategory = "clip_brightness",
            coalesce = true
        ) { _, seg ->
            seg.copy(brightness = brightness.coerceIn(-0.5f, 0.5f))
        }
    }

    fun resetEditorFilters() {
        mutateEditorSegmentWithHistory(
            label = "Reset Filter Klip",
            actionCategory = "clip_filter_reset"
        ) { _, seg ->
            seg.copy(videoFilter = VideoFilterPreset.NONE, brightness = 0f)
        }
    }

    fun updateEditorHook(hook: String) {
        mutateEditorSegmentWithHistory(
            label = "Edit Judul Hook",
            actionCategory = "clip_hook_text",
            coalesce = true
        ) { _, seg ->
            seg.copy(hookLine = hook)
        }
    }

    fun updateEditorCaption(caption: String) {
        mutateEditorSegmentWithHistory(
            label = "Edit Caption",
            actionCategory = "clip_caption_text",
            coalesce = true
        ) { _, seg ->
            seg.copy(captionText = caption)
        }
    }

    fun toggleEditorSubtitles(show: Boolean) {
        mutateEditorSegmentWithHistory(
            label = if (show) "Aktifkan Subtitle" else "Sembunyikan Subtitle",
            actionCategory = "clip_subtitle_toggle"
        ) { _, seg ->
            seg.copy(showSubtitles = show)
        }
    }

    fun toggleEditorIncludeAudio(include: Boolean) {
        mutateEditorSegmentWithHistory(
            label = if (include) "Sertakan Audio Asli" else "Bisukan Audio Asli",
            actionCategory = "clip_audio_toggle"
        ) { _, seg ->
            seg.copy(includeAudio = include)
        }
    }

    fun toggleEditorWatermark(include: Boolean) {
        val updatedSeg = mutateEditorSegmentWithHistory(
            label = if (include) "Watermark Aldi AI Clipper" else "Tanpa Watermark",
            actionCategory = "clip_watermark"
        ) { _, seg ->
            seg.copy(includeWatermark = include)
        }
        updatedSeg?.let { seg ->
            viewModelScope.launch { projectRepository.updateSegment(seg) }
            syncSegmentUpdateToCloud(seg)
        }
    }

    fun updateEditorExportQuality(quality: ExportQuality) {
        mutateEditorSegmentWithHistory(
            label = "Kualitas ${quality.label}",
            actionCategory = "clip_quality"
        ) { _, seg ->
            seg.copy(exportQuality = quality)
        }
    }

    fun toggleEditorIntroHook(show: Boolean) {
        mutateEditorSegmentWithHistory(
            label = if (show) "Aktifkan Hook 3 Detik" else "Nonaktifkan Hook 3 Detik",
            actionCategory = "clip_intro_hook_toggle"
        ) { _, seg ->
            seg.copy(showIntroHook = show)
        }
    }

    fun updateEditorHookDuration(durationMs: Long) {
        mutateEditorSegmentWithHistory(
            label = "Durasi Hook",
            actionCategory = "clip_hook_duration",
            coalesce = true
        ) { _, seg ->
            seg.copy(hookDurationMs = durationMs.coerceIn(1_500L, 5_000L))
        }
    }

    fun updateEditorHookStyle(style: HookOverlayStyle) {
        mutateEditorSegmentWithHistory(
            label = "Gaya Hook ${style.label}",
            actionCategory = "clip_hook_style"
        ) { _, seg ->
            seg.copy(hookOverlayStyle = style)
        }
    }

    fun updateEditorMusicVolume(volume: Float) {
        mutateEditorSegmentWithHistory(
            label = "Volume Musik",
            actionCategory = "clip_music_volume",
            coalesce = true
        ) { _, seg ->
            seg.copy(musicVolume = volume.coerceIn(0.05f, 1.0f))
        }
    }

    fun updateEditorTransitionStyle(style: VideoTransitionStyle) {
        mutateEditorSegmentWithHistory(
            label = "Transisi ${style.label}",
            actionCategory = "clip_transition_style"
        ) { _, seg ->
            seg.copy(transitionStyle = style)
        }
    }

    fun updateEditorTransitionDuration(durationMs: Long) {
        mutateEditorSegmentWithHistory(
            label = "Durasi Transisi",
            actionCategory = "clip_transition_duration",
            coalesce = true
        ) { _, seg ->
            seg.copy(transitionDurationMs = durationMs.coerceIn(250L, 1_500L))
        }
    }

    fun selectMusicTrackForEditor(track: MusicTrackRecommendation?) {
        val updatedSeg = mutateEditorSegmentWithHistory(
            label = if (track != null) "Musik: ${track.title}" else "Tanpa Musik Latar",
            actionCategory = "clip_music_track"
        ) { _, seg ->
            if (track == null) {
                seg.copy(
                    selectedMusicTrackId = null,
                    selectedMusicTitle = null,
                    selectedMusicUrl = null
                )
            } else {
                seg.copy(
                    selectedMusicTrackId = track.id,
                    selectedMusicTitle = track.title,
                    selectedMusicUrl = track.audioUrl,
                    audioMoodLabel = track.mood.label,
                    audioBpm = track.bpm
                )
            }
        }
        updatedSeg?.let { seg ->
            viewModelScope.launch { projectRepository.updateSegment(seg) }
            syncSegmentUpdateToCloud(seg)
        }
        _statusBannerMessage.value = if (track != null) {
            "Musik latar \"${track.title}\" dipasang."
        } else {
            "Musik latar dinonaktifkan."
        }
    }

    fun selectCustomMusicUrlForEditor(rawUrl: String) {
        val customTrack = RoyaltyFreeMusicCatalog.buildCustomUrlTrack(rawUrl)
        if (customTrack == null) {
            _statusBannerMessage.value = "Masukkan URL file audio yang valid (.mp3 / .m4a / .wav)."
            return
        }
        _editorState.update { state ->
            val filtered = state.recommendedTracks.filterNot { it.audioUrl == customTrack.audioUrl }
            state.copy(recommendedTracks = listOf(customTrack) + filtered)
        }
        selectMusicTrackForEditor(customTrack)
    }

    fun toggleMusicPreview(track: MusicTrackRecommendation) {
        val currentPreviewId = _editorState.value.previewingTrackId
        if (currentPreviewId == track.id) {
            stopMusicPreview()
            return
        }
        viewModelScope.launch {
            val minDur = _editorState.value.segment?.durationMs?.coerceAtLeast(10_000L) ?: 15_000L
            val uri = RoyaltyFreeMusicCatalog.resolvePlayableAudioUri(
                context = getApplication(),
                audioUrl = track.audioUrl,
                trackId = track.id,
                minDurationMs = minDur
            )
            if (uri != null) {
                _editorState.update {
                    it.copy(previewingTrackId = track.id, previewTrackUri = uri)
                }
            } else {
                _statusBannerMessage.value = "Pratinjau audio sedang disiapkan."
            }
        }
    }

    fun stopMusicPreview() {
        _editorState.update {
            it.copy(previewingTrackId = null, previewTrackUri = null)
        }
    }

    fun toggleAutoMusicOnExportSettings(enable: Boolean) {
        val seg = _editorState.value.segment ?: return
        if (!enable) {
            selectMusicTrackForEditor(null)
            return
        }
        if (!seg.selectedMusicUrl.isNullOrBlank()) return
        val mood = when (seg.tone) {
            CaptionTone.ENERGETIC -> MusicMood.UPBEAT
            CaptionTone.PROFESSIONAL -> MusicMood.CORPORATE
            CaptionTone.EDUCATIONAL -> MusicMood.LOFI_CHILL
            CaptionTone.HUMOROUS -> MusicMood.UPBEAT
        }
        val bestTrack = RoyaltyFreeMusicCatalog.rankForMoodAndBpm(mood, 112).firstOrNull()
        if (bestTrack != null) {
            selectMusicTrackForEditor(bestTrack)
        }
    }

    fun analyzeAudioAndRecommendMusicForActiveSegment() {
        val current = _editorState.value
        val seg = current.segment ?: return
        val proj = current.project ?: return
        if (current.isAnalyzingAudio) return

        viewModelScope.launch {
            _editorState.update { it.copy(isAnalyzingAudio = true, errorMessage = null) }
            try {
                val summary = AudioMoodAnalyzer.analyzeClipAudio(
                    context = getApplication(),
                    sourceUri = if (proj.hasPlayableStream) Uri.parse(proj.sourceUri) else null,
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    tone = seg.tone,
                    targetPlatform = proj.targetPlatform,
                    focusTopic = proj.focusTopic
                )
                val result = aiRepository.recommendMusicForClip(
                    audioAnalysis = summary,
                    clipTitle = seg.title,
                    focusTopic = proj.focusTopic
                )
                val topTrack = result.recommendedTracks.firstOrNull()
                var beforeSegment: ClipSegment? = null
                var updatedSegment: ClipSegment? = null
                _editorState.update { state ->
                    val latest = state.segment ?: return@update state
                    beforeSegment = latest
                    val nextSeg = if (latest.selectedMusicUrl.isNullOrBlank() && topTrack != null) {
                        latest.copy(
                            selectedMusicTrackId = topTrack.id,
                            selectedMusicTitle = topTrack.title,
                            selectedMusicUrl = topTrack.audioUrl,
                            audioMoodLabel = result.audioAnalysis.detectedMood.label,
                            audioBpm = result.audioAnalysis.estimatedBpm
                        )
                    } else {
                        latest.copy(
                            audioMoodLabel = result.audioAnalysis.detectedMood.label,
                            audioBpm = result.audioAnalysis.estimatedBpm
                        )
                    }
                    updatedSegment = nextSeg
                    state.copy(
                        isAnalyzingAudio = false,
                        audioAnalysis = result.audioAnalysis,
                        recommendedTracks = result.recommendedTracks,
                        segment = nextSeg
                    )
                }
                val bMusic = beforeSegment
                val aMusic = updatedSegment
                if (bMusic != null && aMusic != null) {
                    recordClipHistory(
                        label = "Rekomendasi Musik AI",
                        actionCategory = "clip_ai_music",
                        before = bMusic,
                        after = aMusic
                    )
                }
                updatedSegment?.let {
                    projectRepository.updateSegment(it)
                    syncSegmentUpdateToCloud(it)
                }
                _statusBannerMessage.value = if (result.isPlaceholderMode) {
                    "[Sampel AI] Mood ${result.audioAnalysis.detectedMood.label} terdeteksi • ${result.recommendedTracks.size} musik direkomendasikan."
                } else {
                    "Gemini AI: Mood ${result.audioAnalysis.detectedMood.label} terdeteksi • ${result.recommendedTracks.size} musik direkomendasikan."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio analysis failed", e)
                _editorState.update {
                    it.copy(
                        isAnalyzingAudio = false,
                        errorMessage = "Analisis audio klip perlu dicoba ulang."
                    )
                }
            }
        }
    }

    fun generateVisualHookForActiveSegment() {
        val current = _editorState.value
        val seg = current.segment ?: return
        val proj = current.project ?: return
        if (current.isGeneratingHook) return

        viewModelScope.launch {
            _editorState.update { it.copy(isGeneratingHook = true, errorMessage = null) }
            try {
                val introEnd = minOf(seg.endMs, seg.startMs + seg.hookDurationMs.coerceAtLeast(3_000L))
                val introFrames = if (proj.hasPlayableStream) {
                    runCatching {
                        mediaProcessor.extractClipWindowFrames(
                            uri = Uri.parse(proj.sourceUri),
                            startMs = seg.startMs,
                            endMs = introEnd,
                            count = 3
                        )
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                val result = aiRepository.generateFirst3SecVisualHook(
                    clipTitle = seg.title,
                    focusTopic = proj.focusTopic,
                    tone = seg.tone,
                    aspectRatio = seg.aspectRatio,
                    introFrames = introFrames
                )

                var beforeSegment: ClipSegment? = null
                var updatedSegment: ClipSegment? = null
                _editorState.update { state ->
                    val latest = state.segment ?: return@update state
                    beforeSegment = latest
                    val nextSeg = latest.copy(
                        hookLine = result.hookLine,
                        showIntroHook = true,
                        hookOverlayStyle = result.recommendedStyle
                    )
                    updatedSegment = nextSeg
                    state.copy(
                        isGeneratingHook = false,
                        hookRationale = result.rationale,
                        segment = nextSeg
                    )
                }
                val bHook = beforeSegment
                val aHook = updatedSegment
                if (bHook != null && aHook != null) {
                    recordClipHistory(
                        label = "Visual Hook AI",
                        actionCategory = "clip_ai_hook",
                        before = bHook,
                        after = aHook
                    )
                }
                updatedSegment?.let {
                    projectRepository.updateSegment(it)
                    syncSegmentUpdateToCloud(it)
                }
                _statusBannerMessage.value = if (result.isPlaceholderMode) {
                    "[Sampel AI] Visual hook 3 detik pertama dibuat (${result.recommendedStyle.label})."
                } else {
                    "Gemini AI: Visual hook 3 detik pertama dibuat (${result.recommendedStyle.label})."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate visual hook", e)
                _editorState.update {
                    it.copy(
                        isGeneratingHook = false,
                        errorMessage = "Visual hook 3 detik perlu dibuat ulang."
                    )
                }
            }
        }
    }

    fun regenerateCaptionWithTone(tone: CaptionTone) {
        val current = _editorState.value
        val seg = current.segment ?: return
        val proj = current.project ?: return

        viewModelScope.launch {
            _editorState.update { it.copy(isRegeneratingCaption = true) }
            try {
                val variation = aiRepository.regenerateHookAndCaption(
                    clipTitle = seg.title,
                    currentCaption = seg.captionText,
                    focusTopic = proj.focusTopic,
                    tone = tone
                )
                var beforeSegment: ClipSegment? = null
                var afterSegment: ClipSegment? = null
                _editorState.update { state ->
                    val latest = state.segment ?: return@update state
                    beforeSegment = latest
                    val nextSeg = latest.copy(
                        hookLine = variation.hookLine,
                        captionText = variation.captionText,
                        hashtags = variation.hashtags,
                        tone = tone,
                        isPlaceholderAi = variation.isPlaceholderMode
                    )
                    afterSegment = nextSeg
                    state.copy(
                        isRegeneratingCaption = false,
                        isPlaceholderAi = variation.isPlaceholderMode,
                        segment = nextSeg
                    )
                }
                val bCap = beforeSegment
                val aCap = afterSegment
                if (bCap != null && aCap != null) {
                    recordClipHistory(
                        label = "Caption AI (${tone.label})",
                        actionCategory = "clip_ai_caption",
                        before = bCap,
                        after = aCap
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to regenerate caption", e)
                _editorState.update {
                    it.copy(
                        isRegeneratingCaption = false,
                        errorMessage = "Pembaruan caption perlu dicoba ulang."
                    )
                }
            }
        }
    }

    fun generateSubtitlesForActiveSegment() {
        val current = _editorState.value
        val seg = current.segment ?: return
        val proj = current.project ?: return
        if (current.isGeneratingSubtitles) return

        viewModelScope.launch {
            _editorState.update { it.copy(isGeneratingSubtitles = true, errorMessage = null) }
            try {
                val clipFrames = if (proj.hasPlayableStream) {
                    runCatching {
                        mediaProcessor.extractClipWindowFrames(
                            uri = Uri.parse(proj.sourceUri),
                            startMs = seg.startMs,
                            endMs = seg.endMs,
                            count = 4
                        )
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                val result = aiRepository.generateTimedSubtitles(
                    clipTitle = seg.title,
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    focusTopic = proj.focusTopic,
                    tone = seg.tone,
                    clipFrames = clipFrames
                )

                var beforeSegment: ClipSegment? = null
                var updatedSegment: ClipSegment? = null
                _editorState.update { state ->
                    val latest = state.segment ?: return@update state
                    beforeSegment = latest
                    val nextSegment = latest.copy(
                        subtitleCues = result.cues,
                        showSubtitles = true,
                        isPlaceholderAi = result.isPlaceholderMode
                    )
                    updatedSegment = nextSegment
                    state.copy(
                        isGeneratingSubtitles = false,
                        isPlaceholderAi = result.isPlaceholderMode,
                        segment = nextSegment
                    )
                }
                val bSub = beforeSegment
                val aSub = updatedSegment
                if (bSub != null && aSub != null) {
                    recordClipHistory(
                        label = "Subtitle Otomatis AI",
                        actionCategory = "clip_ai_subtitles",
                        before = bSub,
                        after = aSub
                    )
                }
                updatedSegment?.let {
                    projectRepository.updateSegment(it)
                    syncSegmentUpdateToCloud(it)
                }
                _statusBannerMessage.value = if (result.isPlaceholderMode) {
                    "${result.cues.size} baris subtitle [Sampel AI] dibuat & disinkronkan."
                } else {
                    "${result.cues.size} baris subtitle Gemini AI dibuat & disinkronkan."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate subtitles", e)
                _editorState.update {
                    it.copy(
                        isGeneratingSubtitles = false,
                        errorMessage = "Pembuatan subtitle otomatis perlu dicoba ulang."
                    )
                }
            }
        }
    }

    fun updateSubtitleCueText(index: Int, newText: String) {
        mutateEditorSegmentWithHistory(
            label = "Edit Subtitle",
            actionCategory = "clip_subtitle_cue_$index",
            coalesce = true
        ) { _, seg ->
            if (index !in seg.subtitleCues.indices) return@mutateEditorSegmentWithHistory seg
            val updatedCues = seg.subtitleCues.mapIndexed { idx, cue ->
                if (idx == index) cue.copy(text = newText) else cue
            }
            seg.copy(subtitleCues = updatedCues)
        }
    }

    fun saveEditorChanges() {
        val seg = _editorState.value.segment ?: return
        viewModelScope.launch {
            projectRepository.updateSegment(seg)
            syncSegmentUpdateToCloud(seg)
            _statusBannerMessage.value = "Perubahan klip disimpan & disinkronkan ke Cloud."
        }
    }

    fun exportSegment(segment: ClipSegment, sourceUriString: String? = null) {
        if (_exportProgress.value is ExportProgressState.Exporting) return

        activeExportJob = viewModelScope.launch {
            try {
                var project = projectRepository.getProject(segment.projectId)
                if (project != null && !project.hasPlayableStream) {
                    val isWebLink = project.sourceUri.startsWith("http://", ignoreCase = true) ||
                        project.sourceUri.startsWith("https://", ignoreCase = true)
                    val resolvedStream = if (isWebLink) {
                        runCatching { socialLinkResolver.resolveLink(project!!.sourceUri) }.getOrNull()
                    } else {
                        null
                    }
                    val streamUrl = resolvedStream?.playableStreamUrl
                    if (streamUrl != null) {
                        val upgraded = project.copy(
                            sourceUri = streamUrl,
                            hasPlayableStream = true
                        )
                        projectRepository.updateProject(upgraded)
                        firestoreRepository?.let { repo ->
                            viewModelScope.launch { repo.upsertProject(upgraded) }
                        }
                        if (_editorState.value.project?.id == upgraded.id) {
                            _editorState.update { it.copy(project = upgraded) }
                        }
                        project = upgraded
                    } else {
                        val platformLabel = project.sourcePlatformName ?: "media sosial"
                        _exportProgress.value = ExportProgressState.Error(
                            "Tautan $platformLabel membatasi unduhan file MP4 langsung sesuai kebijakan platform. Pilih file video dari HP atau tempel URL stream .mp4/.m3u8 untuk langsung diekspor ke Galeri."
                        )
                        return@launch
                    }
                }

                val uriStr = project?.sourceUri?.takeIf { project.hasPlayableStream }
                    ?: sourceUriString
                    ?: throw IllegalStateException("Sumber video proyek tidak ditemukan.")

                projectRepository.updateSegment(segment)

                _exportProgress.value = ExportProgressState.Exporting(
                    segmentId = segment.id,
                    clipTitle = segment.title,
                    progressPercent = 0
                )

                val exported = mediaProcessor.exportTrimmedClip(
                    sourceUri = Uri.parse(uriStr),
                    segment = segment,
                    onProgress = { pct ->
                        _exportProgress.value = ExportProgressState.Exporting(
                            segmentId = segment.id,
                            clipTitle = segment.title,
                            progressPercent = pct
                        )
                    },
                    onTransformerCreated = { transformer ->
                        activeTransformer = transformer
                    }
                )

                val savedId = projectRepository.saveExportedClip(exported)
                val finalClip = exported.copy(id = savedId)
                firestoreRepository?.upsertExportedClip(finalClip, project)
                _exportProgress.value = ExportProgressState.Completed(finalClip)
                _statusBannerMessage.value = if (finalClip.galleryUri != null) {
                    "Ekspor MP4 selesai & tersimpan ke Galeri (Movies/AIClipper)"
                } else {
                    "Ekspor MP4 selesai: ${finalClip.title}"
                }
            } catch (e: CancellationException) {
                _exportProgress.value = ExportProgressState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export aborted cleanly", e)
                val rawMessage = e.message?.trim().orEmpty()
                val safeHint = rawMessage
                    .takeIf {
                        it.isNotBlank() &&
                            !it.contains("error", ignoreCase = true) &&
                            !it.contains("gagal", ignoreCase = true) &&
                            !it.contains("buffering", ignoreCase = true)
                    }
                    ?: "Ekspor klip MP4 perlu disesuaikan. Coba turunkan kualitas ke 720p atau pilih file video MP4 lain."
                _exportProgress.value = ExportProgressState.Error(safeHint)
            } finally {
                activeTransformer = null
            }
        }
    }

    fun exportCombinedProjectWithTransitions(projectId: Long) {
        if (_exportProgress.value is ExportProgressState.Exporting) return

        activeExportJob = viewModelScope.launch {
            try {
                val project = projectRepository.getProject(projectId)
                    ?: throw IllegalStateException("Proyek tidak ditemukan.")
                if (!project.hasPlayableStream) {
                    _exportProgress.value = ExportProgressState.Error(
                        "Tautkan file video lokal terlebih dahulu sebelum menggabungkan semua klip."
                    )
                    return@launch
                }

                // Persist any unsaved transition/editor changes on the active segment first
                _editorState.value.segment?.let { activeSeg ->
                    if (activeSeg.projectId == projectId) {
                        projectRepository.updateSegment(activeSeg)
                    }
                }

                val projectSegments = projectRepository.getSegmentsForProject(projectId)
                    .sortedBy { it.startMs }
                if (projectSegments.isEmpty()) {
                    _exportProgress.value = ExportProgressState.Error("Belum ada klip dalam proyek ini.")
                    return@launch
                }

                val activeEditorSeg = _editorState.value.segment?.takeIf { it.projectId == projectId }
                val effectiveSegments = if (activeEditorSeg != null) {
                    projectSegments.map { seg ->
                        seg.copy(
                            aspectRatio = activeEditorSeg.aspectRatio,
                            exportQuality = activeEditorSeg.exportQuality,
                            transitionStyle = activeEditorSeg.transitionStyle,
                            transitionDurationMs = activeEditorSeg.transitionDurationMs,
                            selectedMusicTrackId = activeEditorSeg.selectedMusicTrackId ?: seg.selectedMusicTrackId,
                            selectedMusicTitle = activeEditorSeg.selectedMusicTitle ?: seg.selectedMusicTitle,
                            selectedMusicUrl = activeEditorSeg.selectedMusicUrl ?: seg.selectedMusicUrl,
                            musicVolume = activeEditorSeg.musicVolume,
                            includeWatermark = activeEditorSeg.includeWatermark
                        )
                    }
                } else {
                    projectSegments
                }

                val title = "${project.title} (Kompilasi)"
                _exportProgress.value = ExportProgressState.Exporting(
                    segmentId = effectiveSegments.first().id,
                    clipTitle = title,
                    progressPercent = 0
                )

                val exported = mediaProcessor.exportCombinedClipsWithTransitions(
                    sourceUri = Uri.parse(project.sourceUri),
                    projectTitle = project.title,
                    segments = effectiveSegments,
                    onProgress = { pct ->
                        _exportProgress.value = ExportProgressState.Exporting(
                            segmentId = effectiveSegments.first().id,
                            clipTitle = title,
                            progressPercent = pct
                        )
                    },
                    onTransformerCreated = { transformer ->
                        activeTransformer = transformer
                    }
                )

                val savedId = projectRepository.saveExportedClip(exported)
                val finalClip = exported.copy(id = savedId)
                firestoreRepository?.upsertExportedClip(finalClip, project)
                _exportProgress.value = ExportProgressState.Completed(finalClip)
                _statusBannerMessage.value = "Video gabungan ${effectiveSegments.size} klip dengan transisi ${effectiveSegments.first().transitionStyle.label} selesai!"
            } catch (e: CancellationException) {
                _exportProgress.value = ExportProgressState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Combined export failed", e)
                _exportProgress.value = ExportProgressState.Error(
                    "Penggabungan klip dengan transisi perlu dicoba ulang."
                )
            } finally {
                activeTransformer = null
            }
        }
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeExportJob?.cancel()
        activeTransformer = null
        activeExportJob = null
        _exportProgress.value = ExportProgressState.Idle
        _statusBannerMessage.value = "Render klip dibatalkan."
    }

    fun resetExportState() {
        _exportProgress.value = ExportProgressState.Idle
    }

    fun saveExportToGallery(exportedClip: ExportedClip) {
        viewModelScope.launch {
            try {
                val savedUri = mediaProcessor.saveClipToGallery(exportedClip)
                if (savedUri != null) {
                    val updatedClip = exportedClip.copy(galleryUri = savedUri.toString())
                    if (exportedClip.galleryUri.isNullOrBlank()) {
                        projectRepository.saveExportedClip(updatedClip)
                        val parentProj = projectRepository.getProject(updatedClip.projectId)
                        firestoreRepository?.upsertExportedClip(updatedClip, parentProj)
                    }
                    _shareSheetState.update { current ->
                        if (current != null && current.clip.id == exportedClip.id) {
                            current.copy(clip = updatedClip)
                        } else {
                            current
                        }
                    }
                    _statusBannerMessage.value = "Tersimpan ke folder Movies/AIClipper."
                } else {
                    _statusBannerMessage.value = "File klip tidak ditemukan."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to gallery", e)
                _statusBannerMessage.value = "Penyimpanan ke galeri perlu dicoba ulang."
            }
        }
    }

    fun setAutoOpenShareSheet(enabled: Boolean) {
        _autoOpenShareSheet.value = enabled
    }

    fun openShareSheetForClip(exportedClip: ExportedClip) {
        viewModelScope.launch {
            val shareUri = mediaProcessor.getShareableUri(exportedClip)
            if (shareUri == null) {
                _statusBannerMessage.value = "File video ekspor tidak ditemukan di penyimpanan."
                return@launch
            }
            val segment = runCatching {
                if (exportedClip.segmentId > 0L) projectRepository.getSegment(exportedClip.segmentId) else null
            }.getOrNull()
            val caption = SocialShareHelper.buildDefaultCaption(exportedClip, segment)
            _shareSheetState.value = ShareSheetUiState(
                clip = exportedClip,
                shareUri = shareUri,
                initialCaption = caption
            )
        }
    }

    fun dismissShareSheet() {
        _shareSheetState.value = null
    }

    fun postStatusMessage(message: String) {
        _statusBannerMessage.value = message
    }

    fun getShareUri(exportedClip: ExportedClip): Uri? = mediaProcessor.getShareableUri(exportedClip)

    fun deleteExportedClip(exportedClip: ExportedClip) {
        viewModelScope.launch {
            mediaProcessor.deleteExportFileIfOwned(exportedClip)
            projectRepository.deleteExportedClip(exportedClip.id)
            firestoreRepository?.deleteExportedClip(exportedClip.id)
            _statusBannerMessage.value = "File ekspor dihapus."
        }
    }

    fun updateBatchPlatform(platform: TargetPlatform) {
        _batchConfig.update { it.copy(targetPlatform = platform) }
    }

    fun updateBatchFilter(filter: VideoFilterPreset) {
        _batchConfig.update { it.copy(videoFilter = filter) }
    }

    fun updateBatchAutoExport(enabled: Boolean) {
        _batchConfig.update { it.copy(autoExportTopClip = enabled) }
    }

    fun updateBatchWatermark(include: Boolean) {
        _batchConfig.update { it.copy(includeWatermark = include) }
    }

    fun enqueueGalleryVideos(uris: List<Uri>, startImmediately: Boolean = false) {
        if (uris.isEmpty()) return
        val cfg = _batchConfig.value
        val newItems = uris.map { uri ->
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            BatchQueueItem(
                id = UUID.randomUUID().toString(),
                sourceLabel = mediaProcessor.resolveDisplayName(uri),
                sourceUri = uri.toString(),
                isSocialLink = false,
                targetPlatform = cfg.targetPlatform,
                videoFilter = cfg.videoFilter,
                autoExportTopClip = cfg.autoExportTopClip,
                includeWatermark = cfg.includeWatermark
            )
        }
        _batchQueue.update { it + newItems }
        _statusBannerMessage.value = "${newItems.size} video ditambahkan ke antrean batch."
        if (startImmediately) {
            startBatchProcessing()
        }
    }

    fun enqueueSocialLinks(rawInput: String, startImmediately: Boolean = false) {
        val lines = rawInput
            .split(Regex("[\\r\\n]+"))
            .mapNotNull { line -> socialLinkResolver.extractFirstUrl(line) }
            .distinct()
        if (lines.isEmpty()) {
            _statusBannerMessage.value = "Masukkan minimal 1 tautan video eksternal yang valid dan aman."
            return
        }
        val cfg = _batchConfig.value
        val newItems = lines.map { link ->
            BatchQueueItem(
                id = UUID.randomUUID().toString(),
                sourceLabel = link.removePrefix("https://").removePrefix("http://").take(42),
                sourceUri = link,
                isSocialLink = true,
                targetPlatform = cfg.targetPlatform,
                videoFilter = cfg.videoFilter,
                autoExportTopClip = cfg.autoExportTopClip,
                includeWatermark = cfg.includeWatermark
            )
        }
        _batchQueue.update { it + newItems }
        _statusBannerMessage.value = "${newItems.size} tautan ditambahkan ke antrean batch."
        if (startImmediately) {
            startBatchProcessing()
        }
    }

    fun enqueuePopularVideo(item: PopularVideoItem) {
        val cfg = _batchConfig.value
        val queueItem = BatchQueueItem(
            id = UUID.randomUUID().toString(),
            sourceLabel = item.title,
            sourceUri = item.streamUrl,
            isSocialLink = true,
            durationMs = item.durationMs,
            targetPlatform = cfg.targetPlatform,
            videoFilter = cfg.videoFilter,
            autoExportTopClip = cfg.autoExportTopClip,
            includeWatermark = cfg.includeWatermark
        )
        _batchQueue.update { it + queueItem }
        _statusBannerMessage.value = "\"${item.title}\" ditambahkan ke antrean batch."
    }

    fun startBatchProcessing() {
        if (_isBatchRunning.value) return
        val hasPending = _batchQueue.value.any { it.status == BatchItemStatus.QUEUED }
        if (!hasPending) {
            _statusBannerMessage.value = "Tidak ada item berstatus Menunggu dalam antrean."
            return
        }

        activeBatchJob = viewModelScope.launch {
            _isBatchRunning.value = true
            var completedInRun = 0
            try {
                while (true) {
                    val nextItem = _batchQueue.value.firstOrNull { it.status == BatchItemStatus.QUEUED }
                        ?: break
                    val succeeded = processSingleBatchItem(nextItem)
                    if (succeeded) {
                        completedInRun += 1
                    }
                }
                if (completedInRun > 0) {
                    _statusBannerMessage.value = "Batch selesai: $completedInRun video berhasil diproses."
                }
            } catch (e: CancellationException) {
                _batchQueue.update { list ->
                    list.map { item ->
                        if (item.isActive) {
                            item.copy(
                                status = BatchItemStatus.CANCELLED,
                                stageDetail = "Dibatalkan oleh pengguna"
                            )
                        } else {
                            item
                        }
                    }
                }
                throw e
            } finally {
                activeBatchTransformer = null
                _isBatchRunning.value = false
            }
        }
    }

    private suspend fun processSingleBatchItem(initialItem: BatchQueueItem): Boolean {
        val itemId = initialItem.id
        fun updateItem(transform: (BatchQueueItem) -> BatchQueueItem) {
            _batchQueue.update { list ->
                list.map { if (it.id == itemId) transform(it) else it }
            }
        }

        return try {
            updateItem {
                it.copy(
                    status = BatchItemStatus.INSPECTING,
                    progressPercent = 10,
                    stageDetail = "Memeriksa sumber & metadata video…",
                    errorMessage = null
                )
            }

            val videoMeta: VideoMetadata
            val thumbnails: List<Bitmap>

            if (initialItem.isSocialLink) {
                val resolved = socialLinkResolver.resolveLink(initialItem.sourceUri)
                val streamUrl = resolved.playableStreamUrl
                if (streamUrl != null) {
                    val streamUri = Uri.parse(streamUrl)
                    val inspected = runCatching { mediaProcessor.extractMetadata(streamUri) }.getOrNull()
                    val finalDur = inspected?.durationMs?.takeIf { it > 1_000L } ?: resolved.durationMs
                    val finalW = inspected?.width?.takeIf { it > 0 } ?: resolved.width
                    val finalH = inspected?.height?.takeIf { it > 0 } ?: resolved.height
                    updateItem {
                        it.copy(
                            sourceLabel = resolved.title,
                            durationMs = finalDur,
                            progressPercent = 22,
                            stageDetail = "Mengambil frame pratinjau…"
                        )
                    }
                    thumbnails = runCatching {
                        mediaProcessor.extractThumbnails(streamUri, finalDur, count = 6)
                    }.getOrDefault(emptyList())
                    videoMeta = VideoMetadata(
                        uriString = streamUrl,
                        title = resolved.title,
                        durationMs = finalDur,
                        width = finalW,
                        height = finalH,
                        sourcePlatform = resolved.platform,
                        authorName = resolved.authorName,
                        thumbnailUrl = resolved.thumbnailUrl,
                        originalShareUrl = resolved.originalUrl,
                        hasPlayableStream = true,
                        streamStatusNote = resolved.statusNote
                    )
                } else {
                    updateItem {
                        it.copy(
                            sourceLabel = resolved.title,
                            durationMs = resolved.durationMs,
                            progressPercent = 24,
                            stageDetail = "Metadata sosial berhasil diambil"
                        )
                    }
                    thumbnails = emptyList()
                    videoMeta = VideoMetadata(
                        uriString = resolved.resolvedPageUrl,
                        title = resolved.title,
                        durationMs = resolved.durationMs,
                        width = resolved.width,
                        height = resolved.height,
                        sourcePlatform = resolved.platform,
                        authorName = resolved.authorName,
                        thumbnailUrl = resolved.thumbnailUrl,
                        originalShareUrl = resolved.originalUrl,
                        hasPlayableStream = false,
                        streamStatusNote = resolved.statusNote
                    )
                }
            } else {
                val localUri = mediaProcessor.resolveDurableLocalUri(Uri.parse(initialItem.sourceUri))
                val inspected = mediaProcessor.extractMetadata(localUri)
                updateItem {
                    it.copy(
                        sourceLabel = inspected.title,
                        durationMs = inspected.durationMs,
                        progressPercent = 20,
                        stageDetail = "Mengekstrak frame video…"
                    )
                }
                thumbnails = runCatching {
                    mediaProcessor.extractThumbnails(localUri, inspected.durationMs, count = 6)
                }.getOrDefault(emptyList())
                videoMeta = inspected
            }

            updateItem {
                it.copy(
                    status = BatchItemStatus.ANALYZING,
                    progressPercent = 35,
                    stageDetail = "AI mendeteksi momen terbaik…"
                )
            }
            delay(250L)

            val aiResult = aiRepository.analyzeVideoForClips(
                projectId = 0L,
                videoTitle = videoMeta.title,
                durationMs = videoMeta.durationMs,
                width = videoMeta.width,
                height = videoMeta.height,
                focusTopic = "",
                targetPlatform = initialItem.targetPlatform,
                sampleFrames = thumbnails
            )

            updateItem {
                it.copy(
                    progressPercent = 52,
                    stageDetail = "Menyimpan ${aiResult.detectedClips.size} klip ke proyek…"
                )
            }

            val project = ClipProject(
                title = videoMeta.title,
                sourceUri = videoMeta.uriString,
                durationMs = videoMeta.durationMs,
                width = videoMeta.width,
                height = videoMeta.height,
                targetPlatform = initialItem.targetPlatform,
                focusTopic = "",
                sourcePlatformName = videoMeta.sourcePlatform?.displayName,
                authorName = videoMeta.authorName,
                thumbnailUrl = videoMeta.thumbnailUrl,
                hasPlayableStream = videoMeta.hasPlayableStream,
                isPlaceholderAi = aiResult.isPlaceholderMode
            )

            val savedProjectId = projectRepository.saveProjectWithSegments(project) { projectId ->
                aiResult.detectedClips.map {
                    it.copy(
                        projectId = projectId,
                        videoFilter = initialItem.videoFilter,
                        includeWatermark = initialItem.includeWatermark
                    )
                }
            }
            val savedSegments = projectRepository.getSegmentsForProject(savedProjectId)
            syncProjectSnapshotToCloud(savedProjectId)
            val topSegment = savedSegments.maxByOrNull { it.viralScore }

            var exportedId: Long? = null
            if (initialItem.autoExportTopClip && videoMeta.hasPlayableStream && topSegment != null) {
                updateItem {
                    it.copy(
                        status = BatchItemStatus.RENDERING,
                        progressPercent = 58,
                        stageDetail = "Merender klip #1 ke MP4…",
                        createdProjectId = savedProjectId,
                        generatedClipsCount = savedSegments.size
                    )
                }
                val exported = mediaProcessor.exportTrimmedClip(
                    sourceUri = Uri.parse(videoMeta.uriString),
                    segment = topSegment,
                    onProgress = { renderPct ->
                        val mappedPct = 58 + ((renderPct / 100f) * 40f).toInt()
                        updateItem {
                            it.copy(
                                progressPercent = mappedPct.coerceIn(58, 98),
                                stageDetail = "Merender klip #1 ($renderPct%)…"
                            )
                        }
                    },
                    onTransformerCreated = { transformer ->
                        activeBatchTransformer = transformer
                    }
                )
                activeBatchTransformer = null
                exportedId = projectRepository.saveExportedClip(exported)
                val savedProj = projectRepository.getProject(savedProjectId)
                firestoreRepository?.upsertExportedClip(exported.copy(id = exportedId), savedProj)
            }

            updateItem {
                it.copy(
                    status = BatchItemStatus.COMPLETED,
                    progressPercent = 100,
                    stageDetail = if (exportedId != null) {
                        "${savedSegments.size} klip dibuat & klip #1 tersimpan di Galeri"
                    } else if (!videoMeta.hasPlayableStream) {
                        "${savedSegments.size} klip AI dibuat (tautkan file lokal untuk render MP4)"
                    } else {
                        "${savedSegments.size} klip terbaik siap diedit"
                    },
                    createdProjectId = savedProjectId,
                    generatedClipsCount = savedSegments.size,
                    exportedClipId = exportedId
                )
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Batch item failed: ${initialItem.sourceLabel}", e)
            activeBatchTransformer = null
            updateItem {
                it.copy(
                    status = BatchItemStatus.FAILED,
                    stageDetail = "Perlu diproses ulang",
                    errorMessage = "Video belum dapat diproses otomatis. Ketuk Coba Lagi."
                )
            }
            false
        }
    }

    fun retryBatchItem(itemId: String) {
        _batchQueue.update { list ->
            list.map { item ->
                if (item.id == itemId && (item.status == BatchItemStatus.FAILED || item.status == BatchItemStatus.CANCELLED)) {
                    item.copy(
                        status = BatchItemStatus.QUEUED,
                        progressPercent = 0,
                        stageDetail = "Siap diproses ulang",
                        errorMessage = null
                    )
                } else {
                    item
                }
            }
        }
        if (!_isBatchRunning.value) {
            startBatchProcessing()
        }
    }

    fun removeBatchItem(itemId: String) {
        _batchQueue.update { list ->
            list.filterNot { it.id == itemId && !it.isActive }
        }
    }

    fun clearCompletedBatchItems() {
        _batchQueue.update { list ->
            list.filterNot { it.status == BatchItemStatus.COMPLETED }
        }
    }

    fun cancelBatchProcessing() {
        activeBatchTransformer?.cancel()
        activeBatchTransformer = null
        activeBatchJob?.cancel()
        activeBatchJob = null
        _isBatchRunning.value = false
        _batchQueue.update { list ->
            list.map { item ->
                if (item.isActive) {
                    item.copy(
                        status = BatchItemStatus.CANCELLED,
                        stageDetail = "Dihentikan oleh pengguna"
                    )
                } else {
                    item
                }
            }
        }
        _statusBannerMessage.value = "Proses batch dihentikan."
    }

    fun openShareSheetByExportedId(exportedClipId: Long) {
        val clip = exportedClips.value.firstOrNull { it.id == exportedClipId }
        if (clip != null) {
            openShareSheetForClip(clip)
        } else {
            _statusBannerMessage.value = "File ekspor belum tersedia."
        }
    }

    fun enqueueActiveEditorSegmentForBackgroundExport(autoStart: Boolean = true) {
        val seg = _editorState.value.segment ?: return
        val proj = _editorState.value.project
        enqueueClipForBackgroundExport(
            segment = seg,
            sourceUriString = proj?.sourceUri,
            projectTitle = proj?.title,
            autoStart = autoStart
        )
    }

    fun enqueueManualStudioClipForBackgroundExport(autoStart: Boolean = true) {
        val current = _studioState.value
        val video = current.selectedVideo ?: return
        if (!video.hasPlayableStream) {
            _statusBannerMessage.value = "Tautkan file video lokal atau URL stream video langsung untuk mengantrekan ekspor."
            return
        }
        viewModelScope.launch {
            try {
                val cleanTopic = current.focusTopic.trim().ifEmpty { video.title }
                val project = ClipProject(
                    title = video.title,
                    sourceUri = video.uriString,
                    durationMs = video.durationMs,
                    width = video.width,
                    height = video.height,
                    targetPlatform = current.selectedPlatform,
                    focusTopic = current.focusTopic,
                    sourcePlatformName = video.sourcePlatform?.displayName,
                    authorName = video.authorName,
                    thumbnailUrl = video.thumbnailUrl,
                    hasPlayableStream = true,
                    isPlaceholderAi = false
                )
                val manualSegment = ClipSegment(
                    projectId = 0L,
                    title = "Klip Pilihan (${video.title.take(18)})",
                    hookLine = "Sorotan pilihan dari $cleanTopic",
                    viralScore = 90,
                    rationale = "Rentang waktu dipilih langsung melalui slider timeline Studio.",
                    captionText = "Klip pilihan dari ${video.title} untuk ${current.selectedPlatform.displayName}.",
                    hashtags = "#AIClipper #ClipManual #Shorts",
                    startMs = current.trimStartMs,
                    endMs = current.trimEndMs,
                    aspectRatio = current.selectedPlatform.defaultRatio,
                    tone = CaptionTone.ENERGETIC,
                    videoFilter = current.selectedFilter,
                    brightness = current.brightness,
                    showSubtitles = true,
                    includeWatermark = current.includeWatermark,
                    isPlaceholderAi = false
                )
                val savedProjectId = projectRepository.saveProjectWithSegments(project) { projId ->
                    listOf(manualSegment.copy(projectId = projId))
                }
                val persistedSegment = projectRepository.getSegmentsForProject(savedProjectId).firstOrNull()
                    ?: manualSegment.copy(projectId = savedProjectId)
                syncProjectSnapshotToCloud(savedProjectId)
                _selectedProjectId.value = savedProjectId
                enqueueClipForBackgroundExport(
                    segment = persistedSegment,
                    sourceUriString = video.uriString,
                    projectTitle = video.title,
                    autoStart = autoStart
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue manual studio clip", e)
                _statusBannerMessage.value = "Antrean klip Studio perlu dicoba ulang."
            }
        }
    }

    fun enqueueClipForBackgroundExport(
        segment: ClipSegment,
        sourceUriString: String? = null,
        projectTitle: String? = null,
        autoStart: Boolean = true
    ) {
        viewModelScope.launch {
            val project = projectRepository.getProject(segment.projectId)
            if (project != null && !project.hasPlayableStream) {
                _statusBannerMessage.value = "Tautkan file video lokal terlebih dahulu agar klip dapat dirender di latar belakang."
                return@launch
            }
            val uriStr = sourceUriString ?: project?.sourceUri
            if (uriStr.isNullOrBlank()) {
                _statusBannerMessage.value = "Sumber video proyek tidak ditemukan."
                return@launch
            }
            val parsedUri = Uri.parse(uriStr)
            val scheme = parsedUri.scheme?.lowercase()
            if ((scheme == "http" || scheme == "https") && !UrlSecurityValidator.isSafeExternalHttpUrl(uriStr)) {
                _statusBannerMessage.value = "URL sumber video tidak diizinkan untuk render latar belakang."
                return@launch
            }

            if (segment.id > 0L) {
                projectRepository.updateSegment(segment)
                syncSegmentUpdateToCloud(segment)
            }

            val snapshot = segment.copy(subtitleCues = segment.subtitleCues.toList())
            val job = ExportRenderJob(
                id = UUID.randomUUID().toString(),
                projectId = segment.projectId,
                projectTitle = projectTitle ?: project?.title ?: snapshot.title,
                segmentSnapshot = snapshot,
                sourceUriString = uriStr
            )
            _exportRenderQueue.update { it + job }
            _statusBannerMessage.value = "\"${snapshot.title}\" ditambahkan ke antrean render latar belakang."
            if (autoStart) {
                startExportRenderQueueWorker()
            }
        }
    }

    fun enqueueMultipleClipsForBackgroundExport(
        segments: List<ClipSegment>,
        sourceUriString: String? = null,
        projectTitle: String? = null,
        autoStart: Boolean = true
    ) {
        if (segments.isEmpty()) return
        viewModelScope.launch {
            val jobs = mutableListOf<ExportRenderJob>()
            for (seg in segments) {
                val project = projectRepository.getProject(seg.projectId)
                if (project != null && !project.hasPlayableStream) continue
                val uriStr = sourceUriString ?: project?.sourceUri ?: continue
                val scheme = Uri.parse(uriStr).scheme?.lowercase()
                if ((scheme == "http" || scheme == "https") && !UrlSecurityValidator.isSafeExternalHttpUrl(uriStr)) {
                    continue
                }
                if (seg.id > 0L) {
                    projectRepository.updateSegment(seg)
                }
                val snapshot = seg.copy(subtitleCues = seg.subtitleCues.toList())
                jobs += ExportRenderJob(
                    id = UUID.randomUUID().toString(),
                    projectId = seg.projectId,
                    projectTitle = projectTitle ?: project?.title ?: snapshot.title,
                    segmentSnapshot = snapshot,
                    sourceUriString = uriStr
                )
            }
            if (jobs.isEmpty()) {
                _statusBannerMessage.value = "Tidak ada klip dengan sumber video siap render."
                return@launch
            }
            _exportRenderQueue.update { it + jobs }
            _statusBannerMessage.value = "${jobs.size} klip masuk antrean render latar belakang. Anda bebas mengedit video lain."
            if (autoStart) {
                startExportRenderQueueWorker()
            }
        }
    }

    fun startExportRenderQueueWorker() {
        if (activeRenderQueueJob?.isActive == true) return
        if (_exportRenderQueue.value.none { it.status == ExportJobStatus.QUEUED }) return

        activeRenderQueueJob = viewModelScope.launch {
            _isRenderQueueRunning.value = true
            var completedCount = 0
            try {
                while (true) {
                    val nextJob = _exportRenderQueue.value.firstOrNull { it.status == ExportJobStatus.QUEUED }
                        ?: break

                    while (_exportProgress.value is ExportProgressState.Exporting) {
                        delay(300L)
                    }

                    // Re-check status in case the queued job was cancelled while waiting
                    val currentNext = _exportRenderQueue.value.firstOrNull { it.id == nextJob.id }
                    if (currentNext == null || currentNext.status != ExportJobStatus.QUEUED) {
                        continue
                    }

                    activeRenderQueueJobId = currentNext.id
                    val ok = processSingleExportRenderJob(currentNext)
                    activeRenderQueueJobId = null
                    if (ok) {
                        completedCount += 1
                    }
                }
                if (completedCount > 0) {
                    _statusBannerMessage.value = "Antrean render selesai: $completedCount klip siap di Galeri Ekspor."
                }
            } finally {
                activeRenderQueueTransformer = null
                activeRenderQueueJobId = null
                _isRenderQueueRunning.value = false
            }
        }
    }

    private suspend fun processSingleExportRenderJob(job: ExportRenderJob): Boolean {
        val jobId = job.id
        fun updateJob(transform: (ExportRenderJob) -> ExportRenderJob) {
            _exportRenderQueue.update { list ->
                list.map { if (it.id == jobId) transform(it) else it }
            }
        }

        return try {
            updateJob {
                it.copy(
                    status = ExportJobStatus.RENDERING,
                    progressPercent = 1,
                    stageDetail = "Menyiapkan pipeline render ${job.segmentSnapshot.exportQuality.label}…",
                    errorMessage = null
                )
            }

            val exported = mediaProcessor.exportTrimmedClip(
                sourceUri = Uri.parse(job.sourceUriString),
                segment = job.segmentSnapshot,
                onProgress = { pct ->
                    updateJob {
                        if (it.status == ExportJobStatus.CANCELLED) it
                        else it.copy(
                            progressPercent = pct.coerceIn(0, 99),
                            stageDetail = "Merender ${job.segmentSnapshot.aspectRatio.label} • ${job.segmentSnapshot.exportQuality.label} ($pct%)…"
                        )
                    }
                },
                onTransformerCreated = { transformer ->
                    activeRenderQueueTransformer = transformer
                }
            )
            activeRenderQueueTransformer = null

            val latestJob = _exportRenderQueue.value.firstOrNull { it.id == jobId }
            if (latestJob?.status == ExportJobStatus.CANCELLED) {
                return false
            }

            val savedId = projectRepository.saveExportedClip(exported)
            val finalClip = exported.copy(id = savedId)
            val parentProj = runCatching { projectRepository.getProject(job.projectId) }.getOrNull()
            firestoreRepository?.upsertExportedClip(finalClip, parentProj)

            updateJob {
                it.copy(
                    status = ExportJobStatus.COMPLETED,
                    progressPercent = 100,
                    stageDetail = if (finalClip.galleryUri != null) {
                        "Selesai & tersimpan di Galeri"
                    } else {
                        "Render MP4 selesai"
                    },
                    exportedClip = finalClip
                )
            }
            true
        } catch (e: CancellationException) {
            activeRenderQueueTransformer = null
            val latestStatus = _exportRenderQueue.value.firstOrNull { it.id == jobId }?.status
            if (latestStatus == ExportJobStatus.CANCELLED) {
                false
            } else {
                updateJob {
                    it.copy(
                        status = ExportJobStatus.CANCELLED,
                        stageDetail = "Render dibatalkan"
                    )
                }
                throw e
            }
        } catch (e: Exception) {
            activeRenderQueueTransformer = null
            val latestStatus = _exportRenderQueue.value.firstOrNull { it.id == jobId }?.status
            if (latestStatus == ExportJobStatus.CANCELLED) {
                false
            } else {
                Log.e(TAG, "Background export render failed for ${job.segmentSnapshot.title}", e)
                updateJob {
                    it.copy(
                        status = ExportJobStatus.FAILED,
                        stageDetail = "Perlu render ulang",
                        errorMessage = "Render MP4 perlu dicoba ulang. Pastikan video sumber tersedia."
                    )
                }
                false
            }
        }
    }

    fun cancelExportRenderJob(jobId: String) {
        val isCurrentActive = activeRenderQueueJobId == jobId
        _exportRenderQueue.update { list ->
            list.map { job ->
                if (job.id == jobId && job.isPendingOrActive) {
                    job.copy(
                        status = ExportJobStatus.CANCELLED,
                        stageDetail = "Dibatalkan oleh pengguna"
                    )
                } else {
                    job
                }
            }
        }
        if (isCurrentActive) {
            activeRenderQueueTransformer?.cancel()
            activeRenderQueueTransformer = null
        }
    }

    fun retryExportRenderJob(jobId: String, autoStart: Boolean = true) {
        _exportRenderQueue.update { list ->
            list.map { job ->
                if (job.id == jobId && (job.status == ExportJobStatus.FAILED || job.status == ExportJobStatus.CANCELLED)) {
                    job.copy(
                        status = ExportJobStatus.QUEUED,
                        progressPercent = 0,
                        stageDetail = "Menunggu antrean render ulang",
                        errorMessage = null
                    )
                } else {
                    job
                }
            }
        }
        if (autoStart) {
            startExportRenderQueueWorker()
        }
    }

    fun removeExportRenderJob(jobId: String) {
        _exportRenderQueue.update { list ->
            list.filterNot { it.id == jobId && !it.isActive }
        }
    }

    fun clearCompletedExportRenderJobs() {
        _exportRenderQueue.update { list ->
            list.filterNot { it.status == ExportJobStatus.COMPLETED || it.status == ExportJobStatus.CANCELLED }
        }
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }
}
