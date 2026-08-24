package com.xike.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import java.time.LocalDate
import java.time.ZoneId
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
                    openImage = { null },
                )
            }
        }

        composeRule.onNodeWithText("搜索笔记或主题").performTextInput("工作")
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("找到 1 条").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("找到 1 条").fetchSemanticsNode()
        composeRule.onNodeWithText("工作完成得很顺利").performScrollTo().assertIsDisplayed()
        check(composeRule.onAllNodesWithText("早点休息").fetchSemanticsNodes().isEmpty())
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
