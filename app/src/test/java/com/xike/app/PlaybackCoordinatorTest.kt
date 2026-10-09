package com.xike.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorTest {
    @Test
    fun focusResourcesAreReleasedOnceAfterPauseAndNotByStaleOwners() {
        val events = mutableListOf<String>()
        val coordinator = PlaybackCoordinator { events += "released" }
        val audio = Any()
        val video = Any()
        coordinator.claim(audio) { events += "paused" }
        coordinator.claim(video) { events += "video paused" }
        assertEquals(listOf("paused", "released"), events)
        coordinator.release(audio)
        assertEquals(2, events.size)
        coordinator.release(video)
        coordinator.pauseActive()
        assertEquals(listOf("paused", "released", "released"), events)
    }

    @Test
    fun switchingMediaPausesPreviousAndRevokesPendingAutoplay() {
        val coordinator = PlaybackCoordinator()
        val audio = Any()
        val video = Any()
        var pauses = 0
        coordinator.claim(audio) { pauses++ }
        coordinator.claim(video) {}
        assertEquals(1, pauses)
        assertFalse(coordinator.owns(audio))
        assertTrue(coordinator.owns(video))
        // Late disposal of the audio must not revoke the current video.
        coordinator.release(audio)
        assertTrue(coordinator.owns(video))
    }

    @Test
    fun backgroundOrLockRevokesPlaybackBeforeInvokingPause() {
        val coordinator = PlaybackCoordinator()
        val media = Any()
        var pauses = 0
        coordinator.claim(media) {
            assertFalse(coordinator.owns(media))
            pauses++
        }
        coordinator.pauseActive()
        coordinator.pauseActive()
        assertEquals(1, pauses)
        assertFalse(coordinator.owns(media))
    }

    @Test
    fun resumingSameOwnerDoesNotPauseItself() {
        val coordinator = PlaybackCoordinator()
        val media = Any()
        var pauses = 0
        coordinator.claim(media) { pauses++ }
        coordinator.claim(media) { pauses++ }
        assertEquals(0, pauses)
        coordinator.release(media)
        coordinator.pauseActive()
        assertEquals(0, pauses)
    }
}
