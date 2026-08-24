package com.xike.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class JournalDraftUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restoredDraftIsShownWhileDailyPromptCanStayOff() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(
                        mood = Mood.GOOD,
                        note = "进程恢复后的草稿",
                        tags = setOf("工作"),
                        updatedAt = 123L,
                    ),
                    dailyPromptSettings = DailyPromptSettings(enabled = false),
                    onDraftMoodChange = {},
                    onDraftNoteChange = {},
                    onDraftTagToggle = {},
                    onDraftImagesAdded = {},
                    onDraftImageRemoved = {},
                    onSave = { _, _ -> Result.success(Unit) },
                )
            }
        }

        composeRule.onNodeWithText("进程恢复后的草稿").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("今日一刻").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun enabledDailyPromptUsesSelectedLocalQuestionBank() {
        val expected = dailyQuestion(LocalDate.now(), DailyPromptStyle.AWARENESS)
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(),
                    dailyPromptSettings = DailyPromptSettings(
                        enabled = true,
                        style = DailyPromptStyle.AWARENESS,
                    ),
                    onDraftMoodChange = {},
                    onDraftNoteChange = {},
                    onDraftTagToggle = {},
                    onDraftImagesAdded = {},
                    onDraftImageRemoved = {},
                    onSave = { _, _ -> Result.success(Unit) },
                )
            }
        }

        composeRule.onNodeWithText("今日一刻").assertIsDisplayed()
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }
}
