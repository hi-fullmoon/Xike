package com.xike.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.feature.lock.AppLockPreferences
import com.xike.app.feature.lock.AppLockSessionState
import com.xike.app.feature.lock.AppLockTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Dedicated device only: set device PIN 2468, then run write/read in separate processes. */
class LanguageLockDraftUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun languageChangesPreserveTheAuthenticatedSessionAndCommittedDraft() {
        val phase = InstrumentationRegistry.getArguments().getString("languageAuditPhase")
        assumeTrue(phase == "write" || phase == "read")
        val lockPreferences = AppLockPreferences(context)
        val lock = ViewModelProvider(rule.activity)[AppLockSessionState::class.java]
        if (lockPreferences.enabled) {
            assertTrue(lock.isAppLocked)
            authenticateDeviceCredential { !lock.isAppLocked }
        }
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) { !model.isLoading }
        if (phase == "read") {
            assertTrue(lockPreferences.enabled)
            assertEquals(AppLanguage.SYSTEM, LanguagePreferences(context).language)
            assertEquals(AppLanguage.SYSTEM, AppLocale.selectedLanguage)
            val expectedLanguage =
                InstrumentationRegistry.getArguments().getString("expectedLanguage")
            if (expectedLanguage != null) {
                val expected = AppLanguage.valueOf(expectedLanguage)
                check(expected != AppLanguage.SYSTEM)
                assertEquals(expected, AppLocale.language)
                rule.onNodeWithTag("navigation-settings").performClick()
                rule
                    .onNodeWithText(if (expected == AppLanguage.CHINESE) "应用语言" else "App language")
                    .performScrollTo()
                    .assertIsDisplayed()
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                assertTrue(
                    context
                        .getSystemService(android.app.LocaleManager::class.java)
                        .applicationLocales
                        .isEmpty
                )
            }
            assertFalse(JournalDraftStore(context).hasPendingWrite)
            assertEquals(NOTE, JournalDraftStore(context).load().note)
            assertEquals(NOTE, model.draft.note)
            rule.onNodeWithTag("navigation-home").performClick()
            rule.onNodeWithText(NOTE).performScrollTo().assertIsDisplayed()
            return
        }

        check(model.draft.isEmpty || model.draft.note == NOTE) {
            "Use a dedicated device with no draft or only this audit's input."
        }
        rule.onNodeWithTag("navigation-home").performClick()
        if (model.draft.isEmpty) {
            rule
                .onNodeWithContentDescription(localizedText("此刻的注脚"))
                .performScrollTo()
                .performTextInput(NOTE)
            rule.onNodeWithText(localizedText("完成")).performScrollTo().performClick()
        }
        val draft = model.draft
        assertEquals(NOTE, draft.note)
        rule.onNodeWithTag("navigation-settings").performClick()
        if (lockPreferences.enabled) {
            rule
                .onNode(hasText(localizedText("应用锁")) and isToggleable())
                .performScrollTo()
                .performClick()
            authenticateDeviceCredential { !lockPreferences.enabled }
        }
        rule
            .onNode(hasText(localizedText("应用锁")) and isToggleable())
            .performScrollTo()
            .performClick()
        authenticateDeviceCredential { lockPreferences.enabled && !lock.isAppLocked }
        assertEquals(AppLockTimeout.IMMEDIATELY, lockPreferences.timeout)

        for (selected in listOf(AppLanguage.CHINESE, AppLanguage.ENGLISH, AppLanguage.SYSTEM)) {
            rule.onNodeWithText(localizedText("应用语言")).performScrollTo().performClick()
            rule.onNodeWithText(selected.displayName).performClick()
            rule.onNodeWithText(localizedText("应用语言")).performScrollTo().assertIsDisplayed()
            assertEquals(selected, LanguagePreferences(context).language)
            assertEquals(selected, AppLocale.selectedLanguage)
            assertFalse(lock.isAppLocked)
            assertNull(lock.backgroundedAtMillis)
            assertSame(lock, ViewModelProvider(rule.activity)[AppLockSessionState::class.java])
            assertSame(model, ViewModelProvider(rule.activity)[JournalViewModel::class.java])
            assertEquals(draft, model.draft)
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText(localizedText("应用语言")).performScrollTo().assertIsDisplayed()
            assertSame(lock, ViewModelProvider(rule.activity)[AppLockSessionState::class.java])
            assertSame(model, ViewModelProvider(rule.activity)[JournalViewModel::class.java])
            assertFalse(lock.isAppLocked)
            assertFalse(lock.authenticationInProgress)
            assertNull(lock.backgroundedAtMillis)
            assertEquals(draft, model.draft)
        }

        shell("input keyevent KEYCODE_HOME")
        awaitCondition { lock.backgroundedAtMillis != null }
        shell(
            "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.xike.app/.MainActivity"
        )
        awaitCondition { lock.isAppLocked }
        authenticateDeviceCredential { !lock.isAppLocked }
        assertEquals(draft, model.draft)
        awaitCondition { !JournalDraftStore(context).hasPendingWrite }
        assertEquals(draft, JournalDraftStore(context).load())
    }

    private fun authenticateDeviceCredential(accepted: () -> Boolean) {
        // Enter the dedicated device's credential through the real Android confirmation UI.
        // Cold launches finish the Compose welcome animation before opening authentication.
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        awaitCondition {
            val root = automation.rootInActiveWindow
            root?.packageName?.toString() in
                setOf("com.android.settings", "com.android.systemui") &&
                root?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) !=
                    null
        }
        automation.waitForIdle(300, 5_000)
        val field =
            checkNotNull(
                automation.rootInActiveWindow?.findFocus(
                    android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT
                )
            )
        assertTrue(
            field.performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                android.os.Bundle().apply {
                    putCharSequence(
                        android.view.accessibility.AccessibilityNodeInfo
                            .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "2468",
                    )
                },
            )
        )
        assertTrue(
            field.performAction(
                android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
                    .ACTION_IME_ENTER
                    .id
            )
        )
        awaitCondition(accepted)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Timed out waiting for the real lifecycle or credential result", condition())
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
            .use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
    }

    private companion object {
        const val NOTE = "语言切换验收：这份输入应在切换与重启后保留。"
    }
}
