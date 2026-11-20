package com.xike.app

import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.model.JournalDraft
import com.xike.app.data.model.LocationSource
import com.xike.app.data.model.OutdoorSnapshot
import com.xike.app.data.model.retainOutdoorForEditedTime
import com.xike.app.outdoor.firstNonNullResult
import com.xike.app.outdoor.locationSourceName
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutdoorContextTest {
    @Test
    fun `location providers survive snapshot draft and backup json independently of weather`() {
        for (provider in LocationSource.entries) {
            val snapshot = OutdoorSnapshot("杭州", 20.0, 1, 123L, locationSource = provider)
            val restored = OutdoorSnapshot.fromJson(snapshot.toJson())!!
            assertEquals(provider, restored.locationSource)
            assertEquals("Open-Meteo", restored.source)
            val draft = JournalDraft(outdoor = snapshot, updatedAt = 123L)
            assertEquals(provider, JournalDraft.fromJson(draft.toJson()).outdoor!!.locationSource)
            val entry =
                JournalEntry(
                    id = "source-round-trip",
                    mood = Mood.CALM,
                    tags = emptyList(),
                    note = "",
                    outdoor = snapshot,
                )
            assertEquals(provider, JournalEntry.fromJson(entry.toJson()).outdoor!!.locationSource)
        }
    }

    @Test
    fun `legacy and unrecognized location sources remain unknown instead of guessing system`() {
        val legacy = OutdoorSnapshot("杭州", 20.0, 1, 123L).toJson()
        assertNull(OutdoorSnapshot.fromJson(legacy)!!.locationSource)
        legacy.put("locationSource", "future-provider")
        assertNull(OutdoorSnapshot.fromJson(legacy)!!.locationSource)
        assertEquals("Open-Meteo", OutdoorSnapshot.fromJson(legacy)!!.source)
    }

    @Test
    fun `location source display labels follow the requested language`() {
        assertEquals("高德定位", locationSourceName(LocationSource.AMAP, Locale.CHINESE))
        assertEquals("系统定位", locationSourceName(LocationSource.SYSTEM, Locale.CHINESE))
        assertEquals("手动选择", locationSourceName(LocationSource.MANUAL, Locale.CHINESE))
        assertEquals("AMap", locationSourceName(LocationSource.AMAP, Locale.ENGLISH))
        assertEquals("System location", locationSourceName(LocationSource.SYSTEM, Locale.ENGLISH))
        assertEquals("Manually selected", locationSourceName(LocationSource.MANUAL, Locale.ENGLISH))
    }

    @Test
    fun `snapshot json round trip preserves normalized values`() {
        val snapshot =
            OutdoorSnapshot(
                placeName = "上海 · 浦东新区 · 世纪大道",
                temperatureCelsius = 27.2,
                weatherCode = 2,
                capturedAt = 1_700_000_000_000L,
            )

        val json = snapshot.toJson()
        assertEquals(snapshot, OutdoorSnapshot.fromJson(json))
        assertEquals(
            setOf("placeName", "temperatureCelsius", "weatherCode", "capturedAt", "source"),
            json.keys().asSequence().toSet(),
        )
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
        val result =
            withTimeout(1_000L) {
                firstNonNullResult(listOf("network", "gps")) { provider ->
                    if (provider == "network") awaitCancellation() else "gps-location"
                }
            }

        assertEquals("gps-location", result)
    }

    @Test
    fun `selection waits for a preferred result instead of taking the first result`() =
        runBlocking {
            val result =
                firstNonNullResult(
                    listOf(500, 20),
                    timeoutMillis = 1_000L,
                    isPreferred = { it <= 100 },
                    isBetter = { candidate, previous -> candidate < previous },
                ) { value ->
                    if (value == 20) delay(20)
                    value
                }
            assertEquals(20, result)
        }

    @Test
    fun `timeout retains the best available result and cancels unfinished requests`() =
        runBlocking {
            var unfinishedCancelled = false
            val result =
                firstNonNullResult(
                    listOf(500, 200, null),
                    timeoutMillis = 100L,
                    isPreferred = { it <= 100 },
                    isBetter = { candidate, previous -> candidate < previous },
                ) { value ->
                    if (value == null) {
                        try {
                            awaitCancellation()
                        } finally {
                            unfinishedCancelled = true
                        }
                    } else value
                }
            assertEquals(200, result)
            assertTrue(unfinishedCancelled)
        }

    @Test
    fun `failed provider does not discard the other provider result`() = runBlocking {
        val result =
            firstNonNullResult(listOf(false, true)) { available ->
                check(available)
                available
            }
        assertEquals(true, result)
    }

    @Test
    fun `editing within a date retains outdoor snapshot while crossing dates removes it`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val original =
            LocalDate.of(2026, 8, 26).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val sameDate =
            LocalDate.of(2026, 8, 26).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        val nextDate =
            LocalDate.of(2026, 8, 27).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val snapshot = OutdoorSnapshot("上海", 28.0, 1, original)

        assertEquals(snapshot, retainOutdoorForEditedTime(snapshot, original, sameDate, zone))
        assertNull(retainOutdoorForEditedTime(snapshot, original, nextDate, zone))
    }
}
