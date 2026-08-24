package com.xike.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

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

    @Test
    fun `deep insight reports distributions tags and day types without double counting tags`() {
        val entries = listOf(
            entry(LocalDate.of(2026, 8, 17), Mood.GOOD, listOf("工作", "工作")),
            entry(LocalDate.of(2026, 8, 22), Mood.LOW, listOf("休息")),
            entry(LocalDate.of(2026, 8, 23), Mood.JOYFUL, listOf("工作")),
            entry(LocalDate.of(2026, 8, 23), Mood.CALM, listOf("家庭")),
        )

        val summary = journalPeriodSummary(entries, InsightsPeriod.DAYS_30, today, zone)

        assertEquals(1, summary.moodDistribution.single { it.mood == Mood.JOYFUL }.entryCount)
        assertEquals(2, summary.topTags.single { it.tag == "工作" }.entryCount)
        assertEquals(1, summary.weekdayInsight.entryCount)
        assertEquals(3, summary.weekendInsight.entryCount)
        assertEquals(2, summary.weekendInsight.recordedDayCount)
        assertEquals(10, summary.weekendInsight.elapsedDayCount)
        assertEquals(0.2, summary.weekendInsight.coverageRatio, 0.0)
        assertEquals(InsightEvidenceLevel.DEVELOPING, summary.evidence.level)
    }

    @Test
    fun `period comparison uses the immediately preceding equal window`() {
        val currentEntries = listOf(
            entry(today.minusDays(2), Mood.GOOD),
            entry(today.minusDays(1), Mood.CALM),
            entry(today, Mood.JOYFUL),
            entry(today, Mood.GOOD),
        )
        val previousEntries = listOf(
            entry(today.minusDays(32), Mood.LOW),
            entry(today.minusDays(31), Mood.TIRED),
            entry(today.minusDays(30), Mood.CALM),
        )

        val comparison = journalPeriodSummary(
            currentEntries + previousEntries,
            InsightsPeriod.DAYS_30,
            today,
            zone,
        ).comparison

        assertEquals(3, comparison.entryCount)
        assertEquals(4, comparison.currentEntryCount)
        assertEquals(1, comparison.entryCountDelta)
        assertTrue(comparison.hasEnoughSamples)
        assertEquals(today.minusDays(59), comparison.startDate)
        assertEquals(today.minusDays(30), comparison.endDate)
    }

    @Test
    fun `empty and single-entry periods disclose limited evidence`() {
        val empty = journalPeriodSummary(emptyList(), InsightsPeriod.WEEK, today, zone)
        val single = journalPeriodSummary(
            listOf(entry(today, Mood.CALM)),
            InsightsPeriod.WEEK,
            today,
            zone,
        )

        assertEquals(InsightEvidenceLevel.NONE, empty.evidence.level)
        assertEquals(0.0, empty.evidence.coverageRatio, 0.0)
        assertEquals(InsightEvidenceLevel.LIMITED, single.evidence.level)
        assertFalse(single.evidence.canDescribePatterns)
        assertFalse(single.comparison.hasEnoughSamples)
    }

    @Test
    fun `rolling period crosses new year without dropping December entries`() {
        val januaryToday = LocalDate.of(2027, 1, 10)
        val entries = listOf(
            entry(LocalDate.of(2026, 12, 12), Mood.GOOD),
            entry(LocalDate.of(2027, 1, 10), Mood.CALM),
            entry(LocalDate.of(2026, 12, 11), Mood.LOW),
        )

        val summary = journalPeriodSummary(entries, InsightsPeriod.DAYS_30, januaryToday, zone)

        assertEquals(LocalDate.of(2026, 12, 12), summary.startDate)
        assertEquals(LocalDate.of(2027, 1, 10), summary.endDate)
        assertEquals(2, summary.entryCount)
    }

    @Test
    fun `year comparison uses the previous calendar year to the equivalent date`() {
        val leapToday = LocalDate.of(2024, 3, 1)
        val entries = listOf(
            entry(LocalDate.of(2024, 1, 1), Mood.GOOD),
            entry(LocalDate.of(2024, 3, 1), Mood.CALM),
            entry(LocalDate.of(2023, 1, 1), Mood.LOW),
            entry(LocalDate.of(2023, 3, 1), Mood.TIRED),
            entry(LocalDate.of(2023, 3, 2), Mood.JOYFUL),
        )

        val summary = journalPeriodSummary(entries, InsightsPeriod.YEAR, leapToday, zone)

        assertEquals(2, summary.entryCount)
        assertEquals(2, summary.comparison.entryCount)
        assertEquals(LocalDate.of(2023, 1, 1), summary.comparison.startDate)
        assertEquals(LocalDate.of(2023, 3, 1), summary.comparison.endDate)
    }

    @Test
    fun `day grouping stays correct across daylight saving transition`() {
        val newYork = ZoneId.of("America/New_York")
        val dstToday = LocalDate.of(2026, 3, 8)
        val beforeJump = JournalEntry(
            createdAt = dstToday.atTime(1, 30).atZone(newYork).toInstant().toEpochMilli(),
            mood = Mood.TIRED,
            tags = emptyList(),
            note = "",
        )
        val afterJump = JournalEntry(
            createdAt = dstToday.atTime(3, 30).atZone(newYork).toInstant().toEpochMilli(),
            mood = Mood.GOOD,
            tags = emptyList(),
            note = "",
        )

        val summary = journalPeriodSummary(
            listOf(beforeJump, afterJump),
            InsightsPeriod.WEEK,
            dstToday,
            newYork,
        )

        assertEquals(2, summary.entryCount)
        assertEquals(1, summary.recordedDayCount)
        assertEquals(2, summary.trendPoints.last().entryCount)
    }

    @Test
    fun `local review states sample limits and avoids causal claims`() {
        val summary = journalPeriodSummary(
            listOf(entry(today, Mood.CALM, listOf("自我"))),
            InsightsPeriod.WEEK,
            today,
            zone,
        )

        val review = localReviewText(summary)

        assertTrue(review.contains("样本少于 3 条"))
        assertTrue(review.contains("不代表原因、诊断或建议"))
    }

    @Test
    fun `ten thousand entry insight remains within local performance baseline`() {
        val entries = List(10_000) { index ->
            entry(
                today.minusDays((index % 90).toLong()),
                Mood.entries[index % Mood.entries.size],
                listOf("主题${index % 12}"),
            ).copy(id = "insight-$index")
        }
        lateinit var summary: JournalPeriodSummary

        val elapsed = measureTimeMillis {
            summary = journalPeriodSummary(entries, InsightsPeriod.DAYS_90, today, zone)
        }

        assertEquals(10_000, summary.entryCount)
        assertTrue("10k insight took ${elapsed}ms", elapsed < 5_000L)
    }

    private fun entry(date: LocalDate, mood: Mood, tags: List<String> = emptyList()) = JournalEntry(
        createdAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        mood = mood,
        tags = tags,
        note = "",
    )
}
