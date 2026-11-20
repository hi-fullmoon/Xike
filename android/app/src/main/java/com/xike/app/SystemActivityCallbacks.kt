package com.xike.app

import androidx.compose.runtime.staticCompositionLocalOf

internal data class SystemActivityCallbacks(
    val onLaunch: (String, Boolean) -> Unit = { _, _ -> },
    val onResult: (String) -> Unit = {},
    val onLaunchFailed: (String) -> Unit = {},
    val isLocked: Boolean = false,
    val resultsReady: Boolean = true,
    val canHandleResult: () -> Boolean = { true },
)

internal val LocalSystemActivityCallbacks = staticCompositionLocalOf { SystemActivityCallbacks() }
