package com.xike.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun JournalEntryCard(
    entry: JournalEntry,
    modifier: Modifier = Modifier,
    openImage: (String) -> InputStream?,
    onImageClick: (Int) -> Unit,
    onClick: () -> Unit = {},
    compactMedia: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            if (!compactMedia) entry.video?.let { VideoCard(it, previewOnly = true, onOpen = onClick) }
            if (!compactMedia && entry.imageFileNames.isNotEmpty()) {
                JournalPhotoMosaic(entry.imageFileNames, openImage, onImageClick)
            }
            Row(Modifier.padding(XikeCardPadding), verticalAlignment = Alignment.Top) {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f)) {
                    Box(contentAlignment = Alignment.Center) {
                        MoodEmoji(entry.mood, size = 24.dp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.mood.label, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Text(entry.createdAt.asChineseTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entry.tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(entry.tags.joinToString("  ·  ") { localizedText(it) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    entry.outdoor?.let { outdoor ->
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${outdoor.placeName} · ${outdoor.temperatureCelsius.roundToInt()}° · ${weatherConditionLabel(outdoor.weatherCode)}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (entry.note.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        Text(entry.note, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    entry.audio?.let { audio ->
                        Spacer(Modifier.height(9.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.MicNone,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                tr("语音 · ${formatAudioDuration(audio.durationMillis)}", "Audio · ${formatAudioDuration(audio.durationMillis)}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (compactMedia) {
                val mediaCount = entry.imageFileNames.size + if (entry.video != null) 1 else 0
                val hasMedia = mediaCount > 0
                if (hasMedia) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = XikeCardPadding)) {
                        val thumbnailWidth = (maxWidth - 16.dp) / 3
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            entry.video?.let { video ->
                                item(key = "video-${video.fileName}", contentType = "video") {
                                    Box(Modifier.width(thumbnailWidth)) {
                                        VideoCard(video, aspectRatio = 1f)
                                    }
                                }
                            }
                            items(
                                count = entry.imageFileNames.size,
                                key = { index -> "photo-$index-${entry.imageFileNames[index]}" },
                                contentType = { "photo" },
                            ) { index ->
                                val fileName = entry.imageFileNames[index]
                                SavedJournalImage(
                                    fileName = fileName,
                                    openStream = { openImage(fileName) },
                                    modifier = Modifier.width(thumbnailWidth).aspectRatio(1f).clip(XikeShapes.inner),
                                    order = index + 1,
                                    onClick = { onImageClick(index) },
                                )
                            }
                        }
                    }
                    if (mediaCount > 3) {
                        Text(
                            tr("共 $mediaCount 项媒体 · 左右滑动查看", "$mediaCount media items · Swipe to view"),
                            modifier = Modifier.padding(horizontal = XikeCardPadding).padding(top = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (hasMedia) Spacer(Modifier.height(XikeCardPadding))
            }
        }
    }
}


private fun Long.asChineseTime(): String = DateTimeFormatter.ofPattern("HH:mm", AppLocale.locale)
    .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
