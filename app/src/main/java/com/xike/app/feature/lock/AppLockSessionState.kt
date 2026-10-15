package com.xike.app.feature.lock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.xike.app.SystemActivityLockMonitor

internal enum class LockAuthentication {
    UNLOCK,
    ENABLE,
    DISABLE,
}

/** Public because ViewModelProvider creates this type reflectively from another package. */
class AppLockSessionState : ViewModel() {
    var initialized = false
    var isAppLocked by mutableStateOf(true)
    var journalSessionOpened by mutableStateOf(false)
    var backgroundedAtMillis: Long? = null
    var authenticationInProgress = false
    internal var systemActivityInProgress = false
    internal var authenticationAction: LockAuthentication? = null
    internal var pendingAuthenticationAfterEnrollment: LockAuthentication? = null
    private var systemActivityMonitor: SystemActivityLockMonitor? = null
    private var deviceLockRevision = 0L
    private var authenticationDeviceLockRevision = 0L
    private val activeSystemActivities = mutableMapOf<String, Int>()
    private val exemptSystemActivities = mutableMapOf<String, Int>()
    private val systemActivitiesRequiringAuthentication = mutableSetOf<String>()
    private val unobservableOperationStarts = mutableMapOf<String, Long>()

    internal fun beginAuthentication(action: LockAuthentication) {
        authenticationAction = action
        authenticationDeviceLockRevision = deviceLockRevision
        authenticationInProgress = true
    }

    internal fun isCurrentAuthentication(): Boolean =
        authenticationInProgress && authenticationDeviceLockRevision == deviceLockRevision

    internal fun shouldLockOnForeground(nowMillis: Long, timeout: AppLockTimeout): Boolean =
        isAppLocked ||
            systemActivitiesRequiringAuthentication.isNotEmpty() ||
            unobservableOperationStarts.values.any { shouldLockApp(it, nowMillis, timeout) } ||
            (backgroundedAtMillis != null &&
                shouldLockApp(backgroundedAtMillis, nowMillis, timeout))

    internal fun beginSystemActivity(
        watchTaskVisibility: Boolean = true,
        requireAuthenticationOnReturn: Boolean = false,
        operationId: String = "default",
        startedAtMillis: Long = 0L,
    ) {
        systemActivityInProgress = true
        activeSystemActivities[operationId] = (activeSystemActivities[operationId] ?: 0) + 1
        exemptSystemActivities[operationId] = (exemptSystemActivities[operationId] ?: 0) + 1
        if (requireAuthenticationOnReturn) systemActivitiesRequiringAuthentication.add(operationId)
        if (!watchTaskVisibility) {
            unobservableOperationStarts.putIfAbsent(operationId, startedAtMillis)
        }
        backgroundedAtMillis = null
        systemActivityMonitor?.startWatching(watchTaskVisibility)
    }

    internal fun finishSystemActivity(
        resultReceived: Boolean = true,
        operationId: String = "default",
        nowMillis: Long? = null,
        timeout: AppLockTimeout? = null,
    ) {
        val outstanding = activeSystemActivities[operationId] ?: return
        val expiredOnReturn =
            resultReceived &&
                nowMillis != null &&
                timeout != null &&
                shouldLockOnForeground(nowMillis, timeout)
        exemptSystemActivities[operationId]?.let { count ->
            if (count == 1) exemptSystemActivities.remove(operationId)
            else exemptSystemActivities[operationId] = count - 1
        }
        val requiresAuthentication = operationId in systemActivitiesRequiringAuthentication
        if (expiredOnReturn) isAppLocked = true
        if (resultReceived && requiresAuthentication) isAppLocked = true
        if (outstanding == 1) {
            activeSystemActivities.remove(operationId)
            systemActivitiesRequiringAuthentication.remove(operationId)
            unobservableOperationStarts.remove(operationId)
        } else activeSystemActivities[operationId] = outstanding - 1
        systemActivityInProgress = exemptSystemActivities.isNotEmpty()
        if (!systemActivityInProgress) {
            systemActivityMonitor?.stopWatching()
        }
    }

    internal fun observeDeviceLock(context: Context, taskId: Int, enabled: Boolean) {
        if (!enabled) {
            systemActivityMonitor?.stop()
            return
        }
        val monitor =
            systemActivityMonitor
                ?: SystemActivityLockMonitor(
                        context,
                        ::interruptSystemActivity,
                        ::recordDeviceLocked,
                    )
                    .also { systemActivityMonitor = it }
        monitor.observe(taskId)
    }

    internal fun unlockAfterAuthentication() {
        isAppLocked = false
        journalSessionOpened = true
        backgroundedAtMillis = null
        // Authentication happens in our foreground. Older pending results remain queued,
        // but no longer exempt subsequent backgrounding or require the same authentication twice.
        unobservableOperationStarts.clear()
        exemptSystemActivities.clear()
        systemActivitiesRequiringAuthentication.clear()
        systemActivityInProgress = false
        systemActivityMonitor?.stopWatching()
    }

    internal fun recordDeviceLocked() {
        deviceLockRevision += 1
        systemActivityMonitor?.stopWatching()
        systemActivityInProgress = false
        isAppLocked = true
    }

    internal fun interruptSystemActivity(nowMillis: Long) {
        if (!systemActivityInProgress) return
        systemActivityInProgress = false
        backgroundedAtMillis = backgroundedAtMillis ?: nowMillis
        // A trusted interruption gives us the actual background time. Old pending results
        // must not reactivate an exemption when one of several operations returns.
        unobservableOperationStarts.clear()
        exemptSystemActivities.clear()
    }

    internal fun recordBackgrounded(nowMillis: Long) {
        if (!systemActivityInProgress && backgroundedAtMillis == null)
            backgroundedAtMillis = nowMillis
    }

    override fun onCleared() {
        systemActivityMonitor?.stop()
        super.onCleared()
    }
}
