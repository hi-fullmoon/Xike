package com.xike.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun expandedNoteFieldOffersAnExplicitKeyboardDismissAction() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(),
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

        composeRule.onNodeWithText("多留一点").performClick()
        composeRule.onNodeWithText("发生了什么？也可以只留下一句话……").performClick()
        composeRule.onNodeWithText("完成").assertIsDisplayed()
    }

    @Test
    fun restoredDraftDetailsCanBeCollapsedAndExpandedAgain() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(note = "可以收起的草稿内容"),
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

        composeRule.waitForIdle()
        composeRule.onNodeWithText("可以收起的草稿内容").assertIsDisplayed()
        composeRule.onNodeWithText("多留一点").performClick()
        check(composeRule.onAllNodesWithText("可以收起的草稿内容").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("多留一点").performClick()
        composeRule.onNodeWithText("可以收起的草稿内容").assertIsDisplayed()
    }

    @Test
    fun unreadableSelectedPhotoDoesNotCrashTheScreen() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(
                        imageUriStrings = listOf("content://com.xike.app.missing/not-found"),
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

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("移除第 1 张照片").assertExists()
    }
}
