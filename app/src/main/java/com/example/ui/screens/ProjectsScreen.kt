package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.VideoFilterPreset
import com.example.ui.components.AiDemoNoticeBanner
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.ActionButtonGradient
import com.example.ui.theme.BannerHighlightGradient
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticBlue
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioAmber
import kotlin.math.abs

@Composable
fun ProjectsScreen(
    projects: List<ClipProject>,
    selectedProjectId: Long?,
    segments: List<ClipSegment>,
    isExporting: Boolean,
    onSelectProject: (Long) -> Unit,
    onEditSegment: (Long) -> Unit,
    onExportSegment: (ClipSegment) -> Unit,
    onToggleFavorite: (ClipSegment) -> Unit,
    onBookmarkAllFavorites: () -> Unit,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var favoritesOnly by remember { mutableStateOf(false) }
    val selectedProject = remember(projects, selectedProjectId) {
        projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
    }
    val displayedSegments = remember(segments, favoritesOnly) {
        if (favoritesOnly) segments.filter { it.isFavorite } else segments
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmicBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        if (projects.isEmpty()) {
            EmptyClipsState(onGoToStudio = onGoToStudio)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.clips_screen_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = !favoritesOnly,
                                    onClick = { favoritesOnly = false },
                                    label = { Text(stringResource(R.string.clips_filter_all)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonPurple,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                                FilterChip(
                                    selected = favoritesOnly,
                                    onClick = { favoritesOnly = true },
                                    label = { Text(stringResource(R.string.clips_filter_favorites)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonPurple,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }

                        if (projects.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                projects.forEach { project ->
                                    FilterChip(
                                        selected = project.id == selectedProjectId,
                                        onClick = { onSelectProject(project.id) },
                                        label = {
                                            Text(
                                                text = project.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                }
                            }
                        }

                        // Highlight Banner matching "Ditemukan 5 Klip Terbaik!"
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    displayedSegments.firstOrNull()?.let { onEditSegment(it.id) }
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BannerHighlightGradient)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = StudioAmber,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Ditemukan ${segments.size} Klip Terbaik!",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = stringResource(R.string.clips_found_banner_subtitle),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.88f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        AiDemoNoticeBanner(compact = true)
                    }
                }

                if (displayedSegments.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CosmicSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Belum ada klip yang ditandai Favorit. Ketuk ikon bookmark pada daftar klip di bawah.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(displayedSegments, key = { _, seg -> seg.id }) { index, segment ->
                        RankedClipRowCard(
                            rank = index + 1,
                            segment = segment,
                            thumbnailUrl = selectedProject?.thumbnailUrl,
                            onEdit = { onEditSegment(segment.id) },
                            onToggleFavorite = { onToggleFavorite(segment) }
                        )
                    }
                }

                // Bottom action buttons matching Edit Semua + Simpan Favorit
                if (segments.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ActionButtonGradient)
                                    .clickable {
                                        val target = displayedSegments.firstOrNull() ?: segments.first()
                                        onEditSegment(target.id)
                                    }
                                    .testTag("edit_all_clips_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCut,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.clips_edit_all),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onBookmarkAllFavorites,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("save_all_favorites_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkBorder,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = stringResource(R.string.clips_save_favorite),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedClipRowCard(
    rank: Int,
    segment: ClipSegment,
    thumbnailUrl: String?,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicSurfaceElevated),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .testTag("clip_card_${segment.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Rank circle (1..5)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(NeonPurple),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MonoTimecodeStyle.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            // Thumbnail + duration pill
            Box(
                modifier = Modifier
                    .width(98.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CosmicSurface)
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = segment.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero),
                        contentDescription = segment.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(5.dp),
                    color = Color.Black.copy(alpha = 0.78f)
                ) {
                    Text(
                        text = formatDurationShort(segment.durationMs),
                        style = MonoTimecodeStyle,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            // Viral score & title info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = StudioAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${segment.viralScore}%",
                        style = MonoTimecodeStyle.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    if (segment.videoFilter != VideoFilterPreset.NONE || abs(segment.brightness) >= 0.01f) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = segment.videoFilter.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = FuturisticCyan,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = segment.hookLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("favorite_clip_button_${segment.id}")
            ) {
                Icon(
                    imageVector = if (segment.isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = "Favorit",
                    tint = if (segment.isFavorite) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(FuturisticCyan, FuturisticBlue))
                    )
                    .clickable(onClick = onEdit)
                    .testTag("edit_clip_button_${segment.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.clips_edit_preview),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyClipsState(onGoToStudio: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 460.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_clips),
            contentDescription = stringResource(R.string.clips_empty_title),
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.clips_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.clips_empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onGoToStudio,
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("empty_go_studio_button")
        ) {
            Text(stringResource(R.string.clips_go_to_studio))
        }
    }
}
