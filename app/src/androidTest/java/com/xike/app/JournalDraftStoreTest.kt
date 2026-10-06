package com.xike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalDraftStoreTest {
    @Test
    fun newSessionInheritsQueuedInputBeforeItIsWrittenToDisk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = JournalDraftStore(context).load()
        val oldStore = JournalDraftStore(context)
        val latest = JournalDraft(note = "走完后心情平静")
        try {
            oldStore.save(JournalDraft())
            val queuedRevision = oldStore.queue(latest)
            val newStore = JournalDraftStore(context)
            newStore.nextRevision()
            assertEquals(latest, newStore.load())
            assertFalse(oldStore.saveIfCurrent(latest, queuedRevision))
            newStore.flushPending()
            assertEquals(latest, JournalDraftStore(context).load())
        } finally {
            JournalDraftStore(context).save(original)
        }
    }

    @Test
    fun retiredWriterCannotOverwriteNewSessionEvenAfterRequestingAnotherRevision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = JournalDraftStore(context).load()
        val oldStore = JournalDraftStore(context)
        val oldDraft = JournalDraft(note = "今天走了一段路")
        val oldRevision = oldStore.nextRevision()
        val newStore = JournalDraftStore(context)
        val latest = oldDraft.copy(note = "走完后心情平静")
        try {
            newStore.save(latest)
            assertFalse(oldStore.saveIfCurrent(oldDraft, oldRevision))
            assertFalse(oldStore.saveIfCurrent(oldDraft, oldStore.nextRevision()))
            assertEquals(latest, JournalDraftStore(context).load())
        } finally {
            JournalDraftStore(context).save(original)
        }
    }

    @Test
    fun olderQueuedWriteCannotOverwriteNewerDraftOrRestoreClearedDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalDraftStore(context)
        val original = store.load()
        try {
            val earlier = JournalDraft(note = "今天走了一段路")
            val earlierRevision = store.nextRevision()
            val latest = earlier.copy(note = "走完后心情平静")
            val latestRevision = store.nextRevision()
            store.saveIfCurrent(latest, latestRevision)
            store.saveIfCurrent(earlier, earlierRevision)
            assertEquals(latest, JournalDraftStore(context).load())

            store.save(JournalDraft())
            store.saveIfCurrent(latest, latestRevision)
            assertTrue(JournalDraftStore(context).load().isEmpty)
        } finally {
            store.save(original)
        }
    }

    @Test
    fun encryptedDraftSurvivesStoreRecreationWithoutPlaintextOnDisk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalDraftStore(context)
        val secretNote = "只属于我的草稿-987654"
        val draft = JournalDraft(
            mood = Mood.CALM,
            note = secretNote,
            tags = setOf("自我"),
            updatedAt = 987_654L,
        )
        store.save(JournalDraft())

        try {
            store.save(draft)

            assertEquals(draft, JournalDraftStore(context).load())
            val preferencesFile = File(
                context.applicationInfo.dataDir,
                "shared_prefs/xike-journal-draft.xml",
            )
            assertTrue(preferencesFile.isFile)
            assertFalse(preferencesFile.readText().contains(secretNote))
        } finally {
            store.save(JournalDraft())
        }
    }
}
