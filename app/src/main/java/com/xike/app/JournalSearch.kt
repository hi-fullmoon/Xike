package com.xike.app

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class JournalImageFilter(val label: String) {
    ANY("全部"),
    WITH_IMAGES("有照片"),
    WITHOUT_IMAGES("无照片"),
}

data class JournalSearchQuery(
    val text: String = "",
    val moods: Set<Mood> = emptySet(),
    val tags: Set<String> = emptySet(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val imageFilter: JournalImageFilter = JournalImageFilter.ANY,
) {
    val normalizedText: String get() = text.trim()

    val activeFilterCount: Int
        get() = listOf(
            normalizedText.isNotEmpty(),
            moods.isNotEmpty(),
            tags.isNotEmpty(),
            startDate != null || endDate != null,
            imageFilter != JournalImageFilter.ANY,
        ).count { it }

    val isEmpty: Boolean get() = activeFilterCount == 0
}

data class JournalSearchPage(
    val entries: List<JournalEntry>,
    val totalCount: Int,
    val offset: Int,
) {
    val hasMore: Boolean get() = offset + entries.size < totalCount
}

internal data class JournalEpochRange(
    val startInclusive: Long,
    val endExclusive: Long,
)

internal fun JournalSearchQuery.epochRange(zoneId: ZoneId = ZoneId.systemDefault()): JournalEpochRange {
    val start = startDate?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
    val end = endDate?.plusDays(1)?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
    return JournalEpochRange(start, end)
}

fun filterJournalEntries(
    entries: List<JournalEntry>,
    query: JournalSearchQuery,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<JournalEntry> {
    val text = query.normalizedText
    val textTerms = SEARCH_TERM.findAll(text).map { it.value }.toList()
    return entries.filter { entry ->
        val date = Instant.ofEpochMilli(entry.createdAt).atZone(zoneId).toLocalDate()
        val textMatches = text.isEmpty() || textTerms.isNotEmpty() && textTerms.all { term ->
            entry.note.contains(term, ignoreCase = true) ||
                entry.tags.any { it.contains(term, ignoreCase = true) }
        }
        val moodMatches = query.moods.isEmpty() || entry.mood in query.moods
        val tagMatches = query.tags.isEmpty() || entry.tags.any { it in query.tags }
        val startMatches = query.startDate?.let { !date.isBefore(it) } ?: true
        val endMatches = query.endDate?.let { !date.isAfter(it) } ?: true
        val imageMatches = when (query.imageFilter) {
            JournalImageFilter.ANY -> true
            JournalImageFilter.WITH_IMAGES -> entry.imageFileNames.isNotEmpty()
            JournalImageFilter.WITHOUT_IMAGES -> entry.imageFileNames.isEmpty()
        }
        textMatches && moodMatches && tagMatches && startMatches && endMatches && imageMatches
    }
}

/**
 * FTS4's unicode tokenizer treats adjacent Han characters as a single token on
 * some SQLite builds. Adding single-character and bigram tokens makes short
 * Chinese searches useful without sending journal text to a remote service.
 */
internal fun journalSearchDocument(note: String, tags: List<String>): String {
    val source = buildString {
        append(note)
        append(' ')
        append(tags.joinToString(" "))
    }.trim()
    val cjkTokens = CJK_SEQUENCE.findAll(source)
        .flatMap { match ->
            val value = match.value
            sequence {
                value.forEach { yield(it.toString()) }
                value.windowed(size = 2, step = 1, partialWindows = false).forEach { yield(it) }
            }
        }
        .toList()
    return (listOf(source) + cjkTokens).filter { it.isNotBlank() }.joinToString(" ")
}

internal fun journalFtsQuery(text: String): String {
    val terms = SEARCH_TERM.findAll(text.trim())
        .map { it.value }
        .flatMap { term ->
            if (term.all(::isCjkCharacter) && term.length > 2) {
                term.asSequence().map { it.toString() }
            } else {
                sequenceOf(term)
            }
        }
        .map(::quoteFtsTerm)
        .toList()
    return terms.joinToString(" AND ").ifEmpty { quoteFtsTerm("__xike_no_match__") }
}

fun monthCalendarCells(month: YearMonth): List<LocalDate?> {
    val leadingEmptyDays = month.atDay(1).dayOfWeek.value - 1
    val occupiedCells = leadingEmptyDays + month.lengthOfMonth()
    val cellCount = ((occupiedCells + 6) / 7) * 7
    return List(cellCount) { index ->
        val day = index - leadingEmptyDays + 1
        day.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }
}

private fun quoteFtsTerm(value: String): String = "\"${value.replace("\"", "\"\"")}*\""

private fun isCjkCharacter(character: Char): Boolean = character.code in 0x3400..0x9FFF

private val CJK_SEQUENCE = Regex("[\\u3400-\\u9FFF]+")
private val SEARCH_TERM = Regex("[\\p{L}\\p{N}]+")
