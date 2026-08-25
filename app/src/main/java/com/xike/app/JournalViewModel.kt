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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val store = JournalStore(application)
    private val draftStore = JournalDraftStore(application)
    private val outdoorRepository = OutdoorContextRepository(application)

    var entries by mutableStateOf(emptyList<JournalEntry>())
        private set

    var selectedTheme by mutableStateOf(AppTheme.OCEAN)
        private set

    var draft by mutableStateOf(JournalDraft())
        private set

    var dataError by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var canUndoRestore by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val draftResult = withContext(Dispatchers.IO) { runCatching(::loadAccessibleDraft) }
            val result = withContext(Dispatchers.IO) { runCatching(store::initialize) }
            result.onSuccess { snapshot ->
                entries = snapshot.entries
                selectedTheme = AppTheme.entries.firstOrNull { it.name == snapshot.themeName } ?: AppTheme.OCEAN
                canUndoRestore = runCatching(store::canUndoLastRestore).getOrDefault(false)
                draftResult.onSuccess { draft = it }
                dataError = draftResult.exceptionOrNull()?.message
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching(store::removeOrphanedImages)
                }
                viewModelScope.launch {
                    store.observeEntries()
                        .catch { error ->
                            dataError = error.message ?: "日记数据库暂时无法读取，原数据未被覆盖。"
                        }
                        .collect { latestEntries -> entries = latestEntries }
                }
            }.onFailure { error ->
                dataError = error.message ?: "日记数据库暂时无法读取，原数据未被覆盖。"
            }
            isLoading = false
        }
    }

    fun dismissDataError() {
        dataError = null
    }

    fun selectTheme(theme: AppTheme) {
        selectedTheme = theme
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { store.saveThemeName(theme.name) } }
                .onFailure { error ->
                    dataError = error.message ?: "外观设置保存失败，请重试。"
                }
        }
    }

    fun selectDraftMood(mood: Mood?) {
        persistDraft(draft.copy(mood = mood))
    }

    fun updateDraftNote(note: String) {
        persistDraft(draft.copy(note = note.take(MAX_DRAFT_NOTE_LENGTH)))
    }

    fun toggleDraftTag(tag: String) {
        val updatedTags = if (tag in draft.tags) draft.tags - tag else draft.tags + tag
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

    suspend fun attachCurrentOutdoor(): Result<Unit> = viewModelScope.async {
        runCatching { outdoorRepository.current() }.mapCatching { snapshot ->
            check(draft.recordedAt == null) { "补记过去时不会附加今天的天气。" }
            check(persistDraft(draft.copy(outdoor = snapshot))) {
                "地点与天气已取得，但草稿保存失败。"
            }
        }
    }.await()

    suspend fun attachOutdoorForCity(city: String): Result<Unit> = viewModelScope.async {
        runCatching { outdoorRepository.city(city) }.mapCatching { snapshot ->
            check(draft.recordedAt == null) { "补记过去时不会附加今天的天气。" }
            check(persistDraft(draft.copy(outdoor = snapshot))) {
                "城市天气已取得，但草稿保存失败。"
            }
        }
    }.await()

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
            dataError = "所选照片无法获得长期读取权限，请重新选择。"
            return
        }

        val updated = draft.copy(
            imageUriStrings = draft.imageUriStrings + grantedUris.map(Uri::toString),
        )
        if (!persistDraft(updated)) {
            grantedUris.forEach(::releaseDraftImageAccess)
        } else if (grantedUris.size < newUris.size) {
            dataError = "部分照片无法长期读取，已保留可以恢复的照片。"
        }
    }

    fun removeDraftImage(uriString: String) {
        if (uriString !in draft.imageUriStrings) return
        if (persistDraft(draft.copy(imageUriStrings = draft.imageUriStrings - uriString))) {
            runCatching { Uri.parse(uriString) }.getOrNull()?.let(::releaseDraftImageAccess)
        }
    }

    fun discardDraft() {
        val discardedDraft = draft
        if (persistDraft(JournalDraft())) {
            discardedDraft.imageUriStrings
                .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                .forEach(::releaseDraftImageAccess)
        }
    }

    suspend fun save(entry: JournalEntry, imageUris: List<Uri>): Result<Unit> = viewModelScope.async {
        val savedDraft = draft
        val result = withContext(Dispatchers.IO) {
            runCatching { store.add(entry, imageUris) }
        }
        result.onSuccess { restoredEntries ->
            entries = restoredEntries
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
            runCatching { store.update(entry, retainedImageFileNames, newImageUris) }
        }
        result.onSuccess { updatedEntries ->
            entries = updatedEntries
            dataError = null
        }
        result.mapCatching { updatedEntries ->
            updatedEntries.first { it.id == entry.id }
        }
    }.await()

    suspend fun delete(entry: JournalEntry): Result<Unit> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching { store.delete(entry.id) }
        }
        result.onSuccess { remainingEntries ->
            entries = remainingEntries
            dataError = null
        }
        result.map { }
    }.await()

    suspend fun undoDelete(entryId: String): Result<Unit> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching { store.undoDelete(entryId) }
        }
        result.onSuccess { restoredEntries ->
            entries = restoredEntries
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

    suspend fun exportBackup(uri: Uri, password: String): Result<Unit> = viewModelScope.async(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                store.writeEncryptedBackup(output, password)
            } ?: error("无法写入备份文件")
        }
    }.await()

    suspend fun inspectBackup(uri: Uri, password: String): Result<BackupSummary> = viewModelScope.async(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                store.inspectEncryptedBackup(input, password)
            } ?: error("无法读取备份文件")
        }
    }.await()

    suspend fun restoreBackup(uri: Uri, password: String): Result<Int> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    store.restoreEncryptedBackup(input, password)
                } ?: error("无法读取备份文件")
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
        val result = withContext(Dispatchers.IO) { runCatching(store::undoLastRestore) }
        result.onSuccess {
            entries = it
            canUndoRestore = false
            dataError = null
        }
        result.map { it.size }
    }.await()

    fun openImage(fileName: String): InputStream? = store.openImage(fileName)

    private fun persistDraft(updated: JournalDraft): Boolean {
        val normalized = updated.copy(updatedAt = System.currentTimeMillis()).normalized()
        return runCatching { draftStore.save(normalized) }
            .onSuccess {
                draft = normalized
            }
            .onFailure { error ->
                dataError = error.message ?: "草稿保存失败，请重试。"
            }
            .isSuccess
    }

    private fun clearDraftAfterSave(savedDraft: JournalDraft) {
        val currentDraft = draft
        if (currentDraft == savedDraft) {
            draft = JournalDraft()
            runCatching { draftStore.save(draft) }
                .onFailure { error ->
                    dataError = error.message ?: "记录已保存，但草稿清理失败。"
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
        if (loaded.imageUriStrings.isEmpty()) {
            pruneCameraCaptures(application)
            return loaded
        }
        val contentResolver = application.contentResolver
        val readableUris = contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
        val accessible = loaded.copy(
            imageUriStrings = loaded.imageUriStrings.filter { uriString ->
                val uri = Uri.parse(uriString)
                (isCameraCaptureUri(application, uri) || uriString in readableUris) && runCatching {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
                }.getOrDefault(false)
            },
        )
        if (accessible != loaded) {
            draftStore.save(accessible)
            loaded.imageUriStrings
                .filterNot { it in accessible.imageUriStrings }
                .map(Uri::parse)
                .forEach(::releaseDraftImageAccess)
        }
        pruneCameraCaptures(application)
        return accessible
    }
}
