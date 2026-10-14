package com.xike.app

import com.xike.app.data.BackupSummary
import com.xike.app.feature.settings.PendingExport
import com.xike.app.feature.settings.BackupExportState
import com.xike.app.feature.settings.RestoreConfirmationDialog
import com.xike.app.feature.settings.BackupExportDialog
import com.xike.app.feature.settings.BackupPasswordDialog
import com.xike.app.ui.components.ProcessingDialog
import com.xike.app.ui.components.XikeNotice
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class BackupAction { EXPORT, IMPORT }


private data class PendingRestore(
    val uri: Uri,
    val password: String?,
    val summary: BackupSummary,
)


@Composable
internal fun XikeBackupFlow(
    localEntryCount: Int,
    isBackupExporting: Boolean,
    restoreState: BackupRestoreState,
    onExportBackup: (Uri, String?) -> Unit,
    onBackupRequiresPassword: suspend (Uri) -> Result<Boolean>,
    onInspectBackup: suspend (Uri, String?) -> Result<BackupSummary>,
    onRestoreBackup: suspend (Uri, String?) -> Result<Int>,
    onUndoRestore: suspend () -> Result<Int>,
    content: @Composable (
        onExport: () -> Unit,
        onImport: () -> Unit,
        onUndoRestore: () -> Unit,
        snackbarHostState: SnackbarHostState,
    ) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupAction by remember { mutableStateOf<BackupAction?>(null) }
    val exportState: BackupExportState = viewModel()
    var exportRequested by rememberSaveable { mutableStateOf(false) }
    var encryptedExportRequested by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun runUndoRestore() {
        snackbarHostState.currentSnackbarData?.dismiss()
        onUndoRestore()
    }

    LaunchedEffect(restoreState.outcome) {
        val outcome = restoreState.outcome ?: return@LaunchedEffect
        val result = outcome.result
        if (result.isSuccess && outcome.operation == RestoreOperation.RESTORE) {
            val count = result.getOrThrow()
            val action = snackbarHostState.showSnackbar(
                message = tr("已恢复 $count 条日记", "Restored $count entries"),
                actionLabel = localizedText("撤销"),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            restoreState.acknowledge(outcome)
            if (action == SnackbarResult.ActionPerformed) scope.launch { runUndoRestore() }
        }
    }

    suspend fun inspectSelectedBackup(uri: Uri, password: String?) {
        busyMessage = localizedText("正在验证备份…")
        onInspectBackup(uri, password)
            .onSuccess { summary -> pendingRestore = PendingRestore(uri, password, summary) }
            .onFailure { XikeNotice.makeText(context, it.message ?: localizedText("备份验证失败"), XikeNotice.LENGTH_LONG).show() }
        busyMessage = null
    }

    fun handleExportSelection(uri: Uri?) {
        // Keep passwords only in memory across configuration changes. After process death,
        // plain exports can resume; encrypted exports must ask the user to start again.
        val export = exportState.take()
            ?: if (exportRequested && !encryptedExportRequested) PendingExport(null) else null
        exportRequested = false
        encryptedExportRequested = false
        if (uri != null && export == null) {
            XikeNotice.makeText(context, tr("备份导出已中断，文件尚未写入。请重新导出并输入密码。", "Backup export was interrupted. The file has not been written. Export again and enter the password."), XikeNotice.LENGTH_LONG).show()
        }
        if (uri != null && export != null) {
            onExportBackup(uri, export.password)
        }
    }

    val encryptedExportLauncher = rememberSystemActivityResultLauncher(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
        "backup-export-encrypted",
        ::handleExportSelection,
    )
    val plainExportLauncher = rememberSystemActivityResultLauncher(
        ActivityResultContracts.CreateDocument("application/zip"),
        "backup-export-plain",
        ::handleExportSelection,
    )

    val importLauncher = rememberSystemActivityResultLauncher(ActivityResultContracts.OpenDocument(), "backup-import") { uri ->
        if (uri != null) {
            scope.launch {
                busyMessage = localizedText("正在识别备份…")
                onBackupRequiresPassword(uri)
                    .onSuccess { requiresPassword ->
                        busyMessage = null
                        if (requiresPassword) {
                            pendingImportUri = uri.toString()
                            backupAction = BackupAction.IMPORT
                        } else {
                            inspectSelectedBackup(uri, null)
                        }
                    }
                    .onFailure {
                        busyMessage = null
                        XikeNotice.makeText(context, it.message ?: localizedText("无法识别备份"), XikeNotice.LENGTH_LONG).show()
                    }
            }
        }
    }

    content(
        { backupAction = BackupAction.EXPORT },
        { importLauncher.launch(arrayOf("application/octet-stream", "application/json", "application/zip", "application/x-zip-compressed")) },
        { scope.launch { runUndoRestore() } },
        snackbarHostState,
    )

    if (backupAction == BackupAction.EXPORT) {
        BackupExportDialog(
            onDismiss = { backupAction = null },
            onConfirm = { password ->
                exportState.begin(password)
                exportRequested = true
                encryptedExportRequested = password != null
                backupAction = null
                val date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())
                try {
                    if (password == null) plainExportLauncher.launch("xike-backup-$date.zip")
                    else encryptedExportLauncher.launch("xike-backup-$date.xike")
                } catch (error: Exception) {
                    exportState.take()
                    exportRequested = false
                    encryptedExportRequested = false
                    throw error
                }
            },
        )
    }

    if (backupAction == BackupAction.IMPORT) {
        BackupPasswordDialog(
            title = localizedText("解锁备份"),
            confirm = localizedText("查看备份摘要"),
            description = localizedText("输入创建备份时设置的密码。应用会先完整验证内容，不会修改设备上的日记。"),
            onDismiss = {
                backupAction = null
                pendingImportUri = null
            },
            onConfirm = { password ->
                val uri = pendingImportUri?.let(Uri::parse)
                pendingImportUri = null
                backupAction = null
                if (uri != null) {
                    scope.launch { inspectSelectedBackup(uri, password) }
                }
            },
        )
    }

    pendingRestore?.let { request ->
        RestoreConfirmationDialog(
            summary = request.summary,
            localEntryCount = localEntryCount,
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    onRestoreBackup(request.uri, request.password)
                }
            },
        )
    }

    val operationMessage = when (restoreState.operation) {
        RestoreOperation.RESTORE -> localizedText("正在创建安全快照并恢复…")
        RestoreOperation.UNDO -> localizedText("正在撤销上次恢复…")
        null -> if (isBackupExporting) localizedText("正在创建备份…") else busyMessage
    }
    operationMessage?.let { message -> ProcessingDialog(message) }
}
