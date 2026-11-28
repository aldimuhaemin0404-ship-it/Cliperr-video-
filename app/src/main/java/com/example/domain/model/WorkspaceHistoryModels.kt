package com.example.domain.model

data class StudioWorkspaceStateSnapshot(
    val trimStartMs: Long,
    val trimEndMs: Long,
    val selectedFilter: VideoFilterPreset,
    val brightness: Float,
    val selectedPlatform: TargetPlatform,
    val focusTopic: String,
    val includeWatermark: Boolean = false
)

sealed interface WorkspaceHistoryTarget {
    data object StudioWorkspace : WorkspaceHistoryTarget
    data class ClipSegmentTarget(val segmentId: Long) : WorkspaceHistoryTarget
}

sealed interface WorkspaceUndoEntry {
    val label: String
    val actionCategory: String
    val target: WorkspaceHistoryTarget
    val timestampMs: Long

    data class StudioEdit(
        override val label: String,
        override val actionCategory: String,
        val before: StudioWorkspaceStateSnapshot,
        val after: StudioWorkspaceStateSnapshot,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : WorkspaceUndoEntry {
        override val target: WorkspaceHistoryTarget = WorkspaceHistoryTarget.StudioWorkspace
    }

    data class ClipEdit(
        override val label: String,
        override val actionCategory: String,
        val before: ClipSegment,
        val after: ClipSegment,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : WorkspaceUndoEntry {
        override val target: WorkspaceHistoryTarget = WorkspaceHistoryTarget.ClipSegmentTarget(after.id)
    }
}

data class UndoHistoryUiState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
    val undoCount: Int = 0,
    val redoCount: Int = 0
)
