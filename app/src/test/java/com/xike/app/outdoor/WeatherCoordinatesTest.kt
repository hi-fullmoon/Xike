package com.xike.app.outdoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WeatherCoordinatesTest {
    @Test
    fun `domestic GCJ coordinates use WGS84 rather than a city search reference`() {
        // Public reference pair from Hutool's CoordinateUtilTest, not a device location.
        // https://github.com/chinabugotech/hutool/blob/v5-master/hutool-core/src/test/java/cn/hutool/core/util/CoordinateUtilTest.java
        val converted = weatherCoordinates(39.915, 116.404, wgs84 = false)
        assertEquals(39.91359571849836, converted.latitude, 0.000001)
        assertEquals(116.39775550083061, converted.longitude, 0.000001)
    }

    @Test
    fun `WGS84 in mainland China is not shifted a second time`() {
        val coordinates = weatherCoordinates(30.29365, 120.16142, wgs84 = true)
        assertEquals(30.29365, coordinates.latitude, 0.0)
        assertEquals(120.16142, coordinates.longitude, 0.0)
    }

    @Test
    fun `overseas coordinates are not shifted`() {
        val coordinates = weatherCoordinates(51.50853, -0.12574, wgs84 = false)
        assertEquals(51.50853, coordinates.latitude, 0.0)
        assertEquals(-0.12574, coordinates.longitude, 0.0)
    }

    @Test
    fun `invalid coordinates fail before any weather request`() {
        for ((latitude, longitude) in
            listOf(
                Double.NaN to 116.404,
                39.915 to Double.POSITIVE_INFINITY,
                91.0 to 116.404,
                39.915 to -181.0,
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                weatherCoordinates(latitude, longitude, wgs84 = false)
            }
        }
    }
}
