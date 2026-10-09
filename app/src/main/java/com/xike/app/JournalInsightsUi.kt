package com.xike.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
) {
    var selectedPeriodName by rememberSaveable { mutableStateOf(InsightsPeriod.WEEK.name) }
    val selectedPeriod = InsightsPeriod.entries.firstOrNull { it.name == selectedPeriodName }
        ?: InsightsPeriod.WEEK
    val today = LocalDate.now()
    val summary = remember(entries, selectedPeriod, today, AppLocale.language) {
        journalPeriodSummary(entries, selectedPeriod, today)
    }
    var drilldown by remember { mutableStateOf<InsightDrilldown?>(null) }
    var showReview by rememberSaveable { mutableStateOf(false) }
    val reviewScrollState = rememberSaveable(showReview, saver = ScrollState.Saver) { ScrollState(0) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
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
            item(key = "insights-header") {
                ScreenHeader(
                    eyebrow = tr("细小的痕迹", "THE LITTLE TRACES"),
                    title = localizedText("轨迹"),
                    supporting = tr("${summary.dateRangeLabel()}\n看看心情怎样变化，不急着寻找原因。", "${summary.dateRangeLabel()}\nNotice how your mood changes. There is no rush to find causes."),
                )
            }
            item(key = "insights-period") {
                InsightsPeriodSelector(
                    selected = selectedPeriod,
                    onSelected = { selectedPeriodName = it.name },
                )
            }
            item(key = "insights-overview") {
                InsightsOverviewCard(
                    summary = summary,
                    onClick = {
                        drilldown = InsightDrilldown(
                            title = tr("${selectedPeriod.contextName}的记录", "Entries: ${selectedPeriod.contextName}"),
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
                    drilldown = InsightDrilldown(
                        title = tr("${point.label} · ${point.entryCount} 条", "${point.label} · ${point.entryCount} entries"),
                        subtitle = point.dateRangeLabel(),
                        entryIds = point.entryIds,
                    )
                }
            }
            item(key = "insights-distribution") {
                MoodDistributionCard(summary.moodDistribution) { item ->
                    drilldown = InsightDrilldown(
                        title = tr("${item.mood.label} · ${item.entryCount} 条", "${item.mood.label} · ${item.entryCount} entries"),
                        subtitle = tr("${selectedPeriod.contextName}的心情分布", "Mood distribution: ${selectedPeriod.contextName}"),
                        entryIds = item.entryIds,
                    )
                }
            }
            item(key = "insights-comparison") {
                PeriodComparisonCard(summary.comparison) { previous ->
                    drilldown = InsightDrilldown(
                        title = if (previous) localizedText("前一周期的记录") else tr("${selectedPeriod.contextName}的记录", "Entries: ${selectedPeriod.contextName}"),
                        subtitle = if (previous) summary.comparison.dateRangeLabel() else summary.dateRangeLabel(),
                        entryIds = if (previous) summary.comparison.entryIds else summary.entryIds,
                    )
                }
            }
            item(key = "insights-tags") {
                TagTrendsCard(summary.topTags, summary.evidence) { tag ->
                    drilldown = InsightDrilldown(
                        title = tr("${tag.tag} · ${tag.entryCount} 条", "${localizedText(tag.tag)} · ${tag.entryCount} entries"),
                        subtitle = tr("${selectedPeriod.contextName}的主题", "Topics: ${selectedPeriod.contextName}"),
                        entryIds = tag.entryIds,
                    )
                }
            }
            item(key = "insights-day-type") {
                DayTypeCard(
                    weekday = summary.weekdayInsight,
                    weekend = summary.weekendInsight,
                    onClick = { insight ->
                        drilldown = InsightDrilldown(
                            title = tr("${insight.type.label} · ${insight.entryCount} 条", "${insight.type.label} · ${insight.entryCount} entries"),
                            subtitle = tr("${selectedPeriod.contextName}的记录", "Entries: ${selectedPeriod.contextName}"),
                            entryIds = insight.entryIds,
                        )
                    },
                )
            }
            item(key = "insights-review") {
                LocalReviewCard(
                    enabled = summary.entryCount > 0,
                    periodName = selectedPeriod.contextName,
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
                drilldown = InsightDrilldown(source.label, tr("本地回顾引用的原始记录", "Original entries referenced by the local review"), source.entryIds)
            },
            onDismiss = { showReview = false },
        )
    }
}

@Composable
private fun InsightsOverviewCard(summary: JournalPeriodSummary, onClick: () -> Unit) {
    val stackContent = LocalDensity.current.fontScale >= 1.5f
    val headline = when {
        summary.entryCount == 0 -> localizedText("等待第一条心情记录")
        !summary.evidence.canDescribePatterns -> tr("已留下 ${summary.entryCount} 条心情记录", "${summary.entryCount} mood entries kept")
        else -> summary.averageScore?.let(::moodBandLabel) ?: localizedText("等待第一条心情记录")
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = summary.entryCount > 0, onClick = onClick),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(XikeCardPadding)) {
            if (stackContent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("${summary.period.contextName} · 心情", "${summary.period.contextName} · Mood"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OverviewMoodBadge(summary)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    headline,
                    modifier = Modifier.testTag("insights-overview-headline"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("${summary.period.contextName} · 心情", "${summary.period.contextName} · Mood"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            headline,
                            modifier = Modifier.testTag("insights-overview-headline"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    OverviewMoodBadge(summary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    summary.entryCount == 0 -> localizedText("记录此刻的心情，让轨迹从这里开始。")
                    !summary.evidence.canDescribePatterns -> localizedText("先看看留下的记录，积累更多片段后再回顾变化。")
                    else -> summary.averageScore?.let(::moodSummary) ?: localizedText("记录此刻的心情，让轨迹从这里开始。")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
            )
            Spacer(Modifier.height(18.dp))
            if (stackContent) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverviewMetric(localizedText("记录"), tr("${summary.entryCount} 次", "${summary.entryCount} entries"), Modifier.fillMaxWidth())
                    OverviewMetric(localizedText("留下痕迹"), tr("${summary.recordedDayCount} 天", "${summary.recordedDayCount} days"), Modifier.fillMaxWidth())
                    OverviewMetric(
                        localizedText("日期覆盖"),
                        "${(summary.evidence.coverageRatio * 100).roundToInt()}%",
                        Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverviewMetric(localizedText("记录"), tr("${summary.entryCount} 次", "${summary.entryCount} entries"), Modifier.weight(1f).fillMaxHeight())
                    OverviewMetric(localizedText("留下痕迹"), tr("${summary.recordedDayCount} 天", "${summary.recordedDayCount} days"), Modifier.weight(1f).fillMaxHeight())
                    OverviewMetric(
                        localizedText("日期覆盖"),
                        "${(summary.evidence.coverageRatio * 100).roundToInt()}%",
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            RatioBar(
                ratio = summary.evidence.coverageRatio,
                description = tr("数据覆盖：${summary.evidence.elapsedDayCount} 天中有 ${summary.evidence.recordedDayCount} 天存在记录", "Data coverage: entries on ${summary.evidence.recordedDayCount} of ${summary.evidence.elapsedDayCount} days"),
            )
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                    Text(
                        summary.evidence.level.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    summary.evidence.level.description,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                )
                if (summary.entryCount > 0) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = localizedText("查看这段时间的记录"),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMoodBadge(summary: JournalPeriodSummary) {
    Surface(
        modifier = Modifier.size(56.dp).testTag("insights-overview-badge"),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (summary.averageScore == null) {
                Text("—", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            } else {
                MoodEmoji(summary.averageScore.averageMood(), size = 29.dp)
            }
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Column(
            Modifier.padding(XikeInnerCardPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun InsightsSectionLabel(title: String, supporting: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp),
    ) {
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
private fun EvidenceCard(evidence: InsightEvidence) {
    InsightSectionCard(
        icon = Icons.Outlined.DataUsage,
        index = localizedText("依据"),
        title = evidence.level.label,
    ) {
        Text(
            evidence.level.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val percent = (evidence.coverageRatio * 100).roundToInt()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(localizedText("有记录的日期"), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                tr("${evidence.recordedDayCount} / ${evidence.elapsedDayCount} 天 · $percent%", "${evidence.recordedDayCount} / ${evidence.elapsedDayCount} days · $percent%"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(7.dp))
        RatioBar(
            ratio = evidence.coverageRatio,
            description = tr("数据覆盖：${evidence.elapsedDayCount} 天中有 ${evidence.recordedDayCount} 天存在记录", "Data coverage: entries on ${evidence.recordedDayCount} of ${evidence.elapsedDayCount} days"),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            localizedText("覆盖比例只说明哪些日期有记录，不是完成率，也不要求每天记录。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendCard(
    summary: JournalPeriodSummary,
    today: LocalDate,
    onPointClick: (MoodTrendPoint) -> Unit,
) {
    InsightSectionCard(
        icon = Icons.AutoMirrored.Outlined.ShowChart,
        index = localizedText("趋势"),
        title = summary.period.trendTitle,
        trailing = if (summary.period == InsightsPeriod.YEAR) localizedText("按月") else null,
    ) {
        if (!summary.evidence.canDescribePatterns) {
            InsufficientDataNote(summary.evidence.level.description)
            Spacer(Modifier.height(12.dp))
        }
        Surface(
            shape = XikeShapes.inner,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
        ) {
            MoodTrendChart(summary.trendPoints, today, onPointClick)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            localizedText("折线表示五档心情的平均位置；没有记录的时段会断开。点按时间段可查看原始记录。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoodTrendChart(
    points: List<MoodTrendPoint>,
    today: LocalDate,
    onPointClick: (MoodTrendPoint) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val axisWidth = if (LocalDensity.current.fontScale >= 1.5f) 52.dp else 36.dp
    Column(Modifier.fillMaxWidth().padding(XikeInnerCardPadding)) {
        Box(Modifier.fillMaxWidth().height(142.dp)) {
            Column(
                modifier = Modifier.width(axisWidth).fillMaxHeight().padding(vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(localizedText("愉悦"), localizedText("平静"), localizedText("低落")).forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Box(Modifier.fillMaxSize().padding(start = axisWidth)) {
                Canvas(Modifier.fillMaxSize()) {
                    if (points.isEmpty()) return@Canvas
                    val step = size.width / points.size
                    val top = 16.dp.toPx()
                    val bottom = size.height - 14.dp.toPx()
                    fun position(index: Int, score: Double): Offset = Offset(
                        x = step * (index + 0.5f),
                        y = bottom - ((score.coerceIn(1.0, 5.0) - 1.0) / 4.0).toFloat() * (bottom - top),
                    )
                    listOf(1.0, 3.0, 5.0).forEach { score ->
                        val y = position(0, score).y
                        drawLine(guide, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    var runStart = 0
                    while (runStart < points.size) {
                        if (points[runStart].averageScore == null) {
                            runStart++
                            continue
                        }
                        var runEnd = runStart
                        while (runEnd + 1 < points.size && points[runEnd + 1].averageScore != null) runEnd++
                        if (runEnd > runStart) {
                            val first = position(runStart, requireNotNull(points[runStart].averageScore))
                            val path = Path().apply { moveTo(first.x, first.y) }
                            for (index in runStart until runEnd) {
                                val from = position(index, requireNotNull(points[index].averageScore))
                                val to = position(index + 1, requireNotNull(points[index + 1].averageScore))
                                val midX = (from.x + to.x) / 2f
                                path.cubicTo(midX, from.y, midX, to.y, to.x, to.y)
                            }
                            val area = Path().apply {
                                addPath(path)
                                lineTo(position(runEnd, requireNotNull(points[runEnd].averageScore)).x, bottom)
                                lineTo(first.x, bottom)
                                close()
                            }
                            drawPath(area, Brush.verticalGradient(listOf(primary.copy(alpha = 0.18f), primary.copy(alpha = 0.01f))))
                            drawPath(path, primary.copy(alpha = 0.8f), style = Stroke(width = 2.5.dp.toPx()))
                        }
                        runStart = runEnd + 1
                    }
                    points.forEachIndexed { index, point ->
                        val score = point.averageScore
                        if (score != null) {
                            val current = !today.isBefore(point.startDate) && today.isBefore(point.endDateExclusive)
                            val center = position(index, score)
                            if (current) drawCircle(primary.copy(alpha = 0.15f), radius = 10.dp.toPx(), center = center)
                            drawCircle(primary, radius = if (current) 5.dp.toPx() else 4.dp.toPx(), center = center)
                            drawCircle(Color.White, radius = 1.7.dp.toPx(), center = center)
                        }
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    points.forEach { point ->
                        val description = buildString {
                            append(point.dateRangeLabel())
                            append(tr("，${point.entryCount} 条记录", ", ${point.entryCount} entries"))
                            point.averageScore?.let { append(tr("，心情平均位置 ${it.oneDecimal()}", ", average mood position ${it.oneDecimal()}")) }
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize()
                                .semantics { contentDescription = description }
                                .clickable(enabled = point.entryCount > 0) { onPointClick(point) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(start = axisWidth)) {
            points.forEachIndexed { index, point ->
                val current = !today.isBefore(point.startDate) && today.isBefore(point.endDateExclusive)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (points.size <= 9 || index % 2 == 0 || current) {
                        Text(
                            point.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (points.size > 9) 10.sp else 11.sp),
                            color = if (current) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodDistributionCard(
    distribution: List<MoodDistributionItem>,
    onClick: (MoodDistributionItem) -> Unit,
) {
    InsightSectionCard(
        icon = Icons.Outlined.DataUsage,
        index = localizedText("分布"),
        title = localizedText("心情出现次数"),
    ) {
        distribution.sortedByDescending { it.mood.score }.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(enabled = item.entryCount > 0) { onClick(item) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoodEmoji(item.mood, size = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(item.mood.label, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            tr("${item.entryCount} 次 · ${(item.ratio * 100).roundToInt()}%", "${item.entryCount} entries · ${(item.ratio * 100).roundToInt()}%"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    RatioBar(item.ratio, tr("${item.mood.label}占 ${(item.ratio * 100).roundToInt()}%，共 ${item.entryCount} 条", "${item.mood.label}: ${(item.ratio * 100).roundToInt()}%, ${item.entryCount} entries"))
                }
                if (item.entryCount > 0) {
                    Spacer(Modifier.width(7.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodComparisonCard(comparison: PeriodComparison, onClick: (previous: Boolean) -> Unit) {
    val stackMetrics = LocalDensity.current.fontScale >= 1.5f
    InsightSectionCard(
        icon = Icons.AutoMirrored.Outlined.CompareArrows,
        index = localizedText("对比"),
        title = localizedText("与前一周期"),
    ) {
        if (stackMetrics) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ComparisonMetric(
                    localizedText("当前"),
                    comparison.currentEntryCount,
                    comparison.currentRecordedDayCount,
                    comparison.currentAverageScore,
                    Modifier.fillMaxWidth(),
                ) { onClick(false) }
                ComparisonMetric(
                    localizedText("前期"),
                    comparison.entryCount,
                    comparison.recordedDayCount,
                    comparison.averageScore,
                    Modifier.fillMaxWidth(),
                ) { onClick(true) }
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ComparisonMetric(
                    localizedText("当前"),
                    comparison.currentEntryCount,
                    comparison.currentRecordedDayCount,
                    comparison.currentAverageScore,
                    Modifier.weight(1f).fillMaxHeight(),
                ) { onClick(false) }
                ComparisonMetric(
                    localizedText("前期"),
                    comparison.entryCount,
                    comparison.recordedDayCount,
                    comparison.averageScore,
                    Modifier.weight(1f).fillMaxHeight(),
                ) { onClick(true) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (comparison.hasEnoughSamples) comparisonDescription(comparison)
            else localizedText("两段都至少有 3 条记录后，才描述变化；目前只展示实际计数。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComparisonMetric(
    label: String,
    count: Int,
    days: Int,
    average: Double?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(enabled = count > 0, onClick = onClick),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(Modifier.padding(XikeInnerCardPadding)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(tr("$count 次", "$count entries"), style = MaterialTheme.typography.titleLarge)
            Text(tr("$days 天 · 均值 ${average?.oneDecimal() ?: "—"}", "$days days · Average ${average?.oneDecimal() ?: "—"}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TagTrendsCard(
    tags: List<TagTrendItem>,
    evidence: InsightEvidence,
    onClick: (TagTrendItem) -> Unit,
) {
    InsightSectionCard(
        icon = XikeIcons.Archive,
        index = localizedText("主题"),
        title = localizedText("反复出现的主题"),
    ) {
        if (tags.isEmpty()) {
            InsufficientDataNote(localizedText("添加主题后，这里会显示实际出现次数。"))
        } else {
            tags.forEach { tag ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onClick(tag) }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(localizedText(tag.tag), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr("当前 ${tag.entryCount} 次 · 前期 ${tag.previousEntryCount} 次", "Current ${tag.entryCount} · Previous ${tag.previousEntryCount}"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        tag.countDelta.deltaLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            if (!evidence.canDescribePatterns) {
                Text(
                    localizedText("样本较少，主题仅按次数排序，不解释其意义。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayTypeCard(
    weekday: DayTypeInsight,
    weekend: DayTypeInsight,
    onClick: (DayTypeInsight) -> Unit,
) {
    val stackMetrics = LocalDensity.current.fontScale >= 1.5f
    InsightSectionCard(
        icon = Icons.Outlined.CalendarMonth,
        index = localizedText("节奏"),
        title = localizedText("工作日与周末"),
    ) {
        if (stackMetrics) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DayTypeMetric(weekday, Modifier.fillMaxWidth()) { onClick(weekday) }
                DayTypeMetric(weekend, Modifier.fillMaxWidth()) { onClick(weekend) }
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DayTypeMetric(weekday, Modifier.weight(1f).fillMaxHeight()) { onClick(weekday) }
                DayTypeMetric(weekend, Modifier.weight(1f).fillMaxHeight()) { onClick(weekend) }
            }
        }
        Spacer(Modifier.height(10.dp))
        val enough = weekday.entryCount >= 3 && weekend.entryCount >= 3
        Text(
            if (enough) {
                localizedText("这里只呈现两类日期的心情位置差异，不说明工作日或周末造成了变化。")
            } else {
                localizedText("两类日期分别至少有 3 条记录后，才适合比较均值；目前只展示计数。")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayTypeMetric(insight: DayTypeInsight, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(enabled = insight.entryCount > 0, onClick = onClick),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(XikeInnerCardPadding)) {
            Text(insight.type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(tr("${insight.entryCount} 次", "${insight.entryCount} entries"), style = MaterialTheme.typography.titleLarge)
            Text(
                tr("${insight.recordedDayCount} / ${insight.elapsedDayCount} 天 · 均值 ${insight.averageScore?.oneDecimal() ?: "—"}", "${insight.recordedDayCount} / ${insight.elapsedDayCount} days · Average ${insight.averageScore?.oneDecimal() ?: "—"}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
            )
        }
    }
}

@Composable
private fun LocalReviewCard(enabled: Boolean, periodName: String, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(Modifier.padding(XikeCardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = XikeShapes.button,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            XikeIcons.Mark,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        tr("在设备内，读一遍${periodName}", "Review ${periodName} on your device"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        localizedText("不上传，不评判"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            Text(
                tr("整理心情变化、常见主题和值得回看的片段，形成一份有记录依据的回顾。", "Review mood changes, recurring topics and moments worth revisiting, grounded in your entries."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpen,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.52f),
                ),
                elevation = xikeButtonElevation(),
            ) {
                Text(if (enabled) localizedText("查看本地回顾") else localizedText("有记录后可生成"))
            }
        }
    }
}

@Composable
private fun InsightSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    index: String,
    title: String,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(XikeCardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(index, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(3.dp))
                    Text(title, style = XikeSectionTitleStyle)
                }
                if (trailing != null) {
                    Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun RatioBar(ratio: Double, description: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.toFloat().coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun InsufficientDataNote(message: String) {
    Surface(shape = XikeShapes.inner, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(XikeInnerCardPadding),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        periods.forEach { period ->
                            val isSelected = selected == period
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(XikeShapes.button)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f) else Color.Transparent)
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
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun LocalReviewDialog(review: LocalJournalReview, scrollState: ScrollState, onOpenSource: (ReviewSource) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(localizedText("本地回顾")) },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState).testTag("local-review-scroll"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(review.title, style = MaterialTheme.typography.titleSmall)
                Text(review.dateRange, style = MaterialTheme.typography.bodySmall)
                review.sections.forEach { section ->
                    Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(section.text, style = MaterialTheme.typography.bodyMedium)
                    section.sources.forEach { source ->
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { onOpenSource(source) },
                                ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(source.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(
                    localizedText("分享会把以上文字交给你下一步选择的应用。息刻不会自动上传。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, review.text)
                    context.startActivity(Intent.createChooser(shareIntent, localizedText("分享息刻回顾")))
                },
                shape = XikeShapes.button,
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.xikeInlineActionIcon())
                Spacer(Modifier.width(XikeInlineActionGap))
                Text(localizedText("选择分享应用"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("关闭")) } },
    )
}

@Composable
private fun InsightDrilldownDialog(
    title: String,
    subtitle: String,
    entries: List<JournalEntry>,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
    onDismiss: () -> Unit,
) {
    var detailEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var galleryImages by remember { mutableStateOf<List<String>?>(null) }
    var galleryInitialPage by remember { mutableIntStateOf(0) }

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
                            openImage = openImage,
                            onImageClick = { index ->
                                galleryImages = entry.imageFileNames
                                galleryInitialPage = index
                            },
                            onClick = { detailEntry = entry },
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
            onDismiss = { detailEntry = null },
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

private fun JournalPeriodSummary.dateRangeLabel(): String = startDate.asDateRange(endDate)

private fun PeriodComparison.dateRangeLabel(): String = startDate.asDateRange(endDate)

private fun MoodTrendPoint.dateRangeLabel(): String = startDate.asDateRange(endDateExclusive.minusDays(1))

private fun LocalDate.asDateRange(end: LocalDate): String = if (this == end) {
    format(DateTimeFormatter.ofPattern(tr("yyyy年M月d日", "MMM d, yyyy"), AppLocale.locale))
} else if (year == end.year) {
    tr(
        "$year · ${monthValue}月${dayOfMonth}日 — ${end.monthValue}月${end.dayOfMonth}日",
        "${format(DateTimeFormatter.ofPattern("MMM d", AppLocale.locale))} — ${end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", AppLocale.locale))}",
    )
} else {
    tr(
        "${year}年${monthValue}月${dayOfMonth}日 — ${end.year}年${end.monthValue}月${end.dayOfMonth}日",
        "${format(DateTimeFormatter.ofPattern("MMM d, yyyy", AppLocale.locale))} — ${end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", AppLocale.locale))}",
    )
}

private fun moodBandLabel(average: Double): String = when {
    average >= 4.5 -> localizedText("愉悦时刻更多")
    average >= 3.5 -> localizedText("整体更轻松")
    average >= 2.5 -> localizedText("大多比较平静")
    average >= 1.5 -> localizedText("疲惫感停留较多")
    else -> localizedText("低落时刻较多")
}

private fun Double.averageMood(): Mood = when {
    this >= 4.5 -> Mood.JOYFUL
    this >= 3.5 -> Mood.GOOD
    this >= 2.5 -> Mood.CALM
    this >= 1.5 -> Mood.TIRED
    else -> Mood.LOW
}

private fun moodSummary(average: Double): String = when {
    average >= 4.5 -> localizedText("记录里较多是开心而舒展的时刻。")
    average >= 3.5 -> localizedText("记录里轻松的时刻更多。")
    average >= 2.5 -> localizedText("记录里平静与起伏都曾出现。")
    average >= 1.5 -> localizedText("记录里疲惫和低落停留得更多。")
    else -> localizedText("记录里低落时刻较多，记得照顾自己。")
}

private fun comparisonDescription(comparison: PeriodComparison): String = buildString {
    append(tr("记录次数${comparison.entryCountDelta.deltaPhrase()}，记录天数${comparison.recordedDayDelta.deltaPhrase()}", "Entries ${comparison.entryCountDelta.deltaPhrase()}, days recorded ${comparison.recordedDayDelta.deltaPhrase()}"))
    comparison.averageScoreDelta?.let { delta -> append(tr("，均值${delta.oneDecimalSigned()}", ", average ${delta.oneDecimalSigned()}")) }
    append(localizedText("。这些是描述性差异，不代表原因。"))
}

private fun Int.deltaPhrase(): String = when {
    this > 0 -> tr("增加 $this", "increased by $this")
    this < 0 -> tr("减少 ${-this}", "decreased by ${-this}")
    else -> localizedText("相同")
}

private fun Int.deltaLabel(): String = when {
    this > 0 -> "+$this"
    this < 0 -> toString()
    else -> localizedText("持平")
}

private fun Double.oneDecimal(): String = String.format(AppLocale.locale, "%.1f", this)

private fun Double.oneDecimalSigned(): String = String.format(AppLocale.locale, "%+.1f", this)
