package com.xike.app

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
}
