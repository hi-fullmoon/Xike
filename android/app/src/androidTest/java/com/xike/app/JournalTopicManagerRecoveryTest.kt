package com.xike.app

import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the real Activity on an explicitly isolated device; restores its original size. */
class JournalTopicManagerRecoveryTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun historicalLabelsSurviveRecreationResizeAndAnotherProcess() {
        val phase = InstrumentationRegistry.getArguments().getString("topicRecoveryPhase")
        assumeTrue(phase == "write" || phase == "verify")
        val validation = RetiredLabelValidation()
        if (phase == "verify") {
            validation.verify("recovery")
            return
        }
        validation.checkpoint("recovery")
        rule.withRetiredLabelActivity { scenario ->
            rule.assertRetiredLabelControlsAbsent()
            scenario.recreate()
            awaitHome()
            rule.assertRetiredLabelControlsAbsent()
            withShortWindow(validation, scenario) { rule.assertRetiredLabelControlsAbsent() }
            rule.assertRetiredLabelControlsAbsent()
        }
        validation.verify("recovery")
    }

    @Test
    fun shortWindowKeepsHomeActionsReachableWithoutRetiredManagers() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("topicShortWindow") == "true")
        val validation = RetiredLabelValidation()
        val before = validation.digest()
        rule.withRetiredLabelActivity { scenario ->
            withShortWindow(validation, scenario) {
                rule.assertRetiredLabelControlsAbsent()
                rule
                    .onNodeWithContentDescription(tr("添加内容", "Add content"))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
        assertEquals("Resize changed historical data", before, validation.digest())
    }

    private fun withShortWindow(
        validation: RetiredLabelValidation,
        scenario: ActivityScenario<MainActivity>,
        block: () -> Unit,
    ) {
        val original =
            Settings.Global.getString(validation.context.contentResolver, "display_size_forced")
        val restoreSize = original?.takeIf { it.isNotBlank() }?.replace(',', 'x') ?: "reset"
        try {
            resizeAndWait(scenario, "1866x840")
            block()
        } finally {
            resizeAndWait(scenario, restoreSize)
        }
    }

    private fun resizeAndWait(scenario: ActivityScenario<MainActivity>, size: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("wm size $size")
            .let {
                ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
            }
        // Setting the current size need not trigger Android's automatic recreation. Explicitly
        // recreate after the real size command so both paths check restored UI and data.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.recreate()
        awaitHome()
    }

    private fun awaitHome() {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
