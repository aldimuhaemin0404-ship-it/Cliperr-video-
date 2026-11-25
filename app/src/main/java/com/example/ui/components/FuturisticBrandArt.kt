package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FuturisticBlue
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AldiLogoClapperboard(modifier: Modifier = Modifier.size(136.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Cosmic radial halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonPurple.copy(alpha = 0.50f),
                    FuturisticCyan.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.54f
            ),
            radius = w * 0.54f,
            center = Offset(cx, cy)
        )

        // Glowing orbital ring around clapperboard
        rotate(degrees = -18f, pivot = Offset(cx, cy)) {
            drawOval(
                brush = Brush.sweepGradient(
                    colors = listOf(FuturisticCyan, NeonPurple, NeonMagenta, FuturisticCyan)
                ),
                topLeft = Offset(w * 0.08f, h * 0.22f),
                size = Size(w * 0.84f, h * 0.54f),
                style = Stroke(width = w * 0.048f, cap = StrokeCap.Round)
            )
        }

        // Clapperboard main dark body
        val bodyLeft = w * 0.24f
        val bodyTop = h * 0.34f
        val bodyW = w * 0.52f
        val bodyH = h * 0.42f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1A2246), Color(0xFF0A0E1E))
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
        drawRoundRect(
            brush = Brush.linearGradient(listOf(FuturisticCyan, NeonPurple)),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = w * 0.018f)
        )

        // Tilted top clap bar
        rotate(degrees = -10f, pivot = Offset(bodyLeft, bodyTop)) {
            val barTop = bodyTop - h * 0.11f
            val barH = h * 0.10f
            drawRoundRect(
                color = Color(0xFF141B38),
                topLeft = Offset(bodyLeft, barTop),
                size = Size(bodyW, barH),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )
            // White diagonal stripes
            for (i in 0 until 3) {
                val sx = bodyLeft + bodyW * (0.14f + i * 0.28f)
                val stripe = Path().apply {
                    moveTo(sx, barTop + barH * 0.15f)
                    lineTo(sx + bodyW * 0.12f, barTop + barH * 0.15f)
                    lineTo(sx + bodyW * 0.05f, barTop + barH * 0.85f)
                    lineTo(sx - bodyW * 0.07f, barTop + barH * 0.85f)
                    close()
                }
                drawPath(stripe, color = Color.White.copy(alpha = 0.92f))
            }
        }

        // Play triangle in center
        val playCx = cx + w * 0.015f
        val playCy = bodyTop + bodyH * 0.52f
        val playR = w * 0.11f
        val playPath = Path().apply {
            moveTo(playCx - playR * 0.55f, playCy - playR * 0.72f)
            lineTo(playCx + playR * 0.85f, playCy)
            lineTo(playCx - playR * 0.55f, playCy + playR * 0.72f)
            close()
        }
        drawPath(
            path = playPath,
            brush = Brush.linearGradient(listOf(Color.White, FuturisticCyan))
        )
    }
}

@Composable
fun AiRobotMascot(modifier: Modifier = Modifier.size(116.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Ambient glow behind mascot
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    FuturisticCyan.copy(alpha = 0.35f),
                    NeonPurple.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = Offset(cx, h * 0.45f),
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = Offset(cx, h * 0.45f)
        )

        // Antenna
        drawLine(
            color = FuturisticCyan,
            start = Offset(cx, h * 0.16f),
            end = Offset(cx, h * 0.07f),
            strokeWidth = w * 0.03f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = FuturisticCyan,
            radius = w * 0.045f,
            center = Offset(cx, h * 0.06f)
        )

        // Side ear pods
        drawRoundRect(
            color = FuturisticBlue,
            topLeft = Offset(w * 0.14f, h * 0.28f),
            size = Size(w * 0.10f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        drawRoundRect(
            color = FuturisticBlue,
            topLeft = Offset(w * 0.76f, h * 0.28f),
            size = Size(w * 0.10f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Head shell
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF3F7FF), Color(0xFFC5D4F6))),
            topLeft = Offset(w * 0.20f, h * 0.15f),
            size = Size(w * 0.60f, h * 0.40f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
        )

        // Dark glossy visor
        drawRoundRect(
            color = Color(0xFF0A1026),
            topLeft = Offset(w * 0.26f, h * 0.22f),
            size = Size(w * 0.48f, h * 0.26f),
            cornerRadius = CornerRadius(w * 0.11f, w * 0.11f)
        )

        // Glowing cyan eyes
        drawCircle(
            color = FuturisticCyan,
            radius = w * 0.055f,
            center = Offset(w * 0.39f, h * 0.32f)
        )
        drawCircle(
            color = FuturisticCyan,
            radius = w * 0.055f,
            center = Offset(w * 0.61f, h * 0.32f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.02f,
            center = Offset(w * 0.375f, h * 0.305f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.02f,
            center = Offset(w * 0.595f, h * 0.305f)
        )

        // Smile arc
        drawArc(
            color = FuturisticCyan,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.42f, h * 0.37f),
            size = Size(w * 0.16f, h * 0.07f),
            style = Stroke(width = w * 0.024f, cap = StrokeCap.Round)
        )

        // Body torso
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFEAF0FF), Color(0xFFB3C5EE))),
            topLeft = Offset(w * 0.27f, h * 0.57f),
            size = Size(w * 0.46f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
        )

        // Chest play orb
        drawCircle(
            color = FuturisticBlue,
            radius = w * 0.105f,
            center = Offset(cx, h * 0.74f)
        )
        drawCircle(
            color = FuturisticCyan,
            radius = w * 0.105f,
            center = Offset(cx, h * 0.74f),
            style = Stroke(width = w * 0.02f)
        )
        val chestPlay = Path().apply {
            moveTo(cx - w * 0.03f, h * 0.695f)
            lineTo(cx + w * 0.055f, h * 0.74f)
            lineTo(cx - w * 0.03f, h * 0.785f)
            close()
        }
        drawPath(chestPlay, color = Color.White)

        // Left waving/thumbs-up arm
        drawRoundRect(
            color = Color(0xFFD6E2FC),
            topLeft = Offset(w * 0.11f, h * 0.56f),
            size = Size(w * 0.13f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
        drawCircle(
            color = FuturisticCyan,
            radius = w * 0.05f,
            center = Offset(w * 0.17f, h * 0.53f)
        )
    }
}

@Composable
fun NeonAudioWaveform(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier) {
        val barCount = 28
        val spacing = size.width / barCount
        val barWidth = (spacing * 0.56f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val norm = i.toFloat() / (barCount - 1)
            val envelope = sin(norm * PI).toFloat().coerceAtLeast(0.18f)
            val osc = (0.5f + 0.5f * sin(phase + i * 0.55f)).coerceIn(0.2f, 1f)
            val barH = size.height * 0.88f * envelope * osc
            val x = i * spacing + (spacing - barWidth) / 2f

            val color = when {
                norm < 0.35f -> NeonPurple
                norm < 0.70f -> FuturisticCyan
                else -> NeonMagenta
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
