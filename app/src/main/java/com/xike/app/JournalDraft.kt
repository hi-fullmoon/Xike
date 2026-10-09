package com.xike.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class JournalDraft(
    val mood: Mood? = null,
    val note: String = "",
    val tags: Set<String> = emptySet(),
    val imageUriStrings: List<String> = emptyList(),
    val audio: JournalAudio? = null,
    val video: JournalVideo? = null,
    val pendingVideoUri: String? = null,
    val recordedAt: Long? = null,
    val outdoor: OutdoorSnapshot? = null,
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean
        get() = mood == null &&
            note.isBlank() &&
            tags.isEmpty() &&
            imageUriStrings.isEmpty() &&
            audio == null &&
            video == null &&
            pendingVideoUri == null &&
            recordedAt == null &&
            outdoor == null

    fun normalized(): JournalDraft {
        val normalizedNote = note.take(MAX_DRAFT_NOTE_LENGTH)
        val normalizedTags = tags.filter(String::isNotBlank).canonicalTopics().toCollection(linkedSetOf())
        val normalizedImages = imageUriStrings
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_IMAGES_PER_ENTRY)
        val normalizedAudio = audio?.takeIf {
            it.fileName.isNotBlank() && it.durationMillis in 1..MAX_AUDIO_DURATION_MILLIS
        }
        val normalizedRecordedAt = recordedAt?.takeIf { it > 0L }
        val normalizedOutdoor = outdoor?.normalizedOrNull()?.takeIf { normalizedRecordedAt == null }
        val normalizedIsEmpty = mood == null &&
            normalizedNote.isBlank() &&
            normalizedTags.isEmpty() &&
            normalizedImages.isEmpty() &&
            normalizedAudio == null &&
            video == null &&
            pendingVideoUri == null &&
            normalizedRecordedAt == null &&
            normalizedOutdoor == null
        return copy(
            note = normalizedNote,
            tags = normalizedTags,
            imageUriStrings = normalizedImages,
            audio = normalizedAudio,
            recordedAt = normalizedRecordedAt,
            outdoor = normalizedOutdoor,
            updatedAt = if (normalizedIsEmpty) 0L else updatedAt,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("version", DRAFT_FORMAT_VERSION)
        .put("mood", mood?.name ?: JSONObject.NULL)
        .put("note", note)
        .put("tags", JSONArray(tags.toList()))
        .put("imageUriStrings", JSONArray(imageUriStrings))
        .put("audio", audio?.toJson() ?: JSONObject.NULL)
        .put("video", video?.toJson() ?: JSONObject.NULL)
        .put("pendingVideoUri", pendingVideoUri ?: JSONObject.NULL)
        .put("recordedAt", recordedAt ?: JSONObject.NULL)
        .put("outdoor", outdoor?.toJson() ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): JournalDraft = JournalDraft(
            mood = json.optString("mood")
                .takeIf(String::isNotBlank)
                ?.let { stored -> Mood.entries.firstOrNull { it.name == stored } },
            note = json.optString("note").take(MAX_DRAFT_NOTE_LENGTH),
            tags = json.optJSONArray("tags")?.toStringSet().orEmpty(),
            imageUriStrings = json.optJSONArray("imageUriStrings")?.toStringList().orEmpty(),
            audio = JournalAudio.fromJson(json.optJSONObject("audio")),
            video = JournalVideo.fromJson(json.optJSONObject("video")),
            pendingVideoUri = json.optString("pendingVideoUri").takeIf { it.isNotBlank() && it != "null" },
            recordedAt = json.optLong("recordedAt").takeIf { it > 0L },
            outdoor = OutdoorSnapshot.fromJson(json.optJSONObject("outdoor")),
            updatedAt = json.optLong("updatedAt"),
        ).normalized()
    }
}

internal const val MAX_DRAFT_NOTE_LENGTH = 280
private const val DRAFT_FORMAT_VERSION = 5

internal fun parseJournalDraft(serialized: String?): JournalDraft = serialized
    ?.takeIf(String::isNotBlank)
    ?.let { JSONObject(it) }
    ?.let(JournalDraft::fromJson)
    ?: JournalDraft()

internal class JournalDraftStore(context: Context) {
    private val appContext = context.applicationContext
    private val coordinator = coordinators.computeIfAbsent(appContext.filesDir.absolutePath) { WriteCoordinator() }
    private var writerSession: Long? = null
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val preferences = EncryptedSharedPreferences.create(
        appContext,
        PREFERENCES_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): JournalDraft {
        synchronized(coordinator) { coordinator.pending?.let { return it } }
        return parseJournalDraft(preferences.getString(DRAFT_KEY, null))
    }

    val hasPendingWrite: Boolean
        get() = synchronized(coordinator) {
            coordinator.pending != null
        }

    fun save(draft: JournalDraft) {
        val (previousPending, revision) = synchronized(coordinator) {
            coordinator.pending to nextRevision()
        }
        try {
            check(saveIfCurrent(draft, revision)) { localizedText("草稿保存会话已经更新，请重新打开记录页面。") }
        } catch (error: Throwable) {
            synchronized(coordinator) {
                if (writerSession == coordinator.session && coordinator.revision == revision) {
                    coordinator.pending = previousPending
                }
            }
            throw error
        }
    }

    fun nextRevision(): Long = synchronized(coordinator) {
        if (writerSession == null) writerSession = ++coordinator.session
        if (writerSession != coordinator.session) return@synchronized -1L
        ++coordinator.revision
    }

    fun queue(draft: JournalDraft): Long = synchronized(coordinator) {
        val revision = nextRevision()
        if (revision >= 0L) coordinator.pending = draft.normalized()
        revision
    }

    fun flushPending() {
        val pending = synchronized(coordinator) {
            coordinator.pending?.let { it to coordinator.revision }
        } ?: return
        saveIfCurrent(pending.first, pending.second)
    }

    fun saveIfCurrent(draft: JournalDraft, expectedRevision: Long): Boolean {
        val normalized = draft.normalized()
        synchronized(coordinator) {
            if (writerSession != coordinator.session || coordinator.revision != expectedRevision) return false
            coordinator.pending = normalized
        }
        return synchronized(coordinator.diskLock) {
            synchronized(coordinator) {
                if (writerSession != coordinator.session || coordinator.revision != expectedRevision) return false
            }
            val editor = preferences.edit()
            if (normalized.isEmpty) {
                editor.remove(DRAFT_KEY)
            } else {
                editor.putString(DRAFT_KEY, normalized.toJson().toString())
            }
            check(editor.commit()) { localizedText("无法保存当前草稿。") }
            synchronized(coordinator) {
                if (writerSession == coordinator.session && coordinator.revision == expectedRevision) {
                    coordinator.pending = null
                }
            }
            true
        }
    }

    private class WriteCoordinator {
        val diskLock = Any()
        var session = 0L
        var revision = 0L
        var pending: JournalDraft? = null
    }

    private companion object {
        val coordinators = ConcurrentHashMap<String, WriteCoordinator>()
        const val PREFERENCES_NAME = "xike-journal-draft"
        const val DRAFT_KEY = "draft"
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    repeat(length()) { index -> optString(index).takeIf(String::isNotBlank)?.let(::add) }
}

private fun JSONArray.toStringSet(): Set<String> = toStringList().toCollection(linkedSetOf())
