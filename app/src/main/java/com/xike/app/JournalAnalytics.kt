package com.xike.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class DailyMoodSummary(
    val date: LocalDate,
    val entryCount: Int,
    val averageScore: Double?,
)

data class WeeklyJournalSummary(
    val weekStart: LocalDate,
    val days: List<DailyMoodSummary>,
    val entryCount: Int,
    val averageScore: Double?,
    val mostUsedTag: String?,
)

enum class InsightsPeriod(
    val label: String,
    val metricLabel: String,
    val trendTitle: String,
    val contextName: String,
) {
    WEEK("本周", "本周记录", "一周的起伏", "本周"),
    DAYS_30("近30天", "30天记录", "最近一个月", "近30天"),
    DAYS_90("近90天", "90天记录", "最近三个月", "近90天"),
    YEAR("今年", "今年记录", "今年的变化", "今年"),
}

data class MoodTrendPoint(
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val label: String,
    val entryCount: Int,
    val averageScore: Double?,
    val entryIds: List<String> = emptyList(),
)

data class MoodDistributionItem(
    val mood: Mood,
    val entryCount: Int,
    val ratio: Double,
    val entryIds: List<String>,
)

data class TagTrendItem(
    val tag: String,
    val entryCount: Int,
    val previousEntryCount: Int,
    val ratio: Double,
    val averageScore: Double?,
    val entryIds: List<String>,
) {
    val countDelta: Int
        get() = entryCount - previousEntryCount
}

enum class DayType(val label: String) {
    WEEKDAY("工作日"),
    WEEKEND("周末"),
}

data class DayTypeInsight(
    val type: DayType,
    val entryCount: Int,
    val recordedDayCount: Int,
    val elapsedDayCount: Int,
    val averageScore: Double?,
    val entryIds: List<String>,
) {
    val coverageRatio: Double
        get() = if (elapsedDayCount == 0) 0.0 else recordedDayCount.toDouble() / elapsedDayCount
}

enum class InsightEvidenceLevel(
    val label: String,
    val description: String,
) {
    NONE("暂无样本", "还没有记录，因此不生成趋势判断。"),
    LIMITED("样本很少", "当前只展示事实，不解读变化或差异。"),
    DEVELOPING("初步线索", "可以观察分布，但仍不适合归因。"),
    ESTABLISHED("可供回顾", "样本足以描述这段记录，仍不代表原因或诊断。"),
}

data class InsightEvidence(
    val level: InsightEvidenceLevel,
    val entryCount: Int,
    val recordedDayCount: Int,
    val elapsedDayCount: Int,
) {
    val coverageRatio: Double
        get() = if (elapsedDayCount == 0) 0.0 else recordedDayCount.toDouble() / elapsedDayCount

    val canDescribePatterns: Boolean
        get() = level >= InsightEvidenceLevel.DEVELOPING
}

data class PeriodComparison(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val entryCount: Int,
    val recordedDayCount: Int,
    val averageScore: Double?,
    val entryIds: List<String>,
    val currentEntryCount: Int,
    val currentRecordedDayCount: Int,
    val currentAverageScore: Double?,
) {
    val entryCountDelta: Int
        get() = currentEntryCount - entryCount

    val recordedDayDelta: Int
        get() = currentRecordedDayCount - recordedDayCount

    val averageScoreDelta: Double?
        get() = currentAverageScore?.let { current -> averageScore?.let { current - it } }

    val hasEnoughSamples: Boolean
        get() = currentEntryCount >= MIN_COMPARISON_ENTRIES && entryCount >= MIN_COMPARISON_ENTRIES
}

data class JournalPeriodSummary(
    val period: InsightsPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val trendPoints: List<MoodTrendPoint>,
    val entryCount: Int,
    val recordedDayCount: Int,
    val averageScore: Double?,
    val mostUsedTag: String?,
    val moodDistribution: List<MoodDistributionItem>,
    val topTags: List<TagTrendItem>,
    val weekdayInsight: DayTypeInsight,
    val weekendInsight: DayTypeInsight,
    val evidence: InsightEvidence,
    val comparison: PeriodComparison,
    val entryIds: List<String>,
) {
    val averageEntriesPerRecordedDay: Double?
        get() = if (recordedDayCount == 0) null else entryCount.toDouble() / recordedDayCount
}

private data class DatedJournalEntry(
    val entry: JournalEntry,
    val date: LocalDate,
)

private data class PeriodBounds(
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val trendEndDateExclusive: LocalDate,
)

private const val MIN_COMPARISON_ENTRIES = 3
private const val MAX_TAG_INSIGHTS = 5

fun journalPeriodSummary(
    entries: List<JournalEntry>,
    period: InsightsPeriod,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): JournalPeriodSummary {
    val bounds = period.bounds(today)
    val previousBounds = period.previousBounds(today, bounds)
    val datedEntries = entries.map { entry ->
        DatedJournalEntry(
            entry = entry,
            date = Instant.ofEpochMilli(entry.createdAt).atZone(zoneId).toLocalDate(),
        )
    }
    val periodEntries = datedEntries.inRange(bounds.startDate, bounds.endDateExclusive)
    val previousEntries = datedEntries.inRange(previousBounds.first, previousBounds.second)
    val journals = periodEntries.map(DatedJournalEntry::entry)
    val previousJournals = previousEntries.map(DatedJournalEntry::entry)
    val ranges = period.trendRanges(bounds)
    val trendPoints = ranges.map { (pointStart, pointEnd, label) ->
        val pointEntries = periodEntries
            .filter { dated -> !dated.date.isBefore(pointStart) && dated.date.isBefore(pointEnd) }
            .map(DatedJournalEntry::entry)
        MoodTrendPoint(
            startDate = pointStart,
            endDateExclusive = pointEnd,
            label = label,
            entryCount = pointEntries.size,
            averageScore = pointEntries.moodAverage(),
            entryIds = pointEntries.map(JournalEntry::id),
        )
    }
    val recordedDayCount = periodEntries.map(DatedJournalEntry::date).distinct().size
    val elapsedDayCount = ChronoUnit.DAYS.between(bounds.startDate, bounds.endDateExclusive).toInt()
    val evidence = InsightEvidence(
        level = evidenceLevel(journals.size, recordedDayCount),
        entryCount = journals.size,
        recordedDayCount = recordedDayCount,
        elapsedDayCount = elapsedDayCount,
    )
    val previousTagCounts = previousJournals.tagCounts()
    val topTags = journals.tagCounts().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(MAX_TAG_INSIGHTS)
        .map { (tag, count) ->
            val taggedEntries = journals.filter { tag in it.tags }
            TagTrendItem(
                tag = tag,
                entryCount = count,
                previousEntryCount = previousTagCounts[tag] ?: 0,
                ratio = count.toDouble() / journals.size.coerceAtLeast(1),
                averageScore = taggedEntries.moodAverage(),
                entryIds = taggedEntries.map(JournalEntry::id),
            )
        }
    val weekdayInsight = periodEntries.dayTypeInsight(
        type = DayType.WEEKDAY,
        startDate = bounds.startDate,
        endDateExclusive = bounds.endDateExclusive,
    )
    val weekendInsight = periodEntries.dayTypeInsight(
        type = DayType.WEEKEND,
        startDate = bounds.startDate,
        endDateExclusive = bounds.endDateExclusive,
    )
    val previousRecordedDays = previousEntries.map(DatedJournalEntry::date).distinct().size

    return JournalPeriodSummary(
        period = period,
        startDate = bounds.startDate,
        endDate = bounds.endDateExclusive.minusDays(1),
        trendPoints = trendPoints,
        entryCount = journals.size,
        recordedDayCount = recordedDayCount,
        averageScore = journals.moodAverage(),
        mostUsedTag = topTags.firstOrNull()?.tag,
        moodDistribution = Mood.entries.map { mood ->
            val moodEntries = journals.filter { it.mood == mood }
            MoodDistributionItem(
                mood = mood,
                entryCount = moodEntries.size,
                ratio = moodEntries.size.toDouble() / journals.size.coerceAtLeast(1),
                entryIds = moodEntries.map(JournalEntry::id),
            )
        },
        topTags = topTags,
        weekdayInsight = weekdayInsight,
        weekendInsight = weekendInsight,
        evidence = evidence,
        comparison = PeriodComparison(
            startDate = previousBounds.first,
            endDate = previousBounds.second.minusDays(1),
            entryCount = previousJournals.size,
            recordedDayCount = previousRecordedDays,
            averageScore = previousJournals.moodAverage(),
            entryIds = previousJournals.map(JournalEntry::id),
            currentEntryCount = journals.size,
            currentRecordedDayCount = recordedDayCount,
            currentAverageScore = journals.moodAverage(),
        ),
        entryIds = journals.map(JournalEntry::id),
    )
}

fun weeklyJournalSummary(
    entries: List<JournalEntry>,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): WeeklyJournalSummary {
    val period = journalPeriodSummary(entries, InsightsPeriod.WEEK, today, zoneId)

    return WeeklyJournalSummary(
        weekStart = period.startDate,
        days = period.trendPoints.map { point ->
            DailyMoodSummary(
                date = point.startDate,
                entryCount = point.entryCount,
                averageScore = point.averageScore,
            )
        },
        entryCount = period.entryCount,
        averageScore = period.averageScore,
        mostUsedTag = period.mostUsedTag,
    )
}

internal fun entriesWithIds(entries: List<JournalEntry>, entryIds: Collection<String>): List<JournalEntry> {
    val ids = entryIds.toSet()
    return entries.filter { it.id in ids }.sortedByDescending(JournalEntry::createdAt)
}

internal fun localReviewText(summary: JournalPeriodSummary): String {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    val moodLine = summary.moodDistribution
        .filter { it.entryCount > 0 }
        .sortedByDescending(MoodDistributionItem::entryCount)
        .joinToString("、") { "${it.mood.label} ${it.entryCount} 次" }
        .ifBlank { "暂无心情分布" }
    val tagLine = summary.topTags.take(3)
        .joinToString("、") { "${it.tag} ${it.entryCount} 次" }
        .ifBlank { "暂无关键词" }
    val comparisonLine = if (summary.comparison.hasEnoughSamples) {
        "相比前一周期，记录次数${summary.comparison.entryCountDelta.signedCount()}，记录天数${summary.comparison.recordedDayDelta.signedCount()}。"
    } else {
        "当前或前一周期样本少于 $MIN_COMPARISON_ENTRIES 条，因此不解读周期变化。"
    }
    return buildString {
        appendLine("息刻 · ${summary.period.contextName}本地回顾")
        appendLine("${summary.startDate.format(dateFormatter)} — ${summary.endDate.format(dateFormatter)}")
        appendLine("记录 ${summary.entryCount} 次，分布在 ${summary.recordedDayCount} 天。")
        appendLine("心情分布：$moodLine。")
        appendLine("常见关键词：$tagLine。")
        appendLine(comparisonLine)
        append("这些是本机记录的描述性统计，不代表原因、诊断或建议。")
    }
}

private fun InsightsPeriod.bounds(today: LocalDate): PeriodBounds {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val start = when (this) {
        InsightsPeriod.WEEK -> weekStart
        InsightsPeriod.DAYS_30 -> today.minusDays(29)
        InsightsPeriod.DAYS_90 -> today.minusDays(89)
        InsightsPeriod.YEAR -> today.withDayOfYear(1)
    }
    return PeriodBounds(
        startDate = start,
        endDateExclusive = today.plusDays(1),
        trendEndDateExclusive = if (this == InsightsPeriod.WEEK) weekStart.plusDays(7) else today.plusDays(1),
    )
}

private fun InsightsPeriod.previousBounds(
    today: LocalDate,
    current: PeriodBounds,
): Pair<LocalDate, LocalDate> = when (this) {
    InsightsPeriod.WEEK -> {
        val elapsedDays = ChronoUnit.DAYS.between(current.startDate, current.endDateExclusive)
        current.startDate.minusWeeks(1) to current.startDate.minusWeeks(1).plusDays(elapsedDays)
    }
    InsightsPeriod.DAYS_30 -> current.startDate.minusDays(30) to current.startDate
    InsightsPeriod.DAYS_90 -> current.startDate.minusDays(90) to current.startDate
    InsightsPeriod.YEAR -> {
        val previousEquivalentDate = today.minusYears(1)
        previousEquivalentDate.withDayOfYear(1) to previousEquivalentDate.plusDays(1)
    }
}

private fun InsightsPeriod.trendRanges(bounds: PeriodBounds): List<Triple<LocalDate, LocalDate, String>> = when (this) {
    InsightsPeriod.WEEK -> (0L..6L).map { offset ->
        val day = bounds.startDate.plusDays(offset)
        Triple(day, day.plusDays(1), day.weekdayLabel())
    }
    InsightsPeriod.DAYS_30 -> fixedDayRanges(bounds.startDate, bounds.trendEndDateExclusive, bucketDays = 5)
    InsightsPeriod.DAYS_90 -> fixedDayRanges(bounds.startDate, bounds.trendEndDateExclusive, bucketDays = 10)
    InsightsPeriod.YEAR -> (1..bounds.endDateExclusive.minusDays(1).monthValue).map { month ->
        val monthStart = bounds.startDate.withMonth(month)
        Triple(
            monthStart,
            minOf(monthStart.plusMonths(1), bounds.trendEndDateExclusive),
            month.toString(),
        )
    }
}

private fun fixedDayRanges(
    startDate: LocalDate,
    endDateExclusive: LocalDate,
    bucketDays: Long,
): List<Triple<LocalDate, LocalDate, String>> {
    val ranges = mutableListOf<Triple<LocalDate, LocalDate, String>>()
    var cursor = startDate
    while (cursor.isBefore(endDateExclusive)) {
        val end = minOf(cursor.plusDays(bucketDays), endDateExclusive)
        ranges += Triple(cursor, end, "${cursor.monthValue}/${cursor.dayOfMonth}")
        cursor = end
    }
    return ranges
}

private fun List<DatedJournalEntry>.inRange(
    startDate: LocalDate,
    endDateExclusive: LocalDate,
): List<DatedJournalEntry> = filter { dated ->
    !dated.date.isBefore(startDate) && dated.date.isBefore(endDateExclusive)
}

private fun List<DatedJournalEntry>.dayTypeInsight(
    type: DayType,
    startDate: LocalDate,
    endDateExclusive: LocalDate,
): DayTypeInsight {
    val selected = filter { dated ->
        val isWeekend = dated.date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        (type == DayType.WEEKEND) == isWeekend
    }
    val journals = selected.map(DatedJournalEntry::entry)
    val elapsedDayCount = generateSequence(startDate) { date -> date.plusDays(1) }
        .takeWhile { date -> date.isBefore(endDateExclusive) }
        .count { date ->
            val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            (type == DayType.WEEKEND) == isWeekend
        }
    return DayTypeInsight(
        type = type,
        entryCount = journals.size,
        recordedDayCount = selected.map(DatedJournalEntry::date).distinct().size,
        elapsedDayCount = elapsedDayCount,
        averageScore = journals.moodAverage(),
        entryIds = journals.map(JournalEntry::id),
    )
}

private fun evidenceLevel(entryCount: Int, recordedDayCount: Int): InsightEvidenceLevel = when {
    entryCount == 0 -> InsightEvidenceLevel.NONE
    entryCount < 3 || recordedDayCount < 2 -> InsightEvidenceLevel.LIMITED
    entryCount < 10 || recordedDayCount < 5 -> InsightEvidenceLevel.DEVELOPING
    else -> InsightEvidenceLevel.ESTABLISHED
}

private fun List<JournalEntry>.moodAverage(): Double? =
    map { it.mood.score }.average().takeUnless(Double::isNaN)

private fun List<JournalEntry>.tagCounts(): Map<String, Int> = flatMap { entry -> entry.tags.distinct() }
    .groupingBy { it }
    .eachCount()

private fun LocalDate.weekdayLabel(): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private fun Int.signedCount(): String = when {
    this > 0 -> "增加 $this"
    this < 0 -> "减少 ${-this}"
    else -> "相同"
}

fun entriesOnDate(
    entries: List<JournalEntry>,
    date: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Int = entries.count { entry ->
    Instant.ofEpochMilli(entry.createdAt).atZone(zoneId).toLocalDate() == date
}
