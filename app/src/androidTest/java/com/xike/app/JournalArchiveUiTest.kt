package com.xike.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JournalArchiveUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchShowsOnlyMatchingJournalAndItsTotal() {
        val today = LocalDate.now()
        val entries = listOf(
            entry("work", today, listOf("工作"), "工作完成得很顺利"),
            entry("rest", today.minusDays(1), listOf("睡眠"), "早点休息"),
        )

        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalArchiveScreen(
                    padding = PaddingValues(),
                    entries = entries,
                    onSearch = { query, offset, limit ->
                        val matches = filterJournalEntries(entries, query)
                        Result.success(
                            JournalSearchPage(
                                entries = matches.drop(offset).take(limit),
                                totalCount = matches.size,
                                offset = offset,
                            ),
                        )
                    },
                    onUpdate = { updated, _, _ -> Result.success(updated) },
                    onDelete = { Result.success(Unit) },
                    onUndoDelete = { Result.success(Unit) },
                    onFinalizeDelete = { Result.success(Unit) },
                    openImage = { null },
                )
            }
        }

        composeRule.onNodeWithText("搜索注脚或关键词").performTextInput("工作")
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("找到 1 条").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("找到 1 条").fetchSemanticsNode()
        composeRule.onNodeWithText("工作完成得很顺利").performScrollTo().assertIsDisplayed()
        check(composeRule.onAllNodesWithText("早点休息").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun calendarDatesWithoutEntriesAreNotClickable() {
        val today = LocalDate.now()
        val emptyDate = generateSequence(today.withDayOfMonth(1)) { date -> date.plusDays(1) }
            .first { date -> date.month == today.month && date != today }
        val entries = listOf(entry("today", today, emptyList(), "今天的记录"))

        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalArchiveScreen(
                    padding = PaddingValues(),
                    entries = entries,
                    onSearch = { query, offset, limit ->
                        val matches = filterJournalEntries(entries, query)
                        Result.success(
                            JournalSearchPage(
                                entries = matches.drop(offset).take(limit),
                                totalCount = matches.size,
                                offset = offset,
                            ),
                        )
                    },
                    onUpdate = { updated, _, _ -> Result.success(updated) },
                    onDelete = { Result.success(Unit) },
                    onUndoDelete = { Result.success(Unit) },
                    onFinalizeDelete = { Result.success(Unit) },
                    openImage = { null },
                )
            }
        }

        val spokenDate = emptyDate.format(
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA),
        )
        composeRule.onNodeWithContentDescription("$spokenDate，没有记录").assertIsNotEnabled()
    }

    @Test
    fun selectingRecordedSundayKeepsCalendarScrollPosition() {
        val today = LocalDate.now()
        val firstOfMonth = today.withDayOfMonth(1)
        val sunday = generateSequence(firstOfMonth) { date -> date.plusDays(1) }
            .first { date -> date.dayOfWeek == DayOfWeek.SUNDAY }
        val entries = buildList {
            add(entry("sunday", sunday, emptyList(), "周日记录"))
            repeat(24) { index ->
                add(
                    entry(
                        "history-$index",
                        firstOfMonth.minusDays(index.toLong() + 1),
                        emptyList(),
                        "历史记录 $index",
                    ),
                )
            }
        }.distinctBy(JournalEntry::id)

        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalArchiveScreen(
                    padding = PaddingValues(),
                    entries = entries,
                    onSearch = { query, offset, limit ->
                        val matches = filterJournalEntries(entries, query)
                        Result.success(
                            JournalSearchPage(
                                entries = matches.drop(offset).take(limit),
                                totalCount = matches.size,
                                offset = offset,
                            ),
                        )
                    },
                    onUpdate = { updated, _, _ -> Result.success(updated) },
                    onDelete = { Result.success(Unit) },
                    onUndoDelete = { Result.success(Unit) },
                    onFinalizeDelete = { Result.success(Unit) },
                    openImage = { null },
                )
            }
        }

        val spokenDate = sunday.format(
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA),
        )
        val sundayNode = composeRule.onNodeWithContentDescription("$spokenDate，1 条记录")
        sundayNode.performScrollTo()
        val beforeTop = sundayNode.fetchSemanticsNode().boundsInRoot.top
        sundayNode.performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText(
                sunday.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)) + "的记录",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        val afterTop = sundayNode.fetchSemanticsNode().boundsInRoot.top

        assertTrue("calendar moved from $beforeTop to $afterTop", abs(afterTop - beforeTop) < 2f)
    }

    @Test
    fun deletingARecordRequiresExplicitSecondConfirmation() {
        val journal = entry("delete-me", LocalDate.now(), listOf("自我"), "准备删除的记录")
        var deletedEntry: JournalEntry? = null
        var restoredEntryId: String? = null
        var finalizedEntryId: String? = null

        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalArchiveScreen(
                    padding = PaddingValues(),
                    entries = listOf(journal),
                    onSearch = { query, offset, limit ->
                        val matches = filterJournalEntries(listOf(journal), query)
                        Result.success(JournalSearchPage(matches.drop(offset).take(limit), matches.size, offset))
                    },
                    onUpdate = { updated, _, _ -> Result.success(updated) },
                    onDelete = {
                        deletedEntry = it
                        Result.success(Unit)
                    },
                    onUndoDelete = {
                        restoredEntryId = it
                        Result.success(Unit)
                    },
                    onFinalizeDelete = {
                        finalizedEntryId = it
                        Result.success(Unit)
                    },
                    openImage = { null },
                )
            }
        }

        composeRule.onNodeWithText("准备删除的记录").performScrollTo().performClick()
        composeRule.onNodeWithText("删除记录").performScrollTo().performClick()
        composeRule.onNodeWithText("删除这条记录？").assertIsDisplayed()
        composeRule.onNodeWithText("删除后可在底部提示消失前撤销。请确认这不是误操作。").assertIsDisplayed()
        composeRule.runOnIdle { check(deletedEntry == null) }

        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) { deletedEntry?.id == journal.id }
        composeRule.onNodeWithText("撤销").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) { restoredEntryId == journal.id }
        composeRule.runOnIdle { check(finalizedEntryId == null) }
    }

    @Test
    fun editingARecordUpdatesWeatherNoteAndKeywords() {
        val journal = entry("edit-me", LocalDate.now(), listOf("自我"), "准备编辑的记录")
        var updatedEntry: JournalEntry? = null

        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalArchiveScreen(
                    padding = PaddingValues(),
                    entries = listOf(journal),
                    onSearch = { query, offset, limit ->
                        val matches = filterJournalEntries(listOf(journal), query)
                        Result.success(JournalSearchPage(matches.drop(offset).take(limit), matches.size, offset))
                    },
                    onUpdate = { updated, retainedImages, newImages ->
                        check(retainedImages.isEmpty())
                        check(newImages.isEmpty())
                        updatedEntry = updated
                        Result.success(updated)
                    },
                    onDelete = { Result.success(Unit) },
                    onUndoDelete = { Result.success(Unit) },
                    onFinalizeDelete = { Result.success(Unit) },
                    openImage = { null },
                )
            }
        }

        composeRule.onNodeWithText("准备编辑的记录").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("编辑记录").performClick()
        composeRule.onNodeWithText("编辑这一刻").assertIsDisplayed()
        composeRule.onNode(
            hasSetTextAction() and hasText("准备编辑的记录"),
        ).performTextReplacement("修改后的注脚")
        composeRule.onNodeWithContentDescription("微风").performClick()
        composeRule.onNodeWithText("工作").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("已选 2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("保存").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { updatedEntry != null }
        composeRule.runOnIdle {
            val saved = checkNotNull(updatedEntry)
            check(saved.note == "修改后的注脚")
            check(saved.mood == Mood.CALM)
            check(saved.tags == listOf("自我", "工作"))
        }
    }

    private fun entry(
        id: String,
        date: LocalDate,
        tags: List<String>,
        note: String,
    ) = JournalEntry(
        id = id,
        createdAt = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        mood = Mood.GOOD,
        tags = tags,
        note = note,
    )
}
