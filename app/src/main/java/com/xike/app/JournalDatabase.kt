package com.xike.app

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

internal const val THEME_SETTING = "theme"
internal const val LEGACY_MIGRATION_SETTING = "legacy-shared-preferences-migrated-v1"

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

    @Query("SELECT COUNT(*) FROM journal_entries")
    abstract fun entryCount(): Int

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    abstract fun settingValue(key: String): String?

    @Query("SELECT file_name FROM journal_images")
    abstract fun imageFileNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertTags(tags: List<JournalTagEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertImages(images: List<JournalImageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putSetting(setting: AppSettingEntity)

    @Query("DELETE FROM journal_tags")
    protected abstract fun deleteTags()

    @Query("DELETE FROM journal_images")
    protected abstract fun deleteImages()

    @Query("DELETE FROM journal_entries")
    protected abstract fun deleteEntries()

    @Transaction
    open fun insertJournal(bundle: JournalBundle) {
        insertEntry(bundle.entry)
        if (bundle.tags.isNotEmpty()) insertTags(bundle.tags)
        if (bundle.images.isNotEmpty()) insertImages(bundle.images)
    }

    @Transaction
    open fun replaceJournals(bundles: List<JournalBundle>) {
        deleteTags()
        deleteImages()
        deleteEntries()
        bundles.forEach(::insertJournal)
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
    ],
    version = 1,
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
                .build()
        }
    }
}
