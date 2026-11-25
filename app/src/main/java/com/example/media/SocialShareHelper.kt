package com.example.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportedClip
import com.example.ui.theme.FuturisticBlue
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.StudioEmerald

enum class SharePlatformTarget(
    val displayName: String,
    val shortLabel: String,
    val candidatePackages: List<String>,
    val accentColor: Color
) {
    TIKTOK(
        displayName = "TikTok",
        shortLabel = "TikTok",
        candidatePackages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        accentColor = FuturisticCyan
    ),
    INSTAGRAM(
        displayName = "Instagram Reels",
        shortLabel = "Instagram",
        candidatePackages = listOf("com.instagram.android"),
        accentColor = NeonMagenta
    ),
    YOUTUBE(
        displayName = "YouTube Shorts",
        shortLabel = "YouTube",
        candidatePackages = listOf("com.google.android.youtube"),
        accentColor = Color(0xFFFF5252)
    ),
    WHATSAPP(
        displayName = "WhatsApp",
        shortLabel = "WhatsApp",
        candidatePackages = listOf("com.whatsapp"),
        accentColor = StudioEmerald
    ),
    FACEBOOK(
        displayName = "Facebook",
        shortLabel = "Facebook",
        candidatePackages = listOf("com.facebook.katana"),
        accentColor = FuturisticBlue
    ),
    X_TWITTER(
        displayName = "X / Twitter",
        shortLabel = "X",
        candidatePackages = listOf("com.twitter.android"),
        accentColor = Color(0xFFE2E8F0)
    )
}

sealed interface ShareLaunchResult {
    data class LaunchedDirect(val platform: SharePlatformTarget) : ShareLaunchResult
    data class OpenedSystemChooser(val fallbackFrom: SharePlatformTarget?) : ShareLaunchResult
    data object Unavailable : ShareLaunchResult
}

object SocialShareHelper {

    fun buildDefaultCaption(clip: ExportedClip, segment: ClipSegment?): String {
        if (segment == null) {
            return "${clip.title}\n\n#AIClipper #Shorts #Reels #TikTok"
        }
        return buildList {
            if (segment.hookLine.isNotBlank()) add(segment.hookLine.trim())
            if (segment.captionText.isNotBlank()) add(segment.captionText.trim())
            if (segment.hashtags.isNotBlank()) add(segment.hashtags.trim())
        }.joinToString("\n\n").ifBlank {
            "${clip.title}\n\n#AIClipper #Shorts"
        }
    }

    fun findInstalledPackage(context: Context, target: SharePlatformTarget): String? {
        val pm = context.packageManager
        return target.candidatePackages.firstOrNull { pkg ->
            val probe = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                setPackage(pkg)
            }
            resolveIntent(pm, probe)
        }
    }

    fun buildShareVideoIntent(
        context: Context,
        shareUri: Uri,
        title: String,
        captionText: String?,
        targetPackage: String? = null
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            if (!captionText.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, captionText)
            }
            clipData = ClipData.newUri(context.contentResolver, title, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
    }

    fun launchDirectPlatformShare(
        context: Context,
        shareUri: Uri,
        title: String,
        captionText: String?,
        platform: SharePlatformTarget
    ): ShareLaunchResult {
        val installedPkg = findInstalledPackage(context, platform)
        if (installedPkg != null) {
            val directIntent = buildShareVideoIntent(
                context = context,
                shareUri = shareUri,
                title = title,
                captionText = captionText,
                targetPackage = installedPkg
            )
            val launched = runCatching {
                context.startActivity(directIntent)
                true
            }.getOrDefault(false)
            if (launched) {
                return ShareLaunchResult.LaunchedDirect(platform)
            }
        }
        return launchSystemShareSheet(
            context = context,
            shareUri = shareUri,
            title = title,
            captionText = captionText,
            fallbackFrom = platform
        )
    }

    fun launchSystemShareSheet(
        context: Context,
        shareUri: Uri,
        title: String,
        captionText: String?,
        fallbackFrom: SharePlatformTarget? = null
    ): ShareLaunchResult {
        val baseIntent = buildShareVideoIntent(
            context = context,
            shareUri = shareUri,
            title = title,
            captionText = captionText,
            targetPackage = null
        )
        val chooserTitle = if (fallbackFrom != null) {
            "Bagikan ke ${fallbackFrom.displayName} / Aplikasi Lain"
        } else {
            "Bagikan Klip ke Media Sosial"
        }
        val chooser = Intent.createChooser(baseIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val opened = runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)

        return if (opened) {
            ShareLaunchResult.OpenedSystemChooser(fallbackFrom)
        } else {
            ShareLaunchResult.Unavailable
        }
    }

    private fun resolveIntent(pm: PackageManager, intent: Intent): Boolean {
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return matches.isNotEmpty()
    }
}
