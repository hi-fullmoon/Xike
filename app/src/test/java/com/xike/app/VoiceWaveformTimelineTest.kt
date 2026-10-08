package com.xike.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWaveformTimelineTest {
    @Test
    fun exact2625MillisBoundaryAcceptsAmplitudeOnce() {
        val before = VoiceWaveformTimeline().append(2624L, 0f)
        assertEquals(48L, before.lastSlot)
        assertFalse(before.isSampleDue(2624L))
        assertEquals(1L, before.nextSampleDelay(2624L))
        assertTrue(before.isSampleDue(2625L))
        val sampled = before.append(2625L, 1f)
        assertEquals(49L, sampled.lastSlot)
        assertEquals(0.72f, requireNotNull(sampled.values.last()), 0.0001f)
        assertEquals(0f, sampled.offset(2625L), 0f)
        assertFalse(sampled.isSampleDue(2625L))
    }

    @Test
    fun everyBoundaryWithinFiveMinutesAgreesWithSamplingDeadline() {
        var timeline = VoiceWaveformTimeline()
        for (slot in 1L..5600L) {
            val deadline = (slot * 375L + 6L) / 7L
            assertFalse("Early read for slot $slot", timeline.isSampleDue(deadline - 1L))
            assertEquals(1L, timeline.nextSampleDelay(deadline - 1L))
            assertTrue("Missing read for slot $slot", timeline.isSampleDue(deadline))
            timeline = timeline.append(deadline, 0f)
            assertEquals(slot, timeline.lastSlot)
            assertFalse(timeline.isSampleDue(deadline))
            assertTrue(timeline.offset(deadline) < 1f)
        }
    }

    @Test
    fun selectedSpeedAdvancesFourteenSlotsIn750Millis() {
        val timeline = VoiceWaveformTimeline().append(750L, 1f)
        assertEquals(14L, timeline.lastSlot)
        assertEquals(0f, timeline.offset(750L), 0.0001f)
        assertEquals(54L, VoiceWaveformTimeline().nextSampleDelay(0L))
    }

    @Test
    fun delayedReadPreservesTimeAndMarksUnreadSlots() {
        val first = VoiceWaveformTimeline().append(54L, 1f)
        val delayed = first.append(300L, 0.5f)
        assertEquals(5L, delayed.lastSlot)
        assertEquals(first.values.last(), delayed.values[251])
        for (index in 252..254) assertNull(delayed.values[index])
        assertEquals(0.6f, delayed.offset(300L), 0.0001f)
        // The same historical sample moves four slots across a delayed read.
        assertEquals(first.offset(300L), 4f + delayed.offset(300L), 0.0001f)
    }

    @Test
    fun longStallKeepsHistoryBoundedWithoutCopyingAmplitudeIntoGap() {
        val timeline = VoiceWaveformTimeline().append(300_000L, 1f)
        assertEquals(256, timeline.values.size)
        assertTrue(timeline.values.dropLast(1).all { it == null })
        assertEquals(0.72f, requireNotNull(timeline.values.last()), 0.0001f)
    }

    @Test
    fun unchangedActiveDurationFreezesTimelineAndScrollPosition() {
        val timeline = VoiceWaveformTimeline().append(54L, 0.5f)
        assertEquals(timeline, timeline.append(54L, 1f))
        assertEquals(0.008f, timeline.offset(54L), 0.0001f)
    }
}
