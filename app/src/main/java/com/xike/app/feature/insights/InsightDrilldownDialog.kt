package com.xike.app.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.xike.app.JournalReadRepository
import com.xike.app.JournalSearchQuery
import com.xike.app.data.JournalEntry
import com.xike.app.feature.archive.ArchiveReadEntry
import com.xike.app.journalReadResult
import com.xike.app.localizedText
import com.xike.app.ui.components.JournalEntryCard
import com.xike.app.ui.components.PhotoGalleryDialog
import com.xike.app.ui.components.SecondaryPageHeader
import com.xike.app.ui.components.journal.JournalEntryDetailDialog
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import java.io.InputStream

@Composable
internal fun InsightDrilldownDialog(
    title: String,
    subtitle: String,
    entries: List<JournalEntry>,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
    onDismiss: () -> Unit,
    readRepository: JournalReadRepository? = null,
    entryIds: List<String> = entries.map { it.id },
    dataRevision: Long = 0,
    onReadError: (String) -> Unit = {},
) {
    var detailEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedDetail by remember { mutableStateOf<JournalEntry?>(null) }
    val detailEntry =
        if (readRepository == null) entries.firstOrNull { it.id == detailEntryId } else loadedDetail
    val flow =
        remember(readRepository, entryIds, dataRevision) {
            readRepository?.pages(JournalSearchQuery(), entryIds)
        }
    val paged = flow?.collectAsLazyPagingItems()
    val heights = remember(entryIds, dataRevision) { mutableMapOf<String, Int>() }
    LaunchedEffect(paged?.loadState) {
        if (
            paged?.loadState?.refresh is LoadState.Error ||
                paged?.loadState?.append is LoadState.Error ||
                paged?.loadState?.prepend is LoadState.Error
        ) {
            onReadError(localizedText("日记数据库暂时无法读取，原数据未被覆盖。"))
        }
    }
    LaunchedEffect(detailEntryId, dataRevision) {
        val id = detailEntryId
        if (id == null) loadedDetail = null
        else if (readRepository != null)
            journalReadResult { readRepository.entry(id) }
                .onSuccess { loadedDetail = it }
                .onFailure { onReadError(it.message ?: localizedText("日记数据库暂时无法读取，原数据未被覆盖。")) }
    }
    var galleryImages by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var galleryInitialPage by rememberSaveable { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier =
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = XikeContentMaxWidth)
                        .fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        horizontal = XikeScreenHorizontalPadding,
                        vertical = XikeScreenVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(XikeContentGap),
            ) {
                item(key = "drilldown-header") {
                    SecondaryPageHeader(
                        title = title,
                        subtitle = subtitle,
                        closeDescription = localizedText("关闭原始记录"),
                        onClose = onDismiss,
                    )
                }
                if (entryIds.isEmpty()) {
                    item(key = "drilldown-empty") {
                        InsufficientDataNote(localizedText("这个统计项没有对应记录。"))
                    }
                } else if (paged != null) {
                    items(entryIds.size, key = { entryIds[it] }) { index ->
                        val entry = if (index < paged.itemCount) paged[index] else null
                        ArchiveReadEntry(
                            entryIds[index],
                            entry?.takeIf { it.id == entryIds[index] },
                            readRepository,
                            dataRevision,
                            heights,
                            onReadError,
                        ) { loaded ->
                            JournalEntryCard(
                                entry = loaded,
                                modifier = Modifier.testTag("insight-source-${loaded.id}"),
                                openImage = openImage,
                                onImageClick = { imageIndex ->
                                    galleryImages = loaded.imageFileNames
                                    galleryInitialPage = imageIndex
                                },
                                onClick = {
                                    loadedDetail = loaded
                                    detailEntryId = loaded.id
                                },
                            )
                        }
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
