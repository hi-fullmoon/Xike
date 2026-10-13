package com.xike.app

import com.xike.app.data.draft.JournalDraftStore

import com.xike.app.feature.lock.AppLockSessionState

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

/** Run on a dedicated device with an empty draft; input goes through the real app. */
class DraftReturnUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun discardAppearsForNewInputAndStaysVisibleAfterReturning() {
        val draftActions = localizedText("放弃草稿")
        val noteHint = localizedText("这一刻，有什么想留下？")
        lateinit var model: JournalViewModel
        composeRule.activityRule.scenario.onActivity {
            model = ViewModelProvider(it)[JournalViewModel::class.java]
        }
        composeRule.waitUntil(15_000) { !model.isLoading }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(noteHint).fetchSemanticsNodes().isNotEmpty()
        }
        check(JournalDraftStore(composeRule.activity).load().isEmpty) {
            "Use a dedicated device with an empty draft."
        }
        composeRule.onNodeWithText(draftActions).assertDoesNotExist()
        composeRule.onNodeWithText(noteHint).performScrollTo().performTextInput("Draft return check")
        composeRule.onNodeWithText(localizedText("完成")).performScrollTo().performClick()
        composeRule.onNodeWithText(draftActions).performScrollTo().assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText(draftActions).assertIsDisplayed()
        lateinit var activity: MainActivity
        lateinit var lockSession: AppLockSessionState
        composeRule.activityRule.scenario.onActivity {
            activity = it
            check(model === ViewModelProvider(it)[JournalViewModel::class.java])
            lockSession = ViewModelProvider(it)[AppLockSessionState::class.java]
        }

        // A real system picker round trip keeps the new draft and its discard action.
        composeRule.onNodeWithContentDescription(tr("添加内容", "Add content")).performScrollTo().performClick()
        composeRule.onNodeWithText(localizedText("照片")).performClick()
        composeRule.onNodeWithText(localizedText("从相册选择")).performClick()
        SystemClock.sleep(1_500)
        repeat(3) {
            if (activity.lifecycle.currentState != Lifecycle.State.RESUMED) {
                shell("input keyevent KEYCODE_BACK")
                SystemClock.sleep(500)
            }
        }
        check(activity.lifecycle.currentState == Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(draftActions).performScrollTo().assertIsDisplayed()

        check(!lockSession.systemActivityInProgress) { "System picker has not completed" }
        check(!model.draft.isEmpty) { "Input was lost before backgrounding" }

        shell("input keyevent KEYCODE_HOME")
        awaitState(activity, Lifecycle.State.CREATED)
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.xike.app/.MainActivity")
        awaitState(activity, Lifecycle.State.RESUMED)
        check(model.hasResumedDraft) { "Draft was not marked resumed after foregrounding" }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(draftActions).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(draftActions).performScrollTo().assertIsDisplayed()
        val todayLabel = java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern(tr("M月d日 · E", "MMM d · E"), AppLocale.locale),
        )
        composeRule.onNodeWithText(todayLabel).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(draftActions).assertIsDisplayed()
        composeRule.onNodeWithText(draftActions).assertHeightIsAtLeast(48.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(draftActions).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        check(layouts.isNotEmpty() && layouts.all { layout ->
            (0 until layout.lineCount).all { line ->
                !layout.isLineEllipsized(line) && layout.getLineLeft(line) >= 0 &&
                    layout.getLineRight(line) <= layout.size.width &&
                    layout.getLineBottom(line) <= layout.size.height
            }
        }) {
            "Discard text extends beyond its drawing bounds"
        }
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        checkNotNull(screenshot)
        java.io.File(activity.getExternalFilesDir(null), "draft-header.png").outputStream().use {
            check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        composeRule.onNodeWithText("Draft return check").performScrollTo().performTextInput(" continued")
        composeRule.onNodeWithText(localizedText("完成")).performScrollTo().performClick()
        composeRule.onNodeWithText(draftActions).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText(localizedText("放弃这份草稿？")).assertIsDisplayed()
        composeRule.onNodeWithText(localizedText("继续保留")).performClick()
        composeRule.onNodeWithText(draftActions).performClick()
        composeRule.onNode(hasText(draftActions) and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNodeWithText(draftActions).assertDoesNotExist()
        check(JournalDraftStore(composeRule.activity).load().isEmpty)
        composeRule.onNodeWithText(noteHint).performScrollTo().performTextInput("New draft check")
        composeRule.onNodeWithText("New draft check").performTextClearance()
        composeRule.onNodeWithText(draftActions).assertDoesNotExist()
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            .use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
    }

    private fun awaitState(activity: MainActivity, state: Lifecycle.State) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (activity.lifecycle.currentState != state && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        check(activity.lifecycle.currentState == state) { "Activity did not reach $state" }
    }
}
