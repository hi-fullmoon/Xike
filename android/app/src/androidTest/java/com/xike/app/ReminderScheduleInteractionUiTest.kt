package com.xike.app

import android.Manifest
import android.content.Context
import android.view.View
import android.widget.TimePicker
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import java.time.format.TextStyle
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Dedicated clean device: execute write/read in separate instrumentation processes. */
class ReminderScheduleInteractionUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun cancelDiscardsEditsAndSavePersistsTheRealReminderSettings() {
        val phase = InstrumentationRegistry.getArguments().getString("reminderAuditPhase")
        assumeTrue(phase == "write" || phase == "read")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = HabitPreferences(context)
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) { !model.isLoading }
        rule.onNodeWithTag("navigation-settings").performClick()
        if (phase == "read") {
            assertEquals(21, preferences.reminder.hour)
            assertEquals(37, preferences.reminder.minute)
            assertEquals(
                DayOfWeek.entries.toSet() - DayOfWeek.MONDAY,
                preferences.reminder.weekdays,
            )
            assertEquals(false, preferences.reminder.quietHoursEnabled)
            openSchedule()
            rule.onNodeWithText("21:37").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(localizedText("修改")).performScrollTo().performClick()
            onView(withId(android.R.id.button1)).check(matches(isCompletelyDisplayed()))
            onView(withId(android.R.id.button2))
                .check(matches(isCompletelyDisplayed()))
                .perform(click())
            rule.onNodeWithText("21:37").performScrollTo().assertIsDisplayed()
            return
        }
        check(preferences.reminder == ReminderSettings()) { "Use a clean dedicated device." }
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        rule
            .onNode(hasText(localizedText("记录提醒")) and isToggleable())
            .performScrollTo()
            .performClick()
        rule.waitUntil(5_000) { preferences.reminder.enabled }
        val original = preferences.reminder
        openSchedule()
        rule.onNodeWithText(localizedText("修改")).performScrollTo().performClick()
        setPickerTime(21, 37)
        onView(withId(android.R.id.button2)).perform(click())
        rule.onNodeWithText(original.timeLabel).performScrollTo().assertIsDisplayed()
        editSchedule()
        rule.onNodeWithText(localizedText("取消")).performClick()
        assertEquals(original, preferences.reminder)
        openSchedule()
        rule.onNodeWithText(original.timeLabel).performScrollTo().assertIsDisplayed()
        editSchedule()
        rule.onNodeWithText(localizedText("保存")).performClick()
        val expected =
            original.copy(
                hour = 21,
                minute = 37,
                weekdays = original.weekdays - DayOfWeek.MONDAY,
                quietHoursEnabled = !original.quietHoursEnabled,
            )
        assertEquals(expected, preferences.reminder)
        rule.activityRule.scenario.recreate()
        openSchedule()
        rule.onNodeWithText("21:37").performScrollTo().assertIsDisplayed()
        assertEquals(expected, preferences.reminder)
        rule.onNodeWithText(localizedText("取消")).performClick()
    }

    private fun openSchedule() {
        rule.onNodeWithText(localizedText("提醒时间")).performScrollTo().performClick()
    }

    private fun editSchedule() {
        rule.onNodeWithText(localizedText("修改")).performScrollTo().performClick()
        setPickerTime(21, 37)
        onView(withId(android.R.id.button1)).perform(click())
        rule.onNodeWithText("21:37").performScrollTo().assertIsDisplayed()
        rule
            .onNodeWithContentDescription(
                DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, AppLocale.locale)
            )
            .performScrollTo()
            .performClick()
        rule
            .onNode(hasText(localizedText("夜间勿扰")) and isToggleable())
            .performScrollTo()
            .performClick()
    }

    private fun setPickerTime(hour: Int, minute: Int) {
        onView(isAssignableFrom(TimePicker::class.java))
            .perform(
                object : ViewAction {
                    override fun getConstraints(): Matcher<View> =
                        isAssignableFrom(TimePicker::class.java)

                    override fun getDescription() = "Set the real Android time picker"

                    override fun perform(controller: UiController, view: View) {
                        (view as TimePicker).hour = hour
                        view.minute = minute
                        controller.loopMainThreadUntilIdle()
                    }
                }
            )
    }
}
