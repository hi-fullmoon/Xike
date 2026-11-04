package com.xike.app

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.JournalLabelKind
import com.xike.app.ui.theme.AppStyle
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicitly run on this task's isolated device; all changes go through the real Activity. */
class JournalTopicManagerRecoveryTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun editorSurvivesRecreationAndResize() {
        val phase = InstrumentationRegistry.getArguments().getString("topicRecoveryPhase")
        assumeTrue(phase == "write" || phase == "verify")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context).also { it.initialize(loadEntries = false) }
        val preferences =
            context.getSharedPreferences("topic-recovery-validation", Context.MODE_PRIVATE)
        LanguagePreferences(context).language = AppLanguage.CHINESE
        AppearancePreferences(context).saveStyle(AppStyle.BREATHE)
        if (phase == "verify") {
            val id = checkNotNull(preferences.getString("id", null))
            val catalog =
                kotlinx.coroutines.runBlocking { store.readRepository.libraryState(0).labels }
            val label = catalog.single { it.id == id }
            assertEquals("重建后更新的主题", label.name)
            assertEquals("身体", label.iconKey)
            assertTrue(store.entries().isEmpty())
            return
        }
        check(store.entries().isEmpty()) { "Use the isolated device without journal entries" }
        check(preferences.getString("id", null) == null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            rule.onNodeWithText("再留下一点").performScrollTo().performClick()
            rule.onNodeWithText("管理主题").performScrollTo().performClick()
            rule.onNodeWithTag("topic-manager-add").performClick()
            nameField().performTextInput("重建保留的主题")
            rule.onNodeWithContentDescription("学习").performScrollTo().performClick()
            scenario.recreate()
            nameField().assertTextContains("重建保留的主题")
            rule.onNodeWithContentDescription("学习").assertIsSelected()
            resizeAndWait(scenario, "1866x840")
            nameField().assertTextContains("重建保留的主题")
            rule.onNodeWithContentDescription("学习").assertIsSelected()
            resizeAndWait(scenario, "reset")
            nameField().assertTextContains("重建保留的主题")
            rule.onNodeWithText("保存").performClick()
            rule.waitUntil(10_000) { model!!.libraryState.labels.any { it.name == "重建保留的主题" } }
            awaitEditorClosed()
            val label = model!!.libraryState.labels.single { it.name == "重建保留的主题" }
            assertEquals("学习", label.iconKey)
            rule.onNodeWithTag("label-manager-list").performScrollToNode(hasText(label.name))
            rule.onNodeWithTag("label-row:${label.id}").performClick()
            nameField().performTextReplacement("重建后更新的主题")
            rule.onNodeWithContentDescription("身体").performScrollTo().performClick()
            scenario.recreate()
            nameField().assertTextContains("重建后更新的主题")
            rule.onNodeWithContentDescription("身体").assertIsSelected()
            capture(context, "recreated-editor")
            rule.onNodeWithText("取消").performClick()
            awaitEditorClosed()
            assertEquals("重建保留的主题", model!!.libraryState.labels.single { it.id == label.id }.name)
            rule.onNodeWithTag("label-manager-list").performScrollToNode(hasText(label.name))
            rule.onNodeWithTag("label-row:${label.id}").performClick()
            nameField().assertTextContains("重建保留的主题")
            nameField().performTextReplacement("重建后更新的主题")
            rule.onNodeWithContentDescription("身体").performScrollTo().performClick()
            scenario.recreate()
            nameField().assertTextContains("重建后更新的主题")
            rule.onNodeWithText("保存").performClick()
            rule.waitUntil(10_000) {
                model!!.libraryState.labels.any {
                    it.id == label.id && it.name == "重建后更新的主题" && it.iconKey == "身体"
                }
            }
            awaitEditorClosed()
            assertEquals(1, model!!.libraryState.labels.count { it.name == "重建后更新的主题" })
            check(preferences.edit().putString("id", label.id).commit())
            rule.onNodeWithContentDescription("关闭主题管理").performClick()
        }
    }

    @Test
    fun shortWindowKeepsRowsAndActionsReachable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("topicShortWindow") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        LanguagePreferences(context).language = AppLanguage.ENGLISH
        AppearancePreferences(context).saveStyle(AppStyle.PAPER)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            rule.onNodeWithText(localizedText("再留下一点")).performScrollTo().performClick()
            rule.onNodeWithText("Manage topics").performScrollTo().performClick()
            val list = rule.onNodeWithTag("label-manager-list")
            val work =
                model!!.libraryState.labels.first {
                    it.kind == JournalLabelKind.TOPIC && it.name == "工作"
                }
            list.performScrollToNode(hasContentDescription("More actions for Work"))
            rule
                .onNodeWithContentDescription("More actions for Work")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            rule.onNodeWithText("Archive").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand("input keyevent 4")
                .let {
                    ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
                }
            list.performScrollToNode(hasTestTag("topic-manager-add"))
            assertFullyVisible(rule.onNodeWithTag("topic-manager-add"))
            assertFullyVisible(rule.onNodeWithTag("topic-manager-sort"))
            capture(context, "short-footer")
            rule.onNodeWithTag("topic-manager-sort").performClick()
            list.performScrollToNode(hasContentDescription("Move Work down"))
            rule
                .onNodeWithContentDescription("Move Work down")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            rule.waitUntil(10_000) {
                model!!
                    .libraryState
                    .labels
                    .first { it.kind == JournalLabelKind.TOPIC && !it.isArchived }
                    .id != work.id
            }
            list.performScrollToNode(hasContentDescription("Move Work up"))
            rule.onNodeWithContentDescription("Move Work up").performScrollTo().performClick()
            rule.waitUntil(10_000) {
                model!!
                    .libraryState
                    .labels
                    .first { it.kind == JournalLabelKind.TOPIC && !it.isArchived }
                    .id == work.id
            }
            list.performScrollToNode(hasTestTag("topic-manager-sort-done"))
            assertFullyVisible(rule.onNodeWithTag("topic-manager-sort-done"))
            rule.onNodeWithTag("topic-manager-sort-done").performClick()
            list.performScrollToNode(hasText("Archived"))
            rule.onNodeWithText("Archived").performScrollTo().performClick()
            list.performScrollToNode(hasText("Family"))
            val family =
                model!!.libraryState.labels.first {
                    it.kind == JournalLabelKind.TOPIC && it.name == "家庭"
                }
            rule
                .onNode(hasText("Restore") and hasAnyAncestor(hasTestTag("label-row:${family.id}")))
                .performScrollTo()
                .assertIsDisplayed()
            capture(context, "short-archived")
            list.performScrollToNode(hasTestTag("topic-manager-add"))
            rule.onNodeWithTag("topic-manager-add").performClick()
            nameField().assertExists()
            rule.onNodeWithText("Cancel").performClick()
            awaitEditorClosed()
            rule.onNodeWithContentDescription("Close topic manager").performClick()
        }
    }

    private fun nameField() = rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))

    private fun awaitEditorClosed() =
        rule.waitUntil(10_000) {
            rule
                .onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
                .fetchSemanticsNodes()
                .isEmpty()
        }

    private fun resizeAndWait(scenario: ActivityScenario<MainActivity>, size: String) {
        var previous: MainActivity? = null
        scenario.onActivity { previous = it }
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("wm size $size")
            .let {
                ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
            }
        rule.waitUntil(15_000) {
            var changed = false
            scenario.onActivity { changed = it !== previous }
            changed
        }
    }

    private fun assertFullyVisible(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val full = node.getUnclippedBoundsInRoot()
        val visible = node.getBoundsInRoot()
        assertTrue(
            full.top.value >= visible.top.value - 0.5f &&
                full.bottom.value <= visible.bottom.value + 0.5f
        )
    }

    private fun capture(context: Context, name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory =
            File(context.getExternalFilesDir(null), "topic-manager-recovery").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
