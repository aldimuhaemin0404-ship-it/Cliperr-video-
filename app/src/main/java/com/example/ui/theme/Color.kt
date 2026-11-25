package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Cosmic Dark + Neon Purple + Futuristic Blue Palette (Aldi AI Clipper)
val CosmicBackground = Color(0xFF070914)
val CosmicSurface = Color(0xFF0E1328)
val CosmicSurfaceElevated = Color(0xFF151C38)
val CosmicSurfaceHighest = Color(0xFF1E274B)
val CosmicOutline = Color(0xFF313B6E)
val CosmicOutlineVariant = Color(0xFF20284D)

val NeonPurple = Color(0xFF9B4DFF)
val NeonPurpleDeep = Color(0xFF6D28D9)
val NeonPurpleContainer = Color(0xFF28134D)
val OnNeonPurpleContainer = Color(0xFFEBD6FF)
val NeonMagenta = Color(0xFFEC4899)

val FuturisticCyan = Color(0xFF00D4FF)
val FuturisticBlue = Color(0xFF2563EB)
val FuturisticCyanContainer = Color(0xFF09314B)
val OnFuturisticCyanContainer = Color(0xFFBDF4FF)

val StudioAmber = Color(0xFFFFB703)
val StudioAmberContainer = Color(0xFF3E2C00)
val OnStudioAmberContainer = Color(0xFFFFE08C)

val StudioEmerald = Color(0xFF22E58A)
val StudioEmeraldContainer = Color(0xFF0B3B28)

// Compatibility aliases for existing components
val ObsidianBackground = CosmicBackground
val ObsidianSurface = CosmicSurface
val ObsidianSurfaceElevated = CosmicSurfaceElevated
val ObsidianSurfaceHighest = CosmicSurfaceHighest
val ObsidianOutline = CosmicOutline
val ObsidianOutlineVariant = CosmicOutlineVariant
val ElectricCyan = FuturisticCyan
val ElectricCyanDark = FuturisticBlue
val ElectricCyanContainer = FuturisticCyanContainer
val OnElectricCyanContainer = OnFuturisticCyanContainer
val ViralCoral = NeonMagenta
val ViralCoralContainer = Color(0xFF4C1138)
val OnViralCoralContainer = Color(0xFFFFD8EE)

val TextPrimaryDark = Color(0xFFF4F7FF)
val TextSecondaryDark = Color(0xFFA0ADD0)

// Light Studio Fallback Palette
val StudioLightBackground = Color(0xFFF6F8FC)
val StudioLightSurface = Color(0xFFFFFFFF)
val StudioLightSurfaceVariant = Color(0xFFE9EEF7)
val StudioLightPrimary = Color(0xFF6D28D9)
val StudioLightPrimaryContainer = Color(0xFFEDE2FF)
val StudioLightSecondary = Color(0xFF0284C7)
val StudioLightSecondaryContainer = Color(0xFFD6F3FF)
val StudioLightOutline = Color(0xFFC4CEDF)
val TextPrimaryLight = Color(0xFF101520)
val TextSecondaryLight = Color(0xFF525F75)

// Signature Neon Gradients
val BrandNeonGradient = Brush.horizontalGradient(
    colors = listOf(FuturisticCyan, NeonPurple, NeonMagenta)
)

val ActionButtonGradient = Brush.horizontalGradient(
    colors = listOf(NeonPurple, FuturisticBlue)
)

val BannerHighlightGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF4F46E5), Color(0xFF9333EA), Color(0xFFEC4899))
)
