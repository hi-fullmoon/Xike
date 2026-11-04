package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xike.app.data.BackupCipher
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.model.OutdoorSnapshot
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
    fun favoriteOnlyRestoreCanBeUndoneAndRepeatedMergeDoesNotReplaceUndo() {
        val source = JournalEntry(id = "favorite-only", mood = Mood.CALM, note = "")
        store.add(source)
        listOf<String?>(null, PASSWORD).forEach { password ->
            store.setFavorite(source.id, true)
            val bytes =
                ByteArrayOutputStream().also { store.writeBackup(it, password) }.toByteArray()
            store.setFavorite(source.id, false)
            val summary = store.inspectBackup(bytes.inputStream(), password)
            assertEquals(1, summary.favoriteCount)
            assertEquals(0, summary.mergeNewEntryCount)
            assertEquals(0, summary.mergeConflictCount)
            assertEquals(1, summary.mergeFavoriteChangeCount)
            assertEquals(1, store.restoreBackup(bytes.inputStream(), password).size)
            assertTrue(store.entries().single().isFavorite)
            val token = dao.settingValue(RESTORE_UNDO_TOKEN_SETTING)
            store.restoreBackup(bytes.inputStream(), password)
            assertEquals(token, dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
            assertFalse(store.undoLastRestore().single().isFavorite)
            store.restoreBackup(bytes.inputStream(), password)
            store.setFavorite(source.id, true)
            assertTrue(store.canUndoLastRestore())
            store.setFavorite(source.id, false)
            assertFalse(store.canUndoLastRestore())
        }
    }

    @Test
    fun deleteUndoAndStaleEditingPreserveTheSavedFavorite() {
        val stale = JournalEntry(id = "favorite-edit", mood = Mood.CALM, note = "")
        store.add(stale)
        store.setFavorite(stale.id, true)
        val saved =
            store.update(stale.copy(note = "updated"), emptyList(), refreshEntries = false).single()
        assertTrue(saved.isFavorite)
        store.delete(stale.id)
        assertTrue(store.undoDelete(stale.id).single().isFavorite)
        assertEquals("updated", store.entries().single().note)
    }

    @Test
    fun oldLabelBackupDefaultsFavoriteAndInvalidNewFlagsDoNotChangeData() {
        val source =
            JournalEntry(id = "favorite-format", mood = Mood.CALM, note = "", isFavorite = true)
        store.add(source)
        val backup = ByteArrayOutputStream().also { store.writeBackup(it, null) }.toByteArray()
        fun changed(version: Int, value: Any?): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                ZipInputStream(backup.inputStream()).use { input ->
                    var part = input.nextEntry
                    while (part != null) {
                        var bytes = input.readBytes()
                        if (part.name == "manifest.json") {
                            val json =
                                JSONObject(bytes.toString(Charsets.UTF_8)).put("version", version)
                            val entry = json.getJSONArray("entries").getJSONObject(0)
                            if (value == null) entry.remove("isFavorite")
                            else entry.put("isFavorite", value)
                            bytes = json.toString().toByteArray(Charsets.UTF_8)
                        }
                        zip.putNextEntry(ZipEntry(part.name))
                        zip.write(bytes)
                        zip.closeEntry()
                        input.closeEntry()
                        part = input.nextEntry
                    }
                }
            }
            return out.toByteArray()
        }
        listOf(null, "true", 1, JSONObject.NULL).forEach { value ->
            assertTrue(
                runCatching { store.restoreBackup(changed(11, value).inputStream(), null) }
                    .isFailure
            )
            assertEquals(listOf(source), store.entries())
        }
        store.restoreBackup(
            changed(10, null).inputStream(),
            null,
            mode = com.xike.app.data.BackupRestoreMode.REPLACE,
        )
        assertFalse(store.entries().single().isFavorite)
        assertTrue(store.undoLastRestore().single().isFavorite)
    }

    @Test
    fun validatedIncomingBackupRemainsInspectableWhenLocalComparisonFails() {
        val source = JournalEntry(id = "preview-local-read", mood = Mood.CALM, note = "")
        val png =
            java.util.Base64.getDecoder()
                .decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9jX/0AAAAASUVORK5CYII="
                )
        val stored = store.add(source, imageUris(listOf(png))).single()
        val backup = ByteArrayOutputStream().also { store.writeBackup(it, null) }.toByteArray()
        check(java.io.File(journalImagesDirectory(), stored.imageFileNames.single()).delete())
        val summary = store.inspectBackup(backup.inputStream(), null)
        assertEquals(1, summary.entryCount)
        assertNull(summary.mergeNewEntryCount)
        assertTrue(runCatching { store.restoreBackup(backup.inputStream(), null) }.isFailure)
        assertEquals(listOf(stored), store.entries())
    }

    @Test
    fun currentBackupsPreserveOverallMoodAndFeelingsInPlainAndEncryptedArchives() {
        val source =
            JournalEntry(
                id = "mood-v9",
                mood = Mood.BELOW_AVERAGE,
                tags = emptyList(),
                note = "",
                feelings = listOf("HOPEFUL", "ANXIOUS"),
            )
        store.add(source, emptyList())
        listOf<String?>(null, PASSWORD).forEach { password ->
            val bytes =
                ByteArrayOutputStream().also { store.writeBackup(it, password) }.toByteArray()
            val input =
                if (password == null) bytes.inputStream()
                else {
                    val encrypted = bytes.inputStream()
                    assertEquals(
                        BackupCipher.magicSize.toLong(),
                        encrypted.skip(BackupCipher.magicSize.toLong()),
                    )
                    BackupCipher.decryptingStream(encrypted, password)
                }
            val parts = mutableMapOf<String, ByteArray>()
            ZipInputStream(input).use { archive ->
                var part = archive.nextEntry
                while (part != null) {
                    parts[part.name] = archive.readBytes()
                    archive.closeEntry()
                    part = archive.nextEntry
                }
            }
            val manifest = JSONObject(parts.getValue("manifest.json").toString(Charsets.UTF_8))
            assertEquals(11, manifest.getInt("version"))
            assertEquals(
                source,
                JournalEntry.fromJson(manifest.getJSONArray("entries").getJSONObject(0)),
            )
            val html = parts.getValue("index.html").toString(Charsets.UTF_8)
            assertTrue(html.contains("期待、焦虑"))
            replaceWithLocalEntry()
            val restored =
                store
                    .restoreBackup(bytes.inputStream(), password, mode = BackupRestoreMode.REPLACE)
                    .single()
            assertEquals(source, restored)
        }
    }

    @Test
    fun savingAfterRenameUsesCurrentNameAndLeavesEarlierRecordUnchanged() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            store.saveLabel(
                com.xike.app.data.JournalLabel(
                    "duplicate-english",
                    com.xike.app.data.JournalLabelKind.TOPIC,
                    "Work",
                )
            )
        }
        val label =
            com.xike.app.data.JournalLabel(
                "rename-in-flight",
                com.xike.app.data.JournalLabelKind.TOPIC,
                "个人项目",
            )
        store.saveLabel(label)
        val first =
            JournalEntry(
                id = "before-rename",
                mood = Mood.CALM,
                note = "",
                labels = listOf(label.selection()),
            )
        store.add(first)
        store.saveLabel(label.copy(name = "个人计划"))
        store.add(first.copy(id = "after-rename"))
        assertEquals("个人项目", store.entries().single { it.id == first.id }.labels.single().savedName)
        assertEquals(
            "个人计划",
            store.entries().single { it.id == "after-rename" }.labels.single().savedName,
        )
        assertEquals(2, store.search(JournalSearchQuery(topicIds = setOf(label.id))).totalCount)
    }

    @Test
    fun unusedArchivedCatalogAndOrderRoundTripInPlainAndEncryptedBackups() {
        val topic =
            com.xike.app.data.JournalLabel(
                "unused-topic",
                com.xike.app.data.JournalLabelKind.TOPIC,
                "长期计划",
                sortOrder = 20,
            )
        val activity =
            com.xike.app.data.JournalLabel(
                "archived-activity",
                com.xike.app.data.JournalLabelKind.ACTIVITY,
                "晚间散步",
                isArchived = true,
                sortOrder = 30,
            )
        store.saveLabel(topic)
        store.saveLabel(activity)
        listOf<String?>(null, PASSWORD).forEachIndexed { index, password ->
            val expected = dao.labels().toSet()
            val output =
                ByteArrayOutputStream().also { store.writeBackup(it, password) }.toByteArray()
            val extra = topic.copy(id = "extra-$index", name = "额外主题$index")
            store.saveLabel(extra)
            store.restoreBackup(output.inputStream(), password, mode = BackupRestoreMode.REPLACE)
            assertEquals(expected, dao.labels().toSet())
            store.undoLastRestore()
            assertTrue(dao.labels().any { it.id == extra.id })
        }
    }

    @Test
    fun legacyVersion9WithoutCatalogKeepsTopicsAndFeelings() {
        val source =
            JournalEntry(
                id = "legacy-v9",
                mood = Mood.ABOVE_AVERAGE,
                note = "",
                tags = listOf("运动", "个人项目"),
                feelings = listOf("HOPEFUL"),
            )
        val json = source.toJson().apply { remove("labels") }
        listOf<String?>(null, PASSWORD).forEach { password ->
            val restored =
                store
                    .restoreBackup(
                        versionedMoodArchive(9, json, password).inputStream(),
                        password,
                        mode = BackupRestoreMode.REPLACE,
                    )
                    .single()
            assertEquals(source, restored)
            assertTrue(restored.labels.all { it.kind == com.xike.app.data.JournalLabelKind.TOPIC })
        }
    }

    @Test
    fun plainAndEncryptedVersion8BackupsRemainRestorableWithoutChangingLegacyMood() {
        val old = JournalEntry(id = "mood-v8", mood = Mood.TIRED, tags = emptyList(), note = "")
        val legacyJson =
            old.toJson().apply {
                remove("feelings")
                remove("moodScaleVersion")
                remove("labels")
            }
        listOf<String?>(null, PASSWORD).forEach { password ->
            val bytes = versionedMoodArchive(8, legacyJson, password)
            assertEquals(1, store.inspectBackup(bytes.inputStream(), password).entryCount)
            val restored =
                store
                    .restoreBackup(bytes.inputStream(), password, mode = BackupRestoreMode.REPLACE)
                    .single()
            assertEquals(old, restored)
        }
    }

    @Test
    fun unsupportedVersionsAreRejectedBeforeReplacingEntries() {
        val original =
            store
                .add(
                    JournalEntry(id = "keep", mood = Mood.CALM, tags = emptyList(), note = ""),
                    emptyList(),
                )
                .single()
        listOf(12 to null, 12 to PASSWORD, 7 to null).forEach { (version, password) ->
            val bytes =
                versionedMoodArchive(version, original.copy(id = "incoming").toJson(), password)
            assertTrue(runCatching { store.inspectBackup(bytes.inputStream(), password) }.isFailure)
            assertTrue(
                runCatching {
                    store.restoreBackup(
                        bytes.inputStream(),
                        password,
                        mode = BackupRestoreMode.REPLACE,
                    )
                }
                    .isFailure
            )
            assertEquals(listOf(original), store.entries())
        }
    }

    private fun versionedMoodArchive(
        version: Int,
        entry: JSONObject,
        password: String?,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val destination =
            if (password == null) output else BackupCipher.encryptingStream(output, password)
        ZipOutputStream(destination).use { archive ->
            val manifest =
                JSONObject()
                    .put("format", "xike")
                    .put("version", version)
                    .put("entries", JSONArray(listOf(entry)))
            listOf(
                    "manifest.json" to manifest.toString(),
                    "index.html" to "",
                    "complete.txt" to "XIKE_BACKUP_COMPLETE",
                )
                .forEach { (name, text) ->
                    archive.putNextEntry(ZipEntry(name))
                    archive.write(text.toByteArray(Charsets.UTF_8))
                    archive.closeEntry()
                }
        }
        return output.toByteArray()
    }

    @Test
    fun backupWithMultipleImagesCanBeInspectedRestoredAndUndone() {
        val imageContents =
            listOf(
                "first image".toByteArray(),
                ByteArray(32 * 1024) { index -> (index % 251).toByte() },
                "third image".toByteArray(),
            )
        val source =
            entry("source", 1_700_000_000_000L, "含多图的备份")
                .copy(
                    outdoor =
                        OutdoorSnapshot(
                            placeName = "上海 · 浦东",
                            temperatureCelsius = 27.2,
                            weatherCode = 2,
                            capturedAt = 1_700_000_000_000L,
                        )
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

        val restored =
            store.restoreEncryptedBackup(
                backup.inputStream(),
                PASSWORD,
                mode = BackupRestoreMode.REPLACE,
            )
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
        val sourceFile =
            File(context.cacheDir, "backup-test-audio.m4a").also { it.writeBytes(audioContents) }
        val audio = store.importAudio(sourceFile, 12_500L)
        store.add(entry("voice-source", 1_700_000_000_500L, "有一段语音").copy(audio = audio))
        val backup = encryptedBackup()
        assertTrue(store.backupRequiresPassword(backup.inputStream()))
        val encryptedInput =
            backup.inputStream().also { input ->
                val magic = ByteArray(BackupCipher.magicSize)
                assertEquals(magic.size, input.read(magic))
                assertTrue(BackupCipher.hasStreamingMagic(magic))
            }
        ZipInputStream(BufferedInputStream(BackupCipher.decryptingStream(encryptedInput, PASSWORD)))
            .use { archive ->
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
        val restored =
            store
                .restoreEncryptedBackup(
                    backup.inputStream(),
                    PASSWORD,
                    mode = BackupRestoreMode.REPLACE,
                )
                .single()

        assertEquals(12_500L, restored.audio?.durationMillis)
        val restoredBytes =
            store.openAudio(requireNotNull(restored.audio).fileName)?.use { it.readBytes() }
        assertArrayEquals(audioContents, restoredBytes)
    }

    @Test
    fun unencryptedBackupContainsReadableHtmlAndRestoresAllMedia() {
        val imageContents =
            java.util.Base64.getDecoder()
                .decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9jX/0AAAAASUVORK5CYII="
                )
        val audioContents = ByteArray(1024) { it.toByte() }
        val audioFile =
            File(context.cacheDir, "backup-test-audio-plain.m4a").also {
                it.writeBytes(audioContents)
            }
        val audio = store.importAudio(audioFile, 2_000L)
        val source =
            store
                .add(
                    entry("plain", 1_700_000_000_000L, "浏览器可读 <内容>").copy(audio = audio),
                    imageUris(listOf(imageContents)),
                )
                .single()
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
        val restored =
            store
                .restoreBackup(backup.inputStream(), null, mode = BackupRestoreMode.REPLACE)
                .single()
        assertEquals(source.note, restored.note)
        assertImageContents(restored, listOf(imageContents))
        assertArrayEquals(
            audioContents,
            store.openAudio(requireNotNull(restored.audio).fileName)?.use { it.readBytes() },
        )
        assertTrue(store.canUndoLastRestore())
        assertEquals("设备上的原记录", store.undoLastRestore().single().note)
    }

    @Test
    fun incompleteUnencryptedBackupDoesNotReplaceExistingData() {
        replaceWithLocalEntry()
        val manifest =
            JSONObject()
                .put("format", "xike")
                .put("version", 7)
                .put(
                    "entries",
                    JSONArray(
                        listOf(
                            entry("missing", 300L, "缺少图片")
                                .copy(imageFileNames = listOf("missing.png"))
                                .toJson()
                        )
                    ),
                )
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
        val manifest =
            JSONObject()
                .put("format", "xike")
                .put("version", 4)
                .put("entries", JSONArray(listOf(entry("legacy-v4", 150L, "旧版流式备份").toJson())))
                .toString()
        val output = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(BackupCipher.encryptingStream(output, PASSWORD)))
            .use { archive ->
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
        val audioFile =
            File(context.cacheDir, "backup-test-audio-delete.m4a").also {
                it.writeBytes("private audio".toByteArray())
            }
        val audio = store.importAudio(audioFile, 2_000L)
        val stored =
            store
                .add(
                    entry("delete-me", 300L, "待删除标记").copy(audio = audio),
                    imageUris(listOf("private image".toByteArray())),
                )
                .single()
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

    private fun encryptedBackup(): ByteArray =
        ByteArrayOutputStream()
            .also { output ->
                store.writeEncryptedBackup(output, PASSWORD)
            }
            .toByteArray()

    private fun backupWithOversizedImage(): ByteArray {
        val fileName = "oversized.xike-image"
        val manifest =
            JSONObject()
                .put("format", "xike")
                .put("version", 4)
                .put(
                    "entries",
                    JSONArray(
                        listOf(
                            entry("oversized", 300L, "超限图片")
                                .copy(imageFileNames = listOf(fileName))
                                .toJson()
                        )
                    ),
                )
                .toString()
        val output = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(BackupCipher.encryptingStream(output, PASSWORD)))
            .use { archive ->
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
        }
            .exceptionOrNull()

        assertNotNull("恢复应当失败", failure)
        assertEquals(beforeEntries, store.entries())
        val afterImages = storedImageContents(store.entries())
        assertEquals(beforeImages.keys, afterImages.keys)
        beforeImages.forEach { (fileName, bytes) ->
            assertArrayEquals(bytes, afterImages.getValue(fileName))
        }
        assertFalse(store.canUndoLastRestore())
        assertTrue(
            context.filesDir.listFiles().orEmpty().none {
                it.isDirectory && it.name.startsWith("journal-images-restore-")
            }
        )
    }

    private fun storedImageContents(entries: List<JournalEntry>): Map<String, ByteArray> =
        entries
            .flatMap { it.imageFileNames }
            .associateWith { fileName ->
                store.openImage(fileName)?.use { it.readBytes() } ?: error("图片无法读取：$fileName")
            }

    private fun assertImageContents(entry: JournalEntry, expected: List<ByteArray>) {
        val actual =
            entry.imageFileNames.map { fileName ->
                store.openImage(fileName)?.use { it.readBytes() } ?: error("图片无法读取：$fileName")
            }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedBytes, actualBytes) ->
            assertArrayEquals(expectedBytes, actualBytes)
        }
    }

    private fun imageUris(contents: List<ByteArray>): List<Uri> =
        contents.mapIndexed { index, bytes ->
            File(context.cacheDir, "backup-test-image-$index")
                .also { it.writeBytes(bytes) }
                .let(Uri::fromFile)
        }

    private fun clearStoredData() {
        if (::dao.isInitialized) JournalDatabase.get(context).clearAllTables()
        if (::context.isInitialized) {
            journalImagesDirectory().listFiles()?.forEach(File::delete)
            journalAudiosDirectory().listFiles()?.forEach(File::delete)
            context.filesDir
                .listFiles()
                ?.filter {
                    it.name.startsWith("journal-images-restore-") ||
                        it.name.startsWith("journal-restore-undo-")
                }
                ?.forEach { file ->
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            context.cacheDir
                .listFiles()
                ?.filter {
                    it.name.startsWith("backup-test-image-") ||
                        it.name.startsWith("backup-test-audio")
                }
                ?.forEach(File::delete)
            restorePreferences().edit().clear().commit()
        }
    }

    private fun journalImagesDirectory(): File = File(context.filesDir, "journal-images")

    private fun journalAudiosDirectory(): File = File(context.filesDir, "journal-audios")

    private fun restorePreferences() =
        EncryptedSharedPreferences.create(
            context,
            "xike-restore",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun entry(id: String, createdAt: Long, note: String) =
        JournalEntry(
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
