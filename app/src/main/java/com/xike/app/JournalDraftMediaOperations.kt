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

    fun recoverPendingAudio(): StagedDraftAudio? = pendingAudioStore.recover()

    fun restorePendingAudio(staged: StagedDraftAudio?) {
        pendingDraftAudio = staged?.let {
            PendingDraftAudio(it.durationMillis, draftGeneration, it.fileName)
        }
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
        scope.launch {
            val staged =
                try {
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
                        onDataError(error.message ?: localizedText("录音未能加密暂存，请重新录制。"))
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
                    check(persistDraft(draft.copy(audio = audio), reportError = false)) {
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
        val staged =
            pending.stagedFileName?.let { StagedDraftAudio(it, pending.durationMillis) } ?: return
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
            scope.launch(Dispatchers.IO) { store.deleteUnreferencedAudio(removed.fileName) }
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
                importVideo(uri) { progress -> scope.launch { draftVideoProgress = progress } }
                    .onSuccess { video ->
                        val previous = draft.video
                        if (
                            generation == draftGeneration &&
                                persistDraft(draft.copy(video = video, pendingVideoUri = null))
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
            previous?.let(::releaseVideo)
            pending?.let { releaseVideoAccess(Uri.parse(it)) }
        }
    }

    fun cancelPendingDraftVideo() {
        if (isDraftSaving || isDraftVideoSaving) return
        val pending = draft.pendingVideoUri ?: return
        if (persistDraft(draft.copy(pendingVideoUri = null))) {
            releaseVideoAccess(Uri.parse(pending))
        }
    }

    private fun releaseDraftImageAccess(uri: Uri) {
        if (imageInUse(uri)) return
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
