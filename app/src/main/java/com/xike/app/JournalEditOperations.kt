package com.xike.app

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal enum class EditOperation { RECOVER, AUDIO, VIDEO, REMOVE_VIDEO, SAVE, DISCARD }
internal sealed interface EditOutcome {
    data class AudioReady(val audio: JournalAudio) : EditOutcome
    data object AudioCleared : EditOutcome
    data class AudioPending(val staged: StagedDraftAudio, val message: String) : EditOutcome
    data class Recovered(val raw: Pair<File, Long>?, val staged: StagedDraftAudio?) : EditOutcome
    data class Restored(val audio: EditOutcome, val videoDraft: VideoEditDraft?) : EditOutcome
    data class VideoReady(val video: JournalVideo?) : EditOutcome
    data class Saved(val entry: JournalEntry, val cleanupFailed: Boolean = false) : EditOutcome
    data object Discarded : EditOutcome
    data class Failed(val message: String) : EditOutcome
}

internal class EditOperationState {
    var operation by mutableStateOf<EditOperation?>(null)
        internal set
    var outcome by mutableStateOf<EditOutcome?>(null)
        internal set
    var progress by mutableStateOf<Float?>(null)
        internal set
    internal val imageUris = mutableSetOf<Uri>()
    internal var ended = false
    val busy: Boolean get() = operation != null
}

/** Owns work and its unconsumed result independently of the editor's composition. */
internal class JournalEditOperations(
    context: Context,
    private val scope: CoroutineScope,
    private val store: JournalStore,
    private val draftImageUris: () -> List<String>,
) {
    private val context = context.applicationContext
    private val states = mutableMapOf<String, EditOperationState>()
    private val retiredImageLeases = mutableSetOf<Uri>()
    private val receipts by lazy {
        EncryptedSharedPreferences.create(this.context, "xike-edit-audio-receipts",
            MasterKey.Builder(this.context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    fun state(id: String): EditOperationState = states.getOrPut(id, ::EditOperationState)

    fun start(id: String, operation: EditOperation, work: suspend () -> EditOutcome): Boolean {
        val state = state(id)
        if (state.busy || state.ended) return false
        state.operation = operation
        scope.launch {
            val outcome = try { work() } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                EditOutcome.Failed(error.message ?: localizedText("修改保存失败，请重试。"))
            }
            state.ended = outcome is EditOutcome.Saved || outcome is EditOutcome.Discarded
            state.outcome = outcome
        }
        return true
    }

    fun acknowledge(id: String) {
        val state = state(id)
        state.outcome = null
        state.operation = null
        state.progress = null
        if (state.ended) {
            retiredImageLeases.addAll(state.imageUris)
            states.remove(id)
            if (retiredImageLeases.isNotEmpty()) scope.launch {
                repeat(3) {
                    delay(1_000)
                    retiredImageLeases.toList().forEach { uri ->
                        runCatching { releaseUnusedImage(uri) }.onSuccess { retiredImageLeases.remove(uri) }
                    }
                }
            }
        }
    }

    private fun key(id: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8)))
    private fun audioStore(id: String) = PendingDraftAudioStore(context, "pending-edit-audio/${key(id)}")
    fun recordingDirectory(id: String) = File(context.cacheDir, "edit-recordings/${key(id)}")
    private fun rawDirectory(id: String) = recordingDirectory(id)
    private fun receipt(id: String): JSONObject? = receipts.getString(key(id), null)?.let(::JSONObject)
    private fun receiptAudio(receipt: JSONObject): JournalAudio = requireNotNull(JournalAudio.fromJson(receipt.getJSONObject("audio")))

    fun progress(id: String, value: Float) {
        scope.launch { states[id]?.takeIf { it.operation == EditOperation.VIDEO }?.progress = value }
    }
    fun imageInUse(uri: Uri): Boolean = states.values.any { uri in it.imageUris && !it.ended }

    suspend fun recoverAudio(id: String): EditOutcome = withContext(Dispatchers.IO) {
        val raw = rawDirectory(id).takeIf(File::exists)?.let { directory ->
            checkNotNull(directory.listFiles()).filter { it.isFile }.maxByOrNull(File::lastModified)
        }
        if (raw != null) {
            val retriever = MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(raw.absolutePath)
                requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
            } finally { retriever.release() }
            require(duration in 1..MAX_AUDIO_DURATION_MILLIS)
            EditOutcome.Recovered(raw to duration, null)
        } else {
            val staged = audioStore(id).recoverLatest()
            val receiptResult = runCatching { receipt(id) }
            val receipt = receiptResult.getOrNull()
            if (receipt != null && (staged == null || receipt.optString("staged") == staged.fileName)) {
                val imported = runCatching {
                    receiptAudio(receipt).also { store.requireReadableAudio(it.fileName) }
                }
                if (imported.isSuccess) EditOutcome.AudioReady(imported.getOrThrow())
                else if (staged != null) EditOutcome.Recovered(null, staged)
                else throw requireNotNull(imported.exceptionOrNull())
            } else {
                if (staged == null) receiptResult.getOrThrow()
                EditOutcome.Recovered(null, staged)
            }
        }
    }

    suspend fun stageAudio(id: String, raw: Pair<File, Long>): StagedDraftAudio = withContext(Dispatchers.IO + NonCancellable) {
        audioStore(id).stage(raw.first, raw.second).also { check(raw.first.delete()) }
    }

    suspend fun importAudio(id: String, raw: Pair<File, Long>?, pending: StagedDraftAudio?): EditOutcome =
        withContext(Dispatchers.IO + NonCancellable) {
            val staging = audioStore(id)
            val previousReceipt = runCatching { receipt(id) }.getOrNull()
            if (previousReceipt != null && (
                    pending?.fileName == previousReceipt.optString("staged") ||
                        raw?.first?.absolutePath == previousReceipt.optString("raw"))) {
                val imported = runCatching {
                    receiptAudio(previousReceipt).also { store.requireReadableAudio(it.fileName) }
                }
                if (imported.isSuccess) return@withContext EditOutcome.AudioReady(imported.getOrThrow())
            }
            val staged = pending ?: if (raw?.first?.isFile == true) stageAudio(id, raw)
                else requireNotNull(staging.recoverLatest()) { localizedText("录音暂存文件暂时无法读取，原文件已保留。") }
            runCatching {
                val audio = staging.open(staged).use { store.importEditAudio(it, staged.durationMillis) }
                try {
                    check(receipts.edit().putString(key(id), JSONObject()
                        .put("audio", audio.toJson()).put("staged", staged.fileName)
                        .put("raw", raw?.first?.absolutePath ?: JSONObject.NULL).toString()).commit())
                } catch (error: Throwable) {
                    runCatching { check(receipts.edit().remove(key(id)).commit()) }
                    runCatching { store.releaseEditAudio(audio) }
                    throw error
                }
                audio
            }
                .fold(
                    onSuccess = { audio -> staging.delete(staged); EditOutcome.AudioReady(audio) },
                    onFailure = { EditOutcome.AudioPending(staged, it.message ?: localizedText("录音暂存文件暂时无法读取，原文件已保留。")) },
                )
        }

    suspend fun discardAudio(id: String, preserveReceipt: Boolean = false) = withContext(Dispatchers.IO + NonCancellable) {
        audioStore(id).discardAll()
        if (!preserveReceipt) check(receipts.edit().remove(key(id)).commit())
        rawDirectory(id).takeIf(File::exists)?.let { directory ->
            checkNotNull(directory.listFiles()).forEach { check(it.delete()) }
        }
    }

    fun retainImage(id: String, uri: Uri) { state(id).takeUnless { it.ended }?.imageUris?.add(uri) }
    suspend fun releaseImage(id: String, uri: Uri) = withContext(Dispatchers.Main.immediate) {
        val state = state(id)
        if (uri !in state.imageUris) return@withContext
        val shared = uri.toString() in draftImageUris() || states.any { (otherId, other) ->
            otherId != id && uri in other.imageUris
        }
        if (!shared && !isCameraCaptureUri(context, uri)) {
            if (context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        state.imageUris.remove(uri)
    }
    private fun releaseUnusedImage(uri: Uri) {
        if (uri.toString() in draftImageUris() || imageInUse(uri) || isCameraCaptureUri(context, uri)) return
        if (context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    suspend fun releaseImages(id: String) {
        state(id).imageUris.toList().forEach { releaseImage(id, it) }
    }
}

internal val LocalJournalEditOperations = compositionLocalOf<JournalEditOperations> { error("Edit operation service unavailable") }
