package com.xike.app

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ARCHIVE_PAGE_SIZE = 60
private enum class ArchiveViewMode { CALENDAR, TIMELINE }

private enum class ArchiveDatePreset(val label: String) {
    ALL("全部日期"),
    LAST_7_DAYS("近 7 天"),
    LAST_30_DAYS("近 30 天"),
    THIS_YEAR("今年");

    fun range(today: LocalDate): Pair<LocalDate?, LocalDate?> = when (this) {
        ALL -> null to null
        LAST_7_DAYS -> today.minusDays(6) to today
        LAST_30_DAYS -> today.minusDays(29) to today
        THIS_YEAR -> today.withDayOfYear(1) to today
    }
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
    var viewModeName by rememberSaveable { mutableStateOf(ArchiveViewMode.CALENDAR.name) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var resultEntries by remember { mutableStateOf(entries) }
    var totalResultCount by remember { mutableIntStateOf(entries.size) }
    var hasMoreResults by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var galleryImages by remember { mutableStateOf<List<String>?>(null) }
    var galleryInitialPage by remember { mutableIntStateOf(0) }
    var detailEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var editEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var pendingScrollPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var deleteCandidate by remember { mutableStateOf<JournalEntry?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val selectedMoods = remember(selectedMoodNames) {
        selectedMoodNames.mapNotNull { name -> Mood.entries.firstOrNull { it.name == name } }.toSet()
    }
    val imageFilter = JournalImageFilter.entries.firstOrNull { it.name == imageFilterName }
        ?: JournalImageFilter.ANY
    val selectedDate = selectedDateValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val datePreset = ArchiveDatePreset.entries.firstOrNull { it.name == datePresetName }
        ?: ArchiveDatePreset.ALL
    val presetRange = datePreset.range(today)
    val searchQuery = remember(
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

    LaunchedEffect(entries, searchQuery) {
        if (searchQuery.normalizedText.isNotEmpty()) delay(220)
        isSearching = true
        isLoadingMore = false
        onSearch(searchQuery, 0, ARCHIVE_PAGE_SIZE)
            .onSuccess { page ->
                resultEntries = page.entries
                totalResultCount = page.totalCount
                hasMoreResults = page.hasMore
                searchError = null
            }
            .onFailure { error ->
                val fallback = filterJournalEntries(entries, searchQuery)
                resultEntries = fallback
                totalResultCount = fallback.size
                hasMoreResults = false
                searchError = error.message ?: "搜索暂时不可用"
            }
        isSearching = false
        pendingScrollPosition?.let { (index, offset) ->
            withFrameNanos { }
            archiveListState.scrollToItem(index, offset)
            pendingScrollPosition = null
        }
    }

    val groupedResults = remember(resultEntries) {
        resultEntries.groupBy { it.localDate() }.toList().sortedByDescending { it.first }
    }
    val availableTags = remember(entries) {
        entries.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }
    val calendarQuery = remember(searchQuery) {
        searchQuery.copy(startDate = null, endDate = null)
    }
    val calendarEntries = remember(entries, calendarQuery) {
        filterJournalEntries(entries, calendarQuery)
    }
    val calendarEntriesByDate = remember(calendarEntries) {
        calendarEntries.groupBy { it.localDate() }
    }
    val visibleMonth = runCatching { YearMonth.parse(visibleMonthValue) }.getOrDefault(YearMonth.from(today))
    val viewMode = ArchiveViewMode.entries.firstOrNull { it.name == viewModeName } ?: ArchiveViewMode.CALENDAR

    LaunchedEffect(entries, detailEntry?.id) {
        val selectedId = detailEntry?.id ?: return@LaunchedEffect
        detailEntry = entries.firstOrNull { it.id == selectedId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = archiveListState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item(key = "archive-header") {
            ScreenHeader(
                eyebrow = if (searchQuery.isEmpty) "${entries.size} 条记录" else "找到 $totalResultCount 条",
                title = "回望",
                supporting = "沿着日期、内在天气和关键词，找回过去的自己。",
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

        if (showFilters) {
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
                    onToggleTag = { tag -> selectedTags = selectedTags.toggle(tag) },
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
                    entriesByDate = calendarEntriesByDate,
                    onPreviousMonth = { visibleMonthValue = visibleMonth.minusMonths(1).toString() },
                    onNextMonth = { visibleMonthValue = visibleMonth.plusMonths(1).toString() },
                    onSelectDate = { date ->
                        pendingScrollPosition = archiveListState.firstVisibleItemIndex to
                            archiveListState.firstVisibleItemScrollOffset
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
                libraryCount = entries.size,
                isSearching = isSearching,
                searchError = searchError,
                selectedDate = selectedDate,
            )
        }

        if (entries.isEmpty()) {
            item(key = "archive-empty") {
                ArchiveEmptyState(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "这里还很安静",
                    description = "第一条不必完整，选一种内在天气就够了。",
                )
            }
        } else if (resultEntries.isEmpty() && !isSearching) {
            item(key = "archive-no-results") {
                ArchiveEmptyState(
                    icon = Icons.Outlined.Search,
                    title = "没有找到这一刻",
                    description = "试试减少筛选条件，或换一个关键词。",
                )
            }
        } else {
            groupedResults.forEach { (date, dayEntries) ->
                item(key = "date-$date") { DateSectionHeader(date, dayEntries.size) }
                items(dayEntries, key = { entry -> "entry-${entry.id}" }) { entry ->
                    ArchiveTimelineEntry(
                        entry = entry,
                        openImage = openImage,
                        onImageClick = { index ->
                            galleryImages = entry.imageFileNames
                            galleryInitialPage = index
                        },
                        onClick = { detailEntry = entry },
                    )
                }
            }
            if (hasMoreResults) {
                item(key = "archive-load-more") {
                    TextButton(
                        enabled = !isLoadingMore,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (isLoadingMore) return@TextButton
                            isLoadingMore = true
                            scope.launch {
                                onSearch(searchQuery, resultEntries.size, ARCHIVE_PAGE_SIZE)
                                    .onSuccess { page ->
                                        resultEntries = (resultEntries + page.entries).distinctBy { it.id }
                                        totalResultCount = page.totalCount
                                        hasMoreResults = page.hasMore
                                        searchError = null
                                    }
                                    .onFailure { error ->
                                        searchError = error.message ?: "更多记录暂时无法读取"
                                    }
                                isLoadingMore = false
                            }
                        },
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isLoadingMore) "正在读取…" else "加载更多记录")
                    }
                }
            }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
            onDismiss = { editEntry = null },
            onSave = { updatedEntry, retainedImages, newImageUris ->
                onUpdate(updatedEntry, retainedImages, newImageUris).onSuccess { savedEntry ->
                    detailEntry = savedEntry
                    editEntry = null
                }
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
                            deleteError = error.message ?: "删除失败，请重试。"
                        }
                        if (result.isSuccess) {
                            if (detailEntry?.id == entry.id) detailEntry = null
                            if (editEntry?.id == entry.id) editEntry = null
                            deleteCandidate = null
                            var deleteWasUndone = false
                            try {
                                val snackbarResult = snackbarHostState.showSnackbar(
                                    message = "记录已删除",
                                    actionLabel = "撤销",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long,
                                )
                                if (snackbarResult == SnackbarResult.ActionPerformed) {
                                    onUndoDelete(entry.id)
                                        .onSuccess {
                                            deleteWasUndone = true
                                            Toast.makeText(context, "记录已恢复", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                error.message ?: "撤销删除失败",
                                                Toast.LENGTH_LONG,
                                            ).show()
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
private fun ArchiveToolbox(
    queryText: String,
    viewMode: ArchiveViewMode,
    activeFilterCount: Int,
    showFilters: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onViewModeChange: (ArchiveViewMode) -> Unit,
    onToggleFilters: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArchiveSearchBar(
                value = queryText,
                onValueChange = onQueryChange,
                onClear = onClearQuery,
            )
            ArchiveControls(
                viewMode = viewMode,
                activeFilterCount = activeFilterCount,
                showFilters = showFilters,
                onViewModeChange = onViewModeChange,
                onToggleFilters = onToggleFilters,
            )
        }
    }
}

@Composable
private fun ArchiveSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = XikeShapes.inner,
        placeholder = { Text("搜索注脚或关键词") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ArchiveControls(
    viewMode: ArchiveViewMode,
    activeFilterCount: Int,
    showFilters: Boolean,
    onViewModeChange: (ArchiveViewMode) -> Unit,
    onToggleFilters: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        ) {
            Row(
                Modifier.padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ArchiveModeButton(
                    label = "月历",
                    icon = Icons.Outlined.CalendarMonth,
                    selected = viewMode == ArchiveViewMode.CALENDAR,
                    modifier = Modifier.weight(1f),
                    onClick = { onViewModeChange(ArchiveViewMode.CALENDAR) },
                )
                ArchiveModeButton(
                    label = "时间流",
                    icon = Icons.Outlined.ViewAgenda,
                    selected = viewMode == ArchiveViewMode.TIMELINE,
                    modifier = Modifier.weight(1f),
                    onClick = { onViewModeChange(ArchiveViewMode.TIMELINE) },
                )
            }
        }
        Surface(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(onClick = onToggleFilters),
            shape = RoundedCornerShape(18.dp),
            color = if (showFilters || activeFilterCount > 0) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = if (activeFilterCount == 0) {
                        "打开筛选"
                    } else {
                        "筛选，已启用 $activeFilterCount 个条件"
                    },
                    modifier = Modifier.size(18.dp),
                    tint = if (showFilters || activeFilterCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeFilterCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        activeFilterCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .sizeIn(minHeight = 40.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArchiveFilters(
    selectedMoodNames: List<String>,
    availableTags: List<String>,
    selectedTags: List<String>,
    imageFilter: JournalImageFilter,
    datePreset: ArchiveDatePreset,
    selectedDate: LocalDate?,
    onToggleMood: (Mood) -> Unit,
    onToggleTag: (String) -> Unit,
    onImageFilterChange: (JournalImageFilter) -> Unit,
    onDatePresetChange: (ArchiveDatePreset) -> Unit,
    onClearDate: () -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("筛选这段记忆", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "条件可以叠加使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClearAll) { Text("全部清除") }
            }
            FilterTitle("内在天气")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Mood.entries, key = Mood::name) { mood ->
                    FilterChip(
                        selected = mood.name in selectedMoodNames,
                        onClick = { onToggleMood(mood) },
                        label = { Text(mood.label) },
                        leadingIcon = {
                            Icon(mood.weatherIcon(), contentDescription = null, modifier = Modifier.size(17.dp))
                        },
                    )
                }
            }

            if (availableTags.isNotEmpty()) {
                FilterTitle("此刻关键词")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(availableTags, key = { it }) { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = { onToggleTag(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            FilterTitle("日期")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedDate != null) {
                    item(key = "selected-day") {
                        FilterChip(
                            selected = true,
                            onClick = onClearDate,
                            label = { Text(selectedDate.asShortChineseDate()) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "清除指定日期", Modifier.size(17.dp)) },
                        )
                    }
                }
                items(ArchiveDatePreset.entries, key = ArchiveDatePreset::name) { preset ->
                    FilterChip(
                        selected = selectedDate == null && datePreset == preset,
                        onClick = { onDatePresetChange(preset) },
                        label = { Text(preset.label) },
                    )
                }
            }

            FilterTitle("照片")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                JournalImageFilter.entries.forEach { option ->
                    FilterChip(
                        selected = imageFilter == option,
                        onClick = { onImageFilterChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun JournalMonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    entriesByDate: Map<LocalDate, List<JournalEntry>>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val monthTitle = month.format(DateTimeFormatter.ofPattern("yyyy年 M月", Locale.CHINA))
    val cells = remember(month) { monthCalendarCells(month) }
    val monthEntries = entriesByDate.filterKeys { YearMonth.from(it) == month }
    val monthEntryCount = monthEntries.values.sumOf(List<JournalEntry>::size)
    Surface(
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    monthTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下个月")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (monthEntryCount == 0) "这个月还没有留下记录" else "${monthEntries.size} 天留下了痕迹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$monthEntryCount 条",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                    Text(
                        weekday,
                        modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f).height(46.dp))
                        } else {
                            val dayEntries = entriesByDate[date].orEmpty()
                            CalendarDay(
                                date = date,
                                count = dayEntries.size,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                enabled = dayEntries.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectDate(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val spokenDate = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))
    Surface(
        modifier = modifier
            .height(46.dp)
            .padding(horizontal = 2.dp)
            .semantics {
                contentDescription = "$spokenDate，${if (count == 0) "没有记录" else "$count 条记录"}"
                selected = isSelected
            }
            .clickable(enabled = enabled, onClick = onClick),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    enabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    else -> Color.Transparent
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Box(
                modifier = Modifier.height(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (count > 0) {
                    Box(
                        Modifier
                            .size(width = if (count > 1) 10.dp else 4.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.tertiary,
                            ),
                    )
                }
            }
        }
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selectedDate?.let { "${it.asShortChineseDate()}的记录" } ?: "时间流",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (selectedDate == null) "从最近的一刻向前走" else "只看这一天留下的片段",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isSearching) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)) {
                    Text(
                        when {
                            shownCount < matchingCount -> "$shownCount / $matchingCount 条"
                            matchingCount == libraryCount -> "$matchingCount 条"
                            else -> "$matchingCount / $libraryCount 条"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (searchError != null) {
                Text(
                    "$searchError，已使用当前页面内容继续筛选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArchiveTimelineEntry(
    entry: JournalEntry,
    openImage: (String) -> InputStream?,
    onImageClick: (Int) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        JournalEntryCard(
            entry = entry,
            modifier = Modifier.weight(1f),
            openImage = openImage,
            onImageClick = onImageClick,
            onClick = onClick,
        )
    }
}

@Composable
private fun ArchiveEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(horizontal = 26.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(modifier = Modifier.size(58.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(7.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun JournalEntryDetailDialog(
    entry: JournalEntry,
    onDismiss: () -> Unit,
    onRequestEdit: (() -> Unit)? = null,
    onRequestDelete: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("这一刻", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    if (onRequestEdit != null) {
                        IconButton(onClick = onRequestEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑记录")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭记录详情")
                    }
                }

                Surface(shape = XikeShapes.card, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = XikeShapes.inner,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    entry.mood.weatherIcon(),
                                    contentDescription = entry.mood.label,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(entry.mood.label, style = MaterialTheme.typography.titleLarge)
                            Text(
                                entry.createdAt.asDetailChineseDateTime(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (entry.tags.isNotEmpty()) {
                    Text("此刻关键词", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(entry.tags.joinToString("  ·  "), style = MaterialTheme.typography.bodyLarge)
                }

                Text("记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    entry.note.ifBlank { "这一刻只留下了一种内在天气。" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )

                if (entry.imageFileNames.isNotEmpty()) {
                    Surface(shape = XikeShapes.inner, color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("包含 ${entry.imageFileNames.size} 张照片，可在记录卡片中点按查看。")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (onRequestDelete == null) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        elevation = xikeButtonElevation(),
                    ) { Text("关闭") }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            onClick = onRequestDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("删除记录")
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            elevation = xikeButtonElevation(),
                        ) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEntryEditDialog(
    entry: JournalEntry,
    openImage: (String) -> InputStream?,
    onDismiss: () -> Unit,
    onSave: suspend (JournalEntry, List<String>, List<Uri>) -> Result<JournalEntry>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMoodName by rememberSaveable(entry.id) { mutableStateOf(entry.mood.name) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    var selectedTags by rememberSaveable(entry.id) { mutableStateOf(entry.tags) }
    var createdAt by rememberSaveable(entry.id) { mutableStateOf(entry.createdAt) }
    var retainedImages by rememberSaveable(entry.id) { mutableStateOf(entry.imageFileNames) }
    var newImageUriStrings by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val selectedMood = Mood.entries.firstOrNull { it.name == selectedMoodName } ?: entry.mood
    val availableImageSlots = MAX_IMAGES_PER_ENTRY - retainedImages.size - newImageUriStrings.size
    val onImagesPicked: (List<Uri>) -> Unit = { uris ->
        val additions = uris
            .map(Uri::toString)
            .filterNot { it in newImageUriStrings }
            .take(availableImageSlots.coerceAtLeast(0))
        newImageUriStrings = newImageUriStrings + additions
        if (additions.size < uris.distinct().size) {
            Toast.makeText(context, "每条记录最多保留 $MAX_IMAGES_PER_ENTRY 张照片", Toast.LENGTH_SHORT).show()
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_ENTRY),
        onImagesPicked,
    )
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        onImagesPicked,
    )
    val openPhotoPicker = {
        if (availableImageSlots > 0 && !isSaving) {
            val openDocuments = {
                val input = arrayOf("image/*")
                val contract = ActivityResultContracts.OpenMultipleDocuments()
                val canOpen = contract.createIntent(context, input)
                    .resolveActivity(context.packageManager) != null
                if (canOpen) documentPicker.launch(input)
                canOpen
            }
            val opened = if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                runCatching {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    true
                }.onFailure { error ->
                    Log.w("XikeEditPhotoPicker", "Photo picker launch failed", error)
                }.getOrElse {
                    runCatching(openDocuments).getOrDefault(false)
                }
            } else {
                runCatching(openDocuments).getOrDefault(false)
            }
            if (!opened) Toast.makeText(context, "无法打开系统照片选择器", Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(enabled = !isSaving, onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "取消编辑")
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("编辑这一刻", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "修改会同步更新回望、搜索与轨迹",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        enabled = !isSaving,
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            saveError = null
                            val updatedEntry = entry.copy(
                                createdAt = createdAt,
                                mood = selectedMood,
                                tags = selectedTags,
                                note = note.trim(),
                            )
                            scope.launch {
                                onSave(
                                    updatedEntry,
                                    retainedImages,
                                    newImageUriStrings.map(Uri::parse),
                                ).onSuccess {
                                    Toast.makeText(context, "修改已保存", Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    saveError = error.message ?: "修改保存失败，请重试。"
                                }
                                isSaving = false
                            }
                        },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(if (isSaving) "保存中" else "保存")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                EditSectionCard(title = "内在天气", supporting = "重新选择最接近那一刻的天气") {
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Mood.entries.forEach { mood ->
                            val selected = mood == selectedMood
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .selectable(
                                        selected = selected,
                                        enabled = !isSaving,
                                        role = Role.RadioButton,
                                        onClick = { selectedMoodName = mood.name },
                                    ),
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else Color.Transparent,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        mood.weatherIcon(),
                                        contentDescription = mood.label,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(mood.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                EditSectionCard(title = "记录时间", supporting = "可修正补记日期与具体时间") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !isSaving) {
                            showRecordedAtPicker(context, createdAt) { createdAt = it }
                        },
                        shape = XikeShapes.inner,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(11.dp))
                            Text(
                                createdAt.asDetailChineseDateTime(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                EditSectionCard(title = "此刻的注脚", supporting = "最多 $MAX_DRAFT_NOTE_LENGTH 字") {
                    TextField(
                        value = note,
                        onValueChange = { note = it.take(MAX_DRAFT_NOTE_LENGTH) },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        placeholder = { Text("这一刻发生了什么？") },
                        shape = XikeShapes.inner,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Text(
                        "${note.length} / $MAX_DRAFT_NOTE_LENGTH",
                        modifier = Modifier.align(Alignment.End),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                EditSectionCard(
                    title = "此刻关键词",
                    supporting = if (selectedTags.isEmpty()) "可多选" else "已选 ${selectedTags.size}",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        journalTopics.chunked(4).forEach { rowTopics ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                rowTopics.forEach { topic ->
                                    TopicChip(
                                        topic = topic,
                                        selected = topic.label in selectedTags,
                                        onClick = {
                                            if (!isSaving) selectedTags = selectedTags.toggle(topic.label)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                EditSectionCard(
                    title = "照片",
                    supporting = "${retainedImages.size + newImageUriStrings.size} / $MAX_IMAGES_PER_ENTRY · 移除只影响息刻副本",
                ) {
                    EditPhotoStrip(
                        retainedImages = retainedImages,
                        newImageUriStrings = newImageUriStrings,
                        canAdd = availableImageSlots > 0 && !isSaving,
                        openImage = openImage,
                        onAdd = openPhotoPicker,
                        onRemoveRetained = { retainedImages = retainedImages - it },
                        onRemoveNew = { newImageUriStrings = newImageUriStrings - it },
                    )
                }

                saveError?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = XikeShapes.inner,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            error,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun EditSectionCard(
    title: String,
    supporting: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun EditPhotoStrip(
    retainedImages: List<String>,
    newImageUriStrings: List<String>,
    canAdd: Boolean,
    openImage: (String) -> InputStream?,
    onAdd: () -> Unit,
    onRemoveRetained: (String) -> Unit,
    onRemoveNew: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        retainedImages.forEachIndexed { index, fileName ->
            item(key = "existing-$fileName") {
                EditPhotoTile(
                    key = "existing-$fileName",
                    contentDescription = "已有照片 ${index + 1}",
                    openStream = { openImage(fileName) },
                    onRemove = { onRemoveRetained(fileName) },
                )
            }
        }
        newImageUriStrings.forEachIndexed { index, uriString ->
            item(key = "new-$uriString") {
                EditPhotoTile(
                    key = "new-$uriString",
                    contentDescription = "新照片 ${index + 1}",
                    openStream = { context.contentResolver.openInputStream(Uri.parse(uriString)) },
                    onRemove = { onRemoveNew(uriString) },
                )
            }
        }
        if (canAdd) {
            item(key = "add-photo") {
                Surface(
                    modifier = Modifier.size(92.dp).clickable(onClick = onAdd),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("添加照片", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPhotoTile(
    key: String,
    contentDescription: String,
    openStream: () -> InputStream?,
    onRemove: () -> Unit,
) {
    val bitmap = rememberPreviewBitmap(key = key, maxDimension = 360, openStream = openStream)
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = contentDescription,
                modifier = Modifier.align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.66f),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "移除$contentDescription",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White,
                )
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        icon = {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        title = { Text("删除这条记录？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = XikeShapes.inner,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(entry.mood.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            entry.createdAt.asDetailChineseDateTime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    if (entry.imageFileNames.isEmpty()) {
                        "删除后可在底部提示消失前撤销。请确认这不是误操作。"
                    } else {
                        "删除后可在底部提示消失前撤销。期限结束后，记录和息刻内保存的 ${entry.imageFileNames.size} 张照片副本会一并清理；系统相册中的原图不会受影响。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isDeleting, onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            TextButton(
                enabled = !isDeleting,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("正在删除…")
                } else {
                    Text("确认删除")
                }
            }
        },
    )
}

private fun List<String>.toggle(value: String): List<String> = if (value in this) this - value else this + value

private fun JournalEntry.localDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate()

private fun LocalDate.asShortChineseDate(): String =
    format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))

private fun Long.asDetailChineseDateTime(): String =
    DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE · HH:mm", Locale.CHINA)
        .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
