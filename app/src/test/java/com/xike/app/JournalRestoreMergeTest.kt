package com.xike.app

import com.xike.app.data.*
import org.junit.Assert.*
import org.junit.Test

class JournalRestoreMergeTest {
    private val original =
        JournalEntry(
            id = "original",
            createdAt = 123L,
            mood = Mood.CALM,
            tags = listOf("工作"),
            note = "原记录",
        )

    private fun key(entry: JournalEntry) =
        restoreContentKey(
            entry,
            { error("No image expected") },
            { error("No audio expected") },
            { error("No video expected") },
        )

    private fun merge(local: List<JournalEntry>, incoming: List<JournalEntry>) =
        planMergedEntries(local, incoming, ::key, ::key)

    @Test
    fun identicalVersionsAreSkippedButIndependentIdentitiesRemain() {
        assertTrue(merge(listOf(original), listOf(original)).isEmpty())
        val independent = original.copy(id = "independent")
        assertEquals(listOf(independent), merge(listOf(original), listOf(independent)))
    }

    @Test
    fun conflictingVersionGetsStableIdentityAcrossRepeatedImportsAndExport() {
        val edited = original.copy(note = "修改后的记录")
        val addition = merge(listOf(edited), listOf(original)).single()
        assertNotEquals(original.id, addition.id)
        assertEquals(original.copy(id = addition.id), addition)
        assertTrue(merge(listOf(edited, addition), listOf(original)).isEmpty())
        assertTrue(merge(listOf(edited, addition), listOf(edited, addition)).isEmpty())
    }

    @Test
    fun editedConflictCopyAndOccupiedConflictIdentityAreNeverOverwritten() {
        val edited = original.copy(note = "本机修改")
        val copy = merge(listOf(edited), listOf(original)).single()
        val modifiedCopy = copy.copy(note = "副本再次修改")
        val next = merge(listOf(edited, modifiedCopy), listOf(original)).single()
        assertNotEquals(copy.id, next.id)
        assertTrue(merge(listOf(edited, modifiedCopy, next), listOf(original)).isEmpty())
    }

    @Test
    fun backupInternalDuplicatesAndConflictsArePreservedWithoutRepeatedCopies() {
        val alternate = original.copy(mood = Mood.JOYFUL, note = "另一版本")
        val entries = merge(emptyList(), listOf(original, original, alternate, alternate))
        assertEquals(2, entries.size)
        assertEquals(setOf(original.note, alternate.note), entries.map { it.note }.toSet())
        assertTrue(merge(entries, listOf(original, alternate)).isEmpty())
        val normalized =
            normalizeRestoredEntries(
                listOf(original, alternate),
                emptySet(),
                preserveConflicts = true,
            )
        assertEquals(2, normalized.size)
    }

    @Test
    fun existingConflictVersionIsSkippedAfterOriginalIsDeleted() {
        val conflict = merge(listOf(original.copy(note = "本机修改")), listOf(original)).single()
        assertTrue(merge(listOf(conflict), listOf(original)).isEmpty())
    }

    @Test
    fun deletingEarlierEditedConflictDoesNotDuplicateLaterVersion() {
        val edited = original.copy(note = "本机修改")
        val first = merge(listOf(edited), listOf(original)).single()
        val firstEdited = first.copy(note = "副本再次修改")
        val later = merge(listOf(edited, firstEdited), listOf(original)).single()
        assertTrue(merge(listOf(edited, later), listOf(original)).isEmpty())
        assertTrue(merge(listOf(later), listOf(original)).isEmpty())
    }

    @Test
    fun retainedVersionAfterManyDeletedSlotsIsStillRecognized() {
        val edited = original.copy(note = "本机修改")
        val retained = mutableListOf(edited)
        repeat(10) { index ->
            val copy = merge(retained, listOf(original)).single()
            retained += copy.copy(note = "副本修改 $index")
        }
        val latest = merge(retained, listOf(original)).single()
        assertTrue(merge(listOf(latest), listOf(original)).isEmpty())
    }

    @Test
    fun earlierBuildConflictIdentityRemainsRecognized() {
        val id =
            java.util.UUID.nameUUIDFromBytes(
                    "xike-restore-conflict:${original.id}:${key(original)}:0"
                        .toByteArray(Charsets.UTF_8)
                )
                .toString()
        assertTrue(merge(listOf(original.copy(id = id)), listOf(original)).isEmpty())
    }

    @Test
    fun earlierBuildConflictAfterDeletedSlotsIsStillSkipped() {
        val id =
            java.util.UUID.nameUUIDFromBytes(
                    "xike-restore-conflict:${original.id}:${key(original)}:7"
                        .toByteArray(Charsets.UTF_8)
                )
                .toString()
        assertTrue(merge(listOf(original.copy(id = id)), listOf(original)).isEmpty())
        assertTrue(
            merge(listOf(original.copy(note = "本机修改"), original.copy(id = id)), listOf(original))
                .isEmpty()
        )
    }

    @Test
    fun unrelatedVersionThreeIdentityIsNotCollapsedByMatchingContent() {
        val id = java.util.UUID.nameUUIDFromBytes("independent-record".toByteArray()).toString()
        assertEquals(listOf(original), merge(listOf(original.copy(id = id)), listOf(original)))
    }

    @Test
    fun changedMetadataIsAConflictAndLongUnicodeNotesAreSupported() {
        val changed =
            listOf(
                original.copy(createdAt = 124L),
                original.copy(mood = Mood.LOW),
                original.copy(tags = listOf("自我")),
                original.copy(note = "中文🙂".repeat(20_000)),
            )
        assertEquals(changed.size, merge(listOf(original), changed).size)
    }
}
