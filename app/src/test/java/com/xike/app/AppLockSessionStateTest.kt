package com.xike.app

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionStateTest {
    @Test
    fun unlockedSessionWithoutBackgroundingStaysUnlockedForEveryTimeout() {
        val state = AppLockSessionState().apply { isAppLocked = false }

        AppLockTimeout.entries.forEach { timeout ->
            assertFalse(state.shouldLockOnForeground(10_000L, timeout))
        }
    }

    @Test
    fun coldAndManuallyLockedSessionsRequireAuthenticationWithoutBackgrounding() {
        val state = AppLockSessionState()
        assertTrue(state.shouldLockOnForeground(10_000L, AppLockTimeout.THIRTY_MINUTES))

        state.isAppLocked = false
        state.isAppLocked = true
        assertTrue(state.shouldLockOnForeground(10_000L, AppLockTimeout.THIRTY_MINUTES))
    }

    @Test
    fun unlockedSessionLocksOnlyAfterItsBackgroundTimeout() {
        val state = AppLockSessionState().apply { isAppLocked = false }
        state.recordBackgrounded(1_000L)

        assertTrue(state.shouldLockOnForeground(1_000L, AppLockTimeout.IMMEDIATELY))
        assertFalse(state.shouldLockOnForeground(60_999L, AppLockTimeout.ONE_MINUTE))
        assertTrue(state.shouldLockOnForeground(61_000L, AppLockTimeout.ONE_MINUTE))
    }

    @Test
    fun viewModelIsPubliclyConstructibleByLifecycleFactory() {
        val viewModelClass = AppLockSessionState::class.java

        assertTrue(Modifier.isPublic(viewModelClass.modifiers))
        assertTrue(Modifier.isPublic(viewModelClass.getConstructor().modifiers))
    }

    @Test
    fun systemActivityRoundTripDoesNotLockAnUnlockedSession() {
        val state = AppLockSessionState().apply { isAppLocked = false }

        state.beginSystemActivity()
        state.recordBackgrounded(12_000L)

        assertTrue(state.systemActivityInProgress)
        assertNull(state.backgroundedAtMillis)

        state.finishSystemActivity()
        assertFalse(state.systemActivityInProgress)
        assertNull(state.backgroundedAtMillis)
        assertFalse(state.shouldLockOnForeground(18_000L, AppLockTimeout.IMMEDIATELY))

        state.recordBackgrounded(18_000L)
        assertEquals(18_000L, state.backgroundedAtMillis)
        assertTrue(state.shouldLockOnForeground(18_000L, AppLockTimeout.IMMEDIATELY))
    }

    @Test
    fun systemActivityReturnDoesNotUnlockAnAlreadyLockedSession() {
        val state = AppLockSessionState()
        state.beginSystemActivity()
        state.recordBackgrounded(12_000L)
        state.finishSystemActivity()

        assertTrue(state.shouldLockOnForeground(18_000L, AppLockTimeout.IMMEDIATELY))
    }

    @Test
    fun leavingSystemPickerKeepsTheBackgroundTimerAfterItsResult() {
        val state = AppLockSessionState().apply { isAppLocked = false }
        state.beginSystemActivity()
        state.recordBackgrounded(1_000L)
        state.interruptSystemActivity(5_000L)
        state.recordBackgrounded(8_000L)
        state.finishSystemActivity()

        assertFalse(state.systemActivityInProgress)
        assertEquals(5_000L, state.backgroundedAtMillis)
        assertTrue(state.shouldLockOnForeground(5_000L, AppLockTimeout.IMMEDIATELY))
        assertFalse(state.shouldLockOnForeground(64_999L, AppLockTimeout.ONE_MINUTE))
        assertTrue(state.shouldLockOnForeground(65_000L, AppLockTimeout.ONE_MINUTE))
    }

    @Test
    fun repeatedInterruptionDoesNotRestartTheBackgroundTimer() {
        val state = AppLockSessionState().apply { isAppLocked = false }
        state.beginSystemActivity()
        state.interruptSystemActivity(5_000L)
        state.interruptSystemActivity(20_000L)
        state.finishSystemActivity()

        assertEquals(5_000L, state.backgroundedAtMillis)
        assertTrue(state.shouldLockOnForeground(65_000L, AppLockTimeout.ONE_MINUTE))
    }
}
