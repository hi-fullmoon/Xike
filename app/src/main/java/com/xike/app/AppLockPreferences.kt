package com.xike.app

import android.content.Context

enum class AppLockTimeout(
    val durationMillis: Long,
    val label: String,
) {
    IMMEDIATELY(0L, "立即"),
    ONE_MINUTE(60_000L, "1 分钟后"),
    FIVE_MINUTES(5 * 60_000L, "5 分钟后"),
    THIRTY_MINUTES(30 * 60_000L, "30 分钟后");

    companion object {
        fun fromStorage(value: String?): AppLockTimeout = entries
            .firstOrNull { it.name == value }
            ?: IMMEDIATELY
    }
}

internal fun shouldLockApp(
    backgroundedAtMillis: Long?,
    nowMillis: Long,
    timeout: AppLockTimeout,
): Boolean = backgroundedAtMillis == null ||
    nowMillis - backgroundedAtMillis >= timeout.durationMillis

/**
 * App-lock preferences intentionally live outside the journal database: the lock gate must be
 * readable before encrypted journal data is opened, and these device-specific choices must not be
 * included when a journal backup is restored on another device.
 */
internal class AppLockPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var enabled: Boolean
        get() = preferences.getBoolean(ENABLED, false)
        set(value) {
            check(preferences.edit().putBoolean(ENABLED, value).commit()) {
                "无法保存应用锁设置。"
            }
        }

    var timeout: AppLockTimeout
        get() = AppLockTimeout.fromStorage(preferences.getString(TIMEOUT, null))
        set(value) {
            check(preferences.edit().putString(TIMEOUT, value.name).commit()) {
                "无法保存自动锁定时间。"
            }
        }

    private companion object {
        const val PREFERENCES_NAME = "xike-app-lock"
        const val ENABLED = "enabled"
        const val TIMEOUT = "timeout"
    }
}
