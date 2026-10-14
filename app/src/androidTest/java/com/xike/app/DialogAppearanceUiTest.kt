package com.xike.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.feature.settings.BackupExportDialog
import com.xike.app.ui.components.showRecordedAtPicker
import com.xike.app.ui.components.showRecordedAtTimePicker
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.LocalRecordedAtPickerContext
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DialogAppearanceUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun exportActionsRemainReachableAcrossThemesAndStyles() {
        val selectedTheme = mutableStateOf(AppTheme.PINE)
        val selectedStyle = mutableStateOf(AppStyle.BREATHE)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    XikeTheme(selectedTheme.value, selectedStyle.value) {
                        BackupExportDialog(onDismiss = {}, onConfirm = {})
                    }
                }
            }
            for (theme in AppTheme.entries) {
                for (style in AppStyle.entries) {
                    rule.runOnIdle {
                        selectedTheme.value = theme
                        selectedStyle.value = style
                    }
                    rule.onNodeWithText(localizedText("备份密码（至少 8 位）")).performScrollTo().assertIsDisplayed()
                    rule.onNodeWithText(localizedText("取消")).assertIsDisplayed()
                    rule.onNodeWithText(localizedText("选择保存位置")).assertIsDisplayed()
                    saveScreenshot("export-$theme-$style")
                }
            }
        }
    }

    @Test
    fun nativePickerActionsRemainInsideWindowAcrossThemesAndStyles() {
        val selectedTheme = mutableStateOf(AppTheme.PINE)
        val selectedStyle = mutableStateOf(AppStyle.BREATHE)
        lateinit var pickerContext: Context
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    XikeTheme(selectedTheme.value, selectedStyle.value) {
                        val themedContext = LocalRecordedAtPickerContext.current
                        SideEffect { pickerContext = themedContext }
                    }
                }
            }
            for (theme in AppTheme.entries) {
                for (style in AppStyle.entries) {
                    rule.runOnIdle {
                        selectedTheme.value = theme
                        selectedStyle.value = style
                    }
                    lateinit var dateDialog: android.app.DatePickerDialog
                    rule.runOnIdle { dateDialog = showRecordedAtPicker(pickerContext, null) {} }
                    try {
                        verifyNativeActions(dateDialog)
                        saveScreenshot("date-$theme-$style")
                    } finally {
                        rule.runOnIdle { dateDialog.dismiss() }
                    }
                    val now = LocalDateTime.now()
                    lateinit var timeDialog: android.app.TimePickerDialog
                    rule.runOnIdle {
                        timeDialog = showRecordedAtTimePicker(pickerContext, now.toLocalDate(), now.hour, now.minute) {}
                    }
                    try {
                        verifyNativeActions(timeDialog)
                        saveScreenshot("time-$theme-$style")
                    } finally {
                        rule.runOnIdle { timeDialog.dismiss() }
                    }
                }
            }
        }
    }

    private fun verifyNativeActions(dialog: AlertDialog) {
        rule.waitUntil(5_000) { dialog.window?.decorView?.isLaidOut == true }
        rule.runOnIdle {
            val windowBounds = Rect()
            assertTrue(requireNotNull(dialog.window).decorView.getGlobalVisibleRect(windowBounds))
            for (action in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) {
                val button = dialog.getButton(action)
                val visible = Rect()
                assertTrue(button.getGlobalVisibleRect(visible))
                assertEquals(button.width, visible.width())
                assertEquals(button.height, visible.height())
                assertTrue(windowBounds.contains(visible))
                assertEquals(View.VISIBLE, button.visibility)
            }
        }
    }

    private fun saveScreenshot(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        val config = context.resources.configuration
        val directory = File(context.getExternalFilesDir(null), "dialog-appearance").apply { mkdirs() }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(directory, "$name-${config.screenWidthDp}-${config.fontScale}-${config.uiMode}.png")
                .outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            screenshot.recycle()
        }
    }
}
