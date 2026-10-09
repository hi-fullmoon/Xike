package com.xike.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ReviewSource(val label: String, val entryIds: List<String>)
internal data class ReviewSection(val title: String, val text: String, val sources: List<ReviewSource> = emptyList())
internal data class LocalJournalReview(val title: String, val dateRange: String, val sections: List<ReviewSection>) {
    val text: String
        get() = buildString {
            appendLine(title)
            appendLine(dateRange)
            sections.forEach { section ->
                appendLine()
                appendLine(section.title)
                appendLine(section.text)
            }
        }.trimEnd()
}

internal fun localReviewText(summary: JournalPeriodSummary): String = localJournalReview(summary).text

internal fun localJournalReview(summary: JournalPeriodSummary): LocalJournalReview {
    val sections = mutableListOf<ReviewSection>()
    val evidence = summary.evidence
    val moods = summary.moodDistribution.filter { it.entryCount > 0 }.sortedByDescending { it.entryCount }
    val dominant = moods.filter { it.entryCount == moods.firstOrNull()?.entryCount }
    val moodNames = dominant.joinToString(tr("、", ", ")) { it.mood.label }
    sections += ReviewSection(
        tr("这段时间的概况", "Period overview"),
        tr("这段时间留下了 ${summary.entryCount} 条记录，涉及 ${summary.recordedDayCount} 天，占已过去 ${evidence.elapsedDayCount} 天的 ${percent(evidence.coverageRatio)}。",
            "You kept ${summary.entryCount} entries across ${summary.recordedDayCount} days, covering ${percent(evidence.coverageRatio)} of the ${evidence.elapsedDayCount} elapsed days.") +
            when {
                summary.entryCount == 0 -> tr("还没有记录，暂不生成心情或主题判断。", "There are no entries yet, so no mood or topic observations are made.")
                !evidence.canDescribePatterns -> tr("目前记录较少或集中在一天，以下只整理已记录的事实。", "Entries are few or concentrated on one day; this review describes recorded facts only.")
                dominant.size == moods.size && moods.size > 1 -> tr("记录中的各类心情次数相同，没有单一最常见的心情。", "All recorded moods occur equally often; no single mood is most frequent.")
                else -> tr("记录中最常出现的心情是 $moodNames${if (dominant.size > 1) "（并列）" else ""}。", "The most frequent recorded mood${if (dominant.size > 1) "s are" else " is"} $moodNames${if (dominant.size > 1) " (tied)" else ""}.")
            },
        if (summary.entryCount > 0) listOf(ReviewSource(tr("查看本期记录", "View this period's entries"), summary.entryIds)) else emptyList(),
    )
    if (summary.entryCount > 0) {
        val distribution = moods.joinToString(tr("；", "; ")) {
            tr("${it.mood.label} ${it.entryCount} 次（${percent(it.ratio)}）", "${it.mood.label}: ${it.entryCount} entries (${percent(it.ratio)})")
        }
        sections += ReviewSection(tr("心情怎样变化", "Mood over time"),
            distribution + tr("。分布按记录条数计算，同一天的多条记录会分别计入。", ". The distribution counts entries, including multiple entries on the same day.") +
                if (evidence.canDescribePatterns) tr("时间变化按每天的记录先汇总，不让记录较多的一天获得更高权重。", "Time comparisons summarize each recorded day first, giving days equal weight.")
                else tr("目前不判断时间趋势。", "Time trends are not interpreted yet."))

        val tags = summary.topTags.take(3)
        sections += ReviewSection(tr("哪些主题出现了", "Recorded topics"),
            if (tags.isEmpty()) tr("这些记录没有标记主题，因此不推断生活事件。", "These entries have no topic tags, so life events are not inferred.")
            else tags.joinToString("\n") { tag ->
                val counts = tag.moodCounts.entries.sortedByDescending { it.value }.joinToString(tr("、", ", ")) {
                    tr("${it.key.label} ${it.value} 次", "${it.key.label}: ${it.value}")
                }
                tr("“${localizedText(tag.tag)}”出现于 ${tag.entryCount} 条记录、${tag.recordedDayCount} 天；伴随的心情：$counts。",
                    "“${localizedText(tag.tag)}” appears in ${tag.entryCount} entries across ${tag.recordedDayCount} days; recorded moods: $counts.")
            } + tr("\n主题可能同时标记；共同出现不代表原因，未标记也不代表没有发生。", "\nTopics may overlap. Co-occurrence does not establish cause, and an absent tag does not mean an event did not happen."),
            tags.map { ReviewSource(tr("回看“${localizedText(it.tag)}”", "Revisit “${localizedText(it.tag)}”"), it.entryIds) })

        sections += comparisonSection(summary)
        if (evidence.canDescribePatterns) sections += highlightsSection(summary)
        val repeated = tags.firstOrNull { it.recordedDayCount >= 2 }
        sections += ReviewSection(tr("留给自己的一个问题", "A question for reflection"),
            if (repeated != null) tr("在记录了“${localizedText(repeated.tag)}”的不同日子里，当时的具体情境和感受有什么相同或不同？",
                "Across the days tagged “${localizedText(repeated.tag)}”, what was similar or different about the situations and your feelings?")
            else tr("回看其中一条记录：当时发生了什么，哪些细节是你现在仍想记住的？", "Revisit one entry: what was happening, and which details would you still like to remember?"))
    }
    sections += ReviewSection(tr("这份回顾的范围", "Scope of this review"),
        tr("仅依据本机的日期、心情和主题生成，不分析日记正文、照片或录音。没有记录的日期不参与心情判断，也不等于心情平稳。这些是本机记录的描述性统计，不代表原因、诊断或建议。",
            "Generated on your device from dates, moods and topic tags; journal text, photos and audio are not analyzed. Unrecorded days are excluded from mood observations and do not imply a steady mood. These descriptive statistics do not establish causes, diagnoses or advice."))
    return LocalJournalReview(tr("息刻 · ${summary.period.contextName}本地回顾", "Xike · Local review: ${summary.period.contextName}"),
        "${reviewDate(summary.startDate)} — ${reviewDate(summary.endDate)}", sections)
}

private fun comparisonSection(summary: JournalPeriodSummary): ReviewSection {
    val previous = summary.comparison
    val enough = summary.evidence.canDescribePatterns && previous.hasEnoughSamples && previous.recordedDayCount >= 2
    val text = buildString {
        append(tr("对照时段：${reviewDate(previous.startDate)} — ${reviewDate(previous.endDate)}。本期 ${summary.entryCount} 条、${summary.recordedDayCount} 天；前期 ${previous.entryCount} 条、${previous.recordedDayCount} 天。",
            "Comparison window: ${reviewDate(previous.startDate)} — ${reviewDate(previous.endDate)}. Current: ${summary.entryCount} entries across ${summary.recordedDayCount} days; previous: ${previous.entryCount} across ${previous.recordedDayCount} days."))
        if (!enough) {
            append(tr("当前或前一周期样本少于 3 条，或记录不足两天，因此不解读周期变化。", "One period has fewer than 3 entries or fewer than two recorded days, so period changes are not interpreted."))
        } else {
            val changes = summary.moodDistribution.map { item ->
                item.mood to reviewMoodShareChange(item.ratio, (summary.previousMoodCounts[item.mood] ?: 0).toDouble() / previous.entryCount)
            }.sortedByDescending { abs(it.second) }.filter { it.second != 0 }.take(2)
            append(if (changes.isEmpty()) tr("两期心情占比按整数百分比看没有变化。", "Mood shares show no change at whole-percentage precision.")
            else changes.joinToString(tr("；", "; "), prefix = "\n") { (mood, delta) ->
                tr("${mood.label}的记录占比${if (delta > 0) "增加" else "减少"} ${abs(delta)} 个百分点",
                    "${mood.label} share ${if (delta > 0) "increased" else "decreased"} by ${abs(delta)} percentage points")
            } + tr("。", "."))
            summary.topTags.take(2).forEach { tag ->
                append(tr("\n“${localizedText(tag.tag)}”：本期 ${tag.entryCount} 条，前期 ${tag.previousEntryCount} 条。", "\n“${localizedText(tag.tag)}”: ${tag.entryCount} entries this period, ${tag.previousEntryCount} previously."))
            }
            append(tr("记录频率和覆盖不同，差异只描述记录本身，不代表整体生活变好或变差。", "Recording frequency and coverage differ; these changes describe entries, not an overall improvement or decline in life."))
        }
    }
    return ReviewSection(tr("与上一阶段相比", "Compared with the previous period"), text,
        if (previous.entryCount > 0) listOf(ReviewSource(tr("查看对照时段记录", "View comparison entries"), previous.entryIds)) else emptyList())
}

private fun highlightsSection(summary: JournalPeriodSummary): ReviewSection {
    val days = summary.reviewDays.filter { it.averageScore != null }
    val low = days.minOfOrNull { it.averageScore!! }
    val high = days.maxOfOrNull { it.averageScore!! }
    if (low == null || high == low) return ReviewSection(tr("值得回看的片段", "Moments to revisit"),
        tr("有记录日期的日均心情相同，不挑选较高或较低的一天。可以从本期记录中选择自己想回看的片段。", "Recorded daily mood averages are equal, so no higher or lower day is selected. Choose a moment from this period that you would like to revisit."))
    val higher = days.filter { it.averageScore == high }
    val lower = days.filter { it.averageScore == low }
    val first = days.first().averageScore!!
    val last = days.last().averageScore!!
    val direction = when {
        last > first -> tr("最后一个有记录日期的日均心情高于第一个", "The last recorded daily mood average is higher than the first")
        last < first -> tr("最后一个有记录日期的日均心情低于第一个", "The last recorded daily mood average is lower than the first")
        else -> tr("首尾两个有记录日期的日均心情相同", "The first and last recorded daily mood averages are equal")
    }
    return ReviewSection(tr("值得回看的片段", "Moments to revisit"),
        tr("按五档心情的日均值比较，${reviewDate(higher.first().startDate)}是记录中较高的一天（共 ${higher.size} 天并列）；${reviewDate(lower.first().startDate)}是较低的一天（共 ${lower.size} 天并列）。",
            "Using daily averages of the five mood categories, ${reviewDate(higher.first().startDate)} is a higher day (${higher.size} tied days), and ${reviewDate(lower.first().startDate)} a lower day (${lower.size} tied days).") +
            "$direction" + tr("，这只是首尾对照，不代表持续趋势。所选日期展示当天全部记录，不代表最重要的事件。", "; this is an endpoint comparison, not a sustained trend. Selected days show all their entries and do not identify the most important events."),
        listOf(ReviewSource(tr("回看较高的日子", "Revisit higher days"), higher.flatMap { it.entryIds }),
            ReviewSource(tr("回看较低的日子", "Revisit lower days"), lower.flatMap { it.entryIds })))
}

private fun roundedPercent(ratio: Double): Int = (ratio * 100).roundToInt()
internal fun reviewMoodShareChange(currentRatio: Double, previousRatio: Double): Int =
    roundedPercent(currentRatio) - roundedPercent(previousRatio)
private fun percent(ratio: Double): String = "${roundedPercent(ratio)}%"
private fun reviewDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern(tr("yyyy年M月d日", "MMM d, yyyy"), AppLocale.locale))
