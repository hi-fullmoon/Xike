package com.xike.app

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionStateTest {
    @Test
    fun viewModelIsPubliclyConstructibleByLifecycleFactory() {
        val viewModelClass = AppLockSessionState::class.java

        assertTrue(Modifier.isPublic(viewModelClass.modifiers))
        assertTrue(Modifier.isPublic(viewModelClass.getConstructor().modifiers))
    }

    @Test
    fun systemCameraRoundTripDoesNotStartTheLockTimer() {
        val state = AppLockSessionState()

        state.beginSystemActivity()
        state.recordBackgrounded(12_000L)

        assertTrue(state.systemActivityInProgress)
        assertNull(state.backgroundedAtMillis)

        state.finishSystemActivity()
        assertFalse(state.systemActivityInProgress)
        assertNull(state.backgroundedAtMillis)

        state.recordBackgrounded(18_000L)
        assertEquals(18_000L, state.backgroundedAtMillis)
    }
}
