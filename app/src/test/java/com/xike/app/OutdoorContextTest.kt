package com.xike.app

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutdoorContextTest {
    @Test
    fun `snapshot json round trip preserves normalized values`() {
        val snapshot = OutdoorSnapshot(
            placeName = "上海 · 浦东",
            temperatureCelsius = 27.2,
            weatherCode = 2,
            capturedAt = 1_700_000_000_000L,
        )

        assertEquals(snapshot, OutdoorSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun `invalid snapshot is rejected`() {
        assertNull(OutdoorSnapshot("", 27.0, 2, 100L).normalizedOrNull())
        assertNull(OutdoorSnapshot("上海", Double.NaN, 2, 100L).normalizedOrNull())
        assertNull(OutdoorSnapshot("上海", 27.0, -1, 100L).normalizedOrNull())
        assertNull(OutdoorSnapshot("上海", 27.0, 2, 0L).normalizedOrNull())
    }

    @Test
    fun `weather codes map to stable Chinese labels`() {
        assertEquals("晴朗", weatherConditionLabel(0))
        assertEquals("有雨", weatherConditionLabel(63))
        assertEquals("雷雨伴冰雹", weatherConditionLabel(99))
        assertEquals("天气未知", weatherConditionLabel(100))
    }

    @Test
    fun `slow first provider does not block a later location result`() = runBlocking {
        val result = withTimeout(1_000L) {
            firstNonNullResult(listOf("network", "gps")) { provider ->
                if (provider == "network") awaitCancellation() else "gps-location"
            }
        }

        assertEquals("gps-location", result)
    }

    @Test
    fun `editing within a date retains outdoor snapshot while crossing dates removes it`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val original = LocalDate.of(2026, 8, 26).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val sameDate = LocalDate.of(2026, 8, 26).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        val nextDate = LocalDate.of(2026, 8, 27).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val snapshot = OutdoorSnapshot("上海", 28.0, 1, original)

        assertEquals(snapshot, retainOutdoorForEditedTime(snapshot, original, sameDate, zone))
        assertNull(retainOutdoorForEditedTime(snapshot, original, nextDate, zone))
    }
}
