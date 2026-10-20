package com.xike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.hutool.core.util.CoordinateUtil
import com.xike.app.data.model.LocationSource
import com.xike.app.outdoor.OutdoorContextRepository
import com.xike.app.outdoor.PlaceLabels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real weather requests for public reference coordinates; no SDK responses or journals created. */
@RunWith(AndroidJUnit4::class)
class AmapWeatherTest {
    @Test
    fun domesticCoordinatesFetchWeatherWithoutSearchingAdministrativeCityNames() = runBlocking {
        val repository =
            OutdoorContextRepository(ApplicationProvider.getApplicationContext<Context>())
        // Public Hutool conversion reference and Open-Meteo's Hangzhou reference coordinates.
        val beijing =
            repository.weatherAt(
                39.915,
                116.404,
                wgs84 = false,
                PlaceLabels("北京市", "中国 · 北京市"),
            )
        assertEquals("北京市", beijing.placeName)
        assertEquals(LocationSource.AMAP, beijing.locationSource)
        assertEquals("Open-Meteo", beijing.source)
        assertEquals("中国 · 北京市", beijing.fullPlaceName)
        assertTrue(beijing.temperatureCelsius.isFinite())
        val hangzhouGcj = CoordinateUtil.wgs84ToGcj02(120.16142, 30.29365)
        val hangzhou =
            repository.weatherAt(
                hangzhouGcj.lat,
                hangzhouGcj.lng,
                wgs84 = false,
                PlaceLabels("杭州市", "中国 · 浙江省 · 杭州市"),
            )
        assertEquals("杭州市", hangzhou.placeName)
        assertTrue(hangzhou.temperatureCelsius.isFinite())
    }

    @Test
    fun wgs84WeatherPreservesEnglishAddressText() = runBlocking {
        val repository =
            OutdoorContextRepository(ApplicationProvider.getApplicationContext<Context>())
        val weather =
            repository.weatherAt(
                30.29365,
                120.16142,
                wgs84 = true,
                PlaceLabels("Hangzhou", "Zhejiang · Hangzhou"),
            )
        assertEquals("Hangzhou", weather.placeName)
        assertEquals(LocationSource.AMAP, weather.locationSource)
        assertEquals("Zhejiang · Hangzhou", weather.fullPlaceName)
        assertTrue(weather.temperatureCelsius.isFinite())
    }
}
