package com.xike.app

import android.Manifest
import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.draft.JournalDraftStore
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Dedicated device; uses an actual microphone file and real filesystem permission failures. */
class DraftAudioRecoveryProcessTest {
    @Test
    fun activeRecordingIsNotOfferedAsACompletedRecovery() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = ApplicationProvider.getApplicationContext<Application>()
        grantMicrophone(application)
        val store = PendingDraftAudioStore(application)
        check(store.recoverRecording() == null) {
            "Use an isolated device with no pending recording."
        }
        val recorder = XikeAudioRecorder(application, draftRecordingDirectory(application))
        var completed: File? = null
        try {
            val active = recorder.start {}
            SystemClock.sleep(1000)
            assertNull(store.recoverRecording())
            completed = recorder.stop()
            assertFalse(active.exists())
            assertTrue(completed.isFile)
            val recovered = requireNotNull(store.recoverRecording())
            assertTrue(recovered.first == completed && recovered.second > 0L)
        } finally {
            recorder.cancel()
            completed?.delete()
        }
    }

    @Test
    fun failedEncryptionCanBeRetriedAfterProcessExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("draftAudioRecoveryIsolated") == "true")
        val application = ApplicationProvider.getApplicationContext<Application>()
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        val staging = File(application.filesDir, "pending-draft-audio")
        val expected = File(application.cacheDir, "xike-recovery-test.sha256")
        lateinit var model: JournalViewModel
        instrumentation.runOnMainSync {
            model =
                ViewModelProvider(
                    owner,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(application),
                )[JournalViewModel::class.java]
        }
        try {
            await { !model.isLoading }
            when (arguments.getString("draftAudioRecoveryPhase")) {
                "write" -> {
                    check(
                        JournalDraftStore(application).load().isEmpty &&
                            model.pendingDraftAudio == null
                    )
                    check(!expected.exists())
                    grantMicrophone(application)
                    val recorder =
                        XikeAudioRecorder(application, draftRecordingDirectory(application))
                    try {
                        recorder.start {}
                        SystemClock.sleep(1200)
                        val source = recorder.stop()
                        expected.writeBytes(source.inputStream().use(::digest))
                        check(staging.setWritable(false, false))
                        instrumentation.runOnMainSync { model.queueDraftAudio(source, 1200) }
                        await { !model.isDraftAudioSaving && model.draftAudioSaveError != null }
                        assertNotNull(model.pendingDraftAudio)
                        assertTrue(source.isFile)
                        assertArrayEquals(expected.readBytes(), source.inputStream().use(::digest))
                    } finally {
                        recorder.cancel()
                        check(staging.setWritable(true, true))
                    }
                }
                "read" -> {
                    await {
                        model.draft.audio != null &&
                            model.pendingDraftAudio == null &&
                            !model.isDraftAudioSaving
                    }
                    val audio = requireNotNull(model.draft.audio)
                    assertArrayEquals(
                        expected.readBytes(),
                        model.openAudio(audio.fileName)!!.use(::digest),
                    )
                    assertFalse(
                        draftRecordingDirectory(application).listFiles().orEmpty().any { it.isFile }
                    )
                    instrumentation.runOnMainSync { model.removeDraftAudio() }
                    await { !JournalDraftStore(application).hasPendingWrite }
                    expected.delete()
                }
                else -> error("draftAudioRecoveryPhase must be write or read")
            }
        } finally {
            staging.setWritable(true, true)
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
        }
    }

    private fun grantMicrophone(application: Application) {
        val descriptor =
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand(
                    "pm grant ${application.packageName} ${Manifest.permission.RECORD_AUDIO}"
                )
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun digest(input: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("Timed out waiting for real audio persistence", condition())
    }
}
