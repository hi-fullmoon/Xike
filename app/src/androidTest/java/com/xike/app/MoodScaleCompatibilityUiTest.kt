package com.xike.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.Mood
import com.xike.app.feature.insights.InsightsPeriod
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Start from a record saved through the old app UI on an isolated device, then upgrade. */
class MoodScaleCompatibilityUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun legacyEditAndNewScaleRemainDistinctAfterUpgrade() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("legacyMoodUpgrade") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context)
        store.initialize(loadEntries = false)
        val legacy = store.entries().single { !it.mood.isOverallScale }
        val previousRun = store.entries().singleOrNull { it.mood.isOverallScale }
        check(
            store.entries().size <= 2 &&
                store.entries().all {
                    it.note.isEmpty() &&
                        it.tags.isEmpty() &&
                        it.imageFileNames.isEmpty() &&
                        it.audio == null &&
                        it.video == null
                }
        )
        assertTrue(legacy.mood in Mood.legacyChoices)
        assertEquals(0, legacy.moodScaleVersion)
        LanguagePreferences(context).language = AppLanguage.CHINESE
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText("回望").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("回望").performClick()
            if (previousRun != null) {
                assertEquals(Mood.BELOW_AVERAGE, previousRun.mood)
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithText("不太好").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText("不太好").performClick()
                rule.onNodeWithText("删除记录").performClick()
                rule.onNodeWithText("确认删除").performClick()
                rule.waitUntil(10_000) { store.entries().none { it.id == previousRun.id } }
            }
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText(legacy.mood.label).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(legacy.mood.label).performClick()
            rule.onNodeWithContentDescription("编辑记录").performClick()
            if (legacy.mood == Mood.CALM) {
                rule.onNodeWithText("疲惫").performScrollTo().performClick()
                rule.onNodeWithText("保存").performClick()
                rule.waitUntil(10_000) { store.entries().single().mood == Mood.TIRED }
                rule.onNodeWithContentDescription("编辑记录").performClick()
            }
            rule.onNodeWithText("平静").performScrollTo().performClick()
            rule.onNodeWithText("一般").assertDoesNotExist()
            capture("legacy-mood-editor")
            rule.onNodeWithText("保存").performClick()
            rule.waitUntil(10_000) { store.entries().single().mood == Mood.CALM }
            assertEquals(0, store.entries().single().moodScaleVersion)
            rule.onNodeWithContentDescription("关闭记录详情").performClick()
            rule.onNodeWithText("此刻").performClick()
            rule.onNodeWithText("不太好").performScrollTo().performClick()
            rule.onNodeWithText("记下此刻").performClick()
            rule.waitUntil(10_000) { store.entries().size == 2 }
            val current = store.entries().single { it.id != legacy.id }
            assertEquals(Mood.BELOW_AVERAGE, current.mood)
            assertEquals(1, current.moodScaleVersion)
            runBlocking {
                val repository = store.readRepository
                assertEquals(
                    listOf(current.id),
                    repository
                        .indexPage(JournalSearchQuery(moods = setOf(Mood.BELOW_AVERAGE)), 0)
                        .entries
                        .map { it.id },
                )
                assertEquals(
                    listOf(legacy.id),
                    repository
                        .indexPage(JournalSearchQuery(moods = setOf(Mood.CALM)), 0)
                        .entries
                        .map { it.id },
                )
                val summary = repository.insights(InsightsPeriod.WEEK, LocalDate.now())
                assertEquals(
                    setOf("平静", "不太好"),
                    summary.moodDistribution.map { it.mood.label }.toSet(),
                )
            }
            rule.onNodeWithText("回望").performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText("不太好").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithContentDescription("打开筛选").performClick()
            val rowId =
                rule
                    .onNode(
                        hasScrollToIndexAction() and
                            hasAnyDescendant(hasText("低落")) and
                            !hasTestTag("archive-list")
                    )
                    .fetchSemanticsNode()
                    .id
            val row = SemanticsMatcher("mood filter row") { it.id == rowId }
            rule.onNode(row).performScrollToNode(hasText("不太好"))
            rule.onNode(hasText("不太好") and hasAnyAncestor(row)).performClick()
            rule.waitUntil(10_000) {
                rule
                    .onAllNodes(hasText("平静") and hasAnyAncestor(hasTestTag("archive-list")))
                    .fetchSemanticsNodes()
                    .isEmpty()
            }
            rule
                .onNode(
                    hasText("不太好") and
                        hasAnyAncestor(hasTestTag("archive-list")) and
                        !hasAnyAncestor(row)
                )
                .assertIsDisplayed()
            capture("new-mood-filter")
            rule.onNodeWithText("轨迹").performClick()
            rule.onNodeWithTag("insights-list").performScrollToNode(hasText("心情出现次数"))
            rule.onNodeWithText("心情出现次数").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("不太好").assertIsDisplayed()
            rule.onAllNodesWithText("平静").onLast().assertIsDisplayed()
            capture("mixed-mood-distribution")
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
