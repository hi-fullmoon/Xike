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
        val nonce = Regex("<script nonce=\"([^\"]+)\">").find(html)!!.groupValues[1]
        assertTrue(html.contains("script-src 'nonce-$nonce'"))
        assertTrue(html.contains("href=\"images/photo.png\""))
    }

    @Test
    fun mediaFileNamesAreEncodedAsUrlPaths() {
        val html = BackupHtml.render(listOf(JournalEntry(
            createdAt = 1_700_000_000_000L,
            mood = Mood.CALM,
            tags = emptyList(),
            note = "",
            imageFileNames = listOf("照片 #1%?.png"),
            audio = JournalAudio("录音 #1%?.m4a", 2_000L),
        )))
        val imageUrl = "images/%E7%85%A7%E7%89%87%20%231%25%3F.png"
        assertTrue(html.contains("href=\"$imageUrl\""))
        assertTrue(html.contains("src=\"$imageUrl\""))
        assertTrue(html.contains("src=\"audios/%E5%BD%95%E9%9F%B3%20%231%25%3F.m4a\""))
    }

    @Test
    fun photoGroupsKeepEveryAttachmentAndTheirOwnCount() {
        for (count in 1..10) {
            val names = (1..count).map { "photo-$it.png" }
            val html = BackupHtml.render(listOf(JournalEntry(
                createdAt = 1_700_000_000_000L,
                mood = Mood.CALM,
                tags = emptyList(),
                note = "",
                imageFileNames = names,
            )))
            assertTrue(html.contains("class=\"photos\" data-count=\"$count\""))
            names.forEach { assertTrue(html.contains("href=\"images/$it\"")) }
            assertTrue(html.contains("<dialog class=\"viewer\""))
        }
    }
}
