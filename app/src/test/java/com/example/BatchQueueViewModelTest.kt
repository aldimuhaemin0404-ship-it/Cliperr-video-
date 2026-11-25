package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.BatchItemStatus
import com.example.domain.model.TargetPlatform
import com.example.domain.model.VideoFilterPreset
import com.example.ui.state.ClipperViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatchQueueViewModelTest {

    @Test
    fun `enqueueGalleryVideos and enqueueSocialLinks populate batch queue with config`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ClipperViewModel(app)

        vm.updateBatchPlatform(TargetPlatform.YOUTUBE_SHORTS)
        vm.updateBatchFilter(VideoFilterPreset.VIVID)
        vm.updateBatchAutoExport(false)

        vm.enqueueGalleryVideos(
            listOf(
                Uri.parse("content://media/external/video/media/101"),
                Uri.parse("content://media/external/video/media/102")
            )
        )
        vm.enqueueSocialLinks(
            "https://youtu.be/abc123\nhttps://vt.tiktok.com/xyz789"
        )

        val queue = vm.batchQueue.value
        assertEquals(4, queue.size)
        assertTrue(queue.all { it.status == BatchItemStatus.QUEUED })
        assertTrue(queue.all { it.targetPlatform == TargetPlatform.YOUTUBE_SHORTS })
        assertTrue(queue.all { it.videoFilter == VideoFilterPreset.VIVID })
        assertFalse(queue.first().autoExportTopClip)
        assertFalse(queue[0].isSocialLink)
        assertTrue(queue[2].isSocialLink)
    }

    @Test
    fun `removeBatchItem removes queued item cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ClipperViewModel(app)

        vm.enqueueSocialLinks("https://youtu.be/one\nhttps://youtu.be/two")
        val initial = vm.batchQueue.value
        assertEquals(2, initial.size)

        vm.removeBatchItem(initial.first().id)
        val remaining = vm.batchQueue.value
        assertEquals(1, remaining.size)
        assertEquals(initial.last().id, remaining.first().id)
    }
}
