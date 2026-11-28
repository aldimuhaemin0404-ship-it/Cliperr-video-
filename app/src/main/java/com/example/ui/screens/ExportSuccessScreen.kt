package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ExportedClip
import com.example.media.ShareLaunchResult
import com.example.media.SharePlatformTarget
import com.example.media.SocialShareHelper
import com.example.ui.components.VideoClipPlayerPreview
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticBlue
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioEmerald
import java.io.File

@Composable
fun ExportSuccessScreen(
    exportedClip: ExportedClip?,
    shareCaption: String?,
    onGetShareUri: (ExportedClip) -> Uri?,
    onOpenShareSheet: (ExportedClip) -> Unit,
    onShareFeedback: (String) -> Unit,
    onEditAgain: () -> Unit,
    onOpenExportsHistory: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E0826), CosmicBackground, CosmicBackground)
                )
            )
            .testTag("export_success_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Festive confetti dots
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            val confetti = listOf(
                Triple(0.14f, 0.25f, NeonPurple),
                Triple(0.24f, 0.14f, FuturisticCyan),
                Triple(0.32f, 0.34f, StudioAmber),
                Triple(0.72f, 0.18f, NeonMagenta),
                Triple(0.84f, 0.30f, StudioEmerald),
                Triple(0.88f, 0.50f, FuturisticCyan),
                Triple(0.12f, 0.56f, NeonMagenta),
                Triple(0.76f, 0.58f, StudioAmber)
            )
            confetti.forEach { (fx, fy, col) ->
                drawRect(
                    color = col.copy(alpha = 0.85f),
                    topLeft = Offset(size.width * fx, size.height * fy),
                    size = Size(9.dp.toPx(), 6.dp.toPx())
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(StudioEmerald, Color(0xFF0D9488))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.export_success_title),
                            tint = Color.White,
                            modifier = Modifier.size(50.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.export_success_title),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    Text(
                        text = stringResource(R.string.export_success_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (exportedClip != null) {
                item(key = "exported_video_preview") {
                    val previewUri = remember(exportedClip.id, exportedClip.filePath, exportedClip.galleryUri) {
                        val file = File(exportedClip.filePath)
                        when {
                            file.exists() && file.length() > 0L -> Uri.fromFile(file)
                            !exportedClip.galleryUri.isNullOrBlank() -> Uri.parse(exportedClip.galleryUri)
                            else -> null
                        }
                    }
                    if (previewUri != null) {
                        VideoClipPlayerPreview(
                            videoUri = previewUri,
                            aspectRatio = exportedClip.aspectRatio,
                            autoPlay = true
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Bagikan ke Media Sosial -> Opens interactive Share Sheet
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(FuturisticCyan, FuturisticBlue)
                                )
                            )
                            .clickable(enabled = exportedClip != null) {
                                val clip = exportedClip ?: return@clickable
                                onOpenShareSheet(clip)
                            }
                            .testTag("export_success_share_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(
                                text = stringResource(R.string.export_success_share_social),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    // 1-Tap Quick Share Chips for popular platforms
                    if (exportedClip != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                SharePlatformTarget.TIKTOK,
                                SharePlatformTarget.INSTAGRAM,
                                SharePlatformTarget.YOUTUBE,
                                SharePlatformTarget.WHATSAPP
                            ).forEach { target ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val uri = onGetShareUri(exportedClip)
                                        if (uri == null) {
                                            onShareFeedback("File klip tidak ditemukan.")
                                            return@FilterChip
                                        }
                                        val caption = shareCaption?.takeIf { it.isNotBlank() }
                                            ?: SocialShareHelper.buildDefaultCaption(exportedClip, null)
                                        clipboard.setText(AnnotatedString(caption))
                                        val result = SocialShareHelper.launchDirectPlatformShare(
                                            context = context,
                                            shareUri = uri,
                                            title = exportedClip.title,
                                            captionText = caption,
                                            platform = target
                                        )
                                        onShareFeedback(
                                            when (result) {
                                                is ShareLaunchResult.LaunchedDirect ->
                                                    "Membuka ${target.displayName} (caption disalin)"
                                                is ShareLaunchResult.OpenedSystemChooser ->
                                                    "Share Sheet dibuka untuk ${target.displayName}"
                                                ShareLaunchResult.Unavailable ->
                                                    "Aplikasi berbagi tidak tersedia."
                                            }
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = target.shortLabel,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = CosmicSurfaceElevated
                                    ),
                                    border = BorderStroke(1.dp, target.accentColor.copy(alpha = 0.65f)),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("quick_share_chip_${target.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Buka di Galeri
                    SecondarySuccessButton(
                        icon = Icons.Filled.FolderOpen,
                        label = stringResource(R.string.export_success_open_gallery),
                        onClick = {
                            val clip = exportedClip
                            val targetUri = clip?.galleryUri?.let { Uri.parse(it) }
                                ?: clip?.let { onGetShareUri(it) }
                            if (targetUri != null) {
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(targetUri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(viewIntent)
                                }.onFailure {
                                    onOpenExportsHistory()
                                }
                            } else {
                                onOpenExportsHistory()
                            }
                        },
                        testTag = "export_success_open_gallery_button"
                    )

                    // Edit Lagi
                    SecondarySuccessButton(
                        icon = Icons.Filled.Edit,
                        label = stringResource(R.string.export_success_edit_again),
                        onClick = onEditAgain,
                        testTag = "export_success_edit_again_button"
                    )

                    // Kembali ke Beranda
                    SecondarySuccessButton(
                        icon = Icons.Filled.Home,
                        label = stringResource(R.string.export_success_back_home),
                        onClick = onBackToHome,
                        testTag = "export_success_home_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondarySuccessButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = CosmicSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
        }
    }
}
