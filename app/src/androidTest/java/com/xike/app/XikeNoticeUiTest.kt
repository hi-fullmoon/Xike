package com.xike.app

import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class XikeNoticeUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun showHost() {
        rule.setContent {
            XikeTheme(AppTheme.PINE) {
                Box(Modifier.fillMaxSize())
            }
        }
    }

    @Test
    fun noticeAppearsAtTheTopAndCanBeDismissedWithoutTakingWindowFocus() {
        showHost()
        val text = localizedText("记录时间不能晚于现在")
        rule.runOnIdle {
            XikeNotice.makeText(rule.activity, text, XikeNotice.LENGTH_LONG).show()
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithText(text).assertIsDisplayed().getBoundsInRoot()
        val screenHeight = rule.activity.resources.configuration.screenHeightDp
        assertTrue("Feedback should stay in the top half of the screen", bounds.bottom.value < screenHeight / 2f)
        assertTrue("Feedback must not steal activity focus", rule.activity.hasWindowFocus())
        rule.onNodeWithContentDescription(localizedText("关闭")).performClick()
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun newNoticeReplacesThePreviousNoticeAndExpires() {
        showHost()
        val first = localizedText("记录时间不能晚于现在")
        val second = localizedText("请先完成或取消录音")
        rule.runOnIdle {
            XikeNotice.makeText(rule.activity, first, XikeNotice.LENGTH_LONG).show()
        }
        rule.onNodeWithText(first).assertIsDisplayed()
        rule.runOnIdle {
            XikeNotice.makeText(rule.activity, second, XikeNotice.LENGTH_SHORT).show()
        }
        rule.onNodeWithText(first).assertDoesNotExist()
        rule.onNodeWithText(second).assertIsDisplayed()
        rule.waitUntil(10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText(second)).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText(first).assertDoesNotExist()
    }

    @Test
    fun longLocalizedMessagesAndDismissButtonRemainVisibleInEveryStyle() {
        val style = mutableStateOf(AppStyle.BREATHE)
        rule.setContent {
            XikeTheme(AppTheme.PINE, style.value) {
                Box(Modifier.fillMaxSize())
            }
        }
        for (selectedStyle in AppStyle.entries) {
            for (language in AppLanguage.entries) {
                val context = rule.activity.forLanguage(language)
                val text = context.getString(R.string.text_microphone_access_is_unavailable_you_can_still)
                rule.runOnIdle {
                    style.value = selectedStyle
                    XikeNotice.makeText(context, text, XikeNotice.LENGTH_LONG).show()
                }
                val node = rule.onNodeWithText(text).assertIsDisplayed()
                val visible = node.getBoundsInRoot()
                val full = node.getUnclippedBoundsInRoot()
                assertEquals((full.right - full.left).value, (visible.right - visible.left).value, 1f)
                assertEquals((full.bottom - full.top).value, (visible.bottom - visible.top).value, 1f)
                rule.onNodeWithContentDescription(localizedText("关闭")).assertIsDisplayed()
                val config = rule.activity.resources.configuration
                val folder = File(rule.activity.getExternalFilesDir(null), "notice-audit").apply { mkdirs() }
                val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                try {
                    File(folder, "${selectedStyle.name}-${language.name}-${config.screenWidthDp}-${config.fontScale}-${config.uiMode}.png")
                        .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    screenshot.recycle()
                }
                rule.onNodeWithContentDescription(localizedText("关闭")).performClick()
                rule.onNodeWithText(text).assertDoesNotExist()
            }
        }
    }
}
