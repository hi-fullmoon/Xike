package com.xike.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.xike.app.data.JournalLabel
import com.xike.app.data.defaultJournalLabels

internal data class JournalLabelServices(
    val catalog: List<JournalLabel> = defaultJournalLabels,
    val save: suspend (JournalLabel) -> Result<Unit> = {
        Result.failure(IllegalStateException("Label services unavailable"))
    },
    val move: suspend (String, Int) -> Result<Unit> = { _, _ ->
        Result.failure(IllegalStateException("Label services unavailable"))
    },
)

internal val LocalJournalLabelServices = staticCompositionLocalOf { JournalLabelServices() }
