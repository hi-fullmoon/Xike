package com.xike.app.feature.lock

import com.xike.app.SystemActivityLockMonitor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

internal enum class LockAuthentication { UNLOCK, ENABLE, DISABLE }

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
    private val systemActivitiesRequiringAuthentication = mutableSetOf<String>()

    internal fun beginAuthentication(action: LockAuthentication) {
        authenticationAction = action
        authenticationDeviceLockRevision = deviceLockRevision
        authenticationInProgress = true
    }

    internal fun isCurrentAuthentication(): Boolean =
        authenticationInProgress && authenticationDeviceLockRevision == deviceLockRevision

    internal fun shouldLockOnForeground(nowMillis: Long, timeout: AppLockTimeout): Boolean =
        isAppLocked || systemActivitiesRequiringAuthentication.isNotEmpty() ||
            (backgroundedAtMillis != null && shouldLockApp(backgroundedAtMillis, nowMillis, timeout))

    internal fun beginSystemActivity(
        watchTaskVisibility: Boolean = true,
        requireAuthenticationOnReturn: Boolean = false,
        operationId: String = "default",
    ) {
        systemActivityInProgress = true
        activeSystemActivities[operationId] = (activeSystemActivities[operationId] ?: 0) + 1
        if (requireAuthenticationOnReturn) systemActivitiesRequiringAuthentication.add(operationId)
        backgroundedAtMillis = null
        systemActivityMonitor?.startWatching(watchTaskVisibility)
    }

    internal fun finishSystemActivity(resultReceived: Boolean = true, operationId: String = "default") {
        val outstanding = activeSystemActivities[operationId] ?: return
        val requiresAuthentication = operationId in systemActivitiesRequiringAuthentication
        if (resultReceived && requiresAuthentication) isAppLocked = true
        if (outstanding == 1) {
            activeSystemActivities.remove(operationId)
            systemActivitiesRequiringAuthentication.remove(operationId)
        } else activeSystemActivities[operationId] = outstanding - 1
        if (activeSystemActivities.isEmpty()) {
            systemActivityMonitor?.stopWatching()
            systemActivityInProgress = false
        }
    }

    internal fun observeDeviceLock(context: Context, taskId: Int, enabled: Boolean) {
        if (!enabled) {
            systemActivityMonitor?.stop()
            return
        }
        val monitor = systemActivityMonitor ?: SystemActivityLockMonitor(
            context, ::interruptSystemActivity, ::recordDeviceLocked,
        ).also { systemActivityMonitor = it }
        monitor.observe(taskId)
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
    }

    internal fun recordBackgrounded(nowMillis: Long) {
        if (!systemActivityInProgress && backgroundedAtMillis == null) backgroundedAtMillis = nowMillis
    }

    override fun onCleared() {
        systemActivityMonitor?.stop()
        super.onCleared()
    }
}
