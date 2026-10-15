package com.xike.app.data.model

import com.xike.app.canonicalTopics
import com.xike.app.data.JournalAudio
import com.xike.app.data.MAX_AUDIO_DURATION_MILLIS
import com.xike.app.data.MAX_IMAGES_PER_ENTRY
import com.xike.app.data.Mood
import com.xike.app.data.feelingKeys
import org.json.JSONArray
import org.json.JSONObject

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
    val feelings: List<String> = emptyList(),
    val moodScaleVersion: Int = if (mood?.isOverallScale == false) 0 else 1,
) {
    val isEmpty: Boolean
        get() =
            mood == null &&
                note.isBlank() &&
                tags.isEmpty() &&
                feelings.isEmpty() &&
                imageUriStrings.isEmpty() &&
                audio == null &&
                video == null &&
                pendingVideoUri == null &&
                recordedAt == null &&
                outdoor == null

    fun normalized(): JournalDraft {
        val normalizedNote = note.take(MAX_DRAFT_NOTE_LENGTH)
        val normalizedFeelings = feelings.filter(String::isNotBlank).distinct()
        val normalizedTags =
            tags.filter(String::isNotBlank).canonicalTopics().toCollection(linkedSetOf())
        val normalizedImages =
            imageUriStrings.filter(String::isNotBlank).distinct().take(MAX_IMAGES_PER_ENTRY)
        val normalizedAudio = audio?.takeIf {
            it.fileName.isNotBlank() && it.durationMillis in 1..MAX_AUDIO_DURATION_MILLIS
        }
        val normalizedRecordedAt = recordedAt?.takeIf { it > 0L }
        val normalizedOutdoor =
            outdoor?.normalizedOrNull()?.let { snapshot ->
                if (normalizedRecordedAt == null) snapshot
                else retainOutdoorForEditedTime(snapshot, snapshot.capturedAt, normalizedRecordedAt)
            }
        val normalizedIsEmpty =
            mood == null &&
                normalizedNote.isBlank() &&
                normalizedTags.isEmpty() &&
                normalizedFeelings.isEmpty() &&
                normalizedImages.isEmpty() &&
                normalizedAudio == null &&
                video == null &&
                pendingVideoUri == null &&
                normalizedRecordedAt == null &&
                normalizedOutdoor == null
        return copy(
            note = normalizedNote,
            tags = normalizedTags,
            feelings = normalizedFeelings,
            imageUriStrings = normalizedImages,
            audio = normalizedAudio,
            recordedAt = normalizedRecordedAt,
            outdoor = normalizedOutdoor,
            updatedAt = if (normalizedIsEmpty) 0L else updatedAt,
        )
    }

    fun toJson(): JSONObject =
        JSONObject()
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
            .put("feelings", JSONArray(feelings))
            .put("moodScaleVersion", moodScaleVersion)

    companion object {
        fun fromJson(json: JSONObject): JournalDraft =
            JournalDraft(
                    mood =
                        json.optString("mood").takeIf(String::isNotBlank)?.let { stored ->
                            Mood.entries
                                .firstOrNull { it.name == stored }
                                ?.let { Mood.fromName(it.name, json.optInt("moodScaleVersion", 0)) }
                        },
                    note = json.optString("note").take(MAX_DRAFT_NOTE_LENGTH),
                    tags = json.optJSONArray("tags")?.toStringSet().orEmpty(),
                    imageUriStrings =
                        json.optJSONArray("imageUriStrings")?.toStringList().orEmpty(),
                    audio = JournalAudio.fromJson(json.optJSONObject("audio")),
                    video = JournalVideo.fromJson(json.optJSONObject("video")),
                    pendingVideoUri =
                        json.optString("pendingVideoUri").takeIf {
                            it.isNotBlank() && it != "null"
                        },
                    recordedAt = json.optLong("recordedAt").takeIf { it > 0L },
                    outdoor = OutdoorSnapshot.fromJson(json.optJSONObject("outdoor")),
                    updatedAt = json.optLong("updatedAt"),
                    feelings = json.optJSONArray("feelings")?.feelingKeys().orEmpty(),
                    moodScaleVersion = json.optInt("moodScaleVersion", 0),
                )
                .normalized()
    }
}

internal const val MAX_DRAFT_NOTE_LENGTH = 280
private const val DRAFT_FORMAT_VERSION = 6

internal fun parseJournalDraft(serialized: String?): JournalDraft =
    serialized?.takeIf(String::isNotBlank)?.let { JSONObject(it) }?.let(JournalDraft::fromJson)
        ?: JournalDraft()

private fun JSONArray.toStringList(): List<String> = buildList {
    repeat(length()) { index -> optString(index).takeIf(String::isNotBlank)?.let(::add) }
}

private fun JSONArray.toStringSet(): Set<String> = toStringList().toCollection(linkedSetOf())
