package com.xike.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class JournalDraft(
    val mood: Mood? = null,
    val note: String = "",
    val tags: Set<String> = emptySet(),
    val imageUriStrings: List<String> = emptyList(),
    val recordedAt: Long? = null,
    val outdoor: OutdoorSnapshot? = null,
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean
        get() = mood == null &&
            note.isBlank() &&
            tags.isEmpty() &&
            imageUriStrings.isEmpty() &&
            recordedAt == null &&
            outdoor == null

    fun normalized(): JournalDraft {
        val normalizedNote = note.take(MAX_DRAFT_NOTE_LENGTH)
        val normalizedTags = tags.filterTo(linkedSetOf()) { it.isNotBlank() }
        val normalizedImages = imageUriStrings
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_IMAGES_PER_ENTRY)
        val normalizedRecordedAt = recordedAt?.takeIf { it > 0L }
        val normalizedOutdoor = outdoor?.normalizedOrNull()?.takeIf { normalizedRecordedAt == null }
        val normalizedIsEmpty = mood == null &&
            normalizedNote.isBlank() &&
            normalizedTags.isEmpty() &&
            normalizedImages.isEmpty() &&
            normalizedRecordedAt == null &&
            normalizedOutdoor == null
        return copy(
            note = normalizedNote,
            tags = normalizedTags,
            imageUriStrings = normalizedImages,
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
            recordedAt = json.optLong("recordedAt").takeIf { it > 0L },
            outdoor = OutdoorSnapshot.fromJson(json.optJSONObject("outdoor")),
            updatedAt = json.optLong("updatedAt"),
        ).normalized()
    }
}

internal const val MAX_DRAFT_NOTE_LENGTH = 280
private const val DRAFT_FORMAT_VERSION = 3

internal fun parseJournalDraft(serialized: String?): JournalDraft = serialized
    ?.takeIf(String::isNotBlank)
    ?.let { JSONObject(it) }
    ?.let(JournalDraft::fromJson)
    ?: JournalDraft()

internal class JournalDraftStore(context: Context) {
    private val appContext = context.applicationContext
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

    fun load(): JournalDraft = parseJournalDraft(preferences.getString(DRAFT_KEY, null))

    fun save(draft: JournalDraft) {
        val normalized = draft.normalized()
        val editor = preferences.edit()
        if (normalized.isEmpty) {
            editor.remove(DRAFT_KEY)
        } else {
            editor.putString(DRAFT_KEY, normalized.toJson().toString())
        }
        check(editor.commit()) { "无法保存当前草稿。" }
    }

    private companion object {
        const val PREFERENCES_NAME = "xike-journal-draft"
        const val DRAFT_KEY = "draft"
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    repeat(length()) { index -> optString(index).takeIf(String::isNotBlank)?.let(::add) }
}

private fun JSONArray.toStringSet(): Set<String> = toStringList().toCollection(linkedSetOf())
