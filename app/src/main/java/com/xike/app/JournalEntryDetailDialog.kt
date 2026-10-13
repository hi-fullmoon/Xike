package com.xike.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun JournalEntryDetailDialog(
    entry: JournalEntry,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream? = { null },
    onDismiss: () -> Unit,
    onRequestEdit: (() -> Unit)? = null,
    onRequestDelete: (() -> Unit)? = null,
) {
    var photoPage by remember(entry.id) { mutableIntStateOf(-1) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = XikeContentMaxWidth)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = XikeScreenHorizontalPadding,
                        vertical = XikeScreenVerticalPadding,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(localizedText("这一刻"), modifier = Modifier.weight(1f), style = XikePageTitleStyle.copy(fontSize = 30.sp, lineHeight = 38.sp))
                    if (onRequestEdit != null) {
                        IconButton(onClick = onRequestEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = localizedText("编辑记录"))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = localizedText("关闭记录详情"))
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("journal-detail-body")
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = XikeScreenHorizontalPadding, vertical = XikeScreenVerticalPadding),
                    verticalArrangement = Arrangement.spacedBy(XikeContentGap),
                ) {
                Surface(shape = XikeShapes.card, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(XikeCardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = XikeShapes.inner,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MoodEmoji(entry.mood, size = 28.dp)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.mood.label, style = MaterialTheme.typography.titleLarge)
                            Text(
                                entry.createdAt.asDetailChineseDateTime(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                            )
                        }
                    }
                }

                if (entry.note.isNotBlank()) {
                    JournalDetailSection(localizedText("记录")) {
                        JournalDetailCard {
                            Text(
                                entry.note,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (entry.tags.isNotEmpty()) {
                    JournalDetailSection(localizedText("主题")) {
                        JournalDetailCard {
                            Text(entry.tags.joinToString("  ·  ") { localizedText(it) }, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                entry.outdoor?.let { outdoor ->
                    JournalDetailSection(localizedText("此刻窗外")) {
                        JournalDetailCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(outdoor.placeName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${outdoor.temperatureCelsius.roundToInt()}° · ${weatherConditionLabel(outdoor.weatherCode)} · ${outdoor.source}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                entry.audio?.let { audio ->
                    JournalDetailSection(localizedText("这一刻的声音")) {
                        VoicePlaybackCard(
                            audio = audio,
                            openAudio = openAudio,
                            showBorder = false,
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = XikeShapes.card,
                        )
                    }
                }
                if (entry.imageFileNames.isNotEmpty()) {
                    JournalDetailSection(tr("这一刻的照片 · ${entry.imageFileNames.size} 张", "Photos from this moment · ${entry.imageFileNames.size}")) {
                        JournalDetailCard {
                            Box(Modifier.fillMaxWidth().clip(XikeShapes.inner)) {
                                JournalPhotoMosaic(entry.imageFileNames, openImage) { photoPage = it }
                            }
                        }
                    }
                }
                entry.video?.let { video ->
                    JournalDetailSection(tr("这一刻的视频", "Video from this moment")) {
                        JournalDetailCard { VideoCard(video) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (onRequestDelete == null) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = XikeShapes.button,
                    ) { Text(localizedText("关闭")) }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            onClick = onRequestDelete,
                            modifier = Modifier.weight(1f),
                            shape = XikeShapes.button,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(localizedText("删除记录"))
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = XikeShapes.button,
                        ) { Text(localizedText("关闭")) }
                    }
                }
                }
            }
        }
    }
    if (photoPage >= 0 && photoPage < entry.imageFileNames.size) {
        PhotoGalleryDialog(
            fileNames = entry.imageFileNames,
            initialPage = photoPage,
            openImage = openImage,
            onDismiss = { photoPage = -1 },
        )
    }
}

@Composable
private fun JournalDetailCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(XikeCardPadding)) { content() }
    }
}

@Composable
private fun JournalDetailSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

internal fun Long.asDetailChineseDateTime(): String =
    DateTimeFormatter.ofPattern(tr("yyyy年M月d日 EEEE · HH:mm", "EEE, MMM d, yyyy · HH:mm"), AppLocale.locale)
        .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
