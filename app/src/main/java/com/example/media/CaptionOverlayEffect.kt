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
import androidx.media3.effect.TextureOverlay
import com.example.domain.model.ClipSegment
import com.example.domain.model.HookOverlayStyle
import kotlin.math.max
import kotlin.math.min

@OptIn(UnstableApi::class)
object CaptionOverlayEffects {

    fun buildOverlayEffect(segment: ClipSegment): OverlayEffect? {
        val overlays = mutableListOf<TextureOverlay>()

        if (segment.showIntroHook && segment.hookLine.isNotBlank()) {
            overlays += IntroHookBitmapOverlay(segment)
        }

        val hasSubtitlesContent = segment.subtitleCues.any { it.text.isNotBlank() } ||
            (!segment.showIntroHook && segment.hookLine.isNotBlank())
        if (segment.showSubtitles && hasSubtitlesContent) {
            overlays += CaptionBitmapOverlay(segment)
        }

        if (segment.includeWatermark) {
            overlays += WatermarkBitmapOverlay()
        }

        if (overlays.isEmpty()) return null
        return OverlayEffect(overlays)
    }
}

@OptIn(UnstableApi::class)
private class IntroHookBitmapOverlay(
    private val segment: ClipSegment
) : BitmapOverlay() {

    private var videoSize: Size = Size(1080, 1920)
    private var firstPtsUs: Long? = null
    private var cachedBitmap: Bitmap? = null

    private val emptyBitmap: Bitmap by lazy {
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
        }
    }

    private val overlaySettings: StaticOverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f, 0.56f)
        .setOverlayFrameAnchor(0f, 0f)
        .build()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        if (videoSize.width > 0 && videoSize.height > 0) {
            this.videoSize = videoSize
        }
        cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedBitmap = null
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val baseUs = firstPtsUs ?: presentationTimeUs.also { firstPtsUs = it }
        val relativeMs = ((presentationTimeUs - baseUs) / 1000L).coerceAtLeast(0L)
        if (relativeMs > segment.hookDurationMs) {
            return emptyBitmap
        }
        val cleanText = segment.hookLine.trim()
        if (cleanText.isEmpty()) {
            return emptyBitmap
        }
        val existing = cachedBitmap
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        return renderHookBitmap(
            text = cleanText,
            style = segment.hookOverlayStyle,
            frameWidth = videoSize.width,
            frameHeight = videoSize.height
        ).also { cachedBitmap = it }
    }

    override fun release() {
        super.release()
        cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedBitmap = null
    }

    private fun renderHookBitmap(
        text: String,
        style: HookOverlayStyle,
        frameWidth: Int,
        frameHeight: Int
    ): Bitmap {
        val safeW = frameWidth.coerceAtLeast(360)
        val safeH = frameHeight.coerceAtLeast(360)
        val minDim = min(safeW, safeH).toFloat()

        val textSizePx = (minDim * 0.048f).coerceIn(24f, 60f)
        val badgeTextSizePx = (textSizePx * 0.56f).coerceIn(14f, 30f)
        val hPadding = (textSizePx * 0.80f).toInt().coerceAtLeast(18)
        val vPadding = (textSizePx * 0.52f).toInt().coerceAtLeast(12)
        val maxContentWidth = ((safeW * 0.86f).toInt() - hPadding * 2).coerceAtLeast(180)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(5f, 0f, 2f, Color.argb(190, 0, 0, 0))
        }

        val badgeText = "HOOK 0-${(segment.hookDurationMs / 1000L).coerceAtLeast(1L)}S"
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (style) {
                HookOverlayStyle.NEON_BADGE -> Color.rgb(0, 229, 255)
                HookOverlayStyle.CYBER_BANNER -> Color.rgb(255, 214, 102)
                HookOverlayStyle.MINIMAL_BOLD -> Color.rgb(244, 114, 182)
            }
            textSize = badgeTextSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }

        val layout = createCenteredStaticLayout(text, textPaint, maxContentWidth)

        var maxLineWidth = badgePaint.measureText(badgeText)
        for (i in 0 until layout.lineCount) {
            maxLineWidth = max(maxLineWidth, layout.getLineWidth(i))
        }

        val badgeHeight = (badgeTextSizePx * 1.35f).toInt()
        val boxWidth = (maxLineWidth.toInt() + hPadding * 2).coerceIn(160, (safeW * 0.90f).toInt())
        val boxHeight = (layout.height + badgeHeight + vPadding * 2 + 8).coerceAtMost((safeH * 0.40f).toInt())

        val bitmap = Bitmap.createBitmap(boxWidth, boxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(3f, 3f, boxWidth - 3f, boxHeight - 3f)
        val cornerRadius = when (style) {
            HookOverlayStyle.NEON_BADGE -> (textSizePx * 0.56f).coerceIn(14f, 32f)
            HookOverlayStyle.CYBER_BANNER -> (textSizePx * 0.28f).coerceIn(8f, 18f)
            HookOverlayStyle.MINIMAL_BOLD -> (textSizePx * 0.38f).coerceIn(10f, 22f)
        }

        val bgColor = when (style) {
            HookOverlayStyle.NEON_BADGE -> Color.argb(230, 24, 12, 54)
            HookOverlayStyle.CYBER_BANNER -> Color.argb(232, 8, 28, 56)
            HookOverlayStyle.MINIMAL_BOLD -> Color.argb(218, 12, 14, 22)
        }
        val borderColor = when (style) {
            HookOverlayStyle.NEON_BADGE -> Color.argb(235, 168, 85, 247)
            HookOverlayStyle.CYBER_BANNER -> Color.argb(240, 0, 229, 255)
            HookOverlayStyle.MINIMAL_BOLD -> Color.argb(210, 236, 72, 153)
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            color = bgColor
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = (textSizePx * 0.08f).coerceIn(2.5f, 5f)
            color = borderColor
        }

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Draw tiny top tag + hook body text centered
        val badgeW = badgePaint.measureText(badgeText)
        canvas.drawText(
            badgeText,
            (boxWidth - badgeW) / 2f,
            vPadding + badgeTextSizePx,
            badgePaint
        )

        canvas.save()
        val textOffsetX = (boxWidth - layout.width) / 2f
        val textOffsetY = (vPadding + badgeHeight + 6).toFloat()
        canvas.translate(textOffsetX, textOffsetY)
        layout.draw(canvas)
        canvas.restore()

        return bitmap
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
        if (segment.showIntroHook) return null
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

        val layout = createCenteredStaticLayout(text, textPaint, maxContentWidth)

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

@OptIn(UnstableApi::class)
private class WatermarkBitmapOverlay : BitmapOverlay() {

    private var videoSize: Size = Size(1080, 1920)
    private var cachedBitmap: Bitmap? = null

    private val overlaySettings: StaticOverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0.88f, 0.88f)
        .setOverlayFrameAnchor(1f, 1f)
        .build()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        if (videoSize.width > 0 && videoSize.height > 0) {
            this.videoSize = videoSize
        }
        cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedBitmap = null
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val existing = cachedBitmap
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        return renderWatermarkBitmap(videoSize.width, videoSize.height).also {
            cachedBitmap = it
        }
    }

    override fun release() {
        super.release()
        cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedBitmap = null
    }

    private fun renderWatermarkBitmap(frameWidth: Int, frameHeight: Int): Bitmap {
        val safeW = frameWidth.coerceAtLeast(360)
        val safeH = frameHeight.coerceAtLeast(360)
        val minDim = min(safeW, safeH).toFloat()

        val label = "Aldi AI Clipper"
        val textSizePx = (minDim * 0.030f).coerceIn(16f, 34f)
        val hPadding = (textSizePx * 0.85f).toInt().coerceAtLeast(14)
        val vPadding = (textSizePx * 0.45f).toInt().coerceAtLeast(8)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(3f, 0f, 1.5f, Color.argb(180, 0, 0, 0))
        }

        val textWidth = textPaint.measureText(label)
        val fm = textPaint.fontMetrics
        val textHeight = (fm.descent - fm.ascent)

        val pillWidth = (textWidth.toInt() + hPadding * 2).coerceIn(110, (safeW * 0.48f).toInt())
        val pillHeight = (textHeight.toInt() + vPadding * 2).coerceAtLeast(28)

        val bitmap = Bitmap.createBitmap(pillWidth, pillHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(2f, 2f, pillWidth - 2f, pillHeight - 2f)
        val cornerRadius = pillHeight / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(196, 11, 14, 20)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (textSizePx * 0.08f).coerceIn(1.5f, 3.5f)
            color = Color.argb(200, 6, 182, 212)
        }

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val textX = (pillWidth - textWidth) / 2f
        val textY = (pillHeight - (fm.descent + fm.ascent)) / 2f
        canvas.drawText(label, textX, textY, textPaint)

        return bitmap
    }
}

private fun createCenteredStaticLayout(
    text: String,
    textPaint: TextPaint,
    maxContentWidth: Int
): StaticLayout {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
}
