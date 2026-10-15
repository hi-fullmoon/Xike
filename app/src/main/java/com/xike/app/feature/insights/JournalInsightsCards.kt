package com.xike.app.feature.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.xike.app.AppLocale
import com.xike.app.data.Mood
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.components.MoodEmoji
import com.xike.app.ui.components.XikeIcons
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.xikeButtonElevation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun InsightsOverviewCard(summary: JournalPeriodSummary, onClick: () -> Unit) {
    val stackContent = LocalDensity.current.fontScale >= 1.5f
    val headline =
        when {
            summary.entryCount == 0 -> localizedText("等待第一条心情记录")
            !summary.evidence.canDescribePatterns ->
                tr("已留下 ${summary.entryCount} 条心情记录", "${summary.entryCount} mood entries kept")
            else -> summary.averageScore?.let(::moodBandLabel) ?: localizedText("等待第一条心情记录")
        }
    Surface(
        modifier =
            Modifier.fillMaxWidth().clickable(enabled = summary.entryCount > 0, onClick = onClick),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(XikeContentCardPadding)) {
            if (stackContent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr(
                            "${summary.period.contextName} · 心情",
                            "${summary.period.contextName} · Mood",
                        ),
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
                            tr(
                                "${summary.period.contextName} · 心情",
                                "${summary.period.contextName} · Mood",
                            ),
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
                    else ->
                        summary.averageScore?.let(::moodSummary)
                            ?: localizedText("记录此刻的心情，让轨迹从这里开始。")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
            )
            Spacer(Modifier.height(18.dp))
            if (stackContent) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverviewMetric(
                        localizedText("记录"),
                        tr("${summary.entryCount} 次", "${summary.entryCount} entries"),
                        Modifier.fillMaxWidth(),
                    )
                    OverviewMetric(
                        localizedText("留下痕迹"),
                        tr("${summary.recordedDayCount} 天", "${summary.recordedDayCount} days"),
                        Modifier.fillMaxWidth(),
                    )
                    OverviewMetric(
                        localizedText("日期覆盖"),
                        "${(summary.evidence.coverageRatio * 100).roundToInt()}%",
                        Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OverviewMetric(
                        localizedText("记录"),
                        tr("${summary.entryCount} 次", "${summary.entryCount} entries"),
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                    OverviewMetric(
                        localizedText("留下痕迹"),
                        tr("${summary.recordedDayCount} 天", "${summary.recordedDayCount} days"),
                        Modifier.weight(1f).fillMaxHeight(),
                    )
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
                description =
                    tr(
                        "数据覆盖：${summary.evidence.elapsedDayCount} 天中有 ${summary.evidence.recordedDayCount} 天存在记录",
                        "Data coverage: entries on ${summary.evidence.recordedDayCount} of ${summary.evidence.elapsedDayCount} days",
                    ),
            )
            Spacer(Modifier.height(9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = if (stackContent) 1 else 2,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        summary.evidence.level.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
                Text(
                    "—",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                MoodEmoji(summary.averageScore.averageMood(), size = 29.dp)
            }
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
internal fun PeriodComparisonCard(
    comparison: PeriodComparison,
    onClick: (previous: Boolean) -> Unit,
) {
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
                ) {
                    onClick(false)
                }
                ComparisonMetric(
                    localizedText("前期"),
                    comparison.entryCount,
                    comparison.recordedDayCount,
                    comparison.averageScore,
                    Modifier.fillMaxWidth(),
                ) {
                    onClick(true)
                }
            }
        } else {
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ComparisonMetric(
                    localizedText("当前"),
                    comparison.currentEntryCount,
                    comparison.currentRecordedDayCount,
                    comparison.currentAverageScore,
                    Modifier.weight(1f).fillMaxHeight(),
                ) {
                    onClick(false)
                }
                ComparisonMetric(
                    localizedText("前期"),
                    comparison.entryCount,
                    comparison.recordedDayCount,
                    comparison.averageScore,
                    Modifier.weight(1f).fillMaxHeight(),
                ) {
                    onClick(true)
                }
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
            InsightMetricLabel(label, clickable = count > 0)
            Spacer(Modifier.height(6.dp))
            Text(tr("$count 次", "$count entries"), style = MaterialTheme.typography.titleLarge)
            Text(
                tr(
                    "$days 天 · 均值 ${average?.oneDecimal() ?: "—"}",
                    "$days days · Average ${average?.oneDecimal() ?: "—"}",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TagTrendsCard(
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
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onClick(tag) }
                            .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(localizedText(tag.tag), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr(
                                "当前 ${tag.entryCount} 次 · 前期 ${tag.previousEntryCount} 次",
                                "Current ${tag.entryCount} · Previous ${tag.previousEntryCount}",
                            ),
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
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
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
internal fun DayTypeCard(
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
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
            InsightMetricLabel(insight.type.label, clickable = insight.entryCount > 0)
            Spacer(Modifier.height(6.dp))
            Text(
                tr("${insight.entryCount} 次", "${insight.entryCount} entries"),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                tr(
                    "${insight.recordedDayCount} / ${insight.elapsedDayCount} 天 · 均值 ${insight.averageScore?.oneDecimal() ?: "—"}",
                    "${insight.recordedDayCount} / ${insight.elapsedDayCount} days · Average ${insight.averageScore?.oneDecimal() ?: "—"}",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
            )
        }
    }
}

@Composable
private fun InsightMetricLabel(label: String, clickable: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (clickable) {
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

@Composable
internal fun LocalReviewCard(enabled: Boolean, periodName: String, onOpen: () -> Unit) {
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
                tr(
                    "整理心情变化、常见主题和值得回看的片段，形成一份有记录依据的回顾。",
                    "Review mood changes, recurring topics and moments worth revisiting, grounded in your entries.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpen,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor =
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        disabledContentColor =
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.52f),
                    ),
                elevation = xikeButtonElevation(),
            ) {
                Text(if (enabled) localizedText("查看本地回顾") else localizedText("有记录后可生成"))
            }
        }
    }
}

internal fun JournalPeriodSummary.dateRangeLabel(): String = startDate.asDateRange(endDate)

internal fun PeriodComparison.dateRangeLabel(): String = startDate.asDateRange(endDate)

internal fun MoodTrendPoint.dateRangeLabel(): String =
    startDate.asDateRange(endDateExclusive.minusDays(1))

private fun LocalDate.asDateRange(end: LocalDate): String =
    if (this == end) {
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

private fun moodBandLabel(average: Double): String =
    when {
        average >= 4.5 -> tr("心情均值很高", "Very high mood average")
        average >= 3.5 -> tr("心情均值较高", "Higher mood average")
        average >= 2.5 -> tr("心情均值居中", "Mid-range mood average")
        average >= 1.5 -> tr("心情均值较低", "Lower mood average")
        else -> tr("心情均值很低", "Very low mood average")
    }

private fun Double.averageMood(): Mood =
    when {
        this >= 4.5 -> Mood.VERY_GOOD
        this >= 3.5 -> Mood.ABOVE_AVERAGE
        this >= 2.5 -> Mood.NEUTRAL
        this >= 1.5 -> Mood.BELOW_AVERAGE
        else -> Mood.VERY_LOW
    }

private fun moodSummary(average: Double): String =
    tr(
        "五档记录的平均分为 ${average.oneDecimal()}。均值不代表某一种具体感受。",
        "The five-level average is ${average.oneDecimal()}. An average does not identify a specific feeling.",
    )

private fun comparisonDescription(comparison: PeriodComparison): String = buildString {
    append(
        tr(
            "记录次数${comparison.entryCountDelta.deltaPhrase()}，记录天数${comparison.recordedDayDelta.deltaPhrase()}",
            "Entries ${comparison.entryCountDelta.deltaPhrase()}, days recorded ${comparison.recordedDayDelta.deltaPhrase()}",
        )
    )
    comparison.averageScoreDelta?.let { delta ->
        append(tr("，均值${delta.oneDecimalSigned()}", ", average ${delta.oneDecimalSigned()}"))
    }
    append(localizedText("。这些是描述性差异，不代表原因。"))
}

private fun Int.deltaPhrase(): String =
    when {
        this > 0 -> tr("增加 $this", "increased by $this")
        this < 0 -> tr("减少 ${-this}", "decreased by ${-this}")
        else -> localizedText("相同")
    }

private fun Int.deltaLabel(): String =
    when {
        this > 0 -> "+$this"
        this < 0 -> toString()
        else -> localizedText("持平")
    }

internal fun Double.oneDecimal(): String = String.format(AppLocale.locale, "%.1f", this)

private fun Double.oneDecimalSigned(): String = String.format(AppLocale.locale, "%+.1f", this)
