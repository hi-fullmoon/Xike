package com.xike.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HabitPreferencesTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun `next reminder uses selected local time`() {
        val now = ZonedDateTime.of(2026, 8, 24, 19, 0, 0, 0, shanghai)
        val settings = ReminderSettings(enabled = true, hour = 20, minute = 30)

        assertEquals(
            ZonedDateTime.of(2026, 8, 24, 20, 30, 0, 0, shanghai),
            nextReminderAt(settings, now),
        )
    }

    @Test
    fun `next reminder skips unselected weekdays and elapsed time`() {
        val mondayNight = ZonedDateTime.of(2026, 8, 24, 21, 0, 0, 0, shanghai)
        val settings = ReminderSettings(
            enabled = true,
            hour = 20,
            minute = 30,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 26, 20, 30, 0, 0, shanghai),
            nextReminderAt(settings, mondayNight),
        )
    }

    @Test
    fun `pause defers reminder until resume date`() {
        val now = ZonedDateTime.of(2026, 8, 24, 9, 0, 0, 0, shanghai)
        val settings = ReminderSettings(
            enabled = true,
            hour = 8,
            minute = 0,
            pausedUntilEpochDay = LocalDate.of(2026, 8, 31).toEpochDay(),
        )

        assertEquals(LocalDate.of(2026, 8, 31), nextReminderAt(settings, now)?.toLocalDate())
    }

    @Test
    fun `night quiet hours defer a reminder and survive morning rescheduling`() {
        val settings = ReminderSettings(
            enabled = true,
            hour = 23,
            minute = 0,
            weekdays = setOf(DayOfWeek.MONDAY),
            quietHoursEnabled = true,
            quietHoursStart = 22,
            quietHoursEnd = 8,
        )
        val afterReboot = ZonedDateTime.of(2026, 8, 25, 7, 0, 0, 0, shanghai)

        assertEquals(
            ZonedDateTime.of(2026, 8, 25, 8, 0, 0, 0, shanghai),
            nextReminderAt(settings, afterReboot),
        )
    }

    @Test
    fun `timezone recalculation preserves wall clock preference`() {
        val settings = ReminderSettings(enabled = true, hour = 20, minute = 30)
        val shanghaiNext = nextReminderAt(
            settings,
            ZonedDateTime.of(2026, 8, 24, 9, 0, 0, 0, shanghai),
        )
        val londonZone = ZoneId.of("Europe/London")
        val londonNext = nextReminderAt(
            settings,
            ZonedDateTime.of(2026, 8, 24, 9, 0, 0, 0, londonZone),
        )

        assertEquals(20, shanghaiNext?.hour)
        assertEquals(20, londonNext?.hour)
        assertNotEquals(shanghaiNext?.offset, londonNext?.offset)
    }

    @Test
    fun `disabled reminder has no next occurrence`() {
        assertNull(nextReminderAt(ReminderSettings(), ZonedDateTime.now(shanghai)))
    }

    @Test
    fun `daily prompts are deterministic and stay in selected local bank`() {
        val date = LocalDate.of(2026, 8, 24)

        assertEquals(
            dailyQuestion(date, DailyPromptStyle.GENTLE),
            dailyQuestion(date, DailyPromptStyle.GENTLE),
        )
        assertNotEquals(
            dailyQuestion(date, DailyPromptStyle.GENTLE),
            dailyQuestion(date, DailyPromptStyle.AWARENESS),
        )
    }
}
