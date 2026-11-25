package com.example.domain.model

enum class BatchItemStatus(val label: String) {
    QUEUED("Menunggu"),
    INSPECTING("Memeriksa"),
    ANALYZING("Analisis AI"),
    RENDERING("Render MP4"),
    COMPLETED("Selesai"),
    FAILED("Gagal"),
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
    val autoExportTopClip: Boolean = true
)
