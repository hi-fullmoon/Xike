package com.xike.app

import com.xike.app.data.JournalAudio
import java.io.File

internal data class AudioEditServices(
    val import: suspend (File, Long) -> Result<JournalAudio>,
    val release: (JournalAudio) -> Unit,
)

internal val LocalAudioEditServices =
    androidx.compose.runtime.compositionLocalOf<AudioEditServices> {
        error("Audio edit service unavailable")
    }
