package com.xike.app

import android.Manifest
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ARCHIVE_PAGE_SIZE = 60
internal enum class ArchiveViewMode { CALENDAR, TIMELINE }

private enum class ArchiveDatePreset(private val sourceLabel: String) {
    ALL("全部日期"),
    LAST_7_DAYS("近 7 天"),
    LAST_30_DAYS("近 30 天"),
    THIS_YEAR("今年");

    val label: String get() = localizedText(sourceLabel)

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
    openAudio: (String) -> InputStream? = { null },
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
    var scrollToSelectedDateResults by remember { mutableStateOf(false) }
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
                searchError = error.message ?: localizedText("搜索暂时不可用")
            }
        isSearching = false
        if (scrollToSelectedDateResults) {
            withFrameNanos { }
            archiveListState.animateScrollToItem(
                2 + (if (showFilters) 1 else 0) + (if (viewModeName == ArchiveViewMode.CALENDAR.name) 1 else 0),
            )
            scrollToSelectedDateResults = false
        }
    }

    val groupedResults = remember(resultEntries) {
        resultEntries.groupBy { it.localDate() }.toList().sortedByDescending { it.first }
    }
    val availableTags = remember(entries) {
        entries.flatMap { it.tags.canonicalTopics() }
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
            modifier = Modifier
                .widthIn(max = XikeContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = XikeScreenHorizontalPadding,
                vertical = XikeScreenVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(XikeContentGap),
        ) {
        item(key = "archive-header") {
            ArchiveHeader(
                recordLabel = if (searchQuery.isEmpty) tr("${entries.size} 条记录", "${entries.size} entries") else tr("找到 $totalResultCount 条", "$totalResultCount entries found"),
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
                    entriesByDate = calendarEntriesByDate,
                    onPreviousMonth = { visibleMonthValue = visibleMonth.minusMonths(1).toString() },
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
                    title = localizedText("这里还很安静"),
                    description = localizedText("第一条不必完整，选一种心情就够了。"),
                )
            }
        } else if (resultEntries.isEmpty() && !isSearching) {
            item(key = "archive-no-results") {
                ArchiveEmptyState(
                    icon = Icons.Outlined.Search,
                    title = localizedText("没有找到这一刻"),
                    description = localizedText("试试减少筛选条件，或换一个主题。"),
                )
            }
        } else {
            groupedResults.forEach { (date, dayEntries) ->
                item(key = "date-$date") { ArchiveDateHeader(date, dayEntries.size) }
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
                        shape = XikeShapes.button,
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
                                        searchError = error.message ?: localizedText("更多记录暂时无法读取")
                                    }
                                isLoadingMore = false
                            }
                        },
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isLoadingMore) localizedText("正在读取…") else localizedText("加载更多记录"))
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
                            deleteError = error.message ?: localizedText("删除失败，请重试。")
                        }
                        if (result.isSuccess) {
                            if (detailEntry?.id == entry.id) detailEntry = null
                            if (editEntry?.id == entry.id) editEntry = null
                            deleteCandidate = null
                            var deleteWasUndone = false
                            try {
                                val snackbarResult = snackbarHostState.showSnackbar(
                                    message = localizedText("记录已删除"),
                                    actionLabel = localizedText("撤销"),
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long,
                                )
                                if (snackbarResult == SnackbarResult.ActionPerformed) {
                                    onUndoDelete(entry.id)
                                        .onSuccess {
                                            deleteWasUndone = true
                                            Toast.makeText(context, localizedText("记录已恢复"), Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                error.message ?: localizedText("撤销删除失败"),
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
internal fun ArchiveHeader(recordLabel: String) {
    val textMeasurer = rememberTextMeasurer()
    val title = localizedText("回望")
    val titleStyle = XikePageTitleStyle
    val countStyle = MaterialTheme.typography.labelSmall
    val requiredWidth = with(LocalDensity.current) {
        (textMeasurer.measure(title, titleStyle, softWrap = false).size.width +
            textMeasurer.measure(recordLabel, countStyle, softWrap = false).size.width).toDp()
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp),
    ) {
        Text(tr("值得记住的时刻", "MOMENTS TO REMEMBER"), style = XikeEyebrowStyle, color = MaterialTheme.colorScheme.primary)
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ArchiveSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("archive-search")
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), XikeShapes.inner),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                shape = XikeShapes.inner,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        localizedText("搜索注脚或主题"),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                prefix = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Outlined.Close, contentDescription = localizedText("清空搜索"), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
    )
}

@Composable
internal fun ArchiveControls(
    viewMode: ArchiveViewMode,
    activeFilterCount: Int,
    showFilters: Boolean,
    onViewModeChange: (ArchiveViewMode) -> Unit,
    onToggleFilters: () -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val labelWidthPixels = maxOf(
        textMeasurer.measure(localizedText("月历"), labelStyle, softWrap = false).size.width,
        textMeasurer.measure(localizedText("时间流"), labelStyle, softWrap = false).size.width,
    )
    val minimumModesWidth = with(density) {
        // Match each padding/gap's pixel rounding when reserving single-line label space.
        ((labelWidthPixels + 4.dp.roundToPx() * 2) * 2 +
            4.dp.roundToPx() * 2 + 3.dp.roundToPx()).toDp()
    }
    val filterWidth = if (activeFilterCount == 0) 48.dp else {
        maxOf(48.dp, 40.dp + with(density) {
            textMeasurer.measure(activeFilterCount.toString(), MaterialTheme.typography.labelSmall, softWrap = false).size.width.toDp()
        })
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < minimumModesWidth + filterWidth + 6.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArchiveModeGroup(viewMode, minimumModesWidth, onViewModeChange, Modifier.fillMaxWidth())
                ArchiveFilterToggle(activeFilterCount, showFilters, onToggleFilters, Modifier.align(Alignment.End))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ArchiveModeGroup(viewMode, minimumModesWidth, onViewModeChange, Modifier.weight(1f))
                ArchiveFilterToggle(activeFilterCount, showFilters, onToggleFilters)
            }
        }
    }
}

@Composable
private fun ArchiveModeGroup(
    viewMode: ArchiveViewMode,
    minimumWidth: Dp,
    onViewModeChange: (ArchiveViewMode) -> Unit,
    modifier: Modifier,
) {
    val selectedItemRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    Surface(modifier = modifier, shape = XikeShapes.inner, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints {
            LaunchedEffect(viewMode, maxWidth, minimumWidth, density) {
                withFrameNanos { }
                selectedItemRequester.bringIntoView()
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .width(maxOf(maxWidth, minimumWidth)).padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ArchiveModeButton(
                    label = localizedText("月历"),
                    icon = Icons.Outlined.CalendarMonth,
                    selected = viewMode == ArchiveViewMode.CALENDAR,
                    modifier = Modifier.weight(1f).testTag("archive-mode-calendar").then(
                        if (viewMode == ArchiveViewMode.CALENDAR) Modifier.bringIntoViewRequester(selectedItemRequester) else Modifier,
                    ),
                    onClick = { onViewModeChange(ArchiveViewMode.CALENDAR) },
                )
                ArchiveModeButton(
                    label = localizedText("时间流"),
                    icon = Icons.Outlined.ViewAgenda,
                    selected = viewMode == ArchiveViewMode.TIMELINE,
                    modifier = Modifier.weight(1f).testTag("archive-mode-timeline").then(
                        if (viewMode == ArchiveViewMode.TIMELINE) Modifier.bringIntoViewRequester(selectedItemRequester) else Modifier,
                    ),
                    onClick = { onViewModeChange(ArchiveViewMode.TIMELINE) },
                )
            }
        }
    }
}

@Composable
private fun ArchiveFilterToggle(
    activeFilterCount: Int,
    showFilters: Boolean,
    onToggleFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onToggleFilters),
        shape = XikeShapes.inner,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        color = if (showFilters || activeFilterCount > 0) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = if (activeFilterCount == 0) {
                    localizedText("打开筛选")
                } else {
                    tr("筛选，已启用 $activeFilterCount 个条件", "Filters: $activeFilterCount active")
                },
                modifier = Modifier.size(18.dp),
                tint = if (showFilters || activeFilterCount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (activeFilterCount > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    activeFilterCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
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
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val labelWidth = with(LocalDensity.current) {
        maxOf(
            textMeasurer.measure(localizedText("月历"), labelStyle, softWrap = false).size.width,
            textMeasurer.measure(localizedText("时间流"), labelStyle, softWrap = false).size.width,
        ).toDp()
    }
    Surface(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        shape = XikeShapes.button,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        BoxWithConstraints(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val iconContent: @Composable () -> Unit = {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val labelContent: @Composable () -> Unit = {
                Text(
                    label,
                    style = labelStyle,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (maxWidth < labelWidth + 22.dp) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    iconContent()
                    labelContent()
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    iconContent()
                    Spacer(Modifier.width(4.dp))
                    labelContent()
                }
            }
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
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = XikeCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = XikeCardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(localizedText("筛选这段记忆"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        localizedText("条件可以叠加使用"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClearAll, shape = XikeShapes.button) { Text(localizedText("全部清除")) }
            }
            FilterTitle(localizedText("心情"))
            LazyRow(
                contentPadding = PaddingValues(horizontal = XikeCardPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Mood.entries, key = Mood::name) { mood ->
                    FilterChip(
                        shape = XikeShapes.inner,
                        selected = mood.name in selectedMoodNames,
                        onClick = { onToggleMood(mood) },
                        label = { Text(mood.label) },
                        leadingIcon = {
                            MoodEmoji(mood, size = 18.dp)
                        },
                    )
                }
            }

            if (availableTags.isNotEmpty()) {
                FilterTitle(localizedText("主题"))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = XikeCardPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(availableTags, key = { it }) { tag ->
                        FilterChip(
                            shape = XikeShapes.inner,
                            selected = selectedTags.containsTopic(tag),
                            onClick = { onToggleTag(tag) },
                            label = { Text(localizedText(tag)) },
                        )
                    }
                }
            }

            FilterTitle(localizedText("日期"))
            LazyRow(
                contentPadding = PaddingValues(horizontal = XikeCardPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedDate != null) {
                    item(key = "selected-day") {
                        FilterChip(
                            shape = XikeShapes.inner,
                            selected = true,
                            onClick = onClearDate,
                            label = { Text(selectedDate.asShortChineseDate()) },
                            trailingIcon = { Icon(Icons.Outlined.Close, localizedText("清除指定日期"), Modifier.size(18.dp)) },
                        )
                    }
                }
                items(ArchiveDatePreset.entries, key = ArchiveDatePreset::name) { preset ->
                    FilterChip(
                        shape = XikeShapes.inner,
                        selected = selectedDate == null && datePreset == preset,
                        onClick = { onDatePresetChange(preset) },
                        label = { Text(preset.label) },
                    )
                }
            }

            FilterTitle(localizedText("照片"))
            LazyRow(
                contentPadding = PaddingValues(horizontal = XikeCardPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(JournalImageFilter.entries, key = JournalImageFilter::name) { option ->
                    FilterChip(
                        shape = XikeShapes.inner,
                        selected = imageFilter == option,
                        onClick = { onImageFilterChange(option) },
                        label = { Text(option.label, maxLines = 1) },
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
        modifier = Modifier.padding(horizontal = XikeCardPadding),
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
    val monthTitle = month.format(DateTimeFormatter.ofPattern(tr("yyyy年 M月", "MMMM yyyy"), AppLocale.locale))
    val cells = remember(month) { monthCalendarCells(month) }
    val monthEntries = entriesByDate.filterKeys { YearMonth.from(it) == month }
    val monthEntryCount = monthEntries.values.sumOf(List<JournalEntry>::size)
    val dayDiameter = 32.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val dayHeight = dayDiameter + 16.dp
    Surface(
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(XikeCardPadding)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(monthTitle, style = XikeSectionTitleStyle)
                    Text(
                        if (monthEntryCount == 0) localizedText("这个月还没有留下记录") else tr("${monthEntries.size} 天 · $monthEntryCount 条记录", "${monthEntries.size} days · $monthEntryCount entries"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = XikeShapes.button,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                ) {
                    Row {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = localizedText("上个月"))
                        }
                        IconButton(onClick = onNextMonth) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = localizedText("下个月"))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.horizontalScroll(rememberScrollState())
                        .width(maxOf(maxWidth, (dayDiameter + 4.dp) * 7)),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf(localizedText("一"), localizedText("二"), localizedText("三"), localizedText("四"), localizedText("五"), localizedText("六"), localizedText("日")).forEach { weekday ->
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
                                    Spacer(Modifier.weight(1f).height(dayHeight))
                                } else {
                                    val dayEntries = entriesByDate[date].orEmpty()
                                    CalendarDay(
                                        date = date,
                                        count = dayEntries.size,
                                        isToday = date == today,
                                        isSelected = date == selectedDate,
                                        enabled = dayEntries.isNotEmpty(),
                                        diameter = dayDiameter,
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
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    diameter: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val spokenDate = date.format(DateTimeFormatter.ofPattern(tr("yyyy年M月d日 EEEE", "EEEE, MMM d, yyyy"), AppLocale.locale))
    Surface(
        modifier = modifier
            .height(diameter + 16.dp)
            .padding(horizontal = 2.dp)
            .semantics {
                contentDescription = "$spokenDate，${if (count == 0) localizedText("没有记录") else tr("$count 条记录", "$count entries")}"
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
                modifier = Modifier.size(diameter),
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
                        maxLines = 1,
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
                                if (isSelected) MaterialTheme.colorScheme.primary
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    selectedDate?.let { tr("${it.asShortChineseDate()}的记录", "Entries for ${it.asShortChineseDate()}") } ?: localizedText("最近的片段"),
                    style = XikeSectionTitleStyle,
                )
                Text(
                    if (selectedDate == null) localizedText("按时间从近到远") else localizedText("只看这一天留下的片段"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSearching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)) {
                Text(
                    when {
                        shownCount < matchingCount -> tr("$shownCount / $matchingCount 条", "$shownCount / $matchingCount entries")
                        matchingCount == libraryCount -> tr("$matchingCount 条", "$matchingCount entries")
                        else -> tr("$matchingCount / $libraryCount 条", "$matchingCount / $libraryCount entries")
                    },
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (searchError != null) {
            Text(
                tr("$searchError，已使用当前页面内容继续筛选。", "$searchError. Filtering continues using the entries on this page."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        openImage = openImage,
        onImageClick = onImageClick,
        onClick = onClick,
    )
}

@Composable
private fun ArchiveEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(XikeCardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(modifier = Modifier.size(58.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = XikeSectionTitleStyle)
            Spacer(Modifier.height(7.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = XikeScreenHorizontalPadding, vertical = XikeScreenVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(XikeContentGap),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Column {
                            Text(entry.mood.label, style = MaterialTheme.typography.titleLarge)
                            Text(
                                entry.createdAt.asDetailChineseDateTime(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                            )
                        }
                    }
                }

                if (entry.tags.isNotEmpty()) {
                    Text(localizedText("主题"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(entry.tags.joinToString("  ·  ") { localizedText(it) }, style = MaterialTheme.typography.bodyLarge)
                }

                entry.outdoor?.let { outdoor ->
                    Surface(shape = XikeShapes.inner, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(XikeInnerCardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr("此刻窗外 · ${outdoor.placeName}", "Outside now · ${outdoor.placeName}"), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${outdoor.temperatureCelsius.roundToInt()}° · ${weatherConditionLabel(outdoor.weatherCode)} · ${outdoor.source}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Text(localizedText("记录"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    entry.note.ifBlank {
                        when {
                            entry.audio != null -> localizedText("这一刻还留下了一段声音。")
                            entry.video != null -> tr("这一刻还留下了一段视频。", "A video was kept from this moment.")
                            entry.imageFileNames.isNotEmpty() -> tr("这一刻还留下了照片。", "Photos were kept from this moment.")
                            else -> localizedText("这一刻只留下了一种心情。")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )

                entry.audio?.let { audio ->
                    Text(localizedText("这一刻的声音"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    VoicePlaybackCard(audio = audio, openAudio = openAudio)
                }
                entry.video?.let { VideoCard(it) }

                if (entry.imageFileNames.isNotEmpty()) {
                    Text(tr("照片 · ${entry.imageFileNames.size} 张", "Photos · ${entry.imageFileNames.size}"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Surface(shape = XikeShapes.inner, color = MaterialTheme.colorScheme.surface) {
                        JournalPhotoMosaic(entry.imageFileNames, openImage) { photoPage = it }
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
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.xikeInlineActionIcon())
                            Spacer(Modifier.width(XikeInlineActionGap))
                            Text(localizedText("删除记录"), maxLines = 1)
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
private fun JournalEntryEditDialog(
    entry: JournalEntry,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
    onDismiss: () -> Unit,
    onSave: suspend (JournalEntry, List<String>, List<Uri>) -> Result<JournalEntry>,
) {
    val context = LocalContext.current
    val systemActivityCallbacks = LocalSystemActivityCallbacks.current
    val scope = rememberCoroutineScope()
    var selectedMoodName by rememberSaveable(entry.id) { mutableStateOf(entry.mood.name) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    var selectedTags by rememberSaveable(entry.id) { mutableStateOf(entry.tags) }
    var retainedAudio by remember(entry.id) { mutableStateOf(entry.audio) }
    val videoServices = LocalVideoServices.current
    var retainedVideoJson by rememberSaveable(entry.id) {
        val recovered = videoServices.editDraft(entry)
        mutableStateOf((if (recovered != null) recovered.video else entry.video)?.toJson()?.toString())
    }
    val retainedVideo = retainedVideoJson?.let { JournalVideo.fromJson(org.json.JSONObject(it)) }
    var isVideoImporting by remember { mutableStateOf(false) }
    var videoProgress by remember { mutableStateOf<Float?>(null) }
    val importedVideos = remember { mutableListOf<JournalVideo>() }
    var createdAt by rememberSaveable(entry.id) { mutableStateOf(entry.createdAt) }
    val pickerContext = LocalRecordedAtPickerContext.current
    var previewPhoto by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    var retainedImages by rememberSaveable(entry.id) { mutableStateOf(entry.imageFileNames) }
    var newImageUriStrings by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirmation by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showPhotoSourceDialog by rememberSaveable(entry.id) { mutableStateOf(false) }
    var pendingCameraUriString by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    val selectedMood = Mood.entries.firstOrNull { it.name == selectedMoodName } ?: entry.mood
    val editedOutdoor = retainOutdoorForEditedTime(entry.outdoor, entry.createdAt, createdAt)
    val removesOutdoor = entry.outdoor != null && editedOutdoor == null
    val availableImageSlots = MAX_IMAGES_PER_ENTRY - retainedImages.size - newImageUriStrings.size
    val onImagesPicked: (List<Uri>) -> Unit = { uris ->
        val additions = uris
            .map(Uri::toString)
            .filterNot { it in newImageUriStrings }
            .take(availableImageSlots.coerceAtLeast(0))
        newImageUriStrings = newImageUriStrings + additions
        if (additions.size < uris.distinct().size) {
            Toast.makeText(context, tr("每条记录最多保留 $MAX_IMAGES_PER_ENTRY 张照片", "Each entry can keep up to $MAX_IMAGES_PER_ENTRY photos"), Toast.LENGTH_SHORT).show()
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_ENTRY),
    ) { uris ->
        systemActivityCallbacks.onResult()
        onImagesPicked(uris)
    }
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        systemActivityCallbacks.onResult()
        onImagesPicked(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        systemActivityCallbacks.onResult()
        val captureUri = pendingCameraUriString?.let(Uri::parse)
        pendingCameraUriString = null
        if (captureUri != null && finalizeCameraCapture(context, captureUri)) {
            onImagesPicked(listOf(captureUri))
            Toast.makeText(context, localizedText("照片已添加并保存到系统相册"), Toast.LENGTH_SHORT).show()
        } else if (captureUri != null) {
            deleteCameraCapture(context, captureUri)
            if (captured) Toast.makeText(context, localizedText("照片保存失败，请重试"), Toast.LENGTH_SHORT).show()
        }
    }
    val openPhotoPicker = {
        if (availableImageSlots > 0 && !isSaving) {
            val openDocuments = {
                val input = arrayOf("image/*")
                val contract = ActivityResultContracts.OpenMultipleDocuments()
                val canOpen = contract.createIntent(context, input)
                    .resolveActivity(context.packageManager) != null
                if (canOpen) {
                    systemActivityCallbacks.onLaunch()
                    runCatching { documentPicker.launch(input) }
                        .onFailure { systemActivityCallbacks.onResult() }
                        .getOrThrow()
                }
                canOpen
            }
            val opened = if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                runCatching {
                    systemActivityCallbacks.onLaunch()
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    true
                }.onFailure { error ->
                    systemActivityCallbacks.onResult()
                    Log.w("XikeEditPhotoPicker", "Photo picker launch failed", error)
                }.getOrElse {
                    runCatching(openDocuments).getOrDefault(false)
                }
            } else {
                runCatching(openDocuments).getOrDefault(false)
            }
            if (!opened) Toast.makeText(context, localizedText("无法打开系统照片选择器"), Toast.LENGTH_LONG).show()
        }
    }
    val launchSystemCamera = {
        if (availableImageSlots > 0 && !isSaving) {
            runCatching { createCameraCaptureUri(context) }
                .onSuccess { captureUri ->
                    pendingCameraUriString = captureUri.toString()
                    systemActivityCallbacks.onLaunch()
                    runCatching { cameraLauncher.launch(captureUri) }
                        .onFailure { error ->
                            systemActivityCallbacks.onResult()
                            pendingCameraUriString = null
                            deleteCameraCapture(context, captureUri)
                            Log.w("XikeEditCamera", "Camera launch failed", error)
                            Toast.makeText(context, localizedText("无法打开系统相机"), Toast.LENGTH_LONG).show()
                        }
                }
                .onFailure { error ->
                    Log.w("XikeEditCamera", "Camera capture file creation failed", error)
                    Toast.makeText(context, localizedText("无法准备拍照，请重试"), Toast.LENGTH_LONG).show()
                }
        }
    }
    val galleryWritePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchSystemCamera()
        } else {
            Toast.makeText(context, localizedText("需要存储权限才能把照片保存到系统相册"), Toast.LENGTH_LONG).show()
        }
    }
    val openCamera = {
        if (hasGalleryWriteAccess(context)) {
            launchSystemCamera()
        } else {
            galleryWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    val hasUnsavedChanges = selectedMood != entry.mood ||
        createdAt != entry.createdAt ||
        note != entry.note ||
        selectedTags != entry.tags ||
        retainedAudio != entry.audio ||
        retainedVideo != entry.video ||
        retainedImages != entry.imageFileNames ||
        newImageUriStrings.isNotEmpty()
    val discardEditor: () -> Unit = {
        scope.launch {
            val cleared = if (entry.video != null || retainedVideo != null || importedVideos.isNotEmpty()) videoServices.clearEdit(entry.id) else Result.success(Unit)
            cleared.onSuccess {
                importedVideos.forEach(videoServices.release)
                retainedVideo?.takeIf { it != entry.video }?.let(videoServices.release)
                pendingCameraUriString?.let(Uri::parse)?.let { deleteCameraCapture(context, it) }
                pendingCameraUriString = null
                onDismiss()
            }.onFailure { saveError = it.message }
        }
    }
    val dismissEditor = {
        if (hasUnsavedChanges) showDiscardConfirmation = true else discardEditor()
    }

    Dialog(
        onDismissRequest = { if (!isSaving && !isVideoImporting) dismissEditor() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier.widthIn(max = XikeContentMaxWidth)
                        .fillMaxWidth().align(Alignment.CenterHorizontally)
                        .padding(horizontal = XikeScreenHorizontalPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(enabled = !isSaving && !isVideoImporting, onClick = dismissEditor) {
                        Icon(Icons.Outlined.Close, contentDescription = localizedText("取消编辑"))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(localizedText("编辑这一刻"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Button(
                        enabled = !isSaving && !isVideoImporting,
                        shape = XikeShapes.button,
                        onClick = {
                            if (isSaving || isVideoImporting) return@Button
                            isSaving = true
                            saveError = null
                            val updatedEntry = entry.copy(
                                createdAt = createdAt,
                                mood = selectedMood,
                                tags = selectedTags,
                                note = note.trim(),
                                audio = retainedAudio,
                                video = retainedVideo,
                                outdoor = editedOutdoor,
                            )
                            val imagesToRetain = retainedImages.toList()
                            val imagesToAdd = newImageUriStrings.map(Uri::parse)
                            scope.launch {
                                onSave(
                                    updatedEntry,
                                    imagesToRetain,
                                    imagesToAdd,
                                ).onSuccess {
                                    videoServices.clearEdit(entry.id)
                                    importedVideos.forEach(videoServices.release)
                                    importedVideos.clear()
                                    Toast.makeText(context, localizedText("修改已保存"), Toast.LENGTH_SHORT).show()
                                }.onFailure { error ->
                                    saveError = error.message ?: localizedText("修改保存失败，请重试。")
                                }
                                isSaving = false
                            }
                        },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(if (isSaving) localizedText("保存中") else localizedText("保存"))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = XikeContentMaxWidth)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = XikeScreenHorizontalPadding, vertical = XikeScreenVerticalPadding),
                    verticalArrangement = Arrangement.spacedBy(XikeContentGap),
                ) {
                EditSectionCard(
                    title = localizedText("心情"),
                ) {
                    MoodChoices(selectedMood, !isSaving, { selectedMoodName = it.name })
                }

                EditSectionCard(title = localizedText("记录时间")) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !isSaving) {
                            showRecordedAtPicker(pickerContext, createdAt) { createdAt = it }
                        },
                        shape = XikeShapes.inner,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                    ) {
                        Row(
                            modifier = Modifier.padding(XikeInnerCardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
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
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (removesOutdoor) {
                        Text(
                            localizedText("日期已经改变，保存时会移除原先的窗外天气，避免误记到另一天。"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                EditSectionCard(
                    title = localizedText("此刻的注脚"),
                    trailing = "${note.length} / $MAX_DRAFT_NOTE_LENGTH",
                    trailingStyle = MaterialTheme.typography.labelSmall,
                ) {
                    TextField(
                        value = note,
                        onValueChange = { note = it.take(MAX_DRAFT_NOTE_LENGTH) },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = localizedText("此刻的注脚") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 3,
                        maxLines = 8,
                        placeholder = { Text(localizedText("这一刻发生了什么？")) },
                        shape = XikeShapes.inner,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }

                EditSectionCard(
                    title = localizedText("主题"),
                    trailing = if (selectedTags.isEmpty()) localizedText("可多选") else tr("已选 ${selectedTags.canonicalTopics().size}", "${selectedTags.canonicalTopics().size} selected"),
                ) {
                    TopicChoices(selectedTags) { topic ->
                        if (!isSaving) selectedTags = toggleTopic(selectedTags, topic.label)
                    }
                }

                EditSectionCard(title = tr("天气", "Weather")) {
                    val outdoor = editedOutdoor
                    if (outdoor != null) {
                        EditMediaSummary(
                            icon = Icons.Outlined.Cloud,
                            text = "${outdoor.placeName} · ${weatherConditionLabel(outdoor.weatherCode)} · ${outdoor.temperatureCelsius.roundToInt()}°C",
                        )
                    } else {
                        EditMediaSummary(Icons.Outlined.Cloud, tr("这条记录没有天气信息", "This entry has no weather information"))
                    }
                }

                EditSectionCard(title = tr("录音", "Audio")) {
                    val audio = retainedAudio
                    if (audio != null) {
                        VoicePlaybackCard(
                            audio = audio,
                            openAudio = openAudio,
                            onDelete = if (isSaving) null else ({ if (!isSaving) retainedAudio = null }),
                        )
                    } else {
                        EditMediaSummary(Icons.Outlined.Mic, tr("这条记录没有录音", "This entry has no audio"))
                    }
                }

                EditSectionCard(
                    title = localizedText("照片"),
                    trailing = "${retainedImages.size + newImageUriStrings.size} / $MAX_IMAGES_PER_ENTRY",
                ) {
                    EditPhotoStrip(
                        retainedImages = retainedImages,
                        newImageUriStrings = newImageUriStrings,
                        canAdd = availableImageSlots > 0 && !isSaving,
                        enabled = !isSaving,
                        openImage = openImage,
                        onAdd = { showPhotoSourceDialog = true },
                        onPreview = { previewPhoto = it },
                        onRemoveRetained = { if (!isSaving) retainedImages = retainedImages - it },
                        onRemoveNew = { uriString ->
                            if (!isSaving) newImageUriStrings = newImageUriStrings - uriString
                        },
                    )
                }

                EditSectionCard(title = tr("视频", "Video")) {
                    retainedVideo?.let { VideoCard(it, if (isSaving || isVideoImporting) null else ({
                        scope.launch { videoServices.saveEdit(entry, null).onSuccess { retainedVideoJson = null }.onFailure { saveError = it.message } }
                    })) }
                    if (isVideoImporting) VideoImportStatus(videoProgress)
                    VideoAddButton(enabled = !isSaving && !isVideoImporting, onPicked = { uri ->
                        isVideoImporting = true
                        scope.launch {
                            try {
                                videoServices.import(uri) { progress -> scope.launch { videoProgress = progress } }
                                    .onSuccess { video ->
                                        videoServices.saveEdit(entry, video).onSuccess {
                                            retainedVideo?.takeIf { it != entry.video }?.let(videoServices.release)
                                            importedVideos += video; retainedVideoJson = video.toJson().toString()
                                        }.onFailure { videoServices.release(video); saveError = it.message }
                                    }
                                    .onFailure { saveError = it.message ?: tr("视频导入失败。", "Video import failed.") }
                            } finally { isVideoImporting = false; videoProgress = null }
                        }
                    })
                }

                saveError?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = XikeShapes.inner,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            error,
                            modifier = Modifier.padding(XikeInnerCardPadding),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    val photos = retainedImages + newImageUriStrings
    previewPhoto?.let { photo ->
        if (photo in photos) {
            PhotoGalleryDialog(
                fileNames = photos,
                initialPage = photos.indexOf(photo),
                openImage = { source ->
                    if (source in retainedImages) openImage(source)
                    else context.contentResolver.openInputStream(Uri.parse(source))
                },
                onDismiss = { previewPhoto = null },
            )
        }
    }

    if (showPhotoSourceDialog) {
        PhotoSourceDialog(
            cameraAvailable = canTakePhoto(context),
            onTakePhoto = {
                showPhotoSourceDialog = false
                openCamera()
            },
            onChoosePhotos = {
                showPhotoSourceDialog = false
                openPhotoPicker()
            },
            onDismiss = { showPhotoSourceDialog = false },
        )
    }

    if (showDiscardConfirmation) {
        DestructiveConfirmationDialog(
            onDismiss = { showDiscardConfirmation = false },
            title = localizedText("放弃未保存的修改？"),
            dismissText = localizedText("继续编辑"),
            confirmText = localizedText("放弃修改"),
            text = { Text(localizedText("心情、时间、注脚、主题和附件的本次修改都不会保存。")) },
            onConfirm = {
                showDiscardConfirmation = false
                discardEditor()
            },
        )
    }
}

@Composable
private fun EditMediaSummary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditSectionCard(
    title: String,
    trailing: String? = null,
    trailingStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                trailing?.let {
                    Text(it, style = trailingStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
    enabled: Boolean,
    openImage: (String) -> InputStream?,
    onAdd: () -> Unit,
    onPreview: (String) -> Unit,
    onRemoveRetained: (String) -> Unit,
    onRemoveNew: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        retainedImages.forEachIndexed { index, fileName ->
            item(key = "existing-$fileName") {
                EditPhotoTile(
                    key = "existing-$fileName",
                    photoDescription = tr("已有照片 ${index + 1}", "Existing photo ${index + 1}"),
                    openStream = { openImage(fileName) },
                    onRemove = { onRemoveRetained(fileName) },
                    enabled = enabled,
                    onPreview = { onPreview(fileName) },
                )
            }
        }
        newImageUriStrings.forEachIndexed { index, uriString ->
            item(key = "new-$uriString") {
                EditPhotoTile(
                    key = "new-$uriString",
                    photoDescription = tr("新照片 ${index + 1}", "New photo ${index + 1}"),
                    openStream = { context.contentResolver.openInputStream(Uri.parse(uriString)) },
                    onRemove = { onRemoveNew(uriString) },
                    enabled = enabled,
                    onPreview = { onPreview(uriString) },
                )
            }
        }
        if (canAdd) {
            item(key = "add-photo") {
                AddPhotoTile(modifier = Modifier.size(92.dp), label = localizedText("添加照片"), onClick = onAdd)
            }
        }
    }
}

@Composable
private fun EditPhotoTile(
    key: String,
    photoDescription: String,
    openStream: () -> InputStream?,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    enabled: Boolean,
) {
    val bitmap = rememberPreviewBitmap(key = key, maxDimension = 360, openStream = openStream)
    val removeDescription = tr("移除$photoDescription", "Remove $photoDescription")
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(XikeShapes.inner)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = localizedText("预览照片"), onClick = onPreview),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = photoDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = photoDescription,
                modifier = Modifier.align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .semantics { contentDescription = removeDescription },
        ) {
            Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.TopEnd) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.66f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                    }
                }
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
                    if (entry.imageFileNames.isEmpty() && entry.audio == null && entry.video == null) {
                        localizedText("删除后可在底部提示消失前撤销。请确认这不是误操作。")
                    } else {
                        val attachments = buildList {
                            if (entry.imageFileNames.isNotEmpty()) add(tr("${entry.imageFileNames.size} 张照片", "${entry.imageFileNames.size} photos"))
                            if (entry.audio != null) add(localizedText("1 段语音"))
                            if (entry.video != null) add(tr("1 段视频", "1 video"))
                        }.joinToString(localizedText("和"))
                        tr("删除后可在底部提示消失前撤销。期限结束后，记录和息刻内保存的${attachments}会一并清理；系统相册中的原图不会受影响。", "You can undo deletion before the message at the bottom disappears. After that, the entry and its ${attachments} in Xike are removed. Originals in the system gallery are unaffected.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

private fun List<String>.toggle(value: String): List<String> = if (value in this) this - value else this + value

private fun JournalEntry.localDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate()

private fun LocalDate.asShortChineseDate(): String =
    format(DateTimeFormatter.ofPattern(tr("M月d日", "MMM d"), AppLocale.locale))

private fun Long.asDetailChineseDateTime(): String =
    DateTimeFormatter.ofPattern(tr("yyyy年M月d日 EEEE · HH:mm", "EEE, MMM d, yyyy · HH:mm"), AppLocale.locale)
        .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
