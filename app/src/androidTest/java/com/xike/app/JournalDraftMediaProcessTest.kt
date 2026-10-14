package com.xike.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.data.model.parseJournalDraft
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Dedicated device, actual microphone recording; run write/read in separate processes. */
class JournalDraftMediaProcessTest {
    @Test
    fun realRecordingRemainsReadableAfterViewModelAndProcessExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("draftMediaIsolated") == "true")
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = JournalStore(application)
        val drafts = JournalDraftStore(application)
        val metadata =
            EncryptedSharedPreferences.create(
                application,
                "draft-media-process-test",
                MasterKey.Builder(application).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        lateinit var model: JournalViewModel
        instrumentation.runOnMainSync {
            model =
                ViewModelProvider(
                    owner,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(application),
                )[JournalViewModel::class.java]
        }
        try {
            awaitCondition { !model.isLoading }
            when (arguments.getString("draftMediaPhase")) {
                "write" -> {
                    check(!metadata.contains("original")) {
                        "An earlier media process test requires cleanup."
                    }
                    val original = drafts.load()
                    check(original.isEmpty) { "Use an independent device with an empty draft." }
                    check(
                        metadata.edit().putString("original", original.toJson().toString()).commit()
                    )
                    val recorder = XikeAudioRecorder(application)
                    try {
                        recorder.start {}
                        val startedAt = SystemClock.elapsedRealtime()
                        SystemClock.sleep(2_000)
                        val recording = recorder.stop()
                        val duration = SystemClock.elapsedRealtime() - startedAt
                        val expected = digest(recording.readBytes())
                        try {
                            instrumentation.runOnMainSync {
                                model.queueDraftAudio(recording, duration)
                            }
                            awaitCondition {
                                !model.isDraftAudioSaving && model.pendingDraftAudio == null
                            }
                            val audio =
                                checkNotNull(model.draft.audio) {
                                    model.dataError ?: "Recording was not imported."
                                }
                            assertFalse(recording.exists())
                            assertEquals(
                                expected,
                                store.openAudio(audio.fileName)!!.use { digest(it.readBytes()) },
                            )
                            check(
                                metadata
                                    .edit()
                                    .putString("hash", expected)
                                    .putString("audio", audio.toJson().toString())
                                    .commit()
                            )
                        } finally {
                            recording.delete()
                        }
                    } catch (error: Throwable) {
                        instrumentation.runOnMainSync { model.removeDraftAudio() }
                        drafts.save(original)
                        check(metadata.edit().clear().commit())
                        throw error
                    } finally {
                        recorder.cancel()
                    }
                    instrumentation.runOnMainSync { owner.viewModelStore.clear() }
                    awaitCondition { !drafts.hasPendingWrite }
                }
                "read" -> {
                    val original =
                        parseJournalDraft(checkNotNull(metadata.getString("original", null)))
                    try {
                        assertFalse(drafts.hasPendingWrite)
                        val audio = checkNotNull(drafts.load().audio)
                        assertEquals(metadata.getString("audio", null), audio.toJson().toString())
                        assertEquals(audio, model.draft.audio)
                        assertTrue(model.hasResumedDraft)
                        assertEquals(
                            metadata.getString("hash", null),
                            store.openAudio(audio.fileName)!!.use { digest(it.readBytes()) },
                        )
                    } finally {
                        val importedAudio = model.draft.audio
                        instrumentation.runOnMainSync { model.removeDraftAudio() }
                        importedAudio?.let { audio ->
                            awaitCondition {
                                store.openAudio(audio.fileName)?.use { false } ?: true
                            }
                        }
                        instrumentation.runOnMainSync { owner.viewModelStore.clear() }
                        awaitCondition { !drafts.hasPendingWrite }
                        drafts.save(original)
                        check(metadata.edit().clear().commit())
                    }
                }
                else -> error("draftMediaPhase must be write or read")
            }
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
        }
    }

    private fun digest(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("Timed out waiting for the real draft media operation", condition())
    }
}
