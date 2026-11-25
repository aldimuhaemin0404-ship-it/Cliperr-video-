package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportedClip
import com.example.media.SharePlatformTarget
import com.example.media.SocialShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SocialShareHelperTest {

    private val sampleClip = ExportedClip(
        id = 1L,
        projectId = 10L,
        segmentId = 5L,
        title = "Momen Terbaik 2026",
        filePath = "/tmp/test_clip.mp4",
        durationMs = 15_000L,
        fileSizeBytes = 2_400_000L,
        aspectRatio = AspectRatioPreset.VERTICAL_9_16
    )

    @Test
    fun `buildDefaultCaption combines hook caption and hashtags`() {
        val segment = ClipSegment(
            id = 5L,
            projectId = 10L,
            title = "Momen Terbaik",
            hookLine = "Jangan lewatkan momen ini!",
            viralScore = 98,
            rationale = "Puncak energi",
            captionText = "Aksi terbaik hari ini siap masuk FYP.",
            hashtags = "#AIClipper #FYP #Shorts",
            startMs = 0L,
            endMs = 15_000L,
            aspectRatio = AspectRatioPreset.VERTICAL_9_16
        )

        val caption = SocialShareHelper.buildDefaultCaption(sampleClip, segment)
        assertTrue(caption.contains("Jangan lewatkan momen ini!"))
        assertTrue(caption.contains("Aksi terbaik hari ini siap masuk FYP."))
        assertTrue(caption.contains("#AIClipper #FYP #Shorts"))
    }

    @Test
    fun `buildShareVideoIntent attaches stream uri clipData permissions and caption`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shareUri = Uri.parse("content://com.aistudio.aiclipper.vkxmqz.fileprovider/exported_clips/test_clip.mp4")

        val intent = SocialShareHelper.buildShareVideoIntent(
            context = context,
            shareUri = shareUri,
            title = sampleClip.title,
            captionText = "Klip viral #Shorts",
            targetPackage = "com.zhiliaoapp.musically"
        )

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("video/mp4", intent.type)
        assertEquals(shareUri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("Klip viral #Shorts", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("com.zhiliaoapp.musically", intent.`package`)
        assertNotNull(intent.clipData)
        assertEquals(shareUri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun `all social share platform targets define candidate packages`() {
        SharePlatformTarget.entries.forEach { target ->
            assertTrue(target.candidatePackages.isNotEmpty())
        }
    }
}
