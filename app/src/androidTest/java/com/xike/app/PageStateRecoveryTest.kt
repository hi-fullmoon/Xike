package com.xike.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.format.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class PageStateRecoveryTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun reminderWeekdaysSurviveStateRestoration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = HabitPreferences(context)
        val original = preferences.reminder
        val day = DayOfWeek.entries.first { it !in original.weekdays || original.weekdays.size > 1 }
        val expectedDays = if (day in original.weekdays) original.weekdays - day else original.weekdays + day
        val restoration = StateRestorationTester(composeRule)
        try {
            restoration.setContent {
                XikeTheme(AppTheme.OCEAN) {
                    ReminderScheduleDialog(
                        settings = original,
                        onConfirm = { preferences.reminder = it },
                        onDismiss = {},
                    )
                }
            }
            composeRule.onNodeWithText(day.getDisplayName(TextStyle.NARROW, AppLocale.locale)).performClick()
            restoration.emulateSavedInstanceStateRestore()
            composeRule.onNodeWithText(localizedText("保存")).performClick()
            assertEquals(expectedDays, HabitPreferences(context).reminder.weekdays)
            assertEquals(original.hour, preferences.reminder.hour)
            assertEquals(original.minute, preferences.reminder.minute)
        } finally {
            preferences.reminder = original
        }
    }

    @Test
    fun insightSourceDialogSurvivesStateRestoration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context)
        val entries = store.entries()
        assumeTrue("Existing records in the current period are required", journalPeriodSummary(entries, InsightsPeriod.WEEK).entryCount > 0)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalInsightsScreen(PaddingValues(), entries, store::openImage, store::openAudio)
            }
        }
        awaitSummary()
        composeRule.onNodeWithText(localizedText("查看本地回顾")).let {
            composeRule.onNodeWithTag("insights-list").performScrollToNode(hasText(localizedText("查看本地回顾")))
            it.performClick()
        }
        val source = localJournalReview(journalPeriodSummary(entries, InsightsPeriod.WEEK))
            .sections.flatMap { it.sources }.first()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(source.label))
        composeRule.onNodeWithText(source.label).performClick()
        composeRule.onNodeWithContentDescription(localizedText("关闭原始记录")).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        awaitSummary()
        composeRule.onNodeWithContentDescription(localizedText("关闭原始记录")).assertIsDisplayed()
        composeRule.onNodeWithText(source.label).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(localizedText("关闭原始记录")).performClick()
        composeRule.onNodeWithText(localizedText("本地回顾")).assertIsDisplayed()
    }

    private fun awaitSummary() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription(localizedText("正在读取…")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun refreshingRealEntriesKeepsTheOpenDetail() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context)
        val entries = store.entries()
        val summary = journalPeriodSummary(entries, InsightsPeriod.WEEK)
        assumeTrue("At least two real records and a current-period record are required", entries.size > 1 && summary.entryCount > 0)
        val displayedEntries = mutableStateOf(entries)
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalInsightsScreen(PaddingValues(), displayedEntries.value, store::openImage, store::openAudio)
            }
        }
        awaitSummary()
        composeRule.onNodeWithTag("insights-overview-headline").performClick()
        composeRule.onNodeWithTag("insight-source-${summary.entryIds.first()}").performClick()
        composeRule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
        composeRule.runOnIdle { displayedEntries.value = entries.asReversed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
    }
}
