package com.xike.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPreferencesTest {
    @Test
    fun `cold start always requires authentication`() {
        assertTrue(
            shouldLockApp(
                backgroundedAtMillis = null,
                nowMillis = 10_000L,
                timeout = AppLockTimeout.THIRTY_MINUTES,
            ),
        )
    }

    @Test
    fun `immediate timeout locks as soon as the app leaves`() {
        assertTrue(
            shouldLockApp(
                backgroundedAtMillis = 10_000L,
                nowMillis = 10_000L,
                timeout = AppLockTimeout.IMMEDIATELY,
            ),
        )
    }

    @Test
    fun `timed lock waits until the selected duration has elapsed`() {
        assertFalse(shouldLockApp(1_000L, 60_999L, AppLockTimeout.ONE_MINUTE))
        assertTrue(shouldLockApp(1_000L, 61_000L, AppLockTimeout.ONE_MINUTE))
    }

    @Test
    fun `unknown stored timeout falls back to the safest option`() {
        assertEquals(AppLockTimeout.IMMEDIATELY, AppLockTimeout.fromStorage("UNKNOWN"))
    }
}
