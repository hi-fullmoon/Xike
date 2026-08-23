package com.xike.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalAnalyticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 8, 23)

    @Test
    fun `weekly summary uses the calendar week instead of the latest seven entries`() {
        val entries = listOf(
            entry(LocalDate.of(2026, 8, 17), Mood.GOOD, listOf("工作")),
            entry(LocalDate.of(2026, 8, 19), Mood.JOYFUL, listOf("工作", "运动")),
            entry(LocalDate.of(2026, 8, 19), Mood.LOW, listOf("睡眠")),
            entry(LocalDate.of(2026, 8, 16), Mood.JOYFUL, listOf("朋友")),
            entry(LocalDate.of(2026, 8, 24), Mood.JOYFUL, listOf("朋友")),
        )

        val summary = weeklyJournalSummary(entries, today, zone)

        assertEquals(LocalDate.of(2026, 8, 17), summary.weekStart)
        assertEquals(3, summary.entryCount)
        assertEquals(3.33, summary.averageScore!!, 0.01)
        assertEquals("工作", summary.mostUsedTag)
    }

    @Test
    fun `weekly summary contains all seven days including empty days`() {
        val summary = weeklyJournalSummary(
            listOf(
                entry(LocalDate.of(2026, 8, 19), Mood.GOOD),
                entry(LocalDate.of(2026, 8, 19), Mood.TIRED),
            ),
            today,
            zone,
        )

        assertEquals(7, summary.days.size)
        assertEquals(2, summary.days[2].entryCount)
        assertEquals(3.0, summary.days[2].averageScore!!, 0.0)
        assertNull(summary.days[0].averageScore)
    }

    @Test
    fun `entries on date respects the supplied timezone`() {
        val lateUtcEntry = JournalEntry(
            createdAt = LocalDate.of(2026, 8, 22).atTime(16, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            mood = Mood.CALM,
            tags = emptyList(),
            note = "",
        )

        assertEquals(1, entriesOnDate(listOf(lateUtcEntry), today, zone))
        assertEquals(0, entriesOnDate(listOf(lateUtcEntry), today, ZoneId.of("UTC")))
    }

    @Test
    fun `insight presets use inclusive calendar boundaries`() {
        val entries = listOf(
            entry(today, Mood.GOOD),
            entry(LocalDate.of(2026, 8, 17), Mood.CALM),
            entry(today.minusDays(29), Mood.GOOD),
            entry(today.minusDays(30), Mood.LOW),
            entry(today.minusDays(89), Mood.JOYFUL),
            entry(today.minusDays(90), Mood.TIRED),
            entry(LocalDate.of(2026, 1, 1), Mood.CALM),
            entry(LocalDate.of(2025, 12, 31), Mood.JOYFUL),
            entry(today.plusDays(1), Mood.JOYFUL),
        )

        assertEquals(2, journalPeriodSummary(entries, InsightsPeriod.WEEK, today, zone).entryCount)
        assertEquals(3, journalPeriodSummary(entries, InsightsPeriod.DAYS_30, today, zone).entryCount)
        assertEquals(5, journalPeriodSummary(entries, InsightsPeriod.DAYS_90, today, zone).entryCount)
        assertEquals(7, journalPeriodSummary(entries, InsightsPeriod.YEAR, today, zone).entryCount)
    }

    @Test
    fun `insight presets generate readable trend buckets`() {
        val entries = listOf(
            entry(today, Mood.GOOD),
            entry(today, Mood.JOYFUL),
            entry(today.minusDays(6), Mood.LOW),
        )

        val week = journalPeriodSummary(entries, InsightsPeriod.WEEK, today, zone)
        val month = journalPeriodSummary(entries, InsightsPeriod.DAYS_30, today, zone)
        val quarter = journalPeriodSummary(entries, InsightsPeriod.DAYS_90, today, zone)
        val year = journalPeriodSummary(entries, InsightsPeriod.YEAR, today, zone)

        assertEquals(7, week.trendPoints.size)
        assertEquals(6, month.trendPoints.size)
        assertEquals(9, quarter.trendPoints.size)
        assertEquals(8, year.trendPoints.size)
        assertEquals(2, month.recordedDayCount)
        assertEquals(3, month.entryCount)
        assertEquals(3.33, month.averageScore!!, 0.01)
    }

    private fun entry(date: LocalDate, mood: Mood, tags: List<String> = emptyList()) = JournalEntry(
        createdAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        mood = mood,
        tags = tags,
        note = "",
    )
}
