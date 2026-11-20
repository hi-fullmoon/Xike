package com.xike.app.feature.archive

import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId

internal enum class MemoryKind {
    MONTH_AGO,
    PREVIOUS_YEAR,
}

internal data class MemoryDay(val date: LocalDate, val kind: MemoryKind, val count: Int)

internal data class JournalMemories(
    val today: LocalDate,
    val zoneId: ZoneId,
    val days: List<MemoryDay>,
)

internal fun monthAgo(today: LocalDate): LocalDate = today.minusMonths(1)

internal fun anniversary(today: LocalDate, year: Int): LocalDate? {
    val day = MonthDay.from(today)
    return if (year < today.year && day.isValidYear(year)) day.atYear(year) else null
}
