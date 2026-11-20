package com.xike.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.feature.archive.MemoryKind
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class JournalFavoritesDataTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            JournalDatabase::class.java,
        )

    @Test
    fun upgradeKeepsContentAndReopensWithFavorites() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "favorites-migration-${UUID.randomUUID()}"
        try {
            helper.createDatabase(name, 10).use { db ->
                db.execSQL(
                    "INSERT INTO journal_entries(id, created_at, mood, note) VALUES('existing', 123, 'CALM', '')"
                )
            }
            helper.runMigrationsAndValidate(name, 11, true, JournalDatabase.MIGRATION_10_11).use {
                db ->
                db.query(
                        "SELECT created_at, is_favorite FROM journal_entries WHERE id = 'existing'"
                    )
                    .use {
                        assertTrue(it.moveToFirst())
                        assertEquals(123L, it.getLong(0))
                        assertEquals(0, it.getInt(1))
                    }
            }
            val first = Room.databaseBuilder(context, JournalDatabase::class.java, name).build()
            try {
                first.journalDao().setFavorite("existing", true)
            } finally {
                first.close()
            }
            val reopened = Room.databaseBuilder(context, JournalDatabase::class.java, name).build()
            try {
                assertTrue(reopened.journalDao().record("existing")!!.toJournalEntry().isFavorite)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun explicitFavoriteWritesAreAtomicAndStaleEditingKeepsLatestFavorite() = withDatabase { db ->
        val dao = db.journalDao()
        val original = JournalEntry(id = "one", createdAt = 1, mood = Mood.CALM, note = "")
        dao.insertJournal(original.toBundle())
        dao.putSetting(AppSettingEntity(RESTORE_UNDO_TOKEN_SETTING, "valid-token"))
        assertFalse(dao.setFavorite(original.id, false).isFavorite)
        assertEquals("valid-token", dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
        assertTrue(dao.setFavorite(original.id, true).isFavorite)
        assertEquals("", dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
        dao.updateJournal(original.copy(note = "updated").toBundle())
        assertTrue(dao.record(original.id)!!.toJournalEntry().isFavorite)
        assertEquals("updated", dao.record(original.id)!!.entry.note)
        dao.putSetting(AppSettingEntity(RESTORE_UNDO_TOKEN_SETTING, "still-valid"))
        dao.setFavorite(original.id, true)
        assertEquals("still-valid", dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
        assertThrows(IllegalStateException::class.java) { dao.setFavorite("missing", true) }
        assertEquals(1, dao.entryCount())
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_favorite BEFORE UPDATE OF is_favorite ON journal_entries BEGIN SELECT RAISE(ABORT, 'reject-favorite'); END"
        )
        try {
            assertTrue(runCatching { dao.setFavorite(original.id, false) }.isFailure)
            assertTrue(dao.record(original.id)!!.entry.isFavorite)
            assertEquals("still-valid", dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_favorite")
        }
        assertFalse(dao.setFavorite(original.id, false).isFavorite)
    }

    @Test
    fun favoritesHaveTheSameIndexAndCalendarResults() = withDatabase { db ->
        runBlocking {
            val dao = db.journalDao()
            val zone = ZoneId.systemDefault()
            val day = LocalDate.of(2026, 5, 4)
            val base =
                JournalEntry(
                    id = "favorite",
                    createdAt = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                    mood = Mood.CALM,
                    tags = listOf("工作"),
                    note = "",
                    isFavorite = true,
                )
            dao.insertJournal(base.toBundle())
            dao.insertJournal(base.copy(id = "ordinary", isFavorite = false).toBundle())
            dao.insertJournal(base.copy(id = "other-topic", tags = listOf("学习")).toBundle())
            val query = JournalSearchQuery(favoritesOnly = true, tags = setOf("工作"))
            val repository = JournalReadRepository(db)
            val page = repository.indexPage(query, 0)
            assertEquals(1, page.totalCount)
            assertEquals(listOf(base.id), page.entries.map { it.id })
            assertEquals(mapOf(day to 1), repository.calendar(query, java.time.YearMonth.from(day)))
            assertEquals(
                listOf(base.id),
                dao.recordsForQuery(repository.sql(query, "e.*", ordered = true)).map {
                    it.entry.id
                },
            )
        }
    }

    @Test
    fun memoriesSkipEmptyYearsAndUseExactLocalDateBoundaries() = withDatabase { db ->
        runBlocking {
            val dao = db.journalDao()
            val zone = ZoneId.of("America/New_York")
            val today = LocalDate.of(2024, 2, 29)
            fun add(id: String, day: LocalDate, minute: Long = 0) {
                dao.insertJournal(
                    JournalEntry(
                            id = id,
                            createdAt =
                                day.atStartOfDay(zone)
                                    .plusMinutes(minute)
                                    .toInstant()
                                    .toEpochMilli(),
                            mood = Mood.CALM,
                            note = "",
                        )
                        .toBundle()
                )
            }
            add("month-first", LocalDate.of(2024, 1, 29))
            add("month-last", LocalDate.of(2024, 1, 29), 1439)
            add("month-next", LocalDate.of(2024, 1, 30))
            add("leap-2020", LocalDate.of(2020, 2, 29))
            add("leap-2016", LocalDate.of(2016, 2, 29))
            add("not-leap", LocalDate.of(2023, 2, 28))
            add("old-unrelated", LocalDate.of(1900, 1, 1))
            val repository = JournalReadRepository(db)
            val result = repository.memories(today, zone)
            assertEquals(
                listOf(
                    LocalDate.of(2024, 1, 29),
                    LocalDate.of(2020, 2, 29),
                    LocalDate.of(2016, 2, 29),
                ),
                result.days.map { it.date },
            )
            assertEquals(listOf(2, 1, 1), result.days.map { it.count })
            assertEquals(MemoryKind.MONTH_AGO, result.days.first().kind)
            assertEquals(today, result.today)
            assertEquals(zone, result.zoneId)
            assertTrue(repository.memories(LocalDate.of(2025, 7, 8), zone).days.isEmpty())
        }
    }

    private fun withDatabase(block: (JournalDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
