package com.xike.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.JournalLabel
import com.xike.app.data.JournalLabelKind
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.ui.theme.AppStyle
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Run explicitly in separate processes on this task's fresh, isolated device. */
class JournalTopicDefaultsUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun defaultsAndUserChangesSurviveRestart() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("topicDefaultsPhase")
        assumeTrue(phase in listOf("preview", "fresh", "verify"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences =
            context.getSharedPreferences("topic-defaults-validation", Context.MODE_PRIVATE)
        val store = JournalStore(context).also { it.initialize(loadEntries = false) }
        check(store.entries().isEmpty() && JournalDraftStore(context).load().isEmpty) {
            "Use this task's isolated device without entries or a draft"
        }
        val catalog = runBlocking { store.readRepository.libraryState(0).labels }
        val preview = phase == "preview"
        LanguagePreferences(context).language =
            if (preview) AppLanguage.ENGLISH else AppLanguage.CHINESE
        AppearancePreferences(context).saveStyle(if (preview) AppStyle.PAPER else AppStyle.BREATHE)
        if (phase == "verify") {
            val values = JSONArray(checkNotNull(preferences.getString("catalog", null)))
            val expected = List(values.length()) { JournalLabel.fromJson(values.getJSONObject(it)) }
            assertEquals(expected.sortedBy { it.id }, catalog.sortedBy { it.id })
        } else {
            check(preferences.getString("catalog", null) == null)
            assertEquals(
                setOf("工作", "学习", "关系", "身体", "兴趣", "自我"),
                catalog
                    .filter { it.kind == JournalLabelKind.TOPIC && !it.isArchived }
                    .map { it.name }
                    .toSet(),
            )
            assertEquals(7, catalog.count { it.kind == JournalLabelKind.TOPIC && it.isArchived })
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            rule.onNodeWithText(localizedText("再留下一点")).performScrollTo().performClick()
            if (phase == "verify") {
                rule.onNodeWithText("工作").assertDoesNotExist()
                rule.onNodeWithText("家庭").performScrollTo().assertIsDisplayed()
                capture(context, "preserved-directory")
                return@use
            }
            listOf("工作", "学习", "关系", "身体", "兴趣", "自我").forEach {
                rule.onNodeWithText(localizedText(it)).assertExists()
            }
            listOf("家庭", "睡眠", "饮食", "金钱", "出行", "居住", "其他").forEach {
                rule.onNodeWithText(localizedText(it)).assertDoesNotExist()
            }
            rule.onNodeWithText(localizedText("兴趣")).performScrollTo().assertIsDisplayed()
            capture(context, if (preview) "english-default-six" else "default-six")
            rule.onNodeWithText(tr("管理主题", "Manage topics")).performScrollTo().performClick()
            rule.onNodeWithTag("topic-manager-add").assertIsDisplayed()
            capture(context, if (preview) "english-manager-active" else "manager-active")
            rule.onNodeWithText(tr("已归档", "Archived")).performClick()
            if (preview) {
                rule
                    .onNodeWithTag("label-manager-list")
                    .performScrollToNode(hasText(localizedText("其他")))
                capture(context, "english-archived-topics")
                return@use
            }
            val family = catalog.first { it.kind == JournalLabelKind.TOPIC && it.name == "家庭" }
            capture(context, "manager-archived")
            val work = catalog.first { it.kind == JournalLabelKind.TOPIC && it.name == "工作" }
            rule.onNodeWithTag("label-manager-list").performScrollToNode(hasText("家庭"))
            rule
                .onNode(hasText("恢复") and hasAnyAncestor(hasTestTag("label-row:${family.id}")))
                .performClick()
            rule.waitUntil(10_000) {
                model!!.libraryState.labels.any { it.id == family.id && !it.isArchived }
            }
            rule.onNodeWithText("使用中").performClick()
            rule.onNodeWithTag("topic-manager-sort").performClick()
            capture(context, "manager-sorting")
            rule.onNodeWithTag("label-manager-list").performScrollToNode(hasText("家庭"))
            rule.onNodeWithContentDescription("上移家庭").performClick()
            rule.waitUntil(10_000) {
                val labels = model!!.libraryState.labels
                labels.first { it.id == family.id }.sortOrder <
                    labels.first { it.name == "关系" && it.kind == JournalLabelKind.TOPIC }.sortOrder
            }
            rule.onNodeWithTag("topic-manager-sort-done").performClick()
            rule.onNodeWithTag("label-manager-list").performScrollToNode(hasText("工作"))
            rule.onNodeWithContentDescription("工作的更多操作").performClick()
            capture(context, "manager-menu")
            rule.onNodeWithText("归档").performClick()
            rule.waitUntil(10_000) {
                model!!.libraryState.labels.any { it.id == work.id && it.isArchived }
            }
            val expected = model!!.libraryState.labels
            check(
                preferences
                    .edit()
                    .putString("catalog", JSONArray(expected.map { it.toJson() }).toString())
                    .commit()
            )
            rule.onNodeWithContentDescription("关闭主题管理").performClick()
        }
    }

    @Test
    fun momentTopicManagerSharesHeaderWithoutClipping() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("topicHeaderPreview") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val english = args.getString("previewLanguage") == "en"
        val inline = args.getString("expectInline") == "true"
        LanguagePreferences(context).language =
            if (english) AppLanguage.ENGLISH else AppLanguage.CHINESE
        AppearancePreferences(context)
            .saveStyle(AppStyle.valueOf(args.getString("previewStyle") ?: "BREATHE"))
        val draftStore = JournalDraftStore(context)
        check(draftStore.load().isEmpty) { "Use this task's isolated device with an empty draft" }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            rule.onNodeWithText(localizedText("再留下一点")).performScrollTo().performClick()
            val manage = tr("管理主题", "Manage topics")
            fun checkHeader(count: String, suffix: String) {
                rule.onNodeWithText(manage).performScrollTo().assertIsDisplayed()
                val manager = rule.onNodeWithText(manage, useUnmergedTree = true)
                val countNode = rule.onNodeWithText(count, useUnmergedTree = true)
                countNode.assertIsDisplayed()
                capture(context, args.getString("previewName") + suffix)
                if (inline) {
                    val title =
                        rule
                            .onNodeWithText(localizedText("主题"), useUnmergedTree = true)
                            .getUnclippedBoundsInRoot()
                    val action = manager.getUnclippedBoundsInRoot()
                    assertTrue(
                        "Management action must share the title row",
                        action.top < title.bottom && action.bottom > title.top,
                    )
                }
                listOf(manager, countNode).forEach { interaction ->
                    val node = interaction.fetchSemanticsNode()
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    check(
                        node.config[
                                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
                            .action!!
                            .invoke(layouts)
                    )
                    val layout = layouts.single()
                    assertFalse(
                        "Header text must not be vertically clipped",
                        layout.didOverflowHeight,
                    )
                    assertEquals(
                        layout.layoutInput.text.length,
                        layout.getLineEnd(layout.lineCount - 1),
                    )
                    repeat(layout.lineCount) { line ->
                        assertFalse(layout.isLineEllipsized(line))
                        // Paragraph metrics are fractional; layout bounds use integer pixels.
                        assertTrue(
                            "Header text exceeds its width: ${layout.layoutInput.text}",
                            layout.getLineRight(line) <= layout.size.width + 1f,
                        )
                    }
                }
            }
            checkHeader(localizedText("可多选"), "-empty")
            rule.onNodeWithText(manage).performClick()
            rule.onNode(hasText(manage) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            rule.onNodeWithContentDescription(tr("关闭主题管理", "Close topic manager")).performClick()
            val work =
                model!!.libraryState.labels.first {
                    it.kind == JournalLabelKind.TOPIC && it.name == "工作"
                }
            rule.onNodeWithText(work.displayName).performScrollTo().performClick()
            rule.waitUntil(10_000) { draftStore.load().labels.any { it.id == work.id } }
            checkHeader(tr("已选 1", "1 selected"), "-selected")
            rule.onNodeWithText(work.displayName).performScrollTo().performClick()
            rule.waitUntil(10_000) { draftStore.load().isEmpty }
        }
    }

    private fun capture(context: Context, name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory =
            File(context.getExternalFilesDir(null), "topic-defaults-validation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
