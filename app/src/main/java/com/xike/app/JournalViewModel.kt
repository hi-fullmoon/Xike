package com.xike.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xike.app.data.BackupSummary
import com.xike.app.data.JournalAudio
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.data.model.JournalDraft
import com.xike.app.data.model.JournalVideo
import com.xike.app.data.model.MAX_DRAFT_NOTE_LENGTH
import com.xike.app.data.model.OutdoorSnapshot
import com.xike.app.data.model.VideoEditDraft
import com.xike.app.data.model.retainOutdoorForEditedTime
import com.xike.app.media.JournalVideoStore
import com.xike.app.media.pruneVoiceTemporaryFiles
import com.xike.app.outdoor.OutdoorContextRepository
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal class JournalOperationFeedback(val message: String, val isError: Boolean)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val store = JournalStore(application)
    internal val editOperations by lazy {
        JournalEditOperations(application, viewModelScope, store) { draft.imageUriStrings }
    }
    private val videoStore = JournalVideoStore(application)
    val isDraftVideoSaving: Boolean
        get() = draftMediaOperations.isDraftVideoSaving

    val draftVideoProgress: Float?
        get() = draftMediaOperations.draftVideoProgress

    private val draftStore = JournalDraftStore(application)
    private val mediaMutationMutex = Mutex()
    private val draftMediaOperations =
        JournalDraftMediaOperations(
            application,
            viewModelScope,
            store,
            currentDraft = { draft },
            currentDraftGeneration = { draftGeneration },
            savingDraft = { isDraftSaving },
            saveDraft = ::persistDraft,
            imageInUse = { editOperations.imageInUse(it) },
            onDataError = { dataError = it },
            reportOperation = ::reportOperation,
            mediaMutationMutex = mediaMutationMutex,
        )
    private val outdoorRepository = OutdoorContextRepository(application)
    private val appearancePreferences = AppearancePreferences(application)
    private var draftGeneration = 0L
    private var outdoorRequestGeneration = 0L
    private var outdoorRequestJob: Job? = null
    val isDraftImageResultPending: Boolean
        get() = draftMediaOperations.isDraftImageResultPending

    internal fun setDraftImageResultPending(key: String, pending: Boolean) =
        draftMediaOperations.setDraftImageResultPending(key, pending)

    var isDraftOutdoorLoading by mutableStateOf(false)
        private set

    private var noteSaveJob: Job? = null
    private val noteSaveScopeJob = SupervisorJob()
    private val noteSaveScope = CoroutineScope(noteSaveScopeJob + Dispatchers.IO)
    private var observationFailureReported = false
    private val readRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val readRepository
        get() = store.readRepository

    var libraryState by mutableStateOf(JournalLibraryState())
        private set

    var selectedTheme by mutableStateOf(appearancePreferences.current().theme)
        private set

    var selectedStyle by mutableStateOf(appearancePreferences.current().style)
        private set

    var draft by mutableStateOf(JournalDraft())
        private set

    var hasResumedDraft by mutableStateOf(false)
        private set

    private var draftSessionBackgrounded = false

    internal fun onAppBackgrounded() {
        draftSessionBackgrounded = true
    }

    internal fun onAppForegrounded() {
        if (draftSessionBackgrounded) {
            hasResumedDraft = !draft.isEmpty || pendingDraftAudio != null
            draftSessionBackgrounded = false
        }
        if (!isLoading) readRefreshRequests.tryEmit(Unit)
    }

    val pendingDraftAudio: PendingDraftAudio?
        get() = draftMediaOperations.pendingDraftAudio

    val isDraftAudioSaving: Boolean
        get() = draftMediaOperations.isDraftAudioSaving

    var isDraftSaving by mutableStateOf(false)
        private set

    private val backupOperations =
        JournalBackupOperations(
            application,
            viewModelScope,
            store,
            currentDraft = { draft },
            mediaMutationMutex = mediaMutationMutex,
            onRestored = { _, undoAvailable ->
                canUndoRestore = undoAvailable
                dataError = null
            },
            reportOperation = ::reportOperation,
        )
    val isBackupExporting: Boolean
        get() = backupOperations.isBackupExporting

    internal val backupRestoreState
        get() = backupOperations.restoreState

    internal var operationFeedback by mutableStateOf(emptyList<JournalOperationFeedback>())
        private set

    internal fun acknowledgeFeedback(feedback: JournalOperationFeedback) {
        operationFeedback = operationFeedback.filterNot { it === feedback }
    }

    private fun reportOperation(message: String, isError: Boolean = false) {
        operationFeedback = operationFeedback + JournalOperationFeedback(message, isError)
    }

    val draftAudioSaveError: String?
        get() = draftMediaOperations.draftAudioSaveError

    var dataError by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var systemActivityResultsReady by mutableStateOf(false)
        private set

    var canUndoRestore by mutableStateOf(false)
        private set

    init {
        draftStore.nextRevision()
        pruneVoiceTemporaryFiles(application)
        viewModelScope.launch {
            val draftResult = withContext(Dispatchers.IO) { runCatching(::loadAccessibleDraft) }
            val draftFlushResult =
                withContext(Dispatchers.IO) { runCatching { draftStore.flushPending() } }
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        store.initialize(loadEntries = false) to readRepository.libraryState(0)
                    }
                }
            val pendingResult =
                withContext(Dispatchers.IO) {
                    runCatching(draftMediaOperations::recoverPendingAudio)
                }
            result
                .onSuccess { (snapshot, initialState) ->
                    val appearance =
                        appearancePreferences.migrateFromDatabase(
                            themeName = snapshot.themeName,
                            styleName = snapshot.styleName,
                        )
                    libraryState = initialState.copy(revision = libraryState.revision + 1)
                    selectedTheme = appearance.theme
                    selectedStyle = appearance.style
                    canUndoRestore = runCatching(store::canUndoLastRestore).getOrDefault(false)
                    draftResult.onSuccess {
                        draft = it
                        hasResumedDraft = !it.isEmpty
                    }
                    dataError =
                        draftResult.exceptionOrNull()?.message
                            ?: draftFlushResult.exceptionOrNull()?.message
                    pendingResult
                        .onSuccess { staged ->
                            draftMediaOperations.restorePendingAudio(staged)
                            if (staged != null) hasResumedDraft = true
                        }
                        .onFailure { error ->
                            dataError = error.message ?: localizedText("录音暂存文件暂时无法读取。")
                        }
                    val audioResult =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                draft.audio?.let { store.requireReadableAudio(it.fileName) }
                            }
                        }
                    audioResult.onFailure {
                        dataError =
                            it.message
                                ?: tr(
                                    "草稿录音暂时无法读取，原文件已保留。",
                                    "Draft audio cannot be read. The original file is preserved.",
                                )
                    }
                    val imageResult =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                draft.imageUriStrings.forEach { uriString ->
                                    getApplication<Application>()
                                        .contentResolver
                                        .openAssetFileDescriptor(Uri.parse(uriString), "r")
                                        ?.use {} ?: error("草稿照片暂时无法读取，原引用已保留。")
                                }
                            }
                        }
                    imageResult.onFailure { dataError = "草稿照片暂时无法读取，原引用已保留。" }
                    val videoResult =
                        withContext(Dispatchers.IO) {
                            runCatching { store.requireReadableVideo(draft.video) }
                        }
                    videoResult.onFailure {
                        dataError =
                            tr(
                                "草稿视频暂时无法读取，原引用已保留。",
                                "Draft video cannot be read. Its reference is preserved.",
                            )
                    }
                    if (
                        draftResult.isSuccess &&
                            audioResult.isSuccess &&
                            imageResult.isSuccess &&
                            videoResult.isSuccess
                    ) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                store.removeOrphanedMedia(draft.audio?.fileName, draft.video)
                            }
                        }
                    }
                    viewModelScope.launch {
                        merge(readRepository.changes().map { Unit }, readRefreshRequests)
                            .map { readRepository.libraryState(0) }
                            .retryWhen { error, attempt ->
                                if (!observationFailureReported) {
                                    observationFailureReported = true
                                    dataError =
                                        error.message ?: localizedText("日记数据库暂时无法读取，原数据未被覆盖。")
                                }
                                delay((1_000L * (attempt.coerceAtMost(4L) + 1L)))
                                true
                            }
                            .collect { latestState ->
                                observationFailureReported = false
                                // Foreground refreshes must also retry failed page and summary
                                // reads.
                                libraryState =
                                    latestState.copy(revision = libraryState.revision + 1)
                            }
                    }
                }
                .onFailure { error ->
                    dataError = error.message ?: localizedText("日记数据库暂时无法读取，原数据未被覆盖。")
                }
            systemActivityResultsReady = draftResult.isSuccess && result.isSuccess
            isLoading = false
            if (pendingDraftAudio?.stagedFileName != null) retryPendingDraftAudio()
            draft.pendingVideoUri?.let { queueDraftVideo(Uri.parse(it)) }
        }
    }

    fun dismissDataError() {
        dataError = null
    }

    fun reportReadError(message: String) {
        dataError = message
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
        draft =
            draft
                .copy(
                    note = note.take(MAX_DRAFT_NOTE_LENGTH),
                    updatedAt = System.currentTimeMillis(),
                )
                .normalized()
        if (draft.isEmpty) hasResumedDraft = false
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
                    viewModelScope.launch {
                        dataError = error.message ?: localizedText("草稿保存失败，请重试。")
                    }
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
        if (isDraftSaving) return
        val saved =
            persistDraft(
                draft.copy(
                    recordedAt = recordedAt,
                    outdoor =
                        draft.outdoor?.let { snapshot ->
                            retainOutdoorForEditedTime(
                                snapshot,
                                snapshot.capturedAt,
                                recordedAt ?: System.currentTimeMillis(),
                            )
                        },
                )
            )
        if (saved) cancelOutdoorRequest()
    }

    fun clearDraftOutdoor() {
        if (isDraftSaving) return
        if (persistDraft(draft.copy(outdoor = null))) cancelOutdoorRequest()
    }

    private fun cancelOutdoorRequest() {
        outdoorRequestGeneration++
        val request = outdoorRequestJob
        outdoorRequestJob = null
        isDraftOutdoorLoading = false
        request?.cancel()
    }

    suspend fun attachCurrentOutdoor(): Result<Unit> =
        attachOutdoor(
            saveError = localizedText("地点与天气已取得，但草稿保存失败。"),
            request = outdoorRepository::current,
        )

    suspend fun attachOutdoorForCity(city: String): Result<Unit> =
        attachOutdoor(
            saveError = localizedText("城市天气已取得，但草稿保存失败。"),
            request = { outdoorRepository.city(city) },
        )

    private suspend fun attachOutdoor(
        saveError: String,
        request: suspend () -> OutdoorSnapshot,
    ): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            if (isDraftSaving || isDraftOutdoorLoading) {
                return@withContext Result.failure(
                    IllegalStateException(
                        tr("请等待当前操作完成。", "Please wait for the current operation to finish.")
                    )
                )
            }
            if (draft.recordedAt != null) {
                return@withContext Result.failure(
                    IllegalStateException(localizedText("补记过去时不会附加今天的天气。"))
                )
            }
            val requestDraftGeneration = draftGeneration
            val requestGeneration = ++outdoorRequestGeneration
            coroutineScope {
                val operation = async(start = CoroutineStart.LAZY) { runOutdoorRequest(request) }
                outdoorRequestJob = operation
                isDraftOutdoorLoading = true
                try {
                    val result = operation.await()
                    if (
                        requestDraftGeneration != draftGeneration ||
                            requestGeneration != outdoorRequestGeneration
                    ) {
                        return@coroutineScope Result.success(Unit)
                    }
                    result.mapCatching { snapshot ->
                        check(draft.recordedAt == null) { localizedText("补记过去时不会附加今天的天气。") }
                        check(persistDraft(draft.copy(outdoor = snapshot))) { saveError }
                    }
                } finally {
                    if (outdoorRequestJob === operation) {
                        outdoorRequestJob = null
                        isDraftOutdoorLoading = false
                    }
                }
            }
        }

    fun addDraftImages(uris: List<Uri>): Boolean = draftMediaOperations.addDraftImages(uris)

    fun removeDraftImage(uriString: String) = draftMediaOperations.removeDraftImage(uriString)

    fun queueDraftAudio(source: File, durationMillis: Long) =
        draftMediaOperations.queueDraftAudio(source, durationMillis)

    fun retryPendingDraftAudio() = draftMediaOperations.retryPendingDraftAudio()

    fun discardPendingDraftAudio() = draftMediaOperations.discardPendingDraftAudio()

    fun removeDraftAudio() = draftMediaOperations.removeDraftAudio()

    fun discardDraft() {
        if (isDraftSaving || pendingDraftAudio != null || isDraftVideoSaving) return
        val discardedDraft = draft
        if (persistDraft(JournalDraft())) {
            draftGeneration++
            cancelOutdoorRequest()
            draftMediaOperations.releaseDiscardedMedia(discardedDraft)
        }
    }

    suspend fun saveDraft(): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            val snapshot = draft
            val mood =
                snapshot.mood
                    ?: return@withContext Result.failure(
                        IllegalStateException(localizedText("先选择一种心情"))
                    )
            save(
                JournalEntry(
                    createdAt = snapshot.recordedAt ?: System.currentTimeMillis(),
                    mood = mood,
                    tags = snapshot.tags.toList(),
                    note = snapshot.note.trim(),
                    audio = snapshot.audio,
                    video = snapshot.video,
                    outdoor = snapshot.outdoor,
                ),
                snapshot.imageUriStrings.map(Uri::parse),
            )
        }

    suspend fun save(entry: JournalEntry, imageUris: List<Uri>): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            if (isDraftSaving)
                return@withContext Result.failure(
                    IllegalStateException(tr("正在保存，请稍候。", "Saving. Please wait."))
                )
            if (isDraftImageResultPending)
                return@withContext Result.failure(
                    IllegalStateException(
                        tr("请先等待照片添加完成。", "Please wait for the photos to be added.")
                    )
                )
            if (isDraftOutdoorLoading)
                return@withContext Result.failure(
                    IllegalStateException(
                        tr("请先等待地点与天气获取完成。", "Please wait for location and weather to finish.")
                    )
                )
            if (isDraftVideoSaving || draft.pendingVideoUri != null)
                return@withContext Result.failure(
                    IllegalStateException(
                        tr("请先完成视频导入。", "Please finish importing the video first.")
                    )
                )
            isDraftSaving = true
            val savedDraft = draft
            // The operation belongs to the ViewModel even if the requesting page leaves
            // composition.
            viewModelScope
                .async {
                    try {
                        val result =
                            withContext(Dispatchers.IO) {
                                runCatching { store.add(entry, imageUris, refreshEntries = false) }
                            }
                        result
                            .onSuccess {
                                clearDraftAfterSave(savedDraft)
                                reportOperation(
                                    if (savedDraft.recordedAt == null) localizedText("这一刻，已经好好收下了")
                                    else localizedText("那一刻，已经好好收下了")
                                )
                            }
                            .onFailure {
                                reportOperation(
                                    it.message ?: localizedText("保存失败，请重试"),
                                    isError = true,
                                )
                            }
                        result.map {}
                    } finally {
                        isDraftSaving = false
                    }
                }
                .await()
        }

    suspend fun update(
        entry: JournalEntry,
        retainedImageFileNames: List<String>,
        newImageUris: List<Uri>,
    ): Result<JournalEntry> =
        viewModelScope
            .async {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            store.update(
                                entry,
                                retainedImageFileNames,
                                newImageUris,
                                refreshEntries = false,
                            )
                        }
                    }
                result.onSuccess {
                    dataError = null
                }
                result.mapCatching { updatedEntries ->
                    updatedEntries.first { it.id == entry.id }
                }
            }
            .await()

    suspend fun delete(entry: JournalEntry): Result<Unit> =
        viewModelScope
            .async {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching { store.delete(entry.id, refreshEntries = false) }
                    }
                result.onSuccess {
                    dataError = null
                }
                result.map {}
            }
            .await()

    suspend fun undoDelete(entryId: String): Result<Unit> =
        viewModelScope
            .async {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching { store.undoDelete(entryId, refreshEntries = false) }
                    }
                result.onSuccess {
                    dataError = null
                }
                result.map {}
            }
            .await()

    suspend fun finalizeDelete(entryId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { store.finalizeDelete(entryId) }
        }

    suspend fun search(
        query: JournalSearchQuery,
        offset: Int,
        limit: Int,
    ): Result<JournalSearchPage> =
        viewModelScope
            .async(Dispatchers.IO) {
                runCatching { store.search(query, offset, limit) }
            }
            .await()

    fun startBackupExport(uri: Uri, password: String?) =
        backupOperations.startBackupExport(uri, password)

    suspend fun backupRequiresPassword(uri: Uri): Result<Boolean> =
        backupOperations.backupRequiresPassword(uri)

    suspend fun inspectBackup(uri: Uri, password: String?): Result<BackupSummary> =
        backupOperations.inspectBackup(uri, password)

    suspend fun restoreBackup(uri: Uri, password: String?): Result<Int> =
        backupOperations.restoreBackup(uri, password)

    suspend fun undoRestore(): Result<Int> = backupOperations.undoRestore()

    fun openImage(fileName: String): InputStream? = store.openImage(fileName)

    fun openAudio(fileName: String): InputStream? = store.openAudio(fileName)

    fun videoDataSource(video: JournalVideo) = videoStore.dataSource(video)

    internal fun videoEditDraft(entry: JournalEntry): VideoEditDraft? = runCatching {
        videoStore.editDraft(entry)
    }
        .onFailure {
            dataError =
                tr(
                    "视频编辑草稿暂时无法读取，原文件已保留。",
                    "Unable to read the video edit draft. The original files are preserved.",
                )
        }
        .getOrNull()

    internal suspend fun saveVideoEditDraft(
        entry: JournalEntry,
        video: JournalVideo?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching { videoStore.saveEdit(entry, video) } }

    internal suspend fun clearVideoEditDraft(entryId: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching { videoStore.clearEdit(entryId) } }

    fun openVideoCover(name: String): InputStream? = runCatching {
        videoStore.open(name)
    }
        .getOrNull()

    suspend fun importEditAudio(source: File, durationMillis: Long): Result<JournalAudio> =
        withContext(Dispatchers.IO) {
            runCatching { store.importEditAudio(source, durationMillis) }
        }

    fun releaseEditAudio(audio: JournalAudio) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { store.releaseEditAudio(audio) } }
                .onFailure {
                    dataError =
                        tr(
                            "编辑录音暂时无法清理，原文件已保留。",
                            "Unable to clean up the edited audio. The original file is preserved.",
                        )
                }
        }
    }

    suspend fun importVideo(uri: Uri, progress: (Float) -> Unit): Result<JournalVideo> =
        draftMediaOperations.importVideo(uri, progress)

    fun releaseVideo(video: JournalVideo) = draftMediaOperations.releaseVideo(video)

    fun queueDraftVideo(uri: Uri): Boolean = draftMediaOperations.queueDraftVideo(uri)

    fun removeDraftVideo() = draftMediaOperations.removeDraftVideo()

    fun cancelPendingDraftVideo() = draftMediaOperations.cancelPendingDraftVideo()

    private fun persistDraft(updated: JournalDraft, reportError: Boolean = true): Boolean {
        val normalized = updated.copy(updatedAt = System.currentTimeMillis()).normalized()
        return runCatching { draftStore.save(normalized) }
            .onSuccess {
                noteSaveJob?.cancel()
                noteSaveJob = null
                draft = normalized
                if (normalized.isEmpty) hasResumedDraft = false
            }
            .onFailure { error ->
                if (reportError) dataError = error.message ?: localizedText("草稿保存失败，请重试。")
            }
            .isSuccess
    }

    private suspend fun <T> runOutdoorRequest(request: suspend () -> T): Result<T> =
        try {
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
            hasResumedDraft = false
            runCatching { draftStore.save(draft) }
                .onSuccess {
                    noteSaveJob?.cancel()
                    noteSaveJob = null
                }
                .onFailure { error ->
                    dataError = error.message ?: localizedText("记录已保存，但草稿清理失败。")
                }
        }
        draftMediaOperations.releaseSavedImages(savedDraft)
    }

    private fun loadAccessibleDraft(): JournalDraft {
        val loaded = draftStore.load()
        val application = getApplication<Application>()
        pruneCameraCaptures(application)
        return loaded
    }
}
