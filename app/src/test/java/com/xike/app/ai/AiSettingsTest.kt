package com.xike.app.ai

import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiSettingsTest {
    @Test
    fun `local preview does not require credentials consent or enablement`() {
        val settings = AiSettings(address = "https://api.deepseek.com/v1", model = "deepseek-flash")
        assertTrue(settings.canPreview)
        assertTrue(settings.isValid())
        assertFalse(settings.configured)
        assertFalse(settings.canUse)
        assertFalse(settings.copy(model = "").canPreview)
        assertFalse(settings.copy(address = "").canPreview)
        assertFalse(settings.copy(address = "http://api.deepseek.com").canPreview)
        assertFalse(settings.copy(enabled = true, consent = true).isValid())
    }

    @Test
    fun `base and full HTTPS endpoints preserve deployment paths`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            AiSettings(address = "https://api.deepseek.com").endpoint().toString(),
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiSettings(address = "https://api.deepseek.com/v1/").endpoint().toString(),
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiSettings(address = "https://api.deepseek.com/v1/chat/completions/")
                .endpoint()
                .toString(),
        )
    }

    @Test
    fun `credentials apply only to the same resolved endpoint`() {
        val original =
            AiSettings(
                address = "https://api.deepseek.com/v1",
                apiKey = UUID.randomUUID().toString(),
                model = "deepseek-flash",
                enabled = true,
                consent = true,
            )
        val sameEndpoint = original.withAddress("https://api.deepseek.com/v1/chat/completions/")
        assertTrue(sameEndpoint.canUse)
        assertTrue(sameEndpoint.apiKey == original.apiKey)

        listOf(
                "https://api.deepseek.com",
                "https://api.openai.com/v1",
                "http://api.deepseek.com/v1",
            )
            .forEach { address ->
                val changed = original.withAddress(address)
                assertTrue(changed.apiKey.isEmpty())
                assertFalse(changed.consent)
                assertFalse(changed.enabled)
                assertFalse(changed.copy(consent = true).configured)
                assertTrue(changed.withAddress(original.address).apiKey.isEmpty())
            }
    }

    @Test
    fun `insecure and ambiguous destinations are rejected`() {
        listOf(
                "http://api.deepseek.com",
                "https://user:secret@api.deepseek.com/v1",
                "https://api.deepseek.com/v1?key=secret",
                "https://api.deepseek.com/v1#fragment",
                "invalid",
            )
            .forEach {
                assertNull(AiSettings(address = it).endpoint())
                assertFalse(AiSettings(address = it).isValid())
            }
    }

    @Test
    fun `local configuration requires consent and explicit enablement`() {
        val value =
            AiSettings(
                "https://api.deepseek.com/v1",
                UUID.randomUUID().toString(),
                "deepseek-flash",
            )
        assertTrue(value.configured)
        assertFalse(value.canUse)
        assertFalse(value.copy(enabled = true).isValid())
        assertTrue(value.copy(enabled = true, consent = true).canUse)
        assertFalse(value.copy(apiKey = "").configured)
        assertFalse(value.copy(model = "").configured)
        assertFalse(value.copy(apiKey = "\n").isValid())
        assertFalse(value.copy(apiKey = "key\r\nheader").isValid())
    }

    @Test
    fun `connection test contains only fixed test text and model and never credentials`() {
        val settings =
            AiSettings("https://api.deepseek.com", UUID.randomUUID().toString(), "deepseek-flash")
        val body = aiRequest(settings, null)
        val json = JSONObject(body)
        assertEquals(settings.model, json.getString("model"))
        assertFalse(json.getBoolean("stream"))
        assertFalse(body.contains(settings.apiKey))
        assertFalse(body.contains(settings.address))
        assertEquals(
            "Connection test.",
            json.getJSONArray("messages").getJSONObject(1).getString("content"),
        )
        assertFalse(settings.toString().contains(settings.apiKey))
        assertFalse(settings.toString().contains(settings.address))
    }

    @Test
    fun `empty library cannot produce an analysis request`() {
        val summary =
            com.xike.app.feature.insights.journalPeriodSummary(
                emptyList(),
                com.xike.app.feature.insights.InsightsPeriod.WEEK,
            )
        assertThrows(IllegalArgumentException::class.java) {
            aiReviewInput(emptyList(), summary, false)
        }
    }

    @Test
    fun `malformed and ungrounded responses cannot create a review`() {
        val empty = AiReviewInput("", emptyList())
        listOf("{}", "[]", "not JSON").forEach { content ->
            assertTrue(runCatching { parseAiReview(content, empty) }.isFailure)
        }
    }

    private val twoSources =
        AiReviewInput("", listOf(AiSource(1, "a", "2026-11-01"), AiSource(2, "b", "2026-11-02")))

    private fun observation(title: Any, text: Any, vararg references: Any) =
        JSONObject()
            .put("title", title)
            .put("text", text)
            .put("references", org.json.JSONArray(references.toList()))

    @Test
    fun `one malformed observation does not discard the grounded ones`() {
        val response =
            JSONObject()
                .put(
                    "observations",
                    org.json
                        .JSONArray()
                        .put(observation("Kept", "Grounded", 1, 99, 1))
                        .put(observation("Unknown citation", "Dropped", 99))
                        .put(observation(7, "Non-string title is dropped", 2))
                        .put(observation("x".repeat(121), "Overlong title is dropped", 2))
                        .put(observation("Second", "Also kept", 2.0, 1.5)),
                )
                .put("questions", org.json.JSONArray().put("Q1").put(JSONObject.NULL).put(" "))
        val review = parseAiReview(response.toString(), twoSources)
        assertEquals(listOf("Kept", "Second"), review.observations.map { it.title })
        assertEquals(listOf(1), review.observations[0].references)
        assertEquals(listOf(2), review.observations[1].references)
        assertEquals(listOf("Q1"), review.questions)
    }

    @Test
    fun `extra items are capped and missing questions are allowed`() {
        val observations = org.json.JSONArray()
        repeat(6) { observations.put(observation("T$it", "Text", 1, 2, 1, 2, 1, 2)) }
        val questions = org.json.JSONArray()
        repeat(5) { questions.put("Q$it") }
        val review =
            parseAiReview(
                JSONObject()
                    .put("observations", observations)
                    .put("questions", questions)
                    .toString(),
                twoSources,
            )
        assertEquals(AI_MAX_OBSERVATIONS, review.observations.size)
        assertEquals(listOf(1, 2), review.observations.first().references)
        assertEquals(AI_MAX_QUESTIONS, review.questions.size)

        val withoutQuestions =
            parseAiReview(
                JSONObject()
                    .put("observations", org.json.JSONArray().put(observation("T", "Text", 1)))
                    .toString(),
                twoSources,
            )
        assertTrue(withoutQuestions.questions.isEmpty())
    }

    @Test
    fun `review without any grounded observation is rejected`() {
        val response =
            JSONObject()
                .put("observations", org.json.JSONArray().put(observation("T", "Text", 3)))
                .put("questions", org.json.JSONArray().put("Q"))
        assertThrows(IllegalArgumentException::class.java) {
            parseAiReview(response.toString(), twoSources)
        }
    }

    @Test
    fun `requests ask for JSON output including the connection test`() {
        val settings =
            AiSettings("https://api.deepseek.com", UUID.randomUUID().toString(), "deepseek-flash")
        val body = JSONObject(aiRequest(settings, null))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        val system = body.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system.contains("JSON"))
    }

    private fun completion(finishReason: Any?, content: Any?) =
        JSONObject()
            .put(
                "choices",
                org.json
                    .JSONArray()
                    .put(
                        JSONObject()
                            .put("finish_reason", finishReason ?: JSONObject.NULL)
                            .put("message", JSONObject().put("content", content ?: JSONObject.NULL))
                    ),
            )
            .toString()

    @Test
    fun `completion content rejects only truncation filtering and invalid shapes`() {
        assertEquals("{}", aiCompletionContent(completion("stop", "{}")))
        assertEquals("{}", aiCompletionContent(completion(null, "{}")))
        assertEquals("{}", aiCompletionContent(completion("eos", "{}")))
        listOf(
                completion("length", "{}"),
                completion("content_filter", "{}"),
                completion("stop", null),
                completion("stop", " "),
                completion("stop", 1),
                "{}",
                "not JSON",
            )
            .forEach { raw ->
                assertThrows(AiServiceException::class.java) { aiCompletionContent(raw) }
            }
    }

    @Test
    fun `payload too large maps to shortening the request`() {
        assertNotEquals(aiHttpError(500), aiHttpError(413))
        assertNotEquals(aiHttpError(400), aiHttpError(413))
    }
}
