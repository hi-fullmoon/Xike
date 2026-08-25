package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalStoreBackupTest {
    private lateinit var context: Context
    private lateinit var store: JournalStore
    private lateinit var dao: JournalDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = JournalStore(context)
        store.initialize()
        dao = JournalDatabase.get(context).journalDao()
        clearStoredData()
    }

    @After
    fun tearDown() {
        clearStoredData()
    }

    @Test
    fun backupWithMultipleImagesCanBeInspectedRestoredAndUndone() {
        val imageContents = listOf(
            "first image".toByteArray(),
            ByteArray(32 * 1024) { index -> (index % 251).toByte() },
            "third image".toByteArray(),
        )
        val source = entry("source", 1_700_000_000_000L, "含多图的备份")
        val storedSource = store.add(source, imageUris(imageContents)).single()
        val backup = encryptedBackup()

        replaceWithLocalEntry()

        val summary = store.inspectEncryptedBackup(backup.inputStream(), PASSWORD)
        assertEquals(1, summary.entryCount)
        assertEquals(3, summary.imageCount)
        assertEquals(source.createdAt, summary.oldestCreatedAt)
        assertEquals(source.createdAt, summary.newestCreatedAt)
        assertEquals(listOf("local"), store.entries().map { it.id })

        val restored = store.restoreEncryptedBackup(backup.inputStream(), PASSWORD)
        assertEquals(listOf(storedSource.id), restored.map { it.id })
        assertEquals(imageContents.size, restored.single().imageFileNames.size)
        assertImageContents(restored.single(), imageContents)
        assertTrue(store.canUndoLastRestore())

        val undone = store.undoLastRestore()
        assertEquals(listOf("local"), undone.map { it.id })
        assertEquals("设备上的原记录", undone.single().note)
        assertFalse(store.canUndoLastRestore())
    }

    @Test
    fun wrongPasswordDoesNotChangeExistingData() {
        store.add(entry("source", 100L, "备份内容"))
        val backup = encryptedBackup()
        replaceWithLocalEntry()

        assertRestoreFailsWithoutChangingData(backup, "wrong-password")
    }

    @Test
    fun truncatedBackupDoesNotChangeExistingData() {
        store.add(entry("source", 100L, "备份内容"))
        val backup = encryptedBackup()
        replaceWithLocalEntry()
        val truncated = backup.copyOf(backup.size - 8)

        assertRestoreFailsWithoutChangingData(truncated, PASSWORD)
    }

    @Test
    fun oversizedImageDoesNotChangeExistingData() {
        replaceWithLocalEntry()
        val backup = backupWithOversizedImage()

        assertRestoreFailsWithoutChangingData(backup, PASSWORD)
    }

    @Test
    fun finalizedDeleteRemovesRecordSearchIndexAndPrivateImageCopy() {
        val stored = store.add(
            entry("delete-me", 300L, "待删除标记"),
            imageUris(listOf("private image".toByteArray())),
        ).single()
        val imageFileName = stored.imageFileNames.single()

        store.openImage(imageFileName).use { input -> assertNotNull(input) }
        assertEquals(1, store.search(JournalSearchQuery(text = "待删除标记")).totalCount)

        val remaining = store.delete(stored.id)

        assertTrue(remaining.isEmpty())
        assertTrue(store.entries().isEmpty())
        assertEquals(0, store.search(JournalSearchQuery(text = "待删除标记")).totalCount)
        store.openImage(imageFileName).use { input -> assertNotNull(input) }

        store.finalizeDelete(stored.id)

        assertNull(store.openImage(imageFileName))
    }

    private fun replaceWithLocalEntry() {
        dao.replaceJournals(emptyList())
        journalImagesDirectory().listFiles()?.forEach(File::delete)
        store.add(entry("local", 200L, "设备上的原记录"))
    }

    private fun encryptedBackup(): ByteArray = ByteArrayOutputStream().also { output ->
        store.writeEncryptedBackup(output, PASSWORD)
    }.toByteArray()

    private fun backupWithOversizedImage(): ByteArray {
        val fileName = "oversized.xike-image"
        val manifest = JSONObject()
            .put("format", "xike")
            .put("version", 4)
            .put(
                "entries",
                JSONArray(
                    listOf(
                        entry("oversized", 300L, "超限图片")
                            .copy(imageFileNames = listOf(fileName))
                            .toJson(),
                    ),
                ),
            )
            .toString()
        val output = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(BackupCipher.encryptingStream(output, PASSWORD))).use { archive ->
            archive.putNextEntry(ZipEntry("manifest.json"))
            archive.write(manifest.toByteArray())
            archive.closeEntry()
            archive.putNextEntry(ZipEntry("images/$fileName"))
            val chunk = ByteArray(1024 * 1024)
            repeat(21) { archive.write(chunk) }
            archive.closeEntry()
        }
        return output.toByteArray()
    }

    private fun assertRestoreFailsWithoutChangingData(backup: ByteArray, password: String) {
        val beforeEntries = store.entries()
        val beforeImages = storedImageContents(beforeEntries)

        val failure = runCatching {
            store.restoreEncryptedBackup(backup.inputStream(), password)
        }.exceptionOrNull()

        assertNotNull("恢复应当失败", failure)
        assertEquals(beforeEntries, store.entries())
        val afterImages = storedImageContents(store.entries())
        assertEquals(beforeImages.keys, afterImages.keys)
        beforeImages.forEach { (fileName, bytes) ->
            assertArrayEquals(bytes, afterImages.getValue(fileName))
        }
        assertFalse(store.canUndoLastRestore())
        assertTrue(
            context.filesDir.listFiles().orEmpty()
                .none { it.isDirectory && it.name.startsWith("journal-images-restore-") },
        )
    }

    private fun storedImageContents(entries: List<JournalEntry>): Map<String, ByteArray> = entries
        .flatMap { it.imageFileNames }
        .associateWith { fileName ->
            store.openImage(fileName)?.use { it.readBytes() } ?: error("图片无法读取：$fileName")
        }

    private fun assertImageContents(entry: JournalEntry, expected: List<ByteArray>) {
        val actual = entry.imageFileNames.map { fileName ->
            store.openImage(fileName)?.use { it.readBytes() } ?: error("图片无法读取：$fileName")
        }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedBytes, actualBytes) ->
            assertArrayEquals(expectedBytes, actualBytes)
        }
    }

    private fun imageUris(contents: List<ByteArray>): List<Uri> = contents.mapIndexed { index, bytes ->
        File(context.cacheDir, "backup-test-image-$index").also { it.writeBytes(bytes) }.let(Uri::fromFile)
    }

    private fun clearStoredData() {
        if (::dao.isInitialized) JournalDatabase.get(context).clearAllTables()
        if (::context.isInitialized) {
            journalImagesDirectory().listFiles()?.forEach(File::delete)
            context.filesDir.listFiles()
                ?.filter {
                    it.name.startsWith("journal-images-restore-") ||
                        it.name.startsWith("journal-restore-undo-")
                }
                ?.forEach { file ->
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("backup-test-image-") }
                ?.forEach(File::delete)
            restorePreferences().edit().clear().commit()
        }
    }

    private fun journalImagesDirectory(): File = File(context.filesDir, "journal-images")

    private fun restorePreferences() = EncryptedSharedPreferences.create(
        context,
        "xike-restore",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun entry(id: String, createdAt: Long, note: String) = JournalEntry(
        id = id,
        createdAt = createdAt,
        mood = Mood.GOOD,
        tags = listOf("测试"),
        note = note,
    )

    private companion object {
        const val PASSWORD = "correct-password"
    }
}
