package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xike.app.data.BackupSummary
import com.xike.app.data.JournalEntry
import com.xike.app.data.model.JournalDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backup jobs belong to the ViewModel scope; the ViewModel consumes restored entries and feedback. */
internal class JournalBackupOperations(
    context: Context,
    private val scope: CoroutineScope,
    private val store: JournalStore,
    private val currentDraft: () -> JournalDraft,
    private val importingDraftVideo: () -> Boolean,
    private val onRestored: (List<JournalEntry>, Boolean) -> Unit,
    private val reportOperation: (String, Boolean) -> Unit,
) {
    private val context = context.applicationContext
    private val draft: JournalDraft get() = currentDraft()
    private val isDraftVideoSaving: Boolean get() = importingDraftVideo()
    var isBackupExporting by mutableStateOf(false)
        private set

    fun startBackupExport(uri: Uri, password: String?) {
        if (isBackupExporting) return
        isBackupExporting = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            store.writeBackup(output, password)
                        } ?: error(localizedText("无法写入备份文件"))
                    }
                }
                result.onSuccess { reportOperation(localizedText("备份已保存"), false) }
                    .onFailure { reportOperation(it.message ?: localizedText("备份失败"), true) }
            } finally {
                isBackupExporting = false
            }
        }
    }

    suspend fun backupRequiresPassword(uri: Uri): Result<Boolean> = scope.async(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use(store::backupRequiresPassword)
                ?: error(localizedText("无法读取备份文件"))
        }
    }.await()

    suspend fun inspectBackup(uri: Uri, password: String?): Result<BackupSummary> = scope.async(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                store.inspectBackup(input, password)
            } ?: error(localizedText("无法读取备份文件"))
        }
    }.await()

    suspend fun restoreBackup(uri: Uri, password: String?): Result<Int> = scope.async {
        if (isDraftVideoSaving) return@async Result.failure(IllegalStateException(tr("请等待视频导入完成后再恢复备份。", "Wait for video import to finish before restoring a backup.")))
        val result = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    store.restoreBackup(input, password, setOfNotNull(draft.audio?.fileName), draft.video)
                } ?: error(localizedText("无法读取备份文件"))
            }
        }
        result.onSuccess { onRestored(it, true) }
        result.map { it.size }
    }.await()

    suspend fun undoRestore(): Result<Int> = scope.async {
        if (isDraftVideoSaving) return@async Result.failure(IllegalStateException(tr("请等待视频导入完成后再撤销恢复。", "Wait for video import to finish before undoing a restore.")))
        val result = withContext(Dispatchers.IO) {
            runCatching { store.undoLastRestore(setOfNotNull(draft.audio?.fileName), draft.video) }
        }
        result.onSuccess { onRestored(it, false) }
        result.map { it.size }
    }.await()

}
