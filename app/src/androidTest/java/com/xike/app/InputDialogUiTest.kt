package com.xike.app

import android.view.KeyEvent
import android.view.WindowInsets
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

@SdkSuppress(minSdkVersion = 30)
class InputDialogUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun exportActionsRemainVisibleAboveTheKeyboardWithoutEditingJournalData() {
        rule.waitUntil(15_000) {
            !ViewModelProvider(rule.activity)[JournalViewModel::class.java].isLoading
        }
        val viewModel = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        val originalDraft = viewModel.draft
        rule.onNodeWithTag("navigation-settings").performClick()
        rule.onNodeWithText(localizedText("导出日记备份")).performScrollTo().performClick()
        rule.onAllNodes(hasSetTextAction()).onFirst().performScrollTo().performClick()
        rule.waitUntil(5_000) {
            rule.activity.window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom?.let { it > 0 } == true
        }
        rule.onAllNodes(hasSetTextAction()).onFirst().performScrollTo().assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        rule.waitForIdle()
        val config = rule.activity.resources.configuration
        val folder = File(rule.activity.getExternalFilesDir(null), "dialog-audit").apply { mkdirs() }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(folder, "backup-keyboard-${config.screenWidthDp}-${config.fontScale}-${config.uiMode}.png")
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
        assertActionFullyVisible(localizedText("取消"))
        assertActionFullyVisible(localizedText("选择保存位置"))
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText(localizedText("取消")).performClick()
        rule.onNodeWithText(localizedText("取消")).assertDoesNotExist()
        assertEquals(originalDraft, viewModel.draft)
    }

    private fun assertActionFullyVisible(label: String) {
        val action = rule.onNodeWithText(label).assertIsDisplayed()
        val visible = action.getBoundsInRoot()
        val full = action.getUnclippedBoundsInRoot()
        assertEquals("Clipped action width: $label", (full.right - full.left).value, (visible.right - visible.left).value, 1f)
        assertEquals("Clipped action height: $label", (full.bottom - full.top).value, (visible.bottom - visible.top).value, 1f)
    }
}
