package com.xike.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

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
)

data class JournalPeriodSummary(
    val period: InsightsPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val trendPoints: List<MoodTrendPoint>,
    val entryCount: Int,
    val recordedDayCount: Int,
    val averageScore: Double?,
    val mostUsedTag: String?,
)

fun journalPeriodSummary(
    entries: List<JournalEntry>,
    period: InsightsPeriod,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): JournalPeriodSummary {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val startDate = when (period) {
        InsightsPeriod.WEEK -> weekStart
        InsightsPeriod.DAYS_30 -> today.minusDays(29)
        InsightsPeriod.DAYS_90 -> today.minusDays(89)
        InsightsPeriod.YEAR -> today.withDayOfYear(1)
    }
    val endDateExclusive = when (period) {
        InsightsPeriod.WEEK -> weekStart.plusDays(7)
        else -> today.plusDays(1)
    }
    val datedEntries = entries.map { entry ->
        entry to Instant.ofEpochMilli(entry.createdAt).atZone(zoneId).toLocalDate()
    }
    val periodEntries = datedEntries.filter { (_, date) ->
        !date.isBefore(startDate) && date.isBefore(endDateExclusive)
    }
    val ranges = when (period) {
        InsightsPeriod.WEEK -> (0L..6L).map { offset ->
            val day = startDate.plusDays(offset)
            Triple(day, day.plusDays(1), day.weekdayLabel())
        }
        InsightsPeriod.DAYS_30 -> fixedDayRanges(startDate, endDateExclusive, bucketDays = 5)
        InsightsPeriod.DAYS_90 -> fixedDayRanges(startDate, endDateExclusive, bucketDays = 10)
        InsightsPeriod.YEAR -> (1..today.monthValue).map { month ->
            val monthStart = startDate.withMonth(month)
            Triple(monthStart, minOf(monthStart.plusMonths(1), endDateExclusive), month.toString())
        }
    }
    val trendPoints = ranges.map { (pointStart, pointEnd, label) ->
        val pointEntries = periodEntries.filter { (_, date) ->
            !date.isBefore(pointStart) && date.isBefore(pointEnd)
        }.map { it.first }
        MoodTrendPoint(
            startDate = pointStart,
            endDateExclusive = pointEnd,
            label = label,
            entryCount = pointEntries.size,
            averageScore = pointEntries.moodAverage(),
        )
    }
    val journals = periodEntries.map { it.first }

    return JournalPeriodSummary(
        period = period,
        startDate = startDate,
        endDate = endDateExclusive.minusDays(1),
        trendPoints = trendPoints,
        entryCount = journals.size,
        recordedDayCount = periodEntries.map { it.second }.distinct().size,
        averageScore = journals.moodAverage(),
        mostUsedTag = journals.mostUsedTag(),
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

private fun List<JournalEntry>.moodAverage(): Double? =
    map { it.mood.score }.average().takeUnless(Double::isNaN)

private fun List<JournalEntry>.mostUsedTag(): String? = flatMap { it.tags }
    .groupingBy { it }
    .eachCount()
    .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
    ?.key

private fun LocalDate.weekdayLabel(): String = when (dayOfWeek) {
    java.time.DayOfWeek.MONDAY -> "一"
    java.time.DayOfWeek.TUESDAY -> "二"
    java.time.DayOfWeek.WEDNESDAY -> "三"
    java.time.DayOfWeek.THURSDAY -> "四"
    java.time.DayOfWeek.FRIDAY -> "五"
    java.time.DayOfWeek.SATURDAY -> "六"
    java.time.DayOfWeek.SUNDAY -> "日"
}

fun entriesOnDate(
    entries: List<JournalEntry>,
    date: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Int = entries.count { entry ->
    Instant.ofEpochMilli(entry.createdAt).atZone(zoneId).toLocalDate() == date
}
