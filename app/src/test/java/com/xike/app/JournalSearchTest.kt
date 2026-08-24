package com.xike.app

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class JournalSearchTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `combined filters match text mood tag date and image state`() {
        val matching = entry(
            id = "matching",
            date = LocalDate.of(2026, 8, 24),
            mood = Mood.GOOD,
            tags = listOf("工作", "学习"),
            note = "完成了重要方案",
            images = listOf("photo.xike-image"),
        )
        val entries = listOf(
            matching,
            matching.copy(id = "wrong-mood", mood = Mood.LOW),
            matching.copy(id = "wrong-text", note = "散步", tags = listOf("身体")),
            matching.copy(id = "wrong-date", createdAt = epoch(LocalDate.of(2026, 7, 1))),
            matching.copy(id = "no-photo", imageFileNames = emptyList()),
        )

        val results = filterJournalEntries(
            entries,
            JournalSearchQuery(
                text = "方案",
                moods = setOf(Mood.GOOD),
                tags = setOf("工作"),
                startDate = LocalDate.of(2026, 8, 1),
                endDate = LocalDate.of(2026, 8, 31),
                imageFilter = JournalImageFilter.WITH_IMAGES,
            ),
            zone,
        )

        assertEquals(listOf("matching"), results.map { it.id })
    }

    @Test
    fun `date range is inclusive in the selected timezone`() {
        val query = JournalSearchQuery(
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 31),
        )

        val range = query.epochRange(zone)

        assertEquals(epoch(LocalDate.of(2026, 8, 1)), range.startInclusive)
        assertEquals(epoch(LocalDate.of(2026, 9, 1)), range.endExclusive)
    }

    @Test
    fun `search document and query include Chinese bigrams`() {
        val document = journalSearchDocument("今天工作很顺利", listOf("学习"))
        val query = journalFtsQuery("工作顺利")

        assertTrue(document.split(' ').contains("工作"))
        assertTrue(document.split(' ').contains("顺利"))
        assertTrue(document.split(' ').contains("学习"))
        assertTrue(query.contains("\"工*\""))
        assertTrue(query.contains("\"作*\""))
        assertTrue(query.contains("\"顺*\""))
        assertTrue(query.contains("\"利*\""))
        assertTrue(query.contains(" AND "))
    }

    @Test
    fun `calendar begins on Monday and only allocates required weeks`() {
        val august = monthCalendarCells(YearMonth.of(2026, 8))
        val february = monthCalendarCells(YearMonth.of(2027, 2))

        assertEquals(42, august.size)
        assertEquals(LocalDate.of(2026, 8, 1), august[5])
        assertEquals(LocalDate.of(2026, 8, 31), august[35])
        assertEquals(28, february.size)
        assertEquals(LocalDate.of(2027, 2, 1), february.first())
        assertEquals(LocalDate.of(2027, 2, 28), february.last())
    }

    @Test
    fun `active filter count treats a date range as one condition`() {
        val query = JournalSearchQuery(
            text = "工作",
            moods = setOf(Mood.CALM),
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 31),
        )

        assertEquals(3, query.activeFilterCount)
        assertFalse(query.isEmpty)
        assertTrue(JournalSearchQuery().isEmpty)
    }

    @Test
    fun `search page reports whether more records are available`() {
        val page = JournalSearchPage(
            entries = listOf(
                JournalEntry(mood = Mood.CALM, tags = emptyList(), note = ""),
                JournalEntry(mood = Mood.GOOD, tags = emptyList(), note = ""),
            ),
            totalCount = 3,
            offset = 0,
        )

        assertTrue(page.hasMore)
        assertFalse(page.copy(totalCount = 2).hasMore)
    }

    @Test
    fun `ten thousand entry filter stays within an interactive baseline`() {
        val entries = List(10_000) { index ->
            JournalEntry(
                id = "entry-$index",
                createdAt = epoch(LocalDate.of(2026, 8, 1).plusDays((index % 24).toLong())),
                mood = Mood.entries[index % Mood.entries.size],
                tags = if (index % 100 == 0) listOf("目标主题") else listOf("日常"),
                note = if (index % 100 == 0) "需要找到的内容" else "普通记录 $index",
            )
        }
        var results = emptyList<JournalEntry>()

        val elapsed = measureTimeMillis {
            results = filterJournalEntries(
                entries,
                JournalSearchQuery(text = "找到", tags = setOf("目标主题")),
                zone,
            )
        }

        assertEquals(100, results.size)
        assertTrue("10k filter took ${elapsed}ms", elapsed < 5_000L)
    }

    private fun entry(
        id: String,
        date: LocalDate,
        mood: Mood,
        tags: List<String>,
        note: String,
        images: List<String>,
    ) = JournalEntry(
        id = id,
        createdAt = epoch(date),
        mood = mood,
        tags = tags,
        note = note,
        imageFileNames = images,
    )

    private fun epoch(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
