package com.xike.app

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JournalDatabase::class.java,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun removeDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationFrom1To2PreservesEntriesAndCreatesSearchTable() {
        helper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO journal_entries(id, created_at, mood, note)
                VALUES('existing', 100, 'GOOD', '迁移前的记录')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            JournalDatabase.MIGRATION_1_2,
        ).use { database ->
            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM journal_entries"))
            assertEquals(0, database.singleInt("SELECT COUNT(*) FROM journal_entries_fts"))
        }
    }

    private fun SupportSQLiteDatabase.singleInt(query: String): Int =
        this.query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE = "xike-migration-v1-v2"
    }
}
