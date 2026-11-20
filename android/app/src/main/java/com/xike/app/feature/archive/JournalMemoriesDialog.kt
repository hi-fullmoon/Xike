package com.xike.app.feature.archive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.xike.app.*
import com.xike.app.data.JournalEntry
import com.xike.app.ui.components.JournalEntryCard
import com.xike.app.ui.components.PaperCard
import com.xike.app.ui.components.SecondaryPageHeader
import com.xike.app.ui.components.journal.JournalEntryDetailDialog
import com.xike.app.ui.theme.*
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

@Composable
internal fun JournalMemoriesDialog(
    repository: JournalReadRepository,
    revision: Long,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
    onDismiss: () -> Unit,
    onSetFavorite: (suspend (String, Boolean) -> Result<JournalEntry>)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var clock by remember { mutableStateOf(LocalDate.now() to ZoneId.systemDefault()) }
    var refresh by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<JournalMemories?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<JournalEntry?>(null) }
    val groupScroll = rememberLazyListState()
    fun updateClock() {
        clock = LocalDate.now() to ZoneId.systemDefault()
    }
    DisposableEffect(context, lifecycle) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        updateClock()
                        refresh++
                    }
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateClock()
                refresh++
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(30_000)
                updateClock()
            }
        }
    }
    LaunchedEffect(clock, refresh, revision) {
        loading = true
        journalReadResult { repository.memories(clock.first, clock.second) }
            .onSuccess {
                if (
                    snapshot?.let { old -> old.today != it.today || old.zoneId != it.zoneId } ==
                        true
                )
                    selectedDate = null
                snapshot = it
                error = null
            }
            .onFailure { error = tr("暂时无法读取往日记录，请重试。", "Unable to read memories. Try again.") }
        loading = false
    }
    LaunchedEffect(detailId, revision, refresh) {
        val id = detailId
        if (id == null) detail = null
        else
            journalReadResult { repository.entry(id) }
                .onSuccess {
                    detail = it
                    detailError = null
                    if (it == null) {
                        detailId = null
                        notice = tr("记录已不存在。", "This entry no longer exists.")
                        refresh++
                    }
                }
                .onFailure {
                    detailError = tr("暂时无法读取记录，请重试。", "Unable to read the entry. Try again.")
                }
    }
    val current = snapshot
    val date = selectedDate?.let { LocalDate.parse(it) }
    val closePage: () -> Unit = { if (date != null) selectedDate = null else onDismiss() }
    Dialog(
        onDismissRequest = closePage,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = XikeContentMaxWidth)
                    .fillMaxWidth()
            ) {
                SecondaryPageHeader(
                    title = if (date == null) tr("往日回望", "Past moments") else date.asMemoryDate(),
                    subtitle =
                        if (date == null) tr("查看这些日期的全部记录", "All entries from these dates")
                        else tr("查看当天的全部记录", "All entries from this date"),
                    closeDescription =
                        if (date == null) tr("关闭往日回望", "Close memories")
                        else tr("返回往日日期", "Back to memory dates"),
                    onClose = closePage,
                    modifier =
                        Modifier.padding(
                            horizontal = XikeScreenHorizontalPadding,
                            vertical = XikeScreenVerticalPadding,
                        ),
                )
                error?.let {
                    Column(Modifier.padding(horizontal = XikeScreenHorizontalPadding)) {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { refresh++ }) { Text(tr("重试", "Retry")) }
                    }
                }
                notice?.let {
                    Text(
                        it,
                        Modifier.padding(horizontal = XikeScreenHorizontalPadding),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (date != null && current != null) {
                    MemoryEntries(repository, date, current.zoneId, openImage) { entry ->
                        notice = null
                        detailError = null
                        detail = entry
                        detailId = entry.id
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).testTag("memories-dates"),
                        state = groupScroll,
                        contentPadding =
                            PaddingValues(
                                horizontal = XikeScreenHorizontalPadding,
                                vertical = XikeScreenVerticalPadding,
                            ),
                        verticalArrangement = Arrangement.spacedBy(XikeContentGap),
                    ) {
                        if (loading)
                            item {
                                CircularProgressIndicator(
                                    Modifier.size(XikeScreenHorizontalPadding)
                                )
                            }
                        if (current != null) {
                            item {
                                Text(
                                    tr(
                                        "以 ${current.today} 为今天 · ${current.zoneId.id}",
                                        "As of ${current.today} · ${current.zoneId.id}",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (current.days.isEmpty() && error == null && !loading)
                                item {
                                    PaperCard {
                                        Text(tr("这些日子还没有留下记录", "No entries from these dates yet"))
                                    }
                                }
                            items(current.days, key = { "${it.kind}:${it.date}" }) { day ->
                                PaperCard(Modifier.testTag("memory-day:${day.date}")) {
                                    Text(
                                        if (day.kind == MemoryKind.MONTH_AGO)
                                            tr("一个月前", "One month ago")
                                        else tr("往年今天", "On this day"),
                                        style = XikeSectionTitleStyle,
                                    )
                                    Text(
                                        day.date.asMemoryDate(),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        tr(
                                            "${day.count} 条记录",
                                            "${day.count} ${if (day.count == 1) "entry" else "entries"}",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(
                                        onClick = { selectedDate = day.date.toString() },
                                        shape = XikeShapes.button,
                                    ) {
                                        Text(tr("查看记录", "View entries"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    detail?.let { entry ->
        JournalEntryDetailDialog(
            entry,
            openImage,
            openAudio,
            onDismiss = {
                detailId = null
                detail = null
            },
            onSetFavorite = onSetFavorite,
            readError = detailError,
            onRetryRead = { refresh++ },
        )
    }
}

@Composable
private fun MemoryEntries(
    repository: JournalReadRepository,
    date: LocalDate,
    zoneId: ZoneId,
    openImage: (String) -> InputStream?,
    onOpen: (JournalEntry) -> Unit,
) {
    val flow =
        remember(repository, date, zoneId) {
            repository.pages(
                JournalSearchQuery(startDate = date, endDate = date),
                zoneId = zoneId,
                enablePlaceholders = false,
            )
        }
    val entries = flow.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().testTag("memories-entries"),
        state = listState,
        contentPadding =
            PaddingValues(
                horizontal = XikeScreenHorizontalPadding,
                vertical = XikeScreenVerticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(XikeContentGap),
    ) {
        if (entries.loadState.refresh is LoadState.Loading) item { CircularProgressIndicator() }
        if (
            entries.loadState.refresh is LoadState.Error ||
                entries.loadState.prepend is LoadState.Error
        )
            item {
                Text(
                    tr("暂时无法读取往日记录", "Unable to read memories"),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = entries::retry) { Text(tr("重试", "Retry")) }
            }
        if (entries.loadState.refresh is LoadState.NotLoading && entries.itemCount == 0)
            item { Text(tr("当天已没有记录", "No entries remain on this date")) }
        items(entries.itemCount, key = entries.itemKey { it.id }) { index ->
            val entry = entries[index]
            if (entry != null)
                JournalEntryCard(
                    entry,
                    modifier = Modifier.testTag("memory-entry:${entry.id}"),
                    openImage = openImage,
                    onImageClick = { onOpen(entry) },
                    onClick = { onOpen(entry) },
                    compactMedia = true,
                    wrapMoodLabel = true,
                )
        }
        if (entries.loadState.append is LoadState.Loading) item { CircularProgressIndicator() }
        if (entries.loadState.append is LoadState.Error)
            item {
                Text(
                    tr("暂时无法读取往日记录", "Unable to read memories"),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = entries::retry) { Text(tr("重试", "Retry")) }
            }
    }
}

private fun LocalDate.asMemoryDate(): String =
    if (AppLocale.language == AppLanguage.ENGLISH) toString()
    else "${year}年${monthValue}月${dayOfMonth}日"
