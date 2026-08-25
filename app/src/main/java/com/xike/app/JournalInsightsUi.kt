package com.xike.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.LocalOffer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
) {
    var selectedPeriodName by rememberSaveable { mutableStateOf(InsightsPeriod.WEEK.name) }
    val selectedPeriod = InsightsPeriod.entries.firstOrNull { it.name == selectedPeriodName }
        ?: InsightsPeriod.WEEK
    val today = LocalDate.now()
    val summary = remember(entries, selectedPeriod, today) {
        journalPeriodSummary(entries, selectedPeriod, today)
    }
    var drilldown by remember { mutableStateOf<InsightDrilldown?>(null) }
    var showReview by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "insights-header") {
            ScreenHeader(
                eyebrow = summary.dateRangeLabel(),
                title = "轨迹",
                supporting = "看看内在天气怎样经过，不急着为变化寻找原因。",
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
                        title = "${selectedPeriod.contextName}的记录",
                        subtitle = summary.dateRangeLabel(),
                        entryIds = summary.entryIds,
                    )
                },
            )
        }
        item(key = "insights-evidence") { EvidenceCard(summary.evidence) }
        item(key = "insights-trend") {
            TrendCard(summary = summary, today = today) { point ->
                drilldown = InsightDrilldown(
                    title = "${point.label} · ${point.entryCount} 条",
                    subtitle = point.dateRangeLabel(),
                    entryIds = point.entryIds,
                )
            }
        }
        item(key = "insights-distribution") {
            MoodDistributionCard(summary.moodDistribution) { item ->
                drilldown = InsightDrilldown(
                    title = "${item.mood.label} · ${item.entryCount} 条",
                    subtitle = "${selectedPeriod.contextName}的内在天气分布",
                    entryIds = item.entryIds,
                )
            }
        }
        item(key = "insights-comparison") {
            PeriodComparisonCard(summary.comparison) { previous ->
                drilldown = InsightDrilldown(
                    title = if (previous) "前一周期的记录" else "${selectedPeriod.contextName}的记录",
                    subtitle = if (previous) summary.comparison.dateRangeLabel() else summary.dateRangeLabel(),
                    entryIds = if (previous) summary.comparison.entryIds else summary.entryIds,
                )
            }
        }
        item(key = "insights-tags") {
            TagTrendsCard(summary.topTags, summary.evidence) { tag ->
                drilldown = InsightDrilldown(
                    title = "${tag.tag} · ${tag.entryCount} 条",
                    subtitle = "${selectedPeriod.contextName}的关键词",
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
                        title = "${insight.type.label} · ${insight.entryCount} 条",
                        subtitle = "${selectedPeriod.contextName}的记录",
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

    drilldown?.let { request ->
        InsightDrilldownDialog(
            title = request.title,
            subtitle = request.subtitle,
            entries = entriesWithIds(entries, request.entryIds),
            openImage = openImage,
            onDismiss = { drilldown = null },
        )
    }

    if (showReview) {
        LocalReviewDialog(
            reviewText = localReviewText(summary),
            onDismiss = { showReview = false },
        )
    }
}

@Composable
private fun InsightsOverviewCard(summary: JournalPeriodSummary, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = summary.entryCount > 0, onClick = onClick),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${summary.period.contextName}概览",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                )
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f)) {
                    Text(
                        "${summary.entryCount} 次 · ${summary.recordedDayCount} 天",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                summary.averageScore?.let(::weatherBandLabel) ?: "等待第一条记录",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                summary.averageScore?.let(::weatherSummary) ?: "记录第一片天气，让轨迹从这里开始。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            if (summary.averageScore != null) {
                Spacer(Modifier.height(9.dp))
                Text(
                    "平均位置 ${summary.averageScore.oneDecimal()} / 5 · 每个记录日 ${summary.averageEntriesPerRecordedDay?.oneDecimal()} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun EvidenceCard(evidence: InsightEvidence) {
    InsightSectionCard(
        icon = Icons.Outlined.DataUsage,
        index = "依据",
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
            Text("有记录的日期", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "${evidence.recordedDayCount} / ${evidence.elapsedDayCount} 天 · $percent%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(7.dp))
        RatioBar(
            ratio = evidence.coverageRatio,
            description = "数据覆盖：${evidence.elapsedDayCount} 天中有 ${evidence.recordedDayCount} 天存在记录",
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "覆盖比例只说明哪些日期有记录，不是完成率，也不要求每天记录。",
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
        icon = Icons.Outlined.BarChart,
        index = "趋势",
        title = summary.period.trendTitle,
        trailing = if (summary.period == InsightsPeriod.YEAR) "按月" else null,
    ) {
        if (!summary.evidence.canDescribePatterns) {
            InsufficientDataNote(summary.evidence.level.description)
            Spacer(Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(if (summary.trendPoints.size > 9) 3.dp else 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            summary.trendPoints.forEach { point ->
                val isCurrent = !today.isBefore(point.startDate) && today.isBefore(point.endDateExclusive)
                val description = buildString {
                    append(point.dateRangeLabel())
                    append("，${point.entryCount} 条记录")
                    point.averageScore?.let { append("，天气平均位置 ${it.oneDecimal()}") }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = description }
                        .clickable(enabled = point.entryCount > 0) { onPointClick(point) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        point.entryCount.takeIf { it > 0 }?.toString().orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier
                            .width(if (summary.trendPoints.size > 9) 13.dp else 22.dp)
                            .height(point.averageScore?.let { (14 + it * 9).dp } ?: 5.dp),
                        shape = CircleShape,
                        color = if (point.averageScore == null) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isCurrent) 1f else 0.48f)
                        },
                    ) {}
                    Spacer(Modifier.height(8.dp))
                    Text(
                        point.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (summary.trendPoints.size > 9) 10.sp else 11.sp,
                        ),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            "柱高表示该时间段在五档天气中的平均位置，数字表示记录条数；点按可查看原始记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoodDistributionCard(
    distribution: List<MoodDistributionItem>,
    onClick: (MoodDistributionItem) -> Unit,
) {
    InsightSectionCard(
        icon = Icons.Outlined.DataUsage,
        index = "分布",
        title = "内在天气出现次数",
    ) {
        distribution.sortedByDescending { it.mood.score }.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.entryCount > 0) { onClick(item) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            item.mood.weatherIcon(),
                            contentDescription = item.mood.label,
                            modifier = Modifier.size(21.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(item.mood.label, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${item.entryCount} 次 · ${(item.ratio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    RatioBar(item.ratio, "${item.mood.label}占 ${(item.ratio * 100).roundToInt()}%，共 ${item.entryCount} 条")
                }
            }
        }
    }
}

@Composable
private fun PeriodComparisonCard(comparison: PeriodComparison, onClick: (previous: Boolean) -> Unit) {
    InsightSectionCard(
        icon = Icons.AutoMirrored.Outlined.CompareArrows,
        index = "对比",
        title = "与前一周期",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ComparisonMetric(
                label = "当前",
                count = comparison.currentEntryCount,
                days = comparison.currentRecordedDayCount,
                average = comparison.currentAverageScore,
                modifier = Modifier.weight(1f),
                onClick = { onClick(false) },
            )
            ComparisonMetric(
                label = "前期",
                count = comparison.entryCount,
                days = comparison.recordedDayCount,
                average = comparison.averageScore,
                modifier = Modifier.weight(1f),
                onClick = { onClick(true) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (comparison.hasEnoughSamples) comparisonDescription(comparison)
            else "两段都至少有 3 条记录后，才描述变化；目前只展示实际计数。",
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
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("$count 次", style = MaterialTheme.typography.titleLarge)
            Text("$days 天 · 均值 ${average?.oneDecimal() ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        index = "关键词",
        title = "反复出现的关键词",
    ) {
        if (tags.isEmpty()) {
            InsufficientDataNote("添加此刻关键词后，这里会显示实际出现次数。")
        } else {
            tags.forEach { tag ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onClick(tag) }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tag.tag, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "当前 ${tag.entryCount} 次 · 前期 ${tag.previousEntryCount} 次",
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
                    "样本较少，关键词仅按次数排序，不解释其意义。",
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
    InsightSectionCard(
        icon = Icons.Outlined.CalendarMonth,
        index = "节奏",
        title = "工作日与周末",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DayTypeMetric(weekday, Modifier.weight(1f)) { onClick(weekday) }
            DayTypeMetric(weekend, Modifier.weight(1f)) { onClick(weekend) }
        }
        Spacer(Modifier.height(10.dp))
        val enough = weekday.entryCount >= 3 && weekend.entryCount >= 3
        Text(
            if (enough) {
                "这里只呈现两类日期的天气位置差异，不说明工作日或周末造成了变化。"
            } else {
                "两类日期分别至少有 3 条记录后，才适合比较均值；目前只展示计数。"
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
        Column(Modifier.padding(14.dp)) {
            Text(insight.type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("${insight.entryCount} 次", style = MaterialTheme.typography.titleLarge)
            Text(
                "${insight.recordedDayCount} / ${insight.elapsedDayCount} 天 · 均值 ${insight.averageScore?.oneDecimal() ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalReviewCard(enabled: Boolean, periodName: String, onOpen: () -> Unit) {
    InsightSectionCard(
        icon = XikeIcons.Mark,
        index = "回顾",
        title = "完全在设备内生成",
    ) {
        Text(
            "把${periodName}的计数、分布和样本限制整理成文字；只有点按分享后，内容才会交给你选择的应用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpen,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            elevation = xikeButtonElevation(),
        ) {
            Text(if (enabled) "查看本地回顾" else "有记录后可生成")
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
    Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(index, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                if (trailing != null) {
                    Spacer(Modifier.weight(1f))
                    Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
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
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightsPeriodSelector(selected: InsightsPeriod, onSelected: (InsightsPeriod) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)) {
        Row(modifier = Modifier.padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            InsightsPeriod.entries.forEach { period ->
                val isSelected = selected == period
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelected(period) },
                        )
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        period.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalReviewDialog(reviewText: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text("本地回顾") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(reviewText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "分享会把以上文字交给你下一步选择的应用。息刻不会自动上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, reviewText)
                    context.startActivity(Intent.createChooser(shareIntent, "分享息刻回顾"))
                },
                elevation = xikeButtonElevation(),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("选择分享应用")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun InsightDrilldownDialog(
    title: String,
    subtitle: String,
    entries: List<JournalEntry>,
    openImage: (String) -> InputStream?,
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
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "drilldown-header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.headlineSmall)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭原始记录")
                        }
                    }
                }
                if (entries.isEmpty()) {
                    item(key = "drilldown-empty") {
                        InsufficientDataNote("这个统计项没有对应记录。")
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
        JournalEntryDetailDialog(entry = entry, onDismiss = { detailEntry = null })
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
    format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
} else if (year == end.year) {
    "$year · ${monthValue}月${dayOfMonth}日 — ${end.monthValue}月${end.dayOfMonth}日"
} else {
    "${year}年${monthValue}月${dayOfMonth}日 — ${end.year}年${end.monthValue}月${end.dayOfMonth}日"
}

private fun weatherBandLabel(average: Double): String = when {
    average >= 4.5 -> "更多晴朗经过"
    average >= 3.5 -> "更多晴间经过"
    average >= 2.5 -> "大多停在微风附近"
    average >= 1.5 -> "低云停留得更多"
    else -> "风雨停留得更多"
}

private fun weatherSummary(average: Double): String = when {
    average >= 4.5 -> "记录里较多是明亮舒展的时刻。"
    average >= 3.5 -> "记录里的云正在散开，轻盈时刻更多。"
    average >= 2.5 -> "记录里微风与起伏都曾经过。"
    average >= 1.5 -> "记录里低云与风雨停留得更多。"
    else -> "记录里风雨时刻占得更多，记得照顾自己。"
}

private fun comparisonDescription(comparison: PeriodComparison): String = buildString {
    append("记录次数${comparison.entryCountDelta.deltaPhrase()}，记录天数${comparison.recordedDayDelta.deltaPhrase()}")
    comparison.averageScoreDelta?.let { delta -> append("，均值${delta.oneDecimalSigned()}") }
    append("。这些是描述性差异，不代表原因。")
}

private fun Int.deltaPhrase(): String = when {
    this > 0 -> "增加 $this"
    this < 0 -> "减少 ${-this}"
    else -> "相同"
}

private fun Int.deltaLabel(): String = when {
    this > 0 -> "+$this"
    this < 0 -> toString()
    else -> "持平"
}

private fun Double.oneDecimal(): String = String.format(Locale.CHINA, "%.1f", this)

private fun Double.oneDecimalSigned(): String = String.format(Locale.CHINA, "%+.1f", this)
