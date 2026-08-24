package com.xike.app

import java.lang.reflect.Modifier
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionStateTest {
    @Test
    fun viewModelIsPubliclyConstructibleByLifecycleFactory() {
        val viewModelClass = AppLockSessionState::class.java

        assertTrue(Modifier.isPublic(viewModelClass.modifiers))
        assertTrue(Modifier.isPublic(viewModelClass.getConstructor().modifiers))
    }
}
