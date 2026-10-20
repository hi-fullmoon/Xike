package com.xike.app.data.model

import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject

data class OutdoorSnapshot(
    val placeName: String,
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val capturedAt: Long,
    val source: String = OPEN_METEO_SOURCE,
    val fullPlaceName: String? = null,
    val locationSource: LocationSource? = null,
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
            }
            ?.copy(
                placeName = normalizedPlace,
                source = normalizedSource,
                fullPlaceName =
                    fullPlaceName?.trim()?.take(MAX_PLACE_NAME_LENGTH)?.takeIf(String::isNotBlank),
            )
    }

    fun toJson(): JSONObject =
        JSONObject()
            .put("placeName", placeName)
            .put("temperatureCelsius", temperatureCelsius)
            .put("weatherCode", weatherCode)
            .put("capturedAt", capturedAt)
            .put("source", source)
            .apply { fullPlaceName?.let { put("fullPlaceName", it) } }
            .apply { locationSource?.let { put("locationSource", it.id) } }

    companion object {
        fun fromJson(json: JSONObject?): OutdoorSnapshot? = json?.let {
            OutdoorSnapshot(
                    placeName = it.optString("placeName"),
                    temperatureCelsius = it.optDouble("temperatureCelsius", Double.NaN),
                    weatherCode = it.optInt("weatherCode", -1),
                    capturedAt = it.optLong("capturedAt"),
                    source = it.optString("source", OPEN_METEO_SOURCE),
                    fullPlaceName = it.optString("fullPlaceName").takeIf(String::isNotBlank),
                    locationSource = LocationSource.fromId(it.optString("locationSource")),
                )
                .normalizedOrNull()
        }
    }
}

/** Stable identifiers are stored independently of translated labels and weather providers. */
enum class LocationSource(val id: String) {
    AMAP("amap"),
    SYSTEM("system"),
    MANUAL("manual");

    companion object {
        fun fromId(id: String?): LocationSource? = entries.firstOrNull { it.id == id }
    }
}

internal const val OPEN_METEO_SOURCE = "Open-Meteo"
internal const val MAX_PLACE_NAME_LENGTH = 512
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
