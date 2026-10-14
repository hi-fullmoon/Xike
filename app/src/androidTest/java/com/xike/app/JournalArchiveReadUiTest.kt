package com.xike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.lifecycle.ViewModelProvider
import androidx.paging.PagingSource
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.Mood
import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.journalPeriodSummary
import com.xike.app.feature.archive.asShortChineseDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Requires this task's fresh emulator. Saves through the real UI; no seeded or mocked library. */
class JournalArchiveReadUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun initialInsightsReadFailureCanRetryWithoutChangingTheDatabase(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("archiveReadIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(20_000) { !model.isLoading }
        withUnreadableTable("journal_entries") {
            rule.onNodeWithTag("navigation-insights").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText(localizedText("我知道了")).fetchSemanticsNodes().isNotEmpty()
            }
            dismissReadErrorIfShown()
            rule.onNodeWithText(tr("重试", "Retry")).assertIsDisplayed()
            assertTrue(rule.onAllNodesWithContentDescription(localizedText("正在读取…")).fetchSemanticsNodes().isEmpty())
        }
        rule.onNodeWithText(tr("重试", "Retry")).performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText(tr("本周", "This week")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("insights-list").assertIsDisplayed()
        rule.onNodeWithTag("navigation-home").performClick()
        withUnreadableTable("journal_entries") {
            rule.onNodeWithTag("navigation-insights").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText(localizedText("我知道了")).fetchSemanticsNodes().isNotEmpty()
            }
            dismissReadErrorIfShown()
            rule.onNodeWithText(tr("重试", "Retry")).assertIsDisplayed()
        }
        val revision = model.libraryState.revision
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.waitUntil(15_000) { model.libraryState.revision > revision }
        rule.waitUntil(15_000) { rule.onAllNodesWithText(tr("本周", "This week")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("insights-list").assertIsDisplayed()
    }

    @Test
    fun loadMoreShowsTheNextRealSavedRecordAndPreservesEarlierRows(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("archiveReadIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(20_000) { !model.isLoading }
        check(model.draft.isEmpty)
        val originalCount = model.libraryState.totalCount
        val prefix = "回望分页核对 ${java.util.UUID.randomUUID()}"
        val query = JournalSearchQuery(text = prefix)
        val savedIds = mutableListOf<String>()
        val notes = mutableListOf<String>()
        try {
            rule.onNodeWithTag("navigation-home").performClick()
            // Exercise the actual 60-entry boundary through UI saves, without changing page size or seeding the database.
            repeat(61) { index ->
                val note = "$prefix 保存 ${index + 1}"
                notes += note
                rule.onNodeWithText(Mood.CALM.label).performScrollTo().assertIsEnabled().performClick().assertIsSelected()
                rule.onNodeWithContentDescription(localizedText("此刻的注脚")).performScrollTo().performTextInput(note)
                // Let Espresso synchronize the native IME instead of polling Compose view insets.
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                rule.onNodeWithText(localizedText("记下此刻")).assertIsEnabled().performClick()
                rule.waitUntil(15_000) {
                    model.libraryState.totalCount == originalCount + index + 1 && !model.isDraftSaving && model.draft.isEmpty
                }
                // Let the real notice expire before the next save; it may disappear at any time.
                rule.waitUntil(15_000) {
                    rule.onAllNodesWithContentDescription(localizedText("关闭")).fetchSemanticsNodes().isEmpty()
                }
                val id = model.readRepository.indexPage(query, 0).entries.first().id
                assertEquals(note, model.readRepository.entry(id)?.note)
                savedIds += id
            }
            val first = model.readRepository.indexPage(query, 0)
            assertEquals(61, first.totalCount)
            assertEquals(savedIds.asReversed().take(60), first.entries.map { it.id })
            assertEquals(listOf(savedIds.first()), model.readRepository.indexPage(query, 60, first.totalCount).entries.map { it.id })
            rule.onNodeWithTag("navigation-archive").performClick()
            rule.onNodeWithText(tr("时间线", "Timeline")).performClick()
            rule.onNodeWithText(localizedText("搜索注脚或主题")).performTextInput(prefix)
            rule.onNode(hasSetTextAction()).performImeAction()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.waitUntil(15_000) { rule.onAllNodesWithText(tr("找到 61 条", "61 entries found")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("archive-list").performScrollToNode(hasText(localizedText("加载更多记录")))
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasText(localizedText("加载更多记录")) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(rule.onAllNodesWithText(notes.first()).fetchSemanticsNodes().isEmpty())
            withUnreadableTable("journal_entries") {
                assertTrue(journalReadResult { model.readRepository.entry(savedIds.last()) }.isFailure)
                rule.onNodeWithText(localizedText("加载更多记录")).performClick()
                awaitArchiveReadError()
                rule.onNodeWithTag("archive-list").performScrollToIndex(4)
                rule.onNodeWithText(notes.last()).assertIsDisplayed()
                assertTrue(rule.onAllNodesWithText(localizedText("没有找到这一刻")).fetchSemanticsNodes().isEmpty())
                rule.onNodeWithTag("archive-list").performScrollToIndex(0)
                rule.onNodeWithText(localizedText("月历")).performClick()
                awaitArchiveReadError()
                rule.onNodeWithTag("archive-list").performScrollToIndex(5)
                rule.onNodeWithText(notes.last()).assertIsDisplayed()
            }
            rule.onNodeWithTag("archive-list").performScrollToIndex(0)
            rule.onNodeWithText(tr("时间线", "Timeline")).performClick()
            rule.onNodeWithTag("archive-list").performScrollToNode(hasText(localizedText("加载更多记录")))
            rule.onNodeWithText(localizedText("加载更多记录")).performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithText(localizedText("加载更多记录")).fetchSemanticsNodes().isEmpty() &&
                    rule.onAllNodesWithText(localizedText("正在读取…")).fetchSemanticsNodes().isEmpty()
            }
            rule.onNodeWithTag("archive-list").performScrollToNode(hasText(notes.first()))
            rule.onNodeWithText(notes.first()).assertIsDisplayed()
            rule.onNodeWithTag("archive-list").performScrollToIndex(4)
            rule.onNodeWithText(notes.last()).assertIsDisplayed()
        } finally {
            rule.onNodeWithTag("navigation-home").performClick()
            withContext(Dispatchers.Main) {
                if (model.draft.note.startsWith(prefix)) model.discardDraft()
            }
            // Remove only records saved by this test. Other entries and the user's settings remain intact.
            val owned = mutableListOf<JournalEntryIndex>()
            var offset = 0
            do {
                val page = model.readRepository.indexPage(query, offset)
                owned += page.entries
                offset += page.entries.size
            } while (page.entries.isNotEmpty() && offset < page.totalCount)
            for (reference in owned) {
                if (model.readRepository.entry(reference.id)?.note?.startsWith("$prefix ") == true) {
                    withContext(Dispatchers.IO) {
                        val store = JournalStore(rule.activity)
                        store.delete(reference.id, refreshEntries = false)
                        store.finalizeDelete(reference.id)
                    }
                }
            }
            rule.waitUntil(15_000) { model.libraryState.totalCount == originalCount }
        }
    }

    @Test
    fun savedRecordUsesDatabaseQueriesAndSurvivesEditDeleteUndoAndRecreation(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("archiveReadIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(20_000) { !model.isLoading }
        val originalCount = model.libraryState.totalCount
        check(model.draft.isEmpty)
        rule.onNodeWithText(Mood.CALM.label).performScrollTo().performClick()
        val note = "检查回望按需读取 ${System.currentTimeMillis()}"
        rule.onNodeWithContentDescription(localizedText("此刻的注脚")).performScrollTo().performTextInput(note)
        rule.onNodeWithText(localizedText("完成")).performScrollTo().performClick()
        rule.onNodeWithText(localizedText("记下此刻")).performClick()
        rule.waitUntil(15_000) { model.libraryState.totalCount == originalCount + 1 }
        val repository = model.readRepository
        val page = repository.indexPage(JournalSearchQuery(text = note), 0)
        assertEquals(1, page.totalCount)
        val saved = requireNotNull(repository.entry(page.entries.single().id))
        assertEquals(note, saved.note)
        val today = LocalDate.now()
        val date = java.time.Instant.ofEpochMilli(saved.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(mapOf(date to 1), repository.calendar(JournalSearchQuery(text = note), YearMonth.from(date)))
        val store = JournalStore(rule.activity)
        val stored = withContext(Dispatchers.IO) { store.entries() }
        for (period in InsightsPeriod.entries) {
            assertEquals(journalPeriodSummary(stored, period, today), repository.insights(period, today))
        }
        val source = JournalDatabase.get(rule.activity).journalDao().pagedRecords(
            repository.sql(JournalSearchQuery(text = note), "e.*", ordered = true),
        )
        val loaded = source.load(PagingSource.LoadParams.Refresh(null, 60, true))
        assertTrue(loaded is PagingSource.LoadResult.Page)
        assertEquals(listOf(saved), (loaded as PagingSource.LoadResult.Page).data.map(JournalEntryRecord::toJournalEntry))
        assertEquals(0, loaded.itemsBefore)
        assertEquals(0, loaded.itemsAfter)

        rule.onNodeWithTag("navigation-archive").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText(note).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(note).performScrollTo().performClick()
        rule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithContentDescription(localizedText("关闭记录详情")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
        val updatedNote = "$note，已复核"
        rule.onNodeWithContentDescription(localizedText("此刻的注脚")).performScrollTo().performTextReplacement(updatedNote)
        rule.onNodeWithText(localizedText("保存")).performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithContentDescription(localizedText("编辑记录")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(updatedNote, repository.entry(saved.id)?.note)
        rule.onNodeWithText(localizedText("删除记录")).performScrollTo().performClick()
        rule.onNodeWithText(localizedText("确认删除")).performClick()
        rule.onNodeWithText(localizedText("撤销")).assertIsDisplayed().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText(updatedNote).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(updatedNote, repository.entry(saved.id)?.note)
        rule.onNodeWithTag("archive-list").performScrollToIndex(0)
        rule.onNodeWithText(localizedText("搜索注脚或主题")).performTextInput("回望")
        val matchingCount = repository.indexPage(JournalSearchQuery(text = "回望"), 0).totalCount
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(tr("找到 $matchingCount 条", "$matchingCount entries found")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(localizedText("月历")).performClick()
        val calendarDate = date.format(java.time.format.DateTimeFormatter.ofPattern(tr("yyyy年M月d日 EEEE", "EEEE, MMM d, yyyy"), AppLocale.locale))
        val dayCount = repository.calendar(JournalSearchQuery(text = "回望"), YearMonth.from(date)).getValue(date)
        val description = "$calendarDate，${tr("$dayCount 条记录", "$dayCount entries")}"
        rule.waitUntil(15_000) {
            rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription(description).performScrollTo().performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(tr("${date.asShortChineseDate()}的记录", "Entries for ${date.asShortChineseDate()}")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("archive-list").performScrollToNode(hasText(updatedNote))
        rule.onNodeWithText(updatedNote).assertIsDisplayed()
        withUnreadableTable("journal_entries_fts") {
            assertTrue(journalReadResult { repository.indexPage(JournalSearchQuery(text = "回望"), 0) }.isFailure)
            val previousRevision = model.libraryState.revision
            touchEntry(saved.id)
            rule.waitUntil(15_000) { model.libraryState.revision > previousRevision }
            awaitArchiveReadError(pagerMayFail = true)
            dismissReadErrorIfShown()
            rule.onNodeWithTag("archive-list").performScrollToIndex(5)
            rule.onNodeWithText(updatedNote).assertIsDisplayed()
            assertTrue(rule.onAllNodesWithText(localizedText("没有找到这一刻")).fetchSemanticsNodes().isEmpty())
            rule.onNodeWithTag("archive-list").performScrollToIndex(0)
            // Clear the selected day. A failed query must retain the previous day's result heading.
            rule.onNodeWithContentDescription(description).performScrollTo().performClick()
            awaitArchiveReadError()
            rule.onNodeWithTag("archive-list").performScrollToNode(hasText(tr("${date.asShortChineseDate()}的记录", "Entries for ${date.asShortChineseDate()}")))
            rule.onNodeWithText(tr("${date.asShortChineseDate()}的记录", "Entries for ${date.asShortChineseDate()}")).assertIsDisplayed()
            rule.onNodeWithTag("archive-list").performScrollToIndex(0)
            rule.onNode(hasSetTextAction()).performTextReplacement("无匹配 ${java.util.UUID.randomUUID()}")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            awaitArchiveReadError()
            rule.onNodeWithTag("archive-list").performScrollToNode(hasText(updatedNote))
            rule.onNodeWithText(updatedNote).assertIsDisplayed()
        }
        rule.onNodeWithTag("archive-list").performScrollToNode(hasText(tr("重试", "Retry")))
        rule.onNodeWithText(tr("重试", "Retry")).performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(localizedText("没有找到这一刻")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("archive-list").performScrollToIndex(0)
        rule.onNode(hasSetTextAction()).performTextReplacement("回望")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        val beforeRecovery = model.libraryState.revision
        touchEntry(saved.id)
        rule.waitUntil(15_000) { model.libraryState.revision > beforeRecovery }
        rule.waitUntil(15_000) { readErrors().fetchSemanticsNodes().isEmpty() }
        assertEquals(updatedNote, repository.entry(saved.id)?.note)
        // Keep the actual app-created record for subsequent read-only acceptance and screenshots.
        rule.onNodeWithTag("navigation-insights").performClick()
        rule.onNodeWithTag("insights-list").performScrollToNode(hasText(localizedText("查看本地回顾")))
        rule.onNodeWithText(localizedText("查看本地回顾")).assertIsDisplayed()
        rule.onNodeWithTag("insights-list", useUnmergedTree = true).performScrollToIndex(0)
        rule.onNodeWithTag("insights-list", useUnmergedTree = true).performScrollToNode(hasTestTag("insights-overview-headline"))
        rule.onNodeWithTag("insights-overview-headline", useUnmergedTree = true).performTouchInput { click() }
        rule.waitUntil(15_000) { rule.onAllNodesWithText(updatedNote).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("insight-source-${saved.id}").performScrollTo().performClick()
        rule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithContentDescription(localizedText("关闭记录详情")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText(updatedNote) and hasAnyAncestor(hasTestTag("journal-detail-body"))).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(localizedText("关闭记录详情")).performClick()
        rule.onNodeWithTag("insight-source-${saved.id}").assertIsDisplayed()
    }

    private fun readErrors() = rule.onAllNodes(hasText(
        tr("当前显示上次成功读取的结果。", "Showing the last successful results."), substring = true,
    ))

    private fun awaitArchiveReadError(pagerMayFail: Boolean = false) {
        if (pagerMayFail) {
            rule.waitUntil(15_000) {
                readErrors().fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText(localizedText("我知道了")).fetchSemanticsNodes().isNotEmpty()
            }
        }
        dismissReadErrorIfShown()
        rule.onNodeWithTag("archive-list").performScrollToIndex(2)
        rule.waitUntil(15_000) {
            readErrors().fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasText(tr("重试", "Retry")) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissReadErrorIfShown() {
        val dismiss = rule.onAllNodesWithText(localizedText("我知道了"))
        if (dismiss.fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText(localizedText("我知道了")).performClick()
    }

    private suspend fun touchEntry(id: String) = withContext(Dispatchers.IO) {
        val database = JournalDatabase.get(rule.activity)
        database.runInTransaction {
            database.openHelper.writableDatabase.execSQL("UPDATE journal_entries SET created_at = created_at WHERE id = ?", arrayOf(id))
        }
    }

    /** Genuine SQLite failures in the explicitly isolated app database, with schema restored even on assertion failure. */
    private suspend fun <T> withUnreadableTable(table: String, block: suspend () -> T): T {
        require(table in setOf("journal_entries", "journal_entries_fts"))
        val database = JournalDatabase.get(rule.activity)
        val temporary = "${table}_archive_unavailable"
        withContext(Dispatchers.IO) {
            database.runInTransaction { database.openHelper.writableDatabase.execSQL("ALTER TABLE $table RENAME TO $temporary") }
        }
        return try { block() } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                database.runInTransaction { database.openHelper.writableDatabase.execSQL("ALTER TABLE $temporary RENAME TO $table") }
            }
        }
    }
}
