package com.xike.app.backup

import java.time.DayOfWeek
import java.time.ZonedDateTime

internal data class AutoBackupSettings(
    val enabled: Boolean = false,
    val weekday: Int = DayOfWeek.SUNDAY.value,
    val hour: Int = 0,
    val minute: Int = 0,
    val keepCount: Int = 5,
    val directory: String = "",
) {
    init {
        require(weekday in 0..7 && hour in 0..23 && minute in 0..59 && keepCount in 1..100)
    }
}

/** weekday 0 means daily. Resolve each occurrence in local calendar time, including DST. */
internal fun nextAutoBackupAt(settings: AutoBackupSettings, now: ZonedDateTime): ZonedDateTime =
    (0L..7L)
        .asSequence()
        .map { now.toLocalDate().plusDays(it) }
        .filter { settings.weekday == 0 || it.dayOfWeek.value == settings.weekday }
        .map { it.atTime(settings.hour, settings.minute).atZone(now.zone) }
        .first { it.isAfter(now) }

/** Preserve an overdue run, but don't skip nearer occurrences when the clock moves backwards. */
internal fun reconciledAutoBackupAt(
    settings: AutoBackupSettings,
    pendingAt: Long,
    now: ZonedDateTime,
): Long {
    val next = nextAutoBackupAt(settings, now).toInstant().toEpochMilli()
    return if (pendingAt > 0) minOf(pendingAt, next) else next
}
