package com.xike.app

import android.app.Application
import android.Manifest
import android.media.MediaRecorder
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalViewModelPersistenceTest {
    @Test
    fun temporarilyUnavailablePhotoKeepsItsDraftReference() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = JournalDraftStore(application).load()
        val photo = File.createTempFile("draft-screen-", ".png", application.cacheDir)
        val held = File(application.cacheDir, "held-${photo.name}")
        val owner = owner()
        try {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                photo.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                screenshot.recycle()
            }
            val uri = Uri.fromFile(photo).toString()
            val saved = original.copy(imageUriStrings = listOf(uri))
            JournalDraftStore(application).save(saved)
            assertTrue(photo.renameTo(held))
            val viewModel = viewModel(application, owner)
            awaitCondition { !viewModel.isLoading }
            assertEquals(listOf(uri), viewModel.draft.imageUriStrings)
            assertEquals(listOf(uri), JournalDraftStore(application).load().imageUriStrings)
            assertTrue(viewModel.dataError != null)
            assertTrue(held.renameTo(photo))
            application.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r").use {
                assertTrue(requireNotNull(it).length > 0L)
            }
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            JournalDraftStore(application).save(original)
            photo.delete()
            held.delete()
        }
    }

    @Test
    fun immediateRecreationInheritsLatestInputWithoutWaitingForDiskSave() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val original = JournalDraftStore(application).load()
        val firstOwner = owner()
        val secondOwner = owner()
        val first = viewModel(application, firstOwner)
        awaitCondition { !first.isLoading }
        var second: JournalViewModel? = null
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                first.updateDraftNote("走完后心情平静")
                firstOwner.viewModelStore.clear()
                second = viewModel(application, secondOwner)
            }
            awaitCondition { second?.isLoading == false }
            assertEquals("走完后心情平静", requireNotNull(second).draft.note)
            assertEquals("走完后心情平静", JournalDraftStore(application).load().note)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                firstOwner.viewModelStore.clear()
                secondOwner.viewModelStore.clear()
            }
            JournalDraftStore(application).save(original)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun unavailableRecordingRetainsDraftReferenceAndCanBeReadAfterFileReturns() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(application.packageName, Manifest.permission.RECORD_AUDIO)
        val original = JournalDraftStore(application).load()
        val source = File.createTempFile("draft-recording-", ".m4a", application.cacheDir)
        val recorder = MediaRecorder(application)
        val owner = owner()
        val store = JournalStore(application)
        var encryptedFile: File? = null
        var heldFile: File? = null
        var audio: JournalAudio? = null
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(source.absolutePath)
            recorder.prepare()
            recorder.start()
            SystemClock.sleep(500L)
            recorder.stop()
            val pendingStore = PendingDraftAudioStore(application)
            val staged = pendingStore.stage(source, 500L)
            val pendingFile = File(application.filesDir, "pending-draft-audio/${staged.fileName}")
            val renamed = File(pendingFile.parentFile, "xike-pending-${UUID.randomUUID()}.enc")
            try {
                assertTrue(pendingFile.renameTo(renamed))
                assertThrows(IllegalStateException::class.java) { pendingStore.recover() }
                assertTrue(renamed.isFile)
                assertTrue(renamed.renameTo(pendingFile))
                assertEquals(staged, pendingStore.recover())
            } finally {
                if (renamed.exists()) assertTrue(renamed.renameTo(pendingFile))
                pendingStore.delete(staged)
            }
            audio = store.importAudio(source, 500L)
            val recordedDraft = original.copy(audio = audio)
            JournalDraftStore(application).save(recordedDraft)
            encryptedFile = File(application.filesDir, "journal-audios/${audio.fileName}")
            heldFile = File(application.cacheDir, encryptedFile.name)
            assertTrue(encryptedFile.renameTo(heldFile))

            val viewModel = viewModel(application, owner)
            awaitCondition { !viewModel.isLoading }
            assertEquals(audio, viewModel.draft.audio)
            assertEquals(audio, JournalDraftStore(application).load().audio)
            assertTrue(viewModel.dataError != null)
            assertTrue(heldFile.renameTo(encryptedFile))
            store.requireReadableAudio(audio.fileName)
        } finally {
            recorder.release()
            if (heldFile?.exists() == true) assertTrue(heldFile.renameTo(requireNotNull(encryptedFile)))
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            JournalDraftStore(application).save(original)
            audio?.let { store.deleteUnreferencedAudio(it.fileName) }
            source.delete()
        }
    }

    @Test
    fun latestInputSurvivesImmediateViewModelClear() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val draftStore = JournalDraftStore(application)
        val original = draftStore.load()
        val owner = owner()
        val viewModel = viewModel(application, owner)
        awaitCondition { !viewModel.isLoading }
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.updateDraftNote("今天")
                viewModel.updateDraftNote("今天走了一段路")
                assertEquals("今天走了一段路", viewModel.draft.note)
                owner.viewModelStore.clear()
            }
            awaitCondition { !draftStore.hasPendingWrite }
            assertEquals("今天走了一段路", draftStore.load().note)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.viewModelStore.clear() }
            draftStore.save(original)
        }
    }

    @Test
    fun queuedInputCannotRestoreDiscardedDraftAndObservationTracksMutations() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val draftStore = JournalDraftStore(application)
        val original = draftStore.load()
        val owner = owner()
        val viewModel = viewModel(application, owner)
        val entry = JournalEntry(mood = Mood.CALM, tags = emptyList(), note = "今天走了一段路")
        awaitCondition { !viewModel.isLoading }
        // This test never removes pre-existing draft attachments.
        assertTrue(viewModel.draft.audio == null && viewModel.draft.imageUriStrings.isEmpty())
        try {
            withContext(Dispatchers.Main) {
                viewModel.updateDraftNote(entry.note)
                viewModel.discardDraft()
            }
            SystemClock.sleep(350L)
            assertTrue(draftStore.load().isEmpty)
            withContext(Dispatchers.Main) { viewModel.save(entry, emptyList()).getOrThrow() }
            awaitCondition { viewModel.entries.any { it.id == entry.id } }
            val edited = withContext(Dispatchers.Main) {
                viewModel.update(entry.copy(note = "走完后心情平静"), emptyList(), emptyList()).getOrThrow()
            }
            awaitCondition { viewModel.entries.any { it.id == entry.id && it.note == edited.note } }
            withContext(Dispatchers.Main) { viewModel.delete(edited).getOrThrow() }
            awaitCondition { viewModel.entries.none { it.id == entry.id } }
            withContext(Dispatchers.Main) { viewModel.undoDelete(entry.id).getOrThrow() }
            awaitCondition { viewModel.entries.any { it.id == entry.id && it.note == edited.note } }
        } finally {
            val store = JournalStore(application)
            if (store.entries().any { it.id == entry.id }) store.delete(entry.id, refreshEntries = false)
            store.finalizeDelete(entry.id)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.viewModelStore.clear() }
            draftStore.save(original)
        }
    }

    private fun owner() = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun viewModel(application: Application, owner: ViewModelStoreOwner): JournalViewModel =
        ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[JournalViewModel::class.java]

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25L)
        assertTrue("Timed out waiting for persisted state", condition())
    }
}
