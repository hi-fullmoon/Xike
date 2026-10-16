package com.xike.app.feature.archive

import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.xike.app.AppLocale
import com.xike.app.JournalEntryIndex
import com.xike.app.JournalImageFilter
import com.xike.app.JournalIndexPage
import com.xike.app.JournalLibraryState
import com.xike.app.JournalReadRepository
import com.xike.app.JournalSearchPage
import com.xike.app.JournalSearchQuery
import com.xike.app.canonicalTopics
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.filterJournalEntries
import com.xike.app.journalReadResult
import com.xike.app.localizedText
import com.xike.app.toggleTopic
import com.xike.app.tr
import com.xike.app.ui.components.DestructiveConfirmationDialog
import com.xike.app.ui.components.JournalEntryCard
import com.xike.app.ui.components.PhotoGalleryDialog
import com.xike.app.ui.components.XikeNotice
import com.xike.app.ui.components.journal.JournalEntryDetailDialog
import com.xike.app.ui.components.journal.asDetailChineseDateTime
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeEyebrowStyle
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikePageTitleStyle
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikeSectionTitleStyle
import com.xike.app.ui.theme.XikeShapes
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ARCHIVE_PAGE_SIZE = 60

private sealed interface ArchiveRow {
    data class DateHeader(val date: LocalDate, val count: Int) : ArchiveRow

    data class Entry(val reference: JournalEntryIndex, val position: Int) : ArchiveRow
}

private data class ArchiveResults(
    val entries: List<JournalEntryIndex> = emptyList(),
    val rows: List<ArchiveRow> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
)

private suspend fun archiveResults(
    entries: List<JournalEntryIndex>,
    totalCount: Int,
): ArchiveResults =
    withContext(Dispatchers.Default) {
        val groups =
            entries
                .withIndex()
                .groupBy { it.value.createdAt.localDate() }
                .toList()
                .sortedByDescending { it.first }
        val rows = buildList {
            groups.forEach { (date, dayEntries) ->
                add(ArchiveRow.DateHeader(date, dayEntries.size))
                dayEntries.forEach { add(ArchiveRow.Entry(it.value, it.index)) }
            }
        }
        ArchiveResults(entries, rows, totalCount, entries.size < totalCount)
    }

@Composable
fun JournalArchiveScreen(
    padding: PaddingValues,
    entries: List<JournalEntry>,
    onSearch: suspend (JournalSearchQuery, Int, Int) -> Result<JournalSearchPage>,
    onUpdate: suspend (JournalEntry, List<String>, List<Uri>) -> Result<JournalEntry>,
    onDelete: suspend (JournalEntry) -> Result<Unit>,
    onUndoDelete: suspend (String) -> Result<Unit>,
    onFinalizeDelete: suspend (String) -> Result<Unit>,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream? = { null },
    entriesLoading: Boolean = false,
    readRepository: JournalReadRepository? = null,
    libraryState: JournalLibraryState = JournalLibraryState(totalCount = entries.size),
    onReadError: (String) -> Unit = {},
) {
    val today = LocalDate.now()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val archiveListState = rememberLazyListState()
    var queryText by rememberSaveable { mutableStateOf("") }
    var selectedMoodNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedTags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var imageFilterName by rememberSaveable { mutableStateOf(JournalImageFilter.ANY.name) }
    var datePresetName by rememberSaveable { mutableStateOf(ArchiveDatePreset.ALL.name) }
    var selectedDateValue by rememberSaveable { mutableStateOf<String?>(null) }
    var visibleMonthValue by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var viewModeName by rememberSaveable { mutableStateOf(ArchiveViewMode.TIMELINE.name) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(ArchiveResults()) }
    var hasSuccessfulResults by remember { mutableStateOf(false) }
    val retainedEntries = remember(readRepository) { LruCache<String, JournalEntry>(300) }
    val resultEntries = results.entries
    val totalResultCount = results.totalCount
    val headerCount = if (hasSuccessfulResults) totalResultCount else libraryState.totalCount
    val resultRows = results.rows
    val hasMoreResults = results.hasMore
    var fallbackEntries by remember { mutableStateOf(emptyList<JournalEntry>()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var calendarError by remember { mutableStateOf<String?>(null) }
    var retryRevision by remember { mutableIntStateOf(0) }
    var resultDate by remember { mutableStateOf<LocalDate?>(null) }
    var galleryImages by remember { mutableStateOf<List<String>?>(null) }
    var galleryInitialPage by remember { mutableIntStateOf(0) }
    val entrySaver =
        Saver<JournalEntry?, String>(
            save = { it?.toJson()?.toString() ?: "" },
            restore = {
                it.takeIf(String::isNotEmpty)?.let { json ->
                    JournalEntry.fromJson(org.json.JSONObject(json))
                }
            },
        )
    var detailEntry by
        rememberSaveable(stateSaver = entrySaver) { mutableStateOf<JournalEntry?>(null) }
    var editEntry by
        rememberSaveable(stateSaver = entrySaver) { mutableStateOf<JournalEntry?>(null) }
    var scrollToSelectedDateResults by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<JournalEntry?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val selectedMoods =
        remember(selectedMoodNames) {
            selectedMoodNames
                .mapNotNull { name -> Mood.entries.firstOrNull { it.name == name } }
                .toSet()
        }
    val imageFilter =
        JournalImageFilter.entries.firstOrNull { it.name == imageFilterName }
            ?: JournalImageFilter.ANY
    val selectedDate = selectedDateValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val datePreset =
        ArchiveDatePreset.entries.firstOrNull { it.name == datePresetName } ?: ArchiveDatePreset.ALL
    val presetRange = datePreset.range(today)
    val searchQuery =
        remember(
            queryText,
            selectedMoods,
            selectedTags,
            imageFilter,
            selectedDate,
            presetRange,
        ) {
            JournalSearchQuery(
                text = queryText,
                moods = selectedMoods,
                tags = selectedTags.toSet(),
                startDate = selectedDate ?: presetRange.first,
                endDate = selectedDate ?: presetRange.second,
                imageFilter = imageFilter,
            )
        }

    val requestKey = remember(entries, libraryState.revision, searchQuery, retryRevision) { Any() }
    var pagingQuery by remember { mutableStateOf(searchQuery) }
    var pagingRequestKey by remember { mutableStateOf<Any?>(null) }
    val pagingFlow =
        remember(readRepository, pagingQuery, pagingRequestKey) {
            if (pagingRequestKey == null) null else readRepository?.pages(pagingQuery)
        }
    val pagedEntries = pagingFlow?.collectAsLazyPagingItems()
    val entryHeights = remember(pagingRequestKey) { mutableMapOf<String, Int>() }
    LaunchedEffect(pagedEntries?.itemSnapshotList) {
        pagedEntries?.itemSnapshotList?.items?.forEach { retainedEntries.put(it.id, it) }
    }
    val currentRequestKey by androidx.compose.runtime.rememberUpdatedState(requestKey)
    var resultKey by remember { mutableStateOf<Any?>(null) }
    val viewMode =
        ArchiveViewMode.entries.firstOrNull { it.name == viewModeName } ?: ArchiveViewMode.CALENDAR
    // 结果状态项之前固定有页头与工具箱两项，其间按需插入筛选面板与月历；列表项增删时同步维护这里。
    val resultStatusIndex =
        2 + (if (showFilters) 1 else 0) + (if (viewMode == ArchiveViewMode.CALENDAR) 1 else 0)
    val currentResultStatusIndex by androidx.compose.runtime.rememberUpdatedState(resultStatusIndex)

    LaunchedEffect(pagedEntries?.loadState) {
        if (
            pagedEntries?.loadState?.refresh is LoadState.Error ||
                pagedEntries?.loadState?.append is LoadState.Error ||
                pagedEntries?.loadState?.prepend is LoadState.Error
        ) {
            onReadError(localizedText("日记数据库暂时无法读取，原数据未被覆盖。"))
        }
    }
    LaunchedEffect(requestKey, entriesLoading) {
        if (entriesLoading) return@LaunchedEffect
        isSearching = true
        isLoadingMore = false
        searchError = null
        if (searchQuery.normalizedText.isNotEmpty()) delay(220)
        pagedEntries?.itemSnapshotList?.items?.forEach { retainedEntries.put(it.id, it) }
        val firstPage =
            if (readRepository != null) {
                journalReadResult { readRepository.indexPage(searchQuery, 0) }
            } else {
                onSearch(searchQuery, 0, ARCHIVE_PAGE_SIZE).map { page ->
                    fallbackEntries = page.entries
                    JournalIndexPage(
                        page.entries.map { JournalEntryIndex(it.id, it.createdAt) },
                        page.totalCount,
                    )
                }
            }
        if (currentRequestKey !== requestKey) return@LaunchedEffect
        firstPage
            .onSuccess { page ->
                val nextResults = archiveResults(page.entries, page.totalCount)
                if (currentRequestKey !== requestKey) return@LaunchedEffect
                if (readRepository != null) fallbackEntries = emptyList()
                results = nextResults
                hasSuccessfulResults = true
                resultKey = requestKey
                resultDate = selectedDate
                pagingQuery = searchQuery
                pagingRequestKey = requestKey
                searchError = null
            }
            .onFailure { error ->
                // A failed read cannot replace the last successful snapshot or its paging source.
                searchError = error.message ?: localizedText("搜索暂时不可用")
            }
        isSearching = false
        if (scrollToSelectedDateResults) {
            withFrameNanos {}
            archiveListState.animateScrollToItem(currentResultStatusIndex)
            scrollToSelectedDateResults = false
        }
    }

    val legacyTags =
        remember(entries) {
            entries
                .flatMap { it.tags.canonicalTopics() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
                )
                .map { it.key }
        }
    val calendarQuery =
        remember(searchQuery) {
            searchQuery.copy(startDate = null, endDate = null)
        }
    val visibleMonth = runCatching {
        YearMonth.parse(visibleMonthValue)
    }
        .getOrDefault(YearMonth.from(today))
    var availableTags by remember { mutableStateOf(legacyTags) }
    var calendarCounts by remember { mutableStateOf(emptyMap<LocalDate, Int>()) }
    var calendarResultKey by remember { mutableStateOf<Pair<JournalSearchQuery, YearMonth>?>(null) }
    LaunchedEffect(readRepository, entries, libraryState.revision) {
        if (readRepository == null) availableTags = legacyTags
        else
            journalReadResult { readRepository.topics() }
                .onSuccess { availableTags = it }
                .onFailure { onReadError(localizedText("日记数据库暂时无法读取，原数据未被覆盖。")) }
    }
    LaunchedEffect(
        readRepository,
        entries,
        libraryState.revision,
        calendarQuery,
        visibleMonth,
        viewMode,
        retryRevision,
    ) {
        if (viewMode != ArchiveViewMode.CALENDAR) return@LaunchedEffect
        val calendarKey = calendarQuery to visibleMonth
        if (calendarResultKey != calendarKey) calendarCounts = emptyMap()
        val result = journalReadResult {
            if (readRepository != null) readRepository.calendar(calendarQuery, visibleMonth)
            else
                withContext(Dispatchers.Default) {
                    filterJournalEntries(entries, calendarQuery)
                        .groupingBy { it.createdAt.localDate() }
                        .eachCount()
                }
        }
        result
            .onSuccess {
                calendarCounts = it
                calendarResultKey = calendarKey
                calendarError = null
            }
            .onFailure { calendarError = it.message ?: localizedText("搜索暂时不可用") }
    }

    LaunchedEffect(entries, libraryState.revision, detailEntry?.id, entriesLoading) {
        if (entriesLoading) return@LaunchedEffect
        val selectedId = detailEntry?.id ?: return@LaunchedEffect
        if (readRepository == null) detailEntry = entries.firstOrNull { it.id == selectedId }
        else
            journalReadResult { readRepository.entry(selectedId) }
                .onSuccess { detailEntry = it }
                .onFailure { onReadError(localizedText("日记数据库暂时无法读取，原数据未被覆盖。")) }
    }

    // Capture structural conditions once per composition; lazy measurement must see one result
    // snapshot.
    val noResults = hasSuccessfulResults && resultEntries.isEmpty() && !isSearching
    val filtersVisible = showFilters

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = archiveListState,
            modifier =
                Modifier.testTag("archive-list")
                    .widthIn(max = XikeContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(padding),
            contentPadding =
                PaddingValues(
                    horizontal = XikeScreenHorizontalPadding,
                    vertical = XikeScreenVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(XikeContentGap),
        ) {
            item(key = "archive-header") {
                ArchiveHeader(
                    recordLabel =
                        if (pagingQuery.isEmpty) tr("$headerCount 条记录", "$headerCount entries")
                        else tr("找到 $totalResultCount 条", "$totalResultCount entries found")
                )
            }

            item(key = "archive-toolbox") {
                ArchiveToolbox(
                    queryText = queryText,
                    viewMode = viewMode,
                    activeFilterCount = searchQuery.activeFilterCount,
                    showFilters = showFilters,
                    onQueryChange = { queryText = it.take(80) },
                    onClearQuery = { queryText = "" },
                    onViewModeChange = { viewModeName = it.name },
                    onToggleFilters = { showFilters = !showFilters },
                )
            }

            if (filtersVisible) {
                item(key = "archive-filters") {
                    ArchiveFilters(
                        selectedMoodNames = selectedMoodNames,
                        availableTags = availableTags,
                        selectedTags = selectedTags,
                        imageFilter = imageFilter,
                        datePreset = datePreset,
                        selectedDate = selectedDate,
                        onToggleMood = { mood ->
                            selectedMoodNames = selectedMoodNames.toggle(mood.name)
                        },
                        onToggleTag = { tag -> selectedTags = toggleTopic(selectedTags, tag) },
                        onImageFilterChange = { imageFilterName = it.name },
                        onDatePresetChange = {
                            datePresetName = it.name
                            selectedDateValue = null
                        },
                        onClearDate = {
                            datePresetName = ArchiveDatePreset.ALL.name
                            selectedDateValue = null
                        },
                        onClearAll = {
                            queryText = ""
                            selectedMoodNames = emptyList()
                            selectedTags = emptyList()
                            imageFilterName = JournalImageFilter.ANY.name
                            datePresetName = ArchiveDatePreset.ALL.name
                            selectedDateValue = null
                        },
                    )
                }
            }

            if (viewMode == ArchiveViewMode.CALENDAR) {
                item(key = "calendar-$visibleMonthValue") {
                    JournalMonthCalendar(
                        month = visibleMonth,
                        today = today,
                        selectedDate = selectedDate,
                        countsByDate = calendarCounts,
                        onPreviousMonth = {
                            visibleMonthValue = visibleMonth.minusMonths(1).toString()
                        },
                        onNextMonth = { visibleMonthValue = visibleMonth.plusMonths(1).toString() },
                        onSelectDate = { date ->
                            scrollToSelectedDateResults = true
                            selectedDateValue = if (selectedDate == date) null else date.toString()
                            datePresetName = ArchiveDatePreset.ALL.name
                        },
                    )
                }
            }

            item(key = "archive-result-status") {
                ArchiveResultStatus(
                    shownCount = resultEntries.size,
                    matchingCount = totalResultCount,
                    libraryCount = libraryState.totalCount,
                    isSearching = isSearching,
                    searchError =
                        searchError
                            ?: calendarError.takeIf { viewMode == ArchiveViewMode.CALENDAR },
                    selectedDate = resultDate,
                    hasSuccessfulResults = hasSuccessfulResults,
                    onRetry = { retryRevision++ },
                )
            }

            if (libraryState.totalCount == 0) {
                item(key = "archive-empty") {
                    ArchiveEmptyState(
                        icon = Icons.Outlined.CalendarMonth,
                        title = localizedText("这里还很安静"),
                        description = localizedText("第一条不必完整，选一种心情就够了。"),
                    )
                }
            } else if (noResults) {
                item(key = "archive-no-results") {
                    ArchiveEmptyState(
                        icon = Icons.Outlined.Search,
                        title = localizedText("没有找到这一刻"),
                        description = localizedText("试试减少筛选条件，或换一个主题。"),
                    )
                }
            } else {
                items(
                    resultRows,
                    key = { row ->
                        when (row) {
                            is ArchiveRow.DateHeader -> "date-${row.date}"
                            is ArchiveRow.Entry -> "entry-${row.reference.id}"
                        }
                    },
                    contentType = { row -> if (row is ArchiveRow.DateHeader) "date" else "entry" },
                ) { row ->
                    if (row is ArchiveRow.DateHeader) {
                        ArchiveDateHeader(row.date, row.count)
                    } else if (row is ArchiveRow.Entry) {
                        val reference = row.reference
                        val paged =
                            if (row.position < (pagedEntries?.itemCount ?: 0))
                                pagedEntries?.get(row.position)
                            else null
                        val retained =
                            retainedEntries.get(reference.id)
                                ?: fallbackEntries.firstOrNull { it.id == reference.id }
                        val entry = paged?.takeIf { it.id == reference.id } ?: retained
                        ArchiveReadEntry(
                            reference.id,
                            entry,
                            readRepository,
                            libraryState.revision,
                            entryHeights,
                            onReadError,
                        ) { loaded ->
                            ArchiveTimelineEntry(
                                entry = loaded,
                                openImage = openImage,
                                onImageClick = { index ->
                                    galleryImages = loaded.imageFileNames
                                    galleryInitialPage = index
                                },
                                onClick = { detailEntry = loaded },
                            )
                        }
                    }
                }
                if (hasMoreResults) {
                    item(key = "archive-load-more") {
                        TextButton(
                            shape = XikeShapes.button,
                            enabled = !isLoadingMore && !isSearching && resultKey === requestKey,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (isLoadingMore || isSearching || resultKey !== requestKey)
                                    return@TextButton
                                isLoadingMore = true
                                val pageKey = requestKey
                                val pageQuery = searchQuery
                                val baseEntries = resultEntries
                                scope.launch {
                                    val result =
                                        if (readRepository != null) {
                                            journalReadResult {
                                                readRepository.indexPage(
                                                    pageQuery,
                                                    baseEntries.size,
                                                    totalResultCount,
                                                )
                                            }
                                        } else
                                            onSearch(pageQuery, baseEntries.size, ARCHIVE_PAGE_SIZE)
                                                .map { page ->
                                                    fallbackEntries = fallbackEntries + page.entries
                                                    JournalIndexPage(
                                                        page.entries.map {
                                                            JournalEntryIndex(it.id, it.createdAt)
                                                        },
                                                        page.totalCount,
                                                    )
                                                }
                                    if (currentRequestKey !== pageKey) return@launch
                                    result
                                        .onSuccess { page ->
                                            val nextResults =
                                                archiveResults(
                                                    (baseEntries + page.entries).distinctBy {
                                                        it.id
                                                    },
                                                    page.totalCount,
                                                )
                                            if (currentRequestKey !== pageKey) return@launch
                                            results = nextResults
                                            searchError = null
                                        }
                                        .onFailure { error ->
                                            searchError =
                                                error.message ?: localizedText("更多记录暂时无法读取")
                                        }
                                    isLoadingMore = false
                                }
                            },
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (isLoadingMore) localizedText("正在读取…")
                                else localizedText("加载更多记录")
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
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

    detailEntry?.let { entry ->
        JournalEntryDetailDialog(
            entry = entry,
            openImage = openImage,
            openAudio = openAudio,
            onDismiss = { detailEntry = null },
            onRequestEdit = { editEntry = entry },
            onRequestDelete = {
                deleteError = null
                deleteCandidate = entry
            },
        )
    }

    editEntry?.let { entry ->
        JournalEntryEditDialog(
            entry = entry,
            openImage = openImage,
            openAudio = openAudio,
            onDismiss = { editEntry = null },
            onSave = { updatedEntry, retainedImages, newImageUris ->
                onUpdate(updatedEntry, retainedImages, newImageUris)
            },
            onSaved = { savedEntry ->
                detailEntry = savedEntry
                editEntry = null
            },
        )
    }

    deleteCandidate?.let { entry ->
        DeleteJournalDialog(
            entry = entry,
            isDeleting = isDeleting,
            error = deleteError,
            onDismiss = {
                if (!isDeleting) {
                    deleteCandidate = null
                    deleteError = null
                }
            },
            onConfirm = {
                if (!isDeleting) {
                    isDeleting = true
                    deleteError = null
                    scope.launch {
                        val result = onDelete(entry)
                        isDeleting = false
                        result.onFailure { error ->
                            deleteError = error.message ?: localizedText("删除失败，请重试。")
                        }
                        if (result.isSuccess) {
                            if (detailEntry?.id == entry.id) {
                                detailEntry = null
                            }
                            if (editEntry?.id == entry.id) {
                                editEntry = null
                            }
                            deleteCandidate = null
                            var deleteWasUndone = false
                            try {
                                val snackbarResult =
                                    snackbarHostState.showSnackbar(
                                        message = localizedText("记录已删除"),
                                        actionLabel = localizedText("撤销"),
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Long,
                                    )
                                if (snackbarResult == SnackbarResult.ActionPerformed) {
                                    onUndoDelete(entry.id)
                                        .onSuccess {
                                            deleteWasUndone = true
                                            XikeNotice.makeText(
                                                    context,
                                                    localizedText("记录已恢复"),
                                                    XikeNotice.LENGTH_SHORT,
                                                )
                                                .show()
                                        }
                                        .onFailure { error ->
                                            XikeNotice.makeText(
                                                    context,
                                                    error.message ?: localizedText("撤销删除失败"),
                                                    XikeNotice.LENGTH_LONG,
                                                )
                                                .show()
                                        }
                                }
                            } finally {
                                if (!deleteWasUndone) {
                                    withContext(NonCancellable) { onFinalizeDelete(entry.id) }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun ArchiveHeader(recordLabel: String) {
    val textMeasurer = rememberTextMeasurer()
    val title = localizedText("回望")
    val titleStyle = XikePageTitleStyle
    val countStyle = MaterialTheme.typography.labelSmall
    val requiredWidth =
        with(LocalDensity.current) {
            (textMeasurer.measure(title, titleStyle, softWrap = false).size.width +
                    textMeasurer.measure(recordLabel, countStyle, softWrap = false).size.width)
                .toDp()
        } + 22.dp + 12.dp
    val recordBadge: @Composable () -> Unit = {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        ) {
            Text(
                recordLabel,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                style = countStyle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp)) {
        Text(
            tr("值得记住的时刻", "MOMENTS TO REMEMBER"),
            style = XikeEyebrowStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(9.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (requiredWidth > maxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = titleStyle)
                    recordBadge()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), style = titleStyle)
                    recordBadge()
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            localizedText("翻一翻走过的日子，看见当时的自己。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArchiveResultStatus(
    shownCount: Int,
    matchingCount: Int,
    libraryCount: Int,
    isSearching: Boolean,
    searchError: String?,
    selectedDate: LocalDate?,
    hasSuccessfulResults: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    selectedDate?.let {
                        tr(
                            "${it.asShortChineseDate()}的记录",
                            "Entries for ${it.asShortChineseDate()}",
                        )
                    } ?: localizedText("最近的片段"),
                    style = XikeSectionTitleStyle,
                )
                Text(
                    if (selectedDate == null) localizedText("按时间从近到远")
                    else localizedText("只看这一天留下的片段"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSearching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            ) {
                Text(
                    when {
                        shownCount < matchingCount ->
                            tr(
                                "$shownCount / $matchingCount 条",
                                "$shownCount / $matchingCount entries",
                            )
                        matchingCount == libraryCount ->
                            tr("$matchingCount 条", "$matchingCount entries")
                        else ->
                            tr(
                                "$matchingCount / $libraryCount 条",
                                "$matchingCount / $libraryCount entries",
                            )
                    },
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (searchError != null) {
            Text(
                if (hasSuccessfulResults)
                    tr(
                        "$searchError\n筛选或刷新未完成，当前显示上次成功读取的结果。",
                        "$searchError\nFiltering or refresh did not finish. Showing the last successful results.",
                    )
                else searchError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry, enabled = !isSearching, shape = XikeShapes.button) {
                Text(tr("重试", "Retry"))
            }
        }
    }
}

@Composable
private fun ArchiveDateHeader(date: LocalDate, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern(tr("M月d日", "MMM d"), AppLocale.locale)),
            style = XikeSectionTitleStyle,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE", AppLocale.locale)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            tr("$count 条", "$count entries"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArchiveTimelineEntry(
    entry: JournalEntry,
    openImage: (String) -> InputStream?,
    onImageClick: (Int) -> Unit,
    onClick: () -> Unit,
) {
    JournalEntryCard(
        entry = entry,
        compactMedia = true,
        moodIconSize = 36.dp,
        compactMoodOnly = true,
        openImage = openImage,
        onImageClick = onImageClick,
        onClick = onClick,
    )
}

@Composable
private fun ArchiveEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(XikeCardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = XikeSectionTitleStyle)
            Spacer(Modifier.height(7.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeleteJournalDialog(
    entry: JournalEntry,
    isDeleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmationDialog(
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        isProcessing = isDeleting,
        dismissText = localizedText("取消"),
        confirmText = localizedText(if (isDeleting) "正在删除…" else "确认删除"),
        title = localizedText("删除这条记录？"),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = XikeShapes.inner,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                ) {
                    Column(Modifier.padding(XikeInnerCardPadding)) {
                        Text(entry.mood.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            entry.createdAt.asDetailChineseDateTime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    if (
                        entry.imageFileNames.isEmpty() && entry.audio == null && entry.video == null
                    ) {
                        localizedText("删除后可在底部提示消失前撤销。请确认这不是误操作。")
                    } else {
                        val attachments = buildList {
                            if (entry.imageFileNames.isNotEmpty())
                                add(
                                    tr(
                                        "${entry.imageFileNames.size} 张照片",
                                        "${entry.imageFileNames.size} photos",
                                    )
                                )
                            if (entry.audio != null) add(localizedText("1 段语音"))
                            if (entry.video != null) add(tr("1 段视频", "1 video"))
                        }
                            .joinToString(localizedText("和"))
                        tr(
                            "删除后可在底部提示消失前撤销。期限结束后，记录和息刻内保存的${attachments}会一并清理；系统相册中的原图不会受影响。",
                            "You can undo deletion before the message at the bottom disappears. After that, the entry and its ${attachments} in Xike are removed. Originals in the system gallery are unaffected.",
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

private fun List<String>.toggle(value: String): List<String> =
    if (value in this) this - value else this + value

private fun Long.localDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

/** Only visible rows hold a fallback record while a dropped Paging page is reloaded. */
@Composable
internal fun ArchiveReadEntry(
    id: String,
    paged: JournalEntry?,
    repository: JournalReadRepository?,
    revision: Long,
    heights: MutableMap<String, Int>,
    onReadError: (String) -> Unit = {},
    content: @Composable (JournalEntry) -> Unit,
) {
    val loaded by
        produceState(paged, id, revision) {
            if (paged == null && repository != null) {
                try {
                    value = repository.entry(id)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    onReadError(localizedText("日记数据库暂时无法读取，原数据未被覆盖。"))
                }
            }
        }
    val entry = paged ?: loaded
    Box(Modifier.fillMaxWidth().onSizeChanged { if (entry != null) heights[id] = it.height }) {
        if (entry != null) content(entry)
        else
            Spacer(
                Modifier.height(
                    heights[id]?.let { with(LocalDensity.current) { it.toDp() } } ?: 160.dp
                )
            )
    }
}
