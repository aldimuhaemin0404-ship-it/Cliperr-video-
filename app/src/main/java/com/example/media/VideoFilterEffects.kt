package com.example.media

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import com.example.domain.model.VideoFilterPreset
import kotlin.math.abs

@OptIn(UnstableApi::class)
object VideoFilterEffects {

    fun buildEffects(
        preset: VideoFilterPreset,
        brightness: Float
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        when (preset) {
            VideoFilterPreset.NONE -> Unit
            VideoFilterPreset.GRAYSCALE -> {
                effects.add(RgbFilter.createGrayscaleFilter())
            }
            VideoFilterPreset.SEPIA -> {
                effects.add(SepiaRgbMatrix())
            }
            VideoFilterPreset.VIVID -> {
                effects.add(
                    HslAdjustment.Builder()
                        .adjustSaturation(35f)
                        .build()
                )
                effects.add(Contrast(0.16f))
            }
            VideoFilterPreset.WARM -> {
                effects.add(
                    RgbAdjustment.Builder()
                        .setRedScale(1.16f)
                        .setGreenScale(1.03f)
                        .setBlueScale(0.84f)
                        .build()
                )
            }
            VideoFilterPreset.COOL -> {
                effects.add(
                    RgbAdjustment.Builder()
                        .setRedScale(0.84f)
                        .setGreenScale(1.02f)
                        .setBlueScale(1.18f)
                        .build()
                )
            }
        }

        val safeBrightness = brightness.coerceIn(-0.5f, 0.5f)
        if (abs(safeBrightness) >= 0.01f) {
            effects.add(Brightness(safeBrightness))
        }

        return effects
    }

    private class SepiaRgbMatrix : RgbMatrix {
        // Column-major 4x4 RGBA color matrix for classic warm sepia tone.
        private val sepiaMatrix = floatArrayOf(
            0.393f, 0.349f, 0.272f, 0f,
            0.769f, 0.686f, 0.534f, 0f,
            0.189f, 0.168f, 0.131f, 0f,
            0f,     0f,     0f,     1f
        )

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = sepiaMatrix

        override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
    }
}
