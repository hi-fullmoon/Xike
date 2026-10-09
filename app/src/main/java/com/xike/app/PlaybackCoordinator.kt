package com.xike.app

/** Accessed on the main thread, including MediaPlayer callbacks. */
internal class PlaybackCoordinator(private val onReleased: () -> Unit = {}) {
    private var owner: Any? = null
    private var pause: (() -> Unit)? = null

    fun claim(nextOwner: Any, onPause: () -> Unit) {
        if (owner !== nextOwner) pauseActive()
        owner = nextOwner
        pause = onPause
    }

    fun owns(candidate: Any): Boolean = owner === candidate

    fun release(candidate: Any) {
        if (owns(candidate)) {
            owner = null
            pause = null
            onReleased()
        }
    }

    fun pauseActive() {
        val callback = pause
        owner = null
        pause = null
        if (callback != null) {
            try { callback() } finally { onReleased() }
        }
    }
}

internal val journalPlayback = PlaybackCoordinator { journalAudioFocus.abandon() }
