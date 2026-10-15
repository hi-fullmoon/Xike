package com.xike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.JournalEntry
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run after saving a mood and feelings through the real UI on an isolated device. */
class BackupFeelingsProcessTest {
    @Test
    fun exportRestoreAndReadAcrossProcesses() {
        val phase = InstrumentationRegistry.getArguments().getString("backupFeelingsPhase")
        assumeTrue(phase in listOf("write", "restore", "verify"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context).also { it.initialize() }
        val archive = File(context.cacheDir, "feelings-process-backup.zip")
        if (phase == "write") {
            val source = store.entries().single()
            check(
                source.mood.isOverallScale && source.feelings.isNotEmpty() && source.note.isEmpty()
            )
            archive.outputStream().use { store.writeBackup(it, null) }
        }
        val parts =
            ZipFile(archive).use { zip ->
                val manifest =
                    zip.getInputStream(zip.getEntry("manifest.json")).use {
                        JSONObject(it.reader().readText())
                    }
                assertEquals(9, manifest.getInt("version"))
                val expected =
                    JournalEntry.fromJson(manifest.getJSONArray("entries").getJSONObject(0))
                val html = zip.getInputStream(zip.getEntry("index.html")).use { it.readBytes() }
                expected to html
            }
        val expected = parts.first
        when (phase) {
            "write" -> {
                File(context.getExternalFilesDir(null), "backup-reading.html")
                    .writeBytes(parts.second)
                File(context.getExternalFilesDir(null), "feelings-version9.zip")
                    .writeBytes(archive.readBytes())
                assertEquals(listOf(expected), store.entries())
            }
            "restore" -> {
                store.delete(expected.id)
                store.finalizeDelete(expected.id)
                assertTrue(store.entries().isEmpty())
                val restored =
                    archive.inputStream().use {
                        store.restoreBackup(it, null, mode = BackupRestoreMode.REPLACE)
                    }
                assertEquals(listOf(expected), restored)
            }
            "verify" -> {
                assertEquals(listOf(expected), store.entries())
                assertEquals(listOf("HOPEFUL", "ANXIOUS"), store.entries().single().feelings)
            }
        }
    }
}
