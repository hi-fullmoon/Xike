package com.xike.app

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

internal const val THEME_SETTING = "theme"
internal const val LEGACY_MIGRATION_SETTING = "legacy-shared-preferences-migrated-v1"
internal const val SEARCH_INDEX_SETTING = "journal-search-index-version"
internal const val SEARCH_INDEX_VERSION = "1"

@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["created_at"])],
)
internal data class JournalEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val mood: String,
    val note: String,
)

@Entity(
    tableName = "journal_tags",
    primaryKeys = ["entry_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["entry_id"])],
)
internal data class JournalTagEntity(
    @ColumnInfo(name = "entry_id") val entryId: String,
    val position: Int,
    val tag: String,
)

@Entity(
    tableName = "journal_images",
    primaryKeys = ["entry_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["entry_id"])],
)
internal data class JournalImageEntity(
    @ColumnInfo(name = "entry_id") val entryId: String,
    val position: Int,
    @ColumnInfo(name = "file_name") val fileName: String,
)

@Entity(tableName = "app_settings")
internal data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "journal_entries_fts")
internal data class JournalSearchEntity(
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "searchable_text") val searchableText: String,
)

internal data class JournalEntryRecord(
    @Embedded val entry: JournalEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entry_id")
    val tags: List<JournalTagEntity>,
    @Relation(parentColumn = "id", entityColumn = "entry_id")
    val images: List<JournalImageEntity>,
)

internal data class JournalBundle(
    val entry: JournalEntryEntity,
    val tags: List<JournalTagEntity>,
    val images: List<JournalImageEntity>,
)

internal fun JournalBundle.toSearchEntity(): JournalSearchEntity = JournalSearchEntity(
    entryId = entry.id,
    searchableText = journalSearchDocument(entry.note, tags.sortedBy { it.position }.map { it.tag }),
)

internal fun JournalEntry.toBundle(): JournalBundle = JournalBundle(
    entry = JournalEntryEntity(id, createdAt, mood.name, note),
    tags = tags.mapIndexed { position, tag -> JournalTagEntity(id, position, tag) },
    images = imageFileNames.mapIndexed { position, fileName -> JournalImageEntity(id, position, fileName) },
)

internal fun JournalEntryRecord.toJournalEntry(): JournalEntry = JournalEntry(
    id = entry.id,
    createdAt = entry.createdAt,
    mood = Mood.fromName(entry.mood),
    tags = tags.sortedBy { it.position }.map { it.tag },
    note = entry.note,
    imageFileNames = images.sortedBy { it.position }.map { it.fileName },
)

@Dao
internal abstract class JournalDao {
    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY created_at DESC")
    abstract fun records(): List<JournalEntryRecord>

    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY created_at DESC")
    abstract fun observeRecords(): Flow<List<JournalEntryRecord>>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id = :entryId LIMIT 1")
    abstract fun record(entryId: String): JournalEntryRecord?

    @Query("SELECT COUNT(*) FROM journal_entries")
    abstract fun entryCount(): Int

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    abstract fun settingValue(key: String): String?

    @Query("SELECT file_name FROM journal_images")
    abstract fun imageFileNames(): List<String>

    @Query("SELECT file_name FROM journal_images WHERE entry_id = :entryId ORDER BY position")
    protected abstract fun imageFileNames(entryId: String): List<String>

    @Transaction
    @Query(
        """
        SELECT e.* FROM journal_entries AS e
        WHERE (:hasText = 0 OR e.id IN (
            SELECT entry_id FROM journal_entries_fts
            WHERE journal_entries_fts MATCH :ftsQuery
        ))
        AND e.created_at >= :startInclusive
        AND e.created_at < :endExclusive
        AND (:filterMoods = 0 OR e.mood IN (:moods))
        AND (:filterTags = 0 OR EXISTS (
            SELECT 1 FROM journal_tags AS selected_tags
            WHERE selected_tags.entry_id = e.id AND selected_tags.tag IN (:tags)
        ))
        AND (
            :imageFilter = 'ANY'
            OR (:imageFilter = 'WITH_IMAGES' AND EXISTS (
                SELECT 1 FROM journal_images AS selected_images WHERE selected_images.entry_id = e.id
            ))
            OR (:imageFilter = 'WITHOUT_IMAGES' AND NOT EXISTS (
                SELECT 1 FROM journal_images AS selected_images WHERE selected_images.entry_id = e.id
            ))
        )
        ORDER BY e.created_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    abstract fun searchRecords(
        hasText: Int,
        ftsQuery: String,
        startInclusive: Long,
        endExclusive: Long,
        filterMoods: Int,
        moods: List<String>,
        filterTags: Int,
        tags: List<String>,
        imageFilter: String,
        limit: Int,
        offset: Int,
    ): List<JournalEntryRecord>

    @Query(
        """
        SELECT COUNT(*) FROM journal_entries AS e
        WHERE (:hasText = 0 OR e.id IN (
            SELECT entry_id FROM journal_entries_fts
            WHERE journal_entries_fts MATCH :ftsQuery
        ))
        AND e.created_at >= :startInclusive
        AND e.created_at < :endExclusive
        AND (:filterMoods = 0 OR e.mood IN (:moods))
        AND (:filterTags = 0 OR EXISTS (
            SELECT 1 FROM journal_tags AS selected_tags
            WHERE selected_tags.entry_id = e.id AND selected_tags.tag IN (:tags)
        ))
        AND (
            :imageFilter = 'ANY'
            OR (:imageFilter = 'WITH_IMAGES' AND EXISTS (
                SELECT 1 FROM journal_images AS selected_images WHERE selected_images.entry_id = e.id
            ))
            OR (:imageFilter = 'WITHOUT_IMAGES' AND NOT EXISTS (
                SELECT 1 FROM journal_images AS selected_images WHERE selected_images.entry_id = e.id
            ))
        )
        """,
    )
    abstract fun searchRecordCount(
        hasText: Int,
        ftsQuery: String,
        startInclusive: Long,
        endExclusive: Long,
        filterMoods: Int,
        moods: List<String>,
        filterTags: Int,
        tags: List<String>,
        imageFilter: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertTags(tags: List<JournalTagEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertImages(images: List<JournalImageEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertSearchEntry(search: JournalSearchEntity)

    @Update
    protected abstract fun updateEntry(entry: JournalEntryEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putSetting(setting: AppSettingEntity)

    @Query("DELETE FROM journal_tags")
    protected abstract fun deleteTags()

    @Query("DELETE FROM journal_images")
    protected abstract fun deleteImages()

    @Query("DELETE FROM journal_entries")
    protected abstract fun deleteEntries()

    @Query("DELETE FROM journal_entries_fts")
    protected abstract fun deleteSearchEntries()

    @Query("DELETE FROM journal_entries_fts WHERE entry_id = :entryId")
    protected abstract fun deleteSearchEntry(entryId: String)

    @Query("DELETE FROM journal_tags WHERE entry_id = :entryId")
    protected abstract fun deleteEntryTags(entryId: String)

    @Query("DELETE FROM journal_images WHERE entry_id = :entryId")
    protected abstract fun deleteEntryImages(entryId: String)

    @Query("DELETE FROM journal_entries WHERE id = :entryId")
    protected abstract fun deleteEntry(entryId: String): Int

    @Transaction
    open fun insertJournal(bundle: JournalBundle) {
        insertEntry(bundle.entry)
        if (bundle.tags.isNotEmpty()) insertTags(bundle.tags)
        if (bundle.images.isNotEmpty()) insertImages(bundle.images)
        insertSearchEntry(bundle.toSearchEntity())
    }

    @Transaction
    open fun deleteJournal(entryId: String): List<String> {
        val images = imageFileNames(entryId)
        deleteSearchEntry(entryId)
        check(deleteEntry(entryId) == 1) { "记录不存在或已经删除。" }
        return images
    }

    @Transaction
    open fun updateJournal(bundle: JournalBundle): List<String> {
        val previousImages = imageFileNames(bundle.entry.id)
        check(updateEntry(bundle.entry) == 1) { "记录不存在或已经删除。" }
        deleteEntryTags(bundle.entry.id)
        deleteEntryImages(bundle.entry.id)
        deleteSearchEntry(bundle.entry.id)
        if (bundle.tags.isNotEmpty()) insertTags(bundle.tags)
        if (bundle.images.isNotEmpty()) insertImages(bundle.images)
        insertSearchEntry(bundle.toSearchEntity())
        return previousImages
    }

    @Transaction
    open fun replaceJournals(bundles: List<JournalBundle>) {
        deleteSearchEntries()
        deleteTags()
        deleteImages()
        deleteEntries()
        bundles.forEach(::insertJournal)
    }

    @Transaction
    open fun rebuildSearchIndex(bundles: List<JournalBundle>) {
        deleteSearchEntries()
        bundles.forEach { insertSearchEntry(it.toSearchEntity()) }
        putSetting(AppSettingEntity(SEARCH_INDEX_SETTING, SEARCH_INDEX_VERSION))
    }

    @Transaction
    open fun importLegacyIfNeeded(bundles: List<JournalBundle>, themeName: String?) {
        if (settingValue(LEGACY_MIGRATION_SETTING) != null) return
        if (entryCount() == 0) bundles.forEach(::insertJournal)
        if (themeName != null && settingValue(THEME_SETTING) == null) {
            putSetting(AppSettingEntity(THEME_SETTING, themeName))
        }
        putSetting(AppSettingEntity(LEGACY_MIGRATION_SETTING, "1"))
    }
}

@Database(
    entities = [
        JournalEntryEntity::class,
        JournalTagEntity::class,
        JournalImageEntity::class,
        AppSettingEntity::class,
        JournalSearchEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
internal abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        const val DATABASE_NAME = "xike-journal.db"

        @Volatile
        private var instance: JournalDatabase? = null

        fun get(context: Context): JournalDatabase = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context): JournalDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(DatabaseKeyManager(context).getOrCreatePassphrase())
            return Room.databaseBuilder(context, JournalDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `journal_entries_fts`
                    USING FTS4(`entry_id` TEXT NOT NULL, `searchable_text` TEXT NOT NULL, tokenize=unicode61)
                    """.trimIndent(),
                )
            }
        }
    }
}
