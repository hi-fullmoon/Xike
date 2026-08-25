package com.xike.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class JournalDaoTest {
    private lateinit var database: JournalDatabase
    private lateinit var dao: JournalDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.journalDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun entryRelationsRoundTripInOriginalOrder() {
        val older = entry("older", 100L, listOf("生活"), listOf("old.xike-image"))
        val newer = entry(
            "newer",
            200L,
            listOf("工作", "运动", "阅读"),
            listOf("first.xike-image", "second.xike-image"),
        )

        dao.insertJournal(older.toBundle())
        dao.insertJournal(newer.toBundle())

        val stored = dao.records().map(JournalEntryRecord::toJournalEntry)
        assertEquals(listOf("newer", "older"), stored.map { it.id })
        assertEquals(newer.tags, stored.first().tags)
        assertEquals(newer.imageFileNames, stored.first().imageFileNames)
    }

    @Test
    fun replaceIsCompleteAndKeepsSettings() {
        dao.insertJournal(entry("old", 100L).toBundle())
        dao.putSetting(AppSettingEntity(THEME_SETTING, "FOREST"))

        val replacement = entry("replacement", 300L, listOf("新的"), listOf("new.xike-image"))
        dao.replaceJournals(listOf(replacement.toBundle()))

        assertEquals(listOf("replacement"), dao.records().map { it.entry.id })
        assertEquals(listOf("new.xike-image"), dao.imageFileNames())
        assertEquals("FOREST", dao.settingValue(THEME_SETTING))
    }

    @Test
    fun legacyImportRunsOnlyOnce() {
        dao.importLegacyIfNeeded(listOf(entry("legacy", 100L).toBundle()), "SUNSET")
        dao.importLegacyIfNeeded(listOf(entry("ignored", 200L).toBundle()), "FOREST")

        assertEquals(listOf("legacy"), dao.records().map { it.entry.id })
        assertEquals("SUNSET", dao.settingValue(THEME_SETTING))
        assertEquals("1", dao.settingValue(LEGACY_MIGRATION_SETTING))
    }

    @Test
    fun legacyImportNeverOverwritesExistingDatabaseEntries() {
        dao.insertJournal(entry("existing", 300L).toBundle())

        dao.importLegacyIfNeeded(listOf(entry("legacy", 100L).toBundle()), null)

        assertEquals(listOf("existing"), dao.records().map { it.entry.id })
        assertEquals("1", dao.settingValue(LEGACY_MIGRATION_SETTING))
    }

    @Test
    fun searchCombinesFtsMoodTagDateAndImageFilters() {
        val matching = entry("matching", 300L, listOf("工作"), listOf("photo.xike-image"))
            .copy(note = "今天工作顺利", mood = Mood.GOOD)
        dao.insertJournal(matching.toBundle())
        dao.insertJournal(entry("other", 200L, listOf("睡眠")).copy(note = "早点休息").toBundle())

        val results = dao.searchRecords(
            hasText = 1,
            ftsQuery = journalFtsQuery("工作"),
            startInclusive = 250L,
            endExclusive = 400L,
            filterMoods = 1,
            moods = listOf(Mood.GOOD.name),
            filterTags = 1,
            tags = listOf("工作"),
            imageFilter = JournalImageFilter.WITH_IMAGES.name,
            limit = 20,
            offset = 0,
        ).map(JournalEntryRecord::toJournalEntry)

        assertEquals(listOf("matching"), results.map { it.id })
        assertEquals(
            1,
            dao.searchRecordCount(
                hasText = 1,
                ftsQuery = journalFtsQuery("工作"),
                startInclusive = 250L,
                endExclusive = 400L,
                filterMoods = 1,
                moods = listOf(Mood.GOOD.name),
                filterTags = 1,
                tags = listOf("工作"),
                imageFilter = JournalImageFilter.WITH_IMAGES.name,
            ),
        )
    }

    @Test
    fun observedRecordsEmitDatabaseChangesInDisplayOrder() = runBlocking {
        dao.insertJournal(entry("older", 100L).toBundle())
        dao.insertJournal(entry("newer", 200L).toBundle())

        val observed = dao.observeRecords().first().map(JournalEntryRecord::toJournalEntry)

        assertEquals(listOf("newer", "older"), observed.map { it.id })
    }

    @Test
    fun updateReplacesRelationsAndRefreshesSearchIndexInOneTransaction() {
        val original = entry(
            id = "editable",
            createdAt = 100L,
            tags = listOf("旧关键词"),
            images = listOf("old.xike-image"),
        ).copy(note = "旧注脚", mood = Mood.TIRED)
        dao.insertJournal(original.toBundle())
        val updated = original.copy(
            createdAt = 500L,
            mood = Mood.JOYFUL,
            tags = listOf("工作", "创作"),
            note = "新的可搜索注脚",
            imageFileNames = listOf("new.xike-image"),
            outdoor = OutdoorSnapshot("上海 · 浦东", 27.2, 2, 450L),
        )

        val previousImages = dao.updateJournal(updated.toBundle())
        val stored = dao.record(updated.id)?.toJournalEntry()
        val searchResults = dao.searchRecords(
            hasText = 1,
            ftsQuery = journalFtsQuery("工作"),
            startInclusive = 0L,
            endExclusive = 1_000L,
            filterMoods = 0,
            moods = listOf(Mood.CALM.name),
            filterTags = 0,
            tags = listOf(""),
            imageFilter = JournalImageFilter.ANY.name,
            limit = 20,
            offset = 0,
        ).map(JournalEntryRecord::toJournalEntry)

        assertEquals(listOf("old.xike-image"), previousImages)
        assertEquals(updated, stored)
        assertEquals(listOf(updated.id), searchResults.map(JournalEntry::id))
    }

    private fun entry(
        id: String,
        createdAt: Long,
        tags: List<String> = emptyList(),
        images: List<String> = emptyList(),
    ) = JournalEntry(
        id = id,
        createdAt = createdAt,
        mood = Mood.GOOD,
        tags = tags,
        note = "note-$id",
        imageFileNames = images,
    )
}
