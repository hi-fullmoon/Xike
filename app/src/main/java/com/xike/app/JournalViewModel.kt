package com.xike.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.InputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingDraftAudio(
    val durationMillis: Long,
    val draftGeneration: Long,
    val stagedFileName: String? = null,
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val store = JournalStore(application)
    private val draftStore = JournalDraftStore(application)
    private val pendingAudioStore = PendingDraftAudioStore(application)
    private val outdoorRepository = OutdoorContextRepository(application)
    private val appearancePreferences = AppearancePreferences(application)
    private var draftGeneration = 0L
    private var noteSaveJob: Job? = null
    private val noteSaveScopeJob = SupervisorJob()
    private val noteSaveScope = CoroutineScope(noteSaveScopeJob + Dispatchers.IO)
    private var observationFailureReported = false

    var entries by mutableStateOf(emptyList<JournalEntry>())
        private set

    var selectedTheme by mutableStateOf(appearancePreferences.current().theme)
        private set

    var selectedStyle by mutableStateOf(appearancePreferences.current().style)
        private set

    var draft by mutableStateOf(JournalDraft())
        private set

    var pendingDraftAudio by mutableStateOf<PendingDraftAudio?>(null)
        private set

    var isDraftAudioSaving by mutableStateOf(false)
        private set

    var draftAudioSaveError by mutableStateOf<String?>(null)
        private set

    var dataError by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var canUndoRestore by mutableStateOf(false)
        private set

    init {
        draftStore.nextRevision()
        pruneVoiceTemporaryFiles(application)
        viewModelScope.launch {
            val draftResult = withContext(Dispatchers.IO) { runCatching(::loadAccessibleDraft) }
            val draftFlushResult = withContext(Dispatchers.IO) { runCatching { draftStore.flushPending() } }
            val result = withContext(Dispatchers.IO) { runCatching(store::initialize) }
            val pendingResult = withContext(Dispatchers.IO) { runCatching(pendingAudioStore::recover) }
            result.onSuccess { snapshot ->
                val appearance = appearancePreferences.migrateFromDatabase(
                    themeName = snapshot.themeName,
                    styleName = snapshot.styleName,
                )
                entries = snapshot.entries
                selectedTheme = appearance.theme
                selectedStyle = appearance.style
                canUndoRestore = runCatching(store::canUndoLastRestore).getOrDefault(false)
                draftResult.onSuccess { draft = it }
                dataError = draftResult.exceptionOrNull()?.message ?: draftFlushResult.exceptionOrNull()?.message
                pendingResult.onSuccess { staged ->
                    pendingDraftAudio = staged?.let {
                        PendingDraftAudio(it.durationMillis, draftGeneration, it.fileName)
                    }
                }.onFailure { error ->
                    dataError = error.message ?: localizedText("录音暂存文件暂时无法读取。")
                }
                val audioResult = withContext(Dispatchers.IO) {
                    runCatching { draft.audio?.let { store.requireReadableAudio(it.fileName) } }
                }
                audioResult.onFailure {
                    dataError = it.message ?: tr("草稿录音暂时无法读取，原文件已保留。", "Draft audio cannot be read. The original file is preserved.")
                }
                val imageResult = withContext(Dispatchers.IO) {
                    runCatching {
                        draft.imageUriStrings.forEach { uriString ->
                            getApplication<Application>().contentResolver
                                .openAssetFileDescriptor(Uri.parse(uriString), "r")?.use { }
                                ?: error("草稿照片暂时无法读取，原引用已保留。")
                        }
                    }
                }
                imageResult.onFailure { dataError = "草稿照片暂时无法读取，原引用已保留。" }
                if (draftResult.isSuccess && audioResult.isSuccess && imageResult.isSuccess) {
                    withContext(Dispatchers.IO) {
                        runCatching { store.removeOrphanedMedia(draft.audio?.fileName) }
                    }
                }
                viewModelScope.launch {
                    store.observeEntries()
                        .retryWhen { error, attempt ->
                            if (!observationFailureReported) {
                                observationFailureReported = true
                                dataError = error.message ?: localizedText("日记数据库暂时无法读取，原数据未被覆盖。")
                            }
                            delay((1_000L * (attempt.coerceAtMost(4L) + 1L)))
                            true
                        }
                        .collect { latestEntries ->
                            observationFailureReported = false
                            entries = latestEntries
                        }
                }
            }.onFailure { error ->
                dataError = error.message ?: localizedText("日记数据库暂时无法读取，原数据未被覆盖。")
            }
            isLoading = false
            if (pendingDraftAudio?.stagedFileName != null) retryPendingDraftAudio()
        }
    }

    fun dismissDataError() {
        dataError = null
    }

    fun selectTheme(theme: AppTheme) {
        selectedTheme = theme
        appearancePreferences.saveTheme(theme)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { store.saveThemeName(theme.name) } }
                .onFailure { error ->
                    dataError = error.message ?: localizedText("外观设置保存失败，请重试。")
                }
        }
    }

    fun selectStyle(style: AppStyle) {
        selectedStyle = style
        appearancePreferences.saveStyle(style)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { store.saveStyleName(style.name) } }
                .onFailure { error ->
                    dataError = error.message ?: localizedText("界面风格保存失败，请重试。")
                }
        }
    }

    fun selectDraftMood(mood: Mood?) {
        persistDraft(draft.copy(mood = mood))
    }

    fun updateDraftNote(note: String) {
        draft = draft.copy(note = note.take(MAX_DRAFT_NOTE_LENGTH), updatedAt = System.currentTimeMillis()).normalized()
        scheduleNoteSave(250L)
    }

    fun flushDraft() {
        if (noteSaveJob != null && draftStore.hasPendingWrite) scheduleNoteSave(0L)
    }

    private fun scheduleNoteSave(delayMillis: Long) {
        noteSaveJob?.cancel()
        val snapshot = draft
        val revision = draftStore.queue(snapshot)
        noteSaveJob = noteSaveScope.launch {
            delay(delayMillis)
            runCatching { draftStore.saveIfCurrent(snapshot, revision) }
                .onFailure { error ->
                    viewModelScope.launch { dataError = error.message ?: localizedText("草稿保存失败，请重试。") }
                }
        }
    }

    override fun onCleared() {
        flushDraft()
        noteSaveScopeJob.complete()
        super.onCleared()
    }

    fun toggleDraftTag(tag: String) {
        val updatedTags = toggleTopic(draft.tags, tag).toSet()
        persistDraft(draft.copy(tags = updatedTags))
    }

    fun updateDraftRecordedAt(recordedAt: Long?) {
        persistDraft(
            draft.copy(
                recordedAt = recordedAt,
                outdoor = if (recordedAt == null) draft.outdoor else null,
            ),
        )
    }

    fun clearDraftOutdoor() {
        persistDraft(draft.copy(outdoor = null))
    }

    suspend fun attachCurrentOutdoor(): Result<Unit> {
        val requestGeneration = draftGeneration
        return runOutdoorRequest(outdoorRepository::current).mapCatching { snapshot ->
            if (requestGeneration != draftGeneration) return@mapCatching
            check(draft.recordedAt == null) { localizedText("补记过去时不会附加今天的天气。") }
            check(persistDraft(draft.copy(outdoor = snapshot))) {
                localizedText("地点与天气已取得，但草稿保存失败。")
            }
        }
    }

    suspend fun attachOutdoorForCity(city: String): Result<Unit> {
        val requestGeneration = draftGeneration
        return runOutdoorRequest { outdoorRepository.city(city) }.mapCatching { snapshot ->
            if (requestGeneration != draftGeneration) return@mapCatching
            check(draft.recordedAt == null) { localizedText("补记过去时不会附加今天的天气。") }
            check(persistDraft(draft.copy(outdoor = snapshot))) {
                localizedText("城市天气已取得，但草稿保存失败。")
            }
        }
    }

    fun addDraftImages(uris: List<Uri>) {
        val availableSlots = MAX_IMAGES_PER_ENTRY - draft.imageUriStrings.size
        val candidates = uris
            .filterNot { it.toString() in draft.imageUriStrings }
            .distinctBy(Uri::toString)
        val newUris = candidates.take(availableSlots)
        candidates.drop(newUris.size).forEach(::releaseDraftImageAccess)
        if (newUris.isEmpty()) return

        val application = getApplication<Application>()
        val contentResolver = application.contentResolver
        val grantedUris = mutableListOf<Uri>()
        newUris.forEach { uri ->
            if (isCameraCaptureUri(application, uri)) {
                val readable = runCatching {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
                }.getOrDefault(false)
                if (readable) grantedUris += uri else deleteCameraCapture(application, uri)
            } else {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.onSuccess {
                    grantedUris += uri
                }
            }
        }
        if (grantedUris.isEmpty()) {
            dataError = localizedText("所选照片无法获得长期读取权限，请重新选择。")
            return
        }

        val updated = draft.copy(
            imageUriStrings = draft.imageUriStrings + grantedUris.map(Uri::toString),
        )
        if (!persistDraft(updated)) {
            grantedUris.forEach(::releaseDraftImageAccess)
        } else if (grantedUris.size < newUris.size) {
            dataError = localizedText("部分照片无法长期读取，已保留可以恢复的照片。")
        }
    }

    fun removeDraftImage(uriString: String) {
        if (uriString !in draft.imageUriStrings) return
        if (persistDraft(draft.copy(imageUriStrings = draft.imageUriStrings - uriString))) {
            runCatching { Uri.parse(uriString) }.getOrNull()?.let(::releaseDraftImageAccess)
        }
    }

    fun queueDraftAudio(source: File, durationMillis: Long) {
        if (pendingDraftAudio != null) {
            source.delete()
            return
        }
        val pending = PendingDraftAudio(durationMillis, draftGeneration)
        pendingDraftAudio = pending
        isDraftAudioSaving = true
        draftAudioSaveError = null
        viewModelScope.launch {
            val staged = try {
                withContext(Dispatchers.IO) {
                    try {
                        pendingAudioStore.stage(source, durationMillis)
                    } finally {
                        source.delete()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (pending === pendingDraftAudio) {
                    pendingDraftAudio = null
                    dataError = error.message ?: localizedText("录音未能加密暂存，请重新录制。")
                }
                isDraftAudioSaving = false
                return@launch
            }
            if (pending !== pendingDraftAudio || pending.draftGeneration != draftGeneration) {
                try {
                    withContext(Dispatchers.IO) { runCatching { pendingAudioStore.delete(staged) } }
                } finally {
                    if (pending === pendingDraftAudio) pendingDraftAudio = null
                    isDraftAudioSaving = false
                }
                return@launch
            }
            pendingDraftAudio = pending.copy(stagedFileName = staged.fileName)
            isDraftAudioSaving = false
            retryPendingDraftAudio()
        }
    }

    fun retryPendingDraftAudio() {
        val pending = pendingDraftAudio ?: return
        val staged = pending.stagedFileName?.let { StagedDraftAudio(it, pending.durationMillis) } ?: return
        if (isDraftAudioSaving) return
        isDraftAudioSaving = true
        draftAudioSaveError = null
        viewModelScope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    runCatching {
                        pendingAudioStore.open(staged).use { input ->
                            store.importAudio(input, pending.durationMillis)
                        }
                    }
                }
                val result = imported.mapCatching { audio ->
                    check(pending === pendingDraftAudio && pending.draftGeneration == draftGeneration) {
                        localizedText("这段录音已经取消。")
                    }
                    val previousAudio = draft.audio
                    check(persistDraft(draft.copy(audio = audio), reportError = false)) {
                        localizedText("录音已经完成，但草稿保存失败。")
                    }
                    if (previousAudio != null && previousAudio.fileName != audio.fileName) {
                        viewModelScope.launch(Dispatchers.IO) {
                            store.deleteUnreferencedAudio(previousAudio.fileName)
                        }
                    }
                }
                imported.getOrNull()?.takeIf { result.isFailure }?.let { audio ->
                    withContext(Dispatchers.IO) {
                        runCatching { store.deleteUnreferencedAudio(audio.fileName) }
                    }
                }
                if (pending === pendingDraftAudio) {
                    result.onSuccess {
                        check(withContext(Dispatchers.IO) { pendingAudioStore.delete(staged) }) {
                            localizedText("录音已保存，但暂存文件清理失败，请重试。")
                        }
                        pendingDraftAudio = null
                    }.onFailure { error ->
                        draftAudioSaveError = error.message ?: localizedText("录音保存失败，请重试。")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (pending === pendingDraftAudio) {
                    draftAudioSaveError = error.message ?: localizedText("录音保存失败，请重试。")
                }
            } finally {
                isDraftAudioSaving = false
            }
        }
    }

    fun discardPendingDraftAudio() {
        if (isDraftAudioSaving) return
        val pending = pendingDraftAudio ?: return
        val staged = pending.stagedFileName?.let { StagedDraftAudio(it, pending.durationMillis) } ?: return
        if (!pendingAudioStore.delete(staged)) {
            draftAudioSaveError = localizedText("录音暂存文件暂时无法删除，请重试。")
            return
        }
        pendingDraftAudio = null
        draftAudioSaveError = null
    }

    fun removeDraftAudio() {
        val removed = draft.audio ?: return
        if (persistDraft(draft.copy(audio = null))) {
            viewModelScope.launch(Dispatchers.IO) { store.deleteUnreferencedAudio(removed.fileName) }
        }
    }

    fun discardDraft() {
        if (pendingDraftAudio != null) return
        val discardedDraft = draft
        if (persistDraft(JournalDraft())) {
            draftGeneration++
            discardedDraft.imageUriStrings
                .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                .forEach(::releaseDraftImageAccess)
            discardedDraft.audio?.fileName?.let { fileName ->
                viewModelScope.launch(Dispatchers.IO) { store.deleteUnreferencedAudio(fileName) }
            }
        }
    }

    suspend fun save(entry: JournalEntry, imageUris: List<Uri>): Result<Unit> = viewModelScope.async {
        val savedDraft = draft
        val result = withContext(Dispatchers.IO) {
            runCatching { store.add(entry, imageUris, refreshEntries = false) }
        }
        result.onSuccess {
            clearDraftAfterSave(savedDraft)
        }
        result.map { }
    }.await()

    suspend fun update(
        entry: JournalEntry,
        retainedImageFileNames: List<String>,
        newImageUris: List<Uri>,
    ): Result<JournalEntry> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching { store.update(entry, retainedImageFileNames, newImageUris, refreshEntries = false) }
        }
        result.onSuccess {
            dataError = null
        }
        result.mapCatching { updatedEntries ->
            updatedEntries.first { it.id == entry.id }
        }
    }.await()

    suspend fun delete(entry: JournalEntry): Result<Unit> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching { store.delete(entry.id, refreshEntries = false) }
        }
        result.onSuccess {
            dataError = null
        }
        result.map { }
    }.await()

    suspend fun undoDelete(entryId: String): Result<Unit> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching { store.undoDelete(entryId, refreshEntries = false) }
        }
        result.onSuccess {
            dataError = null
        }
        result.map { }
    }.await()

    suspend fun finalizeDelete(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { store.finalizeDelete(entryId) }
    }

    suspend fun search(
        query: JournalSearchQuery,
        offset: Int,
        limit: Int,
    ): Result<JournalSearchPage> = viewModelScope.async(Dispatchers.IO) {
        runCatching { store.search(query, offset, limit) }
    }.await()

    suspend fun exportBackup(uri: Uri, password: String?): Result<Unit> = viewModelScope.async(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                store.writeBackup(output, password)
            } ?: error(localizedText("无法写入备份文件"))
        }
    }.await()

    suspend fun backupRequiresPassword(uri: Uri): Result<Boolean> = viewModelScope.async(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use(store::backupRequiresPassword)
                ?: error(localizedText("无法读取备份文件"))
        }
    }.await()

    suspend fun inspectBackup(uri: Uri, password: String?): Result<BackupSummary> = viewModelScope.async(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                store.inspectBackup(input, password)
            } ?: error(localizedText("无法读取备份文件"))
        }
    }.await()

    suspend fun restoreBackup(uri: Uri, password: String?): Result<Int> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    store.restoreBackup(input, password, setOfNotNull(draft.audio?.fileName))
                } ?: error(localizedText("无法读取备份文件"))
            }
        }
        result.onSuccess {
            entries = it
            canUndoRestore = true
            dataError = null
        }
        result.map { it.size }
    }.await()

    suspend fun undoRestore(): Result<Int> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching { store.undoLastRestore(setOfNotNull(draft.audio?.fileName)) }
        }
        result.onSuccess {
            entries = it
            canUndoRestore = false
            dataError = null
        }
        result.map { it.size }
    }.await()

    fun openImage(fileName: String): InputStream? = store.openImage(fileName)

    fun openAudio(fileName: String): InputStream? = store.openAudio(fileName)

    private fun persistDraft(updated: JournalDraft, reportError: Boolean = true): Boolean {
        val normalized = updated.copy(updatedAt = System.currentTimeMillis()).normalized()
        return runCatching { draftStore.save(normalized) }
            .onSuccess {
                noteSaveJob?.cancel()
                noteSaveJob = null
                draft = normalized
            }
            .onFailure { error ->
                if (reportError) dataError = error.message ?: localizedText("草稿保存失败，请重试。")
            }
            .isSuccess
    }

    private suspend fun <T> runOutdoorRequest(request: suspend () -> T): Result<T> = try {
        Result.success(request())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun clearDraftAfterSave(savedDraft: JournalDraft) {
        draftGeneration++
        val currentDraft = draft
        if (currentDraft == savedDraft) {
            draft = JournalDraft()
            runCatching { draftStore.save(draft) }
                .onSuccess {
                    noteSaveJob?.cancel()
                    noteSaveJob = null
                }
                .onFailure { error ->
                    dataError = error.message ?: localizedText("记录已保存，但草稿清理失败。")
                }
        }
        savedDraft.imageUriStrings
            .filterNot { it in draft.imageUriStrings }
            .map(Uri::parse)
            .forEach(::releaseDraftImageAccess)
    }

    private fun releaseDraftImageAccess(uri: Uri) {
        val application = getApplication<Application>()
        if (isCameraCaptureUri(application, uri)) {
            return
        } else {
            runCatching {
                application.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private fun loadAccessibleDraft(): JournalDraft {
        val loaded = draftStore.load()
        val application = getApplication<Application>()
        pruneCameraCaptures(application)
        return loaded
    }
}
