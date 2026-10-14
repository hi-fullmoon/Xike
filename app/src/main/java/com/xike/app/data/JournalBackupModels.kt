package com.xike.app.data

import kotlinx.coroutines.flow.map

internal fun normalizeRestoredEntries(
    entries: List<JournalEntry>,
    availableImages: Set<String>,
    availableAudios: Set<String> = emptySet(),
    availableVideos: Set<String> = emptySet(),
): List<JournalEntry> =
    entries
        .distinctBy { it.id }
        .map { entry ->
            entry.copy(
                video =
                    entry.video?.takeIf {
                        it.fileName in availableVideos && it.coverFileName in availableVideos
                    },
                imageFileNames =
                    entry.imageFileNames
                        .distinct()
                        .filter { it in availableImages }
                        .take(MAX_IMAGES_PER_ENTRY),
                audio =
                    entry.audio?.takeIf {
                        it.fileName in availableAudios &&
                            it.durationMillis in 1..MAX_AUDIO_DURATION_MILLIS
                    },
            )
        }
        .sortedByDescending { it.createdAt }

data class BackupSummary(
    val entryCount: Int,
    val imageCount: Int,
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val videoBytes: Long = 0,
    val oldestCreatedAt: Long?,
    val newestCreatedAt: Long?,
)
