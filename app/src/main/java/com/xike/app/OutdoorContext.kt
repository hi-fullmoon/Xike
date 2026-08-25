package com.xike.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

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
private const val MAX_PLACE_NAME_LENGTH = 80
private const val MAX_WEATHER_SOURCE_LENGTH = 40
private const val MIN_TEMPERATURE_CELSIUS = -100.0
private const val MAX_TEMPERATURE_CELSIUS = 70.0
private const val MIN_WEATHER_CODE = 0
private const val MAX_WEATHER_CODE = 99

internal fun weatherConditionLabel(code: Int): String = when (code) {
    0 -> "晴朗"
    1, 2 -> "晴间多云"
    3 -> "阴"
    45, 48 -> "有雾"
    51, 53, 55 -> "毛毛雨"
    56, 57, 66, 67 -> "冻雨"
    61, 63, 65 -> "有雨"
    71, 73, 75, 77 -> "有雪"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95 -> "雷雨"
    96, 99 -> "雷雨伴冰雹"
    else -> "天气未知"
}

internal class OutdoorContextException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal class OutdoorContextRepository(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    suspend fun current(): OutdoorSnapshot {
        val location = currentCoarseLocation()
        val placeName = reverseGeocode(location) ?: "当前位置"
        return fetchWeather(location.latitude, location.longitude, placeName)
    }

    suspend fun city(query: String): OutdoorSnapshot {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) { "请输入城市名称。" }
        val city = withContext(Dispatchers.IO) { searchCity(normalizedQuery) }
        return fetchWeather(city.latitude, city.longitude, city.label)
    }

    private suspend fun currentCoarseLocation(): Location {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw OutdoorContextException("需要粗略位置权限才能获取当前位置。")
        }
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            throw OutdoorContextException("系统定位尚未开启，也可以手动选择城市。")
        }

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider ->
                LocationManagerCompat.hasProvider(locationManager, provider) &&
                    runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }
        if (providers.isEmpty()) {
            throw OutdoorContextException("暂时找不到可用的定位服务，也可以手动选择城市。")
        }

        providers.forEach { provider ->
            val location = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                requestCurrentLocation(provider)
            }
            if (location != null) return location
        }
        throw OutdoorContextException("暂时无法取得当前位置，请稍后重试或手动选择城市。")
    }

    private suspend fun requestCurrentLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(appContext),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            }.onFailure { error ->
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private suspend fun reverseGeocode(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.CHINA)
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                }.getOrNull()
            }
        }
        return address?.asPlaceLabel()
    }

    private suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        placeName: String,
    ): OutdoorSnapshot = withContext(Dispatchers.IO) {
        val coordinateFormat = "%.5f"
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?latitude=")
            append(coordinateFormat.format(Locale.US, latitude))
            append("&longitude=")
            append(coordinateFormat.format(Locale.US, longitude))
            append("&current=temperature_2m,weather_code&timezone=auto&forecast_days=1")
        }
        val current = requestJson(url).optJSONObject("current")
            ?: throw OutdoorContextException("天气服务暂时没有返回当前天气。")
        OutdoorSnapshot(
            placeName = placeName,
            temperatureCelsius = current.optDouble("temperature_2m", Double.NaN),
            weatherCode = current.optInt("weather_code", -1),
            capturedAt = System.currentTimeMillis(),
        ).normalizedOrNull() ?: throw OutdoorContextException("天气服务返回了无法识别的数据。")
    }

    private fun searchCity(query: String): CityCoordinate {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val response = requestJson(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=zh&format=json",
        )
        val result = response.optJSONArray("results")?.optJSONObject(0)
            ?: throw OutdoorContextException("没有找到这个城市，请换一种写法。")
        val name = result.optString("name").trim()
        val admin = result.optString("admin1").trim()
        val label = listOf(admin, name)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · ")
            .ifBlank { query }
        return CityCoordinate(
            label = label.take(MAX_PLACE_NAME_LENGTH),
            latitude = result.optDouble("latitude", Double.NaN),
            longitude = result.optDouble("longitude", Double.NaN),
        ).takeIf { it.latitude.isFinite() && it.longitude.isFinite() }
            ?: throw OutdoorContextException("城市服务返回了无法识别的位置。")
    }

    private fun requestJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Xike/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw OutdoorContextException("天气服务暂时不可用，请稍后重试。")
            }
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(payload)
        } catch (error: OutdoorContextException) {
            throw error
        } catch (error: Exception) {
            throw OutdoorContextException("无法连接天气服务，请检查网络后重试。", error)
        } finally {
            connection.disconnect()
        }
    }

    private data class CityCoordinate(
        val label: String,
        val latitude: Double,
        val longitude: Double,
    )

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 12_000L
        const val NETWORK_TIMEOUT_MILLIS = 10_000
    }
}

private fun Address.asPlaceLabel(): String? = listOf(
    adminArea,
    locality,
    subAdminArea,
    subLocality,
)
    .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
    .distinct()
    .take(2)
    .joinToString(" · ")
    .takeIf(String::isNotBlank)
