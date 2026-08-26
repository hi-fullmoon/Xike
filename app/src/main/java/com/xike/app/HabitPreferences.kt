package com.xike.app

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class DailyPromptStyle(
    val label: String,
    val description: String,
    internal val questions: List<String>,
) {
    GENTLE(
        "温柔陪伴",
        "把注意力放在被照顾与需要上",
        listOf(
            "有什么事，让你感到被好好对待？",
            "今天的哪一刻，让你想慢下来？",
            "如果不需要逞强，你最想说什么？",
            "此刻，有什么值得轻轻感谢？",
            "今天的你，最需要怎样的陪伴？",
        ),
    ),
    AWARENESS(
        "身体觉察",
        "留意身体、心情与当下感受",
        listOf(
            "此刻的身体，哪里最需要放松？",
            "今天哪种心情停留得最久？",
            "现在呼吸时，你注意到了什么？",
            "今天什么时候，你最像自己？",
            "有什么感受，正等待被看见？",
        ),
    ),
    REFLECTION(
        "轻轻复盘",
        "看见今天的选择与小小变化",
        listOf(
            "今天做对了哪一件小事？",
            "有什么决定，让你更靠近自己？",
            "今天学到的哪一点，值得留下？",
            "如果重来一次，你想对自己更温柔在哪里？",
            "今天结束前，有什么可以先放下？",
        ),
    );

    companion object {
        fun fromStorage(value: String?): DailyPromptStyle = entries
            .firstOrNull { it.name == value }
            ?: GENTLE
    }
}

data class DailyPromptSettings(
    val enabled: Boolean = false,
    val style: DailyPromptStyle = DailyPromptStyle.GENTLE,
)

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 30,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val pausedUntilEpochDay: Long? = null,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 8,
) {
    init {
        require(hour in 0..23) { "提醒小时需要在 0 到 23 之间。" }
        require(minute in 0..59) { "提醒分钟需要在 0 到 59 之间。" }
        require(quietHoursStart in 0..23) { "勿扰开始时间需要在 0 到 23 之间。" }
        require(quietHoursEnd in 0..23) { "勿扰结束时间需要在 0 到 23 之间。" }
    }

    val timeLabel: String
        get() = "%02d:%02d".format(Locale.ROOT, hour, minute)

    fun summary(locale: Locale = Locale.CHINA): String {
        val days = when {
            weekdays.size == DayOfWeek.entries.size -> "每天"
            weekdays == setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ) -> "工作日"
            else -> weekdays.sortedBy(DayOfWeek::getValue).joinToString("、") {
                it.getDisplayName(TextStyle.SHORT, locale)
            }
        }
        return "$days $timeLabel"
    }

    fun pauseLabel(today: LocalDate = LocalDate.now()): String? = pausedUntilEpochDay
        ?.let(LocalDate::ofEpochDay)
        ?.takeIf { it.isAfter(today) }
        ?.format(DateTimeFormatter.ofPattern("M 月 d 日恢复"))
}

internal fun dailyQuestion(date: LocalDate, style: DailyPromptStyle): String {
    val questions = style.questions
    return questions[Math.floorMod(date.toEpochDay(), questions.size.toLong()).toInt()]
}

internal fun nextReminderAt(
    settings: ReminderSettings,
    now: ZonedDateTime,
): ZonedDateTime? {
    if (!settings.enabled || settings.weekdays.isEmpty()) return null
    val pausedUntil = settings.pausedUntilEpochDay?.let(LocalDate::ofEpochDay)
    val earliestDate = if (settings.quietHoursEnabled && settings.quietHoursStart > settings.quietHoursEnd) {
        now.toLocalDate().minusDays(1)
    } else {
        now.toLocalDate()
    }
    val firstDate = pausedUntil?.takeIf { it.isAfter(earliestDate) } ?: earliestDate

    repeat(16) { offset ->
        val date = firstDate.plusDays(offset.toLong())
        if (date.dayOfWeek in settings.weekdays && (pausedUntil == null || !date.isBefore(pausedUntil))) {
            val candidate = date.atTime(settings.hour, settings.minute).atZone(now.zone)
            val deliveryTime = candidate.afterQuietHours(settings)
            if (deliveryTime.isAfter(now)) return deliveryTime
        }
    }
    return null
}

internal class HabitPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var dailyPrompt: DailyPromptSettings
        get() = DailyPromptSettings(
            enabled = preferences.getBoolean(PROMPT_ENABLED, false),
            style = DailyPromptStyle.fromStorage(preferences.getString(PROMPT_STYLE, null)),
        )
        set(value) {
            check(
                preferences.edit()
                    .putBoolean(PROMPT_ENABLED, value.enabled)
                    .putString(PROMPT_STYLE, value.style.name)
                    .commit(),
            ) { "无法保存每日一问设置。" }
        }

    var reminder: ReminderSettings
        get() = ReminderSettings(
            enabled = preferences.getBoolean(REMINDER_ENABLED, false),
            hour = preferences.getInt(REMINDER_HOUR, 20).coerceIn(0, 23),
            minute = preferences.getInt(REMINDER_MINUTE, 30).coerceIn(0, 59),
            weekdays = preferences.getStringSet(REMINDER_WEEKDAYS, null)
                ?.mapNotNull { stored -> DayOfWeek.entries.firstOrNull { it.name == stored } }
                ?.toSet()
                ?: DayOfWeek.entries.toSet(),
            pausedUntilEpochDay = preferences.getLong(REMINDER_PAUSED_UNTIL, NO_PAUSE)
                .takeUnless { it == NO_PAUSE },
            quietHoursEnabled = preferences.getBoolean(QUIET_HOURS_ENABLED, true),
            quietHoursStart = preferences.getInt(QUIET_HOURS_START, 22).coerceIn(0, 23),
            quietHoursEnd = preferences.getInt(QUIET_HOURS_END, 8).coerceIn(0, 23),
        )
        set(value) {
            val editor = preferences.edit()
                .putBoolean(REMINDER_ENABLED, value.enabled)
                .putInt(REMINDER_HOUR, value.hour)
                .putInt(REMINDER_MINUTE, value.minute)
                .putStringSet(REMINDER_WEEKDAYS, value.weekdays.map(DayOfWeek::name).toSet())
                .putBoolean(QUIET_HOURS_ENABLED, value.quietHoursEnabled)
                .putInt(QUIET_HOURS_START, value.quietHoursStart)
                .putInt(QUIET_HOURS_END, value.quietHoursEnd)
            if (value.pausedUntilEpochDay == null) {
                editor.remove(REMINDER_PAUSED_UNTIL)
            } else {
                editor.putLong(REMINDER_PAUSED_UNTIL, value.pausedUntilEpochDay)
            }
            check(editor.commit()) { "无法保存提醒设置。" }
        }

    private companion object {
        const val PREFERENCES_NAME = "xike-habits"
        const val PROMPT_ENABLED = "daily-prompt-enabled"
        const val PROMPT_STYLE = "daily-prompt-style"
        const val REMINDER_ENABLED = "reminder-enabled"
        const val REMINDER_HOUR = "reminder-hour"
        const val REMINDER_MINUTE = "reminder-minute"
        const val REMINDER_WEEKDAYS = "reminder-weekdays"
        const val REMINDER_PAUSED_UNTIL = "reminder-paused-until"
        const val QUIET_HOURS_ENABLED = "quiet-hours-enabled"
        const val QUIET_HOURS_START = "quiet-hours-start"
        const val QUIET_HOURS_END = "quiet-hours-end"
        const val NO_PAUSE = Long.MIN_VALUE
    }
}

private fun ZonedDateTime.afterQuietHours(settings: ReminderSettings): ZonedDateTime {
    if (!settings.quietHoursEnabled || settings.quietHoursStart == settings.quietHoursEnd) return this
    val start = LocalTime.of(settings.quietHoursStart, 0)
    val end = LocalTime.of(settings.quietHoursEnd, 0)
    val time = toLocalTime()
    val isQuiet = if (start < end) {
        !time.isBefore(start) && time.isBefore(end)
    } else {
        !time.isBefore(start) || time.isBefore(end)
    }
    if (!isQuiet) return this

    val endDate = if (start > end && !time.isBefore(start)) {
        toLocalDate().plusDays(1)
    } else {
        toLocalDate()
    }
    return endDate.atTime(end).atZone(zone)
}
