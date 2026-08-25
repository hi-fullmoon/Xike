package com.xike.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class JournalDraftUiTest {
    @Test
    fun outdoorEntryIsIntegratedAndExplainsPermissionBeforeRequest() {
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

        composeRule.onNodeWithText("窗外此刻").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("添加窗外此刻？").assertIsDisplayed()
        composeRule.onNodeWithText("手动选城市").assertIsDisplayed()
        composeRule.onNodeWithText("允许粗略定位").assertIsDisplayed()
    }

    @Test
    fun capturedOutdoorSnapshotIsVisibleInDraft() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(
                        outdoor = OutdoorSnapshot("上海 · 浦东", 27.2, 2, 1_700_000_000_000L),
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

        composeRule.onNodeWithText("窗外此刻 · 上海 · 浦东").assertIsDisplayed()
        composeRule.onNodeWithText("27°").assertIsDisplayed()
        composeRule.onNodeWithText("晴间多云").assertIsDisplayed()
    }

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
    fun weatherGuideExplainsTheMetaphorWithPlainEmotionWords() {
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

        composeRule.onNodeWithText("看看含义").performClick()
        composeRule.onNodeWithText("五种天气，怎么选？").assertIsDisplayed()
        composeRule.onNodeWithText("低落、难过，或有些不知所措").assertIsDisplayed()
        composeRule.onNodeWithText("轻松、不错，或有一点期待").assertIsDisplayed()
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

        composeRule.onNodeWithText("再留下一点").performClick()
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
        composeRule.onNodeWithText("再留下一点").performClick()
        check(composeRule.onAllNodesWithText("可以收起的草稿内容").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("再留下一点").performClick()
        composeRule.onNodeWithText("可以收起的草稿内容").assertIsDisplayed()
    }

    @Test
    fun expandedKeywordListOffersAdditionalEverydayChoices() {
        var selectedTag: String? = null
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(),
                    dailyPromptSettings = DailyPromptSettings(enabled = false),
                    onDraftMoodChange = {},
                    onDraftNoteChange = {},
                    onDraftTagToggle = { selectedTag = it },
                    onDraftImagesAdded = {},
                    onDraftImageRemoved = {},
                    onSave = { _, _ -> Result.success(Unit) },
                )
            }
        }

        composeRule.onNodeWithText("再留下一点").performClick()
        composeRule.onNodeWithText("添加照片").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("饮食").assertIsDisplayed().performClick()
        composeRule.runOnIdle { check(selectedTag == "饮食") }
        composeRule.onNodeWithText("其他").assertIsDisplayed()
    }

    @Test
    fun addPhotoOffersCameraAndGallerySources() {
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

        composeRule.onNodeWithText("再留下一点").performClick()
        composeRule.onNodeWithText("添加照片").performScrollTo().performClick()
        composeRule.onNodeWithText("拍照").assertIsDisplayed()
        composeRule.onNodeWithText("从相册选择").assertIsDisplayed()
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

    @Test
    fun restoredBackdatedDraftShowsItsTimeAndCanReturnToNow() {
        val recordedAt = LocalDate.now()
            .minusDays(1)
            .atTime(20, 15)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        var resetValue: Long? = recordedAt
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(mood = Mood.CALM, recordedAt = recordedAt),
                    dailyPromptSettings = DailyPromptSettings(enabled = false),
                    onDraftMoodChange = {},
                    onDraftNoteChange = {},
                    onDraftTagToggle = {},
                    onDraftImagesAdded = {},
                    onDraftImageRemoved = {},
                    onSave = { _, _ -> Result.success(Unit) },
                    onDraftRecordedAtChange = { resetValue = it },
                )
            }
        }

        composeRule.onNodeWithText("昨天 20:15").assertIsDisplayed()
        composeRule.onNodeWithText("补记这一刻").assertIsDisplayed()
        composeRule.onNodeWithText("改为现在").performScrollTo().performClick()
        composeRule.runOnIdle { check(resetValue == null) }
    }

    @Test
    fun clearingAnEncryptedDraftRequiresConfirmation() {
        var discarded = false
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(note = "还没保存的内容"),
                    dailyPromptSettings = DailyPromptSettings(enabled = false),
                    onDraftMoodChange = {},
                    onDraftNoteChange = {},
                    onDraftTagToggle = {},
                    onDraftImagesAdded = {},
                    onDraftImageRemoved = {},
                    onSave = { _, _ -> Result.success(Unit) },
                    onDraftDiscard = { discarded = true },
                )
            }
        }

        composeRule.onNodeWithText("清空").performScrollTo().performClick()
        composeRule.onNodeWithText("放弃这份草稿？").assertIsDisplayed()
        composeRule.onNodeWithText("继续保留").performClick()
        composeRule.runOnIdle { check(!discarded) }

        composeRule.onNodeWithText("清空").performScrollTo().performClick()
        composeRule.onNodeWithText("放弃草稿").performClick()
        composeRule.runOnIdle { check(discarded) }
    }
}
