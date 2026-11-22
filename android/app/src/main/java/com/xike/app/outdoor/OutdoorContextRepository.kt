package com.xike.app.outdoor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.xike.app.AppLocale
import com.xike.app.BuildConfig
import com.xike.app.data.model.LocationSource
import com.xike.app.data.model.MAX_PLACE_NAME_LENGTH
import com.xike.app.data.model.OutdoorSnapshot
import com.xike.app.localizedText
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal suspend fun <Candidate, Value : Any> firstNonNullResult(
    candidates: List<Candidate>,
    timeoutMillis: Long? = null,
    isPreferred: (Value) -> Boolean = { true },
    isBetter: (Value, Value) -> Boolean = { _, _ -> false },
    request: suspend (Candidate) -> Value?,
): Value? {
    if (candidates.isEmpty()) return null
    return supervisorScope {
        val result = CompletableDeferred<Unit>()
        val best = AtomicReference<Value?>(null)
        val remaining = AtomicInteger(candidates.size)
        candidates.forEach { candidate ->
            launch {
                try {
                    val resolved =
                        try {
                            request(candidate)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    if (resolved != null) {
                        best.updateAndGet { previous ->
                            if (previous == null || isBetter(resolved, previous)) resolved
                            else previous
                        }
                        if (isPreferred(resolved)) result.complete(Unit)
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) result.complete(Unit)
                }
            }
        }
        if (timeoutMillis == null) result.await()
        else withTimeoutOrNull(timeoutMillis) { result.await() }
        coroutineContext.cancelChildren()
        best.get()
    }
}

internal class OutdoorContextException(
    message: String,
    cause: Throwable? = null,
    val amapDiagnostic: AmapNetworkDiagnostic? = null,
) : IllegalStateException(message, cause)

/**
 * Coarse WGS84 coordinates kept strictly in memory for single-tap weather retry within the active
 * editor session. Never written to drafts, database, or backups.
 */
internal data class OutdoorRetryCoordinates(
    val latitude: Double,
    val longitude: Double,
)

internal data class OutdoorFetchResult(
    val snapshot: OutdoorSnapshot,
    val weatherError: String? = null,
    val retryCoordinates: OutdoorRetryCoordinates? = null,
)

internal fun coarseRetryCoordinates(latitude: Double, longitude: Double): OutdoorRetryCoordinates =
    OutdoorRetryCoordinates(
        latitude = (latitude * 1_000.0).roundToLong() / 1_000.0,
        longitude = (longitude * 1_000.0).roundToLong() / 1_000.0,
    )

internal fun placeOnlySnapshot(
    placeName: String,
    fullPlaceName: String?,
    locationSource: LocationSource,
    capturedAt: Long = System.currentTimeMillis(),
): OutdoorSnapshot? =
    OutdoorSnapshot(
            placeName = placeName,
            temperatureCelsius = null,
            weatherCode = null,
            capturedAt = capturedAt,
            source = null,
            fullPlaceName = fullPlaceName,
            locationSource = locationSource,
        )
        .normalizedOrNull()

internal class OutdoorContextRepository(
    context: Context,
    private val amapSettings: AmapSettingsStore = AmapSettingsStore(context),
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    suspend fun current(): OutdoorSnapshot = currentWithFallback().snapshot

    suspend fun currentWithFallback(
        onPlaceReady: suspend (OutdoorSnapshot) -> Unit = {}
    ): OutdoorFetchResult {
        val settings = withContext(Dispatchers.IO) { amapSettings.read() }
        if (settings.enabled) {
            if (!settings.canUseAmap) throw amapFailure(-5)
            val place = currentAmapPlace(settings, allowMissingAddress = true)
            val coordinates = weatherCoordinates(place.latitude, place.longitude, place.wgs84)
            val partial =
                place.labels?.let { labels ->
                    placeOnlySnapshot(
                        placeName = labels.label,
                        fullPlaceName = labels.fullLabel,
                        locationSource = LocationSource.AMAP,
                    )
                }
            if (partial != null) onPlaceReady(partial)
            return try {
                val weather =
                    fetchWeatherWithRetry(
                        coordinates.latitude,
                        coordinates.longitude,
                        place.labels?.label ?: localizedText("当前位置"),
                        place.labels?.fullLabel,
                        LocationSource.AMAP,
                    )
                OutdoorFetchResult(weather)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (partial != null) {
                    OutdoorFetchResult(
                        snapshot = partial,
                        weatherError = error.message ?: localizedText("天气暂未获取到"),
                        retryCoordinates =
                            coarseRetryCoordinates(coordinates.latitude, coordinates.longitude),
                    )
                } else {
                    throw amapFailure(-4)
                }
            }
        }
        val location = currentLocation()
        return coroutineScope {
            val geocodeDeferred = async { reverseGeocode(location) }
            val weatherDeferred = async {
                runCatching { fetchRawWeatherWithRetry(location.latitude, location.longitude) }
            }
            val place = geocodeDeferred.await()
            val partial = place?.let {
                placeOnlySnapshot(
                    placeName = it.label,
                    fullPlaceName = it.fullLabel,
                    locationSource = LocationSource.SYSTEM,
                )
            }
            if (partial != null) onPlaceReady(partial)
            val weatherResult = weatherDeferred.await()
            weatherResult.fold(
                onSuccess = { weather ->
                    val snapshot =
                        OutdoorSnapshot(
                                placeName = place?.label ?: localizedText("当前位置"),
                                fullPlaceName = place?.fullLabel,
                                locationSource = LocationSource.SYSTEM,
                                temperatureCelsius = weather.temperatureCelsius,
                                weatherCode = weather.weatherCode,
                                capturedAt = System.currentTimeMillis(),
                            )
                            .normalizedOrNull()
                            ?: throw OutdoorContextException(localizedText("天气服务返回了无法识别的数据。"))
                    OutdoorFetchResult(snapshot)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (partial != null) {
                        OutdoorFetchResult(
                            snapshot = partial,
                            weatherError = error.message ?: localizedText("天气暂未获取到"),
                            retryCoordinates =
                                coarseRetryCoordinates(location.latitude, location.longitude),
                        )
                    } else {
                        throw error
                    }
                },
            )
        }
    }

    internal suspend fun weatherAt(
        latitude: Double,
        longitude: Double,
        wgs84: Boolean,
        labels: PlaceLabels,
    ): OutdoorSnapshot {
        val coordinates = weatherCoordinates(latitude, longitude, wgs84)
        return fetchWeatherWithRetry(
            coordinates.latitude,
            coordinates.longitude,
            labels.label,
            labels.fullLabel,
            LocationSource.AMAP,
        )
    }

    suspend fun retryWeather(
        snapshot: OutdoorSnapshot,
        coordinates: OutdoorRetryCoordinates,
    ): OutdoorFetchResult =
        try {
            val updated =
                fetchWeatherWithRetry(
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
                    placeName = snapshot.placeName,
                    fullPlaceName = snapshot.fullPlaceName,
                    locationSource = snapshot.locationSource ?: LocationSource.SYSTEM,
                )
            OutdoorFetchResult(updated)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            OutdoorFetchResult(
                snapshot = snapshot,
                weatherError = error.message ?: localizedText("天气暂未获取到"),
                retryCoordinates = coordinates,
            )
        }

    private suspend fun currentAmapPlace(
        settings: AmapSettings,
        allowMissingAddress: Boolean = false,
    ): AmapPlace =
        AmapLocator(appContext).current(settings, allowMissingAddress) { location ->
            reverseGeocode(location)?.let { PlaceLabels(it.label, it.fullLabel) }
        }

    internal suspend fun testAmapLocation(settings: AmapSettings): String {
        val place = currentAmapPlace(settings, allowMissingAddress = false)
        val labels = place.labels ?: throw amapFailure(-4)
        return labels.fullLabel +
            if (place.systemAddressCompleted) {
                com.xike.app.tr("（系统地址服务补全）", " (completed by system address service)")
            } else ""
    }

    suspend fun city(query: String): OutdoorSnapshot = cityWithFallback(query).snapshot

    suspend fun cityWithFallback(
        query: String,
        onPlaceReady: suspend (OutdoorSnapshot) -> Unit = {},
    ): OutdoorFetchResult {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) { localizedText("请输入城市名称。") }
        val city = withContext(Dispatchers.IO) { searchCity(normalizedQuery) }
        val partial =
            placeOnlySnapshot(
                placeName = city.name,
                fullPlaceName = city.label,
                locationSource = LocationSource.MANUAL,
            )
        if (partial != null) onPlaceReady(partial)
        return try {
            val weather =
                fetchWeatherWithRetry(
                    city.latitude,
                    city.longitude,
                    city.name,
                    city.label,
                    LocationSource.MANUAL,
                )
            OutdoorFetchResult(weather)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (partial != null) {
                OutdoorFetchResult(
                    snapshot = partial,
                    weatherError = error.message ?: localizedText("天气暂未获取到"),
                    retryCoordinates = coarseRetryCoordinates(city.latitude, city.longitude),
                )
            } else {
                throw error
            }
        }
    }

    fun manualPlace(placeName: String): OutdoorSnapshot {
        val normalized = placeName.trim()
        require(normalized.isNotBlank()) { localizedText("请输入地点名称。") }
        return placeOnlySnapshot(
            placeName = normalized,
            fullPlaceName = null,
            locationSource = LocationSource.MANUAL,
        ) ?: throw OutdoorContextException(localizedText("请输入地点名称。"))
    }

    private suspend fun currentLocation(): Location {
        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw OutdoorContextException(localizedText("需要粗略位置权限才能获取当前位置。"))
        }
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            throw OutdoorContextException(localizedText("系统定位尚未开启，也可以手动选择城市。"))
        }

        val providers =
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { provider
                ->
                LocationManagerCompat.hasProvider(locationManager, provider) &&
                    runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }
        if (providers.isEmpty()) {
            throw OutdoorContextException(localizedText("暂时找不到可用的定位服务，也可以手动选择城市。"))
        }

        val precise =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        val location =
            firstNonNullResult(
                providers,
                timeoutMillis = LOCATION_TIMEOUT_MILLIS,
                isPreferred = { !precise || it.accuracy <= PREFERRED_ACCURACY_METERS },
                isBetter = { candidate, previous -> candidate.accuracy < previous.accuracy },
            ) { provider ->
                requestCurrentLocation(provider)?.takeIf { candidate ->
                    val ageNanos =
                        SystemClock.elapsedRealtimeNanos() - candidate.elapsedRealtimeNanos
                    ageNanos in 0..MAX_LOCATION_AGE_NANOS &&
                        candidate.hasAccuracy() &&
                        candidate.accuracy.isFinite() &&
                        candidate.accuracy >= 0 &&
                        candidate.latitude.isFinite() &&
                        candidate.latitude in -90.0..90.0 &&
                        candidate.longitude.isFinite() &&
                        candidate.longitude in -180.0..180.0
                }
            }
        if (location != null) return location
        throw OutdoorContextException(localizedText("暂时无法取得当前位置，请稍后重试或手动选择城市。"))
    }

    private suspend fun requestCurrentLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
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
            }
                .onFailure { error ->
                    if (continuation.isActive) continuation.resume(null)
                }
        }

    private suspend fun reverseGeocode(location: Location): PlaceLabel? {
        if (!Geocoder.isPresent()) return null
        val locale = AppLocale.locale
        val address =
            withTimeoutOrNull(NETWORK_TIMEOUT_MILLIS.toLong()) {
                geocodeAddress(location, locale)
            } ?: return null
        val includeStreet = canIncludeStreet(location)
        val fullLabel = address.asPlaceLabel(includeStreet) ?: return null
        val label =
            listOf(
                    address.locality,
                    address.subAdminArea,
                    address.subLocality,
                    address.thoroughfare.takeIf { includeStreet },
                )
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .distinct()
                .joinToString(" · ")
                .ifBlank {
                    address.adminArea?.takeIf(String::isNotBlank)
                        ?: address.countryName
                        ?: fullLabel
                }
        return withContext(Dispatchers.Default) {
            PlaceLabel(localizePlaceName(label, locale), localizePlaceName(fullLabel, locale))
        }
    }

    private fun canIncludeStreet(location: Location): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED && location.accuracy <= PREFERRED_ACCURACY_METERS

    private suspend fun geocodeAddress(location: Location, locale: Locale): Address? {
        val geocoder = runCatching { Geocoder(appContext, locale) }.getOrNull() ?: return null
        val address =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    runCatching {
                        geocoder.getFromLocation(
                            location.latitude,
                            location.longitude,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: List<Address>) {
                                    if (continuation.isActive)
                                        continuation.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            },
                        )
                    }
                        .onFailure {
                            if (continuation.isActive) continuation.resume(null)
                        }
                }
            } else {
                // A blocking Binder call may ignore interruption. Do not make the caller
                // wait for that worker after timeout, or queue more calls behind it.
                suspendCancellableCoroutine { continuation ->
                    runCatching {
                        legacyGeocoderExecutor.submit {
                            @Suppress("DEPRECATION")
                            val result = runCatching {
                                geocoder
                                    .getFromLocation(location.latitude, location.longitude, 1)
                                    ?.firstOrNull()
                            }
                                .getOrNull()
                            if (continuation.isActive) continuation.resume(result)
                        }
                    }
                        .onSuccess { task ->
                            continuation.invokeOnCancellation { task.cancel(true) }
                        }
                        .onFailure {
                            if (continuation.isActive) continuation.resume(null)
                        }
                }
            }
        return address
    }

    private suspend fun fetchWeatherWithRetry(
        latitude: Double,
        longitude: Double,
        placeName: String,
        fullPlaceName: String? = null,
        locationSource: LocationSource,
    ): OutdoorSnapshot {
        val weather = fetchRawWeatherWithRetry(latitude, longitude)
        return OutdoorSnapshot(
                placeName = placeName,
                fullPlaceName = fullPlaceName,
                locationSource = locationSource,
                temperatureCelsius = weather.temperatureCelsius,
                weatherCode = weather.weatherCode,
                capturedAt = System.currentTimeMillis(),
            )
            .normalizedOrNull() ?: throw OutdoorContextException(localizedText("天气服务返回了无法识别的数据。"))
    }

    private suspend fun fetchRawWeatherWithRetry(
        latitude: Double,
        longitude: Double,
    ): RawWeather {
        var lastError: Exception? = null
        repeat(WEATHER_MAX_ATTEMPTS) { attempt ->
            try {
                return fetchRawWeather(latitude, longitude)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                if (attempt + 1 < WEATHER_MAX_ATTEMPTS) {
                    delay(WEATHER_RETRY_DELAY_MILLIS)
                }
            }
        }
        throw lastError ?: OutdoorContextException(localizedText("无法连接天气服务，请检查网络后重试。"))
    }

    private suspend fun fetchRawWeather(
        latitude: Double,
        longitude: Double,
    ): RawWeather =
        withContext(Dispatchers.IO) {
            // Weather models do not benefit from street-level coordinates. Rounding here keeps
            // the outgoing request coarse even if a device provider supplies more precision.
            val coordinateFormat = "%.3f"
            val url = buildString {
                append("https://api.open-meteo.com/v1/forecast?latitude=")
                append(coordinateFormat.format(Locale.US, latitude))
                append("&longitude=")
                append(coordinateFormat.format(Locale.US, longitude))
                append("&current=temperature_2m,weather_code&timezone=auto&forecast_days=1")
            }
            val current =
                requestJson(url).optJSONObject("current")
                    ?: throw OutdoorContextException(localizedText("天气服务暂时没有返回当前天气。"))
            val temperature = current.optDouble("temperature_2m", Double.NaN)
            val weatherCode = current.optInt("weather_code", -1)
            if (!temperature.isFinite() || weatherCode < 0) {
                throw OutdoorContextException(localizedText("天气服务返回了无法识别的数据。"))
            }
            RawWeather(temperatureCelsius = temperature, weatherCode = weatherCode)
        }

    private fun searchCity(query: String): CityCoordinate {
        val locale = AppLocale.locale
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val response =
            requestJson(
                "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=${locale.language}&format=json"
            )
        val result =
            response.optJSONArray("results")?.optJSONObject(0)
                ?: throw OutdoorContextException(localizedText("没有找到这个城市，请换一种写法。"))
        val name = result.optString("name").trim()
        val admin = result.optString("admin1").trim()
        val label =
            listOf(admin, name).filter(String::isNotBlank).distinct().joinToString(" · ").ifBlank {
                query
            }
        return CityCoordinate(
                label = localizePlaceName(label, locale).take(MAX_PLACE_NAME_LENGTH),
                name =
                    localizePlaceName(name.ifBlank { query }, locale).take(MAX_PLACE_NAME_LENGTH),
                latitude = result.optDouble("latitude", Double.NaN),
                longitude = result.optDouble("longitude", Double.NaN),
            )
            .takeIf { it.latitude.isFinite() && it.longitude.isFinite() }
            ?: throw OutdoorContextException(localizedText("城市服务返回了无法识别的位置。"))
    }

    private fun requestJson(url: String): JSONObject {
        val connection =
            (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NETWORK_TIMEOUT_MILLIS
                readTimeout = NETWORK_TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Xike/${BuildConfig.VERSION_NAME}")
            }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw OutdoorContextException(localizedText("天气服务暂时不可用，请稍后重试。"))
            }
            val payload =
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(payload)
        } catch (error: OutdoorContextException) {
            throw error
        } catch (error: Exception) {
            throw OutdoorContextException(localizedText("无法连接天气服务，请检查网络后重试。"), error)
        } finally {
            connection.disconnect()
        }
    }

    private data class CityCoordinate(
        val label: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    private data class RawWeather(
        val temperatureCelsius: Double,
        val weatherCode: Int,
    )

    private data class PlaceLabel(val label: String, val fullLabel: String)

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 12_000L
        const val NETWORK_TIMEOUT_MILLIS = 10_000
        const val WEATHER_MAX_ATTEMPTS = 2
        const val WEATHER_RETRY_DELAY_MILLIS = 800L
        const val MAX_LOCATION_AGE_NANOS = 120_000_000_000L
        const val PREFERRED_ACCURACY_METERS = 100f
        val legacyGeocoderExecutor =
            ThreadPoolExecutor(
                0,
                1,
                30L,
                TimeUnit.SECONDS,
                SynchronousQueue(),
                { task -> Thread(task, "xike-geocoder").apply { isDaemon = true } },
            )
    }
}

private fun Address.asPlaceLabel(includeStreet: Boolean): String? {
    if (includeStreet) {
        val fullAddress =
            (0..maxAddressLineIndex)
                .mapNotNull { getAddressLine(it)?.trim()?.takeIf(String::isNotBlank) }
                .distinct()
                .joinToString(" · ")
        if (fullAddress.isNotBlank()) return fullAddress
    }
    val regions =
        listOf(countryName, adminArea, locality, subAdminArea, subLocality)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinctBy {
                when (it.removeSuffix("市")) {
                    "北京",
                    "上海",
                    "天津",
                    "重庆" -> it.removeSuffix("市")
                    else -> it
                }
            }
    val street =
        if (includeStreet) listOfNotNull(thoroughfare, subThoroughfare, premises, postalCode)
        else emptyList()
    return (regions + street)
        .mapNotNull { it.trim().takeIf(String::isNotBlank) }
        .distinct()
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
}
