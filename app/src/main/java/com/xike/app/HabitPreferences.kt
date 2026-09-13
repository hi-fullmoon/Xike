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
            "今天有人给过你一点温暖吗？",
            "哪句话，让你心里松了一下？",
            "今天有什么小事，替你分担了重量？",
            "如果给自己留一句话，你会说什么？",
            "今天有没有一个地方，让你觉得安心？",
            "此刻，你最想听见怎样的回应？",
            "今天谁的出现，让你觉得不那么孤单？",
            "有什么微小的快乐，值得收进口袋？",
            "今天你怎样照顾了自己？",
            "有什么需要，可以先被你自己听见？",
            "哪一刻，你允许自己休息了？",
            "今天的你，值得被怎样理解？",
            "有什么事，可以不必今天解决？",
            "今天哪种善意，让你记在心里？",
            "如果此刻能被拥抱，你想说什么？",
            "今天有哪些温柔，是你给自己的？",
            "有什么熟悉的东西，让你感到踏实？",
            "今天的一个小愿望是什么？",
            "哪一刻，你觉得有人懂你？",
            "此刻你想给自己留出什么空间？",
            "今天有什么声音，让你觉得舒服？",
            "有什么味道，带来了片刻安定？",
            "今天最想感谢自己哪一点？",
            "有谁的关心，你想轻轻记下？",
            "今天哪件平常事，给了你一点力量？",
            "如果放慢脚步，你想先看见什么？",
            "今天你为自己守住了什么？",
            "有什么话，适合留给此刻的自己？",
            "哪一个瞬间，让你感到被接住？",
            "今天有没有一件事，比想象中容易？",
            "你希望今晚怎样照顾自己？",
            "有什么可以允许自己暂时不会？",
            "今天谁的笑容，留在了你的记忆里？",
            "你想把哪份温暖带到明天？",
            "有什么小小的期待，正在心里发芽？",
            "今天你曾为谁留下一点温柔？",
            "哪件事提醒你，可以不用那么着急？",
            "如果疲惫会说话，它希望你做什么？",
            "今天有什么让你愿意再试一次？",
            "你想为今天的自己留一盏什么灯？",
            "有什么担心，值得被温柔地听见？",
            "今天有哪一刻，你对自己宽容了些？",
            "哪件小事，让你的心安静了一会儿？",
            "你现在最想被怎样陪着？",
            "今天有什么惊喜，哪怕很轻？",
            "什么能让此刻的你更舒服一点？",
            "今天谁做的一件小事，让你记得？",
            "你想对那个努力了一天的自己说什么？",
            "有什么可以只做到刚刚好？",
            "今天最柔软的一个瞬间是什么？",
            "哪一刻，你觉得自己可以停下来？",
            "今天有什么值得悄悄庆祝？",
            "你希望明天的自己记得什么？",
            "此刻，什么能给你一点安稳？",
            "今天有什么，是你愿意珍惜的？",
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
            "此刻，你的肩膀是什么感觉？",
            "今天身体什么时候提醒你休息？",
            "现在脚下的触感是什么样的？",
            "此刻，你的呼吸快还是慢？",
            "今天哪一刻，你感到轻松了一点？",
            "现在你的手是暖的还是凉的？",
            "今天的心情像哪种天气？",
            "此刻，你最先注意到周围什么声音？",
            "今天有什么让你不自觉地笑了？",
            "现在你的身体想伸展哪里？",
            "今天什么时候，你感到有些紧绷？",
            "此刻你能看见哪一种颜色？",
            "今天哪种气味，让你停留了一秒？",
            "现在坐着或站着，哪里最有支撑感？",
            "今天有什么情绪来过，又慢慢离开？",
            "此刻你的心情更像靠近还是退后？",
            "今天什么时候，你想保持安静？",
            "现在你需要一点水、空气，还是休息？",
            "今天的哪个画面，还停在脑海里？",
            "此刻，身体哪一处最放松？",
            "今天什么时候，你觉得时间过得很快？",
            "此刻，你能听见多远处的声音？",
            "今天有没有一个瞬间，你想深呼吸？",
            "现在的光线让你有什么感觉？",
            "今天身体给过你什么小提示？",
            "此刻，你的眉头是舒展的吗？",
            "今天哪一刻，你感到心里亮了一点？",
            "现在你最想靠近什么？",
            "今天哪个声音，还在你耳边？",
            "此刻你的步调，像走路还是奔跑？",
            "今天哪一刻，你注意到自己的呼吸？",
            "现在身体更想活动，还是安静？",
            "今天你在哪个瞬间感到自在？",
            "此刻，有什么感受难以命名？",
            "今天哪种情绪，比你预想的更明显？",
            "现在你的下颌是放松的吗？",
            "今天什么时刻，你想暂时离开人群？",
            "此刻你能感到衣服碰到皮肤吗？",
            "今天哪件小事改变了你的心情？",
            "现在你注意到的温度是什么样的？",
            "今天哪个瞬间，让你有了精神？",
            "此刻你觉得心里是满的还是空的？",
            "今天有没有情绪，你还没来得及照看？",
            "现在你最想把注意力放在哪里？",
            "今天何时你感到身体有力量？",
            "此刻，有什么地方正让你舒服？",
            "今天的心情有过哪一次转弯？",
            "现在你的眼睛想看远处还是近处？",
            "今天有什么触感，让你记住了？",
            "此刻，你想给身体多一点什么？",
            "今天什么时候，你察觉到自己累了？",
            "现在你的心情会用什么颜色表达？",
            "今天有没有一刻，你只是在感受？",
            "此刻，周围有什么是安静的？",
            "今天哪个瞬间，你觉得呼吸顺畅？",
        ),
    ),
    REFLECTION(
        "轻轻复盘",
        "看见今天的选择与小小变化",
        listOf(
            "今天做过哪一件值得记下的小事？",
            "有什么决定，让你更靠近自己？",
            "今天学到的哪一点，值得留下？",
            "如果重来一次，你想对自己更温柔在哪里？",
            "今天结束前，有什么可以先放下？",
            "今天哪件事，比昨天多懂了一点？",
            "有什么选择，是你认真做出的？",
            "今天你把时间留给了什么？",
            "哪件小事，带来了意外的变化？",
            "今天有没有一个想法，值得再想想？",
            "什么事情，今天已经足够了？",
            "今天哪里和原先想的不一样？",
            "有什么没完成，也可以先放在这里？",
            "今天你曾尝试一种新做法吗？",
            "哪一刻，你改变了原来的主意？",
            "今天有什么话，你希望下次再说？",
            "什么事情，值得换个角度看看？",
            "今天你注意到了自己的哪个习惯？",
            "哪一件事，你愿意继续保持？",
            "今天有什么让你重新理解了别人？",
            "哪个选择，让你感到更自在？",
            "今天你为重要的事留了多少空间？",
            "有什么事情，下次可以更轻一点？",
            "今天哪一刻，你决定先等等？",
            "有什么念头，和早晨时不同了？",
            "今天你发现自己在意什么？",
            "哪件事提醒你，事情可以慢慢来？",
            "今天你拒绝了什么，又留下了什么？",
            "有什么小进展，你差点没发现？",
            "今天哪一次停顿，帮到了你？",
            "什么事情，你想用自己的节奏完成？",
            "今天你在哪件事上改变了一点？",
            "有什么原本担心的事，后来怎样了？",
            "今天哪句话，让你想了很久？",
            "如果给今天起个名字，会是什么？",
            "今天你从一次交流里带走了什么？",
            "哪件事，你想留到明天再想？",
            "今天你有没有发现新的可能？",
            "什么事情，让你更清楚自己的边界？",
            "今天哪件事，值得你停下来看看？",
            "有什么决定，你还想再给自己时间？",
            "今天哪个瞬间，你看见了变化？",
            "哪种期待，今天变得更清楚了？",
            "今天你做的哪件事，是为了自己？",
            "有什么问题，暂时不需要答案？",
            "今天你愿意记下哪个微小发现？",
            "哪件事，让你知道自己需要帮助？",
            "今天什么安排，最贴合你的状态？",
            "有什么旧想法，今天松动了一点？",
            "今天你为下一步留下了什么线索？",
            "哪件事，可以试着用别的方法做？",
            "今天你对自己多了解了一点什么？",
            "有什么承诺，值得重新衡量？",
            "今天哪一次选择，让你松了口气？",
            "什么事情，你愿意继续观察？",
            "今天你把什么留在了过去？",
            "如果再过一周，你想记得今天什么？",
            "哪件平凡的事，说明日子在往前走？",
            "今天有什么经历，你想慢慢消化？",
            "明天你想为自己留出哪一点余地？",
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
