package com.xike.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.draft.JournalDraftStore
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Uses the real Activity and storage. Run explicitly on an isolated preview device. */
class JournalFeelingsUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun chooseCancelConfirmSaveAndReopen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments().getString("feelingsPhase")
        assumeTrue(phase == "write" || phase == "read")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("feelings-ui-test", Context.MODE_PRIVATE)
        val store = JournalStore(context)
        store.initialize(loadEntries = false)
        if (phase == "write") {
            check(JournalDraftStore(context).load().isEmpty) {
                "Use an isolated device with an empty draft"
            }
            check(store.entries().isEmpty()) { "Use an isolated device with no existing entries" }
            LanguagePreferences(context).language = AppLanguage.CHINESE
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("此刻的心情").fetchSemanticsNodes().isNotEmpty()
            }
            awaitJournalReady(scenario)
            if (phase == "write") {
                rule.onNodeWithText("还不错").performClick()
                rule.onNodeWithText("添加具体感受").performClick()
                rule.onNodeWithText("期待").performClick()
                rule.onNodeWithText("焦虑").performClick()
                capture("feelings-sheet")
                rule.onNodeWithText("完成").performScrollTo().performClick()
                rule.onNodeWithText("期待、焦虑").assertIsDisplayed()
                assertEquals(
                    listOf("HOPEFUL", "ANXIOUS"),
                    JournalDraftStore(context).load().feelings,
                )
                rule.onNodeWithText("编辑").performClick()
                rule.onNodeWithText("开心").performClick()
                androidx.test.espresso.Espresso.pressBack()
                rule.onNodeWithText("期待、焦虑").assertIsDisplayed()
                assertEquals(
                    listOf("HOPEFUL", "ANXIOUS"),
                    JournalDraftStore(context).load().feelings,
                )
                capture("feelings-selected")
                rule.onNodeWithText("记下此刻").performClick()
                rule.waitUntil(10_000) { store.entries().size == 1 }
                val entry = store.entries().single()
                assertEquals(listOf("HOPEFUL", "ANXIOUS"), entry.feelings)
                assertEquals(1, entry.moodScaleVersion)
                check(preferences.edit().putString("entry", entry.id).commit())
            } else {
                val entry = store.entries().single { it.id == preferences.getString("entry", null) }
                assertEquals(listOf("HOPEFUL", "ANXIOUS"), entry.feelings)
                assertEquals(1, entry.moodScaleVersion)
                rule.onNodeWithText("回望", useUnmergedTree = true).performClick()
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithText("期待 · 焦虑").fetchSemanticsNodes().isNotEmpty() &&
                        rule.onAllNodesWithText("期待 · 焦虑").onFirst().isDisplayed()
                }
                rule.onNodeWithText("期待 · 焦虑").assertIsDisplayed().performClick()
                rule.onNodeWithText("具体感受").assertIsDisplayed()
                rule.onNode(hasText("期待 · 焦虑") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                capture("feelings-detail")
                rule.onNodeWithContentDescription("编辑记录").performClick()
                rule.onNodeWithText("期待、焦虑").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("编辑").performClick()
                rule.onNodeWithText("开心").performClick()
                rule.onNodeWithText("完成").performScrollTo().performClick()
                rule.onNodeWithText("期待、焦虑、开心").assertIsDisplayed()
                rule.onNodeWithText("保存", substring = false).performClick()
                rule.waitUntil(10_000) {
                    store.entries().single { it.id == entry.id }.feelings ==
                        listOf("HOPEFUL", "ANXIOUS", "HAPPY")
                }
            }
        }
    }

    @Test
    fun previewRealScreenAtConfiguredSizeAndLanguage() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("feelingsPreview") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val english = args.getString("previewLanguage") == "en"
        LanguagePreferences(context).language =
            if (english) AppLanguage.ENGLISH else AppLanguage.CHINESE
        val style =
            args.getString("previewStyle")?.let { name ->
                com.xike.app.ui.theme.AppStyle.entries.first { it.name == name }
            }
        style?.let { AppearancePreferences(context).saveStyle(it) }
        val originalDraft = JournalDraftStore(context).load()
        check(originalDraft.isEmpty) { "Use an isolated preview device with an empty draft" }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mood = if (english) "Positive" else "还不错"
                rule.waitUntil(15_000) {
                    rule.onAllNodesWithText(mood).fetchSemanticsNodes().isNotEmpty()
                }
                awaitJournalReady(scenario)
                rule.onNodeWithText(mood).performScrollTo().performClick()
                rule
                    .onNodeWithText(if (english) "Add feelings" else "添加具体感受")
                    .performScrollTo()
                    .performClick()
                rule
                    .onNodeWithText(if (english) "Hopeful" else "期待")
                    .performScrollTo()
                    .performClick()
                rule
                    .onNodeWithText(if (english) "Anxious" else "焦虑")
                    .performScrollTo()
                    .performClick()
                capture("feelings-preview-sheet-${args.getString("previewName")}")
                rule.onNodeWithText(if (english) "Done" else "完成").performScrollTo().performClick()
                rule
                    .onNodeWithText(if (english) "Hopeful, Anxious" else "期待、焦虑")
                    .assertIsDisplayed()
                capture("feelings-preview-${args.getString("previewName")}")
            }
        } finally {
            JournalDraftStore(context).save(originalDraft)
        }
    }

    private fun awaitJournalReady(scenario: ActivityScenario<MainActivity>) {
        var model: JournalViewModel? = null
        scenario.onActivity {
            model = androidx.lifecycle.ViewModelProvider(it)[JournalViewModel::class.java]
        }
        rule.waitUntil(15_000) { model?.systemActivityResultsReady == true }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Wait for the platform popup window transition before taking a full display capture.
        android.os.SystemClock.sleep(500L)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
