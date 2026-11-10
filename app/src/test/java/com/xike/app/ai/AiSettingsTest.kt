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
}
