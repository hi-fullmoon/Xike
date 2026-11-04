package com.xike.app

import com.xike.app.data.*
import com.xike.app.feature.archive.anniversary
import com.xike.app.feature.archive.monthAgo
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class JournalFavoritesMemoriesTest {
    private val entry = JournalEntry(id = "entry", createdAt = 123L, mood = Mood.CALM, note = "")

    private fun key(value: JournalEntry) =
        restoreContentKey(
            value,
            { error("No image") },
            { error("No audio") },
            { error("No video") },
        )

    private fun merge(local: List<JournalEntry>, incoming: List<JournalEntry>) =
        planJournalMerge(local, incoming, ::key, ::key)

    @Test
    fun favoritesRoundTripAndRejectMalformedValues() {
        assertEquals(
            entry.copy(isFavorite = true),
            JournalEntry.fromJson(entry.copy(isFavorite = true).toJson()),
        )
        assertFalse(JournalEntry.fromJson(entry.toJson().apply { remove("isFavorite") }).isFavorite)
        assertThrows(IllegalArgumentException::class.java) {
            JournalEntry.fromJson(entry.toJson().put("isFavorite", "true"))
        }
        assertEquals(key(entry), key(entry.copy(isFavorite = true)))
        assertTrue(BackupHtml.render(listOf(entry.copy(isFavorite = true))).contains("已收藏"))
    }

    @Test
    fun mergeKeepsEitherFavoriteWithoutDuplicatingContent() {
        val favorite = entry.copy(isFavorite = true)
        val plan = merge(listOf(entry), listOf(favorite))
        assertTrue(plan.additions.isEmpty())
        assertEquals(setOf(entry.id), plan.favoriteUpdates)
        assertTrue(merge(listOf(favorite), listOf(entry)).favoriteUpdates.isEmpty())
        assertTrue(merge(listOf(favorite), listOf(favorite)).favoriteUpdates.isEmpty())
        val withinImport = merge(emptyList(), listOf(entry, favorite))
        assertEquals(listOf(favorite), withinImport.additions)
        assertTrue(withinImport.favoriteUpdates.isEmpty())
    }

    @Test
    fun conflictingCopiesKeepTheirOwnFavoriteAndCanBeRefavoritedOnReimport() {
        val incoming = entry.copy(note = "changed", isFavorite = true)
        val first = merge(listOf(entry), listOf(incoming))
        assertEquals(1, first.conflictCount)
        assertTrue(first.additions.single().isFavorite)
        assertTrue(first.favoriteUpdates.isEmpty())
        val copy = first.additions.single().copy(isFavorite = false)
        val repeated = merge(listOf(entry, copy), listOf(incoming))
        assertTrue(repeated.additions.isEmpty())
        assertEquals(setOf(copy.id), repeated.favoriteUpdates)
    }

    @Test
    fun favoriteFilterIntersectsOtherDimensionsAndCountsAsOneCondition() {
        val query = JournalSearchQuery(favoritesOnly = true, tags = setOf("工作"))
        val work = entry.copy(tags = listOf("工作"), isFavorite = true)
        assertEquals(
            listOf(work),
            filterJournalEntries(
                listOf(work, work.copy(isFavorite = false), entry.copy(isFavorite = true)),
                query,
            ),
        )
        assertEquals(2, query.activeFilterCount)
        assertFalse(query.isEmpty)
        assertTrue(query.copy(favoritesOnly = false, tags = emptySet()).isEmpty)
    }

    @Test
    fun calendarMonthAndAnniversariesRespectMonthEndsAndLeapYears() {
        assertEquals(LocalDate.of(2024, 2, 29), monthAgo(LocalDate.of(2024, 3, 31)))
        assertEquals(LocalDate.of(2023, 2, 28), monthAgo(LocalDate.of(2023, 3, 31)))
        assertEquals(LocalDate.of(2023, 12, 31), monthAgo(LocalDate.of(2024, 1, 31)))
        val today = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2020, 2, 29), anniversary(today, 2020))
        assertNull(anniversary(today, 2023))
        assertNull(anniversary(today, 2024))
        assertNull(anniversary(today, 2028))
    }

    @Test
    fun fixedZoneUsesCalendarBoundariesAcrossDstInsteadOf24Hours() {
        val zone = ZoneId.of("America/New_York")
        val spring = LocalDate.of(2024, 3, 10)
        val fall = LocalDate.of(2024, 11, 3)
        val a = JournalSearchQuery(startDate = spring, endDate = spring).epochRange(zone)
        val b = JournalSearchQuery(startDate = fall, endDate = fall).epochRange(zone)
        assertEquals(23 * 60 * 60 * 1000L, a.endExclusive - a.startInclusive)
        assertEquals(25 * 60 * 60 * 1000L, b.endExclusive - b.startInclusive)
    }
}
