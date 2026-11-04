package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.BackupSummary
import com.xike.app.data.JournalAudio
import com.xike.app.data.JournalDataException
import com.xike.app.data.JournalEntry
import com.xike.app.data.JournalSnapshot
import com.xike.app.data.MAX_AUDIO_BYTES
import com.xike.app.data.MAX_IMAGES_PER_ENTRY
import com.xike.app.data.Mood
import com.xike.app.data.attachmentDigest
import com.xike.app.data.backup.JournalBackupStorage
import com.xike.app.data.backup.RESTORE_STAGING_PREFIX
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.data.model.JournalVideo
import com.xike.app.data.model.files
import com.xike.app.data.restoreContentKey
import com.xike.app.media.JournalAttachmentStore
import com.xike.app.media.JournalVideoStore
import com.xike.app.media.MAX_BACKUP_AUDIO_BYTES
import com.xike.app.media.MAX_IMAGE_BYTES
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
import org.json.JSONArray

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
    private val masterKey =
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
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
    fun initialize(loadEntries: Boolean = true): JournalSnapshot =
        try {
            dao.ensureLabelCatalog(
                preserveLegacyTopics =
                    dao.settingValue(LEGACY_MIGRATION_SETTING) == null &&
                        (legacyPreferences.contains(ENTRIES_KEY) ||
                            legacyPreferences.contains(THEME_KEY))
            )
            if (dao.settingValue(LEGACY_MIGRATION_SETTING) == null) {
                val legacyEntries =
                    readLegacyEntries().distinctBy { it.id }.sortedByDescending { it.createdAt }
                val legacyTheme = runCatching {
                    legacyPreferences.getString(THEME_KEY, null)
                }
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

    @Synchronized
    fun setFavorite(entryId: String, favorite: Boolean): JournalEntry {
        val saved = dao.setFavorite(entryId, favorite)
        discardInvalidUndoSnapshot()
        return saved
    }

    @Synchronized
    fun saveLabel(value: com.xike.app.data.JournalLabel) {
        database.runInTransaction {
            dao.ensureLabelCatalog()
            com.xike.app.data.validateLabelName(value.name)
            require(value.iconKey in com.xike.app.data.labelIconKeys)
            val catalog = dao.labels().map { it.toLabel() }
            val previous = catalog.firstOrNull { it.id == value.id }
            require(previous == null || previous.kind == value.kind)
            require(previous == null || previous.isBuiltin == value.isBuiltin)
            require(!value.isBuiltin || previous?.name == value.name)
            require(previous != null || !value.isBuiltin && !value.id.startsWith("builtin:"))
            val key = com.xike.app.data.normalizedLabelName(value.name)
            require(
                previous?.name == value.name ||
                    catalog.none { other ->
                        other.id != value.id &&
                            other.kind == value.kind &&
                            other.searchNames.any {
                                com.xike.app.data.normalizedLabelName(it) == key
                            }
                    }
            ) {
                tr(
                    "同类中已存在这个名称，请修改名称或恢复已归档项。",
                    "This name already exists in this category. Rename it or restore the archived item.",
                )
            }
            if (previous == null)
                dao.insertLabels(listOf(value.copy(name = value.name.trim()).toEntity()))
            dao.saveLabel(value.copy(name = value.name.trim()).toEntity())
        }
    }

    @Synchronized
    fun moveLabel(id: String, direction: Int) {
        require(direction == -1 || direction == 1)
        database.runInTransaction {
            val catalog = dao.labels().map { it.toLabel() }
            val selected = catalog.first { it.id == id }
            val group =
                catalog
                    .filter { it.kind == selected.kind && it.isArchived == selected.isArchived }
                    .toMutableList()
            val position = group.indexOfFirst { it.id == id }
            val next = position + direction
            if (next !in group.indices) return@runInTransaction
            java.util.Collections.swap(group, position, next)
            group.forEachIndexed { index, label ->
                dao.saveLabel(label.copy(sortOrder = index).toEntity())
            }
        }
    }

    fun observeEntries(): Flow<List<JournalEntry>> =
        dao.observeRecords().map { records -> records.map(JournalEntryRecord::toJournalEntry) }

    fun search(
        query: JournalSearchQuery,
        offset: Int = 0,
        limit: Int = 60,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): JournalSearchPage =
        try {
            require(offset >= 0) { localizedText("搜索偏移量不能小于 0。") }
            require(limit in 1..200) { localizedText("每页搜索结果需要在 1 到 200 条之间。") }
            database.runInTransaction<JournalSearchPage> {
                val records =
                    dao.recordsForQuery(
                            readRepository.sql(
                                query,
                                "e.*",
                                ordered = true,
                                offset = offset,
                                zoneId = zoneId,
                                pageSize = limit,
                            )
                        )
                        .map(JournalEntryRecord::toJournalEntry)
                val count = dao.queryCount(readRepository.sql(query, "COUNT(*)", zoneId = zoneId))
                JournalSearchPage(records, count, offset)
            }
        } catch (error: Throwable) {
            throw JournalDataException(localizedText("日记搜索暂时不可用，请重试。"), error)
        }

    @Synchronized
    fun add(
        entry: JournalEntry,
        imageUris: List<Uri> = emptyList(),
        refreshEntries: Boolean = true,
    ): List<JournalEntry> {
        val imported = attachments.importImages(imageUris)
        return runCatching {
            val catalog = dao.labels().map { it.toLabel() }.associateBy { it.id }
            val storedEntry =
                entry.copy(
                    imageFileNames = imported,
                    labels =
                        entry.labels.map { selected ->
                            if (selected.isBuiltin) selected
                            else
                                catalog[selected.id]
                                    ?.takeIf { it.kind == selected.kind }
                                    ?.selection() ?: selected
                        },
                )
            attachments.validateAudioReference(storedEntry.audio)
            videos.requireReadable(storedEntry.video)
            dao.insertJournal(storedEntry.toBundle())
            discardInvalidUndoSnapshot()
            storedEntry
        }
            .onFailure {
                imported.forEach(attachments::deleteImage)
            }
            .getOrElse { error ->
                if (error is JournalDataException) throw error
                throw JournalDataException(localizedText("日记保存失败，请重试。"), error)
            }
            .let { storedEntry -> if (refreshEntries) readEntries() else listOf(storedEntry) }
    }

    @Synchronized
    fun update(
        entry: JournalEntry,
        retainedImageFileNames: List<String>,
        newImageUris: List<Uri> = emptyList(),
        refreshEntries: Boolean = true,
    ): List<JournalEntry> {
        require(entry.id.isNotBlank()) { localizedText("记录标识不能为空。") }
        val original =
            dao.record(entry.id)?.toJournalEntry()
                ?: throw JournalDataException(
                    localizedText("记录不存在或已经删除。"),
                    NoSuchElementException(entry.id),
                )
        val retainedImages = retainedImageFileNames.distinct()
        require(retainedImages.all { it in original.imageFileNames }) {
            localizedText("记录包含无效的照片引用。")
        }
        require(retainedImages.size + newImageUris.size <= MAX_IMAGES_PER_ENTRY) {
            localizedText("每条记录最多添加 9 张图片。")
        }

        val imported = attachments.importImages(newImageUris)
        val storedEntry =
            entry.copy(imageFileNames = retainedImages + imported, isFavorite = original.isFavorite)
        val previousImages = runCatching {
            attachments.validateAudioReference(storedEntry.audio)
            videos.requireReadable(storedEntry.video)
            dao.updateJournal(storedEntry.toBundle()).also { discardInvalidUndoSnapshot() }
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
        original.audio
            ?.fileName
            ?.takeIf { it != storedEntry.audio?.fileName && it !in referencedAudioFileNames() }
            ?.let(attachments::deleteAudio)
        original.video?.let { deleteUnreferencedVideo(it) }
        return if (refreshEntries) readEntries() else listOf(storedEntry)
    }

    @Synchronized
    fun delete(entryId: String, refreshEntries: Boolean = true): List<JournalEntry> = runCatching {
        require(entryId.isNotBlank()) { localizedText("记录标识不能为空。") }
        finalizePendingDelete()
        val deletedEntry =
            dao.record(entryId)?.toJournalEntry() ?: error(localizedText("记录不存在或已经删除。"))
        dao.deleteJournal(entryId)
        discardInvalidUndoSnapshot()
        pendingDeletedEntry = deletedEntry
        if (refreshEntries) readEntries() else emptyList()
    }
        .getOrElse { error ->
            if (error is JournalDataException) throw error
            throw JournalDataException(localizedText("记录删除失败，请重试。"), error)
        }

    @Synchronized
    fun undoDelete(entryId: String, refreshEntries: Boolean = true): List<JournalEntry> =
        runCatching {
            val deletedEntry =
                pendingDeletedEntry?.takeIf { it.id == entryId }
                    ?: error(localizedText("这条记录的撤销期限已经结束。"))
            val missingImage =
                deletedEntry.imageFileNames.firstOrNull { fileName ->
                    !File(attachments.imagesDirectory, fileName).isFile
                }
            check(missingImage == null) { localizedText("记录照片已经清理，无法撤销删除。") }
            val missingAudio =
                deletedEntry.audio?.fileName?.takeIf {
                    !File(attachments.audiosDirectory, it).isFile
                }
            check(missingAudio == null) { localizedText("记录语音已经清理，无法撤销删除。") }
            videos.requireReadable(deletedEntry.video)
            dao.insertJournal(deletedEntry.toBundle())
            discardInvalidUndoSnapshot()
            pendingDeletedEntry = null
            if (refreshEntries) readEntries() else listOf(deletedEntry)
        }
        .getOrElse { error ->
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
            .getOrElse { error ->
                throw JournalDataException(localizedText("外观设置保存失败，请重试。"), error)
            }
    }

    @Synchronized
    fun saveStyleName(styleName: String) {
        runCatching { dao.putSetting(AppSettingEntity(THEME_STYLE_SETTING, styleName)) }
            .getOrElse { error ->
                throw JournalDataException(localizedText("界面风格保存失败，请重试。"), error)
            }
    }

    fun openImage(fileName: String): InputStream? = attachments.openImage(fileName)

    @Synchronized
    fun importAudio(source: File, durationMillis: Long): JournalAudio =
        attachments.importAudio(source, durationMillis)

    @Synchronized
    fun importAudio(input: InputStream, durationMillis: Long): JournalAudio =
        attachments.importAudio(input, durationMillis)

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
    fun removeOrphanedMedia(
        activeDraftAudioFileName: String? = null,
        activeDraftVideo: JournalVideo? = null,
    ) {
        cleanupStaleVideoEdits()
        val referencedImages = referencedImageFileNames()
        val referencedAudios = referencedAudioFileNames() + listOfNotNull(activeDraftAudioFileName)
        val activeUndoFileName = currentUndoSnapshot()?.file?.name
        videos.prune(referencedVideoFiles() + activeDraftVideo.files())
        attachments.imagesDirectory
            .listFiles()
            ?.filter { it.name !in referencedImages }
            ?.forEach(File::delete)
        attachments.audiosDirectory
            .listFiles()
            ?.filter { it.name !in referencedAudios }
            ?.forEach(File::delete)
        appContext.filesDir
            .listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(RESTORE_STAGING_PREFIX) }
            ?.forEach(File::deleteRecursively)
        appContext.filesDir
            .listFiles()
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
        dao.ensureLabelCatalog()
        backups.writeBackup(readEntries(), output, password, dao.labels().map { it.toLabel() })
    }

    fun backupRequiresPassword(input: InputStream): Boolean = backups.backupRequiresPassword(input)

    @Synchronized
    fun inspectEncryptedBackup(input: InputStream, password: String): BackupSummary =
        inspectBackup(input, password)

    @Synchronized
    fun inspectBackup(input: InputStream, password: String?): BackupSummary {
        val prepared = backups.readBackup(input, password)
        return try {
            val plan =
                try {
                    val labels =
                        com.xike.app.data.planLabelRestore(
                            dao.labels().map { it.toLabel() },
                            prepared.catalog,
                            prepared.entries,
                            true,
                        )
                    planRestoreMerge(prepared.stagingDirectory, readEntries(), labels.entries)
                } catch (_: JournalDataException) {
                    // Incoming validation already succeeded. A merge preview must not prevent the
                    // user from choosing replacement; the actual restore still checks its own plan.
                    null
                }
            BackupSummary(
                entryCount = prepared.entries.size,
                imageCount = prepared.entries.flatMap { it.imageFileNames }.distinct().size,
                audioCount = prepared.entries.mapNotNull { it.audio?.fileName }.distinct().size,
                videoCount = prepared.entries.count { it.video != null },
                videoBytes = prepared.entries.sumOf { it.video?.sizeBytes ?: 0L },
                oldestCreatedAt = prepared.entries.minOfOrNull { it.createdAt },
                newestCreatedAt = prepared.entries.maxOfOrNull { it.createdAt },
                favoriteCount = prepared.entries.count { it.isFavorite },
                mergeNewEntryCount = plan?.let { it.additions.size - it.conflictCount },
                mergeConflictCount = plan?.conflictCount ?: 0,
                mergeFavoriteChangeCount = plan?.favoriteUpdates?.size ?: 0,
            )
        } finally {
            prepared.stagingDirectory.deleteRecursively()
        }
    }

    @Synchronized
    fun restoreEncryptedBackup(
        input: InputStream,
        password: String,
        retainedAudioFileNames: Set<String> = emptySet(),
        mode: BackupRestoreMode = BackupRestoreMode.MERGE,
    ): List<JournalEntry> = restoreBackup(input, password, retainedAudioFileNames, mode = mode)

    @Synchronized
    fun restoreBackup(
        input: InputStream,
        password: String?,
        retainedAudioFileNames: Set<String> = emptySet(),
        retainedVideo: JournalVideo? = null,
        mode: BackupRestoreMode = BackupRestoreMode.MERGE,
    ): List<JournalEntry> {
        val prepared = backups.readBackup(input, password)
        return try {
            val localCatalog = dao.labels().map { it.toLabel() }
            val labelPlan =
                com.xike.app.data.planLabelRestore(
                    localCatalog,
                    prepared.catalog,
                    prepared.entries,
                    mode == BackupRestoreMode.MERGE,
                    videoDraftStore.load().labels,
                )
            val localEntries = if (mode == BackupRestoreMode.MERGE) readEntries() else emptyList()
            val mergePlan =
                if (mode == BackupRestoreMode.MERGE) {
                    planRestoreMerge(prepared.stagingDirectory, localEntries, labelPlan.entries)
                } else
                    com.xike.app.data.JournalMergePlan(
                        labelPlan.entries.distinctBy { it.id },
                        emptySet(),
                    )
            val entriesToInstall = mergePlan.additions
            // A duplicate import is not a mutation and must retain the previous undo snapshot.
            if (
                mode == BackupRestoreMode.MERGE &&
                    entriesToInstall.isEmpty() &&
                    mergePlan.favoriteUpdates.isEmpty() &&
                    labelPlan.catalog.toSet() == localCatalog.toSet()
            )
                return localEntries
            val snapshotSwap = createUndoSnapshot()
            try {
                installRestoredData(
                        prepared.stagingDirectory,
                        entriesToInstall,
                        retainedAudioFileNames,
                        retainedVideo,
                        localEntries.map {
                            if (it.id in mergePlan.favoriteUpdates) it.copy(isFavorite = true)
                            else it
                        },
                        undoToken = snapshotSwap.current.file.name,
                        catalog = labelPlan.catalog,
                    )
                    .also {
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
    fun canUndoLastRestore(): Boolean {
        discardInvalidUndoSnapshot()
        return currentUndoSnapshot() != null
    }

    @Synchronized
    fun undoLastRestore(
        retainedAudioFileNames: Set<String> = emptySet(),
        retainedVideo: JournalVideo? = null,
    ): List<JournalEntry> {
        val snapshot = currentUndoSnapshot() ?: error(localizedText("没有可撤销的恢复操作。"))
        val prepared =
            snapshot.file.inputStream().use { input ->
                backups.readBackup(input, snapshot.password)
            }
        return try {
            installRestoredData(
                    prepared.stagingDirectory,
                    prepared.entries,
                    retainedAudioFileNames,
                    retainedVideo,
                    expectedUndoToken = snapshot.file.name,
                    catalog =
                        com.xike.app.data.catalogWithSelections(
                            prepared.catalog,
                            videoDraftStore.load().labels,
                        ),
                )
                .also {
                    clearUndoSnapshot(snapshot)
                }
        } finally {
            prepared.stagingDirectory.deleteRecursively()
        }
    }

    private fun createUndoSnapshot(): UndoSnapshotSwap {
        val previous = currentUndoSnapshot()
        val passwordBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val snapshot =
            UndoSnapshot(
                file = File(appContext.filesDir, "$RESTORE_UNDO_PREFIX${UUID.randomUUID()}.xike"),
                password = Base64.getUrlEncoder().withoutPadding().encodeToString(passwordBytes),
            )

        try {
            snapshot.file.outputStream().use { output ->
                writeEncryptedBackup(output, snapshot.password)
            }
            val saved =
                restorePreferences
                    .edit()
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
        swap.previous?.file?.takeIf { it != swap.current.file }?.delete()
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
        val cleared =
            restorePreferences
                .edit()
                .remove(RESTORE_UNDO_FILE_KEY)
                .remove(RESTORE_UNDO_PASSWORD_KEY)
                .commit()
        if (cleared) snapshot.file.delete()
    }

    private fun currentUndoSnapshot(): UndoSnapshot? {
        val fileName =
            restorePreferences.getString(RESTORE_UNDO_FILE_KEY, null)?.takeIf {
                it.startsWith(RESTORE_UNDO_PREFIX) && File(it).name == it
            } ?: return null
        if (dao.settingValue(RESTORE_UNDO_TOKEN_SETTING) != fileName) return null
        val password =
            restorePreferences.getString(RESTORE_UNDO_PASSWORD_KEY, null)?.takeIf { it.length >= 8 }
                ?: return null
        val file = File(appContext.filesDir, fileName).takeIf(File::isFile) ?: return null
        return UndoSnapshot(file, password)
    }

    private fun discardInvalidUndoSnapshot() {
        // The database marker is authoritative and changes in the same transaction as journals.
        // Cleanup is best effort: a crash or preference-write failure cannot re-enable undo.
        runCatching {
            val name = restorePreferences.getString(RESTORE_UNDO_FILE_KEY, null) ?: return
            if (dao.settingValue(RESTORE_UNDO_TOKEN_SETTING) == name) return
            if (name.startsWith(RESTORE_UNDO_PREFIX) && File(name).name == name) {
                File(appContext.filesDir, name).delete()
            }
            restorePreferences
                .edit()
                .remove(RESTORE_UNDO_FILE_KEY)
                .remove(RESTORE_UNDO_PASSWORD_KEY)
                .commit()
        }
    }

    private fun planRestoreMerge(
        stagingDirectory: File,
        localEntries: List<JournalEntry>,
        restoredEntries: List<JournalEntry>,
    ): com.xike.app.data.JournalMergePlan {
        return try {
            val localDigests = mutableMapOf<String, String>()
            val stagedDigests = mutableMapOf<String, String>()
            fun contentKey(entry: JournalEntry, staged: Boolean): String {
                fun digest(kind: String, name: String): String {
                    val cache = if (staged) stagedDigests else localDigests
                    return cache.getOrPut("$kind:$name") {
                        val input =
                            if (staged) {
                                if (kind == "video") videos.openStaged(stagingDirectory, name)
                                else attachments.openEncrypted(File(stagingDirectory, name))
                            } else
                                when (kind) {
                                    "image" -> attachments.openImage(name)
                                    "audio" -> attachments.openAudio(name)
                                    else -> videos.open(name)
                                }
                        checkNotNull(input) { localizedText("附件无法读取，恢复已停止，原数据未被修改。") }
                            .use(::attachmentDigest)
                    }
                }
                return restoreContentKey(
                    entry,
                    { digest("image", it) },
                    { digest("audio", it) },
                    { digest("video", it) },
                )
            }
            com.xike.app.data.planJournalMerge(
                localEntries,
                restoredEntries,
                { contentKey(it, false) },
                { contentKey(it, true) },
            )
        } catch (error: Throwable) {
            throw JournalDataException(
                tr(
                    "无法核对日记及附件，恢复已停止，原数据未被修改。",
                    "Unable to compare entries and attachments. Restore stopped without changing existing data.",
                ),
                error,
            )
        }
    }

    private fun installRestoredData(
        stagingDirectory: File,
        entriesToInstall: List<JournalEntry>,
        retainedAudioFileNames: Set<String>,
        retainedVideo: JournalVideo? = null,
        localEntries: List<JournalEntry> = emptyList(),
        undoToken: String = "",
        expectedUndoToken: String? = null,
        catalog: List<com.xike.app.data.JournalLabel>? = null,
    ): List<JournalEntry> {
        val installedFiles = mutableListOf<File>()
        return try {
            val stagedFiles = stagingDirectory.listFiles().orEmpty()
            val imageNames = entriesToInstall.flatMap { it.imageFileNames }.toSet()
            val audioNames = entriesToInstall.mapNotNull { it.audio?.fileName }.toSet()
            val renamedImages =
                stagedFiles
                    .filter { it.name in imageNames }
                    .associate { stagedFile ->
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
            val renamedAudios =
                stagedFiles
                    .filter { it.name in audioNames }
                    .associate { stagedFile ->
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
            val renamedVideos =
                entriesToInstall
                    .mapNotNull { it.video }
                    .distinct()
                    .associateWith { video ->
                        videos.install(stagingDirectory, video).also {
                            installedFiles.addAll(videos.filesOnDisk(it))
                        }
                    }
            val installedEntries =
                (localEntries +
                        entriesToInstall.map { entry ->
                            entry.copy(
                                video = entry.video?.let(renamedVideos::getValue),
                                imageFileNames =
                                    entry.imageFileNames.mapNotNull(renamedImages::get),
                                audio =
                                    entry.audio?.let { audio ->
                                        renamedAudios[audio.fileName]?.let {
                                            audio.copy(fileName = it)
                                        }
                                    },
                            )
                        })
                    .sortedByDescending { it.createdAt }

            val preservedDraft = videoDraftStore.load()
            val protectedVideos =
                retainedVideo.files() + preservedDraft.video.files() + videos.editReferences()
            val protectedAudios =
                retainedAudioFileNames +
                    listOfNotNull(preservedDraft.audio?.fileName) +
                    editAudioPreferences.all.keys
            writeEntries(installedEntries, undoToken, expectedUndoToken, catalog)
            // The database has committed. A cleanup failure must not remove installed media
            // or roll back the safety snapshot while records already reference the new files.
            runCatching {
                cleanupStaleVideoEdits()
                videos.prune(
                    installedEntries.flatMap { it.video.files() }.toSet() + protectedVideos
                )
                val referencedFiles = installedEntries.flatMap { it.imageFileNames }.toSet()
                attachments.imagesDirectory
                    .listFiles()
                    ?.filter { it.name !in referencedFiles }
                    ?.forEach(File::delete)
                val referencedAudios =
                    installedEntries.mapNotNull { it.audio?.fileName }.toSet() + protectedAudios
                attachments.audiosDirectory
                    .listFiles()
                    ?.filter { it.name !in referencedAudios }
                    ?.forEach(File::delete)
            }
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
        videoDraftStore.load().audio?.fileName?.let(::add)
    }

    private fun referencedVideoFiles(): Set<String> =
        dao.videoReferences().flatMap { it.video().files() }.toSet() +
            pendingDeletedEntry?.video.files() +
            videoDraftStore.load().video.files() +
            videos.editReferences()

    private fun cleanupStaleVideoEdits() {
        val references =
            dao.videoReferences().associate { it.id to it.video()?.fileName }.toMutableMap()
        pendingDeletedEntry?.let { references[it.id] = it.video?.fileName }
        videos.discardStaleEdits(references).forEach(::deleteUnreferencedVideo)
    }

    @Synchronized
    fun importVideo(uri: Uri, onProgress: (Float) -> Unit = {}): JournalVideo =
        videos.import(uri, onProgress)

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
        deletedEntry.audio
            ?.fileName
            ?.takeIf { it !in referencedAudioFileNames() }
            ?.let(attachments::deleteAudio)
        deletedEntry.video?.let(::deleteUnreferencedVideo)
    }

    private fun readEntries(): List<JournalEntry> =
        try {
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
        val serialized =
            runCatching { legacyPreferences.getString(ENTRIES_KEY, null) }
                .getOrElse { error ->
                    throw JournalDataException(localizedText("旧版日记数据暂时无法读取，原数据未被覆盖。"), error)
                } ?: return emptyList()
        return try {
            val values = JSONArray(serialized)
            List(values.length()) { index -> JournalEntry.fromJson(values.getJSONObject(index)) }
        } catch (error: Throwable) {
            throw JournalDataException(localizedText("旧版日记数据暂时无法读取，原数据未被覆盖。"), error)
        }
    }

    private fun writeEntries(
        entries: List<JournalEntry>,
        undoToken: String = "",
        expectedUndoToken: String? = null,
        catalog: List<com.xike.app.data.JournalLabel>? = null,
    ) {
        runCatching {
            dao.replaceJournals(
                entries.map(JournalEntry::toBundle),
                undoToken,
                expectedUndoToken,
                catalog,
            )
        }
            .getOrElse { error ->
                throw JournalDataException(localizedText("恢复内容写入失败，原数据未被覆盖。"), error)
            }
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
