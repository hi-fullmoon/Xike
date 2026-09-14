package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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
        val source = entry("source", 1_700_000_000_000L, "含多图的备份").copy(
            outdoor = OutdoorSnapshot(
                placeName = "上海 · 浦东",
                temperatureCelsius = 27.2,
                weatherCode = 2,
                capturedAt = 1_700_000_000_000L,
            ),
        )
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
        assertEquals(source.outdoor, restored.single().outdoor)
        assertImageContents(restored.single(), imageContents)
        assertTrue(store.canUndoLastRestore())

        val undone = store.undoLastRestore()
        assertEquals(listOf("local"), undone.map { it.id })
        assertEquals("设备上的原记录", undone.single().note)
        assertFalse(store.canUndoLastRestore())
    }

    @Test
    fun encryptedAudioIsIncludedInBackupAndRestore() {
        val audioContents = ByteArray(48 * 1024) { index -> (index % 239).toByte() }
        val sourceFile = File(context.cacheDir, "backup-test-audio.m4a").also { it.writeBytes(audioContents) }
        val audio = store.importAudio(sourceFile, 12_500L)
        store.add(entry("voice-source", 1_700_000_000_500L, "有一段语音").copy(audio = audio))
        val backup = encryptedBackup()
        assertTrue(store.backupRequiresPassword(backup.inputStream()))
        val encryptedInput = backup.inputStream().also { input ->
            val magic = ByteArray(BackupCipher.magicSize)
            assertEquals(magic.size, input.read(magic))
            assertTrue(BackupCipher.hasStreamingMagic(magic))
        }
        ZipInputStream(BufferedInputStream(BackupCipher.decryptingStream(encryptedInput, PASSWORD))).use { archive ->
            val names = mutableListOf<String>()
            var item = archive.nextEntry
            while (item != null) {
                names += item.name
                val contents = archive.readBytes()
                if (item.name == "index.html") {
                    assertTrue(contents.toString(Charsets.UTF_8).contains("有一段语音"))
                }
                archive.closeEntry()
                item = archive.nextEntry
            }
            assertEquals(listOf("manifest.json", "index.html"), names.take(2))
        }

        replaceWithLocalEntry()

        val summary = store.inspectEncryptedBackup(backup.inputStream(), PASSWORD)
        assertEquals(1, summary.audioCount)
        val restored = store.restoreEncryptedBackup(backup.inputStream(), PASSWORD).single()

        assertEquals(12_500L, restored.audio?.durationMillis)
        val restoredBytes = store.openAudio(requireNotNull(restored.audio).fileName)?.use { it.readBytes() }
        assertArrayEquals(audioContents, restoredBytes)
    }

    @Test
    fun unencryptedBackupContainsReadableHtmlAndRestoresAllMedia() {
        val imageContents = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9jX/0AAAAASUVORK5CYII=",
        )
        val audioContents = ByteArray(1024) { it.toByte() }
        val audioFile = File(context.cacheDir, "backup-test-audio-plain.m4a").also { it.writeBytes(audioContents) }
        val audio = store.importAudio(audioFile, 2_000L)
        val source = store.add(
            entry("plain", 1_700_000_000_000L, "浏览器可读 <内容>").copy(audio = audio),
            imageUris(listOf(imageContents)),
        ).single()
        val backup = ByteArrayOutputStream().also { store.writeBackup(it, null) }.toByteArray()

        assertFalse(store.backupRequiresPassword(backup.inputStream()))
        val archiveEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(backup.inputStream()).use { archive ->
            var item = archive.nextEntry
            while (item != null) {
                archiveEntries[item.name] = archive.readBytes()
                archive.closeEntry()
                item = archive.nextEntry
            }
        }
        val html = archiveEntries.getValue("index.html").toString(Charsets.UTF_8)
        assertTrue(html.contains("浏览器可读 &lt;内容&gt;"))
        assertTrue(html.contains(".png\""))
        assertTrue(html.contains(".m4a\""))
        assertEquals(1, archiveEntries.keys.count { it.startsWith("images/") })
        assertEquals(1, archiveEntries.keys.count { it.startsWith("audios/") })

        replaceWithLocalEntry()
        val summary = store.inspectBackup(backup.inputStream(), null)
        assertEquals(1, summary.entryCount)
        assertEquals(1, summary.imageCount)
        assertEquals(1, summary.audioCount)
        val restored = store.restoreBackup(backup.inputStream(), null).single()
        assertEquals(source.note, restored.note)
        assertImageContents(restored, listOf(imageContents))
        assertArrayEquals(audioContents, store.openAudio(requireNotNull(restored.audio).fileName)?.use { it.readBytes() })
        assertTrue(store.canUndoLastRestore())
        assertEquals("设备上的原记录", store.undoLastRestore().single().note)
    }

    @Test
    fun incompleteUnencryptedBackupDoesNotReplaceExistingData() {
        replaceWithLocalEntry()
        val manifest = JSONObject()
            .put("format", "xike")
            .put("version", 7)
            .put("entries", JSONArray(listOf(entry("missing", 300L, "缺少图片")
                .copy(imageFileNames = listOf("missing.png")).toJson())))
            .toString()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { archive ->
            archive.putNextEntry(ZipEntry("manifest.json"))
            archive.write(manifest.toByteArray())
            archive.closeEntry()
            archive.putNextEntry(ZipEntry("index.html"))
            archive.write("<html></html>".toByteArray())
            archive.closeEntry()
            archive.putNextEntry(ZipEntry("complete.txt"))
            archive.write("XIKE_BACKUP_COMPLETE".toByteArray())
            archive.closeEntry()
        }

        assertRestoreFailsWithoutChangingData(output.toByteArray(), null)
    }

    @Test
    fun wrongPasswordDoesNotChangeExistingData() {
        store.add(entry("source", 100L, "备份内容"))
        val backup = encryptedBackup()
        replaceWithLocalEntry()

        assertRestoreFailsWithoutChangingData(backup, "wrong-password")
    }

    @Test
    fun version4StreamingBackupRemainsRestorable() {
        val manifest = JSONObject()
            .put("format", "xike")
            .put("version", 4)
            .put("entries", JSONArray(listOf(entry("legacy-v4", 150L, "旧版流式备份").toJson())))
            .toString()
        val output = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(BackupCipher.encryptingStream(output, PASSWORD))).use { archive ->
            archive.putNextEntry(ZipEntry("manifest.json"))
            archive.write(manifest.toByteArray())
            archive.closeEntry()
        }

        val restored = store.restoreEncryptedBackup(output.toByteArray().inputStream(), PASSWORD)

        assertEquals(listOf("legacy-v4"), restored.map(JournalEntry::id))
        assertNull(restored.single().outdoor)
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
        val audioFile = File(context.cacheDir, "backup-test-audio-delete.m4a").also {
            it.writeBytes("private audio".toByteArray())
        }
        val audio = store.importAudio(audioFile, 2_000L)
        val stored = store.add(
            entry("delete-me", 300L, "待删除标记").copy(audio = audio),
            imageUris(listOf("private image".toByteArray())),
        ).single()
        val imageFileName = stored.imageFileNames.single()

        store.openImage(imageFileName).use { input -> assertNotNull(input) }
        store.openAudio(audio.fileName).use { input -> assertNotNull(input) }
        assertEquals(1, store.search(JournalSearchQuery(text = "待删除标记")).totalCount)

        val remaining = store.delete(stored.id)

        assertTrue(remaining.isEmpty())
        assertTrue(store.entries().isEmpty())
        assertEquals(0, store.search(JournalSearchQuery(text = "待删除标记")).totalCount)
        store.openImage(imageFileName).use { input -> assertNotNull(input) }

        store.finalizeDelete(stored.id)

        assertNull(store.openImage(imageFileName))
        assertNull(store.openAudio(audio.fileName))
    }

    private fun replaceWithLocalEntry() {
        dao.replaceJournals(emptyList())
        journalImagesDirectory().listFiles()?.forEach(File::delete)
        journalAudiosDirectory().listFiles()?.forEach(File::delete)
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

    private fun assertRestoreFailsWithoutChangingData(backup: ByteArray, password: String?) {
        val beforeEntries = store.entries()
        val beforeImages = storedImageContents(beforeEntries)

        val failure = runCatching {
            store.restoreBackup(backup.inputStream(), password)
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
            journalAudiosDirectory().listFiles()?.forEach(File::delete)
            context.filesDir.listFiles()
                ?.filter {
                    it.name.startsWith("journal-images-restore-") ||
                        it.name.startsWith("journal-restore-undo-")
                }
                ?.forEach { file ->
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("backup-test-image-") || it.name.startsWith("backup-test-audio") }
                ?.forEach(File::delete)
            restorePreferences().edit().clear().commit()
        }
    }

    private fun journalImagesDirectory(): File = File(context.filesDir, "journal-images")

    private fun journalAudiosDirectory(): File = File(context.filesDir, "journal-audios")

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
