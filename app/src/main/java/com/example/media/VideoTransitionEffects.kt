package com.example.media

import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix
import com.example.domain.model.VideoTransitionStyle
import kotlin.math.PI
import kotlin.math.cos

@OptIn(UnstableApi::class)
object VideoTransitionEffects {

    fun buildEffects(
        style: VideoTransitionStyle,
        transitionDurationMs: Long,
        clipDurationMs: Long
    ): List<Effect> {
        if (style == VideoTransitionStyle.NONE || transitionDurationMs <= 0L || clipDurationMs < 1_000L) {
            return emptyList()
        }
        val safeTransMs = transitionDurationMs.coerceIn(250L, (clipDurationMs / 2L).coerceAtLeast(250L))
        return when (style) {
            VideoTransitionStyle.NONE -> emptyList()
            VideoTransitionStyle.FADE -> listOf(
                TransitionFadeRgbMatrix(
                    clipDurationMs = clipDurationMs,
                    transitionMs = safeTransMs,
                    floor = 0f
                )
            )
            VideoTransitionStyle.SLIDE -> listOf(
                TransitionSlideMatrix(
                    clipDurationMs = clipDurationMs,
                    transitionMs = safeTransMs
                ),
                TransitionFadeRgbMatrix(
                    clipDurationMs = clipDurationMs,
                    transitionMs = safeTransMs,
                    floor = 0.28f
                )
            )
            VideoTransitionStyle.DISSOLVE -> listOf(
                TransitionDissolveZoomMatrix(
                    clipDurationMs = clipDurationMs,
                    transitionMs = safeTransMs
                ),
                TransitionDissolveRgbMatrix(
                    clipDurationMs = clipDurationMs,
                    transitionMs = safeTransMs
                )
            )
        }
    }

    private fun computeEnvelope(
        presentationTimeUs: Long,
        firstPtsUs: Long,
        clipDurationMs: Long,
        transitionMs: Long
    ): Float {
        val relMs = ((presentationTimeUs - firstPtsUs) / 1000L).coerceAtLeast(0L)
        val transF = transitionMs.toFloat().coerceAtLeast(1f)
        val enter = (relMs / transF).coerceIn(0f, 1f)
        val remainingMs = (clipDurationMs - relMs).coerceAtLeast(0L)
        val exit = (remainingMs / transF).coerceIn(0f, 1f)
        val raw = minOf(enter, exit)
        // Smoothstep easing for natural visual transition
        return raw * raw * (3f - 2f * raw)
    }

    private class TransitionFadeRgbMatrix(
        private val clipDurationMs: Long,
        private val transitionMs: Long,
        private val floor: Float
    ) : RgbMatrix {
        private var firstPtsUs: Long? = null

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val baseUs = firstPtsUs ?: presentationTimeUs.also { firstPtsUs = it }
            val p = computeEnvelope(presentationTimeUs, baseUs, clipDurationMs, transitionMs)
            val s = (floor + (1f - floor) * p).coerceIn(0f, 1f)
            return floatArrayOf(
                s, 0f, 0f, 0f,
                0f, s, 0f, 0f,
                0f, 0f, s, 0f,
                0f, 0f, 0f, 1f
            )
        }

        override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
    }

    private class TransitionSlideMatrix(
        private val clipDurationMs: Long,
        private val transitionMs: Long
    ) : MatrixTransformation {
        private var firstPtsUs: Long? = null

        override fun getMatrix(presentationTimeUs: Long): Matrix {
            val baseUs = firstPtsUs ?: presentationTimeUs.also { firstPtsUs = it }
            val relMs = ((presentationTimeUs - baseUs) / 1000L).coerceAtLeast(0L)
            val transF = transitionMs.toFloat().coerceAtLeast(1f)
            val enterT = (relMs / transF).coerceIn(0f, 1f)
            val exitT = ((clipDurationMs - relMs).coerceAtLeast(0L) / transF).coerceIn(0f, 1f)

            val translateX = when {
                enterT < 1f -> {
                    val easeOut = 1f - (1f - enterT) * (1f - enterT)
                    (1f - easeOut) * 1.4f
                }
                exitT < 1f -> {
                    val easeOut = 1f - (1f - exitT) * (1f - exitT)
                    -(1f - easeOut) * 1.4f
                }
                else -> 0f
            }

            return Matrix().apply {
                setTranslate(translateX, 0f)
            }
        }
    }

    private class TransitionDissolveZoomMatrix(
        private val clipDurationMs: Long,
        private val transitionMs: Long
    ) : MatrixTransformation {
        private var firstPtsUs: Long? = null

        override fun getMatrix(presentationTimeUs: Long): Matrix {
            val baseUs = firstPtsUs ?: presentationTimeUs.also { firstPtsUs = it }
            val p = computeEnvelope(presentationTimeUs, baseUs, clipDurationMs, transitionMs)
            val zoom = 1f + (1f - p) * 0.08f
            return Matrix().apply {
                setScale(zoom, zoom)
            }
        }
    }

    private class TransitionDissolveRgbMatrix(
        private val clipDurationMs: Long,
        private val transitionMs: Long
    ) : RgbMatrix {
        private var firstPtsUs: Long? = null

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val baseUs = firstPtsUs ?: presentationTimeUs.also { firstPtsUs = it }
            val p = computeEnvelope(presentationTimeUs, baseUs, clipDurationMs, transitionMs)
            // Soft cosine cross-dissolve luminance curve
            val s = (0.5 - 0.5 * cos(p * PI)).toFloat().coerceIn(0f, 1f)
            val blueLift = (1f - p) * 0.06f
            return floatArrayOf(
                s, 0f, 0f, 0f,
                0f, s, 0f, 0f,
                0f, 0f, (s + blueLift).coerceAtMost(1f), 0f,
                0f, 0f, 0f, 1f
            )
        }

        override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
    }
}
