package com.xike.app.data

import com.xike.app.tr
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import org.json.JSONArray

enum class BackupRestoreMode {
    MERGE,
    REPLACE,
}

internal fun attachmentDigest(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    return Base64.getEncoder().encodeToString(digest.digest())
}

/** Ordered JSON arrays make the key independent of attachment names and object key ordering. */
internal fun restoreContentKey(
    entry: JournalEntry,
    imageDigest: (String) -> String,
    audioDigest: (String) -> String,
    videoDigest: (String) -> String,
): String {
    val values =
        JSONArray(
                listOf(
                    entry.createdAt,
                    // Keep preview backup conflict identities stable after the identifier upgrade.
                    if (entry.mood.isOverallScale)
                        Mood.legacyChoices.first { it.score == entry.mood.score }.name
                    else entry.mood.name,
                    JSONArray(entry.tags),
                    entry.note,
                    JSONArray(entry.imageFileNames.map(imageDigest)),
                    entry.audio?.let {
                        JSONArray(listOf(audioDigest(it.fileName), it.durationMillis, it.mimeType))
                    },
                    entry.video?.let {
                        JSONArray(
                            listOf(
                                videoDigest(it.fileName),
                                videoDigest(it.coverFileName),
                                it.durationMillis,
                                it.sizeBytes,
                                it.mimeType,
                            )
                        )
                    },
                    entry.outdoor?.let {
                        JSONArray(
                            listOf(
                                it.placeName,
                                it.temperatureCelsius,
                                it.weatherCode,
                                it.capturedAt,
                                it.source,
                                it.fullPlaceName,
                            )
                        )
                    },
                )
            )
            .apply {
                if (entry.feelings.isNotEmpty() || entry.moodScaleVersion != 0) {
                    put(JSONArray(entry.feelings))
                    put(entry.moodScaleVersion)
                }
            }
    return values.toString().byteInputStream().use(::attachmentDigest)
}

/** Keep identity: independently created entries with different IDs are not collapsed. */
internal fun planMergedEntries(
    local: List<JournalEntry>,
    incoming: List<JournalEntry>,
    localKey: (JournalEntry) -> String,
    incomingKey: (JournalEntry) -> String,
): List<JournalEntry> {
    val known = local.associateBy { it.id }.toMutableMap()
    val keys = mutableMapOf<String, String>()
    val versionIds =
        known.keys
            .filter { it.startsWith("xike-restore-version:") }
            .groupBy { it.substringBeforeLast(':') }
            .mapValues { (_, ids) -> ids.toMutableSet() }
            .toMutableMap()
    val additions = mutableListOf<JournalEntry>()
    // Earlier builds used opaque UUID v3 IDs. Prefilter by actual metadata so only plausible
    // legacy copies require media comparison and bounded reconstruction of the old ID.
    fun legacyMetadata(value: JournalEntry): List<Any?> =
        listOf(
            value.createdAt,
            value.mood,
            value.tags,
            value.note,
            value.outdoor,
            value.feelings,
            value.moodScaleVersion,
            value.imageFileNames.size,
            value.audio?.let { it.durationMillis to it.mimeType },
            value.video?.let { Triple(it.durationMillis, it.sizeBytes, it.mimeType) },
        )
    val legacyCandidates =
        local
            .filter {
                it.id.length == 36 &&
                    runCatching { UUID.fromString(it.id).version() == 3 }.getOrDefault(false)
            }
            .groupBy(::legacyMetadata)
    var legacyProbeBudget = 1_000_000
    incoming.forEach { entry ->
        val key = incomingKey(entry)
        known[entry.id]?.let { existing ->
            if (keys.getOrPut(entry.id) { localKey(existing) } == key) return@forEach
        }
        val origin = entry.id.byteInputStream().use(::attachmentDigest)
        val prefix = "xike-restore-version:$origin:$key"
        // Index every retained version, including those after a deleted collision slot.
        if (
            versionIds[prefix].orEmpty().any { id ->
                keys.getOrPut(id) { localKey(known.getValue(id)) } == key
            }
        )
            return@forEach
        fun legacyConflictId(index: Int) =
            UUID.nameUUIDFromBytes(
                    "xike-restore-conflict:${entry.id}:$key:$index".toByteArray(Charsets.UTF_8)
                )
                .toString()
        val matchingLegacyIds =
            legacyCandidates[legacyMetadata(entry)]
                .orEmpty()
                .filter { keys.getOrPut(it.id) { localKey(it) } == key }
                .map { it.id }
                .toSet()
        if (matchingLegacyIds.isNotEmpty()) {
            // Allocating old slot N required N occupied 36-byte UUIDs in the safety manifest.
            // Its unchanged 16 MiB limit therefore bounds every legitimately generated slot.
            // Do not stop at a deleted slot; an import-wide budget prevents adversarial work.
            val lastSlot = (MAX_BACKUP_MANIFEST_BYTES / 36L).toInt()
            for (index in 0..lastSlot) {
                check(legacyProbeBudget-- > 0) {
                    tr(
                        "旧冲突记录校验超过处理上限，原数据未被修改。",
                        "Legacy conflict verification exceeded its processing limit. Existing data is unchanged.",
                    )
                }
                if (legacyConflictId(index) in matchingLegacyIds) return@forEach
            }
        }
        var candidate = entry.id
        var collision = 0
        while (true) {
            val existing = known[candidate]
            if (existing == null) {
                val added = entry.copy(id = candidate)
                additions += added
                known[candidate] = added
                keys[candidate] = key
                if (candidate.startsWith("xike-restore-version:")) {
                    versionIds
                        .getOrPut(candidate.substringBeforeLast(':')) { mutableSetOf() }
                        .add(candidate)
                }
                break
            }
            if (keys.getOrPut(candidate) { localKey(existing) } == key) break
            // Stable conflict identities prevent another copy on every import. Probe occupied
            // IDs rather than overwriting an unrelated record or a subsequently edited copy.
            candidate = "$prefix:${collision++}"
        }
    }
    return additions
}
