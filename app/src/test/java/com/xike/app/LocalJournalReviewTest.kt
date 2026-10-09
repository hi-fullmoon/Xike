package com.xike.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalJournalReviewTest {
    @Test
    fun `comparison respects changes across displayed percentage boundaries`() {
        assertEquals(-1, reviewMoodShareChange(2.0 / 13, 5.0 / 32))
        assertEquals(1, reviewMoodShareChange(11.0 / 13, 27.0 / 32))
    }

    @Test
    fun `equal displayed percentages report no change`() {
        assertEquals(0, reviewMoodShareChange(0.333, 0.334))
        assertEquals(0, reviewMoodShareChange(1.0, 1.0))
        assertEquals(0, reviewMoodShareChange(0.0, 0.0))
    }

    @Test
    fun `comparison direction and magnitude match displayed percentages`() {
        assertEquals(18, reviewMoodShareChange(0.505, 0.334))
        assertEquals(-18, reviewMoodShareChange(0.334, 0.505))
    }
}
