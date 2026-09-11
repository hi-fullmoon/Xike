package com.xike.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalStoreTest {
    @Test
    fun `legacy single image field migrates to image list`() {
        val json = JSONObject()
            .put("id", "legacy-entry")
            .put("createdAt", 123L)
            .put("mood", "GOOD")
            .put("tags", JSONArray(listOf("工作")))
            .put("note", "旧数据")
            .put("imageFileName", "legacy.xike-image")

        val entry = JournalEntry.fromJson(json)

        assertEquals(listOf("legacy.xike-image"), entry.imageFileNames)
    }

    @Test
    fun `entry json round trip preserves encrypted audio metadata`() {
        val entry = JournalEntry(
            id = "with-audio",
            createdAt = 456L,
            mood = Mood.CALM,
            tags = listOf("自我"),
            note = "听见自己",
            audio = JournalAudio("voice.xike-audio", 42_500L),
        )

        assertEquals(entry, JournalEntry.fromJson(entry.toJson()))
    }

    @Test
    fun `restored entries remove duplicates missing images and overflow`() {
        val available = (1..12).map { "photo-$it.xike-image" }.toSet()
        val duplicate = JournalEntry(
            id = "same-id",
            createdAt = 100L,
            mood = Mood.CALM,
            tags = emptyList(),
            note = "first",
            imageFileNames = listOf("photo-1.xike-image", "photo-1.xike-image", "missing.xike-image") +
                (2..12).map { "photo-$it.xike-image" },
        )
        val ignoredDuplicate = duplicate.copy(createdAt = 200L, note = "second")
        val newer = duplicate.copy(id = "newer", createdAt = 300L, imageFileNames = emptyList())

        val restored = normalizeRestoredEntries(listOf(duplicate, ignoredDuplicate, newer), available)

        assertEquals(listOf("newer", "same-id"), restored.map { it.id })
        assertEquals(9, restored.last().imageFileNames.size)
        assertEquals(9, restored.last().imageFileNames.distinct().size)
        assertTrue("missing.xike-image" !in restored.last().imageFileNames)
    }

    @Test
    fun `restored entries only retain available valid audio`() {
        val availableAudio = setOf("available.xike-audio")
        val entry = JournalEntry(
            id = "audio-entry",
            createdAt = 100L,
            mood = Mood.GOOD,
            tags = emptyList(),
            note = "voice",
            audio = JournalAudio("available.xike-audio", 12_000L),
        )

        val restored = normalizeRestoredEntries(emptyList<JournalEntry>() + entry, emptySet(), availableAudio)
        val missing = normalizeRestoredEntries(listOf(entry), emptySet(), emptySet())

        assertEquals(entry.audio, restored.single().audio)
        assertEquals(null, missing.single().audio)
    }

    @Test
    fun `legacy cipher round trips and rejects a wrong password`() {
        val encrypted = BackupCipher.encryptLegacy("一段加密日记", "correct-password")

        assertEquals("一段加密日记", BackupCipher.decryptLegacy(encrypted, "correct-password"))
        assertThrows(Exception::class.java) {
            BackupCipher.decryptLegacy(encrypted, "wrong-password")
        }
    }

    @Test
    fun `streaming cipher writes a recognizable header and round trips`() {
        val encrypted = ByteArrayOutputStream()
        BackupCipher.encryptingStream(encrypted, "stream-password").use { output ->
            output.write("streamed backup".toByteArray())
        }
        val bytes = encrypted.toByteArray()
        val header = bytes.copyOfRange(0, BackupCipher.magicSize)
        val source = ByteArrayInputStream(bytes, BackupCipher.magicSize, bytes.size - BackupCipher.magicSize)

        assertTrue(BackupCipher.hasStreamingMagic(header))
        val restored = BackupCipher.decryptingStream(source, "stream-password").use { it.readBytes() }
        assertEquals("streamed backup", restored.toString(Charsets.UTF_8))
    }
}
