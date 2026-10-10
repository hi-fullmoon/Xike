package com.xike.app

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class JournalSaveConcurrencyTest {
    @Test
    fun leavingTheRequestDoesNotAllowASecondSave() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = JournalStore(application)
        val source = store.entries().firstOrNull()
        assumeTrue("A real existing entry is required", source != null)
        val draftStore = JournalDraftStore(application)
        assumeTrue("The verification environment must have an empty draft", draftStore.load().isEmpty)
        val firstEntry = requireNotNull(source).copy(id = UUID.randomUUID().toString(), imageFileNames = emptyList(), audio = null, video = null)
        val secondEntry = firstEntry.copy(id = UUID.randomUUID().toString())
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[JournalViewModel::class.java]
        }
        try {
            awaitCondition { !model.isLoading }
            withContext(Dispatchers.Main) {
                val requester = async(start = CoroutineStart.UNDISPATCHED) { model.save(firstEntry, emptyList()) }
                assertTrue(model.isDraftSaving)
                requester.cancel()
                assertTrue(model.save(secondEntry, emptyList()).isFailure)
            }
            awaitCondition { !model.isDraftSaving }
            val ids = store.entries().map(JournalEntry::id)
            assertTrue(ids.contains(firstEntry.id))
            assertFalse(ids.contains(secondEntry.id))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.viewModelStore.clear() }
            listOf(firstEntry, secondEntry).forEach { entry ->
                if (store.entries().any { it.id == entry.id }) store.delete(entry.id, refreshEntries = false)
                store.finalizeDelete(entry.id)
            }
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("Timed out waiting for the real save operation", condition())
    }

    @Test
    fun realSaveFailureSurvivesCancellationOfTheRequestingPage() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = JournalStore(application)
        val source = store.entries().firstOrNull()
        assumeTrue("A real existing entry is required", source != null)
        assumeTrue("The verification environment must have an empty draft", JournalDraftStore(application).load().isEmpty)
        val entry = requireNotNull(source).copy(id = UUID.randomUUID().toString())
        val directory = File(application.cacheDir, "save-directory-${System.nanoTime()}").apply { mkdirs() }
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[JournalViewModel::class.java]
        }
        try {
            awaitCondition { !model.isLoading }
            withContext(Dispatchers.Main) {
                val requester = async(start = CoroutineStart.UNDISPATCHED) { model.save(entry, listOf(Uri.fromFile(directory))) }
                assertTrue(model.isDraftSaving)
                requester.cancel()
            }
            awaitCondition { !model.isDraftSaving }
            val feedback = model.operationFeedback.single()
            assertTrue(feedback.isError)
            assertFalse(store.entries().any { it.id == entry.id })
            withContext(Dispatchers.Main) { model.acknowledgeFeedback(feedback) }
            assertTrue(model.operationFeedback.isEmpty())
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.viewModelStore.clear() }
            directory.delete()
            if (store.entries().any { it.id == entry.id }) {
                store.delete(entry.id, refreshEntries = false)
                store.finalizeDelete(entry.id)
            }
        }
    }
}
