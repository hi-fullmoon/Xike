package com.xike.app.media

import android.content.Context
import java.io.File

internal fun pruneVoiceTemporaryFiles(context: Context) {
    context.cacheDir.listFiles()
        // Recording candidates may belong to a pending editor import after process death.
        ?.filter { it.name.startsWith("xike-playback-") }
        ?.forEach(File::delete)
}
