package com.xike.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class JournalEditLifecycleTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun restoredEditorReadsTheCommittedVideoRemovalInsteadOfItsOlderSavedState() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("editIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) { !model.isLoading }
        val store = JournalStore(rule.activity)
        val entries = store.entries()
        assumeTrue("Requires one app-created record containing a real video", entries.size == 1 && entries.single().video != null)
        val entry = entries.single()
        rule.onNodeWithText(localizedText("回望")).performClick()
        rule.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasContentDescription(localizedText("打开筛选"))))
            .performScrollToNode(hasText(entry.mood.label))
        rule.onAllNodesWithText(entry.mood.label)[0].performClick()
        rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
        lateinit var state: EditOperationState
        rule.runOnUiThread { state = model.editOperations.state(entry.id) }
        rule.waitUntil(5_000) { !state.busy }
        rule.onNodeWithContentDescription(tr("移除视频", "Remove video")).assertExists()
        // Commit the real backend removal before the older UI state is restored.
        // This is the durable state left by a completed worker whose result was not consumed.
        JournalVideoStore(rule.activity).saveEdit(entry, null)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5_000) { !state.busy }
        rule.onNodeWithContentDescription(tr("移除视频", "Remove video")).assertDoesNotExist()
        rule.onNodeWithText(localizedText("保存")).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(localizedText("取消编辑")).fetchSemanticsNodes().isEmpty()
        }
        assertNull(store.entries().single().video)
    }

    @Test fun rotatingDuringARealDatabaseWriteKeepsTheSaveGuardAndConsumesItsResult() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("editIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) { !model.isLoading }
        val entries = JournalStore(rule.activity).entries()
        assumeTrue("Requires one app-created record", entries.size == 1)
        val entry = entries.single()
        rule.onNodeWithText(localizedText("回望")).performClick()
        rule.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasContentDescription(localizedText("打开筛选"))))
            .performScrollToNode(hasText(entry.mood.label))
        rule.onAllNodesWithText(entry.mood.label)[0].performClick()
        rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
        lateinit var state: EditOperationState
        rule.runOnUiThread { state = model.editOperations.state(entry.id) }
        rule.waitUntil(5_000) { !state.busy }
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val database = JournalDatabase.get(rule.activity)
        val writer = thread {
            database.runInTransaction { locked.countDown(); check(release.await(20, TimeUnit.SECONDS)) }
        }
        try {
            assertTrue(locked.await(5, TimeUnit.SECONDS))
            rule.onNodeWithText(localizedText("保存")).performClick()
            rule.waitUntil(5_000) { state.operation == EditOperation.SAVE }
            rule.activityRule.scenario.recreate()
            assertSame(model, ViewModelProvider(rule.activity)[JournalViewModel::class.java])
            assertTrue(state.busy)
            rule.onNodeWithText(localizedText("保存中")).assertIsNotEnabled()
            rule.onNodeWithContentDescription(localizedText("取消编辑")).assertIsNotEnabled()
        } finally { release.countDown(); writer.join(5_000) }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(localizedText("取消编辑")).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithContentDescription(localizedText("编辑记录")).assertIsDisplayed()
        assertEquals(entry.note.trim(), JournalStore(rule.activity).entries().single().note)
    }
}
