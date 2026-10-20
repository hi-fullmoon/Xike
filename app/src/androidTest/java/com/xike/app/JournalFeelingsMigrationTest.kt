package com.xike.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class JournalFeelingsMigrationTest {
    @Test
    fun previewScaleIsMigratedWithoutReinterpretingLegacyEntries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "mood-scale-migration-${java.util.UUID.randomUUID()}"
        try {
            helper.createDatabase(name, 7).use { db ->
                db.execSQL(
                    "INSERT INTO journal_entries(id, created_at, mood, note, mood_scale_version) VALUES('legacy', 100, 'TIRED', '', 0), ('preview', 200, 'TIRED', '', 1)"
                )
            }
            helper.runMigrationsAndValidate(name, 8, true, JournalDatabase.MIGRATION_7_8).close()
            val database =
                androidx.room.Room.databaseBuilder(context, JournalDatabase::class.java, name)
                    .addMigrations(JournalDatabase.MIGRATION_8_9)
                    .allowMainThreadQueries()
                    .build()
            try {
                val db = database
                val dao = db.journalDao()
                assertEquals(
                    com.xike.app.data.Mood.TIRED,
                    dao.record("legacy")!!.toJournalEntry().mood,
                )
                assertEquals(
                    com.xike.app.data.Mood.BELOW_AVERAGE,
                    dao.record("preview")!!.toJournalEntry().mood,
                )
                val analytics = dao.analyticsRecords(0, 300).map { it.toEntry() }
                assertEquals(setOf("疲惫", "不太好"), analytics.map { it.mood.label }.toSet())
                kotlinx.coroutines.runBlocking {
                    val repo = JournalReadRepository(db)
                    assertEquals(
                        listOf("legacy"),
                        repo
                            .indexPage(
                                JournalSearchQuery(moods = setOf(com.xike.app.data.Mood.TIRED)),
                                0,
                            )
                            .entries
                            .map { it.id },
                    )
                    assertEquals(
                        listOf("preview"),
                        repo
                            .indexPage(
                                JournalSearchQuery(
                                    moods = setOf(com.xike.app.data.Mood.BELOW_AVERAGE)
                                ),
                                0,
                            )
                            .entries
                            .map { it.id },
                    )
                }
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            JournalDatabase::class.java,
        )

    @Test
    fun upgradePreservesOldMoodAndReopensNewColumns() {
        val name = "feelings-migration-${java.util.UUID.randomUUID()}"
        try {
            helper.createDatabase(name, 6).use { db ->
                db.execSQL(
                    "INSERT INTO journal_entries(id, created_at, mood, note) VALUES('old', 100, 'TIRED', '')"
                )
            }
            helper.runMigrationsAndValidate(name, 7, true, JournalDatabase.MIGRATION_6_7).use { db
                ->
                db.query(
                        "SELECT mood, mood_scale_version, feelings_json FROM journal_entries WHERE id='old'"
                    )
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("TIRED", cursor.getString(0))
                        assertEquals(0, cursor.getInt(1))
                        assertTrue(cursor.isNull(2))
                    }
                db.execSQL(
                    "UPDATE journal_entries SET feelings_json='[\"HOPEFUL\",\"ANXIOUS\"]', mood_scale_version=1 WHERE id='old'"
                )
            }
            helper.runMigrationsAndValidate(name, 7, true).use { db ->
                db.query(
                        "SELECT feelings_json, mood_scale_version FROM journal_entries WHERE id='old'"
                    )
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("[\"HOPEFUL\",\"ANXIOUS\"]", cursor.getString(0))
                        assertEquals(1, cursor.getInt(1))
                    }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
        }
    }
}
