package com.xike.app

import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.feature.lock.AppLockPreferences
import com.xike.app.feature.lock.AppLockSessionState
import com.xike.app.feature.lock.AppLockTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real picker and device-credential authentication, controlled externally on an isolated device.
 */
@RunWith(AndroidJUnit4::class)
class PickerTimeoutDeviceTest {
    @Test
    fun realPickerQuickReturnAndUnobservedTimeoutRetainResultUntilAuthentication() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("pickerIsolated") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        check(JournalStore(context).also { it.initialize() }.entries().isEmpty())
        val preferences = AppLockPreferences(context)
        val beforeEnabled = preferences.enabled
        val beforeTimeout = preferences.timeout
        preferences.enabled = true
        preferences.timeout = AppLockTimeout.ONE_MINUTE
        fun report(phase: String) {
            instrumentation.sendStatus(0, Bundle().apply { putString("pickerPhase", phase) })
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var session: AppLockSessionState
                scenario.onActivity {
                    session = ViewModelProvider(it)[AppLockSessionState::class.java]
                }
                assertTrue(session.isAppLocked)
                report("AUTHENTICATE_INITIAL")
                await { !session.isAppLocked && session.journalSessionOpened }
                report("OPEN_QUICK_PICKER")
                await { session.systemActivityInProgress }
                report("CANCEL_QUICK_PICKER")
                await { !session.systemActivityInProgress }
                assertFalse(session.isAppLocked)
                report("OPEN_LONG_PICKER")
                await { session.systemActivityInProgress }
                report("SWITCH_TO_OTHER_APP")
                val started = SystemClock.elapsedRealtime()
                // Wait actual monotonic time, without modifying the clock or timeout policy.
                await {
                    SystemClock.elapsedRealtime() - started >= 65_000L
                }
                assertTrue(
                    context.getSystemService(android.os.PowerManager::class.java).isInteractive
                )
                assertFalse(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
                assertTrue(session.systemActivityInProgress)
                assertNull(session.backgroundedAtMillis)
                report("RETURN_AND_CANCEL_LONG_PICKER")
                await { session.isAppLocked && !session.systemActivityInProgress }
                lateinit var result: SystemActivityResultState<List<Uri>>
                scenario.onActivity {
                    @Suppress("UNCHECKED_CAST")
                    result =
                        ViewModelProvider(it)
                            .get(
                                "system-activity-result/photo/draft/picker",
                                SystemActivityResultState::class.java,
                            ) as SystemActivityResultState<List<Uri>>
                }
                await { result.hasPendingResult }
                assertTrue(session.isAppLocked)
                report("RESULT_HELD_AUTHENTICATE_AGAIN")
                await { !session.isAppLocked && !result.hasPendingResult }
                assertNull(session.backgroundedAtMillis)
                report("AUTHENTICATED_RESULT_DELIVERED")
            }
        } finally {
            preferences.enabled = beforeEnabled
            preferences.timeout = beforeTimeout
        }
    }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 150_000L
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < end) {
                "Timed out waiting for real device interaction"
            }
            SystemClock.sleep(100L)
        }
    }
}
