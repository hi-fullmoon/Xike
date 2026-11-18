package com.xike.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.backup.JournalStores
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.draft.JournalDraftStore
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run the write and read phases in separate instrumentation processes on an isolated device. */
@RunWith(AndroidJUnit4::class)
class JournalCommitRecoveryProcessTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = JournalStores.get(application)
    private val dao = JournalDatabase.get(application).journalDao()
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun olderUndoSurvivesAnInterruptedSecondRestore() {
        assumeTrue(arguments.getString("commitRecoveryIsolated") == "true")
        store.initialize(loadEntries = false)
        when (arguments.getString("phase")) {
            "write" -> {
                assertEquals(0, dao.entryCount())
                val selected = File.createTempFile("restore-source-", ".zip", application.cacheDir)
                try {
                    val original = JournalEntry(mood = Mood.CALM, note = RESTORED_NOTE)
                    store.add(original)
                    selected.outputStream().use { store.writeBackup(it, null) }
                    store.update(original.copy(note = ORIGINAL_NOTE), emptyList())
                    selected.inputStream().use {
                        store.restoreBackup(it, null, mode = BackupRestoreMode.REPLACE)
                    }
                    val committed = requireNotNull(dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
                    assertTrue(File(application.filesDir, committed).isFile)

                    // Persist the boundary left by a restore that exits before replacing journals.
                    val prepare = JournalStore::class.java.getDeclaredMethod("createUndoSnapshot")
                    prepare.isAccessible = true
                    prepare.invoke(store)
                    assertEquals(committed, dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
                    assertEquals(2, undoFiles().size)
                } finally {
                    selected.delete()
                }
            }
            "read" -> {
                assertEquals(RESTORED_NOTE, store.entries().single().note)
                val committed = requireNotNull(dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
                assertTrue(store.canUndoLastRestore())
                assertEquals(setOf(committed), undoFiles())
                val original = store.undoLastRestore().single()
                assertEquals(ORIGINAL_NOTE, original.note)
                assertTrue(undoFiles().isEmpty())
                store.delete(original.id, refreshEntries = false)
                store.finalizeDelete(original.id)
            }
            else -> error("Specify phase=write or phase=read")
        }
    }

    @Test
    fun legacyUndoCredentialsStillWork() {
        assumeTrue(arguments.getString("commitRecoveryIsolated") == "true")
        store.initialize(loadEntries = false)
        assertEquals(0, dao.entryCount())
        val selected = File.createTempFile("legacy-restore-source-", ".zip", application.cacheDir)
        try {
            val original = JournalEntry(mood = Mood.CALM, note = ORIGINAL_NOTE)
            store.add(original)
            selected.outputStream().use { store.writeBackup(it, null) }
            store.update(original.copy(note = RESTORED_NOTE), emptyList())
            selected.inputStream().use {
                store.restoreBackup(it, null, mode = BackupRestoreMode.REPLACE)
            }
            val token = requireNotNull(dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
            val preferences =
                EncryptedSharedPreferences.create(
                    application,
                    "xike-restore",
                    MasterKey.Builder(application)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            val password = requireNotNull(preferences.getString(token, null))
            check(
                preferences
                    .edit()
                    .remove(token)
                    .putString("undo-file", token)
                    .putString("undo-password", password)
                    .commit()
            )

            assertTrue(store.canUndoLastRestore())
            val restored = store.undoLastRestore().single()
            assertEquals(RESTORED_NOTE, restored.note)
            assertTrue(undoFiles().isEmpty())
            store.delete(restored.id, refreshEntries = false)
            store.finalizeDelete(restored.id)
        } finally {
            selected.delete()
        }
    }

    @Test
    fun committedDraftIsClearedAfterProcessRestart(): Unit = runBlocking {
        assumeTrue(arguments.getString("commitRecoveryIsolated") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val drafts = JournalDraftStore(application)
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        fun openModel() =
            ViewModelProvider(
                owner,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application),
            )[JournalViewModel::class.java]

        try {
            when (arguments.getString("phase")) {
                "write" -> {
                    assertTrue(drafts.load().isEmpty)
                    val model = withContext(Dispatchers.Main) { openModel() }
                    await { !model.isLoading }
                    assertEquals(0, dao.entryCount())
                    val previousReceipt = dao.settingValue(SAVED_DRAFT_SETTING)
                    withContext(Dispatchers.Main) {
                        model.selectDraftMood(Mood.CALM)
                        model.updateDraftNote(DRAFT_NOTE)
                        launch(start = CoroutineStart.UNDISPATCHED) { model.saveDraft() }
                        // Hold the main thread at the commit boundary so it cannot clear the draft.
                        runBlocking(Dispatchers.IO) {
                            await { dao.settingValue(SAVED_DRAFT_SETTING) != previousReceipt }
                        }
                        owner.viewModelStore.clear()
                    }
                    assertEquals(DRAFT_NOTE, drafts.load().note)
                    assertEquals(store.entries().single().id, drafts.load().submissionId)
                }
                "read" -> {
                    assertFalse(drafts.hasPendingWrite)
                    val receipt = requireNotNull(drafts.load().submissionId)
                    assertTrue(store.hasSavedDraft(receipt))
                    val model = withContext(Dispatchers.Main) { openModel() }
                    await { !model.isLoading }
                    assertTrue(model.draft.isEmpty)
                    assertTrue(drafts.load().isEmpty)
                    assertEquals(receipt, store.entries().single().id)
                    withContext(Dispatchers.Main) {
                        model.selectDraftMood(Mood.CALM)
                        model.updateDraftNote(DRAFT_NOTE)
                        model.saveDraft().getOrThrow()
                    }
                    val entries = store.entries()
                    assertEquals(2, entries.size)
                    assertTrue(entries.all { it.note == DRAFT_NOTE })
                    entries.forEach {
                        store.delete(it.id, refreshEntries = false)
                        store.finalizeDelete(it.id)
                    }
                }
                else -> error("Specify phase=write or phase=read")
            }
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
        }
    }

    private fun undoFiles(): Set<String> =
        application.filesDir
            .listFiles()
            .orEmpty()
            .map { it.name }
            .filter { it.startsWith("journal-restore-undo-") }
            .toSet()

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("Timed out waiting for commit or recovery", condition())
    }

    private companion object {
        const val ORIGINAL_NOTE = "恢复前记录"
        const val RESTORED_NOTE = "备份中的记录"
        const val DRAFT_NOTE = "保存提交后恢复草稿"
    }
}
