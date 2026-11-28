package com.example.domain.model

enum class BatchItemStatus(val label: String) {
    QUEUED("Menunggu"),
    INSPECTING("Memeriksa"),
    ANALYZING("Analisis AI"),
    RENDERING("Render MP4"),
    COMPLETED("Selesai"),
    FAILED("Perlu Ulang"),
    CANCELLED("Dibatalkan")
}

data class BatchQueueItem(
    val id: String,
    val sourceLabel: String,
    val sourceUri: String,
    val isSocialLink: Boolean = false,
    val durationMs: Long = 0L,
    val status: BatchItemStatus = BatchItemStatus.QUEUED,
    val progressPercent: Int = 0,
    val stageDetail: String = "Menunggu antrean proses",
    val targetPlatform: TargetPlatform = TargetPlatform.REELS_TIKTOK,
    val videoFilter: VideoFilterPreset = VideoFilterPreset.NONE,
    val includeWatermark: Boolean = false,
    val autoExportTopClip: Boolean = true,
    val createdProjectId: Long? = null,
    val generatedClipsCount: Int = 0,
    val exportedClipId: Long? = null,
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = status == BatchItemStatus.INSPECTING ||
            status == BatchItemStatus.ANALYZING ||
            status == BatchItemStatus.RENDERING

    val isTerminal: Boolean
        get() = status == BatchItemStatus.COMPLETED ||
            status == BatchItemStatus.FAILED ||
            status == BatchItemStatus.CANCELLED
}

data class BatchQueueConfig(
    val targetPlatform: TargetPlatform = TargetPlatform.REELS_TIKTOK,
    val videoFilter: VideoFilterPreset = VideoFilterPreset.NONE,
    val includeWatermark: Boolean = false,
    val autoExportTopClip: Boolean = true
)

enum class ExportJobStatus(val label: String) {
    QUEUED("Menunggu"),
    RENDERING("Merender MP4"),
    COMPLETED("Selesai"),
    FAILED("Perlu Ulang"),
    CANCELLED("Dibatalkan")
}

data class ExportRenderJob(
    val id: String,
    val projectId: Long,
    val projectTitle: String,
    val segmentSnapshot: ClipSegment,
    val sourceUriString: String,
    val status: ExportJobStatus = ExportJobStatus.QUEUED,
    val progressPercent: Int = 0,
    val stageDetail: String = "Menunggu antrean render latar belakang",
    val exportedClip: ExportedClip? = null,
    val errorMessage: String? = null,
    val queuedAtMs: Long = System.currentTimeMillis()
) {
    val isActive: Boolean
        get() = status == ExportJobStatus.RENDERING

    val isPendingOrActive: Boolean
        get() = status == ExportJobStatus.QUEUED || status == ExportJobStatus.RENDERING

    val isTerminal: Boolean
        get() = status == ExportJobStatus.COMPLETED ||
            status == ExportJobStatus.FAILED ||
            status == ExportJobStatus.CANCELLED
}

