package com.xike.app

import com.xike.app.data.model.JournalDraft
import com.xike.app.data.Mood
import com.xike.app.data.JournalEntry
import com.xike.app.data.BackupSummary
import com.xike.app.feature.lock.AppLockTimeout
import com.xike.app.feature.settings.ProfileSettingsScreen
import com.xike.app.feature.insights.JournalInsightsScreen
import com.xike.app.feature.archive.JournalArchiveScreen
import com.xike.app.feature.moment.MomentScreen
import com.xike.app.ui.components.AppScreen
import com.xike.app.ui.components.XikeNavigationBar
import com.xike.app.ui.components.XikeNotice
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.AppStyle
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.InputStream
import java.io.File

@Composable
internal fun XikeApp(
    // Journal pages and media readers.
    readRepository: JournalReadRepository,
    libraryState: JournalLibraryState,
    entriesLoading: Boolean,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
    // Moment draft state and actions.
    draft: JournalDraft,
    hasResumedDraft: Boolean,
    isDraftSaving: Boolean,
    onDraftMoodChange: (Mood?) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onDraftTagToggle: (String) -> Unit,
    onDraftRecordedAtChange: (Long?) -> Unit,
    onDraftDiscard: () -> Unit,
    onSave: suspend (JournalEntry, List<Uri>) -> Result<Unit>,
    // Outdoor and system media results.
    isDraftOutdoorLoading: Boolean,
    onAttachCurrentOutdoor: suspend () -> Result<Unit>,
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
    // Archive operations.
    onUpdate: suspend (JournalEntry, List<String>, List<Uri>) -> Result<JournalEntry>,
    onDelete: suspend (JournalEntry) -> Result<Unit>,
    onUndoDelete: suspend (String) -> Result<Unit>,
    onFinalizeDelete: suspend (String) -> Result<Unit>,
    onSearch: suspend (JournalSearchQuery, Int, Int) -> Result<JournalSearchPage>,
    // Appearance, security and reminders.
    selectedTheme: AppTheme,
    selectedStyle: AppStyle,
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    authenticationAvailable: Boolean,
    reminderSettings: ReminderSettings,
    dailyPromptSettings: DailyPromptSettings,
    notificationPermissionGranted: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onStyleChange: (AppStyle) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onAppLockTimeoutChange: (AppLockTimeout) -> Unit,
    onLockNow: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderSettingsChange: (ReminderSettings) -> Unit,
    onDailyPromptSettingsChange: (DailyPromptSettings) -> Unit,
    // Backup operations.
    isBackupExporting: Boolean,
    onExportBackup: (Uri, String?) -> Unit,
    onBackupRequiresPassword: suspend (Uri) -> Result<Boolean>,
    onInspectBackup: suspend (Uri, String?) -> Result<BackupSummary>,
    onRestoreBackup: suspend (Uri, String?) -> Result<Int>,
    canUndoRestore: Boolean,
    onUndoRestore: suspend () -> Result<Int>,
    // Navigation requests.
    quickRecordRequest: Int,
    onReadError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var isVoiceCaptureActive by remember { mutableStateOf(false) }
    val screenStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(quickRecordRequest) {
        if (quickRecordRequest > 0) screen = AppScreen.HOME
    }

    XikeBackupFlow(
        localEntryCount = libraryState.totalCount,
        isBackupExporting = isBackupExporting,
        onExportBackup = onExportBackup,
        onBackupRequiresPassword = onBackupRequiresPassword,
        onInspectBackup = onInspectBackup,
        onRestoreBackup = onRestoreBackup,
        onUndoRestore = onUndoRestore,
    ) { onExport, onImport, onUndoRestore, snackbarHostState ->
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
                        entries = emptyList(),
                        recordedTodayCount = libraryState.todayCount,
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
                    AppScreen.INSIGHTS -> JournalInsightsScreen(innerPadding, emptyList(), openImage, openAudio, readRepository, libraryState.revision, onReadError)
                    AppScreen.ARCHIVE -> JournalArchiveScreen(
                        padding = innerPadding,
                        entries = emptyList(),
                        entriesLoading = entriesLoading,
                        readRepository = readRepository,
                        libraryState = libraryState,
                        onReadError = onReadError,
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
                        onExport = onExport,
                        onImport = onImport,
                        canUndoRestore = canUndoRestore,
                        onUndoRestore = onUndoRestore,
                    )
                }
            }
        }

    }
}
