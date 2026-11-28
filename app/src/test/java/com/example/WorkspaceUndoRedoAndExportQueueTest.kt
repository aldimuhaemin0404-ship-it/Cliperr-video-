package com.example

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ClipProjectRepository
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionTone
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportJobStatus
import com.example.domain.model.ExportQuality
import com.example.domain.model.TargetPlatform
import com.example.domain.model.VideoFilterPreset
import com.example.domain.model.VideoTransitionStyle
import com.example.ui.state.ClipperViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspaceUndoRedoAndExportQueueTest {

    private fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(25L)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition was not satisfied within ${timeoutMs}ms", predicate())
    }

    @Test
    fun `studioWorkspaceUndoRedo restores workspace state and clears redo on new edit`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ClipperViewModel(app)

        vm.updateTargetPlatform(TargetPlatform.YOUTUBE_SHORTS)
        vm.updateStudioFilter(VideoFilterPreset.VIVID)
        vm.updateStudioBrightness(0.25f)
        vm.updateFocusTopic("Podcast Viral")
        vm.updateStudioTrim(2_000L, 12_000L)

        val historyAfterEdits = vm.undoHistoryState.value
        assertTrue(historyAfterEdits.canUndo)
        assertFalse(historyAfterEdits.canRedo)
        assertEquals(5, historyAfterEdits.undoCount)

        // 1. Undo trim
        vm.undoGlobal()
        assertEquals(0L, vm.studioState.value.trimStartMs)
        assertEquals(0L, vm.studioState.value.trimEndMs)

        // 2. Undo focus topic
        vm.undoGlobal()
        assertEquals("", vm.studioState.value.focusTopic)

        // 3. Undo brightness
        vm.undoGlobal()
        assertEquals(0f, vm.studioState.value.brightness, 0.0001f)

        // 4. Undo filter
        vm.undoGlobal()
        assertEquals(VideoFilterPreset.NONE, vm.studioState.value.selectedFilter)

        // 5. Undo platform
        vm.undoGlobal()
        assertEquals(TargetPlatform.REELS_TIKTOK, vm.studioState.value.selectedPlatform)

        assertFalse(vm.undoHistoryState.value.canUndo)
        assertTrue(vm.undoHistoryState.value.canRedo)
        assertEquals(5, vm.undoHistoryState.value.redoCount)

        // Redo first two actions (platform + filter)
        vm.redoGlobal()
        assertEquals(TargetPlatform.YOUTUBE_SHORTS, vm.studioState.value.selectedPlatform)
        vm.redoGlobal()
        assertEquals(VideoFilterPreset.VIVID, vm.studioState.value.selectedFilter)
        assertEquals(3, vm.undoHistoryState.value.redoCount)

        // A new edit clears the remaining redo stack
        vm.updateStudioFilter(VideoFilterPreset.WARM)
        assertEquals(VideoFilterPreset.WARM, vm.studioState.value.selectedFilter)
        assertFalse(vm.undoHistoryState.value.canRedo)
        assertEquals(0, vm.undoHistoryState.value.redoCount)
    }

    @Test
    fun `multiClipUndoRedo tracks edits across multiple clips and persists to Room`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ClipProjectRepository(AppDatabase.getInstance(app).clipDao())

        val (segA, segB) = runBlocking {
            val projectId = repo.saveProjectWithSegments(
                ClipProject(
                    title = "Proyek Multi Klip",
                    sourceUri = "https://example.com/multi-clip.mp4",
                    durationMs = 45_000L,
                    width = 1920,
                    height = 1080,
                    targetPlatform = TargetPlatform.REELS_TIKTOK,
                    focusTopic = "Sorotan",
                    hasPlayableStream = false
                )
            ) { pid ->
                listOf(
                    ClipSegment(
                        projectId = pid,
                        title = "Klip A",
                        hookLine = "Hook Awal A",
                        viralScore = 94,
                        rationale = "Pembuka kuat",
                        captionText = "Caption A",
                        hashtags = "#KlipA",
                        startMs = 0L,
                        endMs = 12_000L,
                        aspectRatio = AspectRatioPreset.VERTICAL_9_16,
                        tone = CaptionTone.ENERGETIC,
                        videoFilter = VideoFilterPreset.NONE,
                        brightness = 0f,
                        transitionStyle = VideoTransitionStyle.FADE
                    ),
                    ClipSegment(
                        projectId = pid,
                        title = "Klip B",
                        hookLine = "Hook Awal B",
                        viralScore = 88,
                        rationale = "Klimaks diskusi",
                        captionText = "Caption B",
                        hashtags = "#KlipB",
                        startMs = 15_000L,
                        endMs = 30_000L,
                        aspectRatio = AspectRatioPreset.VERTICAL_9_16,
                        tone = CaptionTone.EDUCATIONAL,
                        videoFilter = VideoFilterPreset.NONE,
                        brightness = 0f,
                        transitionStyle = VideoTransitionStyle.FADE
                    )
                )
            }
            val segments = repo.getSegmentsForProject(projectId)
            segments[0] to segments[1]
        }

        val vm = ClipperViewModel(app)
        vm.loadSegmentForEditor(segA.id)
        waitUntil { vm.editorState.value.segment?.id == segA.id }

        vm.updateEditorFilter(VideoFilterPreset.COOL)
        vm.updateEditorAspectRatio(AspectRatioPreset.SQUARE_1_1)

        vm.loadSegmentForEditor(segB.id)
        waitUntil { vm.editorState.value.segment?.id == segB.id }

        vm.updateEditorTransitionStyle(VideoTransitionStyle.SLIDE)
        vm.updateEditorBrightness(-0.2f)

        assertEquals(4, vm.undoHistoryState.value.undoCount)

        // Undo Klip B brightness
        vm.undoGlobal()
        assertEquals(0f, vm.editorState.value.segment?.brightness ?: -1f, 0.0001f)
        waitUntil { runBlocking { repo.getSegment(segB.id)?.brightness == 0f } }

        // Undo Klip B transition style
        vm.undoGlobal()
        assertEquals(VideoTransitionStyle.FADE, vm.editorState.value.segment?.transitionStyle)
        waitUntil { runBlocking { repo.getSegment(segB.id)?.transitionStyle == VideoTransitionStyle.FADE } }

        // Undo Klip A aspect ratio (even though Klip B is currently loaded in Editor)
        vm.undoGlobal()
        waitUntil { runBlocking { repo.getSegment(segA.id)?.aspectRatio == AspectRatioPreset.VERTICAL_9_16 } }

        // Undo Klip A filter
        vm.undoGlobal()
        waitUntil { runBlocking { repo.getSegment(segA.id)?.videoFilter == VideoFilterPreset.NONE } }

        // Redo Klip A filter and verify persisted state
        vm.redoGlobal()
        waitUntil { runBlocking { repo.getSegment(segA.id)?.videoFilter == VideoFilterPreset.COOL } }
    }

    @Test
    fun `backgroundExportQueue preserves frozen snapshots validates URLs and manages lifecycle`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ClipProjectRepository(AppDatabase.getInstance(app).clipDao())

        val (seg1, seg2) = runBlocking {
            val projectId = repo.saveProjectWithSegments(
                ClipProject(
                    title = "Proyek Render Latar",
                    sourceUri = "content://media/external/video/media/999",
                    durationMs = 60_000L,
                    width = 1920,
                    height = 1080,
                    targetPlatform = TargetPlatform.REELS_TIKTOK,
                    focusTopic = "Produktivitas",
                    hasPlayableStream = true
                )
            ) { pid ->
                listOf(
                    ClipSegment(
                        projectId = pid,
                        title = "Klip Antrean 1",
                        hookLine = "Hook 1",
                        viralScore = 95,
                        rationale = "Top moment",
                        captionText = "Caption 1",
                        hashtags = "#One",
                        startMs = 0L,
                        endMs = 15_000L,
                        aspectRatio = AspectRatioPreset.VERTICAL_9_16
                    ),
                    ClipSegment(
                        projectId = pid,
                        title = "Klip Antrean 2",
                        hookLine = "Hook 2",
                        viralScore = 91,
                        rationale = "Second moment",
                        captionText = "Caption 2",
                        hashtags = "#Two",
                        startMs = 20_000L,
                        endMs = 35_000L,
                        aspectRatio = AspectRatioPreset.PORTRAIT_4_5
                    )
                )
            }
            // Mark project playable after editor load bypasses extractor, or keep explicit sourceUri override
            val segments = repo.getSegmentsForProject(projectId)
            segments[0] to segments[1]
        }

        val vm = ClipperViewModel(app)
        vm.loadSegmentForEditor(seg1.id)
        waitUntil { vm.editorState.value.segment?.id == seg1.id }

        vm.updateEditorFilter(VideoFilterPreset.SEPIA)
        vm.updateEditorExportQuality(ExportQuality.HD_720P)

        vm.enqueueActiveEditorSegmentForBackgroundExport(autoStart = false)
        waitUntil { vm.exportRenderQueue.value.size == 1 }

        // Mutate editor segment after queueing; queued job snapshot must stay isolated
        vm.updateEditorFilter(VideoFilterPreset.GRAYSCALE)
        vm.updateEditorExportQuality(ExportQuality.SD_480P)
        vm.updateEditorHook("Hook Sesudah Antre")

        val queuedJob = vm.exportRenderQueue.value.first()
        assertEquals(ExportJobStatus.QUEUED, queuedJob.status)
        assertEquals(VideoFilterPreset.SEPIA, queuedJob.segmentSnapshot.videoFilter)
        assertEquals(ExportQuality.HD_720P, queuedJob.segmentSnapshot.exportQuality)
        assertEquals("Hook 1", queuedJob.segmentSnapshot.hookLine)

        // Blocked local loopback URL must be rejected by UrlSecurityValidator
        vm.enqueueClipForBackgroundExport(
            segment = seg2,
            sourceUriString = "http://127.0.0.1/private.mp4",
            autoStart = false
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, vm.exportRenderQueue.value.size)

        // Enqueue batch of multiple clips safely
        vm.enqueueMultipleClipsForBackgroundExport(
            segments = listOf(seg1, seg2),
            sourceUriString = "https://example.com/source-video.mp4",
            autoStart = false
        )
        waitUntil { vm.exportRenderQueue.value.size == 3 }

        val job1 = vm.exportRenderQueue.value[0]
        val job2 = vm.exportRenderQueue.value[1]

        // Cancel & Retry job1, Cancel & Clear job2, Remove job1
        vm.cancelExportRenderJob(job1.id)
        assertEquals(ExportJobStatus.CANCELLED, vm.exportRenderQueue.value.first { it.id == job1.id }.status)

        vm.retryExportRenderJob(job1.id, autoStart = false)
        assertEquals(ExportJobStatus.QUEUED, vm.exportRenderQueue.value.first { it.id == job1.id }.status)

        vm.cancelExportRenderJob(job2.id)
        vm.clearCompletedExportRenderJobs()
        assertFalse(vm.exportRenderQueue.value.any { it.id == job2.id })
        assertEquals(2, vm.exportRenderQueue.value.size)

        vm.removeExportRenderJob(job1.id)
        assertFalse(vm.exportRenderQueue.value.any { it.id == job1.id })
        assertEquals(1, vm.exportRenderQueue.value.size)
    }

    @Test
    fun `watermark toggle defaults to off, supports undo redo, and freezes in background export snapshot`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ClipperViewModel(app)

        assertFalse(vm.studioState.value.includeWatermark)
        vm.updateStudioWatermark(true)
        assertTrue(vm.studioState.value.includeWatermark)
        vm.undoGlobal()
        assertFalse(vm.studioState.value.includeWatermark)
        vm.redoGlobal()
        assertTrue(vm.studioState.value.includeWatermark)

        val repo = ClipProjectRepository(AppDatabase.getInstance(app).clipDao())
        val seg = runBlocking {
            val pid = repo.saveProjectWithSegments(
                ClipProject(
                    title = "Proyek Watermark",
                    sourceUri = "content://media/external/video/media/777",
                    durationMs = 20_000L,
                    width = 1080,
                    height = 1920,
                    targetPlatform = TargetPlatform.REELS_TIKTOK,
                    focusTopic = "Testing",
                    hasPlayableStream = true
                )
            ) { projectId ->
                listOf(
                    ClipSegment(
                        projectId = projectId,
                        title = "Klip Watermark",
                        hookLine = "Hook WM",
                        viralScore = 92,
                        rationale = "Test",
                        captionText = "Cap",
                        hashtags = "#Test",
                        startMs = 0L,
                        endMs = 10_000L,
                        aspectRatio = AspectRatioPreset.VERTICAL_9_16
                    )
                )
            }
            repo.getSegmentsForProject(pid).first()
        }

        vm.loadSegmentForEditor(seg.id)
        waitUntil { vm.editorState.value.segment?.id == seg.id }
        assertFalse(vm.editorState.value.segment!!.includeWatermark)

        vm.toggleEditorWatermark(true)
        assertTrue(vm.editorState.value.segment!!.includeWatermark)
        waitUntil { runBlocking { repo.getSegment(seg.id)?.includeWatermark == true } }

        vm.enqueueActiveEditorSegmentForBackgroundExport(autoStart = false)
        waitUntil { vm.exportRenderQueue.value.size == 1 }
        assertTrue(vm.exportRenderQueue.value.first().segmentSnapshot.includeWatermark)

        vm.toggleEditorWatermark(false)
        assertFalse(vm.editorState.value.segment!!.includeWatermark)
        assertTrue(vm.exportRenderQueue.value.first().segmentSnapshot.includeWatermark)

        vm.undoGlobal()
        assertTrue(vm.editorState.value.segment!!.includeWatermark)
    }
}
