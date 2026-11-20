package com.xike.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Uses the real settings page and preferences; run only on an independent device. */
class SettingsAppearanceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyThemeAndStyleCanBeSelectedAndSurvivesRecreation() {
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) { !model.isLoading }
        rule.onNodeWithTag("navigation-settings").performClick()
        val requestedLanguage = InstrumentationRegistry.getArguments().getString("language")
        val language = if (requestedLanguage == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
        rule.onNodeWithText(localizedText("应用语言")).performScrollTo().performClick()
        rule
            .onNode(
                hasText(language.nativeName) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            )
            .performClick()
        rule.waitForIdle()

        AppStyle.entries.forEach { style ->
            rule.onNodeWithText(style.title).performScrollTo().performClick()
            AppTheme.entries.forEach { theme ->
                rule.onNodeWithText(theme.title).performScrollTo().performClick()
                rule.waitForIdle()
                assertEquals(style, model.selectedStyle)
                assertEquals(theme, model.selectedTheme)
                assertCompleteText(theme.title)
                assertCompleteText(theme.subtitle)
                rule.onNodeWithTag("navigation-settings").assertIsDisplayed()
                val night =
                    rule.activity.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                val file =
                    File(
                        rule.activity.getExternalFilesDir(null),
                        "settings-${language.name}-$night-${style.name}-${theme.name}.png",
                    )
                val screenshot =
                    requireNotNull(
                        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    )
                try {
                    file.outputStream().use {
                        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally {
                    screenshot.recycle()
                }
            }
        }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("navigation-settings").assertIsDisplayed()
        val recreated = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) { !recreated.isLoading }
        assertEquals(AppStyle.entries.last(), recreated.selectedStyle)
        assertEquals(AppTheme.entries.last(), recreated.selectedTheme)
        assertEquals(language, LanguagePreferences(rule.activity).language)
    }

    private fun assertCompleteText(text: String) {
        val node =
            rule
                .onNodeWithText(text, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(
            requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(layouts)
        )
        val layout = layouts.single()
        assertFalse(text, layout.didOverflowHeight)
        repeat(layout.lineCount) { line ->
            assertFalse(text, layout.isLineEllipsized(line))
            // Paragraph metrics are fractional; Compose rounds layout bounds to pixels.
            assertTrue(
                "$text: right=${layout.getLineRight(line)}, width=${layout.size.width}",
                layout.getLineRight(line) <= layout.size.width + 1f,
            )
        }
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
    }
}
