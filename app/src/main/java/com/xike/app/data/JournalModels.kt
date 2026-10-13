package com.xike.app.data

import com.xike.app.localizedText
import com.xike.app.JournalVideo
import com.xike.app.OutdoorSnapshot

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Mood(private val sourceLabel: String, val score: Int, val emoji: String) {
    LOW("低落", 1, "😔"),
    TIRED("疲惫", 2, "😫"),
    CALM("平静", 3, "😌"),
    GOOD("轻松", 4, "🙂"),
    JOYFUL("愉悦", 5, "😄");

    val label: String get() = localizedText(sourceLabel)

    companion object {
        fun fromName(value: String): Mood = entries.firstOrNull { it.name == value } ?: CALM
    }
}

const val MAX_IMAGES_PER_ENTRY = 9
const val MAX_AUDIO_DURATION_MILLIS = 5L * 60L * 1000L
internal const val MAX_AUDIO_BYTES = 16L * 1024L * 1024L

data class JournalAudio(
    val fileName: String,
    val durationMillis: Long,
    val mimeType: String = "audio/mp4",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fileName", fileName)
        .put("durationMillis", durationMillis)
        .put("mimeType", mimeType)

    companion object {
        fun fromJson(json: JSONObject?): JournalAudio? = json?.let {
            JournalAudio(
                fileName = it.optString("fileName"),
                durationMillis = it.optLong("durationMillis"),
                mimeType = it.optString("mimeType", "audio/mp4"),
            ).takeIf { audio -> audio.fileName.isNotBlank() && audio.durationMillis > 0L }
        }
    }
}

data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val mood: Mood,
    val tags: List<String>,
    val note: String,
    val imageFileNames: List<String> = emptyList(),
    val audio: JournalAudio? = null,
    val video: JournalVideo? = null,
    val outdoor: OutdoorSnapshot? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("createdAt", createdAt)
        .put("mood", mood.name)
        .put("tags", JSONArray(tags))
        .put("note", note)
        .put("imageFileNames", JSONArray(imageFileNames))
        .put("audio", audio?.toJson() ?: JSONObject.NULL)
        .put("video", video?.toJson() ?: JSONObject.NULL)
        .put("outdoor", outdoor?.toJson() ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): JournalEntry = JournalEntry(
            id = json.getString("id"),
            createdAt = json.getLong("createdAt"),
            mood = Mood.fromName(json.getString("mood")),
            tags = json.optJSONArray("tags")?.let { values ->
                List(values.length()) { index -> values.getString(index) }
            } ?: emptyList(),
            note = json.optString("note"),
            imageFileNames = json.optJSONArray("imageFileNames")?.let { values ->
                List(values.length()) { index -> values.getString(index) }
            } ?: listOfNotNull(json.optString("imageFileName").takeIf { it.isNotBlank() }),
            audio = JournalAudio.fromJson(json.optJSONObject("audio")),
            video = JournalVideo.fromJson(json.optJSONObject("video")),
            outdoor = OutdoorSnapshot.fromJson(json.optJSONObject("outdoor")),
        )
    }
}

class JournalDataException(message: String, cause: Throwable) : IllegalStateException(message, cause)

data class JournalSnapshot(
    val entries: List<JournalEntry>,
    val themeName: String?,
    val styleName: String? = null,
)
