package com.xike.app

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Run on an isolated device: launches the real app and its local encrypted store. */
class WelcomeLaunchUiTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test
    fun launchShowsWelcomeButRecreationAndForegroundReturnDoNotReplayIt() {
        composeRule.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.mainClock.advanceTimeBy(320)
            composeRule.onNodeWithTag("welcome-screen").assertIsDisplayed()
            composeRule.onNodeWithTag("navigation-home").assertDoesNotExist()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "welcome-launch.png").outputStream().use {
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .takeScreenshot()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            composeRule.mainClock.advanceTimeBy(1500)
            composeRule.onNodeWithTag("welcome-screen").assertDoesNotExist()
            composeRule.onNodeWithTag("navigation-home").assertIsDisplayed()
            scenario.recreate()
            composeRule.onNodeWithTag("welcome-screen").assertDoesNotExist()
            composeRule.onNodeWithTag("navigation-home").assertIsDisplayed()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.onNodeWithTag("welcome-screen").assertDoesNotExist()
            composeRule.onNodeWithTag("navigation-home").assertIsDisplayed()
        }
    }
}
