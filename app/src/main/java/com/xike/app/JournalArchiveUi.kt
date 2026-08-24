package com.xike.app

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    openImage: (String) -> InputStream?,
) {
    val today = LocalDate.now()
    val scope = rememberCoroutineScope()
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "archive-header") {
            ScreenHeader(
                eyebrow = if (searchQuery.isEmpty) "${entries.size} 条记录" else "找到 $totalResultCount 条",
                title = "回顾",
                supporting = "按日期、文字和感受，找回过去的自己。",
            )
        }

        item(key = "archive-search") {
            ArchiveSearchBar(
                value = queryText,
                onValueChange = { queryText = it.take(80) },
                onClear = { queryText = "" },
            )
        }

        item(key = "archive-controls") {
            ArchiveControls(
                viewMode = viewMode,
                activeFilterCount = searchQuery.activeFilterCount,
                showFilters = showFilters,
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
                    description = "第一条不必完整，记录一种感受就够了。",
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
                    JournalEntryCard(
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

    galleryImages?.let { images ->
        PhotoGalleryDialog(
            fileNames = images,
            initialPage = galleryInitialPage,
            openImage = openImage,
            onDismiss = { galleryImages = null },
        )
    }

    detailEntry?.let { entry ->
        JournalEntryDetailDialog(entry = entry, onDismiss = { detailEntry = null })
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
        placeholder = { Text("搜索笔记或主题") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
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
        FilterChip(
            selected = viewMode == ArchiveViewMode.CALENDAR,
            onClick = { onViewModeChange(ArchiveViewMode.CALENDAR) },
            label = { Text("月历") },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = viewMode == ArchiveViewMode.TIMELINE,
            onClick = { onViewModeChange(ArchiveViewMode.TIMELINE) },
            label = { Text("时间流") },
            leadingIcon = { Icon(Icons.Outlined.ViewAgenda, null, Modifier.size(18.dp)) },
        )
        Spacer(Modifier.weight(1f))
        FilterChip(
            selected = showFilters || activeFilterCount > 0,
            onClick = onToggleFilters,
            label = { Text(if (activeFilterCount == 0) "筛选" else "筛选 $activeFilterCount") },
            leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) },
        )
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
    Surface(shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterTitle("心情")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Mood.entries, key = Mood::name) { mood ->
                    FilterChip(
                        selected = mood.name in selectedMoodNames,
                        onClick = { onToggleMood(mood) },
                        label = { Text("${mood.emoji} ${mood.label}") },
                    )
                }
            }

            if (availableTags.isNotEmpty()) {
                FilterTitle("主题")
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

            TextButton(onClick = onClearAll, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                Text("清除全部条件")
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
    Surface(shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    monthTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下个月")
                }
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                    Text(
                        weekday,
                        modifier = Modifier.weight(1f).padding(vertical = 7.dp),
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
                            Spacer(Modifier.weight(1f).height(58.dp))
                        } else {
                            val dayEntries = entriesByDate[date].orEmpty()
                            CalendarDay(
                                date = date,
                                count = dayEntries.size,
                                isToday = date == today,
                                isSelected = date == selectedDate,
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
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val spokenDate = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))
    Surface(
        modifier = modifier
            .height(58.dp)
            .padding(2.dp)
            .semantics {
                contentDescription = "$spokenDate，${if (count == 0) "没有记录" else "$count 条记录"}"
                selected = isSelected
            }
            .clickable(onClick = onClick),
        shape = XikeShapes.inner,
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary
            count > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
            else -> Color.Transparent
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                count.takeIf { it > 0 }?.let { "$it 条" }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                else MaterialTheme.colorScheme.primary,
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                selectedDate?.let { "${it.asShortChineseDate()}的记录" } ?: "时间流",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            if (isSearching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(7.dp))
            }
            Text(
                when {
                    shownCount < matchingCount -> "已显示 $shownCount / $matchingCount 条"
                    matchingCount == libraryCount -> "$matchingCount 条"
                    else -> "$matchingCount / $libraryCount 条"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
internal fun JournalEntryDetailDialog(entry: JournalEntry, onDismiss: () -> Unit) {
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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭记录详情")
                    }
                }

                Surface(shape = XikeShapes.card, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.mood.emoji, style = MaterialTheme.typography.headlineLarge)
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
                    Text("主题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(entry.tags.joinToString("  ·  "), style = MaterialTheme.typography.bodyLarge)
                }

                Text("记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    entry.note.ifBlank { "这一刻只留下了心情。" },
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
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }
    }
}

private fun List<String>.toggle(value: String): List<String> = if (value in this) this - value else this + value

private fun JournalEntry.localDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate()

private fun LocalDate.asShortChineseDate(): String =
    format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))

private fun Long.asDetailChineseDateTime(): String =
    DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE · HH:mm", Locale.CHINA)
        .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
