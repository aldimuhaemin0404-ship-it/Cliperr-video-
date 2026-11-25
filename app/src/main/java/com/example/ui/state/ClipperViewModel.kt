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
import com.example.data.ai.PlaceholderAiClipperRepository
import com.example.data.local.AppDatabase
import com.example.data.local.ClipProjectRepository
import com.example.data.network.SocialLinkResolver
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.BatchItemStatus
import com.example.domain.model.BatchQueueConfig
import com.example.domain.model.BatchQueueItem
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportProgressState
import com.example.domain.model.ExportQuality
import com.example.domain.model.ExportedClip
import com.example.domain.model.PopularVideoItem
import com.example.domain.model.StudioImportSource
import com.example.domain.model.TargetPlatform
import com.example.domain.model.VideoFilterPreset
import com.example.domain.model.VideoMetadata
import com.example.media.SocialShareHelper
import com.example.media.VideoMediaProcessor
import com.example.ui.components.ShareSheetUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val isPlaceholderAi: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ClipperViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val projectRepository = ClipProjectRepository(database.clipDao())
    private val aiRepository: AiClipperRepository = PlaceholderAiClipperRepository()
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

    private var activeExportJob: Job? = null
    private var activeAnalysisJob: Job? = null
    private var activeBatchJob: Job? = null
    private var activeTransformer: Transformer? = null
    private var activeBatchTransformer: Transformer? = null

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
        viewModelScope.launch {
            _studioState.update {
                it.copy(isLoadingVideo = true, errorMessage = null)
            }
            try {
                val resolved = socialLinkResolver.resolveLink(rawInput)
                val streamUrl = resolved.playableStreamUrl
                if (streamUrl != null) {
                    val streamUri = Uri.parse(streamUrl)
                    val inspected = runCatching { mediaProcessor.extractMetadata(streamUri) }.getOrNull()
                    val finalDuration = inspected?.durationMs?.takeIf { it > 1_000L } ?: resolved.durationMs
                    val finalWidth = inspected?.width?.takeIf { it > 0 } ?: resolved.width
                    val finalHeight = inspected?.height?.takeIf { it > 0 } ?: resolved.height
                    val thumbs = runCatching {
                        mediaProcessor.extractThumbnails(streamUri, finalDuration, count = 8)
                    }.getOrDefault(emptyList())

                    _studioState.update { state ->
                        val initialEnd = minOf(finalDuration, state.selectedPlatform.maxRecommendedSec * 1000L)
                            .coerceAtLeast(minOf(finalDuration, 1_000L))
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
                            filmstripThumbnails = thumbs,
                            trimStartMs = 0L,
                            trimEndMs = initialEnd,
                            suggestedHighlights = emptyList(),
                            isLoadingVideo = false
                        )
                    }
                    _statusBannerMessage.value = "Stream ${resolved.platform.displayName} siap diputar & dipotong."
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
                _studioState.update {
                    it.copy(
                        isLoadingVideo = false,
                        errorMessage = e.localizedMessage ?: "Gagal memproses link video. Pastikan link valid dan terhubung ke internet."
                    )
                }
            }
        }
    }

    fun loadPopularVideo(item: PopularVideoItem) {
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    importSource = StudioImportSource.SOCIAL_LINK,
                    linkInput = item.streamUrl,
                    isLoadingVideo = true,
                    errorMessage = null
                )
            }
            try {
                val streamUri = Uri.parse(item.streamUrl)
                val inspected = runCatching { mediaProcessor.extractMetadata(streamUri) }.getOrNull()
                val finalDuration = inspected?.durationMs?.takeIf { it > 1_000L } ?: item.durationMs
                val finalWidth = inspected?.width?.takeIf { it > 0 } ?: item.width
                val finalHeight = inspected?.height?.takeIf { it > 0 } ?: item.height
                val thumbs = runCatching {
                    mediaProcessor.extractThumbnails(streamUri, finalDuration, count = 8)
                }.getOrDefault(emptyList())

                _studioState.update { state ->
                    val initialEnd = minOf(finalDuration, state.selectedPlatform.maxRecommendedSec * 1000L)
                        .coerceAtLeast(minOf(finalDuration, 1_000L))
                    state.copy(
                        selectedVideo = VideoMetadata(
                            uriString = item.streamUrl,
                            title = item.title,
                            durationMs = finalDuration,
                            width = finalWidth,
                            height = finalHeight,
                            sourcePlatform = item.platform,
                            authorName = item.author,
                            thumbnailUrl = item.thumbnailUrl,
                            originalShareUrl = item.streamUrl,
                            hasPlayableStream = true,
                            streamStatusNote = "Video populer ${item.platform.displayName} siap diputar & dipotong."
                        ),
                        filmstripThumbnails = thumbs,
                        trimStartMs = 0L,
                        trimEndMs = initialEnd,
                        suggestedHighlights = emptyList(),
                        isLoadingVideo = false
                    )
                }
                _statusBannerMessage.value = "Video \"${item.title}\" siap diputar & dipotong!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load popular video", e)
                _studioState.update {
                    it.copy(
                        isLoadingVideo = false,
                        errorMessage = e.localizedMessage ?: "Gagal memuat video populer."
                    )
                }
            }
        }
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _studioState.update {
                it.copy(isLoadingVideo = true, errorMessage = null)
            }
            try {
                runCatching {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val metadata = mediaProcessor.extractMetadata(uri)
                val thumbs = mediaProcessor.extractThumbnails(uri, metadata.durationMs, count = 8)
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
                        filmstripThumbnails = thumbs,
                        trimStartMs = 0L,
                        trimEndMs = initialEnd,
                        suggestedHighlights = emptyList(),
                        isLoadingVideo = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect selected video", e)
                _studioState.update {
                    it.copy(
                        isLoadingVideo = false,
                        errorMessage = "Gagal membaca metadata video: ${e.localizedMessage ?: "Format tidak didukung"}"
                    )
                }
            }
        }
    }

    fun attachLocalVideoToProject(projectId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                runCatching {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val existing = projectRepository.getProject(projectId) ?: return@launch
                val metadata = mediaProcessor.extractMetadata(uri)
                val thumbs = mediaProcessor.extractThumbnails(uri, metadata.durationMs, count = 8)
                val updatedProject = existing.copy(
                    sourceUri = metadata.uriString,
                    durationMs = metadata.durationMs,
                    width = metadata.width,
                    height = metadata.height,
                    hasPlayableStream = true
                )
                projectRepository.updateProject(updatedProject)

                if (_editorState.value.project?.id == projectId) {
                    _editorState.update { state ->
                        val seg = state.segment
                        val clampedSeg = seg?.let {
                            val safeEnd = it.endMs.coerceIn(1_000L, metadata.durationMs)
                            val safeStart = it.startMs.coerceIn(0L, (safeEnd - 500L).coerceAtLeast(0L))
                            it.copy(startMs = safeStart, endMs = safeEnd)
                        }
                        if (clampedSeg != null) {
                            viewModelScope.launch { projectRepository.updateSegment(clampedSeg) }
                        }
                        state.copy(
                            project = updatedProject,
                            segment = clampedSeg,
                            thumbnails = thumbs
                        )
                    }
                }
                _statusBannerMessage.value = "File video lokal ditautkan. Sekarang siap diputar dan diekspor ke MP4!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach local video to project", e)
                _statusBannerMessage.value = "Gagal menautkan video: ${e.localizedMessage}"
            }
        }
    }

    fun updateTargetPlatform(platform: TargetPlatform) {
        _studioState.update { it.copy(selectedPlatform = platform) }
    }

    fun updateFocusTopic(topic: String) {
        _studioState.update { it.copy(focusTopic = topic) }
    }

    fun updateStudioTrim(startMs: Long, endMs: Long) {
        _studioState.update { state ->
            val maxDuration = state.selectedVideo?.durationMs ?: Long.MAX_VALUE
            val safeStart = startMs.coerceIn(0L, (maxDuration - 1_000L).coerceAtLeast(0L))
            val safeEnd = endMs.coerceIn((safeStart + 1_000L).coerceAtMost(maxDuration), maxDuration)
            state.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
        }
    }

    fun updateStudioFilter(filter: VideoFilterPreset) {
        _studioState.update { it.copy(selectedFilter = filter) }
    }

    fun updateStudioBrightness(brightness: Float) {
        _studioState.update { it.copy(brightness = brightness.coerceIn(-0.5f, 0.5f)) }
    }

    fun resetStudioFilters() {
        _studioState.update { it.copy(selectedFilter = VideoFilterPreset.NONE, brightness = 0f) }
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
                _studioState.update { state ->
                    state.copy(
                        isSuggestingTimestamps = false,
                        suggestedHighlights = aiResult.detectedClips,
                        trimStartMs = topClip?.startMs ?: state.trimStartMs,
                        trimEndMs = topClip?.endMs ?: state.trimEndMs
                    )
                }
                _statusBannerMessage.value = "Rekomendasi timestamp [Sampel AI] diterapkan ke slider."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Timestamp suggestion failed", e)
                _studioState.update {
                    it.copy(
                        isSuggestingTimestamps = false,
                        errorMessage = e.localizedMessage ?: "Gagal menyarankan timestamp klip."
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
                    isPlaceholderAi = false
                )
                val savedProjectId = projectRepository.saveProjectWithSegments(project) { projId ->
                    listOf(manualSegment.copy(projectId = projId))
                }
                val persistedSegment = projectRepository.getSegmentsForProject(savedProjectId).firstOrNull()
                    ?: manualSegment.copy(projectId = savedProjectId)
                _selectedProjectId.value = savedProjectId
                exportSegment(persistedSegment, video.uriString)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Manual studio export failed", e)
                _statusBannerMessage.value = "Gagal memulai ekspor: ${e.localizedMessage}"
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
                            brightness = current.brightness
                        )
                    }
                }

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
                        errorMessage = e.localizedMessage ?: "Analisis video gagal."
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
            _statusBannerMessage.value = "Proyek dihapus."
        }
    }

    fun toggleSegmentFavorite(segment: ClipSegment) {
        viewModelScope.launch {
            val updated = segment.copy(isFavorite = !segment.isFavorite)
            projectRepository.updateSegment(updated)
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
                    projectRepository.updateSegment(seg.copy(isFavorite = true))
                }
            }
            _statusBannerMessage.value = "Semua ${list.size} klip terbaik disimpan ke Favorit!"
        }
    }

    fun loadSegmentForEditor(segmentId: Long) {
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
                val thumbs = if (project.hasPlayableStream) {
                    runCatching {
                        mediaProcessor.extractThumbnails(
                            Uri.parse(project.sourceUri),
                            project.durationMs,
                            count = 8
                        )
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                _editorState.value = EditorUiState(
                    project = project,
                    segment = segment,
                    thumbnails = thumbs,
                    isLoading = false,
                    isPlaceholderAi = segment.isPlaceholderAi
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load clip editor", e)
                _editorState.update {
                    it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Gagal memuat editor.")
                }
            }
        }
    }

    fun updateEditorTrim(startMs: Long, endMs: Long) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            val maxDuration = state.project?.durationMs ?: Long.MAX_VALUE
            val safeStart = startMs.coerceIn(0L, (maxDuration - 1000L).coerceAtLeast(0L))
            val safeEnd = endMs.coerceIn((safeStart + 1000L).coerceAtMost(maxDuration), maxDuration)
            state.copy(segment = seg.copy(startMs = safeStart, endMs = safeEnd))
        }
    }

    fun updateEditorAspectRatio(ratio: AspectRatioPreset) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(aspectRatio = ratio))
        }
    }

    fun updateEditorFilter(filter: VideoFilterPreset) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(videoFilter = filter))
        }
    }

    fun updateEditorBrightness(brightness: Float) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(brightness = brightness.coerceIn(-0.5f, 0.5f)))
        }
    }

    fun resetEditorFilters() {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(videoFilter = VideoFilterPreset.NONE, brightness = 0f))
        }
    }

    fun updateEditorHook(hook: String) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(hookLine = hook))
        }
    }

    fun updateEditorCaption(caption: String) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(captionText = caption))
        }
    }

    fun toggleEditorSubtitles(show: Boolean) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(showSubtitles = show))
        }
    }

    fun toggleEditorIncludeAudio(include: Boolean) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(includeAudio = include))
        }
    }

    fun updateEditorExportQuality(quality: ExportQuality) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            state.copy(segment = seg.copy(exportQuality = quality))
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
                _editorState.update { state ->
                    val latest = state.segment ?: return@update state
                    state.copy(
                        isRegeneratingCaption = false,
                        isPlaceholderAi = variation.isPlaceholderMode,
                        segment = latest.copy(
                            hookLine = variation.hookLine,
                            captionText = variation.captionText,
                            hashtags = variation.hashtags,
                            tone = tone,
                            isPlaceholderAi = variation.isPlaceholderMode
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to regenerate caption", e)
                _editorState.update {
                    it.copy(
                        isRegeneratingCaption = false,
                        errorMessage = e.localizedMessage ?: "Gagal memperbarui caption."
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

                var updatedSegment: ClipSegment? = null
                _editorState.update { state ->
                    val latest = state.segment ?: return@update state
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
                updatedSegment?.let { projectRepository.updateSegment(it) }
                _statusBannerMessage.value = "${result.cues.size} baris subtitle [Sampel AI] dibuat & disinkronkan."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate subtitles", e)
                _editorState.update {
                    it.copy(
                        isGeneratingSubtitles = false,
                        errorMessage = e.localizedMessage ?: "Gagal membuat subtitle otomatis."
                    )
                }
            }
        }
    }

    fun updateSubtitleCueText(index: Int, newText: String) {
        _editorState.update { state ->
            val seg = state.segment ?: return@update state
            if (index !in seg.subtitleCues.indices) return@update state
            val updatedCues = seg.subtitleCues.mapIndexed { idx, cue ->
                if (idx == index) cue.copy(text = newText) else cue
            }
            state.copy(segment = seg.copy(subtitleCues = updatedCues))
        }
    }

    fun saveEditorChanges() {
        val seg = _editorState.value.segment ?: return
        viewModelScope.launch {
            projectRepository.updateSegment(seg)
            _statusBannerMessage.value = "Perubahan klip disimpan."
        }
    }

    fun exportSegment(segment: ClipSegment, sourceUriString: String? = null) {
        if (_exportProgress.value is ExportProgressState.Exporting) return

        activeExportJob = viewModelScope.launch {
            try {
                val project = projectRepository.getProject(segment.projectId)
                if (project != null && !project.hasPlayableStream) {
                    _exportProgress.value = ExportProgressState.Error(
                        "Tautkan file video lokal terlebih dahulu di Editor atau gunakan URL stream video langsung (.mp4/.m3u8) untuk merender MP4."
                    )
                    return@launch
                }

                val uriStr = sourceUriString
                    ?: project?.sourceUri
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
                Log.e(TAG, "Export failed", e)
                _exportProgress.value = ExportProgressState.Error(
                    e.localizedMessage ?: "Gagal mengekspor klip MP4."
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
                _statusBannerMessage.value = "Gagal menyimpan ke galeri: ${e.localizedMessage}"
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
            runCatching { File(exportedClip.filePath).delete() }
            projectRepository.deleteExportedClip(exportedClip.id)
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
                autoExportTopClip = cfg.autoExportTopClip
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
            .map { line -> socialLinkResolver.extractFirstUrl(line) ?: line.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            _statusBannerMessage.value = "Masukkan minimal 1 tautan video yang valid."
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
                autoExportTopClip = cfg.autoExportTopClip
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
            autoExportTopClip = cfg.autoExportTopClip
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
                val localUri = Uri.parse(initialItem.sourceUri)
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
                        videoFilter = initialItem.videoFilter
                    )
                }
            }
            val savedSegments = projectRepository.getSegmentsForProject(savedProjectId)
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
                    stageDetail = "Proses gagal",
                    errorMessage = e.localizedMessage ?: "Gagal memproses video."
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

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }
}
