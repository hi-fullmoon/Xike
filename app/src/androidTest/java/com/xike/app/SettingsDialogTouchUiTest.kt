package com.xike.app

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.feature.lock.AppLockPreferences
import com.xike.app.feature.lock.AppLockTimeout
import com.xike.app.feature.settings.AppLanguageDialog
import com.xike.app.feature.settings.AppLockTimeoutDialog
import com.xike.app.feature.settings.DailyPromptStyleDialog
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Renders production dialogs and writes real preferences on an independent device. */
class SettingsDialogTouchUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun optionRowsKeepTheirFullTouchAreaAndSelectOnlyTheTappedOption() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedDialogs") == "true")
        val languages = LanguagePreferences(context)
        val locks = AppLockPreferences(context)
        val habits = HabitPreferences(context)
        val originalLanguage = languages.language
        val originalTimeout = locks.timeout
        val originalPrompt = habits.dailyPrompt
        try {
            for (language in listOf(AppLanguage.CHINESE, AppLanguage.ENGLISH)) {
                languages.language = language
                InstrumentationRegistry.getInstrumentation().runOnMainSync { AppLocale.initialize(context) }
                verifyOptions(
                    "language", AppLanguage.entries, { it.displayName },
                    { languages.language }, { languages.language = it },
                ) { selected, onSelected, onDismiss ->
                    AppLanguageDialog(selected, onSelected, onDismiss)
                }
                languages.language = language
                verifyOptions(
                    "timeout", AppLockTimeout.entries, { it.label },
                    { locks.timeout }, { locks.timeout = it },
                ) { selected, onSelected, onDismiss ->
                    AppLockTimeoutDialog(selected, onSelected, onDismiss)
                }
                verifyOptions(
                    "prompt", DailyPromptStyle.entries, { it.label },
                    { habits.dailyPrompt.style }, { habits.dailyPrompt = habits.dailyPrompt.copy(style = it) },
                ) { selected, onSelected, onDismiss ->
                    DailyPromptStyleDialog(selected, onSelected, onDismiss)
                }
            }
        } finally {
            locks.timeout = originalTimeout
            habits.dailyPrompt = originalPrompt
            languages.language = originalLanguage
            InstrumentationRegistry.getInstrumentation().runOnMainSync { AppLocale.initialize(context) }
        }
    }

    private fun <T> verifyOptions(
        name: String,
        options: List<T>,
        label: (T) -> String,
        readSelected: () -> T,
        writeSelected: (T) -> Unit,
        dialog: @Composable (T, (T) -> Unit, () -> Unit) -> Unit,
    ) {
        val style = mutableStateOf(AppStyle.BREATHE)
        val selected = mutableStateOf(readSelected())
        val showing = mutableStateOf(true)
        val appearance = AppearancePreferences(context).current()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    XikeTheme(appearance.theme, style.value) {
                        if (showing.value) {
                            dialog(selected.value, {
                                writeSelected(it)
                                selected.value = readSelected()
                                showing.value = false
                            }, { showing.value = false })
                        }
                    }
                }
            }
            for (appStyle in AppStyle.entries) {
                rule.runOnIdle { style.value = appStyle }
                for (option in options) {
                    for (bottomEdge in listOf(false, true)) {
                        // Select a different stored option first so an ignored or wrong tap cannot pass.
                        rule.runOnIdle {
                            writeSelected(options.first { it != option })
                            selected.value = readSelected()
                            showing.value = true
                        }
                        val optionLabel = label(option)
                        val row = rule.onNode(hasText(optionLabel) and radioRole)
                            .performScrollTo().assertIsDisplayed()
                        val full = row.getUnclippedBoundsInRoot()
                        val visible = row.getBoundsInRoot()
                        val fullHeight = (full.bottom - full.top).value
                        val fullWidth = (full.right - full.left).value
                        val pixelBounds = row.fetchSemanticsNode().boundsInRoot
                        // Compare physical pixels to avoid cancellation when subtracting converted Dp coordinates.
                        val minimumPixels = (48f * context.resources.displayMetrics.density).roundToInt()
                        assertTrue("$name/$appStyle/$optionLabel: touch height $fullHeight", pixelBounds.height.roundToInt() >= minimumPixels)
                        assertTrue("$name/$appStyle/$optionLabel: touch width $fullWidth", pixelBounds.width.roundToInt() >= minimumPixels)
                        assertEquals(fullHeight, (visible.bottom - visible.top).value, 1f)
                        assertEquals(fullWidth, (visible.right - visible.left).value, 1f)
                        assertVisibleRowsDoNotOverlap()
                        if (!bottomEdge) saveScreenshot("$name-${AppLocale.language}-$appStyle-$option")
                        row.performTouchInput {
                            click(Offset(width - 2f, if (bottomEdge) height - 2f else 2f))
                        }
                        rule.runOnIdle { assertEquals(option, readSelected()) }
                        rule.onNode(hasText(optionLabel) and radioRole).assertDoesNotExist()
                        rule.runOnIdle { showing.value = true }
                        rule.onNode(hasText(optionLabel) and radioRole).performScrollTo().assertIsSelected()
                    }
                }
                rule.onNodeWithText(localizedText("取消")).performClick()
                rule.runOnIdle {
                    assertEquals(false, showing.value)
                    showing.value = true
                }
            }
        }
    }

    private fun assertVisibleRowsDoNotOverlap() {
        val bounds = rule.onAllNodes(radioRole).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.width > 0 && it.height > 0 }.sortedBy { it.top }
        bounds.zipWithNext().forEach { (above, below) ->
            assertTrue("Adjacent option touch areas overlap", above.bottom <= below.top + 1f)
        }
    }

    private fun saveScreenshot(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        val config = context.resources.configuration
        val directory = File(context.getExternalFilesDir(null), "settings-dialog-touch").apply { mkdirs() }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(directory, "$name-${config.screenWidthDp}-${config.fontScale}-${config.uiMode}.png")
                .outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            screenshot.recycle()
        }
    }

    private val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
}
