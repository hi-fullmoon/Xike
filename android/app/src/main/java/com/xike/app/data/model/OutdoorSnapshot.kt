package com.xike.app.data.model

import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject

data class OutdoorSnapshot(
    val placeName: String,
    val temperatureCelsius: Double? = null,
    val weatherCode: Int? = null,
    val capturedAt: Long,
    val source: String? = OPEN_METEO_SOURCE,
    val fullPlaceName: String? = null,
    val locationSource: LocationSource? = null,
) {
    val hasWeather: Boolean
        get() = temperatureCelsius != null && weatherCode != null

    fun normalizedOrNull(): OutdoorSnapshot? {
        val normalizedPlace = placeName.trim().take(MAX_PLACE_NAME_LENGTH)
        if (normalizedPlace.isBlank() || capturedAt <= 0L) return null
        val normalizedFullPlace =
            fullPlaceName?.trim()?.take(MAX_PLACE_NAME_LENGTH)?.takeIf(String::isNotBlank)
        if (temperatureCelsius == null && weatherCode == null) {
            return copy(
                placeName = normalizedPlace,
                temperatureCelsius = null,
                weatherCode = null,
                source = null,
                fullPlaceName = normalizedFullPlace,
            )
        }
        val temp = temperatureCelsius ?: return null
        val code = weatherCode ?: return null
        val normalizedSource =
            source?.trim()?.take(MAX_WEATHER_SOURCE_LENGTH)?.takeIf(String::isNotBlank)
                ?: return null
        return takeIf {
                temp.isFinite() &&
                    temp in MIN_TEMPERATURE_CELSIUS..MAX_TEMPERATURE_CELSIUS &&
                    code in MIN_WEATHER_CODE..MAX_WEATHER_CODE
            }
            ?.copy(
                placeName = normalizedPlace,
                source = normalizedSource,
                fullPlaceName = normalizedFullPlace,
            )
    }

    fun toJson(): JSONObject =
        JSONObject()
            .put("placeName", placeName)
            .apply {
                if (temperatureCelsius != null && weatherCode != null) {
                    put("temperatureCelsius", temperatureCelsius)
                    put("weatherCode", weatherCode)
                }
            }
            .put("capturedAt", capturedAt)
            .apply { source?.let { put("source", it) } }
            .apply { fullPlaceName?.let { put("fullPlaceName", it) } }
            .apply { locationSource?.let { put("locationSource", it.id) } }

    companion object {
        fun fromJson(json: JSONObject?): OutdoorSnapshot? = json?.let {
            val hasTemperature = it.has("temperatureCelsius") && !it.isNull("temperatureCelsius")
            val hasWeatherCode = it.has("weatherCode") && !it.isNull("weatherCode")
            val temperature =
                if (hasTemperature) it.optDouble("temperatureCelsius", Double.NaN) else null
            val weatherCode = if (hasWeatherCode) it.optInt("weatherCode", -1) else null
            val source =
                if (temperature != null || weatherCode != null) {
                    it.optString("source", OPEN_METEO_SOURCE)
                } else {
                    null
                }
            OutdoorSnapshot(
                    placeName = it.optString("placeName"),
                    temperatureCelsius = temperature,
                    weatherCode = weatherCode,
                    capturedAt = it.optLong("capturedAt"),
                    source = source,
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
    !it.hasWeather ||
        Instant.ofEpochMilli(originalCreatedAt).atZone(zoneId).toLocalDate() ==
            Instant.ofEpochMilli(editedCreatedAt).atZone(zoneId).toLocalDate()
}
