package com.xike.app

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LocalReviewNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun originalEntriesReturnToTheSameReviewPosition() {
        // Read existing records only. Missing real records are a verification limit,
        // not a reason to create entries or clear another session's data.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context)
        val entries = store.entries()
        val summary = journalPeriodSummary(entries, InsightsPeriod.WEEK)
        val sources = localJournalReview(summary).sections.flatMap { it.sources }
        assumeTrue("Real records with a source below the overview are required", sources.size > 1)
        val source = sources.last()

        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalInsightsScreen(PaddingValues(), entries, store::openImage, store::openAudio)
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(localizedText("查看本地回顾")))
        composeRule.onNodeWithText(localizedText("查看本地回顾")).performClick()
        composeRule.onNodeWithText(source.label).performScrollTo()
        val before = reviewScrollPosition()
        assertTrue("Source should require scrolling", before > 0f)
        composeRule.onNodeWithText(source.label).performClick()
        composeRule.onNodeWithText(source.label).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(localizedText("关闭原始记录")).performClick()
        composeRule.onNodeWithText(localizedText("本地回顾")).assertIsDisplayed()
        composeRule.onNodeWithText(source.label).assertIsDisplayed()
        assertEquals(before, reviewScrollPosition(), 1f)
    }

    private fun reviewScrollPosition(): Float = composeRule.onNodeWithTag("local-review-scroll")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
}
