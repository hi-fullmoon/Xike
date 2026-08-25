package com.xike.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
                    onDelete = { Result.success(Unit) },
                    openImage = { null },
                )
            }
        }

        composeRule.onNodeWithText("搜索注脚或关键词").performTextInput("工作")
        composeRule.waitUntil(timeoutMillis = 3_000) {
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
                    onDelete = { Result.success(Unit) },
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
                    onDelete = { Result.success(Unit) },
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

        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalArchiveScreen(
                    padding = PaddingValues(),
                    entries = listOf(journal),
                    onSearch = { query, offset, limit ->
                        val matches = filterJournalEntries(listOf(journal), query)
                        Result.success(JournalSearchPage(matches.drop(offset).take(limit), matches.size, offset))
                    },
                    onDelete = {
                        deletedEntry = it
                        Result.success(Unit)
                    },
                    openImage = { null },
                )
            }
        }

        composeRule.onNodeWithText("准备删除的记录").performScrollTo().performClick()
        composeRule.onNodeWithText("删除记录").performScrollTo().performClick()
        composeRule.onNodeWithText("删除这条记录？").assertIsDisplayed()
        composeRule.onNodeWithText("删除后无法恢复。请确认这不是误操作。").assertIsDisplayed()
        composeRule.runOnIdle { check(deletedEntry == null) }

        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) { deletedEntry?.id == journal.id }
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
