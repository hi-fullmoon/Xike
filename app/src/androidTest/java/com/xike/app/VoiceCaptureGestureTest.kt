package com.xike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import android.view.InputDevice
import android.os.SystemClock
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import java.io.File
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class VoiceCaptureGestureTest {
    private val style = AppStyle.entries.firstOrNull {
        it.name == InstrumentationRegistry.getArguments().getString("style")
    } ?: AppStyle.PAPER

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun selectLanguage() {
        val language = if (InstrumentationRegistry.getArguments().getString("language") == "en") {
            AppLanguage.ENGLISH
        } else AppLanguage.CHINESE
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { AppLocale.select(instrumentation.targetContext, language) }
    }

    @Test
    fun openingAndTappingDoesNotStartRecording() {
        var recordings = 0
        var closes = 0
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                VoiceCaptureSheet(
                    enabled = true,
                    onRecorded = { file, _ -> recordings++; file.delete() },
                    onClosed = { closes++ },
                )
            }
        }
        composeRule.onNodeWithText(tr("按住说话", "Hold to record")).assertIsDisplayed()
            .performTouchInput { click() }
        composeRule.onNodeWithText(tr("按住下方按钮开始录音", "Hold the button below to record")).assertIsDisplayed()
        composeRule.onNodeWithText(localizedText("正在录音")).assertDoesNotExist()
        composeRule.onNodeWithText("0:00").assertDoesNotExist()
        captureScreenshot("voice-capture.png")
        assertCompleteLabel(tr("按住下方按钮开始录音", "Hold the button below to record"))
        assertCompleteLabel(tr("最长 5 分钟", "Up to 5 minutes"))
        assertCompleteLabel(tr("按住说话", "Hold to record"))
        composeRule.runOnIdle {
            assertEquals(0, recordings)
            assertEquals(0, closes)
        }
    }

    @Test
    fun holdAndReleaseProducesARealRecording() {
        var recording: File? = null
        var duration = 0L
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                VoiceCaptureSheet(true, { file, millis -> recording = file; duration = millis }, {})
            }
        }
        try {
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            val buttonDescription = tr("长按录音，上滑取消，松手完成", "Hold to record, slide up to cancel, release to finish")
            val buttonBounds = composeRule.onNodeWithContentDescription(buttonDescription)
                .fetchSemanticsNode().boundsInRoot
            composeRule.onNodeWithText(tr("按住说话", "Hold to record"))
                .performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(700)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText(localizedText("正在录音")).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(buttonBounds, composeRule.onNodeWithContentDescription(buttonDescription)
                .fetchSemanticsNode().boundsInRoot)
            composeRule.onNodeWithContentDescription(buttonDescription)
                .performTouchInput { moveTo(Offset(center.x, -300f)) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithText(tr("松开取消", "Release to cancel"))[0].assertIsDisplayed()
            composeRule.onNodeWithContentDescription(buttonDescription)
                .performTouchInput { moveTo(center) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithText(tr("松手完成", "Release to finish")).assertIsDisplayed()
            Thread.sleep(800)
            assertCompleteLabel(tr("松手完成，上滑取消", "Release to finish · Slide up to cancel"))
            assertCompleteLabel(tr("松手完成", "Release to finish"))
            captureScreenshot("voice-recording.png")
            composeRule.onNodeWithText(tr("松手完成", "Release to finish"))
                .performTouchInput { up() }
            composeRule.runOnIdle {
                assertTrue(requireNotNull(recording).length() > 0L)
                assertTrue(duration >= 500L)
            }
        } finally {
            recording?.delete()
        }
    }

    @Test
    fun slidingUpAndReleasingDiscardsRecording() {
        var recordings = 0
        var closes = 0
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                VoiceCaptureSheet(true, { file, _ -> recordings++; file.delete() }, { closes++ })
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText(tr("按住说话", "Hold to record"))
            .performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(localizedText("正在录音")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(tr("松手完成", "Release to finish"))
            .performTouchInput { moveTo(Offset(center.x, -300f)) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onAllNodesWithText(tr("松开取消", "Release to cancel"))[0].assertIsDisplayed()
        composeRule.waitForIdle()
        assertCompleteLabel(tr("松开取消", "Release to cancel"))
        captureScreenshot("voice-cancel.png")
        composeRule.onAllNodesWithText(tr("松开取消", "Release to cancel"))[0].performTouchInput { up() }
        composeRule.runOnIdle {
            assertEquals(0, recordings)
            assertEquals(1, closes)
        }
    }

    @Test
    fun accessibilityLongPressCanStartAndClickCanFinishRecording() {
        var recording: File? = null
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                VoiceCaptureSheet(true, { file, _ -> recording = file }, {})
            }
        }
        try {
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            val button = composeRule.onNodeWithContentDescription(tr("长按录音，上滑取消，松手完成", "Hold to record, slide up to cancel, release to finish"))
            assertEquals(Role.Button, button.fetchSemanticsNode().config[SemanticsProperties.Role])
            button.performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithText(localizedText("正在录音")).assertIsDisplayed()
            Thread.sleep(800)
            button.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            composeRule.runOnIdle { assertTrue(requireNotNull(recording).length() > 0L) }
        } finally {
            recording?.delete()
        }
    }

    @Test
    fun accessibilityClickCanStartAndCustomActionCanCancelRecording() {
        var recordings = 0
        var closes = 0
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                VoiceCaptureSheet(true, { file, _ -> recordings++; file.delete() }, { closes++ })
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val button = composeRule.onNodeWithContentDescription(tr("长按录音，上滑取消，松手完成", "Hold to record, slide up to cancel, release to finish"))
        button.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(localizedText("正在录音")).assertIsDisplayed()
        val cancel = button.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == localizedText("取消") }
        composeRule.runOnIdle { assertTrue(cancel.action()) }
        composeRule.runOnIdle { assertEquals(0, recordings); assertEquals(1, closes) }
    }

    @Test
    fun nativeAccessibilityTreeExposesRecordingButton() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                VoiceCaptureSheet(true, { file, _ -> file.delete() }, {})
            }
        }
        composeRule.waitForIdle()
        val description = tr("长按录音，上滑取消，松手完成", "Hold to record, slide up to cancel, release to finish")
        fun containsButton(node: AccessibilityNodeInfo): Boolean {
            val children = (0 until node.childCount).mapNotNull(node::getChild)
            try {
                // Compose exposes a merged control's description and role as native children.
                val hasDescription = node.contentDescription?.toString() == description ||
                    children.any { it.contentDescription?.toString() == description }
                val hasButtonRole = node.className?.toString() == "android.widget.Button" ||
                    children.any { it.className?.toString() == "android.widget.Button" }
                return (node.isClickable && node.isLongClickable && hasDescription && hasButtonRole) ||
                    children.any(::containsButton)
            } finally {
                children.forEach { it.recycle() }
            }
        }
        composeRule.waitUntil(5_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.let { root ->
                try { containsButton(root) } finally { root.recycle() }
            } ?: false
        }
    }

    @Test
    fun dismissingSheetBeforeReleaseDoesNotSaveRecording() {
        verifyExitBeforeRelease {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
    }

    @Test
    fun pausingActivityBeforeReleaseDoesNotSaveRecording() {
        verifyExitBeforeRelease {
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        }
    }

    private fun verifyExitBeforeRelease(exit: () -> Unit) {
        var recordings = 0
        var closes = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalFiles = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("xike-recording-") }.toSet()
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                VoiceCaptureSheet(true, { file, _ -> recordings++; file.delete() }, { closes++ })
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val button = composeRule.onNodeWithContentDescription(tr("长按录音，上滑取消，松手完成", "Hold to record, slide up to cancel, release to finish"))
        val center = button.fetchSemanticsNode().boundsInRoot.center
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun injectTouch(action: Int) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, center.x, center.y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        injectTouch(MotionEvent.ACTION_DOWN)
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(localizedText("正在录音")).fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(800)
        exit()
        injectTouch(MotionEvent.ACTION_UP)
        instrumentation.runOnMainSync {
            assertEquals(0, recordings)
            assertEquals(1, closes)
            assertEquals(originalFiles, context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("xike-recording-") }.toSet())
        }
    }

    private fun assertCompleteLabel(label: String) {
        val nodes = composeRule.onAllNodesWithText(label, useUnmergedTree = true)
        assertTrue(nodes.fetchSemanticsNodes().isNotEmpty())
        nodes.fetchSemanticsNodes().indices.forEach { index ->
            val node = nodes[index].assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            val layout = layouts.single()
            assertFalse("Vertically clipped: $label", layout.didOverflowHeight)
            // Paragraph metrics are fractional, while layout bounds use integer pixels.
            repeat(layout.lineCount) { line ->
                assertTrue("Horizontally clipped: $label, left=${layout.getLineLeft(line)}, right=${layout.getLineRight(line)}, width=${layout.size.width}, paragraph=${layout.multiParagraph.width}", layout.getLineLeft(line) >= -1f &&
                    layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse("Ellipsized: $label", layout.isLineEllipsized(line))
            }
            assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1))
            val full = node.getUnclippedBoundsInRoot()
            val visible = node.getBoundsInRoot()
            assertTrue("Clipped by parent: $label", full.top >= visible.top - 0.5.dp && full.bottom <= visible.bottom + 0.5.dp)
        }
    }

    private fun captureScreenshot(name: String) {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), name)
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
    }
}
