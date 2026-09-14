package com.xike.app

import androidx.compose.runtime.mutableStateOf
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
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

        composeRule.onNodeWithText("此刻窗外").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("地点与天气 · 可选").assertIsDisplayed()
        composeRule.onNodeWithText("添加").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("添加此刻窗外？").assertIsDisplayed()
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

        composeRule.onNodeWithText("此刻窗外 · 上海 · 浦东").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("27°").assertIsDisplayed()
        composeRule.onNodeWithText("晴间多云").assertIsDisplayed()
    }

    @Test
    fun newlyAddedPhotosOpenAtTappedImageAndSwipeBothDirections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = (1..3).map { index ->
            File.createTempFile("preview-$index-", ".png", context.cacheDir).also { file ->
                val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.rgb(index * 70, 100, 150))
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        try {
            showPhotoDraft(files.map { Uri.fromFile(it).toString() })
            composeRule.onNodeWithContentDescription("待保存的第 2 张照片")
                .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            waitForPhotoPage("2 / 3")
            composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
            composeRule.onNodeWithTag("photo-gallery-pager").performTouchInput { swipeLeft() }
            composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
            composeRule.onNodeWithTag("photo-gallery-pager").performTouchInput { swipeRight() }
            composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("关闭图片查看").performClick()
            composeRule.onNodeWithTag("photo-gallery-pager").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("待保存的第 2 张照片").assertExists()
        } finally {
            files.forEach { it.delete() }
        }
    }

    @Test
    fun singleUnavailablePhotoCanCloseAndRemovalDoesNotOpenPreview() {
        val uri = "content://com.xike.app.missing/preview"
        var removed: String? = null
        showPhotoDraft(listOf(uri), onRemove = { removed = it })
        composeRule.onNodeWithContentDescription("待保存的第 1 张照片")
            .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitForPhotoPage("1 / 1")
        composeRule.onNodeWithText("1 / 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭图片查看").performClick()
        composeRule.onNodeWithContentDescription("移除第 1 张照片").performScrollTo().performClick()
        composeRule.runOnIdle { check(removed == uri) }
        composeRule.onNodeWithTag("photo-gallery-pager").assertDoesNotExist()
    }

    @Test
    fun addedPhotosAreRevealedWithoutOpeningKeywordsAndSurviveCollapse() {
        val draft = mutableStateOf(JournalDraft())
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = draft.value,
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
        composeRule.runOnIdle {
            draft.value = JournalDraft(imageUriStrings = listOf("content://com.xike.app.missing/added"))
        }
        composeRule.waitUntil(5_000) {
            composeRule.onNodeWithContentDescription("待保存的第 1 张照片").isDisplayed()
        }
        composeRule.onNodeWithText("主题").assertDoesNotExist()
        composeRule.onNodeWithText("再留下一点").performScrollTo().performClick()
        composeRule.onNodeWithText("再留下一点").performClick()
        composeRule.onNodeWithText("主题").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("待保存的第 1 张照片").performScrollTo().assertIsDisplayed()
    }

    private fun waitForPhotoPage(label: String) {
        composeRule.waitUntil(5_000) { composeRule.onNodeWithText(label).isDisplayed() }
    }

    private fun showPhotoDraft(uris: List<String>, onRemove: (String) -> Unit = {}) {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(imageUriStrings = uris),
                    dailyPromptSettings = DailyPromptSettings(enabled = false),
                    onDraftMoodChange = {},
                    onDraftNoteChange = {},
                    onDraftTagToggle = {},
                    onDraftImagesAdded = {},
                    onDraftImageRemoved = onRemove,
                    onSave = { _, _ -> Result.success(Unit) },
                )
            }
        }
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

        composeRule.onNodeWithText("今日一刻").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun moodGuideOffersPlainEmotionReferences() {
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

        composeRule.onNodeWithText("选择参考").performClick()
        composeRule.onNodeWithText("五种心情，怎么选？").assertIsDisplayed()
        composeRule.onNodeWithText("难过、无助，或有些不知所措").assertIsDisplayed()
        composeRule.onNodeWithText("舒展、不错，或有一点期待").assertIsDisplayed()
    }

    @Test
    fun noteFieldIsImmediatelyAvailableAndOffersKeyboardDismissAction() {
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

        composeRule.onNodeWithText("这一刻，有什么想留下？").performScrollTo().performClick()
        composeRule.onNodeWithText("完成").assertIsDisplayed()
    }

    @Test
    fun collapsingOptionalDetailsKeepsTheNoteAvailable() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                MomentScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    draft = JournalDraft(note = "一直保留的草稿内容", tags = setOf("工作")),
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
        composeRule.onNodeWithText("一直保留的草稿内容").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("再留下一点").performScrollTo().performClick()
        check(composeRule.onAllNodesWithText("主题").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("一直保留的草稿内容").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("再留下一点").performScrollTo().performClick()
        composeRule.onNodeWithText("一直保留的草稿内容").performScrollTo().assertIsDisplayed()
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

        composeRule.onNodeWithText("再留下一点").performScrollTo().performClick()
        composeRule.onNodeWithText("饮食").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { check(selectedTag == "饮食") }
        composeRule.onNodeWithText("其他").performScrollTo().assertIsDisplayed()
        check(composeRule.onAllNodesWithText("社交").fetchSemanticsNodes().isEmpty())
        check(composeRule.onAllNodesWithText("运动").fetchSemanticsNodes().isEmpty())
        check(composeRule.onAllNodesWithText("创作").fetchSemanticsNodes().isEmpty())
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

        composeRule.onNodeWithText("照片").performScrollTo().performClick()
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

        composeRule.onNodeWithText("昨天 20:15").performScrollTo().assertIsDisplayed()
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
