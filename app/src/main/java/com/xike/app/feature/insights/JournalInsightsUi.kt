package com.xike.app.feature.insights

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xike.app.AppLocale
import com.xike.app.JournalReadRepository
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.journalReadResult
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.components.ScreenHeader
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikeSectionTitleStyle
import com.xike.app.ui.theme.XikeShapes
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InsightDrilldown(
    val title: String,
    val subtitle: String,
    val entryIds: List<String>,
)

@Composable
fun JournalInsightsScreen(
    padding: PaddingValues,
    entries: List<JournalEntry>,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream? = { null },
    readRepository: JournalReadRepository? = null,
    dataRevision: Long = 0,
    onReadError: (String) -> Unit = {},
) {
    var selectedPeriodName by rememberSaveable { mutableStateOf(InsightsPeriod.WEEK.name) }
    val selectedPeriod =
        InsightsPeriod.entries.firstOrNull { it.name == selectedPeriodName } ?: InsightsPeriod.WEEK
    val today = LocalDate.now()
    val language = AppLocale.language
    var calculatedSummary by remember {
        mutableStateOf<JournalPeriodSummary?>(null)
    }
    var readError by remember { mutableStateOf<String?>(null) }
    var retryRevision by remember { mutableStateOf(0) }
    LaunchedEffect(entries, dataRevision, selectedPeriod, today, language, retryRevision) {
        readError = null
        journalReadResult {
            if (readRepository != null) readRepository.insights(selectedPeriod, today)
            else
                withContext(Dispatchers.Default) {
                    journalPeriodSummary(entries, selectedPeriod, today)
                }
        }
            .onSuccess { calculatedSummary = it }
            .onFailure {
                readError = it.message ?: localizedText("日记数据库暂时无法读取，原数据未被覆盖。")
                onReadError(requireNotNull(readError))
            }
    }
    var drilldown by
        rememberSaveable(
            stateSaver =
                listSaver<InsightDrilldown?, String>(
                    save = { request ->
                        request?.let { listOf(it.title, it.subtitle) + it.entryIds } ?: emptyList()
                    },
                    restore = { values ->
                        if (values.isEmpty()) null
                        else InsightDrilldown(values[0], values[1], values.drop(2))
                    },
                )
        ) {
            mutableStateOf<InsightDrilldown?>(null)
        }
    var showReview by rememberSaveable { mutableStateOf(false) }
    val reviewScrollState =
        rememberSaveable(showReview, saver = ScrollState.Saver) { ScrollState(0) }
    val listState = rememberLazyListState()

    val summary = calculatedSummary
    if (summary == null) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (readError == null) {
                CircularProgressIndicator(
                    Modifier.semantics { contentDescription = localizedText("正在读取…") }
                )
            } else {
                Column(
                    Modifier.padding(XikeScreenHorizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(requireNotNull(readError))
                    TextButton(onClick = { retryRevision++ }, shape = XikeShapes.button) {
                        Text(tr("重试", "Retry"))
                    }
                }
            }
        }
        return
    }
    val displayedPeriod = summary.period

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier.testTag("insights-list")
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
            item(key = "insights-header") {
                ScreenHeader(
                    eyebrow = tr("细小的痕迹", "THE LITTLE TRACES"),
                    title = localizedText("轨迹"),
                    supporting =
                        tr(
                            "${summary.dateRangeLabel()}\n看看心情怎样变化，不急着寻找原因。",
                            "${summary.dateRangeLabel()}\nNotice how your mood changes. There is no rush to find causes.",
                        ),
                )
            }
            item(key = "insights-period") {
                Column {
                    InsightsPeriodSelector(
                        selected = selectedPeriod,
                        onSelected = { selectedPeriodName = it.name },
                    )
                    if (readError != null) {
                        TextButton(onClick = { retryRevision++ }, shape = XikeShapes.button) {
                            Text(tr("读取失败，重试", "Read failed. Retry"))
                        }
                    }
                }
            }
            item(key = "insights-overview") {
                InsightsOverviewCard(
                    summary = summary,
                    onClick = {
                        drilldown =
                            InsightDrilldown(
                                title =
                                    tr(
                                        "${displayedPeriod.contextName}的记录",
                                        "Entries: ${displayedPeriod.contextName}",
                                    ),
                                subtitle = summary.dateRangeLabel(),
                                entryIds = summary.entryIds,
                            )
                    },
                )
            }
            item(key = "insights-section-label") {
                InsightsSectionLabel(
                    title = localizedText("细看这段时间"),
                    supporting = localizedText("每一项都可以回到原始记录"),
                )
            }
            item(key = "insights-trend") {
                TrendCard(summary = summary, today = today) { point ->
                    drilldown =
                        InsightDrilldown(
                            title =
                                tr(
                                    "${point.label} · ${point.entryCount} 条",
                                    "${point.label} · ${point.entryCount} entries",
                                ),
                            subtitle = point.dateRangeLabel(),
                            entryIds = point.entryIds,
                        )
                }
            }
            item(key = "insights-distribution") {
                MoodDistributionCard(summary.moodDistribution) { item ->
                    drilldown =
                        InsightDrilldown(
                            title =
                                tr(
                                    "${item.mood.label} · ${item.entryCount} 条",
                                    "${item.mood.label} · ${item.entryCount} entries",
                                ),
                            subtitle =
                                tr(
                                    "${displayedPeriod.contextName}的心情分布",
                                    "Mood distribution: ${displayedPeriod.contextName}",
                                ),
                            entryIds = item.entryIds,
                        )
                }
            }
            item(key = "insights-comparison") {
                PeriodComparisonCard(summary.comparison) { previous ->
                    drilldown =
                        InsightDrilldown(
                            title =
                                if (previous) localizedText("前一周期的记录")
                                else
                                    tr(
                                        "${displayedPeriod.contextName}的记录",
                                        "Entries: ${displayedPeriod.contextName}",
                                    ),
                            subtitle =
                                if (previous) summary.comparison.dateRangeLabel()
                                else summary.dateRangeLabel(),
                            entryIds =
                                if (previous) summary.comparison.entryIds else summary.entryIds,
                        )
                }
            }
            item(key = "insights-tags") {
                TagTrendsCard(summary.topTags, summary.evidence) { tag ->
                    drilldown =
                        InsightDrilldown(
                            title =
                                tr(
                                    "${tag.tag} · ${tag.entryCount} 条",
                                    "${localizedText(tag.tag)} · ${tag.entryCount} entries",
                                ),
                            subtitle =
                                tr(
                                    "${displayedPeriod.contextName}的主题",
                                    "Topics: ${displayedPeriod.contextName}",
                                ),
                            entryIds = tag.entryIds,
                        )
                }
            }
            item(key = "insights-day-type") {
                DayTypeCard(
                    weekday = summary.weekdayInsight,
                    weekend = summary.weekendInsight,
                    onClick = { insight ->
                        drilldown =
                            InsightDrilldown(
                                title =
                                    tr(
                                        "${insight.type.label} · ${insight.entryCount} 条",
                                        "${insight.type.label} · ${insight.entryCount} entries",
                                    ),
                                subtitle =
                                    tr(
                                        "${displayedPeriod.contextName}的记录",
                                        "Entries: ${displayedPeriod.contextName}",
                                    ),
                                entryIds = insight.entryIds,
                            )
                    },
                )
            }
            item(key = "insights-review") {
                LocalReviewCard(
                    enabled = summary.entryCount > 0,
                    periodName = displayedPeriod.contextName,
                    onOpen = { showReview = true },
                )
            }
        }
    }

    drilldown?.let { request ->
        InsightDrilldownDialog(
            title = request.title,
            subtitle = request.subtitle,
            entries = entriesWithIds(entries, request.entryIds),
            readRepository = readRepository,
            entryIds = request.entryIds,
            dataRevision = dataRevision,
            onReadError = onReadError,
            openImage = openImage,
            openAudio = openAudio,
            onDismiss = { drilldown = null },
        )
    }

    if (showReview && drilldown == null) {
        LocalReviewDialog(
            review = localJournalReview(summary),
            scrollState = reviewScrollState,
            onOpenSource = { source ->
                drilldown =
                    InsightDrilldown(
                        source.label,
                        tr("本地回顾引用的原始记录", "Original entries referenced by the local review"),
                        source.entryIds,
                    )
            },
            onDismiss = { showReview = false },
        )
    }
}

@Composable
private fun InsightsSectionLabel(title: String, supporting: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp)) {
        Text(title, style = XikeSectionTitleStyle)
        Spacer(Modifier.height(3.dp))
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightsPeriodSelector(selected: InsightsPeriod, onSelected: (InsightsPeriod) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
            val columns = if (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f) 2 else 4
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                InsightsPeriod.entries.chunked(columns).forEach { periods ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        periods.forEach { period ->
                            val isSelected = selected == period
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .clip(XikeShapes.button)
                                        .background(
                                            if (isSelected)
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.82f
                                                )
                                            else Color.Transparent
                                        )
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.RadioButton,
                                            onClick = { onSelected(period) },
                                        )
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 4.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    period.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight =
                                        if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color =
                                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
