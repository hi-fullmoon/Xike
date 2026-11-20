package com.xike.app

import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ai.aiReviewInput
import com.xike.app.ai.parseAiReview
import com.xike.app.feature.insights.InsightsPeriod
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AiReviewPayloadTest {
    @Test
    fun payloadUsesSelectedRecordsAndResponseRequiresStringFields() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("existingAiPreview") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = JournalReadRepository { JournalDatabase.get(context) }
        val summary = repository.insights(InsightsPeriod.WEEK, LocalDate.now())
        check(summary.entryCount > 0) { "Requires existing journal records this week" }
        val entries = repository.aiReviewEntries(summary.startDate, summary.endDate)
        val input = aiReviewInput(entries, summary, includeNotes = false)
        val distribution = JSONObject(input.json).getJSONArray("moodDistribution")
        check(
            (0 until distribution.length()).associate {
                val row = distribution.getJSONObject(it)
                row.getString("mood") to row.getInt("count")
            } == entries.groupingBy { it.mood.label }.eachCount()
        )

        val source = input.sources.first()
        val observation =
            JSONObject()
                .put("title", source.date)
                .put("text", source.date)
                .put("references", JSONArray().put(source.reference))
        val questions = JSONArray().put(source.date)
        val response =
            JSONObject()
                .put("observations", JSONArray().put(observation))
                .put("questions", questions)
        parseAiReview(response.toString(), input)

        observation.put("title", source.reference)
        assertThrows(IllegalArgumentException::class.java) {
            parseAiReview(response.toString(), input)
        }
        observation.put("title", source.date).put("text", JSONObject())
        assertThrows(IllegalArgumentException::class.java) {
            parseAiReview(response.toString(), input)
        }
        observation.put("text", source.date)
        questions.put(0, JSONObject.NULL)
        // An invalid question is dropped without discarding the grounded observation.
        val review = parseAiReview(response.toString(), input)
        assertEquals(1, review.observations.size)
        assertTrue(review.questions.isEmpty())
        Unit
    }
}
