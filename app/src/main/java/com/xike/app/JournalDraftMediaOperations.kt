package com.xike.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xike.app.data.MAX_IMAGES_PER_ENTRY
import com.xike.app.data.model.JournalDraft
import com.xike.app.data.model.JournalVideo
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PendingDraftAudio(
    val durationMillis: Long,
    val draftGeneration: Long,
    val stagedFileName: String? = null,
    val source: File? = null,
)

/** Owns media jobs and pending results; durable draft writes remain in the ViewModel. */
internal class JournalDraftMediaOperations(
    context: Context,
    private val scope: CoroutineScope,
    private val store: JournalStore,
    private val currentDraft: () -> JournalDraft,
    private val currentDraftGeneration: () -> Long,
    private val savingDraft: () -> Boolean,
    private val saveDraft: (JournalDraft, Boolean) -> Boolean,
    private val awaitDraftPersistence: suspend () -> Boolean,
    private val imageInUse: (Uri) -> Boolean,
    private val onDataError: (String) -> Unit,
    private val reportOperation: (String, Boolean) -> Unit,
    private val mediaMutationMutex: Mutex,
) {
    private val context = context.applicationContext
    private val pendingAudioStore = PendingDraftAudioStore(context)
    private val draft: JournalDraft
        get() = currentDraft()

    private val isDraftSaving: Boolean
        get() = savingDraft()

    private val draftGeneration: Long
        get() = currentDraftGeneration()

    private var pendingDraftImageResults by mutableStateOf(emptySet<String>())
    val isDraftImageResultPending: Boolean
        get() = pendingDraftImageResults.isNotEmpty()

    var pendingDraftAudio by mutableStateOf<PendingDraftAudio?>(null)
        private set

    var isDraftAudioSaving by mutableStateOf(false)
        private set

    var draftAudioSaveError by mutableStateOf<String?>(null)
        private set

    var isDraftVideoSaving by mutableStateOf(false)
        private set

    var draftVideoProgress by mutableStateOf<Float?>(null)
        private set

    fun setDraftImageResultPending(key: String, pending: Boolean) {
        pendingDraftImageResults =
            if (pending) pendingDraftImageResults + key else pendingDraftImageResults - key
    }

    fun recoverPendingAudio(): PendingDraftAudio? {
        pendingAudioStore.recoverRecording()?.let { (file, duration) ->
            return PendingDraftAudio(duration, draftGeneration, source = file)
        }
        return pendingAudioStore.recover()?.let {
            PendingDraftAudio(it.durationMillis, draftGeneration, it.fileName)
        }
    }

    fun restorePendingAudio(pending: PendingDraftAudio?) {
        pendingDraftAudio = pending?.copy(draftGeneration = draftGeneration)
    }

    private fun persistDraft(updated: JournalDraft, reportError: Boolean = true): Boolean =
        saveDraft(updated, reportError)

    // Keep the imported file, draft publication and staging cleanup in one restore boundary.
    private fun launchMediaMutation(work: suspend () -> Unit) {
        scope.launch { mediaMutationMutex.withLock { work() } }
    }

    fun releaseDiscardedMedia(discardedDraft: JournalDraft) {
        discardedDraft.imageUriStrings
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .forEach(::releaseDraftImageAccess)
        discardedDraft.audio?.fileName?.let { fileName ->
            scope.launch(Dispatchers.IO) { store.deleteUnreferencedAudio(fileName) }
        }
        discardedDraft.video?.let { video ->
            scope.launch(Dispatchers.IO) { store.deleteUnreferencedVideo(video) }
        }
        discardedDraft.pendingVideoUri?.let { releaseVideoAccess(Uri.parse(it)) }
    }

    fun releaseSavedImages(savedDraft: JournalDraft) {
        savedDraft.imageUriStrings
            .filterNot { it in draft.imageUriStrings }
            .map(Uri::parse)
            .forEach(::releaseDraftImageAccess)
    }

    fun addDraftImages(uris: List<Uri>): Boolean {
        if (isDraftSaving) return false
        val availableSlots = MAX_IMAGES_PER_ENTRY - draft.imageUriStrings.size
        val candidates =
            uris.filterNot { it.toString() in draft.imageUriStrings }.distinctBy(Uri::toString)
        val newUris = candidates.take(availableSlots)
        if (newUris.isEmpty()) {
            candidates.forEach(::releaseDraftImageAccess)
            if (candidates.isNotEmpty())
                reportOperation(
                    tr(
                        "照片已达上限，请先移除照片再添加。",
                        "The photo limit has been reached. Remove a photo before adding more.",
                    ),
                    false,
                )
            return true
        }

        val application = context
        val contentResolver = application.contentResolver
        val grantedUris = mutableListOf<Uri>()
        newUris.forEach { uri ->
            if (isCameraCaptureUri(application, uri)) {
                val readable = runCatching {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
                }
                    .getOrDefault(false)
                if (readable) grantedUris += uri else deleteCameraCapture(application, uri)
            } else {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                    .onSuccess {
                        grantedUris += uri
                    }
            }
        }
        if (grantedUris.isEmpty()) {
            candidates.forEach(::releaseDraftImageAccess)
            onDataError(localizedText("所选照片无法获得长期读取权限，请重新选择。"))
            return true
        }

        val updated =
            draft.copy(imageUriStrings = draft.imageUriStrings + grantedUris.map(Uri::toString))
        if (!persistDraft(updated)) {
            return false
        } else if (grantedUris.size < newUris.size) {
            onDataError(localizedText("部分照片无法长期读取，已保留可以恢复的照片。"))
        }
        candidates.filterNot { it in grantedUris }.forEach(::releaseDraftImageAccess)
        if (candidates.size > availableSlots) {
            reportOperation(
                tr(
                    "本次已添加 ${grantedUris.size} 张照片，其余照片因数量上限未添加。",
                    "Added ${grantedUris.size} photos. The remaining photos were not added because of the limit.",
                ),
                false,
            )
        }
        return true
    }

    fun removeDraftImage(uriString: String) {
        if (uriString !in draft.imageUriStrings) return
        if (persistDraft(draft.copy(imageUriStrings = draft.imageUriStrings - uriString))) {
            scope.launch {
                if (awaitDraftPersistence())
                    runCatching { Uri.parse(uriString) }.getOrNull()?.let(::releaseDraftImageAccess)
            }
        }
    }

    fun queueDraftAudio(source: File, durationMillis: Long) {
        if (pendingDraftAudio != null) {
            source.delete()
            return
        }
        val pending = PendingDraftAudio(durationMillis, draftGeneration, source = source)
        pendingDraftAudio = pending
        stagePendingDraftAudio(pending)
    }

    private fun stagePendingDraftAudio(pending: PendingDraftAudio) {
        val source = pending.source ?: return
        isDraftAudioSaving = true
        draftAudioSaveError = null
        scope.launch {
            val staged =
                try {
                    withContext(Dispatchers.IO) {
                        pending.stagedFileName?.let { StagedDraftAudio(it, pending.durationMillis) }
                            ?: pendingAudioStore.stage(source, pending.durationMillis)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (pending === pendingDraftAudio) {
                        val recoverable =
                            withContext(Dispatchers.IO) {
                                if (source.isFile && source.length() > 0L) true
                                else {
                                    source.delete()
                                    false
                                }
                            }
                        if (recoverable) {
                            draftAudioSaveError = error.message ?: localizedText("录音保存失败，请重试。")
                        } else {
                            pendingDraftAudio = null
                            onDataError(error.message ?: localizedText("录音文件不可用。"))
                        }
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
            val sourceRemoved =
                withContext(Dispatchers.IO) {
                    runCatching { source.delete() || !source.exists() }.getOrDefault(false)
                }
            if (!sourceRemoved) {
                pendingDraftAudio = pending.copy(stagedFileName = staged.fileName)
                draftAudioSaveError = localizedText("录音暂存文件暂时无法清理。")
                isDraftAudioSaving = false
                return@launch
            }
            pendingDraftAudio = pending.copy(stagedFileName = staged.fileName, source = null)
            isDraftAudioSaving = false
            retryPendingDraftAudio()
        }
    }

    fun retryPendingDraftAudio() {
        val pending = pendingDraftAudio ?: return
        if (isDraftAudioSaving) return
        if (pending.source != null) {
            stagePendingDraftAudio(pending)
            return
        }
        val staged =
            pending.stagedFileName?.let { StagedDraftAudio(it, pending.durationMillis) } ?: return
        if (isDraftAudioSaving) return
        isDraftAudioSaving = true
        draftAudioSaveError = null
        launchMediaMutation {
            try {
                val imported =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            pendingAudioStore.open(staged).use { input ->
                                store.importAudio(input, pending.durationMillis)
                            }
                        }
                    }
                val result = imported.mapCatching { audio ->
                    check(
                        pending === pendingDraftAudio && pending.draftGeneration == draftGeneration
                    ) {
                        localizedText("这段录音已经取消。")
                    }
                    val previousAudio = draft.audio
                    check(
                        persistDraft(draft.copy(audio = audio), reportError = false) &&
                            awaitDraftPersistence()
                    ) {
                        localizedText("录音已经完成，但草稿保存失败。")
                    }
                    if (previousAudio != null && previousAudio.fileName != audio.fileName) {
                        scope.launch(Dispatchers.IO) {
                            store.deleteUnreferencedAudio(previousAudio.fileName)
                        }
                    }
                }
                imported
                    .getOrNull()
                    ?.takeIf { result.isFailure }
                    ?.let { audio ->
                        withContext(Dispatchers.IO) {
                            runCatching { store.deleteUnreferencedAudio(audio.fileName) }
                        }
                    }
                if (pending === pendingDraftAudio) {
                    result
                        .onSuccess {
                            check(
                                withContext(Dispatchers.IO) { pendingAudioStore.delete(staged) }
                            ) {
                                localizedText("录音已保存，但暂存文件清理失败，请重试。")
                            }
                            pendingDraftAudio = null
                        }
                        .onFailure { error ->
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
        isDraftAudioSaving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pending.stagedFileName?.let {
                        check(
                            pendingAudioStore.delete(StagedDraftAudio(it, pending.durationMillis))
                        )
                    }
                    pending.source?.let { check(it.delete() || !it.exists()) }
                }
                pendingDraftAudio = null
                draftAudioSaveError = null
            } catch (error: Exception) {
                draftAudioSaveError = localizedText("录音暂存文件暂时无法删除，请重试。")
            } finally {
                isDraftAudioSaving = false
            }
        }
    }

    fun removeDraftAudio() {
        val removed = draft.audio ?: return
        if (persistDraft(draft.copy(audio = null))) {
            scope.launch {
                if (awaitDraftPersistence())
                    withContext(Dispatchers.IO) { store.deleteUnreferencedAudio(removed.fileName) }
            }
        }
    }

    suspend fun importVideo(uri: Uri, progress: (Float) -> Unit): Result<JournalVideo> =
        withContext(Dispatchers.IO) {
            runCatching { store.importVideo(uri, progress) }
        }

    fun releaseVideo(video: JournalVideo) {
        scope.launch(Dispatchers.IO) { store.deleteUnreferencedVideo(video) }
    }

    private fun releaseVideoAccess(uri: Uri) {
        if (draft.pendingVideoUri == uri.toString()) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun queueDraftVideo(uri: Uri): Boolean {
        if (isDraftVideoSaving) return false
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        if (!persistDraft(draft.copy(pendingVideoUri = uri.toString()))) return false
        val generation = draftGeneration
        isDraftVideoSaving = true
        draftVideoProgress = null
        launchMediaMutation {
            try {
                if (!awaitDraftPersistence()) return@launchMediaMutation
                importVideo(uri) { progress -> scope.launch { draftVideoProgress = progress } }
                    .onSuccess { video ->
                        val previous = draft.video
                        if (
                            generation == draftGeneration &&
                                persistDraft(draft.copy(video = video, pendingVideoUri = null)) &&
                                awaitDraftPersistence()
                        ) {
                            previous?.let(::releaseVideo)
                            releaseVideoAccess(uri)
                        } else releaseVideo(video)
                    }
                    .onFailure { error ->
                        onDataError(
                            error.message
                                ?: tr(
                                    "视频导入失败，请重试或取消本次导入。",
                                    "Video import failed. Retry or cancel this import.",
                                )
                        )
                    }
            } finally {
                isDraftVideoSaving = false
                draftVideoProgress = null
            }
        }
        return true
    }

    fun removeDraftVideo() {
        if (isDraftVideoSaving) return
        val previous = draft.video
        val pending = draft.pendingVideoUri
        if (persistDraft(draft.copy(video = null, pendingVideoUri = null))) {
            scope.launch {
                if (awaitDraftPersistence()) {
                    previous?.let(::releaseVideo)
                    pending?.let { releaseVideoAccess(Uri.parse(it)) }
                }
            }
        }
    }

    fun cancelPendingDraftVideo() {
        if (isDraftSaving || isDraftVideoSaving) return
        val pending = draft.pendingVideoUri ?: return
        if (persistDraft(draft.copy(pendingVideoUri = null))) {
            scope.launch {
                if (awaitDraftPersistence()) releaseVideoAccess(Uri.parse(pending))
            }
        }
    }

    private fun releaseDraftImageAccess(uri: Uri) {
        if (imageInUse(uri) || uri.toString() in draft.imageUriStrings) return
        val application = context
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
}
