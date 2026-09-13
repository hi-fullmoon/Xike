package com.xike.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceRecordingFlowTest {
    @Test
    fun encryptedPendingRecordingSurvivesStoreRecreation() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = PendingDraftAudioStore(application)
        val bytes = "只属于这次录音的内容-987654".toByteArray()
        val source = File.createTempFile("xike-recording-test-", ".m4a", application.cacheDir)
        source.writeBytes(bytes)
        var staged: StagedDraftAudio? = null
        try {
            staged = store.stage(source, 3_000L)
            source.delete()
            val encryptedFile = File(application.filesDir, "pending-draft-audio/${staged.fileName}")
            assertTrue(encryptedFile.isFile)
            assertFalse(encryptedFile.readBytes().toString(Charsets.UTF_8).contains("只属于这次录音"))
            assertEquals(staged, PendingDraftAudioStore(application).recover())
            assertArrayEquals(bytes, PendingDraftAudioStore(application).open(staged).use { it.readBytes() })
        } finally {
            source.delete()
            staged?.let(store::delete)
        }
    }

    @Test
    fun recoveredRecordingIsImportedIntoDraftAfterViewModelRecreation() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val pendingStore = PendingDraftAudioStore(application)
        JournalDraftStore(application).save(JournalDraft())
        val bytes = "recovered-audio-${SystemClock.elapsedRealtime()}".toByteArray()
        val source = File.createTempFile("xike-recording-test-", ".m4a", application.cacheDir)
        source.writeBytes(bytes)
        val staged = pendingStore.stage(source, 3_000L)
        source.delete()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val viewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[JournalViewModel::class.java]
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            awaitCondition {
                !viewModel.isLoading && !viewModel.isDraftAudioSaving &&
                    viewModel.pendingDraftAudio == null && viewModel.draft.audio != null
            }
            val audio = requireNotNull(viewModel.draft.audio)
            assertArrayEquals(bytes, viewModel.openAudio(audio.fileName)?.use { it.readBytes() })
            assertFalse(File(application.filesDir, "pending-draft-audio/${staged.fileName}").exists())
        } finally {
            instrumentation.runOnMainSync { viewModel.discardDraft() }
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            pendingStore.delete(staged)
            JournalDraftStore(application).save(JournalDraft())
            source.delete()
        }
    }

    @Test
    fun recordingThatCannotBeEncryptedDoesNotLeavePlaintextBehind() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val viewModel = ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[JournalViewModel::class.java]
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        awaitCondition { !viewModel.isLoading }
        val source = File.createTempFile("xike-recording-test-", ".m4a", application.cacheDir)
        try {
            instrumentation.runOnMainSync { viewModel.queueDraftAudio(source, 3_000L) }
            awaitCondition { !viewModel.isDraftAudioSaving && viewModel.dataError != null }
            assertFalse(source.exists())
            assertNull(viewModel.pendingDraftAudio)
        } finally {
            source.delete()
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(25L)
        }
        assertTrue("Timed out waiting for audio save state", condition())
    }
}
