package com.xike.app.data.model

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

data class OutdoorSnapshot(
    val placeName: String,
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val capturedAt: Long,
    val source: String = OPEN_METEO_SOURCE,
) {
    fun normalizedOrNull(): OutdoorSnapshot? {
        val normalizedPlace = placeName.trim().take(MAX_PLACE_NAME_LENGTH)
        val normalizedSource = source.trim().take(MAX_WEATHER_SOURCE_LENGTH)
        return takeIf {
            normalizedPlace.isNotBlank() &&
                temperatureCelsius.isFinite() &&
                temperatureCelsius in MIN_TEMPERATURE_CELSIUS..MAX_TEMPERATURE_CELSIUS &&
                weatherCode in MIN_WEATHER_CODE..MAX_WEATHER_CODE &&
                capturedAt > 0L &&
                normalizedSource.isNotBlank()
        }?.copy(placeName = normalizedPlace, source = normalizedSource)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("placeName", placeName)
        .put("temperatureCelsius", temperatureCelsius)
        .put("weatherCode", weatherCode)
        .put("capturedAt", capturedAt)
        .put("source", source)

    companion object {
        fun fromJson(json: JSONObject?): OutdoorSnapshot? = json?.let {
            OutdoorSnapshot(
                placeName = it.optString("placeName"),
                temperatureCelsius = it.optDouble("temperatureCelsius", Double.NaN),
                weatherCode = it.optInt("weatherCode", -1),
                capturedAt = it.optLong("capturedAt"),
                source = it.optString("source", OPEN_METEO_SOURCE),
            ).normalizedOrNull()
        }
    }
}

internal const val OPEN_METEO_SOURCE = "Open-Meteo"
internal const val MAX_PLACE_NAME_LENGTH = 80
private const val MAX_WEATHER_SOURCE_LENGTH = 40
private const val MIN_TEMPERATURE_CELSIUS = -100.0
private const val MAX_TEMPERATURE_CELSIUS = 70.0
private const val MIN_WEATHER_CODE = 0
private const val MAX_WEATHER_CODE = 99

internal fun retainOutdoorForEditedTime(
    snapshot: OutdoorSnapshot?,
    originalCreatedAt: Long,
    editedCreatedAt: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): OutdoorSnapshot? = snapshot?.takeIf {
    Instant.ofEpochMilli(originalCreatedAt).atZone(zoneId).toLocalDate() ==
        Instant.ofEpochMilli(editedCreatedAt).atZone(zoneId).toLocalDate()
}
