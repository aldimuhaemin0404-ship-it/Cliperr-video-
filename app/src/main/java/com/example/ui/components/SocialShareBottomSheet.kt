package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ExportedClip
import com.example.media.ShareLaunchResult
import com.example.media.SharePlatformTarget
import com.example.media.SocialShareHelper
import com.example.ui.theme.ActionButtonGradient
import com.example.ui.theme.CosmicOutline
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioEmeraldContainer

data class ShareSheetUiState(
    val clip: ExportedClip,
    val shareUri: Uri,
    val initialCaption: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialShareBottomSheet(
    state: ShareSheetUiState,
    onDismiss: () -> Unit,
    onSaveToGallery: (ExportedClip) -> Unit,
    onShareLaunched: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var includeCaption by remember(state.clip.id) { mutableStateOf(true) }
    var captionText by remember(state.clip.id, state.initialCaption) { mutableStateOf(state.initialCaption) }

    val installedMap = remember(state.clip.id) {
        SharePlatformTarget.entries.associateWith { target ->
            SocialShareHelper.findInstalledPackage(context, target) != null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CosmicSurface,
        contentColor = Color.White,
        modifier = Modifier.testTag("social_share_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NeonPurple.copy(alpha = 0.22f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                tint = FuturisticCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.share_sheet_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_share_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Clip Metadata Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CosmicSurfaceElevated,
                border = BorderStroke(1.dp, CosmicOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeonPurple.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = FuturisticCyan
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.clip.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.clip.aspectRatio.label} • ${formatDurationShort(state.clip.durationMs)} • ${formatFileSize(state.clip.fileSizeBytes)}",
                            style = MonoTimecodeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!state.clip.galleryUri.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StudioEmeraldContainer
                        ) {
                            Text(
                                text = stringResource(R.string.exports_saved_in_gallery),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = StudioEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSaveToGallery(state.clip) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("share_sheet_save_gallery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.share_sheet_save_gallery_first),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Caption & Hashtags Toggle + Editor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.share_sheet_include_caption),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White
                )
                Switch(
                    checked = includeCaption,
                    onCheckedChange = { includeCaption = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPurple
                    ),
                    modifier = Modifier.testTag("share_sheet_caption_switch")
                )
            }

            if (includeCaption) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        label = { Text(stringResource(R.string.share_sheet_caption_label)) },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_sheet_caption_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(captionText))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.share_sheet_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("share_sheet_copy_caption_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                tint = FuturisticCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.share_sheet_copy_caption),
                                color = FuturisticCyan,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Popular Social Platforms Grid (2 rows x 3 columns)
            Text(
                text = stringResource(R.string.share_sheet_platforms_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            val activeCaption = if (includeCaption) captionText.trim().ifBlank { null } else null

            val launchTarget: (SharePlatformTarget) -> Unit = { target ->
                if (!activeCaption.isNullOrBlank()) {
                    clipboard.setText(AnnotatedString(activeCaption))
                }
                val result = SocialShareHelper.launchDirectPlatformShare(
                    context = context,
                    shareUri = state.shareUri,
                    title = state.clip.title,
                    captionText = activeCaption,
                    platform = target
                )
                val statusMsg = when (result) {
                    is ShareLaunchResult.LaunchedDirect ->
                        "Membuka ${target.displayName} (caption disalin otomatis)"
                    is ShareLaunchResult.OpenedSystemChooser ->
                        "Share Sheet dibuka untuk ${target.displayName}"
                    ShareLaunchResult.Unavailable ->
                        "Tidak ada aplikasi pemutar/berbagi video yang tersedia."
                }
                onShareLaunched(statusMsg)
                onDismiss()
            }

            val rows = SharePlatformTarget.entries.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowTargets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTargets.forEach { target ->
                            val isInstalled = installedMap[target] == true
                            ShareTargetTile(
                                target = target,
                                isInstalled = isInstalled,
                                onClick = { launchTarget(target) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_platform_${target.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Primary Android System Share Sheet Button
            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (!activeCaption.isNullOrBlank()) {
                            clipboard.setText(AnnotatedString(activeCaption))
                        }
                        val result = SocialShareHelper.launchSystemShareSheet(
                            context = context,
                            shareUri = state.shareUri,
                            title = state.clip.title,
                            captionText = activeCaption
                        )
                        val msg = if (result is ShareLaunchResult.Unavailable) {
                            "Tidak ada aplikasi berbagi yang tersedia."
                        } else {
                            "Share Sheet Android dibuka (caption disalin otomatis)."
                        }
                        onShareLaunched(msg)
                        onDismiss()
                    }
                    .testTag("share_sheet_system_button")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ActionButtonGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = stringResource(R.string.share_sheet_system_button),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTargetTile(
    target: SharePlatformTarget,
    isInstalled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CosmicSurfaceElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (isInstalled) target.accentColor.copy(alpha = 0.7f) else CosmicOutline
        ),
        modifier = modifier
            .height(82.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = target.accentColor.copy(alpha = 0.18f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = platformIcon(target),
                        contentDescription = target.displayName,
                        tint = target.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = target.shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    if (isInstalled) R.string.share_sheet_status_installed
                    else R.string.share_sheet_status_chooser
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (isInstalled) target.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun platformIcon(target: SharePlatformTarget): ImageVector = when (target) {
    SharePlatformTarget.TIKTOK -> Icons.Filled.MusicNote
    SharePlatformTarget.INSTAGRAM -> Icons.Filled.CameraAlt
    SharePlatformTarget.YOUTUBE -> Icons.Filled.PlayCircleFilled
    SharePlatformTarget.WHATSAPP -> Icons.Filled.ChatBubbleOutline
    SharePlatformTarget.FACEBOOK -> Icons.Filled.Public
    SharePlatformTarget.X_TWITTER -> Icons.Filled.Tag
}
