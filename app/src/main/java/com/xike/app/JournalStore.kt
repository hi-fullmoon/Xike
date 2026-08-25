package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Mood(val label: String, val emoji: String, val score: Int) {
    LOW("风雨", "☔", 1),
    TIRED("低云", "☁", 2),
    CALM("微风", "〰", 3),
    GOOD("晴间", "⛅", 4),
    JOYFUL("晴朗", "☀", 5);

    companion object {
        fun fromName(value: String): Mood = entries.firstOrNull { it.name == value } ?: CALM
    }
}

const val MAX_IMAGES_PER_ENTRY = 9

data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val mood: Mood,
    val tags: List<String>,
    val note: String,
    val imageFileNames: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("createdAt", createdAt)
        .put("mood", mood.name)
        .put("tags", JSONArray(tags))
        .put("note", note)
        .put("imageFileNames", JSONArray(imageFileNames))

    companion object {
        fun fromJson(json: JSONObject): JournalEntry = JournalEntry(
            id = json.getString("id"),
            createdAt = json.getLong("createdAt"),
            mood = Mood.fromName(json.getString("mood")),
            tags = json.optJSONArray("tags")?.let { values ->
                List(values.length()) { index -> values.getString(index) }
            } ?: emptyList(),
            note = json.optString("note"),
            imageFileNames = json.optJSONArray("imageFileNames")?.let { values ->
                List(values.length()) { index -> values.getString(index) }
            } ?: listOfNotNull(json.optString("imageFileName").takeIf { it.isNotBlank() }),
        )
    }
}

internal fun normalizeRestoredEntries(
    entries: List<JournalEntry>,
    availableImages: Set<String>,
): List<JournalEntry> = entries
    .distinctBy { it.id }
    .map { entry ->
        entry.copy(
            imageFileNames = entry.imageFileNames
                .distinct()
                .filter { it in availableImages }
                .take(MAX_IMAGES_PER_ENTRY),
        )
    }
    .sortedByDescending { it.createdAt }

class JournalDataException(message: String, cause: Throwable) : IllegalStateException(message, cause)

data class JournalSnapshot(
    val entries: List<JournalEntry>,
    val themeName: String?,
)

data class BackupSummary(
    val entryCount: Int,
    val imageCount: Int,
    val oldestCreatedAt: Long?,
    val newestCreatedAt: Long?,
)

private data class PreparedBackup(
    val stagingDirectory: File,
    val entries: List<JournalEntry>,
)

private data class UndoSnapshot(
    val file: File,
    val password: String,
)

private data class UndoSnapshotSwap(
    val previous: UndoSnapshot?,
    val current: UndoSnapshot,
)

class JournalStore(context: Context) {
    private val appContext = context.applicationContext
    private val imagesDirectory = File(appContext.filesDir, IMAGES_DIRECTORY)
    private val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val database by lazy { JournalDatabase.get(appContext) }
    private val dao by lazy { database.journalDao() }
    private val legacyPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            LEGACY_PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val restorePreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            RESTORE_PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    init {
        check(imagesDirectory.isDirectory || imagesDirectory.mkdirs()) { "无法创建图片存储目录。" }
    }

    @Synchronized
    fun initialize(): JournalSnapshot = try {
        if (dao.settingValue(LEGACY_MIGRATION_SETTING) == null) {
            val legacyEntries = readLegacyEntries()
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
            val legacyTheme = runCatching { legacyPreferences.getString(THEME_KEY, null) }
                .getOrElse { error ->
                    throw JournalDataException("旧版外观设置暂时无法读取，原数据未被覆盖。", error)
            }
            dao.importLegacyIfNeeded(legacyEntries.map(JournalEntry::toBundle), legacyTheme)
        }
        ensureSearchIndex()
        JournalSnapshot(readEntries(), dao.settingValue(THEME_SETTING))
    } catch (error: JournalDataException) {
        throw error
    } catch (error: Throwable) {
        throw JournalDataException("加密数据库初始化失败，原数据未被覆盖。", error)
    }

    fun entries(): List<JournalEntry> = readEntries()

    fun observeEntries(): Flow<List<JournalEntry>> =
        dao.observeRecords().map { records -> records.map(JournalEntryRecord::toJournalEntry) }

    fun search(
        query: JournalSearchQuery,
        offset: Int = 0,
        limit: Int = 60,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): JournalSearchPage = try {
        require(offset >= 0) { "搜索偏移量不能小于 0。" }
        require(limit in 1..200) { "每页搜索结果需要在 1 到 200 条之间。" }
        val range = query.epochRange(zoneId)
        val hasText = if (query.normalizedText.isEmpty()) 0 else 1
        val ftsQuery = query.normalizedText.takeIf(String::isNotEmpty)?.let(::journalFtsQuery) ?: "\"\""
        val filterMoods = if (query.moods.isEmpty()) 0 else 1
        val moods = query.moods.map(Mood::name).ifEmpty { listOf(Mood.CALM.name) }
        val filterTags = if (query.tags.isEmpty()) 0 else 1
        val tags = query.tags.toList().ifEmpty { listOf("") }
        val imageFilter = query.imageFilter.name
        val records = dao.searchRecords(
            hasText = hasText,
            ftsQuery = ftsQuery,
            startInclusive = range.startInclusive,
            endExclusive = range.endExclusive,
            filterMoods = filterMoods,
            moods = moods,
            filterTags = filterTags,
            tags = tags,
            imageFilter = imageFilter,
            limit = limit,
            offset = offset,
        ).map(JournalEntryRecord::toJournalEntry)
        val totalCount = dao.searchRecordCount(
            hasText = hasText,
            ftsQuery = ftsQuery,
            startInclusive = range.startInclusive,
            endExclusive = range.endExclusive,
            filterMoods = filterMoods,
            moods = moods,
            filterTags = filterTags,
            tags = tags,
            imageFilter = imageFilter,
        )
        JournalSearchPage(records, totalCount, offset)
    } catch (error: Throwable) {
        throw JournalDataException("日记搜索暂时不可用，请重试。", error)
    }

    @Synchronized
    fun add(entry: JournalEntry, imageUris: List<Uri> = emptyList()): List<JournalEntry> {
        val imported = importImages(imageUris)
        return runCatching {
            val storedEntry = entry.copy(imageFileNames = imported)
            dao.insertJournal(storedEntry.toBundle())
            readEntries()
        }.onFailure {
            imported.forEach(::deleteImage)
        }.getOrElse { error ->
            if (error is JournalDataException) throw error
            throw JournalDataException("日记保存失败，请重试。", error)
        }
    }

    @Synchronized
    fun delete(entryId: String): List<JournalEntry> = runCatching {
        require(entryId.isNotBlank()) { "记录标识不能为空。" }
        val deletedImages = dao.deleteJournal(entryId)
        val stillReferenced = dao.imageFileNames().toSet()
        deletedImages.filterNot { it in stillReferenced }.forEach(::deleteImage)
        readEntries()
    }.getOrElse { error ->
        if (error is JournalDataException) throw error
        throw JournalDataException("记录删除失败，请重试。", error)
    }

    fun savedThemeName(): String? = dao.settingValue(THEME_SETTING)

    @Synchronized
    fun saveThemeName(themeName: String) {
        runCatching { dao.putSetting(AppSettingEntity(THEME_SETTING, themeName)) }
            .getOrElse { error -> throw JournalDataException("外观设置保存失败，请重试。", error) }
    }

    private fun importImages(uris: List<Uri>): List<String> {
        require(uris.size <= MAX_IMAGES_PER_ENTRY) { "每条记录最多添加 9 张图片。" }
        val imported = mutableListOf<String>()
        return runCatching {
            uris.forEach { imported += importImage(it) }
            imported.toList()
        }.onFailure {
            imported.forEach(::deleteImage)
        }.getOrThrow()
    }

    private fun importImage(uri: Uri): String {
        val fileName = "${UUID.randomUUID()}.xike-image"
        val target = File(imagesDirectory, fileName)

        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                writeEncryptedImage(target, input, MAX_IMAGE_BYTES, null)
            } ?: error("无法读取所选图片")
        }.onFailure {
            target.delete()
            throw it
        }
        return fileName
    }

    fun openImage(fileName: String): InputStream? = fileName
        .takeIf(::isSafeImageFileName)
        ?.let { File(imagesDirectory, it) }
        ?.takeIf(File::isFile)
        ?.let { file -> runCatching { encryptedImage(file).openFileInput() }.getOrNull() }

    @Synchronized
    fun removeOrphanedImages() {
        val referencedImages = dao.imageFileNames().toSet()
        val activeUndoFileName = currentUndoSnapshot()?.file?.name
        imagesDirectory.listFiles()
            ?.filter { it.name !in referencedImages }
            ?.forEach(File::delete)
        appContext.filesDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(RESTORE_STAGING_PREFIX) }
            ?.forEach(File::deleteRecursively)
        appContext.filesDir.listFiles()
            ?.filter {
                it.isFile &&
                    it.name.startsWith(RESTORE_UNDO_PREFIX) &&
                    it.name != activeUndoFileName
            }
            ?.forEach(File::delete)
    }

    @Synchronized
    fun writeEncryptedBackup(output: OutputStream, password: String) {
        require(password.length >= 8) { "备份密码至少需要 8 位。" }
        val currentEntries = readEntries()
        val imageNames = currentEntries.flatMap { it.imageFileNames }.distinct()
        val manifest = JSONObject()
            .put("format", "xike")
            .put("version", STREAMING_BACKUP_VERSION)
            .put("entries", JSONArray(currentEntries.map { it.toJson() }))
            .toString()

        ZipOutputStream(BufferedOutputStream(BackupCipher.encryptingStream(output, password))).use { archive ->
            archive.setLevel(Deflater.BEST_SPEED)
            archive.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            archive.write(manifest.toByteArray(Charsets.UTF_8))
            archive.closeEntry()

            imageNames.forEach { fileName ->
                openImage(fileName)?.use { image ->
                    archive.putNextEntry(ZipEntry("$IMAGES_PREFIX$fileName"))
                    image.copyTo(archive)
                    archive.closeEntry()
                }
            }
        }
    }

    @Synchronized
    fun inspectEncryptedBackup(input: InputStream, password: String): BackupSummary {
        val prepared = readEncryptedBackup(input, password)
        return try {
            val createdAtValues = prepared.entries.map { it.createdAt }
            BackupSummary(
                entryCount = prepared.entries.size,
                imageCount = prepared.entries.flatMap { it.imageFileNames }.distinct().size,
                oldestCreatedAt = createdAtValues.minOrNull(),
                newestCreatedAt = createdAtValues.maxOrNull(),
            )
        } finally {
            prepared.stagingDirectory.deleteRecursively()
        }
    }

    @Synchronized
    fun restoreEncryptedBackup(input: InputStream, password: String): List<JournalEntry> {
        val prepared = readEncryptedBackup(input, password)
        return try {
            val snapshotSwap = createUndoSnapshot()
            try {
                installRestoredData(prepared.stagingDirectory, prepared.entries).also {
                    commitUndoSnapshot(snapshotSwap)
                }
            } catch (error: Throwable) {
                rollbackUndoSnapshot(snapshotSwap)
                throw error
            }
        } finally {
            prepared.stagingDirectory.deleteRecursively()
        }
    }

    @Synchronized
    fun canUndoLastRestore(): Boolean = currentUndoSnapshot() != null

    @Synchronized
    fun undoLastRestore(): List<JournalEntry> {
        val snapshot = currentUndoSnapshot() ?: error("没有可撤销的恢复操作。")
        val prepared = snapshot.file.inputStream().use { input ->
            readEncryptedBackup(input, snapshot.password)
        }
        return try {
            installRestoredData(prepared.stagingDirectory, prepared.entries).also {
                clearUndoSnapshot(snapshot)
            }
        } finally {
            prepared.stagingDirectory.deleteRecursively()
        }
    }

    private fun readEncryptedBackup(input: InputStream, password: String): PreparedBackup {
        require(password.length >= 8) { "备份密码至少需要 8 位。" }
        val source = PushbackInputStream(BufferedInputStream(input), BackupCipher.magicSize)
        val prefix = ByteArray(BackupCipher.magicSize)
        val prefixSize = source.readAvailable(prefix)

        return if (prefixSize == BackupCipher.magicSize && BackupCipher.hasStreamingMagic(prefix)) {
            readStreamingBackup(source, password)
        } else {
            if (prefixSize > 0) source.unread(prefix, 0, prefixSize)
            readLegacyBackup(source.readUtf8Limited(MAX_LEGACY_BACKUP_BYTES), password)
        }
    }

    private fun readStreamingBackup(source: InputStream, password: String): PreparedBackup {
        val stagingDirectory = createStagingDirectory()
        return try {
            var parsedEntries: List<JournalEntry>? = null
            val restoredImages = linkedSetOf<String>()
            var referencedImages = emptySet<String>()
            val totalBytes = longArrayOf(0L)

            ZipInputStream(BufferedInputStream(BackupCipher.decryptingStream(source, password))).use { archive ->
                val manifestEntry = archive.nextEntry
                validateBackup(manifestEntry?.name == MANIFEST_ENTRY) { "备份清单缺失或顺序不正确。" }
                val manifest = JSONObject(archive.readUtf8Limited(MAX_MANIFEST_BYTES))
                archive.closeEntry()
                validateBackup(manifest.optString("format") == "xike") { "这不是息刻备份文件。" }
                validateBackup(manifest.optInt("version") == STREAMING_BACKUP_VERSION) { "暂不支持这个版本的息刻备份。" }

                parsedEntries = parseEntries(manifest.getJSONArray("entries"))
                referencedImages = parsedEntries.orEmpty()
                    .flatMap { it.imageFileNames }
                    .filter(::isSafeImageFileName)
                    .toSet()
                validateBackup(referencedImages.size <= MAX_BACKUP_IMAGES) { "备份包含过多图片。" }

                var entry = archive.nextEntry
                while (entry != null) {
                    validateBackup(!entry.isDirectory && entry.name.startsWith(IMAGES_PREFIX)) { "备份中包含未知内容。" }
                    val fileName = entry.name.removePrefix(IMAGES_PREFIX)
                    validateBackup(fileName in referencedImages) { "备份中包含未引用的图片。" }
                    validateBackup(fileName !in restoredImages) { "备份中包含重复图片。" }
                    val target = File(stagingDirectory, fileName)
                    writeEncryptedImage(target, archive, MAX_IMAGE_BYTES, totalBytes)
                    restoredImages += fileName
                    archive.closeEntry()
                    entry = archive.nextEntry
                }
            }

            val restored = normalizeRestoredEntries(
                requireNotNull(parsedEntries) { "备份清单缺失。" },
                restoredImages,
            )
            PreparedBackup(stagingDirectory, restored)
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            throw normalizedBackupError(error)
        }
    }

    private fun readLegacyBackup(payload: String, password: String): PreparedBackup {
        val stagingDirectory = createStagingDirectory()
        return try {
            val backup = JSONObject(BackupCipher.decryptLegacy(payload, password))
            validateBackup(backup.optString("format") == "xike") { "这不是息刻备份文件。" }
            validateBackup(backup.optInt("version", 1) in 1..3) { "暂不支持这个版本的息刻备份。" }
            val parsedEntries = parseEntries(backup.getJSONArray("entries"))
            val referencedImages = parsedEntries
                .flatMap { it.imageFileNames }
                .filter(::isSafeImageFileName)
                .toSet()
            validateBackup(referencedImages.size <= MAX_BACKUP_IMAGES) { "备份包含过多图片。" }

            val restoredImages = linkedSetOf<String>()
            val totalBytes = longArrayOf(0L)
            val images = backup.optJSONObject("images")
            if (images != null) {
                val keys = images.keys()
                while (keys.hasNext()) {
                    val fileName = keys.next()
                    if (fileName in referencedImages && isSafeImageFileName(fileName)) {
                        val encoded = images.getString(fileName)
                        Base64.getDecoder().wrap(encoded.byteInputStream(Charsets.US_ASCII)).use { decoded ->
                            writeEncryptedImage(
                                target = File(stagingDirectory, fileName),
                                input = decoded,
                                byteLimit = MAX_IMAGE_BYTES,
                                totalBytes = totalBytes,
                            )
                        }
                        restoredImages += fileName
                    }
                }
            }

            val restored = normalizeRestoredEntries(parsedEntries, restoredImages)
            PreparedBackup(stagingDirectory, restored)
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            throw normalizedBackupError(error)
        }
    }

    private fun normalizedBackupError(error: Throwable): Throwable = when (error) {
        is BackupValidationException -> IllegalArgumentException(error.message, error)
        is JournalDataException -> error
        else -> IllegalArgumentException("密码错误或备份文件已损坏。", error)
    }

    private fun parseEntries(values: JSONArray): List<JournalEntry> {
        validateBackup(values.length() <= MAX_BACKUP_ENTRIES) { "备份包含过多日记。" }
        return List(values.length()) { index ->
            val entry = JournalEntry.fromJson(values.getJSONObject(index))
            entry.copy(
                imageFileNames = entry.imageFileNames
                    .filter(::isSafeImageFileName)
                    .distinct()
                    .take(MAX_IMAGES_PER_ENTRY),
            )
        }
    }

    private fun writeEncryptedImage(
        target: File,
        input: InputStream,
        byteLimit: Long,
        totalBytes: LongArray?,
    ) {
        runCatching {
            encryptedImage(target).openFileOutput().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var imageBytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    imageBytes += count
                    validateBackup(imageBytes <= byteLimit) { "图片不能超过 20 MB。" }
                    if (totalBytes != null) {
                        totalBytes[0] += count
                        validateBackup(totalBytes[0] <= MAX_BACKUP_IMAGE_BYTES) { "备份图片总大小超过限制。" }
                    }
                    output.write(buffer, 0, count)
                }
            }
        }.onFailure {
            target.delete()
            throw it
        }
    }

    private fun createStagingDirectory(): File = File(
        appContext.filesDir,
        "$RESTORE_STAGING_PREFIX${UUID.randomUUID()}",
    ).also { directory ->
        check(directory.mkdir()) { "无法创建恢复临时目录。" }
    }

    private fun createUndoSnapshot(): UndoSnapshotSwap {
        val previous = currentUndoSnapshot()
        val passwordBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val snapshot = UndoSnapshot(
            file = File(appContext.filesDir, "$RESTORE_UNDO_PREFIX${UUID.randomUUID()}.xike"),
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(passwordBytes),
        )

        try {
            snapshot.file.outputStream().use { output ->
                writeEncryptedBackup(output, snapshot.password)
            }
            val saved = restorePreferences.edit()
                .putString(RESTORE_UNDO_FILE_KEY, snapshot.file.name)
                .putString(RESTORE_UNDO_PASSWORD_KEY, snapshot.password)
                .commit()
            if (!saved) error("无法保存撤销快照信息。")
            return UndoSnapshotSwap(previous, snapshot)
        } catch (error: Throwable) {
            snapshot.file.delete()
            throw JournalDataException("无法创建恢复撤销快照，设备内容未被替换。", error)
        }
    }

    private fun commitUndoSnapshot(swap: UndoSnapshotSwap) {
        swap.previous?.file
            ?.takeIf { it != swap.current.file }
            ?.delete()
    }

    private fun rollbackUndoSnapshot(swap: UndoSnapshotSwap) {
        val editor = restorePreferences.edit()
        if (swap.previous == null) {
            editor.remove(RESTORE_UNDO_FILE_KEY).remove(RESTORE_UNDO_PASSWORD_KEY)
        } else {
            editor
                .putString(RESTORE_UNDO_FILE_KEY, swap.previous.file.name)
                .putString(RESTORE_UNDO_PASSWORD_KEY, swap.previous.password)
        }
        if (editor.commit()) swap.current.file.delete()
    }

    private fun clearUndoSnapshot(snapshot: UndoSnapshot) {
        val cleared = restorePreferences.edit()
            .remove(RESTORE_UNDO_FILE_KEY)
            .remove(RESTORE_UNDO_PASSWORD_KEY)
            .commit()
        if (cleared) snapshot.file.delete()
    }

    private fun currentUndoSnapshot(): UndoSnapshot? {
        val fileName = restorePreferences.getString(RESTORE_UNDO_FILE_KEY, null)
            ?.takeIf { it.startsWith(RESTORE_UNDO_PREFIX) && File(it).name == it }
            ?: return null
        val password = restorePreferences.getString(RESTORE_UNDO_PASSWORD_KEY, null)
            ?.takeIf { it.length >= 8 }
            ?: return null
        val file = File(appContext.filesDir, fileName).takeIf(File::isFile) ?: return null
        return UndoSnapshot(file, password)
    }

    private fun installRestoredData(
        stagingDirectory: File,
        restoredEntries: List<JournalEntry>,
    ): List<JournalEntry> {
        val installedFiles = mutableListOf<File>()
        return try {
            val renamedImages = stagingDirectory.listFiles().orEmpty().associate { stagedFile ->
                val newName = "${UUID.randomUUID()}.xike-image"
                val installedFile = File(imagesDirectory, newName)
                encryptedImage(stagedFile).openFileInput().use { decryptedImage ->
                    writeEncryptedImage(
                        target = installedFile,
                        input = decryptedImage,
                        byteLimit = MAX_IMAGE_BYTES,
                        totalBytes = null,
                    )
                }
                installedFiles += installedFile
                stagedFile.name to newName
            }
            val installedEntries = restoredEntries.map { entry ->
                entry.copy(imageFileNames = entry.imageFileNames.mapNotNull(renamedImages::get))
            }

            writeEntries(installedEntries)
            val referencedFiles = installedEntries.flatMap { it.imageFileNames }.toSet()
            imagesDirectory.listFiles()
                ?.filter { it.name !in referencedFiles }
                ?.forEach(File::delete)
            stagingDirectory.deleteRecursively()
            installedEntries
        } catch (error: Throwable) {
            installedFiles.forEach(File::delete)
            throw error
        }
    }

    private fun encryptedImage(file: File): EncryptedFile = EncryptedFile.Builder(
        appContext,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

    private fun deleteImage(fileName: String) {
        if (isSafeImageFileName(fileName)) File(imagesDirectory, fileName).delete()
    }

    private fun isSafeImageFileName(fileName: String): Boolean =
        fileName.isNotBlank() &&
            fileName.length <= MAX_FILE_NAME_LENGTH &&
            File(fileName).name == fileName &&
            !fileName.contains('/') &&
            !fileName.contains('\\')

    private fun readEntries(): List<JournalEntry> = try {
        dao.records().map(JournalEntryRecord::toJournalEntry)
    } catch (error: JournalDataException) {
        throw error
    } catch (error: Throwable) {
        throw JournalDataException("日记数据库暂时无法读取，原数据未被覆盖。", error)
    }

    private fun ensureSearchIndex() {
        if (dao.settingValue(SEARCH_INDEX_SETTING) == SEARCH_INDEX_VERSION) return
        val bundles = dao.records().map { it.toJournalEntry().toBundle() }
        dao.rebuildSearchIndex(bundles)
    }

    private fun readLegacyEntries(): List<JournalEntry> {
        val serialized = runCatching { legacyPreferences.getString(ENTRIES_KEY, null) }
            .getOrElse { error ->
                throw JournalDataException("旧版日记数据暂时无法读取，原数据未被覆盖。", error)
            }
            ?: return emptyList()
        return try {
            val values = JSONArray(serialized)
            List(values.length()) { index -> JournalEntry.fromJson(values.getJSONObject(index)) }
        } catch (error: Throwable) {
            throw JournalDataException("旧版日记数据暂时无法读取，原数据未被覆盖。", error)
        }
    }

    private fun writeEntries(entries: List<JournalEntry>) {
        runCatching { dao.replaceJournals(entries.map(JournalEntry::toBundle)) }
            .getOrElse { error -> throw JournalDataException("恢复内容写入失败，原数据未被覆盖。", error) }
    }

    private companion object {
        const val LEGACY_PREFERENCES_NAME = "xike-journal"
        const val RESTORE_PREFERENCES_NAME = "xike-restore"
        const val ENTRIES_KEY = "entries"
        const val THEME_KEY = "theme"
        const val RESTORE_UNDO_FILE_KEY = "undo-file"
        const val RESTORE_UNDO_PASSWORD_KEY = "undo-password"
        const val IMAGES_DIRECTORY = "journal-images"
        const val RESTORE_STAGING_PREFIX = "journal-images-restore-"
        const val RESTORE_UNDO_PREFIX = "journal-restore-undo-"
        const val MANIFEST_ENTRY = "manifest.json"
        const val IMAGES_PREFIX = "images/"
        const val STREAMING_BACKUP_VERSION = 4
        const val MAX_FILE_NAME_LENGTH = 160
        const val MAX_BACKUP_ENTRIES = 100_000
        const val MAX_BACKUP_IMAGES = 10_000
        const val MAX_MANIFEST_BYTES = 16L * 1024L * 1024L
        const val MAX_LEGACY_BACKUP_BYTES = 320L * 1024L * 1024L
        const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
        const val MAX_BACKUP_IMAGE_BYTES = 1024L * 1024L * 1024L
    }
}

private class BackupValidationException(message: String) : IllegalArgumentException(message)

private inline fun validateBackup(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw BackupValidationException(lazyMessage())
}

private fun InputStream.readAvailable(target: ByteArray): Int {
    var offset = 0
    while (offset < target.size) {
        val count = read(target, offset, target.size - offset)
        if (count < 0) break
        offset += count
    }
    return offset
}

private fun InputStream.readUtf8Limited(limit: Long): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        validateBackup(total <= limit) { "备份文件过大。" }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal object BackupCipher {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private val streamingMagic = byteArrayOf('L'.code.toByte(), 'I'.code.toByte(), 'U'.code.toByte(), 'B'.code.toByte(), 'A'.code.toByte(), 'I'.code.toByte(), 4)

    val magicSize: Int get() = streamingMagic.size

    fun hasStreamingMagic(value: ByteArray): Boolean = value.contentEquals(streamingMagic)

    fun encryptingStream(output: OutputStream, password: String): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        output.write(streamingMagic)
        output.write(salt)
        output.write(nonce)
        output.flush()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return CipherOutputStream(output, cipher)
    }

    fun decryptingStream(input: InputStream, password: String): InputStream {
        val salt = ByteArray(SALT_BYTES)
        val nonce = ByteArray(NONCE_BYTES)
        validateBackup(input.readAvailable(salt) == salt.size) { "备份文件头不完整。" }
        validateBackup(input.readAvailable(nonce) == nonce.size) { "备份文件头不完整。" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return CipherInputStream(input, cipher)
    }

    fun encryptLegacy(plainText: String, password: String): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", "xike-encrypted")
            .put("version", 1)
            .put("salt", Base64.getEncoder().encodeToString(salt))
            .put("nonce", Base64.getEncoder().encodeToString(nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(encrypted))
            .toString()
    }

    fun decryptLegacy(payload: String, password: String): String {
        val backup = JSONObject(payload)
        validateBackup(backup.optString("format") == "xike-encrypted") { "备份未加密或文件格式不正确。" }
        val salt = Base64.getDecoder().decode(backup.getString("salt"))
        val nonce = Base64.getDecoder().decode(backup.getString("nonce"))
        val ciphertext = Base64.getDecoder().decode(backup.getString("ciphertext"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)).encoded
        return SecretKeySpec(derived, "AES")
    }
}
