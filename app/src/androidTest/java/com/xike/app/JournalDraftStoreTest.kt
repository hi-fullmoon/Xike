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
