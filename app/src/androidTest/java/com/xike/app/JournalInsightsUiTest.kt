package com.xike.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class JournalInsightsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyInsightsExplainEvidenceAndDisableLocalReview() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalInsightsScreen(
                    padding = PaddingValues(),
                    entries = emptyList(),
                    openImage = { null },
                )
            }
        }

        composeRule.onNodeWithText("暂无样本").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("有记录后可生成"))
        composeRule.onNodeWithText("有记录后可生成").assertIsDisplayed()
    }

    @Test
    fun moodDistributionDrillsDownToItsOriginalEntries() {
        val entries = listOf(
            entry("good-1", Mood.GOOD, "第一条可追溯记录"),
            entry("good-2", Mood.GOOD, "第二条可追溯记录"),
            entry("calm", Mood.CALM, "平静记录"),
        )
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalInsightsScreen(
                    padding = PaddingValues(),
                    entries = entries,
                    openImage = { null },
                )
            }
        }

        composeRule.onNodeWithText("2 次 · 67%").performScrollTo().performClick()
        composeRule.onNodeWithText("轻松 · 2 条").assertIsDisplayed()
        composeRule.onNodeWithText("第一条可追溯记录").assertIsDisplayed()
        composeRule.onNodeWithText("第二条可追溯记录").assertIsDisplayed()
    }

    @Test
    fun localReviewDisclosesLimitsBeforeSharing() {
        val entries = listOf(entry("one", Mood.CALM, "本地回顾记录"))
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalInsightsScreen(
                    padding = PaddingValues(),
                    entries = entries,
                    openImage = { null },
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("查看本地回顾"))
        composeRule.onNodeWithText("查看本地回顾").performClick()
        composeRule.onNodeWithText("本地回顾").assertIsDisplayed()
        composeRule.onNodeWithText("不代表原因、诊断或建议", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("息刻不会自动上传", substring = true).assertIsDisplayed()
    }

    private fun entry(id: String, mood: Mood, note: String): JournalEntry = JournalEntry(
        id = id,
        createdAt = LocalDate.now().atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        mood = mood,
        tags = listOf("自我"),
        note = note,
    )
}
