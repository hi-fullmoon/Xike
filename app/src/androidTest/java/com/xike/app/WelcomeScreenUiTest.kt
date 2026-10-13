package com.xike.app

import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.XikeTheme


import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class WelcomeScreenUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun welcomeFitsAndFinishesOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val visible = mutableStateOf(true)
        var completions = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, AppStyle.BREATHE) {
                if (visible.value) {
                    WelcomeScreen {
                        completions++
                        visible.value = false
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(320)
        composeRule.waitForIdle()
        val screen = composeRule.onNodeWithTag("welcome-screen")
        screen.assertIsDisplayed()
        val screenBounds = screen.getUnclippedBoundsInRoot()
        val message = composeRule.onNodeWithText(context.getString(R.string.welcome_message))
        val footer = composeRule.onNodeWithText(context.getString(R.string.welcome_footer))
        message.assertIsDisplayed()
        footer.assertIsDisplayed()
        val messageBounds = message.getUnclippedBoundsInRoot()
        val footerBounds = footer.getUnclippedBoundsInRoot()
        assertTrue(messageBounds.left >= screenBounds.left && messageBounds.right <= screenBounds.right)
        assertTrue(messageBounds.bottom < footerBounds.top)
        assertTrue(footerBounds.bottom <= screenBounds.bottom)
        val destination = File(context.getExternalFilesDir(null), "welcome-screen.png")
        destination.outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.mainClock.advanceTimeBy(1500)
        composeRule.waitForIdle()
        screen.assertDoesNotExist()
        assertEquals(1, completions)
    }
}
