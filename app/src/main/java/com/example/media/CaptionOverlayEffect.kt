package com.example.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import com.example.domain.model.ClipSegment
import kotlin.math.max
import kotlin.math.min

@OptIn(UnstableApi::class)
object CaptionOverlayEffects {

    fun buildOverlayEffect(segment: ClipSegment): OverlayEffect? {
        if (!segment.showSubtitles) return null
        val hasContent = segment.subtitleCues.any { it.text.isNotBlank() } || segment.hookLine.isNotBlank()
        if (!hasContent) return null
        return OverlayEffect(listOf(CaptionBitmapOverlay(segment)))
    }
}

@OptIn(UnstableApi::class)
private class CaptionBitmapOverlay(
    private val segment: ClipSegment
) : BitmapOverlay() {

    private var videoSize: Size = Size(1080, 1920)
    private var firstPtsUs: Long? = null
    private var cachedKey: String? = null
    private var cachedBitmap: Bitmap? = null

    private val emptyBitmap: Bitmap by lazy {
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
        }
    }

    private val overlaySettings: StaticOverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, -0.78f)
        .setOverlayFrameAnchor(0f, -1f)
        .build()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        if (videoSize.width > 0 && videoSize.height > 0) {
            this.videoSize = videoSize
        }
        cachedKey = null
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val baseUs = firstPtsUs ?: presentationTimeUs.also { firstPtsUs = it }
        val relativeMs = ((presentationTimeUs - baseUs) / 1000L).coerceAtLeast(0L)

        val activeText = resolveCaptionText(relativeMs)?.trim()
        if (activeText.isNullOrEmpty()) {
            return emptyBitmap
        }

        val currentBitmap = cachedBitmap
        if (activeText == cachedKey && currentBitmap != null && !currentBitmap.isRecycled) {
            return currentBitmap
        }

        val rendered = renderCaptionBitmap(activeText, videoSize.width, videoSize.height)
        cachedBitmap?.let { previous ->
            if (!previous.isRecycled) previous.recycle()
        }
        cachedKey = activeText
        cachedBitmap = rendered
        return rendered
    }

    override fun release() {
        super.release()
        cachedKey = null
        cachedBitmap?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        cachedBitmap = null
    }

    private fun resolveCaptionText(relativeMs: Long): String? {
        val cues = segment.subtitleCues
        if (cues.isNotEmpty()) {
            return cues.firstOrNull { relativeMs in it.startMs..it.endMs }?.text
                ?: cues.lastOrNull { it.startMs <= relativeMs }?.text
                ?: cues.firstOrNull()?.text
        }
        return segment.hookLine.takeIf { it.isNotBlank() }
    }

    private fun renderCaptionBitmap(
        text: String,
        frameWidth: Int,
        frameHeight: Int
    ): Bitmap {
        val safeW = frameWidth.coerceAtLeast(360)
        val safeH = frameHeight.coerceAtLeast(360)
        val minDim = min(safeW, safeH).toFloat()

        val textSizePx = (minDim * 0.044f).coerceIn(22f, 56f)
        val hPadding = (textSizePx * 0.72f).toInt().coerceAtLeast(16)
        val vPadding = (textSizePx * 0.45f).toInt().coerceAtLeast(10)
        val maxContentWidth = ((safeW * 0.86f).toInt() - hPadding * 2).coerceAtLeast(180)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(4f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }

        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, maxContentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                textPaint,
                maxContentWidth,
                Layout.Alignment.ALIGN_CENTER,
                1.15f,
                0f,
                false
            )
        }

        var maxLineWidth = 0f
        for (i in 0 until layout.lineCount) {
            maxLineWidth = max(maxLineWidth, layout.getLineWidth(i))
        }

        val pillWidth = (maxLineWidth.toInt() + hPadding * 2).coerceIn(120, (safeW * 0.90f).toInt())
        val pillHeight = (layout.height + vPadding * 2).coerceAtMost((safeH * 0.35f).toInt())

        val bitmap = Bitmap.createBitmap(pillWidth, pillHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(2f, 2f, pillWidth - 2f, pillHeight - 2f)
        val cornerRadius = (textSizePx * 0.48f).coerceIn(12f, 28f)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(210, 11, 14, 20)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (textSizePx * 0.06f).coerceIn(2f, 4f)
            color = Color.argb(190, 0, 229, 255)
        }

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        canvas.save()
        val textOffsetX = (pillWidth - layout.width) / 2f
        val textOffsetY = (pillHeight - layout.height) / 2f
        canvas.translate(textOffsetX, textOffsetY)
        layout.draw(canvas)
        canvas.restore()

        return bitmap
    }
}
