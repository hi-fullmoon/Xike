package com.xike.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertTrue
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
    fun largeFontOverviewKeepsHeadlineClearOfMoodBadge() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                XikeTheme(AppTheme.OCEAN) {
                    JournalInsightsScreen(
                        padding = PaddingValues(),
                        entries = emptyList(),
                        openImage = { null },
                    )
                }
            }
        }

        val headlineBounds = composeRule
            .onNodeWithTag("insights-overview-headline", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val badgeBounds = composeRule
            .onNodeWithTag("insights-overview-badge", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("Large-font headline should be placed below the mood badge", headlineBounds.top >= badgeBounds.bottom)
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

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("2 次 · 67%"))
        composeRule.onNodeWithText("2 次 · 67%").performClick()
        composeRule.onNodeWithText("轻松 · 2 条").assertIsDisplayed()
        composeRule.onNodeWithText("第一条可追溯记录").assertIsDisplayed()
        composeRule.onNodeWithText("第二条可追溯记录").assertIsDisplayed()
    }

    @Test
    fun trendPointStillOpensItsOriginalEntries() {
        val entries = listOf(
            entry("trend-1", Mood.GOOD, "趋势里的第一条"),
            entry("trend-2", Mood.CALM, "趋势里的第二条"),
            entry("trend-3", Mood.TIRED, "趋势里的第三条"),
        )
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalInsightsScreen(PaddingValues(), entries, openImage = { null })
            }
        }

        composeRule.onNode(hasContentDescription("3 条记录", substring = true)).performScrollTo().performClick()
        composeRule.onNodeWithText("趋势里的第一条").assertIsDisplayed()
        composeRule.onNodeWithText("趋势里的第二条").assertIsDisplayed()
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

        composeRule.onNodeWithText("已留下 1 条心情记录").assertIsDisplayed()
        composeRule.onNodeWithText("先看看留下的记录，积累更多片段后再回顾变化。").assertIsDisplayed()
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
