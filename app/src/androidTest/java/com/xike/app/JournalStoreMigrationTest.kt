package com.xike.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalStoreMigrationTest {
    @Test
    fun encryptedSharedPreferencesAreMigratedWithoutBeingDeleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = JournalDatabase.get(context)
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val legacyPreferences = EncryptedSharedPreferences.create(
            context,
            "xike-journal",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        database.clearAllTables()
        legacyPreferences.edit().clear().commit()
        val legacyEntry = JournalEntry(
            id = "legacy-four-images",
            createdAt = 1_777_777L,
            mood = Mood.JOYFUL,
            tags = listOf("生活", "旅行"),
            note = "旧版加密存储",
            imageFileNames = (1..4).map { "legacy-$it.xike-image" },
        )
        assertTrue(
            legacyPreferences.edit()
                .putString("entries", JSONArray(listOf(legacyEntry.toJson())).toString())
                .putString("theme", "SUNSET")
                .commit(),
        )

        try {
            val firstSnapshot = JournalStore(context).initialize()
            val secondSnapshot = JournalStore(context).initialize()

            assertEquals(listOf(legacyEntry), firstSnapshot.entries)
            assertEquals(firstSnapshot, secondSnapshot)
            assertEquals("SUNSET", firstSnapshot.themeName)
            assertEquals(4, firstSnapshot.entries.single().imageFileNames.size)
            assertTrue(legacyPreferences.contains("entries"))
            assertTrue(legacyPreferences.contains("theme"))
        } finally {
            database.clearAllTables()
            legacyPreferences.edit().clear().commit()
        }
    }
}
