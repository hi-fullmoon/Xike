package com.xike.app.data.model

import java.io.File
import org.json.JSONObject

const val MAX_VIDEO_DURATION_MILLIS = 300_000L
internal const val MAX_VIDEO_BYTES = 500L * 1024 * 1024

data class JournalVideo(
    val fileName: String,
    val coverFileName: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val mimeType: String,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("fileName", fileName)
            .put("coverFileName", coverFileName)
            .put("durationMillis", durationMillis)
            .put("sizeBytes", sizeBytes)
            .put("mimeType", mimeType)

    internal fun isValid(): Boolean =
        durationMillis in 1..MAX_VIDEO_DURATION_MILLIS &&
            sizeBytes in 1..MAX_VIDEO_BYTES &&
            mimeType in videoExtensions &&
            safeVideoName(fileName) &&
            fileName.endsWith(".${videoExtensions[mimeType]}") &&
            safeVideoName(coverFileName) &&
            coverFileName.endsWith(".jpg") &&
            fileName != coverFileName

    companion object {
        fun fromJson(json: JSONObject?): JournalVideo? = json?.let {
            JournalVideo(
                it.getString("fileName"),
                it.getString("coverFileName"),
                it.getLong("durationMillis"),
                it.getLong("sizeBytes"),
                it.getString("mimeType"),
            )
        }
    }
}

internal fun JournalVideo?.files(): Set<String> =
    this?.let { setOf(it.fileName, it.coverFileName) }.orEmpty()

internal val videoExtensions =
    mapOf(
        "video/mp4" to "mp4",
        "video/3gpp" to "3gp",
        "video/webm" to "webm",
        "video/quicktime" to "mov",
        "video/x-matroska" to "mkv",
    )

internal fun safeVideoName(name: String): Boolean =
    name.isNotBlank() &&
        name.length <= 160 &&
        File(name).name == name &&
        '/' !in name &&
        '\\' !in name

internal data class VideoEditDraft(val originalFileName: String?, val video: JournalVideo?)
