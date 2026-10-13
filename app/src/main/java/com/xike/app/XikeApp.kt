package com.xike.app

import com.xike.app.data.model.JournalDraft

import com.xike.app.data.Mood
import com.xike.app.data.JournalEntry
import com.xike.app.data.BackupSummary

import com.xike.app.feature.lock.AppLockTimeout

import com.xike.app.feature.settings.ProfileSettingsScreen
import com.xike.app.feature.settings.PendingExport
import com.xike.app.feature.settings.BackupExportState
import com.xike.app.feature.settings.RestoreConfirmationDialog
import com.xike.app.feature.settings.BackupExportDialog
import com.xike.app.feature.settings.BackupPasswordDialog

import com.xike.app.feature.insights.JournalInsightsScreen

import com.xike.app.feature.archive.JournalArchiveScreen

import com.xike.app.feature.moment.MomentScreen

import com.xike.app.ui.components.AppScreen
import com.xike.app.ui.components.XikeNavigationBar
import com.xike.app.ui.components.ProcessingDialog
import com.xike.app.ui.components.XikeNotice

import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.AppStyle





import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.InputStream
import java.io.File
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
internal fun XikeApp(
    entries: List<JournalEntry>,
    entriesLoading: Boolean,
    draft: JournalDraft,
    selectedTheme: AppTheme,
    selectedStyle: AppStyle,
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    authenticationAvailable: Boolean,
    reminderSettings: ReminderSettings,
    dailyPromptSettings: DailyPromptSettings,
    notificationPermissionGranted: Boolean,
    quickRecordRequest: Int,
    onThemeChange: (AppTheme) -> Unit,
    onStyleChange: (AppStyle) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onAppLockTimeoutChange: (AppLockTimeout) -> Unit,
    onLockNow: () -> Unit,
    onDraftMoodChange: (Mood?) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onDraftTagToggle: (String) -> Unit,
    onDraftRecordedAtChange: (Long?) -> Unit,
    onAttachCurrentOutdoor: suspend () -> Result<Unit>,
    isDraftOutdoorLoading: Boolean,
    onAttachOutdoorForCity: suspend (String) -> Result<Unit>,
    onClearDraftOutdoor: () -> Unit,
    onDraftImagesAdded: (List<Uri>) -> Boolean,
    onDraftImageResultPending: (String, Boolean) -> Unit,
    isDraftImageResultPending: Boolean,
    draftImageResultsReady: Boolean,
    onDraftImageRemoved: (String) -> Unit,
    pendingDraftAudio: PendingDraftAudio?,
    isDraftAudioSaving: Boolean,
    draftAudioSaveError: String?,
    onDraftAudioRecorded: (File, Long) -> Unit,
    onDraftAudioRetry: () -> Unit,
    onDraftAudioDiscard: () -> Unit,
    onDraftAudioRemoved: () -> Unit,
    onDraftDiscard: () -> Unit,
    hasResumedDraft: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderSettingsChange: (ReminderSettings) -> Unit,
    onDailyPromptSettingsChange: (DailyPromptSettings) -> Unit,
    onSave: suspend (JournalEntry, List<Uri>) -> Result<Unit>,
    isDraftSaving: Boolean,
    isBackupExporting: Boolean,
    onUpdate: suspend (JournalEntry, List<String>, List<Uri>) -> Result<JournalEntry>,
    onDelete: suspend (JournalEntry) -> Result<Unit>,
    onUndoDelete: suspend (String) -> Result<Unit>,
    onFinalizeDelete: suspend (String) -> Result<Unit>,
    onSearch: suspend (JournalSearchQuery, Int, Int) -> Result<JournalSearchPage>,
    onExportBackup: (Uri, String?) -> Unit,
    onBackupRequiresPassword: suspend (Uri) -> Result<Boolean>,
    onInspectBackup: suspend (Uri, String?) -> Result<BackupSummary>,
    onRestoreBackup: suspend (Uri, String?) -> Result<Int>,
    canUndoRestore: Boolean,
    onUndoRestore: suspend () -> Result<Int>,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var backupAction by remember { mutableStateOf<BackupAction?>(null) }
    val exportState: BackupExportState = viewModel()
    var exportRequested by rememberSaveable { mutableStateOf(false) }
    var encryptedExportRequested by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var isVoiceCaptureActive by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(quickRecordRequest) {
        if (quickRecordRequest > 0) screen = AppScreen.HOME
    }

    suspend fun runUndoRestore() {
        snackbarHostState.currentSnackbarData?.dismiss()
        busyMessage = localizedText("正在撤销上次恢复…")
        onUndoRestore()
            .onSuccess { count ->
                XikeNotice.makeText(context, tr("已撤销恢复，找回 $count 条日记", "Restore undone. Recovered $count entries"), XikeNotice.LENGTH_SHORT).show()
            }
            .onFailure {
                XikeNotice.makeText(context, it.message ?: localizedText("撤销恢复失败"), XikeNotice.LENGTH_LONG).show()
            }
        busyMessage = null
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            XikeNavigationBar(
                selected = screen,
                onSelected = { selected ->
                    if (isVoiceCaptureActive && selected != AppScreen.HOME) {
                        XikeNotice.makeText(context, localizedText("请先完成或取消录音"), XikeNotice.LENGTH_SHORT).show()
                    } else {
                        screen = selected
                    }
                },
            )
        },
    ) { innerPadding ->
        screenStateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                AppScreen.HOME -> MomentScreen(
                    padding = innerPadding,
                    entries = entries,
                    draft = draft,
                    dailyPromptSettings = dailyPromptSettings,
                    onDraftMoodChange = onDraftMoodChange,
                    onDraftNoteChange = onDraftNoteChange,
                    onDraftTagToggle = onDraftTagToggle,
                    onDraftRecordedAtChange = onDraftRecordedAtChange,
                    onAttachCurrentOutdoor = onAttachCurrentOutdoor,
                    isDraftOutdoorLoading = isDraftOutdoorLoading,
                    onAttachOutdoorForCity = onAttachOutdoorForCity,
                    onClearDraftOutdoor = onClearDraftOutdoor,
                    onDraftImagesAdded = onDraftImagesAdded,
                    onDraftImageResultPending = onDraftImageResultPending,
                    isDraftImageResultPending = isDraftImageResultPending,
                    draftImageResultsReady = draftImageResultsReady,
                    onDraftImageRemoved = onDraftImageRemoved,
                    pendingDraftAudio = pendingDraftAudio,
                    isDraftAudioSaving = isDraftAudioSaving,
                    draftAudioSaveError = draftAudioSaveError,
                    onDraftAudioRecorded = onDraftAudioRecorded,
                    onDraftAudioRetry = onDraftAudioRetry,
                    onDraftAudioDiscard = onDraftAudioDiscard,
                    onDraftAudioRemoved = onDraftAudioRemoved,
                    openAudio = openAudio,
                    onVoiceCaptureStateChange = { isVoiceCaptureActive = it },
                    onDraftDiscard = onDraftDiscard,
                    hasResumedDraft = hasResumedDraft,
                    onSave = onSave,
                    isDraftSaving = isDraftSaving,
                    saveFeedbackHandledExternally = true,
                )
                AppScreen.INSIGHTS -> JournalInsightsScreen(innerPadding, entries, openImage, openAudio)
                AppScreen.ARCHIVE -> JournalArchiveScreen(
                    padding = innerPadding,
                    entries = entries,
                    entriesLoading = entriesLoading,
                    onSearch = onSearch,
                    onUpdate = onUpdate,
                    onDelete = onDelete,
                    onUndoDelete = onUndoDelete,
                    onFinalizeDelete = onFinalizeDelete,
                    openImage = openImage,
                    openAudio = openAudio,
                )
                AppScreen.SETTINGS -> ProfileSettingsScreen(
                    padding = innerPadding,
                    selectedTheme = selectedTheme,
                    selectedStyle = selectedStyle,
                    appLockEnabled = appLockEnabled,
                    appLockTimeout = appLockTimeout,
                    authenticationAvailable = authenticationAvailable,
                    reminderSettings = reminderSettings,
                    dailyPromptSettings = dailyPromptSettings,
                    notificationPermissionGranted = notificationPermissionGranted,
                    onThemeChange = onThemeChange,
                    onStyleChange = onStyleChange,
                    onAppLockChange = onAppLockChange,
                    onAppLockTimeoutChange = onAppLockTimeoutChange,
                    onLockNow = onLockNow,
                    onReminderEnabledChange = onReminderEnabledChange,
                    onReminderSettingsChange = onReminderSettingsChange,
                    onDailyPromptSettingsChange = onDailyPromptSettingsChange,
                    onExport = { backupAction = BackupAction.EXPORT },
                    onImport = {
                        importLauncher.launch(arrayOf("application/octet-stream", "application/json", "application/zip", "application/x-zip-compressed"))
                    },
                    canUndoRestore = canUndoRestore,
                    onUndoRestore = { scope.launch { runUndoRestore() } },
                )
            }
        }
    }

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
            localEntryCount = entries.size,
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    busyMessage = localizedText("正在创建安全快照并恢复…")
                    onRestoreBackup(request.uri, request.password)
                        .onSuccess { count ->
                            busyMessage = null
                            val result = snackbarHostState.showSnackbar(
                                message = tr("已恢复 $count 条日记", "Restored $count entries"),
                                actionLabel = localizedText("撤销"),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) runUndoRestore()
                        }
                        .onFailure {
                            XikeNotice.makeText(context, it.message ?: localizedText("恢复失败"), XikeNotice.LENGTH_LONG).show()
                            busyMessage = null
                        }
                }
            },
        )
    }

    (if (isBackupExporting) localizedText("正在创建备份…") else busyMessage)?.let { message -> ProcessingDialog(message) }
}
