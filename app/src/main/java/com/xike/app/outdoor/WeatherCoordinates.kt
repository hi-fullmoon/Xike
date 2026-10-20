package com.xike.app.outdoor

import cn.hutool.core.util.CoordinateUtil

/** Transient weather coordinates only; never included in journal or draft models. */
internal class WeatherCoordinates(val latitude: Double, val longitude: Double)

internal fun weatherCoordinates(
    latitude: Double,
    longitude: Double,
    wgs84: Boolean,
): WeatherCoordinates {
    require(latitude.isFinite() && latitude in -90.0..90.0)
    require(longitude.isFinite() && longitude in -180.0..180.0)
    if (wgs84 || CoordinateUtil.outOfChina(longitude, latitude)) {
        return WeatherCoordinates(latitude, longitude)
    }
    // Hutool's approximate inverse is sufficient for weather grids; fetchWeather still rounds
    // the outgoing WGS84 coordinates to three decimals. Do not use this for surveying/navigation.
    val converted = CoordinateUtil.gcj02ToWgs84(longitude, latitude)
    return WeatherCoordinates(converted.lat, converted.lng)
}
