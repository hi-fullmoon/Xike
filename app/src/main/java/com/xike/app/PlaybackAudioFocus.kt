package com.xike.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Shares one platform focus request across the app's active playback session. Main thread only. */
internal class PlaybackAudioFocus {
    private var manager: AudioManager? = null
    private var activeRequest: AudioFocusRequest? = null

    fun request(context: Context, attributes: AudioAttributes): Boolean {
        if (activeRequest != null) return true
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        lateinit var request: AudioFocusRequest
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                if (activeRequest === request && (change == AudioManager.AUDIOFOCUS_LOSS ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) {
                    journalPlayback.pauseActive()
                }
            }, Handler(Looper.getMainLooper()))
            .build()
        manager = audioManager
        activeRequest = request
        val granted = runCatching { audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }.getOrDefault(false)
        if (!granted) abandon()
        return granted && activeRequest === request
    }

    fun abandon() {
        val request = activeRequest
        val audioManager = manager
        activeRequest = null
        manager = null
        if (request != null && audioManager != null) audioManager.abandonAudioFocusRequest(request)
    }
}

internal val journalAudioFocus = PlaybackAudioFocus()

internal fun playbackAudioAttributes(contentType: Int): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(contentType)
    .build()

internal fun claimPlaybackFocus(context: Context, owner: Any, attributes: AudioAttributes, onPause: () -> Unit): Boolean {
    journalPlayback.claim(owner, onPause)
    if (!journalAudioFocus.request(context, attributes)) {
        journalPlayback.pauseActive()
        return false
    }
    return journalPlayback.owns(owner)
}
