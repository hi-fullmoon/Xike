package com.xike.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.BackupSummary
import com.xike.app.data.JournalAudio
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.data.model.JournalDraft
import com.xike.app.data.model.JournalVideo
import com.xike.app.data.model.LocationSource
import com.xike.app.data.model.MAX_DRAFT_NOTE_LENGTH
import com.xike.app.data.model.OutdoorSnapshot
import com.xike.app.data.model.VideoEditDraft
import com.xike.app.data.model.retainOutdoorForEditedTime
import com.xike.app.feature.moment.suggestedCityQuery
import com.xike.app.media.JournalVideoStore
import com.xike.app.media.pruneVoiceTemporaryFiles
import com.xike.app.outdoor.AmapSettingsOperations
import com.xike.app.outdoor.AmapSettingsStore
import com.xike.app.outdoor.OutdoorContextRepository
import com.xike.app.outdoor.OutdoorFetchResult
import com.xike.app.outdoor.OutdoorRetryCoordinates
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import java.io.File
import java.io.InputStream
import java.util.UUID
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class JournalOperationFeedback(val message: String, val isError: Boolean)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val store = com.xike.app.backup.JournalStores.get(application)
    internal val editOperations by lazy {
        JournalEditOperations(application, viewModelScope, store) { draft.imageUriStrings }
    }
    private val videoStore = JournalVideoStore(application)
    val isDraftVideoSaving: Boolean
        get() = draftMediaOperations.isDraftVideoSaving

    val draftVideoProgress: Float?
        get() = draftMediaOperations.draftVideoProgress

    private val draftStore = JournalDraftStore(application)
    private val mediaMutationMutex = store.draftMediaMutationMutex
    private val draftMediaOperations =
        JournalDraftMediaOperations(
            application,
            viewModelScope,
            store,
            currentDraft = { draft },
            currentDraftGeneration = { draftGeneration },
            savingDraft = { isDraftSaving },
            saveDraft = ::persistDraft,
            awaitDraftPersistence = ::awaitDraftPersistence,
            imageInUse = { editOperations.imageInUse(it) },
            onDataError = { dataError = it },
            reportOperation = ::reportOperation,
            mediaMutationMutex = mediaMutationMutex,
        )
    private val amapSettingsStore = AmapSettingsStore(application)
    internal val aiReviewOperations by lazy {
        com.xike.app.ai.AiReviewOperations(com.xike.app.ai.AiSettingsStore(application))
    }
    private val outdoorRepository = OutdoorContextRepository(application, amapSettingsStore)
    internal val amapSettingsOperations by lazy {
        AmapSettingsOperations(application, amapSettingsStore, ::cancelOutdoorRequest)
    }
    private val appearancePreferences = AppearancePreferences(application)
    private var draftGeneration = 0L
    private var outdoorRequestGeneration = 0L
    private var outdoorRequestJob: Job? = null
    private var outdoorWeatherRetrySession: OutdoorWeatherRetrySession? = null
    val isDraftImageResultPending: Boolean
        get() = draftMediaOperations.isDraftImageResultPending

    internal fun setDraftImageResultPending(key: String, pending: Boolean) =
        draftMediaOperations.setDraftImageResultPending(key, pending)

    var isDraftOutdoorLoading by mutableStateOf(false)
        private set

    var draftOutdoorWeatherError by mutableStateOf<String?>(null)
        private set

    private var noteSaveJob: Job? = null
    private val noteSaveScopeJob = SupervisorJob()
    private val noteSaveScope = CoroutineScope(noteSaveScopeJob + Dispatchers.IO)
    private var observationFailureReported = false
    private val readRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var draftReady = false
    private var libraryReady = false
    private var initializationJob: Job? = null
    private var observationJob: Job? = null

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
        cancelOutdoorRequest()
        draftSessionBackgrounded = true
    }

    internal fun onAppForegrounded() {
        if (!isLoading && (!draftReady || !libraryReady)) initialize()
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
                // Re-read after publishing: a journal write may have raced the restore result.
                readRefreshRequests.tryEmit(Unit)
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
        initialize()
    }

    private fun initialize() {
        if (initializationJob?.isActive == true) return
        isLoading = true
        initializationJob = viewModelScope.launch {
            val library =
                async(Dispatchers.IO) {
                    runCatching {
                        store.initialize(loadEntries = false) to readRepository.libraryState(0)
                    }
                }
            val pendingAudio =
                async(Dispatchers.IO) {
                    runCatching(draftMediaOperations::recoverPendingAudio)
                }
            val application = getApplication<Application>()
            val (draftResult, draftFlushResult) =
                withContext(Dispatchers.IO) {
                    val loaded = runCatching(draftStore::load)
                    loaded to runCatching(draftStore::flushPending)
                }
            // Draft recovery does not depend on the journal database opening successfully.
            draftResult.onSuccess {
                draft = it
                hasResumedDraft = !it.isEmpty
            }
            draftReady = draftResult.isSuccess && draftFlushResult.isSuccess
            val result = library.await()
            val pendingResult = pendingAudio.await()
            result
                .onSuccess { (snapshot, initialState) ->
                    libraryReady = true
                    val appearance =
                        appearancePreferences.migrateFromDatabase(
                            themeName = snapshot.themeName,
                            styleName = snapshot.styleName,
                        )
                    libraryState = initialState.copy(revision = libraryState.revision + 1)
                    selectedTheme = appearance.theme
                    selectedStyle = appearance.style
                    canUndoRestore =
                        withContext(Dispatchers.IO) {
                            runCatching(store::canUndoLastRestore).getOrDefault(false)
                        }
                    dataError =
                        draftResult.exceptionOrNull()?.message
                            ?: draftFlushResult.exceptionOrNull()?.message
                    if (draftReady) reconcileSavedDraft()
                    pendingResult
                        .onSuccess { staged ->
                            draftMediaOperations.restorePendingAudio(staged)
                            if (staged != null) hasResumedDraft = true
                        }
                        .onFailure { error ->
                            dataError = error.message ?: localizedText("录音暂存文件暂时无法读取。")
                        }
                    observationJob?.cancel()
                    observationJob = viewModelScope.launch {
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
                                canUndoRestore =
                                    withContext(Dispatchers.IO) {
                                        runCatching(store::canUndoLastRestore).getOrDefault(false)
                                    }
                                observationFailureReported = false
                                // Foreground refreshes must also retry failed page and summary
                                // reads.
                                libraryState =
                                    latestState.copy(revision = libraryState.revision + 1)
                            }
                    }
                }
                .onFailure { error ->
                    if (draft.submissionId != null) draftReady = false
                    dataError = error.message ?: localizedText("日记数据库暂时无法读取，原数据未被覆盖。")
                }
            systemActivityResultsReady = draftReady && result.isSuccess
            isLoading = false
            if (systemActivityResultsReady) {
                val restoredDraft = draft
                viewModelScope.launch {
                    checkRestoredDraftMedia(restoredDraft, pruneOrphans = pendingResult.isSuccess)
                }
                if (pendingDraftAudio != null) retryPendingDraftAudio()
                draft.pendingVideoUri?.let { queueDraftVideo(Uri.parse(it)) }
            }
            viewModelScope.launch(Dispatchers.IO) { pruneCameraCaptures(application) }
        }
    }

    private suspend fun checkRestoredDraftMedia(
        restoredDraft: JournalDraft,
        pruneOrphans: Boolean,
    ) {
        val (audio, images, video) =
            withContext(Dispatchers.IO) {
                Triple(
                    runCatching {
                        restoredDraft.audio?.let { store.requireReadableAudio(it.fileName) }
                    },
                    runCatching {
                        val resolver = getApplication<Application>().contentResolver
                        restoredDraft.imageUriStrings.forEach { uriString ->
                            checkNotNull(
                                    resolver.openAssetFileDescriptor(Uri.parse(uriString), "r")
                                )
                                .use {}
                        }
                    },
                    runCatching { store.requireReadableVideo(restoredDraft.video) },
                )
            }
        // The user may have replaced or removed an attachment while its startup check ran.
        if (dataError == null) {
            dataError =
                when {
                    video.isFailure && draft.video == restoredDraft.video ->
                        tr(
                            "草稿视频暂时无法读取，原引用已保留。",
                            "Draft video cannot be read. Its reference is preserved.",
                        )
                    images.isFailure && draft.imageUriStrings == restoredDraft.imageUriStrings ->
                        localizedText("草稿照片暂时无法读取，原引用已保留。")
                    audio.isFailure && draft.audio == restoredDraft.audio ->
                        audio.exceptionOrNull()?.message
                            ?: tr(
                                "草稿录音暂时无法读取，原文件已保留。",
                                "Draft audio cannot be read. The original file is preserved.",
                            )
                    else -> null
                }
        }
        if (pruneOrphans && audio.isSuccess && images.isSuccess && video.isSuccess) {
            mediaMutationMutex.withLock {
                val activeDraft = draft
                withContext(Dispatchers.IO) {
                    runCatching {
                        // A newly removed attachment may still be referenced by the draft on disk.
                        draftStore.flushPending()
                        store.removeOrphanedMediaAtStartup(
                            activeDraft.audio?.fileName,
                            activeDraft.video,
                        )
                    }
                }
            }
        }
    }

    fun dismissDataError() {
        dataError = null
        if (!draftReady || !libraryReady) initialize()
    }

    fun reportReadError(message: String) {
        dataError = message
    }

    internal suspend fun saveLabel(label: com.xike.app.data.JournalLabel): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching { store.saveLabel(label) } }

    internal suspend fun setFavorite(entryId: String, favorite: Boolean): Result<JournalEntry> =
        withContext(Dispatchers.IO) { runCatching { store.setFavorite(entryId, favorite) } }

    internal suspend fun moveLabel(id: String, direction: Int): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching { store.moveLabel(id, direction) } }

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
        persistDraft(
            draft.copy(mood = mood, moodScaleVersion = if (mood?.isOverallScale != false) 1 else 0)
        )
    }

    fun updateDraftFeelings(feelings: List<String>) {
        if (!isDraftSaving) persistDraft(draft.copy(feelings = feelings))
    }

    fun updateDraftNote(note: String) {
        if (!requireDraftReady()) return
        draft =
            draft
                .copy(
                    note = note.take(MAX_DRAFT_NOTE_LENGTH),
                    updatedAt = System.currentTimeMillis(),
                    submissionId = null,
                )
                .normalized()
        if (draft.isEmpty) hasResumedDraft = false
        scheduleNoteSave(250L)
    }

    fun flushDraft() {
        if (!isDraftSaving && noteSaveJob != null && draftStore.hasPendingWrite)
            scheduleNoteSave(0L)
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
        val label =
            libraryState.labels.firstOrNull { it.id == tag }
                ?: libraryState.labels.firstOrNull {
                    it.kind == com.xike.app.data.JournalLabelKind.TOPIC &&
                        it.name == canonicalTopic(tag)
                }
        val id = label?.id ?: com.xike.app.data.legacyTopicSelection(tag).id
        val next =
            if (draft.labels.any { it.id == id }) draft.labels.filterNot { it.id == id }
            else if (label != null && !label.isArchived) draft.labels + label.selection()
            else return
        persistDraft(draft.copy(labels = next))
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
        outdoorWeatherRetrySession = null
        draftOutdoorWeatherError = null
        isDraftOutdoorLoading = false
        request?.cancel()
    }

    suspend fun attachCurrentOutdoor(): Result<Unit> =
        attachOutdoor(
            saveError = localizedText("地点与天气已取得，但草稿保存失败。"),
            request = { onPlaceReady -> outdoorRepository.currentWithFallback(onPlaceReady) },
        )

    suspend fun attachOutdoorForCity(city: String): Result<Unit> {
        val currentOutdoor = draft.outdoor
        val preservePlace = currentOutdoor?.takeIf {
            !it.hasWeather &&
                suggestedCityQuery(it.placeName).let { suggested ->
                    suggested.isNotBlank() && suggested.equals(city.trim(), ignoreCase = true)
                }
        }
        return attachOutdoor(
            saveError = localizedText("城市天气已取得，但草稿保存失败。"),
            request = { onPlaceReady ->
                outdoorRepository.cityWithFallback(
                    query = city,
                    preservePlace = preservePlace,
                    onPlaceReady = onPlaceReady,
                )
            },
        )
    }

    suspend fun attachManualOutdoorPlace(placeName: String): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            if (!requireDraftReady()) {
                return@withContext Result.failure(IllegalStateException(dataError))
            }
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
            runCatching {
                val snapshot = outdoorRepository.manualPlace(placeName)
                outdoorWeatherRetrySession = null
                draftOutdoorWeatherError = null
                check(persistDraft(draft.copy(outdoor = snapshot)) && awaitDraftPersistence()) {
                    tr("地点已记录，但草稿保存失败。", "Location recorded, but saving the draft failed.")
                }
            }
        }

    suspend fun retryDraftOutdoorWeather(): Result<Unit> {
        val currentOutdoor = draft.outdoor
        val session = outdoorWeatherRetrySession
        if (
            currentOutdoor != null &&
                !currentOutdoor.hasWeather &&
                session != null &&
                session.draftGeneration == draftGeneration &&
                session.placeName == currentOutdoor.placeName
        ) {
            return attachOutdoor(
                saveError = localizedText("天气已取得，但草稿保存失败。"),
                request = { outdoorRepository.retryWeather(currentOutdoor, session.coordinates) },
            )
        }
        if (
            currentOutdoor != null &&
                !currentOutdoor.hasWeather &&
                currentOutdoor.locationSource == LocationSource.MANUAL
        ) {
            return attachOutdoorForCity(currentOutdoor.placeName)
        }
        return attachCurrentOutdoor()
    }

    private suspend fun attachOutdoor(
        saveError: String,
        request: suspend (onPlaceReady: suspend (OutdoorSnapshot) -> Unit) -> OutdoorFetchResult,
    ): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            if (!requireDraftReady()) {
                return@withContext Result.failure(IllegalStateException(dataError))
            }
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
            draftOutdoorWeatherError = null
            val onPlaceReady: suspend (OutdoorSnapshot) -> Unit = { partial ->
                withContext(Dispatchers.Main.immediate) {
                    if (
                        requestDraftGeneration == draftGeneration &&
                            requestGeneration == outdoorRequestGeneration &&
                            !isDraftSaving &&
                            draft.recordedAt == null
                    ) {
                        persistDraft(draft.copy(outdoor = partial))
                    }
                }
            }
            coroutineScope {
                val operation =
                    async(start = CoroutineStart.LAZY) {
                        runOutdoorRequest { request(onPlaceReady) }
                    }
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
                    result.mapCatching { fetchResult ->
                        check(draft.recordedAt == null) { localizedText("补记过去时不会附加今天的天气。") }
                        val snapshot = fetchResult.snapshot
                        outdoorWeatherRetrySession =
                            fetchResult.retryCoordinates?.let { coordinates ->
                                OutdoorWeatherRetrySession(
                                    draftGeneration = requestDraftGeneration,
                                    placeName = snapshot.placeName,
                                    coordinates = coordinates,
                                )
                            }
                        draftOutdoorWeatherError = fetchResult.weatherError
                        check(
                            persistDraft(draft.copy(outdoor = snapshot)) && awaitDraftPersistence()
                        ) {
                            saveError
                        }
                    }
                } finally {
                    if (outdoorRequestJob === operation) {
                        outdoorRequestJob = null
                        isDraftOutdoorLoading = false
                    }
                }
            }
        }

    fun addDraftImages(uris: List<Uri>): Boolean =
        requireDraftReady() && draftMediaOperations.addDraftImages(uris)

    fun removeDraftImage(uriString: String) = draftMediaOperations.removeDraftImage(uriString)

    fun queueDraftAudio(source: File, durationMillis: Long) {
        if (requireDraftReady()) draftMediaOperations.queueDraftAudio(source, durationMillis)
    }

    fun retryPendingDraftAudio() {
        if (requireDraftReady()) draftMediaOperations.retryPendingDraftAudio()
    }

    fun discardPendingDraftAudio() {
        if (requireDraftReady()) draftMediaOperations.discardPendingDraftAudio()
    }

    fun removeDraftAudio() = draftMediaOperations.removeDraftAudio()

    fun discardDraft() {
        if (isDraftSaving || pendingDraftAudio != null || isDraftVideoSaving) return
        val discardedDraft = draft
        if (persistDraft(JournalDraft())) {
            draftGeneration++
            cancelOutdoorRequest()
            viewModelScope.launch {
                if (awaitDraftPersistence())
                    draftMediaOperations.releaseDiscardedMedia(discardedDraft)
            }
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
                    id = snapshot.submissionId ?: UUID.randomUUID().toString(),
                    createdAt = snapshot.recordedAt ?: System.currentTimeMillis(),
                    mood = mood,
                    labels = snapshot.labels,
                    note = snapshot.note.trim(),
                    audio = snapshot.audio,
                    video = snapshot.video,
                    outdoor = snapshot.outdoor,
                    feelings = snapshot.feelings,
                    moodScaleVersion = snapshot.moodScaleVersion,
                ),
                snapshot.imageUriStrings.map(Uri::parse),
                submittedDraft = true,
            )
        }

    suspend fun save(entry: JournalEntry, imageUris: List<Uri>): Result<Unit> =
        save(entry, imageUris, submittedDraft = false)

    private suspend fun save(
        entry: JournalEntry,
        imageUris: List<Uri>,
        submittedDraft: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            if (!requireDraftReady() || !libraryReady)
                return@withContext Result.failure(
                    IllegalStateException(localizedText("日记数据库暂时无法读取，原数据未被覆盖。"))
                )
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
            if (pendingDraftAudio != null)
                return@withContext Result.failure(
                    IllegalStateException(
                        tr(
                            "请先完成语音保存，或明确放弃这段录音。",
                            "Finish saving the recording or discard it first.",
                        )
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
            val submission = savedDraft.copy(submissionId = entry.id).takeIf { submittedDraft }
            val submissionRevision = submission?.let {
                noteSaveJob?.cancel()
                draftStore.queue(it)
            }
            // The operation belongs to the ViewModel even if the requesting page leaves
            // composition.
            viewModelScope
                .async {
                    try {
                        val result =
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    if (submission != null) {
                                        check(
                                            draftStore.saveIfCurrent(
                                                submission,
                                                requireNotNull(submissionRevision),
                                            )
                                        ) {
                                            localizedText("草稿已发生变化，请重新保存。")
                                        }
                                    }
                                    store.add(
                                        entry,
                                        imageUris,
                                        refreshEntries = false,
                                        submittedDraft = submittedDraft,
                                    )
                                }
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
                                if (submission != null && draft == savedDraft) draft = submission
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

    internal suspend fun previewBackup(
        uri: Uri,
        password: String?,
        confirm: suspend (BackupSummary) -> BackupRestoreMode?,
    ): Result<Unit> = backupOperations.previewBackup(uri, password, confirm)

    suspend fun restoreBackup(
        uri: Uri,
        password: String?,
        mode: BackupRestoreMode = BackupRestoreMode.MERGE,
    ): Result<Int> = backupOperations.restoreBackup(uri, password, mode)

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
        withContext(Dispatchers.IO) { runCatching { store.saveVideoEditDraft(entry, video) } }

    internal suspend fun clearVideoEditDraft(entryId: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching { store.clearVideoEditDraft(entryId) } }

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

    fun queueDraftVideo(uri: Uri): Boolean =
        requireDraftReady() && draftMediaOperations.queueDraftVideo(uri)

    fun removeDraftVideo() = draftMediaOperations.removeDraftVideo()

    fun cancelPendingDraftVideo() = draftMediaOperations.cancelPendingDraftVideo()

    private fun persistDraft(updated: JournalDraft, reportError: Boolean = true): Boolean {
        if (!requireDraftReady(reportError)) return false
        val normalized =
            updated.copy(updatedAt = System.currentTimeMillis(), submissionId = null).normalized()
        draft = normalized
        if (normalized.isEmpty) hasResumedDraft = false
        scheduleNoteSave(0L)
        return true
    }

    private suspend fun awaitDraftPersistence(): Boolean =
        withContext(Dispatchers.IO) { runCatching { draftStore.flushPending() } }
            .onFailure { error ->
                dataError = error.message ?: localizedText("草稿保存失败，请重试。")
            }
            .isSuccess

    private fun requireDraftReady(reportError: Boolean = true): Boolean {
        if (draftReady && !isLoading) return true
        if (reportError) {
            dataError =
                tr(
                    "草稿尚未恢复，原内容已保留。请关闭提示重试，或稍后重新打开应用。",
                    "The draft has not been recovered. Its original contents are preserved. Dismiss this message to retry, or reopen the app later.",
                )
        }
        return false
    }

    private data class OutdoorWeatherRetrySession(
        val draftGeneration: Long,
        val placeName: String,
        val coordinates: OutdoorRetryCoordinates,
    )

    private suspend fun <T> runOutdoorRequest(request: suspend () -> T): Result<T> =
        try {
            Result.success(request())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private suspend fun clearDraftAfterSave(savedDraft: JournalDraft) {
        draftGeneration++
        val currentDraft = draft
        if (currentDraft == savedDraft) {
            persistDraft(JournalDraft())
            if (!awaitDraftPersistence()) return
        }
        draftMediaOperations.releaseSavedImages(savedDraft)
    }

    private suspend fun reconcileSavedDraft() {
        val submitted = draft
        val id = submitted.submissionId ?: return
        val cleared =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!store.hasSavedDraft(id)) false
                    else {
                        draftStore.save(JournalDraft())
                        true
                    }
                }
            }
        cleared
            .onSuccess {
                if (it) {
                    draft = JournalDraft()
                    hasResumedDraft = false
                    draftMediaOperations.releaseSavedImages(submitted)
                }
            }
            .onFailure {
                draftReady = false
                dataError = it.message ?: localizedText("草稿保存失败，请重试。")
            }
    }
}
