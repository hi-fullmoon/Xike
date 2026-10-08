package com.xike.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NativePickerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nativeDatePickerKeepsAllDaysAndActionsInsideTheWindow() {
        lateinit var pickerContext: Context
        composeRule.setContent {
            val languageContext = LocalContext.current.forLanguage(AppLanguage.CHINESE)
            CompositionLocalProvider(LocalContext provides languageContext) {
                XikeTheme(AppTheme.OCEAN, AppStyle.PAPER) {
                    val context = LocalRecordedAtPickerContext.current
                    SideEffect { pickerContext = context }
                }
            }
        }
        lateinit var dialog: DatePickerDialog
        composeRule.runOnIdle {
            dialog = showRecordedAtPicker(pickerContext, null) {}
        }
        try {
            composeRule.waitUntil(5_000) {
                dialog.window?.decorView?.isLaidOut == true
            }
            composeRule.runOnIdle {
                val screenWidth = pickerContext.resources.displayMetrics.widthPixels
                assertViewInsideScreen(dialog.datePicker, screenWidth)
                assertViewInsideScreen(dialog.getButton(AlertDialog.BUTTON_POSITIVE), screenWidth)
                assertViewInsideScreen(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), screenWidth)
            }
            val dayCount = YearMonth.of(dialog.datePicker.year, dialog.datePicker.month + 1).lengthOfMonth()
            var nodes = emptyList<AccessibilityNodeInfo>()
            composeRule.waitUntil(10_000) {
                val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                nodes = root?.let(::descendants).orEmpty()
                nodes.any { it.className == "android.widget.DatePicker" }
            }
            val dayWidths = nodes.mapNotNull { node ->
                if (!node.isVisibleToUser) return@mapNotNull null
                val day = node.text?.toString()?.toIntOrNull() ?: return@mapNotNull null
                if (day !in 1..dayCount) return@mapNotNull null
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.width().takeIf { it > 0 }
            }
            assertEquals("Calendar days are missing", dayCount, dayWidths.size)
            assertTrue("A calendar column is clipped", dayWidths.max() - dayWidths.min() <= 1)
        } finally {
            composeRule.runOnIdle { dialog.dismiss() }
        }
    }

    private fun descendants(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            nodes.add(node)
            repeat(node.childCount) { index -> node.getChild(index)?.let(queue::add) }
        }
        return nodes
    }

    private fun assertViewInsideScreen(view: View, screenWidth: Int) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        assertTrue("Picker control extends past the left edge", location[0] >= 0)
        assertTrue("Picker control extends past the right edge", location[0] + view.width <= screenWidth)
        val visible = Rect()
        assertTrue("Picker control is not visible", view.getGlobalVisibleRect(visible))
        assertEquals("Picker control is clipped", view.width, visible.width())
    }
}
