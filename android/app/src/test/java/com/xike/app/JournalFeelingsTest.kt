package com.xike.app

import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.displayMoodLabel
import com.xike.app.data.model.JournalDraft
import com.xike.app.data.model.parseJournalDraft
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JournalFeelingsTest {
    @Test
    fun feelingsOnlyDraftSurvivesSerializationAndIsNotEmpty() {
        val draft = JournalDraft(feelings = listOf("HOPEFUL", "ANXIOUS"), updatedAt = 12L)
        assertFalse(draft.isEmpty)
        assertEquals(draft, parseJournalDraft(draft.toJson().toString()))
        assertTrue(JournalDraft(feelings = listOf(" "), updatedAt = 12L).normalized().isEmpty)
        assertEquals(
            0L,
            JournalDraft(feelings = listOf(" "), updatedAt = 12L).normalized().updatedAt,
        )
    }

    @Test
    fun backupEntryRoundTripPreservesFeelingsScaleAndUnknownKeys() {
        val entry =
            JournalEntry(
                mood = Mood.ABOVE_AVERAGE,
                tags = emptyList(),
                note = "",
                feelings = listOf("HOPEFUL", "ANXIOUS", "FUTURE_FEELING"),
                moodScaleVersion = 1,
            )
        assertEquals(entry, JournalEntry.fromJson(JSONObject(entry.toJson().toString())))
        assertEquals(
            entry,
            JournalEntryRecord(entry.toBundle().entry, emptyList(), emptyList(), emptyList())
                .toJournalEntry(),
        )
    }

    @Test
    fun oldBackupAndDraftKeepOriginalMoodMeaning() {
        val json = JournalEntry(mood = Mood.TIRED, tags = emptyList(), note = "").toJson()
        json.remove("feelings")
        json.remove("moodScaleVersion")
        val old = JournalEntry.fromJson(json)
        assertEquals("疲惫", old.displayMoodLabel)
        assertTrue(old.feelings.isEmpty())
        assertEquals("不太好", old.copy(moodScaleVersion = 1).displayMoodLabel)
        assertEquals(0, parseJournalDraft("{\"mood\":\"TIRED\"}").moodScaleVersion)
    }

    @Test
    fun previewBackupAndDraftUpgradeToIndependentMoodIdentifiers() {
        val preview =
            JournalEntry(mood = Mood.TIRED, tags = emptyList(), note = "", moodScaleVersion = 1)
        val upgraded = JournalEntry.fromJson(preview.toJson())
        assertEquals(Mood.BELOW_AVERAGE, upgraded.mood)
        assertEquals("不太好", upgraded.mood.label)
        assertEquals(
            Mood.BELOW_AVERAGE,
            parseJournalDraft(
                    JournalDraft(mood = Mood.TIRED, moodScaleVersion = 1).toJson().toString()
                )
                .mood,
        )
        assertEquals(
            Mood.BELOW_AVERAGE,
            JournalAnalyticsRecord("new", 1L, "TIRED", 1, emptyList()).toEntry().mood,
        )
    }

    @Test
    fun filteringAndStatisticsKeepLegacyFatigueSeparateFromNewOverallMood() {
        val old = JournalEntry(id = "old", mood = Mood.TIRED, tags = emptyList(), note = "")
        val current = old.copy(id = "current", mood = Mood.BELOW_AVERAGE, moodScaleVersion = 1)
        val entries = listOf(old, current)
        assertEquals(
            listOf(old),
            filterJournalEntries(entries, JournalSearchQuery(moods = setOf(Mood.TIRED))),
        )
        assertEquals(
            listOf(current),
            filterJournalEntries(entries, JournalSearchQuery(moods = setOf(Mood.BELOW_AVERAGE))),
        )
        val summary =
            com.xike.app.feature.insights.journalPeriodSummary(
                entries,
                com.xike.app.feature.insights.InsightsPeriod.YEAR,
                java.time.LocalDate.now(),
                java.time.ZoneId.systemDefault(),
            )
        assertEquals(
            setOf("疲惫", "不太好"),
            summary.moodDistribution.filter { it.entryCount > 0 }.map { it.mood.label }.toSet(),
        )
    }
}
