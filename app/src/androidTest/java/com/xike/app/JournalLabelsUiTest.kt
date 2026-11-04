package com.xike.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.*
import com.xike.app.data.draft.JournalDraftStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit phases run on this task's isolated device. Entries are created through MainActivity. */
class JournalLabelsUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun createDraftThenReopenRenameArchiveAndRestore() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("labelsPhase")
        assumeTrue(phase in listOf("write", "read", "verify"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context)
        store.initialize(loadEntries = false)
        val draftStore = JournalDraftStore(context)
        val preferences = context.getSharedPreferences("labels-ui-validation", Context.MODE_PRIVATE)
        LanguagePreferences(context).language = AppLanguage.CHINESE
        if (phase == "write") {
            check(store.entries().isEmpty() && draftStore.load().isEmpty) {
                "Use an isolated device with no entries or draft"
            }
        } else {
            check(preferences.getBoolean("created-by-this-test", false))
        }
        if (phase == "verify") {
            val entry = store.entries().single()
            assertEquals(setOf("个人项目", "午间散步"), entry.labels.map { it.savedName }.toSet())
            val catalog =
                kotlinx.coroutines.runBlocking { store.readRepository.libraryState(0).labels }
            assertTrue(catalog.any { it.name == "个人计划" })
            assertTrue(catalog.any { it.name == "午间散步" && it.isArchived })
            return
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            if (phase == "write") {
                rule.onNodeWithText("还不错").performScrollTo().performClick()
                openTopics()
                addLabel("管理主题", "个人项目")
                rule.onNodeWithText("个人项目").performScrollTo().performClick()
                addLabel("管理主题", "个人长期学习计划".repeat(3))
                rule.onNodeWithText("可选 · 展开").performScrollTo().performClick()
                addLabel("管理活动", "午间散步")
                rule.onNodeWithText("午间散步").performScrollTo().performClick()
                rule.waitUntil(10_000) { draftStore.load().labels.size == 2 }
                assertEquals(
                    setOf("个人项目", "午间散步"),
                    draftStore.load().labels.map { it.savedName }.toSet(),
                )
                check(preferences.edit().putBoolean("created-by-this-test", true).commit())
                capture(context, "selected")
            } else {
                assertEquals(
                    setOf("个人项目", "午间散步"),
                    draftStore.load().labels.map { it.savedName }.toSet(),
                )
                rule.onNodeWithText("记下此刻").performClick()
                rule.waitUntil(15_000) { store.entries().size == 1 }
                val entry = store.entries().single()
                assertEquals(2, entry.labels.size)
                openTopics()
                rule.onNodeWithText("管理主题").performScrollTo().performClick()
                rule.onNodeWithTag("label-manager-list").performScrollToNode(hasText("个人项目"))
                rule
                    .onNodeWithTag(
                        "label-row:${entry.labels.first { it.kind == JournalLabelKind.TOPIC }.id}"
                    )
                    .performClick()
                rule
                    .onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
                    .performTextReplacement("个人计划")
                rule.onNodeWithText("保存").performClick()
                rule.waitUntil(10_000) { model!!.libraryState.labels.any { it.name == "个人计划" } }
                awaitLabelEditorClosed()
                rule.onNodeWithContentDescription("关闭主题管理").performClick()
                if (rule.onAllNodesWithText("管理活动").fetchSemanticsNodes().isEmpty())
                    rule.onNodeWithText("可选 · 展开").performScrollTo().performClick()
                rule.onNodeWithText("管理活动").performScrollTo().performClick()
                rule.onNodeWithTag("label-manager-list").performScrollToNode(hasText("午间散步"))
                val activityId = entry.labels.first { it.kind == JournalLabelKind.ACTIVITY }.id
                rule
                    .onNode(hasText("归档") and hasAnyAncestor(hasTestTag("label-row:$activityId")))
                    .performClick()
                rule.waitUntil(10_000) {
                    model!!.libraryState.labels.any { it.id == activityId && it.isArchived }
                }
                rule.onNodeWithText("已归档").performClick()
                rule.onNodeWithText("午间散步").assertIsDisplayed()
                capture(context, "archived")
                rule.onNodeWithText("完成").performClick()
                val matches =
                    store.search(
                        JournalSearchQuery(
                            topicIds =
                                setOf(entry.labels.first { it.kind == JournalLabelKind.TOPIC }.id),
                            activities = setOf(activityId),
                        )
                    )
                assertEquals(listOf(entry.id), matches.entries.map { it.id })
                assertEquals(
                    listOf(entry.id),
                    store.search(JournalSearchQuery(text = "个人计划")).entries.map { it.id },
                )
                assertEquals(
                    listOf(entry.id),
                    store.search(JournalSearchQuery(text = "个人项目")).entries.map { it.id },
                )
                assertEquals(
                    setOf("个人项目", "午间散步"),
                    store.entries().single().labels.map { it.savedName }.toSet(),
                )
            }
        }
        if (phase == "read") {
            val before =
                kotlinx.coroutines
                    .runBlocking { store.readRepository.libraryState(0).labels }
                    .toSet()
            val output = ByteArrayOutputStream()
            store.writeBackup(output, null)
            val unused =
                JournalLabel(
                    java.util.UUID.randomUUID().toString(),
                    JournalLabelKind.TOPIC,
                    "未使用的主题",
                )
            store.saveLabel(unused)
            store.restoreBackup(
                ByteArrayInputStream(output.toByteArray()),
                null,
                mode = BackupRestoreMode.REPLACE,
            )
            assertEquals(
                before,
                kotlinx.coroutines
                    .runBlocking { store.readRepository.libraryState(0).labels }
                    .toSet(),
            )
            assertTrue(store.canUndoLastRestore())
            store.restoreBackup(ByteArrayInputStream(output.toByteArray()), null)
            assertTrue(store.canUndoLastRestore())
            store.undoLastRestore()
            assertTrue(
                kotlinx.coroutines
                    .runBlocking { store.readRepository.libraryState(0).labels }
                    .any { it.id == unused.id }
            )
            assertEquals(1, store.entries().size)
        }
    }

    private fun openTopics() {
        if (rule.onAllNodesWithText("管理主题").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("再留下一点").performScrollTo().performClick()
    }

    @Test
    fun previewCurrentActivityAtConfiguredLanguageAndSize() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("labelsPreview") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(
            context
                .getSharedPreferences("labels-ui-validation", Context.MODE_PRIVATE)
                .getBoolean("created-by-this-test", false)
        )
        val english = args.getString("previewLanguage") == "en"
        LanguagePreferences(context).language =
            if (english) AppLanguage.ENGLISH else AppLanguage.CHINESE
        args.getString("previewStyle")?.let {
            AppearancePreferences(context).saveStyle(com.xike.app.ui.theme.AppStyle.valueOf(it))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            val manager = if (english) "Manage topics" else "管理主题"
            if (rule.onAllNodesWithText(manager).fetchSemanticsNodes().isEmpty())
                rule.onNodeWithText(localizedText("再留下一点")).performScrollTo().performClick()
            rule.onNodeWithText(manager).performScrollTo().assertIsDisplayed()
            capture(context, args.getString("previewName") + "-choices")
            rule.onNodeWithText(manager).performClick()
            val definition =
                model!!.libraryState.labels.first {
                    if (args.getString("previewLongName") == "true") it.name == "个人长期学习计划".repeat(3)
                    else it.name == "个人项目" || it.name == "个人计划"
                }
            rule
                .onNodeWithTag("label-manager-list")
                .performScrollToNode(hasText(definition.displayName))
            capture(context, args.getString("previewName") + "-manager")
            rule.onNodeWithTag("topic-manager-add").assertIsDisplayed()
            rule.onNodeWithTag("topic-manager-sort").assertIsDisplayed()
            rule
                .onNodeWithContentDescription(
                    tr(
                        "${definition.displayName}的更多操作",
                        "More actions for ${definition.displayName}",
                    )
                )
                .performClick()
            capture(context, args.getString("previewName") + "-menu")
            rule.onNodeWithText(tr("编辑", "Edit")).performClick()
            rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertIsDisplayed()
            rule.onNodeWithText(if (english) "Save" else "保存").assertIsDisplayed()
            capture(context, args.getString("previewName") + "-editor")
            rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performClick()
            capture(context, args.getString("previewName") + "-editor-keyboard")
            rule.onNodeWithText(if (english) "Save" else "保存").assertIsDisplayed()
            assertSaveAboveKeyboard(if (english) "Save" else "保存")
        }
    }

    private fun assertSaveAboveKeyboard(text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags =
                    flags or
                        android.accessibilityservice.AccessibilityServiceInfo
                            .FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        rule.waitUntil(5_000) {
            automation.windows.any {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
        }
        val keyboard =
            automation.windows.first {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
        val keyboardBounds = android.graphics.Rect().also { keyboard.getBoundsInScreen(it) }
        val save =
            automation.windows
                .filter {
                    it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
                }
                .firstNotNullOfOrNull { window ->
                    val queue =
                        java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
                    window.root?.let(queue::add)
                    var found: android.view.accessibility.AccessibilityNodeInfo? = null
                    while (queue.isNotEmpty() && found == null) {
                        val node = queue.removeFirst()
                        if (node.text?.toString() == text) found = node
                        else
                            repeat(node.childCount) { index ->
                                node.getChild(index)?.let(queue::add)
                            }
                    }
                    found
                }
        checkNotNull(save) { "Save action is missing from the application accessibility tree" }
        val bounds = android.graphics.Rect().also { save.getBoundsInScreen(it) }
        assertTrue(
            "Save must be above the keyboard",
            bounds.height() > 0 && bounds.top >= 0 && bounds.bottom <= keyboardBounds.top,
        )
    }

    private fun addLabel(manager: String, name: String) {
        rule.onNodeWithText(manager).performScrollTo().performClick()
        rule.onNodeWithText(if (manager == "管理主题") "新增主题" else "新增").performClick()
        rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(name)
        rule.onNodeWithText("保存").performClick()
        awaitLabelEditorClosed()
        if (manager == "管理主题") rule.onNodeWithContentDescription("关闭主题管理").performClick()
        else rule.onNodeWithText("完成").performClick()
    }

    @Test
    fun managerSortingAndCancelledAddKeepContext() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("managerCancelCheck") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(
            context
                .getSharedPreferences("labels-ui-validation", Context.MODE_PRIVATE)
                .getBoolean("created-by-this-test", false)
        )
        val draft = JournalDraftStore(context).load()
        LanguagePreferences(context).language = AppLanguage.CHINESE
        AppearancePreferences(context).saveStyle(com.xike.app.ui.theme.AppStyle.BREATHE)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity {
                model = androidx.lifecycle.ViewModelProvider(it)[JournalViewModel::class.java]
            }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            openTopics()
            rule.onNodeWithText("管理主题").performScrollTo().performClick()
            fun active() =
                model!!.libraryState.labels.filter {
                    it.kind == JournalLabelKind.TOPIC && !it.isArchived
                }
            val order = active().map { it.id }
            val first = active().first()
            rule.onNodeWithTag("topic-manager-sort").performClick()
            rule.onNodeWithContentDescription("下移${first.displayName}").performClick()
            rule.waitUntil(10_000) { active().first().id != first.id }
            rule.onNodeWithContentDescription("上移${first.displayName}").performClick()
            rule.waitUntil(10_000) { active().map { it.id } == order }
            rule.onNodeWithTag("topic-manager-sort-done").performClick()
            rule.onNodeWithText("已归档").performClick()
            rule.onNodeWithTag("topic-manager-add").performClick()
            rule.onNodeWithText("取消").performClick()
            awaitLabelEditorClosed()
            rule.onNodeWithText("已归档").assertIsSelected()
            rule.onNodeWithTag("topic-manager-add").assertIsDisplayed()
            capture(context, "archive-after-cancel")
            rule.onNodeWithContentDescription("关闭主题管理").performClick()
        }
        assertEquals(draft, JournalDraftStore(context).load())
    }

    @Test
    fun emptyManagerKeepsAddVisibleAndDoesNotChangeEntries() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("managerEmptyCheck") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(
            context
                .getSharedPreferences("labels-ui-validation", Context.MODE_PRIVATE)
                .getBoolean("created-by-this-test", false)
        )
        val store = JournalStore(context).also { it.initialize(loadEntries = false) }
        val entries = store.entries()
        LanguagePreferences(context).language = AppLanguage.ENGLISH
        AppearancePreferences(context).saveStyle(com.xike.app.ui.theme.AppStyle.PAPER)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity {
                model = androidx.lifecycle.ViewModelProvider(it)[JournalViewModel::class.java]
            }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            if (rule.onAllNodesWithText("Manage topics").fetchSemanticsNodes().isEmpty())
                rule.onNodeWithText(localizedText("再留下一点")).performScrollTo().performClick()
            rule.onNodeWithText("Manage topics").performScrollTo().performClick()
            fun active() =
                model!!.libraryState.labels.filter {
                    it.kind == JournalLabelKind.TOPIC && !it.isArchived
                }
            while (active().isNotEmpty()) {
                val label = active().first()
                rule
                    .onNodeWithTag("label-manager-list")
                    .performScrollToNode(hasText(label.displayName))
                rule
                    .onNodeWithContentDescription("More actions for ${label.displayName}")
                    .performClick()
                rule.onNodeWithText("Archive").performClick()
                rule.waitUntil(10_000) { active().none { it.id == label.id } }
            }
            rule.onNodeWithTag("topic-manager-add").assertIsDisplayed()
            rule.onNodeWithTag("topic-manager-sort").assertIsNotEnabled()
            capture(context, "empty-manager-large-font")
            rule.onNodeWithTag("topic-manager-add").performClick()
            rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("临时主题")
            rule.onNodeWithText("Save").assertIsDisplayed().performClick()
            rule.waitUntil(10_000) { active().any { it.name == "临时主题" } }
            awaitLabelEditorClosed()
            rule.onNodeWithTag("topic-manager-add").assertIsDisplayed()
            capture(context, "added-from-empty-large-font")
            rule.onNodeWithContentDescription("Close topic manager").performClick()
        }
        assertEquals(entries, store.entries())
    }

    private fun awaitLabelEditorClosed() {
        rule.waitUntil(10_000) {
            rule
                .onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun capture(context: Context, name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Native dialog window animations continue after Compose becomes idle.
        android.os.SystemClock.sleep(350)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory =
            File(context.getExternalFilesDir(null), "labels-validation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
