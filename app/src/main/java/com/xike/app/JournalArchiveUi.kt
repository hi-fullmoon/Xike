package com.xike.app

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import kotlinx.coroutines.Dispatchers
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
    entriesLoading: Boolean = false,
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
    val entrySaver = Saver<JournalEntry?, String>(
        save = { it?.toJson()?.toString() ?: "" },
        restore = { it.takeIf(String::isNotEmpty)?.let { json -> JournalEntry.fromJson(org.json.JSONObject(json)) } },
    )
    var detailEntry by rememberSaveable(stateSaver = entrySaver) { mutableStateOf<JournalEntry?>(null) }
    var editEntry by rememberSaveable(stateSaver = entrySaver) { mutableStateOf<JournalEntry?>(null) }
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

    val requestKey = remember(entries, searchQuery) { Any() }
    val currentRequestKey by androidx.compose.runtime.rememberUpdatedState(requestKey)
    var resultKey by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(requestKey) {
        isSearching = true
        isLoadingMore = false
        if (searchQuery.normalizedText.isNotEmpty()) delay(220)
        val firstPage = onSearch(searchQuery, 0, ARCHIVE_PAGE_SIZE)
        if (currentRequestKey !== requestKey) return@LaunchedEffect
        firstPage
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
        resultKey = requestKey
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

    LaunchedEffect(entries, detailEntry?.id, entriesLoading) {
        if (entriesLoading) return@LaunchedEffect
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
                        enabled = !isLoadingMore && !isSearching && resultKey === requestKey,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (isLoadingMore || isSearching || resultKey !== requestKey) return@TextButton
                            isLoadingMore = true
                            val pageKey = requestKey
                            val pageQuery = searchQuery
                            val baseEntries = resultEntries
                            scope.launch {
                                val result = onSearch(pageQuery, baseEntries.size, ARCHIVE_PAGE_SIZE)
                                if (currentRequestKey !== pageKey) return@launch
                                result
                                    .onSuccess { page ->
                                        resultEntries = (baseEntries + page.entries).distinctBy { it.id }
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
                onUpdate(updatedEntry, retainedImages, newImageUris)
            },
            onSaved = { savedEntry -> detailEntry = savedEntry; editEntry = null },
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
                                            XikeNotice.makeText(context, localizedText("记录已恢复"), XikeNotice.LENGTH_SHORT).show()
                                        }
                                        .onFailure { error ->
                                            XikeNotice.makeText(
                                                context,
                                                error.message ?: localizedText("撤销删除失败"),
                                                XikeNotice.LENGTH_LONG,
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
                    selected = viewMode == ArchiveViewMode.CALENDAR,
                    modifier = Modifier.weight(1f).testTag("archive-mode-calendar").then(
                        if (viewMode == ArchiveViewMode.CALENDAR) Modifier.bringIntoViewRequester(selectedItemRequester) else Modifier,
                    ),
                    onClick = { onViewModeChange(ArchiveViewMode.CALENDAR) },
                )
                ArchiveModeButton(
                    label = localizedText("时间流"),
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
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
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
        Box(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = labelStyle,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val selectedContentColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
            .compositeOver(MaterialTheme.colorScheme.onPrimaryContainer)
    }
    val filterColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = selectedContentColor,
        selectedLeadingIconColor = selectedContentColor,
        selectedTrailingIconColor = selectedContentColor,
    )
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
                        colors = filterColors,
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
                            colors = filterColors,
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
                            colors = filterColors,
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
                        colors = filterColors,
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
                        colors = filterColors,
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
        compactMedia = true,
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun JournalEntryEditDialog(
    entry: JournalEntry,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
    onDismiss: () -> Unit,
    onSave: suspend (JournalEntry, List<String>, List<Uri>) -> Result<JournalEntry>,
    onSaved: (JournalEntry) -> Unit,
) {
    val context = LocalContext.current
    val systemActivityCallbacks = LocalSystemActivityCallbacks.current
    val scope = rememberCoroutineScope()
    val operations = LocalJournalEditOperations.current
    val operationState = operations.state(entry.id)
    val editorScrollState = rememberScrollState()
    var editorFocusManager by remember { mutableStateOf<FocusManager?>(null) }
    var editorKeyboardController by remember { mutableStateOf<SoftwareKeyboardController?>(null) }
    var isNoteFocused by remember { mutableStateOf(false) }
    val dismissKeyboard: () -> Unit = { editorFocusManager?.clearFocus(); editorKeyboardController?.hide() }
    var selectedMoodName by rememberSaveable(entry.id) { mutableStateOf(entry.mood.name) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    var selectedTags by rememberSaveable(entry.id) { mutableStateOf(entry.tags) }
    var showTopics by rememberSaveable(entry.id) { mutableStateOf(entry.tags.isNotEmpty()) }
    val topicsAnchor = remember { BringIntoViewRequester() }
    var revealTopicsRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(revealTopicsRequest) {
        if (revealTopicsRequest > 0) { withFrameNanos { }; topicsAnchor.bringIntoView() }
    }
    var showAddContent by rememberSaveable(entry.id) { mutableStateOf(false) }
    var retainedAudioJson by rememberSaveable(entry.id) { mutableStateOf(entry.audio?.toJson()?.toString()) }
    val retainedAudio = retainedAudioJson?.let { JournalAudio.fromJson(org.json.JSONObject(it)) }
    val audioServices = LocalAudioEditServices.current
    var importedAudioJsons by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    val importedAudios = importedAudioJsons.map { JournalAudio.fromJson(org.json.JSONObject(it))!! }
    var lastImportedAudioFileName by rememberSaveable(entry.id) { mutableStateOf(importedAudios.lastOrNull()?.fileName) }
    var showVoiceCapture by remember { mutableStateOf(false) }
    val pendingAudioSaver = Saver<Pair<java.io.File, Long>?, List<String>>(
        save = { it?.let { (file, duration) -> listOf(file.absolutePath, duration.toString()) } ?: emptyList() },
        restore = { if (it.isEmpty()) null else java.io.File(it[0]) to it[1].toLong() },
    )
    var pendingAudio by rememberSaveable(entry.id, stateSaver = pendingAudioSaver) { mutableStateOf<Pair<java.io.File, Long>?>(null) }
    val isAudioImporting = operationState.operation == EditOperation.AUDIO
    var audioImportError by remember { mutableStateOf<String?>(null) }
    val stagedAudioSaver = Saver<StagedDraftAudio?, List<String>>(
        save = { it?.let { staged -> listOf(staged.fileName, staged.durationMillis.toString()) } ?: emptyList() },
        restore = { if (it.isEmpty()) null else StagedDraftAudio(it[0], it[1].toLong()) },
    )
    var pendingStagedAudio by rememberSaveable(entry.id, stateSaver = stagedAudioSaver) { mutableStateOf<StagedDraftAudio?>(null) }
    var audioRecoveryFailed by rememberSaveable(entry.id) { mutableStateOf(false) }
    val importPendingAudio: () -> Unit = {
        if (pendingAudio != null || pendingStagedAudio != null) {
            val raw = pendingAudio
            val staged = pendingStagedAudio
            operations.start(entry.id, EditOperation.AUDIO) { operations.importAudio(entry.id, raw, staged) }
        }
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showVoiceCapture = true
        else XikeNotice.makeText(context, localizedText("没有麦克风权限，仍可使用文字和照片记录"), XikeNotice.LENGTH_LONG).show()
    }
    val beginVoiceCapture: () -> Unit = {
        dismissKeyboard()
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) showVoiceCapture = true
        else microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val videoServices = LocalVideoServices.current
    var retainedVideoJson by rememberSaveable(entry.id) {
        mutableStateOf(entry.video?.toJson()?.toString())
    }
    LaunchedEffect(entry.id) {
        if (operationState.outcome == null && !operationState.busy) {
            operations.start(entry.id, EditOperation.RECOVER) {
                val videoDraft = withContext(Dispatchers.IO) { videoServices.editDraft(entry) }
                EditOutcome.Restored(operations.recoverAudio(entry.id), videoDraft)
            }
        }
    }
    val retainedVideo = retainedVideoJson?.let { JournalVideo.fromJson(org.json.JSONObject(it)) }
    val isVideoImporting = operationState.operation == EditOperation.VIDEO
    val videoProgress = operationState.progress
    var importedVideoJsons by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    val importedVideos = importedVideoJsons.map { JournalVideo.fromJson(org.json.JSONObject(it))!! }
    var createdAt by rememberSaveable(entry.id) { mutableStateOf(entry.createdAt) }
    val pickerContext = LocalRecordedAtPickerContext.current
    var previewPhoto by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    var retainedImages by rememberSaveable(entry.id) { mutableStateOf(entry.imageFileNames) }
    var newImageUriStrings by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    val isSaving = operationState.operation == EditOperation.SAVE || operationState.operation == EditOperation.DISCARD
    val isVideoRemoving = operationState.operation == EditOperation.REMOVE_VIDEO
    val isDiscarding = operationState.operation == EditOperation.DISCARD
    var saveError by remember { mutableStateOf<String?>(null) }
    var saveFailureCount by remember { mutableIntStateOf(0) }
    var showDiscardConfirmation by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showPhotoSourceDialog by rememberSaveable(entry.id) { mutableStateOf(false) }
    var pendingCameraUriString by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(operationState.outcome) {
        val delivered = operationState.outcome ?: return@LaunchedEffect
        val outcome = if (delivered is EditOutcome.Restored) {
            delivered.videoDraft?.let { draft -> retainedVideoJson = draft.video?.toJson()?.toString() }
            delivered.audio
        } else delivered
        val operation = operationState.operation
        when (outcome) {
            is EditOutcome.AudioReady -> {
                if (lastImportedAudioFileName != outcome.audio.fileName) {
                    importedAudioJsons = (importedAudioJsons + outcome.audio.toJson().toString()).distinct()
                    retainedAudioJson = outcome.audio.toJson().toString()
                    lastImportedAudioFileName = outcome.audio.fileName
                }
                pendingAudio = null; pendingStagedAudio = null; audioImportError = null; audioRecoveryFailed = false
            }
            EditOutcome.AudioCleared -> { pendingAudio = null; pendingStagedAudio = null; audioImportError = null; audioRecoveryFailed = false }
            is EditOutcome.AudioPending -> {
                pendingAudio = null; pendingStagedAudio = outcome.staged; audioImportError = outcome.message
            }
            is EditOutcome.Recovered -> {
                if (outcome.raw != null || pendingAudio?.first?.isFile != true) pendingAudio = outcome.raw
                pendingStagedAudio = outcome.staged ?: pendingStagedAudio
                audioRecoveryFailed = false
            }
            is EditOutcome.Restored -> error("Nested editor recovery result")
            is EditOutcome.VideoReady -> {
                retainedVideoJson = outcome.video?.toJson()?.toString()
                outcome.video?.let { importedVideoJsons = importedVideoJsons + it.toJson().toString() }
            }
            is EditOutcome.Failed -> {
                if (operation == EditOperation.RECOVER) audioRecoveryFailed = true
                if (operation == EditOperation.AUDIO) audioImportError = outcome.message
                saveError = outcome.message; saveFailureCount++
            }
            is EditOutcome.Saved -> {
                operations.acknowledge(entry.id)
                XikeNotice.makeText(context,
                    if (outcome.cleanupFailed) tr("修改已保存，部分临时附件或照片授权暂未清理。", "Changes saved. Some temporary attachments or photo permissions could not be cleaned up yet.") else localizedText("修改已保存"),
                    if (outcome.cleanupFailed) XikeNotice.LENGTH_LONG else XikeNotice.LENGTH_SHORT).show()
                onSaved(outcome.entry)
                return@LaunchedEffect
            }
            EditOutcome.Discarded -> {
                operations.acknowledge(entry.id); onDismiss(); return@LaunchedEffect
            }
        }
        operations.acknowledge(entry.id)
        if (outcome is EditOutcome.Recovered) importPendingAudio()
    }
    LaunchedEffect(entry.id, newImageUriStrings) {
        newImageUriStrings.forEach { operations.retainImage(entry.id, Uri.parse(it)) }
    }
    val selectedMood = Mood.entries.firstOrNull { it.name == selectedMoodName } ?: entry.mood
    var outdoorRemoved by rememberSaveable(entry.id) { mutableStateOf(false) }
    val timeRetainedOutdoor = retainOutdoorForEditedTime(entry.outdoor, entry.createdAt, createdAt)
    val editedOutdoor = if (outdoorRemoved) null else timeRetainedOutdoor
    val removesOutdoor = !outdoorRemoved && entry.outdoor != null && timeRetainedOutdoor == null
    val availableImageSlots = MAX_IMAGES_PER_ENTRY - retainedImages.size - newImageUriStrings.size
    val onImagesPicked: (List<Uri>) -> Unit = { uris ->
        val additions = uris
            .map(Uri::toString)
            .distinct()
            .filterNot { it in newImageUriStrings }
            .take(availableImageSlots.coerceAtLeast(0))
        val readableAdditions = additions.filter { source ->
            val uri = Uri.parse(source)
            isCameraCaptureUri(context, uri) || runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
        }
        readableAdditions.forEach { operations.retainImage(entry.id, Uri.parse(it)) }
        newImageUriStrings = newImageUriStrings + readableAdditions
        if (readableAdditions.size < additions.size) {
            XikeNotice.makeText(context, localizedText("所选照片无法获得长期读取权限，请重新选择。"), XikeNotice.LENGTH_LONG).show()
        }
        if (additions.size < uris.distinct().size) {
            XikeNotice.makeText(context, tr("每条记录最多保留 $MAX_IMAGES_PER_ENTRY 张照片", "Each entry can keep up to $MAX_IMAGES_PER_ENTRY photos"), XikeNotice.LENGTH_SHORT).show()
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
            XikeNotice.makeText(context, localizedText("照片已添加并保存到系统相册"), XikeNotice.LENGTH_SHORT).show()
        } else if (captureUri != null) {
            deleteCameraCapture(context, captureUri)
            if (captured) XikeNotice.makeText(context, localizedText("照片保存失败，请重试"), XikeNotice.LENGTH_SHORT).show()
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
            if (!opened) XikeNotice.makeText(context, localizedText("无法打开系统照片选择器"), XikeNotice.LENGTH_LONG).show()
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
                            XikeNotice.makeText(context, localizedText("无法打开系统相机"), XikeNotice.LENGTH_LONG).show()
                        }
                }
                .onFailure { error ->
                    Log.w("XikeEditCamera", "Camera capture file creation failed", error)
                    XikeNotice.makeText(context, localizedText("无法准备拍照，请重试"), XikeNotice.LENGTH_LONG).show()
                }
        }
    }
    val galleryWritePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchSystemCamera()
        } else {
            XikeNotice.makeText(context, localizedText("需要存储权限才能把照片保存到系统相册"), XikeNotice.LENGTH_LONG).show()
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
        retainedAudio != entry.audio || pendingAudio != null || pendingStagedAudio != null || audioRecoveryFailed ||
        retainedVideo != entry.video ||
        editedOutdoor != entry.outdoor ||
        retainedImages != entry.imageFileNames ||
        newImageUriStrings.isNotEmpty()
    val discardEditor: () -> Unit = {
        val audios = importedAudios.toList()
        val videos = importedVideos.toList()
        val selectedVideo = retainedVideo
        val cameraUri = pendingCameraUriString
        operations.start(entry.id, EditOperation.DISCARD) {
            if (entry.video != null || selectedVideo != null || videos.isNotEmpty()) videoServices.clearEdit(entry.id).getOrThrow()
            operations.discardAudio(entry.id)
            operations.releaseImages(entry.id)
            audios.forEach(audioServices.release)
            videos.forEach(videoServices.release)
            selectedVideo?.takeIf { it != entry.video }?.let(videoServices.release)
            cameraUri?.let(Uri::parse)?.let { deleteCameraCapture(context, it) }
            EditOutcome.Discarded
        }
    }
    val dismissEditor = {
        if (!operationState.busy) {
            if (hasUnsavedChanges) showDiscardConfirmation = true else discardEditor()
        }
    }

    val canSave = !operationState.busy && pendingAudio == null && pendingStagedAudio == null && !audioRecoveryFailed && !showVoiceCapture
    val saveChanges: () -> Unit = saveChanges@{
        if (!canSave) return@saveChanges
        dismissKeyboard()
        saveError = null
        val updatedEntry = entry.copy(
            createdAt = createdAt, mood = selectedMood, tags = selectedTags,
            note = note.trim(), audio = retainedAudio, video = retainedVideo, outdoor = editedOutdoor,
        )
        val imagesToRetain = retainedImages.toList()
        val imagesToAdd = newImageUriStrings.map(Uri::parse)
        val audiosToRelease = importedAudios.toList()
        val videosToRelease = importedVideos.toList()
        operations.start(entry.id, EditOperation.SAVE) {
            val savedEntry = onSave(updatedEntry, imagesToRetain, imagesToAdd).getOrThrow()
            val cleanupFailed = withContext(NonCancellable) {
                // The database is committed. Cleanup must not turn success into a second save.
                val videoCleanup = videoServices.clearEdit(entry.id)
                val audioCleanup = runCatching { operations.discardAudio(entry.id) }
                audiosToRelease.forEach(audioServices.release)
                videosToRelease.forEach(videoServices.release)
                val imageCleanup = runCatching { operations.releaseImages(entry.id) }
                videoCleanup.isFailure || audioCleanup.isFailure || imageCleanup.isFailure
            }
            EditOutcome.Saved(savedEntry, cleanupFailed)
        }
    }
    VideoAddButton(enabled = !operationState.busy, onPicked = { uri ->
        val previousVideo = retainedVideo
        operations.start(entry.id, EditOperation.VIDEO) {
            val video = videoServices.import(uri) { progress -> operations.progress(entry.id, progress) }.getOrThrow()
            val result = videoServices.saveEdit(entry, video)
            if (result.isFailure) { videoServices.release(video); result.getOrThrow() }
            previousVideo?.takeIf { it != entry.video }?.let(videoServices.release)
            EditOutcome.VideoReady(video)
        }
    }) { chooseVideo ->
    Dialog(
        onDismissRequest = { if (!isSaving && !isVideoImporting && !isAudioImporting) dismissEditor() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogFocusManager = LocalFocusManager.current
        val dialogKeyboardController = LocalSoftwareKeyboardController.current
        SideEffect {
            editorFocusManager = dialogFocusManager
            editorKeyboardController = dialogKeyboardController
        }
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
                    IconButton(enabled = !isSaving && !isVideoImporting && !isAudioImporting && !isVideoRemoving && !isDiscarding, onClick = dismissEditor) {
                        Icon(Icons.Outlined.Close, contentDescription = localizedText("取消编辑"))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(localizedText("编辑这一刻"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // A short viewport must let the error scroll away so the form stays usable.
                    val pinnedErrorMaxHeight = 160.dp
                    val minimumBodyHeight = 96.dp
                    val scrollErrorWithContent = maxHeight < pinnedErrorMaxHeight + minimumBodyHeight
                    LaunchedEffect(saveError, saveFailureCount) {
                        if (saveError != null && scrollErrorWithContent) editorScrollState.scrollTo(0)
                    }
                    val errorPanel: @Composable () -> Unit = {
                        saveError?.let { error ->
                            Surface(
                                modifier = Modifier.testTag("journal-edit-error").fillMaxWidth(),
                                shape = XikeShapes.inner,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    error,
                                    modifier = (if (scrollErrorWithContent) Modifier else Modifier.heightIn(max = pinnedErrorMaxHeight)
                                        .verticalScroll(rememberScrollState()))
                                        .padding(XikeInnerCardPadding),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Column(
                        Modifier.widthIn(max = XikeContentMaxWidth).fillMaxWidth()
                            .align(Alignment.TopCenter).fillMaxSize(),
                    ) {
                        if (saveError != null && !scrollErrorWithContent) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = XikeScreenHorizontalPadding)) { errorPanel() }
                        }

                Column(
                    modifier = Modifier
                        .testTag("journal-edit-body")
                        .weight(1f)
                        .widthIn(max = XikeContentMaxWidth)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .verticalScroll(editorScrollState)
                        .padding(horizontal = XikeScreenHorizontalPadding, vertical = XikeScreenVerticalPadding),
                    verticalArrangement = Arrangement.spacedBy(XikeContentGap),
                ) {
                if (saveError != null && scrollErrorWithContent) errorPanel()
                MoodPicker(selectedMood, !isSaving) { mood -> mood?.let { selectedMoodName = it.name } }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("写下一点点"), style = MaterialTheme.typography.titleMedium)
                    MomentNoteCard(
                        note = note,
                        enabled = !isSaving,
                        onNoteChange = { if (!isSaving) note = it.take(MAX_DRAFT_NOTE_LENGTH) },
                        onFocusChange = { isNoteFocused = it },
                    ) {
                        MomentContentToolbar(
                            recordedAt = createdAt,
                            enabled = !isSaving,
                            onDone = dismissKeyboard.takeIf { isNoteFocused },
                            isEditing = true,
                            onAdd = { dismissKeyboard(); showAddContent = true },
                            onChooseTime = { dismissKeyboard(); showRecordedAtPicker(pickerContext, createdAt) { createdAt = it } },
                        )
                    }
                    if (removesOutdoor) {
                        Text(
                            localizedText("日期已经改变，保存时会移除原先的窗外天气，避免误记到另一天。"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                PaperCard(contentPadding = PaddingValues(0.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !isSaving, role = Role.Button,
                            onClickLabel = if (showTopics) localizedText("收起主题") else localizedText("展开主题"),
                        ) {
                            dismissKeyboard()
                            showTopics = !showTopics
                            if (showTopics) revealTopicsRequest++
                        }
                            .padding(XikeCardPadding).heightIn(min = 42.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(localizedText("再留下一点"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (selectedTags.isEmpty()) localizedText("选主题 · 可选") else tr("已选 ${selectedTags.canonicalTopics().size} 个主题", "${selectedTags.canonicalTopics().size} topics selected"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(if (showTopics) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (showTopics) {
                        HorizontalDivider(Modifier.padding(horizontal = XikeCardPadding), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        Column(Modifier.bringIntoViewRequester(topicsAnchor).padding(XikeCardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(localizedText("主题"), style = MaterialTheme.typography.titleSmall)
                                    Text(localizedText("这一刻与什么有关？"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (selectedTags.isEmpty()) localizedText("可多选") else tr("已选 ${selectedTags.canonicalTopics().size}", "${selectedTags.canonicalTopics().size} selected"),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TopicChoices(selectedTags) { topic -> if (!isSaving) selectedTags = toggleTopic(selectedTags, topic.label) }
                        }
                    }
                }

                editedOutdoor?.let { outdoor ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localizedText("此刻窗外"), style = MaterialTheme.typography.titleSmall)
                        OutdoorContextCard(
                            snapshot = outdoor, isBackdated = false, isLoading = false,
                            errorMessage = null, enabled = !isSaving,
                            onAdd = {}, onChooseCity = {}, onRemove = { outdoorRemoved = true },
                            showRefresh = false,
                        )
                    }
                }

                if (retainedAudio != null || pendingAudio != null || pendingStagedAudio != null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("这一刻的声音"), style = MaterialTheme.typography.titleSmall)
                    val audio = retainedAudio
                    if (audio != null) {
                        VoicePlaybackCard(
                            audio = audio,
                            openAudio = openAudio,
                            onDelete = if (operationState.busy || pendingAudio != null || pendingStagedAudio != null) null else ({ retainedAudioJson = null }),
                            onReplace = if (operationState.busy || pendingAudio != null || pendingStagedAudio != null) null else beginVoiceCapture,
                            showBorder = false,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            showDeleteAtTopEnd = true,
                            shape = XikeShapes.inner,
                        )
                    }
                    (pendingStagedAudio?.durationMillis ?: pendingAudio?.second)?.let { duration ->
                        VoicePendingSaveCard(
                            durationMillis = duration,
                            isSaving = isAudioImporting,
                            errorMessage = audioImportError,
                            onRetry = importPendingAudio,
                            onDiscard = {
                                operations.start(entry.id, EditOperation.AUDIO) {
                                    operations.discardAudio(entry.id, preserveReceipt = true); EditOutcome.AudioCleared
                                }
                            },
                        )
                    }
                }
                }

                if (retainedImages.isNotEmpty() || newImageUriStrings.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("这一刻的照片", "Photos of this moment"), style = MaterialTheme.typography.titleSmall)
                    PaperCard {
                    EditPhotoGrid(
                        retainedImages = retainedImages,
                        newImageUriStrings = newImageUriStrings,
                        canAdd = availableImageSlots > 0 && !isSaving,
                        enabled = !isSaving,
                        openImage = openImage,
                        onAdd = { showPhotoSourceDialog = true },
                        onPreview = { previewPhoto = it },
                        onRemoveRetained = { if (!isSaving) retainedImages = retainedImages - it },
                        onRemoveNew = { uriString ->
                            if (!operationState.busy) {
                                scope.launch {
                                    runCatching { operations.releaseImage(entry.id, Uri.parse(uriString)) }
                                        .onSuccess { newImageUriStrings = newImageUriStrings - uriString }
                                        .onFailure { saveError = it.message }
                                }
                            }
                        },
                    )
                }
                }
                }

                if (retainedVideo != null || isVideoImporting) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localizedText("这一刻的视频"), style = MaterialTheme.typography.titleSmall)
                        if (isVideoImporting) VideoImportStatus(videoProgress)
                        val video = retainedVideo
                        if (video != null) {
                            VideoCard(
                                video = video,
                                onRemove = if (isSaving || isVideoImporting || isVideoRemoving || isDiscarding) null else ({
                                    operations.start(entry.id, EditOperation.REMOVE_VIDEO) {
                                        videoServices.saveEdit(entry, null).getOrThrow(); EditOutcome.VideoReady(null)
                                    }
                                }),
                                onReplace = if (isSaving || isVideoImporting || isVideoRemoving || isDiscarding) null else ({ dismissKeyboard(); chooseVideo() }),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                }
                    }
                }
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = saveChanges,
                            enabled = canSave,
                            modifier = Modifier.widthIn(max = XikeContentMaxWidth).fillMaxWidth()
                                .padding(horizontal = XikeScreenHorizontalPadding, vertical = 8.dp)
                                .heightIn(min = 48.dp),
                            shape = XikeShapes.button,
                            elevation = xikeButtonElevation(),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            ),
                        ) {
                            if (isSaving && !isDiscarding) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(
                                when {
                                    isSaving && !isDiscarding -> localizedText("保存中")
                                    isVideoImporting -> tr("正在导入视频…", "Importing video…")
                                    isAudioImporting -> localizedText("正在保存语音…")
                                    pendingAudio != null || pendingStagedAudio != null || audioRecoveryFailed -> localizedText("请先处理录音")
                                    showVoiceCapture -> localizedText("请先完成录音")
                                    else -> localizedText("保存")
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddContent) {
        ModalBottomSheet(
            onDismissRequest = { showAddContent = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetMaxWidth = XikeContentMaxWidth,
            shape = XikeShapes.sheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().testTag("journal-edit-add-content").verticalScroll(rememberScrollState())
                    .padding(horizontal = XikeSheetHorizontalPadding).padding(bottom = XikeSheetBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(tr("添加内容", "Add content"), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleLarge)
                MomentMediaAction(
                    if (retainedAudio == null) tr("录音", "Record audio") else tr("替换录音", "Replace audio"),
                    !operationState.busy && pendingAudio == null && pendingStagedAudio == null && !audioRecoveryFailed && !showVoiceCapture,
                ) { showAddContent = false; beginVoiceCapture() }
                MomentMediaAction(localizedText("照片"), availableImageSlots > 0 && !isSaving) {
                    showAddContent = false; showPhotoSourceDialog = true
                }
                MomentMediaAction(tr("视频", "Video"), !isSaving && !isVideoImporting && !isVideoRemoving && !isDiscarding) {
                    showAddContent = false; chooseVideo()
                }
            }
        }
    }
    }

    if (showVoiceCapture) {
        VoiceCaptureSheet(
            enabled = !isSaving && !isAudioImporting,
            onRecorded = { file, duration ->
                pendingAudio = file to duration
                importPendingAudio()
            },
            onClosed = { showVoiceCapture = false },
            recordingDirectory = operations.recordingDirectory(entry.id),
        )
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
private fun EditPhotoGrid(
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
    val photos = retainedImages + newImageUriStrings
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val columns = ((maxWidth + 8.dp) / (104.dp + 8.dp)).toInt().coerceIn(1, 3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (newImageUriStrings.isEmpty()) localizedText("加密保存在本机") else tr("保存后加密保存在本机", "Encrypted on this device after saving"),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${photos.size} / $MAX_IMAGES_PER_ENTRY", style = MaterialTheme.typography.labelSmall)
        }
        val tiles: List<String?> = photos + if (canAdd) listOf(null) else emptyList()
        tiles.chunked(columns).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTiles.forEach { source ->
                    val tileModifier = Modifier.weight(1f).aspectRatio(1f)
                    if (source == null) {
                        AddPhotoTile(tileModifier, localizedText("继续添加"), onAdd)
                    } else {
                        val retained = source in retainedImages
                        val order = if (retained) retainedImages.indexOf(source) + 1 else newImageUriStrings.indexOf(source) + 1
                        EditPhotoTile(
                            key = source,
                            modifier = tileModifier,
                            photoDescription = if (retained) tr("已有照片 $order", "Existing photo $order") else tr("新照片 $order", "New photo $order"),
                            openStream = { if (retained) openImage(source) else context.contentResolver.openInputStream(Uri.parse(source)) },
                            onRemove = { if (retained) onRemoveRetained(source) else onRemoveNew(source) },
                            enabled = enabled,
                            onPreview = { onPreview(source) },
                        )
                    }
                }
                repeat(columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    }
}

@Composable
private fun EditPhotoTile(
    key: String,
    modifier: Modifier,
    photoDescription: String,
    openStream: () -> InputStream?,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    enabled: Boolean,
) {
    val bitmap = rememberPreviewBitmap(key = key, maxDimension = 360, openStream = openStream)
    val removeDescription = tr("移除$photoDescription", "Remove $photoDescription")
    Box(
        modifier = modifier
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
