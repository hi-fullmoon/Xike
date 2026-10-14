package com.xike.app

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MomentContentUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mediaSheetOpensExistingPhotoAndVideoFlowsWithoutChangingDraft() {
        rule.waitUntil(15_000) {
            !ViewModelProvider(rule.activity)[JournalViewModel::class.java].isLoading
        }
        val viewModel = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        val originalDraft = viewModel.draft
        rule
            .onNodeWithText(tr("今天 · 修改时间", "Today · Change time"))
            .performScrollTo()
            .assertIsDisplayed()
        assertCompleteLabel(tr("今天 · 修改时间", "Today · Change time"))
        rule.onNodeWithContentDescription(tr("添加内容", "Add content")).assertIsDisplayed()
        rule.onNodeWithText(tr("视频", "Video")).assertDoesNotExist()
        openMedia()
        rule.onNodeWithText(tr("录音", "Record audio")).assertIsDisplayed()
        assertCompleteLabel(tr("录音", "Record audio"))
        rule.onNodeWithText(localizedText("照片")).performClick()
        rule.onNodeWithText(localizedText("添加照片")).assertIsDisplayed()
        rule.onNodeWithText(localizedText("取消")).performClick()
        openMedia()
        rule.onNodeWithText(tr("视频", "Video")).performClick()
        rule.onNodeWithText(tr("添加视频", "Add video")).assertIsDisplayed()
        rule.onNodeWithText(localizedText("取消")).performClick()
        openMedia()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText(tr("视频", "Video")).assertDoesNotExist()
        rule.onNodeWithContentDescription(localizedText("此刻的注脚")).performScrollTo().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(localizedText("完成")).fetchSemanticsNodes().size == 1 &&
                rule.onNodeWithText(localizedText("完成")).apply { performScrollTo() }.isDisplayed()
        }
        rule.onNodeWithText(localizedText("完成")).assertIsDisplayed().performClick()
        rule.waitUntil(5_000) {
            rule
                .onAllNodesWithText(tr("今天 · 修改时间", "Today · Change time"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule
            .onNodeWithText(tr("今天 · 修改时间", "Today · Change time"))
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(originalDraft, viewModel.draft)
    }

    private fun openMedia() {
        rule
            .onNodeWithContentDescription(tr("添加内容", "Add content"))
            .performScrollTo()
            .performTouchInput { click(Offset(width - 2f, height / 2f)) }
        rule.onNodeWithText(tr("视频", "Video")).assertIsDisplayed()
    }

    private fun assertCompleteLabel(label: String) {
        val node = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        check(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
        val layout = layouts.single()
        assertFalse("Vertically clipped label: $label", layout.didOverflowHeight)
        // Paragraph metrics are fractional; layout bounds are integer pixels.
        repeat(layout.lineCount) { line ->
            assertTrue("Clipped label: $label", layout.getLineRight(line) <= layout.size.width + 1f)
            assertFalse("Ellipsized label: $label", layout.isLineEllipsized(line))
        }
        assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1))
    }
}
