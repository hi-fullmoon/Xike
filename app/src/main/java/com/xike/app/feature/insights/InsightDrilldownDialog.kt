package com.xike.app.feature.insights

import com.xike.app.data.JournalEntry
import com.xike.app.localizedText

import com.xike.app.ui.components.journal.JournalEntryDetailDialog

import com.xike.app.ui.components.JournalEntryCard
import com.xike.app.ui.components.PhotoGalleryDialog

import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikeContentGap




import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.InputStream

@Composable
internal fun InsightDrilldownDialog(
    title: String,
    subtitle: String,
    entries: List<JournalEntry>,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
    onDismiss: () -> Unit,
) {
    var detailEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val detailEntry = entries.firstOrNull { it.id == detailEntryId }
    var galleryImages by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var galleryInitialPage by rememberSaveable { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = XikeContentMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = XikeScreenHorizontalPadding, vertical = XikeScreenVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(XikeContentGap),
            ) {
                item(key = "drilldown-header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.headlineSmall)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = localizedText("关闭原始记录"))
                        }
                    }
                }
                if (entries.isEmpty()) {
                    item(key = "drilldown-empty") {
                        InsufficientDataNote(localizedText("这个统计项没有对应记录。"))
                    }
                } else {
                    items(entries, key = JournalEntry::id) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            modifier = Modifier.testTag("insight-source-${entry.id}"),
                            openImage = openImage,
                            onImageClick = { index ->
                                galleryImages = entry.imageFileNames
                                galleryInitialPage = index
                            },
                            onClick = { detailEntryId = entry.id },
                        )
                    }
                }
            }
        }
    }

    detailEntry?.let { entry ->
        JournalEntryDetailDialog(
            entry = entry,
            openImage = openImage,
            openAudio = openAudio,
            onDismiss = { detailEntryId = null },
        )
    }
    galleryImages?.let { images ->
        PhotoGalleryDialog(
            fileNames = images,
            initialPage = galleryInitialPage,
            openImage = openImage,
            onDismiss = { galleryImages = null },
        )
    }
}
