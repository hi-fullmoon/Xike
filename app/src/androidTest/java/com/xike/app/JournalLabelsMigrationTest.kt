package com.xike.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.legacyTopicSelection
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class JournalLabelsMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            JournalDatabase::class.java,
        )

    @Test
    fun upgradePreservesLegacyAliasesAndNamesAcrossReopen() {
        val name = "labels-migration-${UUID.randomUUID()}"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            helper.createDatabase(name, 9).use { db ->
                db.execSQL(
                    "INSERT INTO journal_entries(id, created_at, mood, note) VALUES ('existing', 1, 'CALM', '')"
                )
                listOf("运动", "身体", "个人项目").forEachIndexed { index, tag ->
                    db.execSQL(
                        "INSERT INTO journal_tags(entry_id, position, tag) VALUES ('existing', ?, ?)",
                        arrayOf<Any>(index, tag),
                    )
                }
            }
            helper.runMigrationsAndValidate(name, 10, true, JournalDatabase.MIGRATION_9_10).close()
            helper.runMigrationsAndValidate(name, 10, true).use { db ->
                db.query(
                        "SELECT COUNT(*) FROM journal_labels WHERE kind = 'TOPIC' AND is_archived = 0 AND is_builtin = 1"
                    )
                    .use {
                        assertTrue(it.moveToFirst())
                        assertEquals(13, it.getInt(0))
                    }
                db.query("SELECT tag, label_id, label_kind FROM journal_tags ORDER BY position")
                    .use { cursor ->
                        val names = mutableListOf<String>()
                        val ids = mutableListOf<String>()
                        while (cursor.moveToNext()) {
                            names += cursor.getString(0)
                            ids += cursor.getString(1)
                            assertEquals("TOPIC", cursor.getString(2))
                        }
                        assertEquals(listOf("运动", "身体", "个人项目"), names)
                        assertEquals(ids[0], ids[1])
                        assertEquals(legacyTopicSelection("个人项目").id, ids[2])
                    }
                db.query(
                        "SELECT COUNT(*) FROM journal_tags t LEFT JOIN journal_labels l ON l.id = t.label_id WHERE l.id IS NULL"
                    )
                    .use {
                        assertTrue(it.moveToFirst())
                        assertEquals(0, it.getInt(0))
                    }
            }
            val database =
                androidx.room.Room.databaseBuilder(context, JournalDatabase::class.java, name)
                    .build()
            try {
                val dao = database.journalDao()
                dao.ensureLabelCatalog()
                assertEquals(
                    13,
                    dao.labels().count { it.kind == "TOPIC" && it.isBuiltin && !it.isArchived },
                )
                val work = dao.labels().first { it.name == "工作" && it.kind == "TOPIC" }
                dao.saveLabel(work.copy(isArchived = true, sortOrder = 30))
                dao.ensureLabelCatalog()
                assertEquals(
                    work.copy(isArchived = true, sortOrder = 30),
                    dao.labels().first { it.id == work.id },
                )
                assertFalse(dao.labels().first { it.name == "家庭" && it.kind == "TOPIC" }.isArchived)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
