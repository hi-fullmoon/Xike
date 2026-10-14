package com.xike.app

import com.xike.app.media.JournalAttachmentStore
import com.xike.app.media.MAX_IMAGE_BYTES
import com.xike.app.media.MAX_BACKUP_AUDIO_BYTES
import com.xike.app.data.backup.JournalBackupStorage
import com.xike.app.data.backup.RESTORE_STAGING_PREFIX

import com.xike.app.data.model.JournalVideo
import com.xike.app.media.JournalVideoStore
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.data.model.files

import com.xike.app.data.Mood
import com.xike.app.data.MAX_IMAGES_PER_ENTRY
import com.xike.app.data.MAX_AUDIO_BYTES
import com.xike.app.data.JournalAudio
import com.xike.app.data.JournalEntry
import com.xike.app.data.JournalDataException
import com.xike.app.data.JournalSnapshot
import com.xike.app.data.BackupSummary

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    private val videos = JournalVideoStore(appContext)
    private val videoDraftStore by lazy { JournalDraftStore(appContext) }
    private val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val attachments = JournalAttachmentStore(appContext, masterKey)
    private val backups = JournalBackupStorage(appContext, attachments, videos)
    private val database by lazy { JournalDatabase.get(appContext) }
    private val dao by lazy { database.journalDao() }
    val readRepository by lazy { JournalReadRepository { database } }
    private val editAudioPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "xike-edit-audio-references",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
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
    private var pendingDeletedEntry: JournalEntry? = null

    init {
        attachments.initializeDirectories()
    }

    @Synchronized
    fun initialize(loadEntries: Boolean = true): JournalSnapshot = try {
        if (dao.settingValue(LEGACY_MIGRATION_SETTING) == null) {
            val legacyEntries = readLegacyEntries()
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
            val legacyTheme = runCatching { legacyPreferences.getString(THEME_KEY, null) }
                .getOrElse { error ->
                    throw JournalDataException(localizedText("旧版外观设置暂时无法读取，原数据未被覆盖。"), error)
            }
            dao.importLegacyIfNeeded(legacyEntries.map(JournalEntry::toBundle), legacyTheme)
        }
        ensureSearchIndex()
        JournalSnapshot(
            entries = if (loadEntries) readEntries() else emptyList(),
            themeName = dao.settingValue(THEME_SETTING),
            styleName = dao.settingValue(THEME_STYLE_SETTING),
        )
    } catch (error: JournalDataException) {
        throw error
    } catch (error: Throwable) {
        throw JournalDataException(localizedText("加密数据库初始化失败，原数据未被覆盖。"), error)
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
        require(offset >= 0) { localizedText("搜索偏移量不能小于 0。") }
        require(limit in 1..200) { localizedText("每页搜索结果需要在 1 到 200 条之间。") }
        val range = query.epochRange(zoneId)
        val hasText = if (query.normalizedText.isEmpty()) 0 else 1
        val ftsQuery = query.normalizedText.takeIf(String::isNotEmpty)?.let(::journalFtsQuery) ?: "\"\""
        val filterMoods = if (query.moods.isEmpty()) 0 else 1
        val moods = query.moods.map(Mood::name).ifEmpty { listOf(Mood.CALM.name) }
        val filterTags = if (query.tags.isEmpty()) 0 else 1
        val tags = expandedTopicLabels(query.tags).ifEmpty { listOf("") }
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
        throw JournalDataException(localizedText("日记搜索暂时不可用，请重试。"), error)
    }

    @Synchronized
    fun add(entry: JournalEntry, imageUris: List<Uri> = emptyList(), refreshEntries: Boolean = true): List<JournalEntry> {
        val imported = attachments.importImages(imageUris)
        return runCatching {
            val storedEntry = entry.copy(imageFileNames = imported)
            attachments.validateAudioReference(storedEntry.audio)
            videos.requireReadable(storedEntry.video)
            dao.insertJournal(storedEntry.toBundle())
            storedEntry
        }.onFailure {
            imported.forEach(attachments::deleteImage)
        }.getOrElse { error ->
            if (error is JournalDataException) throw error
            throw JournalDataException(localizedText("日记保存失败，请重试。"), error)
        }.let { storedEntry -> if (refreshEntries) readEntries() else listOf(storedEntry) }
    }

    @Synchronized
    fun update(
        entry: JournalEntry,
        retainedImageFileNames: List<String>,
        newImageUris: List<Uri> = emptyList(),
        refreshEntries: Boolean = true,
    ): List<JournalEntry> {
        require(entry.id.isNotBlank()) { localizedText("记录标识不能为空。") }
        val original = dao.record(entry.id)?.toJournalEntry()
            ?: throw JournalDataException(localizedText("记录不存在或已经删除。"), NoSuchElementException(entry.id))
        val retainedImages = retainedImageFileNames.distinct()
        require(retainedImages.all { it in original.imageFileNames }) { localizedText("记录包含无效的照片引用。") }
        require(retainedImages.size + newImageUris.size <= MAX_IMAGES_PER_ENTRY) {
            localizedText("每条记录最多添加 9 张图片。")
        }

        val imported = attachments.importImages(newImageUris)
        val storedEntry = entry.copy(imageFileNames = retainedImages + imported)
        val previousImages = runCatching {
            attachments.validateAudioReference(storedEntry.audio)
            videos.requireReadable(storedEntry.video)
            dao.updateJournal(storedEntry.toBundle())
        }
            .onFailure { imported.forEach(attachments::deleteImage) }
            .getOrElse { error ->
                if (error is JournalDataException) throw error
                throw JournalDataException(localizedText("记录修改失败，请重试。"), error)
            }
        val referencedImages = referencedImageFileNames()
        previousImages
            .filterNot { it in storedEntry.imageFileNames || it in referencedImages }
            .forEach(attachments::deleteImage)
        original.audio?.fileName
            ?.takeIf { it != storedEntry.audio?.fileName && it !in referencedAudioFileNames() }
            ?.let(attachments::deleteAudio)
        original.video?.let { deleteUnreferencedVideo(it) }
        return if (refreshEntries) readEntries() else listOf(storedEntry)
    }

    @Synchronized
    fun delete(entryId: String, refreshEntries: Boolean = true): List<JournalEntry> = runCatching {
        require(entryId.isNotBlank()) { localizedText("记录标识不能为空。") }
        finalizePendingDelete()
        val deletedEntry = dao.record(entryId)?.toJournalEntry()
            ?: error(localizedText("记录不存在或已经删除。"))
        dao.deleteJournal(entryId)
        pendingDeletedEntry = deletedEntry
        if (refreshEntries) readEntries() else emptyList()
    }.getOrElse { error ->
        if (error is JournalDataException) throw error
        throw JournalDataException(localizedText("记录删除失败，请重试。"), error)
    }

    @Synchronized
    fun undoDelete(entryId: String, refreshEntries: Boolean = true): List<JournalEntry> = runCatching {
        val deletedEntry = pendingDeletedEntry
            ?.takeIf { it.id == entryId }
            ?: error(localizedText("这条记录的撤销期限已经结束。"))
        val missingImage = deletedEntry.imageFileNames.firstOrNull { fileName ->
            !File(attachments.imagesDirectory, fileName).isFile
        }
        check(missingImage == null) { localizedText("记录照片已经清理，无法撤销删除。") }
        val missingAudio = deletedEntry.audio?.fileName?.takeIf { !File(attachments.audiosDirectory, it).isFile }
        check(missingAudio == null) { localizedText("记录语音已经清理，无法撤销删除。") }
        videos.requireReadable(deletedEntry.video)
        dao.insertJournal(deletedEntry.toBundle())
        pendingDeletedEntry = null
        if (refreshEntries) readEntries() else listOf(deletedEntry)
    }.getOrElse { error ->
        if (error is JournalDataException) throw error
        throw JournalDataException(localizedText("撤销删除失败，请重试。"), error)
    }

    @Synchronized
    fun finalizeDelete(entryId: String) {
        if (pendingDeletedEntry?.id == entryId) finalizePendingDelete()
    }

    fun savedThemeName(): String? = dao.settingValue(THEME_SETTING)

    @Synchronized
    fun saveThemeName(themeName: String) {
        runCatching { dao.putSetting(AppSettingEntity(THEME_SETTING, themeName)) }
            .getOrElse { error -> throw JournalDataException(localizedText("外观设置保存失败，请重试。"), error) }
    }

    @Synchronized
    fun saveStyleName(styleName: String) {
        runCatching { dao.putSetting(AppSettingEntity(THEME_STYLE_SETTING, styleName)) }
            .getOrElse { error -> throw JournalDataException(localizedText("界面风格保存失败，请重试。"), error) }
    }

    fun openImage(fileName: String): InputStream? = attachments.openImage(fileName)

    @Synchronized
    fun importAudio(source: File, durationMillis: Long): JournalAudio = attachments.importAudio(source, durationMillis)

    @Synchronized
    fun importAudio(input: InputStream, durationMillis: Long): JournalAudio = attachments.importAudio(input, durationMillis)

    fun openAudio(fileName: String): InputStream? = attachments.openAudio(fileName)

    internal fun requireReadableAudio(fileName: String) = attachments.requireReadableAudio(fileName)

    @Synchronized
    fun deleteUnreferencedAudio(fileName: String) {
        if (fileName !in referencedAudioFileNames()) attachments.deleteAudio(fileName)
    }

    @Synchronized
    fun importEditAudio(source: File, durationMillis: Long): JournalAudio {
        return FileInputStream(source).use { importEditAudio(it, durationMillis) }
    }

    @Synchronized
    fun importEditAudio(source: InputStream, durationMillis: Long): JournalAudio {
        val audio = importAudio(source, durationMillis)
        try {
            check(editAudioPreferences.edit().putBoolean(audio.fileName, true).commit())
        } catch (error: Throwable) {
            attachments.deleteAudio(audio.fileName)
            throw error
        }
        return audio
    }

    @Synchronized
    fun releaseEditAudio(audio: JournalAudio) {
        check(editAudioPreferences.edit().remove(audio.fileName).commit())
        deleteUnreferencedAudio(audio.fileName)
    }

    @Synchronized
    fun removeOrphanedMedia(activeDraftAudioFileName: String? = null, activeDraftVideo: JournalVideo? = null) {
        cleanupStaleVideoEdits()
        val referencedImages = referencedImageFileNames()
        val referencedAudios = referencedAudioFileNames() + listOfNotNull(activeDraftAudioFileName)
        val activeUndoFileName = currentUndoSnapshot()?.file?.name
        videos.prune(referencedVideoFiles() + activeDraftVideo.files())
        attachments.imagesDirectory.listFiles()
            ?.filter { it.name !in referencedImages }
            ?.forEach(File::delete)
        attachments.audiosDirectory.listFiles()
            ?.filter { it.name !in referencedAudios }
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

    @Deprecated("Use removeOrphanedMedia so draft audio can be retained")
    fun removeOrphanedImages() = removeOrphanedMedia()

    @Synchronized
    fun writeEncryptedBackup(output: OutputStream, password: String) = writeBackup(output, password)

    @Synchronized
    fun writeBackup(output: OutputStream, password: String?) {
        if (password != null) require(password.length >= 8) { localizedText("备份密码至少需要 8 位。") }
        backups.writeBackup(readEntries(), output, password)
    }

    fun backupRequiresPassword(input: InputStream): Boolean = backups.backupRequiresPassword(input)

    @Synchronized
    fun inspectEncryptedBackup(input: InputStream, password: String): BackupSummary = inspectBackup(input, password)

    @Synchronized
    fun inspectBackup(input: InputStream, password: String?): BackupSummary = backups.inspectBackup(input, password)

    @Synchronized
    fun restoreEncryptedBackup(
        input: InputStream,
        password: String,
        retainedAudioFileNames: Set<String> = emptySet(),
    ): List<JournalEntry> = restoreBackup(input, password, retainedAudioFileNames)

    @Synchronized
    fun restoreBackup(
        input: InputStream,
        password: String?,
        retainedAudioFileNames: Set<String> = emptySet(),
        retainedVideo: JournalVideo? = null,
    ): List<JournalEntry> {
        val prepared = backups.readBackup(input, password)
        return try {
            val snapshotSwap = createUndoSnapshot()
            try {
                installRestoredData(prepared.stagingDirectory, prepared.entries, retainedAudioFileNames, retainedVideo).also {
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
    fun undoLastRestore(retainedAudioFileNames: Set<String> = emptySet(), retainedVideo: JournalVideo? = null): List<JournalEntry> {
        val snapshot = currentUndoSnapshot() ?: error(localizedText("没有可撤销的恢复操作。"))
        val prepared = snapshot.file.inputStream().use { input ->
            backups.readBackup(input, snapshot.password)
        }
        return try {
            installRestoredData(prepared.stagingDirectory, prepared.entries, retainedAudioFileNames, retainedVideo).also {
                clearUndoSnapshot(snapshot)
            }
        } finally {
            prepared.stagingDirectory.deleteRecursively()
        }
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
            if (!saved) error(localizedText("无法保存撤销快照信息。"))
            return UndoSnapshotSwap(previous, snapshot)
        } catch (error: Throwable) {
            snapshot.file.delete()
            throw JournalDataException(localizedText("无法创建恢复撤销快照，设备内容未被替换。"), error)
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
        retainedAudioFileNames: Set<String>,
        retainedVideo: JournalVideo? = null,
    ): List<JournalEntry> {
        val installedFiles = mutableListOf<File>()
        return try {
            val stagedFiles = stagingDirectory.listFiles().orEmpty()
            val imageNames = restoredEntries.flatMap { it.imageFileNames }.toSet()
            val audioNames = restoredEntries.mapNotNull { it.audio?.fileName }.toSet()
            val renamedImages = stagedFiles.filter { it.name in imageNames }.associate { stagedFile ->
                val newName = "${UUID.randomUUID()}.xike-image"
                val installedFile = File(attachments.imagesDirectory, newName)
                attachments.openEncrypted(stagedFile).use { decryptedImage ->
                    attachments.writeEncryptedImage(
                        target = installedFile,
                        input = decryptedImage,
                        byteLimit = MAX_IMAGE_BYTES,
                        totalBytes = null,
                    )
                }
                installedFiles += installedFile
                stagedFile.name to newName
            }
            val renamedAudios = stagedFiles.filter { it.name in audioNames }.associate { stagedFile ->
                val newName = "${UUID.randomUUID()}.xike-audio"
                val installedFile = File(attachments.audiosDirectory, newName)
                attachments.openEncrypted(stagedFile).use { decryptedAudio ->
                    attachments.writeEncryptedAttachment(
                        target = installedFile,
                        input = decryptedAudio,
                        byteLimit = MAX_AUDIO_BYTES,
                        totalBytes = null,
                        itemName = localizedText("录音"),
                        totalName = localizedText("备份录音"),
                        totalLimit = MAX_BACKUP_AUDIO_BYTES,
                    )
                }
                installedFiles += installedFile
                stagedFile.name to newName
            }
            val renamedVideos = restoredEntries.mapNotNull { it.video }.associateWith { video ->
                videos.install(stagingDirectory, video).also { installedFiles.addAll(videos.filesOnDisk(it)) }
            }
            val installedEntries = restoredEntries.map { entry ->
                entry.copy(
                    video = entry.video?.let(renamedVideos::getValue),
                    imageFileNames = entry.imageFileNames.mapNotNull(renamedImages::get),
                    audio = entry.audio?.let { audio ->
                        renamedAudios[audio.fileName]?.let { audio.copy(fileName = it) }
                    },
                )
            }

            val protectedVideos = retainedVideo.files() + videoDraftStore.load().video.files() + videos.editReferences()
            writeEntries(installedEntries)
            runCatching { cleanupStaleVideoEdits() }
            videos.prune(installedEntries.flatMap { it.video.files() }.toSet() + protectedVideos)
            val referencedFiles = installedEntries.flatMap { it.imageFileNames }.toSet()
            attachments.imagesDirectory.listFiles()
                ?.filter { it.name !in referencedFiles }
                ?.forEach(File::delete)
            val referencedAudios = installedEntries.mapNotNull { it.audio?.fileName }.toSet() + retainedAudioFileNames
            attachments.audiosDirectory.listFiles()
                ?.filter { it.name !in referencedAudios }
                ?.forEach(File::delete)
            stagingDirectory.deleteRecursively()
            installedEntries
        } catch (error: Throwable) {
            installedFiles.forEach(File::delete)
            throw error
        }
    }

    private fun referencedImageFileNames(): Set<String> = buildSet {
        addAll(dao.imageFileNames())
        pendingDeletedEntry?.imageFileNames?.let(::addAll)
    }

    private fun referencedAudioFileNames(): Set<String> = buildSet {
        addAll(dao.audioFileNames())
        pendingDeletedEntry?.audio?.fileName?.let(::add)
        addAll(editAudioPreferences.all.keys)
    }

    private fun referencedVideoFiles(): Set<String> =
        dao.videoReferences().flatMap { it.video().files() }.toSet() + pendingDeletedEntry?.video.files() + videoDraftStore.load().video.files() + videos.editReferences()

    private fun cleanupStaleVideoEdits() {
        val references = dao.videoReferences().associate { it.id to it.video()?.fileName }.toMutableMap()
        pendingDeletedEntry?.let { references[it.id] = it.video?.fileName }
        videos.discardStaleEdits(references).forEach(::deleteUnreferencedVideo)
    }

    @Synchronized
    fun importVideo(uri: Uri, onProgress: (Float) -> Unit = {}): JournalVideo = videos.import(uri, onProgress)

    @Synchronized
    fun deleteUnreferencedVideo(video: JournalVideo) {
        // A failed reference read must never turn into an empty reference set.
        val references = runCatching(::referencedVideoFiles).getOrNull() ?: return
        videos.deleteExcept(video, references)
    }

    internal fun requireReadableVideo(video: JournalVideo?) = videos.requireReadable(video)

    private fun finalizePendingDelete() {
        val deletedEntry = pendingDeletedEntry ?: return
        pendingDeletedEntry = null
        runCatching { cleanupStaleVideoEdits() }
        val referencedImages = dao.imageFileNames().toSet()
        deletedEntry.imageFileNames
            .filterNot { it in referencedImages }
            .forEach(attachments::deleteImage)
        deletedEntry.audio?.fileName
            ?.takeIf { it !in referencedAudioFileNames() }
            ?.let(attachments::deleteAudio)
        deletedEntry.video?.let(::deleteUnreferencedVideo)
    }

    private fun readEntries(): List<JournalEntry> = try {
        dao.records().map(JournalEntryRecord::toJournalEntry)
    } catch (error: JournalDataException) {
        throw error
    } catch (error: Throwable) {
        throw JournalDataException(localizedText("日记数据库暂时无法读取，原数据未被覆盖。"), error)
    }

    private fun ensureSearchIndex() {
        if (dao.settingValue(SEARCH_INDEX_SETTING) == SEARCH_INDEX_VERSION) return
        dao.rebuildSearchIndexFromRecords()
    }

    private fun readLegacyEntries(): List<JournalEntry> {
        val serialized = runCatching { legacyPreferences.getString(ENTRIES_KEY, null) }
            .getOrElse { error ->
                throw JournalDataException(localizedText("旧版日记数据暂时无法读取，原数据未被覆盖。"), error)
            }
            ?: return emptyList()
        return try {
            val values = JSONArray(serialized)
            List(values.length()) { index -> JournalEntry.fromJson(values.getJSONObject(index)) }
        } catch (error: Throwable) {
            throw JournalDataException(localizedText("旧版日记数据暂时无法读取，原数据未被覆盖。"), error)
        }
    }

    private fun writeEntries(entries: List<JournalEntry>) {
        runCatching { dao.replaceJournals(entries.map(JournalEntry::toBundle)) }
            .getOrElse { error -> throw JournalDataException(localizedText("恢复内容写入失败，原数据未被覆盖。"), error) }
    }

    private companion object {
        const val LEGACY_PREFERENCES_NAME = "xike-journal"
        const val RESTORE_PREFERENCES_NAME = "xike-restore"
        const val ENTRIES_KEY = "entries"
        const val THEME_KEY = "theme"
        const val RESTORE_UNDO_FILE_KEY = "undo-file"
        const val RESTORE_UNDO_PASSWORD_KEY = "undo-password"
        const val RESTORE_UNDO_PREFIX = "journal-restore-undo-"
    }
}
