package com.xike.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalDraftTest {
    @Test
    fun `draft round trip preserves mood note tags images and update time`() {
        val draft = JournalDraft(
            mood = Mood.GOOD,
            note = "今天完成了重要的事",
            tags = linkedSetOf("工作", "学习"),
            imageUriStrings = listOf("content://photo/1", "content://photo/2"),
            recordedAt = 120_000L,
            updatedAt = 123_456L,
        )

        assertEquals(draft, parseJournalDraft(draft.toJson().toString()))
    }

    @Test
    fun `draft normalization limits note and removes duplicate images`() {
        val normalized = JournalDraft(
            note = "心".repeat(MAX_DRAFT_NOTE_LENGTH + 20),
            imageUriStrings = List(MAX_IMAGES_PER_ENTRY + 3) { "content://photo/${it % 9}" },
            updatedAt = 10L,
        ).normalized()

        assertEquals(MAX_DRAFT_NOTE_LENGTH, normalized.note.length)
        assertEquals(MAX_IMAGES_PER_ENTRY, normalized.imageUriStrings.size)
    }

    @Test
    fun `empty draft resets update time`() {
        val draft = JournalDraft(updatedAt = 99L).normalized()

        assertTrue(draft.isEmpty)
        assertEquals(0L, draft.updatedAt)
    }

    @Test
    fun `recorded time keeps an otherwise empty draft recoverable`() {
        val draft = JournalDraft(recordedAt = 123_456L, updatedAt = 99L).normalized()

        assertTrue(!draft.isEmpty)
        assertEquals(123_456L, draft.recordedAt)
        assertEquals(99L, draft.updatedAt)
    }

    @Test
    fun `invalid recorded time is removed during normalization`() {
        val draft = JournalDraft(recordedAt = 0L, updatedAt = 99L).normalized()

        assertTrue(draft.isEmpty)
        assertEquals(null, draft.recordedAt)
        assertEquals(0L, draft.updatedAt)
    }

    @Test
    fun `backdated draft removes current outdoor snapshot`() {
        val draft = JournalDraft(
            recordedAt = 123_456L,
            outdoor = OutdoorSnapshot("杭州", 26.0, 1, 120_000L),
            updatedAt = 99L,
        ).normalized()

        assertEquals(null, draft.outdoor)
        assertEquals(123_456L, draft.recordedAt)
    }

    @Test
    fun `draft round trip preserves outdoor snapshot`() {
        val draft = JournalDraft(
            mood = Mood.CALM,
            outdoor = OutdoorSnapshot("上海 · 浦东", 27.2, 2, 119_000L),
            updatedAt = 123_456L,
        )

        assertEquals(draft, parseJournalDraft(draft.toJson().toString()))
    }
}
