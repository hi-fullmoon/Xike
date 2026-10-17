package com.xike.app

import com.xike.app.backup.AutoBackupSettings
import com.xike.app.backup.nextAutoBackupAt
import com.xike.app.backup.reconciledAutoBackupAt
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AutoBackupScheduleTest {
    @Test
    fun clockMovedBackwardsDoesNotSkipTheNearestWeeklyOccurrence() {
        val now = ZonedDateTime.parse("2026-10-10T12:00+08:00[Asia/Shanghai]")
        val pending =
            ZonedDateTime.parse("2026-10-18T00:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
        val expected =
            ZonedDateTime.parse("2026-10-11T00:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
        assertEquals(expected, reconciledAutoBackupAt(AutoBackupSettings(), pending, now))
    }

    @Test
    fun clockMovedForwardPreservesOneOverdueBackup() {
        val now = ZonedDateTime.parse("2026-10-19T12:00+08:00[Asia/Shanghai]")
        val pending =
            ZonedDateTime.parse("2026-10-18T00:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
        assertEquals(pending, reconciledAutoBackupAt(AutoBackupSettings(), pending, now))
    }

    @Test
    fun missingDeadlineIsRecreatedInLocalCalendarTime() {
        val now = ZonedDateTime.parse("2026-10-17T12:00+08:00[Asia/Shanghai]")
        assertEquals(
            nextAutoBackupAt(AutoBackupSettings(), now).toInstant().toEpochMilli(),
            reconciledAutoBackupAt(AutoBackupSettings(), 0, now),
        )
    }

    @Test
    fun sundayMidnightMeansStartOfSunday() {
        assertEquals(
            ZonedDateTime.parse("2026-10-18T00:00+08:00[Asia/Shanghai]"),
            nextAutoBackupAt(
                AutoBackupSettings(),
                ZonedDateTime.parse("2026-10-17T23:59+08:00[Asia/Shanghai]"),
            ),
        )
    }

    @Test
    fun deadlineIsStrictlyAfterNow() {
        val now = ZonedDateTime.parse("2026-10-18T00:00+08:00[Asia/Shanghai]")
        assertEquals(now.plusWeeks(1), nextAutoBackupAt(AutoBackupSettings(), now))
    }

    @Test
    fun dailyScheduleRollsToTomorrowAfterSelectedMinute() {
        val now = ZonedDateTime.parse("2026-10-17T22:31+08:00[Asia/Shanghai]")
        assertEquals(
            now.plusDays(1).withMinute(30),
            nextAutoBackupAt(AutoBackupSettings(weekday = 0, hour = 22, minute = 30), now),
        )
    }

    @Test
    fun daylightSavingGapResolvesToValidLocalTime() {
        assertEquals(
            ZonedDateTime.parse("2026-03-08T03:30-04:00[America/New_York]"),
            nextAutoBackupAt(
                AutoBackupSettings(weekday = 0, hour = 2, minute = 30),
                ZonedDateTime.parse("2026-03-07T12:00-05:00[America/New_York]"),
            ),
        )
    }

    @Test
    fun weeklyScheduleKeepsWallClockTimeAcrossDaylightSaving() {
        assertEquals(
            ZonedDateTime.parse("2026-11-01T08:15-05:00[America/New_York]"),
            nextAutoBackupAt(
                AutoBackupSettings(hour = 8, minute = 15),
                ZonedDateTime.parse("2026-10-31T12:00-04:00[America/New_York]"),
            ),
        )
    }

    @Test
    fun rejectsInvalidTimeAndRetention() {
        assertThrows(IllegalArgumentException::class.java) { AutoBackupSettings(keepCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { AutoBackupSettings(keepCount = 101) }
        assertThrows(IllegalArgumentException::class.java) { AutoBackupSettings(hour = 24) }
        assertThrows(IllegalArgumentException::class.java) { AutoBackupSettings(weekday = 8) }
        assertThrows(IllegalArgumentException::class.java) { AutoBackupSettings(minute = 60) }
    }
}
