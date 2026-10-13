package com.xike.app

import com.xike.app.feature.lock.AppLockDialog
import com.xike.app.feature.lock.AppLockScreen
import com.xike.app.feature.lock.LockAuthentication
import com.xike.app.feature.lock.AppLockSessionState
import com.xike.app.feature.lock.AppLockTimeout
import com.xike.app.feature.lock.AppLockPreferences

import com.xike.app.ui.components.ProcessingDialog
import com.xike.app.ui.components.DataErrorDialog
import com.xike.app.ui.components.XikeNotice
import com.xike.app.ui.components.NoticeMessage
import com.xike.app.ui.components.LocalRetainedNotice

import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.XikeTheme



import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val lockSession: AppLockSessionState by viewModels()
    private val welcomeSession: WelcomeLaunchState by viewModels()
    private var openedJournalViewModel: JournalViewModel? = null
    private lateinit var lockPreferences: AppLockPreferences
    private lateinit var appearancePreferences: AppearancePreferences
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var habitPreferences: HabitPreferences
    private var appLockEnabled by mutableStateOf(false)
    private var appLockTimeout by mutableStateOf(AppLockTimeout.IMMEDIATELY)
    private var authenticationAvailable by mutableStateOf(false)
    private var reminderSettings by mutableStateOf(ReminderSettings())
    private var dailyPromptSettings by mutableStateOf(DailyPromptSettings())
    private var notificationPermissionGranted by mutableStateOf(false)
    private var quickRecordRequest by mutableStateOf(0)
    private var lockScreenTheme by mutableStateOf(AppTheme.OCEAN)
    private var lockScreenStyle by mutableStateOf(AppStyle.BREATHE)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hand the system launch background directly to the single welcome animation in Compose.
        splashScreen.setOnExitAnimationListener { it.remove() }
        AppLocale.initialize(this)
        lockPreferences = AppLockPreferences(this)
        appearancePreferences = AppearancePreferences(this)
        appearancePreferences.current().let { appearance ->
            lockScreenTheme = appearance.theme
            lockScreenStyle = appearance.style
        }
        habitPreferences = HabitPreferences(this)
        appLockEnabled = lockPreferences.enabled
        appLockTimeout = lockPreferences.timeout
        reminderSettings = habitPreferences.reminder
        dailyPromptSettings = habitPreferences.dailyPrompt
        notificationPermissionGranted = hasNotificationPermission()
        handleQuickRecordIntent(intent)
        ReminderScheduler.reconcile(this, reminderSettings)
        if (!lockSession.initialized) {
            lockSession.isAppLocked = appLockEnabled
            lockSession.journalSessionOpened = !appLockEnabled
            lockSession.initialized = true
        }
        refreshLockMonitor()
        refreshAuthenticationAvailability()
        createBiometricPrompt()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setLocalizedContent {
            PlaybackOutputGuard()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                notificationPermissionGranted = granted
                if (granted) {
                    persistReminderSettings(reminderSettings.copy(enabled = true))
                } else {
                    XikeNotice.makeText(this, localizedText("没有开启通知，记录功能仍可正常使用"), XikeNotice.LENGTH_LONG).show()
                }
            }
            // Prepare the existing store during the animation only when the lock already permits it.
            val preparedJournal = if (lockSession.journalSessionOpened) viewModel<JournalViewModel>() else null
            openedJournalViewModel = preparedJournal
            if (welcomeSession.visible) {
                XikeTheme(lockScreenTheme, lockScreenStyle) {
                    WelcomeScreen(onFinished = {
                        welcomeSession.finish()
                        if (appLockEnabled && lockSession.isAppLocked) {
                            requestAuthentication(LockAuthentication.UNLOCK, openSettingsWhenUnavailable = false)
                        }
                    })
                }
            } else if (!lockSession.journalSessionOpened) {
                XikeTheme(lockScreenTheme, lockScreenStyle) {
                    AppLockScreen(
                        authenticationAvailable = authenticationAvailable,
                        onUnlock = { requestAuthentication(LockAuthentication.UNLOCK) },
                        onOpenSecuritySettings = {
                            openDeviceSecuritySettings(LockAuthentication.UNLOCK)
                        },
                    )
                }
            } else {
                val journalViewModel = checkNotNull(preparedJournal)
                LaunchedEffect(journalViewModel.selectedTheme, journalViewModel.selectedStyle) {
                    lockScreenTheme = journalViewModel.selectedTheme
                    lockScreenStyle = journalViewModel.selectedStyle
                    appearancePreferences.saveTheme(journalViewModel.selectedTheme)
                    appearancePreferences.saveStyle(journalViewModel.selectedStyle)
                }
                val systemActivityCallbacks =
                    SystemActivityCallbacks(
                        onLaunch = { operationId, sameTask ->
                            val observable = sameTask && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2
                            lockSession.beginSystemActivity(
                                watchTaskVisibility = observable,
                                requireAuthenticationOnReturn = appLockEnabled && !observable,
                                operationId = operationId,
                            )
                        },
                        onResult = { operationId ->
                            refreshLockMonitor()
                            lockSession.finishSystemActivity(operationId = operationId)
                        },
                        onLaunchFailed = { operationId ->
                            refreshLockMonitor()
                            lockSession.finishSystemActivity(resultReceived = false, operationId = operationId)
                        },
                        isLocked = appLockEnabled && lockSession.isAppLocked,
                        resultsReady = journalViewModel.systemActivityResultsReady,
                        canHandleResult = {
                            journalViewModel.systemActivityResultsReady &&
                                (!appLockEnabled || (!lockSession.isAppLocked && isDeviceInteractiveAndUnlocked()))
                        },
                    )

                JournalTheme(journalViewModel, noticesBlocked = appLockEnabled && lockSession.isAppLocked) {
                    CompositionLocalProvider(
                        LocalSystemActivityCallbacks provides systemActivityCallbacks,
                        LocalJournalEditOperations provides journalViewModel.editOperations,
                        LocalAudioEditServices provides AudioEditServices(
                            import = journalViewModel::importEditAudio,
                            release = journalViewModel::releaseEditAudio,
                        ),
                        LocalVideoServices provides VideoServices(
                            import = journalViewModel::importVideo,
                            release = journalViewModel::releaseVideo,
                            source = journalViewModel::videoDataSource,
                            cover = journalViewModel::openVideoCover,
                            addDraft = journalViewModel::queueDraftVideo,
                            removeDraft = journalViewModel::removeDraftVideo,
                            cancelPendingDraft = journalViewModel::cancelPendingDraftVideo,
                            draftSaving = journalViewModel.isDraftVideoSaving,
                            draftProgress = journalViewModel.draftVideoProgress,
                            editDraft = journalViewModel::videoEditDraft,
                            saveEdit = journalViewModel::saveVideoEditDraft,
                            clearEdit = journalViewModel::clearVideoEditDraft,
                        ),
                    ) {
                        XikeApp(
                            entries = journalViewModel.entries,
                            entriesLoading = journalViewModel.isLoading,
                            draft = journalViewModel.draft,
                            selectedTheme = journalViewModel.selectedTheme,
                            selectedStyle = journalViewModel.selectedStyle,
                            appLockEnabled = appLockEnabled,
                            appLockTimeout = appLockTimeout,
                            authenticationAvailable = authenticationAvailable,
                            reminderSettings = reminderSettings,
                            dailyPromptSettings = dailyPromptSettings,
                            notificationPermissionGranted = notificationPermissionGranted,
                            quickRecordRequest = quickRecordRequest,
                            onThemeChange = journalViewModel::selectTheme,
                            onStyleChange = journalViewModel::selectStyle,
                            onAppLockChange = ::changeAppLock,
                            onAppLockTimeoutChange = ::changeAppLockTimeout,
                            onLockNow = ::lockNow,
                            onDraftMoodChange = journalViewModel::selectDraftMood,
                            onDraftNoteChange = journalViewModel::updateDraftNote,
                            onDraftTagToggle = journalViewModel::toggleDraftTag,
                            onDraftRecordedAtChange = journalViewModel::updateDraftRecordedAt,
                            onAttachCurrentOutdoor = journalViewModel::attachCurrentOutdoor,
                            isDraftOutdoorLoading = journalViewModel.isDraftOutdoorLoading,
                            onAttachOutdoorForCity = journalViewModel::attachOutdoorForCity,
                            onClearDraftOutdoor = journalViewModel::clearDraftOutdoor,
                            onDraftImagesAdded = journalViewModel::addDraftImages,
                            onDraftImageResultPending = journalViewModel::setDraftImageResultPending,
                            isDraftImageResultPending = journalViewModel.isDraftImageResultPending,
                            draftImageResultsReady = journalViewModel.dataError == null,
                            onDraftImageRemoved = journalViewModel::removeDraftImage,
                            pendingDraftAudio = journalViewModel.pendingDraftAudio,
                            isDraftAudioSaving = journalViewModel.isDraftAudioSaving,
                            draftAudioSaveError = journalViewModel.draftAudioSaveError,
                            onDraftAudioRecorded = journalViewModel::queueDraftAudio,
                            onDraftAudioRetry = journalViewModel::retryPendingDraftAudio,
                            onDraftAudioDiscard = journalViewModel::discardPendingDraftAudio,
                            onDraftAudioRemoved = journalViewModel::removeDraftAudio,
                            onDraftDiscard = journalViewModel::discardDraft,
                            hasResumedDraft = journalViewModel.hasResumedDraft,
                            onReminderEnabledChange = { enabled ->
                                changeReminderEnabled(enabled) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onReminderSettingsChange = ::persistReminderSettings,
                            onDailyPromptSettingsChange = ::persistDailyPromptSettings,
                            onSave = { _, _ -> journalViewModel.saveDraft() },
                            isDraftSaving = journalViewModel.isDraftSaving,
                            isBackupExporting = journalViewModel.isBackupExporting,
                            onUpdate = journalViewModel::update,
                            onDelete = journalViewModel::delete,
                            onUndoDelete = journalViewModel::undoDelete,
                            onFinalizeDelete = journalViewModel::finalizeDelete,
                            onSearch = journalViewModel::search,
                            onExportBackup = journalViewModel::startBackupExport,
                            onBackupRequiresPassword = journalViewModel::backupRequiresPassword,
                            onInspectBackup = journalViewModel::inspectBackup,
                            onRestoreBackup = journalViewModel::restoreBackup,
                            canUndoRestore = journalViewModel.canUndoRestore,
                            onUndoRestore = journalViewModel::undoRestore,
                            openImage = journalViewModel::openImage,
                            openAudio = journalViewModel::openAudio,
                        )

                        journalViewModel.dataError?.let { message ->
                            DataErrorDialog(message, journalViewModel::dismissDataError)
                        }

                        if (journalViewModel.isLoading) {
                            ProcessingDialog(localizedText("正在读取加密日记…"))
                        }

                        if (appLockEnabled && lockSession.isAppLocked) {
                            AppLockDialog(
                                authenticationAvailable = authenticationAvailable,
                                onUnlock = { requestAuthentication(LockAuthentication.UNLOCK) },
                                onOpenSecuritySettings = {
                                    openDeviceSecuritySettings(LockAuthentication.UNLOCK)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        // Activity results can be delivered from super.onStart(). Capture this first so a camera
        // return never becomes indistinguishable from a normal foreground launch.
        val returningFromSystemActivity = lockSession.systemActivityInProgress
        appLockEnabled = lockPreferences.enabled
        appLockTimeout = lockPreferences.timeout
        refreshLockMonitor()
        if (appLockEnabled) {
            if (lockSession.shouldLockOnForeground(SystemClock.elapsedRealtime(), appLockTimeout)) {
                lockSession.isAppLocked = true
            } else {
                lockSession.backgroundedAtMillis = null
            }
        } else {
            lockSession.isAppLocked = false
            lockSession.journalSessionOpened = true
            lockSession.backgroundedAtMillis = null
        }
        // Apply the gate before super delivers a pending file-picker result.
        super.onStart()
        if (!returningFromSystemActivity) openedJournalViewModel?.onAppForegrounded()
    }

    override fun onResume() {
        super.onResume()
        refreshLockMonitor()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        refreshAuthenticationAvailability()
        notificationPermissionGranted = hasNotificationPermission()

        val pendingAction = lockSession.pendingAuthenticationAfterEnrollment
        lockSession.pendingAuthenticationAfterEnrollment = null
        if (pendingAction != null && authenticationAvailable) {
            window.decorView.post { requestAuthentication(pendingAction) }
        } else if (!welcomeSession.visible && appLockEnabled && lockSession.isAppLocked) {
            window.decorView.post {
                requestAuthentication(LockAuthentication.UNLOCK, openSettingsWhenUnavailable = false)
            }
        }
    }

    override fun onPause() {
        journalPlayback.pauseActive()
        if (appLockEnabled) {
            // Keep journal content out of the system's recent-apps snapshot without blocking
            // screenshots while the user is actively using the app.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        super.onPause()
    }

    override fun onStop() {
        openedJournalViewModel?.flushDraft()
        if (!isChangingConfigurations && !lockSession.systemActivityInProgress) {
            openedJournalViewModel?.onAppBackgrounded()
        }
        if (appLockEnabled && !isChangingConfigurations) {
            lockSession.recordBackgrounded(SystemClock.elapsedRealtime())
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickRecordIntent(intent)
    }

    private fun createBiometricPrompt() {
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val current = lockSession.isCurrentAuthentication() && isDeviceInteractiveAndUnlocked()
                    lockSession.authenticationInProgress = false
                    if (!current) {
                        lockSession.authenticationAction = null
                        return
                    }
                    when (lockSession.authenticationAction) {
                        LockAuthentication.UNLOCK -> {
                            lockSession.isAppLocked = false
                            lockSession.journalSessionOpened = true
                            lockSession.backgroundedAtMillis = null
                        }

                        LockAuthentication.ENABLE -> updateAppLockEnabled(true)
                        LockAuthentication.DISABLE -> updateAppLockEnabled(false)
                        null -> Unit
                    }
                    lockSession.authenticationAction = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    lockSession.authenticationInProgress = false
                    lockSession.authenticationAction = null
                    if (errorCode !in setOf(
                            BiometricPrompt.ERROR_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                        )
                    ) {
                        XikeNotice.makeText(this@MainActivity, errString, XikeNotice.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    private fun requestAuthentication(
        action: LockAuthentication,
        openSettingsWhenUnavailable: Boolean = true,
    ) {
        if (lockSession.authenticationInProgress || !isDeviceInteractiveAndUnlocked()) return
        val availability = BiometricManager.from(this).canAuthenticate(allowedAuthenticators)
        authenticationAvailable = availability == BiometricManager.BIOMETRIC_SUCCESS
        if (!authenticationAvailable) {
            if (openSettingsWhenUnavailable) {
                openDeviceSecuritySettings(action)
            }
            return
        }

        lockSession.beginAuthentication(action)
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(
                    when (action) {
                        LockAuthentication.UNLOCK -> localizedText("解锁息刻")
                        LockAuthentication.ENABLE -> localizedText("开启应用锁")
                        LockAuthentication.DISABLE -> localizedText("关闭应用锁")
                    },
                )
                .setSubtitle(localizedText("使用面容、指纹或设备密码验证身份"))
                .setAllowedAuthenticators(allowedAuthenticators)
                .build(),
        )
    }

    private fun changeAppLock(enabled: Boolean) {
        if (enabled == appLockEnabled) return
        requestAuthentication(
            if (enabled) LockAuthentication.ENABLE else LockAuthentication.DISABLE,
        )
    }

    private fun updateAppLockEnabled(enabled: Boolean) {
        runCatching { lockPreferences.enabled = enabled }
            .onSuccess {
                appLockEnabled = enabled
                lockSession.isAppLocked = false
                lockSession.journalSessionOpened = true
                lockSession.backgroundedAtMillis = null
                refreshLockMonitor()
                XikeNotice.makeText(
                    this,
                    if (enabled) localizedText("应用锁已开启") else localizedText("应用锁已关闭"),
                    XikeNotice.LENGTH_SHORT,
                ).show()
            }
            .onFailure { error ->
                XikeNotice.makeText(this, error.message ?: localizedText("应用锁设置保存失败"), XikeNotice.LENGTH_SHORT).show()
            }
    }

    private fun refreshLockMonitor() {
        lockSession.observeDeviceLock(applicationContext, taskId, appLockEnabled)
        if (appLockEnabled && !isDeviceInteractiveAndUnlocked()) {
            lockSession.recordDeviceLocked()
        }
    }

    private fun isDeviceInteractiveAndUnlocked(): Boolean =
        getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isDeviceLocked

    private fun changeAppLockTimeout(timeout: AppLockTimeout) {
        runCatching { lockPreferences.timeout = timeout }
            .onSuccess { appLockTimeout = timeout }
            .onFailure { error ->
                XikeNotice.makeText(this, error.message ?: localizedText("自动锁定设置保存失败"), XikeNotice.LENGTH_SHORT).show()
            }
    }

    private fun lockNow() {
        if (!appLockEnabled) return
        journalPlayback.pauseActive()
        lockSession.recordDeviceLocked()
        window.decorView.post { requestAuthentication(LockAuthentication.UNLOCK) }
    }

    private fun changeReminderEnabled(enabled: Boolean, requestPermission: () -> Unit) {
        if (!enabled) {
            persistReminderSettings(reminderSettings.copy(enabled = false))
            return
        }
        notificationPermissionGranted = hasNotificationPermission()
        if (notificationPermissionGranted) {
            persistReminderSettings(reminderSettings.copy(enabled = true))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission()
        } else {
            persistReminderSettings(reminderSettings.copy(enabled = true))
        }
    }

    private fun persistReminderSettings(settings: ReminderSettings) {
        runCatching { habitPreferences.reminder = settings }
            .onSuccess {
                reminderSettings = settings
                ReminderScheduler.reconcile(this, settings)
            }
            .onFailure { error ->
                XikeNotice.makeText(this, error.message ?: localizedText("提醒设置保存失败"), XikeNotice.LENGTH_LONG).show()
            }
    }

    private fun persistDailyPromptSettings(settings: DailyPromptSettings) {
        runCatching { habitPreferences.dailyPrompt = settings }
            .onSuccess { dailyPromptSettings = settings }
            .onFailure { error ->
                XikeNotice.makeText(this, error.message ?: localizedText("每日一问设置保存失败"), XikeNotice.LENGTH_LONG).show()
            }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun handleQuickRecordIntent(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_RECORD) quickRecordRequest += 1
    }

    private fun refreshAuthenticationAvailability() {
        authenticationAvailable = BiometricManager.from(this)
            .canAuthenticate(allowedAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun openDeviceSecuritySettings(action: LockAuthentication) {
        lockSession.pendingAuthenticationAfterEnrollment = action
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                allowedAuthenticators,
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                lockSession.pendingAuthenticationAfterEnrollment = null
                XikeNotice.makeText(this, localizedText("无法打开系统安全设置"), XikeNotice.LENGTH_SHORT).show()
            }
    }

    private val allowedAuthenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
}

@Composable
private fun JournalTheme(journal: JournalViewModel, noticesBlocked: Boolean, content: @Composable () -> Unit) {
    val feedback = journal.operationFeedback.firstOrNull()
    val retainedNotice = remember(feedback) {
        feedback?.let {
            NoticeMessage(it.message, if (it.isError) XikeNotice.LENGTH_LONG else XikeNotice.LENGTH_SHORT) {
                journal.acknowledgeFeedback(it)
            }
        }
    }
    CompositionLocalProvider(LocalRetainedNotice provides retainedNotice.takeUnless { noticesBlocked }) {
        XikeTheme(journal.selectedTheme, journal.selectedStyle, content = content)
    }
}
