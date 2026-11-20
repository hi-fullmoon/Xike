package com.xike.app

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            JournalDatabase::class.java,
        )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun removeDatabase() {
        context.deleteDatabase(TEST_DATABASE)
        context.deleteDatabase(TEST_DATABASE_V2)
        context.deleteDatabase(TEST_DATABASE_V3)
        context.deleteDatabase(TEST_DATABASE_V4)
    }

    @Test
    fun migrationFrom1To2PreservesEntriesAndCreatesSearchTable() {
        helper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO journal_entries(id, created_at, mood, note)
                VALUES('existing', 100, 'GOOD', '迁移前的记录')
                """
                    .trimIndent()
            )
        }

        helper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                2,
                true,
                JournalDatabase.MIGRATION_1_2,
            )
            .use { database ->
                assertEquals(1, database.singleInt("SELECT COUNT(*) FROM journal_entries"))
                assertEquals(0, database.singleInt("SELECT COUNT(*) FROM journal_entries_fts"))
            }
    }

    @Test
    fun migrationFrom2To3PreservesEntriesAndAddsNullableOutdoorColumns() {
        helper.createDatabase(TEST_DATABASE_V2, 2).use { database ->
            database.execSQL(
                """
                INSERT INTO journal_entries(id, created_at, mood, note)
                VALUES('existing-v2', 200, 'CALM', '升级前的记录')
                """
                    .trimIndent()
            )
        }

        helper
            .runMigrationsAndValidate(
                TEST_DATABASE_V2,
                3,
                true,
                JournalDatabase.MIGRATION_2_3,
            )
            .use { database ->
                assertEquals(1, database.singleInt("SELECT COUNT(*) FROM journal_entries"))
                assertEquals(
                    1,
                    database.singleInt(
                        "SELECT COUNT(*) FROM journal_entries WHERE outdoor_place_name IS NULL"
                    ),
                )
            }
    }

    @Test
    fun migrationFrom3To4PreservesEntriesAndCreatesAudioTable() {
        helper.createDatabase(TEST_DATABASE_V3, 3).use { database ->
            database.execSQL(
                """
                INSERT INTO journal_entries(
                    id, created_at, mood, note, outdoor_place_name,
                    outdoor_temperature_celsius, outdoor_weather_code,
                    outdoor_captured_at, outdoor_source
                ) VALUES('existing-v3', 300, 'CALM', '升级前的记录', NULL, NULL, NULL, NULL, NULL)
                """
                    .trimIndent()
            )
        }

        helper
            .runMigrationsAndValidate(
                TEST_DATABASE_V3,
                4,
                true,
                JournalDatabase.MIGRATION_3_4,
            )
            .use { database ->
                assertEquals(1, database.singleInt("SELECT COUNT(*) FROM journal_entries"))
                assertEquals(0, database.singleInt("SELECT COUNT(*) FROM journal_audios"))
            }
    }

    private fun SupportSQLiteDatabase.singleInt(query: String): Int =
        this.query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    @Test
    fun migrationFrom4To5PreservesEntriesAndAudio() {
        helper.createDatabase(TEST_DATABASE_V4, 4).use { db ->
            db.execSQL(
                "INSERT INTO journal_entries(id, created_at, mood, note) VALUES('existing-v4', 400, 'CALM', '')"
            )
            db.execSQL(
                "INSERT INTO journal_audios(entry_id, file_name, duration_millis, mime_type) VALUES('existing-v4', 'existing.m4a', 1000, 'audio/mp4')"
            )
        }
        helper
            .runMigrationsAndValidate(TEST_DATABASE_V4, 5, true, JournalDatabase.MIGRATION_4_5)
            .use { db ->
                assertEquals(
                    1,
                    db.singleInt("SELECT COUNT(*) FROM journal_entries WHERE video_json IS NULL"),
                )
                assertEquals(1, db.singleInt("SELECT COUNT(*) FROM journal_audios"))
            }
    }

    private companion object {
        const val TEST_DATABASE = "xike-migration-v1-v2"
        const val TEST_DATABASE_V2 = "xike-migration-v2-v3"
        const val TEST_DATABASE_V3 = "xike-migration-v3-v4"
        const val TEST_DATABASE_V4 = "xike-migration-v4-v5"
    }
}

@RunWith(AndroidJUnit4::class)
class OutdoorAddressMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            JournalDatabase::class.java,
        )
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun migrationFrom5To6AddsNullableFullAddressAndPreservesExistingSchema() {
        val databaseName = "xike-address-migration-${java.util.UUID.randomUUID()}"
        try {
            val originalColumns =
                helper.createDatabase(databaseName, 5).use { db ->
                    db.query("PRAGMA table_info(journal_entries)").use { cursor ->
                        buildSet {
                            while (cursor.moveToNext()) add(
                                cursor.getString(cursor.getColumnIndexOrThrow("name"))
                            )
                        }
                    }
                }
            helper
                .runMigrationsAndValidate(databaseName, 6, true, JournalDatabase.MIGRATION_5_6)
                .use { db ->
                    val columns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(journal_entries)").use { cursor ->
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                            columns.add(name)
                            if (name == "outdoor_full_place_name") {
                                assertEquals(
                                    "TEXT",
                                    cursor.getString(cursor.getColumnIndexOrThrow("type")),
                                )
                                assertEquals(
                                    0,
                                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull")),
                                )
                                assertNull(
                                    cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                                )
                            }
                        }
                    }
                    assertEquals(originalColumns + "outdoor_full_place_name", columns)
                    assertTrue("outdoor_full_place_name" in columns)
                }
            // Reopen the migrated file and validate the persisted schema again.
            helper.runMigrationsAndValidate(databaseName, 6, true).close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationFrom1To6ValidatesTheCompleteUpgradePath() {
        val databaseName = "xike-address-upgrade-${java.util.UUID.randomUUID()}"
        try {
            helper.createDatabase(databaseName, 1).close()
            helper
                .runMigrationsAndValidate(
                    databaseName,
                    6,
                    true,
                    JournalDatabase.MIGRATION_1_2,
                    JournalDatabase.MIGRATION_2_3,
                    JournalDatabase.MIGRATION_3_4,
                    JournalDatabase.MIGRATION_4_5,
                    JournalDatabase.MIGRATION_5_6,
                )
                .close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
