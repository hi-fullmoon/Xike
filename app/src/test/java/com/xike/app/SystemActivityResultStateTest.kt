package com.xike.app

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SystemActivityResultStateTest {
    @Test
    fun rejectedDeliveryKeepsPendingNotificationUntilAccepted() {
        val state = SystemActivityResultState<Boolean>(SavedStateHandle())
        val observed = mutableListOf<Boolean>()
        state.observePending(observed::add)
        state.receive(true)
        assertEquals(listOf(false, true), observed)
        assertEquals(false, state.acceptWhenReady(true) { false })
        assertEquals(listOf(false, true), observed)
        state.acceptWhenReady(true) { true }
        assertEquals(listOf(false, true, false), observed)
    }

    @Test
    fun restoredPendingResultBlocksImmediatelyAndDetachedObserverIsNotCalled() {
        val savedState = SavedStateHandle()
        SystemActivityResultState<Boolean>(savedState).receive(true)
        val restored = SystemActivityResultState<Boolean>(restore(savedState))
        val observed = mutableListOf<Boolean>()
        val stop = restored.observePending(observed::add)
        assertEquals(listOf(true), observed)
        stop()
        restored.consume()
        assertEquals(listOf(true), observed)
        val resumed = mutableListOf<Boolean>()
        restored.observePending(resumed::add)
        assertEquals(listOf(false), resumed)
    }

    @Test
    fun busyConsumerKeepsResultUntilReadyAndAcceptsItOnce() {
        val state = SystemActivityResultState<Boolean>(SavedStateHandle())
        state.receive(true)
        var deliveries = 0
        val accept: (Boolean) -> Boolean = { deliveries++; it }
        assertEquals(false, state.acceptWhenReady(false, accept))
        assertEquals(0, deliveries)
        assertNotNull(state.pending)
        assertEquals(true, state.acceptWhenReady(true, accept))
        assertEquals(false, state.acceptWhenReady(true, accept))
        assertEquals(1, deliveries)
    }

    @Test
    fun rejectedResultSurvivesRestorationAndCanBeRetried() {
        val savedState = SavedStateHandle()
        val state = SystemActivityResultState<Boolean>(savedState)
        state.receive(true)
        assertEquals(false, state.acceptWhenReady(true) { false })
        assertNotNull(state.pending)
        val restored = SystemActivityResultState<Boolean>(restore(savedState))
        assertEquals(true, restored.acceptWhenReady(true) { it })
        assertNull(restored.pending)
    }

    @Test
    fun restoredCancellationIsDeliveredOnceInsteadOfBeingTreatedAsNoResult() {
        val savedState = SavedStateHandle()
        SystemActivityResultState<Uri?>(savedState).receive(null)

        val restored = SystemActivityResultState<Uri?>(restore(savedState))
        val result = restored.consume()
        assertNotNull(result)
        assertNull(result?.value)
        assertNull(restored.consume())
    }

    @Test
    fun consumedCaptureResultsAreNotReplayedAfterAnotherStateRestoration() {
        listOf(true, false).forEach { captured ->
            val savedState = SavedStateHandle()
            SystemActivityResultState<Boolean>(savedState).receive(captured)
            val restoredState = restore(savedState)
            val restored = SystemActivityResultState<Boolean>(restoredState)

            assertEquals(captured, restored.consume()?.value)
            assertNull(SystemActivityResultState<Boolean>(restore(restoredState)).consume())
        }
    }

    private fun restore(state: SavedStateHandle) = SavedStateHandle(
        state.keys().associateWith { state.get<Any?>(it) },
    )
}
