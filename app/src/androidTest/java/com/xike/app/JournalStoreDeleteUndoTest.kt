package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalStoreDeleteUndoTest {
    @Test
    fun deleteKeepsEncryptedPhotosUntilUndoWindowIsFinalized() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = JournalDatabase.get(context)
        val store = JournalStore(context)
        val source = File(context.cacheDir, "undo-source-${UUID.randomUUID()}.bin")
        source.writeBytes("encrypted photo lifecycle".toByteArray())
        database.clearAllTables()
        store.removeOrphanedImages()
        val entry = JournalEntry(
            id = "undo-${UUID.randomUUID()}",
            mood = Mood.CALM,
            tags = listOf("自我"),
            note = "可撤销的记录",
        )

        try {
            val stored = store.add(entry, listOf(Uri.fromFile(source))).single()
            val imageFileName = stored.imageFileNames.single()

            assertEquals(emptyList<JournalEntry>(), store.delete(stored.id))
            store.openImage(imageFileName).use { assertNotNull(it) }

            assertEquals(listOf(stored), store.undoDelete(stored.id))
            store.openImage(imageFileName).use { assertNotNull(it) }

            store.delete(stored.id)
            store.finalizeDelete(stored.id)

            assertNull(store.openImage(imageFileName))
            assertThrows(JournalDataException::class.java) { store.undoDelete(stored.id) }
        } finally {
            database.clearAllTables()
            store.removeOrphanedImages()
            source.delete()
        }
    }
}
