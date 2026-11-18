package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.BackupSummary
import com.xike.app.data.JournalEntry
import com.xike.app.data.backup.PreparedBackup
import com.xike.app.data.model.JournalDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class RestoreOperation {
    RESTORE,
    UNDO,
}

internal class RestoreOutcome(
    val operation: RestoreOperation,
    val result: Result<Int>,
    val mode: BackupRestoreMode = BackupRestoreMode.MERGE,
    val undoAvailable: Boolean = true,
)

internal class BackupRestoreState {
    var operation by mutableStateOf<RestoreOperation?>(null)
        internal set

    var outcome by mutableStateOf<RestoreOutcome?>(null)
        internal set

    fun acknowledge(value: RestoreOutcome) {
        if (outcome === value) outcome = null
    }
}

/**
 * Backup jobs belong to the ViewModel scope; the ViewModel consumes restored entries and feedback.
 */
internal class JournalBackupOperations(
    context: Context,
    private val scope: CoroutineScope,
    private val store: JournalStore,
    private val currentDraft: () -> JournalDraft,
    private val mediaMutationMutex: Mutex,
    private val onRestored: (List<JournalEntry>, Boolean) -> Unit,
    private val reportOperation: (String, Boolean) -> Unit,
) {
    private val context = context.applicationContext
    private val draft: JournalDraft
        get() = currentDraft()

    val restoreState = BackupRestoreState()
    var isBackupExporting by mutableStateOf(false)
        private set

    fun startBackupExport(uri: Uri, password: String?) {
        if (isBackupExporting) return
        isBackupExporting = true
        scope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                store.writeBackup(output, password)
                            } ?: error(localizedText("无法写入备份文件"))
                        }
                    }
                result
                    .onSuccess { reportOperation(localizedText("备份已保存"), false) }
                    .onFailure { reportOperation(it.message ?: localizedText("备份失败"), true) }
            } finally {
                isBackupExporting = false
            }
        }
    }

    suspend fun backupRequiresPassword(uri: Uri): Result<Boolean> =
        scope
            .async(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use(store::backupRequiresPassword)
                        ?: error(localizedText("无法读取备份文件"))
                }
            }
            .await()

    suspend fun previewBackup(
        uri: Uri,
        password: String?,
        confirm: suspend (BackupSummary) -> BackupRestoreMode?,
    ): Result<Unit> {
        var staged: PreparedBackup? = null
        try {
            val preview = journalReadResult {
                withContext(Dispatchers.IO) {
                    val backup =
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            store.prepareRestore(input, password).also { staged = it }
                        } ?: error(localizedText("无法读取备份文件"))
                    store.inspectBackup(backup)
                }
            }
            val summary = preview.getOrElse {
                return Result.failure(it)
            }
            val mode = confirm(summary) ?: return Result.success(Unit)
            val backup = checkNotNull(staged)
            val restore =
                startRestore(RestoreOperation.RESTORE, mode, backup) { snapshot ->
                    store.restoreBackup(
                        backup,
                        setOfNotNull(snapshot.audio?.fileName),
                        snapshot.video,
                        mode,
                    )
                }
            staged = null // The ViewModel now owns the staging directory.
            restore.await()
            return Result.success(Unit)
        } finally {
            staged?.let { withContext(NonCancellable + Dispatchers.IO) { it.close() } }
        }
    }

    suspend fun restoreBackup(
        uri: Uri,
        password: String?,
        mode: BackupRestoreMode = BackupRestoreMode.MERGE,
    ): Result<Int> =
        startRestore(RestoreOperation.RESTORE, mode) { snapshot ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    store.restoreBackup(
                        input,
                        password,
                        setOfNotNull(snapshot.audio?.fileName),
                        snapshot.video,
                        mode,
                    )
                } ?: error(localizedText("无法读取备份文件"))
            }
            .await()

    suspend fun undoRestore(): Result<Int> =
        startRestore(RestoreOperation.UNDO) { snapshot ->
                store.undoLastRestore(setOfNotNull(snapshot.audio?.fileName), snapshot.video)
            }
            .await()

    @OptIn(DelicateCoroutinesApi::class)
    private fun startRestore(
        operation: RestoreOperation,
        mode: BackupRestoreMode = BackupRestoreMode.MERGE,
        staged: PreparedBackup? = null,
        work: (JournalDraft) -> List<JournalEntry>,
    ) =
        scope.async(start = CoroutineStart.ATOMIC) {
            // Enter the cleanup block even if the ViewModel is cancelled after accepting a preview.
            try {
                if (restoreState.operation != null) {
                    val failure =
                        Result.failure<Int>(
                            IllegalStateException(
                                tr("请等待当前操作完成。", "Please wait for the current operation to finish.")
                            )
                        )
                    if (staged != null)
                        reportOperation(requireNotNull(failure.exceptionOrNull()?.message), true)
                    return@async failure
                }
                restoreState.operation = operation
                try {
                    val result = mediaMutationMutex.withLock {
                        // Read the draft only after any import has published its durable reference.
                        val snapshot = draft
                        withContext(Dispatchers.IO) { runCatching { work(snapshot) } }
                    }
                    val undoAvailable =
                        if (result.isSuccess)
                            withContext(Dispatchers.IO) {
                                runCatching(store::canUndoLastRestore).getOrDefault(false)
                            }
                        else false
                    result.onSuccess { onRestored(it, undoAvailable) }
                    result
                        .map { it.size }
                        .also { outcome ->
                            if (operation == RestoreOperation.RESTORE && outcome.isSuccess) {
                                restoreState.outcome =
                                    RestoreOutcome(operation, outcome, mode, undoAvailable)
                            } else {
                                val message =
                                    outcome.fold(
                                        onSuccess = { count ->
                                            tr(
                                                "已撤销恢复，找回 $count 条日记",
                                                "Restore undone. Recovered $count entries",
                                            )
                                        },
                                        onFailure = {
                                            it.message
                                                ?: localizedText(
                                                    if (operation == RestoreOperation.UNDO) "撤销恢复失败"
                                                    else "恢复失败"
                                                )
                                        },
                                    )
                                reportOperation(message, outcome.isFailure)
                            }
                        }
                } finally {
                    restoreState.operation = null
                }
            } finally {
                staged?.let { withContext(NonCancellable + Dispatchers.IO) { it.close() } }
            }
        }
}
