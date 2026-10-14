package com.xike.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.feature.settings.ReminderScheduleDialog
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import java.time.DayOfWeek
import java.time.format.TextStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Run on a dedicated device; width, font scale and night mode use real device configuration. */
class ReminderScheduleLayoutUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun weekdayLabelsAndActionsRemainReadableAndReachable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val language = AppLanguage.valueOf(
            InstrumentationRegistry.getArguments().getString("reminderUiLanguage") ?: "ENGLISH",
        )
        val originalLanguage = LanguagePreferences(context).language
        val theme = mutableStateOf(AppTheme.PINE)
        val style = mutableStateOf(AppStyle.BREATHE)
        try {
            rule.runOnIdle { AppLocale.select(context, language) }
            rule.setContent {
                XikeTheme(theme.value, style.value) {
                    ReminderScheduleDialog(HabitPreferences(context).reminder, onConfirm = {}, onDismiss = {})
                }
            }
            for (palette in AppTheme.entries) {
                for (appearance in AppStyle.entries) {
                    rule.runOnIdle {
                        theme.value = palette
                        style.value = appearance
                    }
                    val timeLayouts = mutableListOf<TextLayoutResult>()
                    rule.onNodeWithText(HabitPreferences(context).reminder.timeLabel)
                        .performScrollTo().assertIsDisplayed()
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(timeLayouts) }
                    assertTrue(timeLayouts.isNotEmpty())
                    timeLayouts.forEach { layout ->
                        assertEquals("$palette $appearance time row: ${layout.layoutInput.text}", 1, layout.lineCount)
                        assertFalse(layout.didOverflowHeight)
                    }
                    rule.onNodeWithText(localizedText("修改")).assertIsDisplayed()
                    if (palette == AppTheme.VIOLET) saveScreenshot(context, "$language-$appearance-top")
                    for (day in DayOfWeek.entries) {
                        val node = rule.onNodeWithContentDescription(day.getDisplayName(TextStyle.FULL, AppLocale.locale))
                        node.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        assertTrue(layouts.isNotEmpty())
                        layouts.forEach { layout ->
                            val clipped = (0 until layout.lineCount).any { line ->
                                layout.getLineLeft(line) < -1f || layout.getLineRight(line) > layout.size.width + 1f ||
                                    layout.getLineBottom(line) > layout.size.height + 1f || layout.isLineEllipsized(line)
                            }
                            if (clipped) {
                                val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                                try {
                                    File(context.getExternalFilesDir(null), "reminder-overflow.png")
                                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                            assertFalse("$palette $appearance $day: ${layout.size}, lines=${layout.lineCount}, left=${layout.getLineLeft(0)}, right=${layout.getLineRight(0)}, bottom=${layout.getLineBottom(0)}", clipped)
                        }
                    }
                    rule.onNodeWithText(localizedText("夜间勿扰")).performScrollTo().assertIsDisplayed()
                    rule.onNodeWithText(localizedText("保存")).assertIsDisplayed()
                    rule.onNodeWithText(localizedText("取消")).assertIsDisplayed()
                    if (palette == AppTheme.VIOLET) {
                        saveScreenshot(context, "$language-$appearance-bottom")
                    }
                }
            }
            val initial = HabitPreferences(context).reminder.weekdays
            for (day in initial) {
                rule.onNodeWithContentDescription(day.getDisplayName(TextStyle.FULL, AppLocale.locale))
                    .performScrollTo().assertIsOn().performClick().assertIsOff()
            }
            rule.onNodeWithText(localizedText("至少选择一天")).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(localizedText("保存")).assertIsNotEnabled()
            rule.onNodeWithContentDescription(DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, AppLocale.locale))
                .performScrollTo().performClick().assertIsOn()
            rule.onNodeWithText(localizedText("保存")).assertIsEnabled()
        } finally {
            rule.runOnIdle { AppLocale.select(context, originalLanguage) }
        }
    }

    private fun saveScreenshot(context: Context, name: String) {
        val config = context.resources.configuration
        val folder = File(context.getExternalFilesDir(null), "reminder-ui-audit").apply { mkdirs() }
        rule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(200, 5_000)
        val bitmap = requireNotNull(automation.takeScreenshot())
        try {
            File(folder, "$name-${config.screenWidthDp}-${config.fontScale}-${config.uiMode}.png")
                .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }
}
