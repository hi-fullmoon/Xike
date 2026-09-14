package com.xike.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupHtmlTest {
    @Test
    fun renderedReadingCopyEscapesJournalContentAndKeepsOfflineMediaLinks() {
        val entry = JournalEntry(
            createdAt = 1_700_000_000_000L,
            mood = Mood.CALM,
            tags = listOf("<script>alert(1)</script>"),
            note = "今天 <b>还好</b> & 明天",
            imageFileNames = listOf("photo.png"),
            audio = JournalAudio("voice.m4a", 2_000L),
        )

        val html = BackupHtml.render(listOf(entry))

        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("今天 &lt;b&gt;还好&lt;/b&gt; &amp; 明天"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("src=\"images/photo.png\""))
        assertTrue(html.contains("src=\"audios/voice.m4a\""))
        assertTrue(html.contains("default-src 'none'"))
    }
}
