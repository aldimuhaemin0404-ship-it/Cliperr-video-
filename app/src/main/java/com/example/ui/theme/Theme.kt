package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkStudioColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    primaryContainer = NeonPurpleContainer,
    onPrimaryContainer = OnNeonPurpleContainer,
    secondary = FuturisticCyan,
    onSecondary = Color(0xFF001F2A),
    secondaryContainer = FuturisticCyanContainer,
    onSecondaryContainer = OnFuturisticCyanContainer,
    tertiary = StudioAmber,
    onTertiary = Color(0xFF241A00),
    tertiaryContainer = StudioAmberContainer,
    onTertiaryContainer = OnStudioAmberContainer,
    background = CosmicBackground,
    onBackground = TextPrimaryDark,
    surface = CosmicSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CosmicSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = CosmicBackground,
    surfaceContainerLow = CosmicSurface,
    surfaceContainer = CosmicSurfaceElevated,
    surfaceContainerHigh = CosmicSurfaceHighest,
    outline = CosmicOutline,
    outlineVariant = CosmicOutlineVariant
)

private val LightStudioColorScheme = lightColorScheme(
    primary = StudioLightPrimary,
    onPrimary = Color.White,
    primaryContainer = StudioLightPrimaryContainer,
    onPrimaryContainer = Color(0xFF001F28),
    secondary = StudioLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = StudioLightSecondaryContainer,
    onSecondaryContainer = Color(0xFF400010),
    tertiary = Color(0xFF8C5D00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF2D1C00),
    background = StudioLightBackground,
    onBackground = TextPrimaryLight,
    surface = StudioLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = StudioLightOutline
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AiClipperTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkStudioColorScheme else LightStudioColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
