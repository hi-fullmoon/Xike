package com.xike.app

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.attachmentDigest
import com.xike.app.data.restoreContentKey
import com.xike.app.media.JournalVideoStore
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** App-created records and real microphone audio; each phase runs in a separate process. */
class BackupMergeProcessTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mergeConflictsRepeatImportReplaceAndUndoAcrossProcesses() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("mergeIsolated") == "true")
        val context = rule.activity.applicationContext
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(20_000) { !model.isLoading }
        val store = JournalStore(context).also { it.initialize() }
        val directory = File(context.cacheDir, "merge-process-validation").apply { mkdirs() }
        val backup = File(directory, "original.zip")
        val encrypted = File(directory, "original.xike")
        val state = context.getSharedPreferences("merge-process-validation", Context.MODE_PRIVATE)
        when (args.getString("mergePhase")) {
            "write" -> {
                check(store.entries().isEmpty() && model.draft.isEmpty && !backup.exists())
                val recorder = XikeAudioRecorder(context)
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
                recorder.start {}
                SystemClock.sleep(900)
                val source = recorder.stop()
                rule.runOnIdle { model.queueDraftAudio(source, 900L) }
                rule.waitUntil(15_000) { model.draft.audio != null && !model.isDraftAudioSaving }
                // Optional real camera media is imported through the normal draft workflow.
                args.getString("mergePhotoUri")?.let { uri ->
                    val capture = createCameraCaptureUri(context)
                    requireNotNull(context.contentResolver.openInputStream(Uri.parse(uri))).use {
                        input ->
                        requireNotNull(context.contentResolver.openOutputStream(capture))
                            .use(input::copyTo)
                    }
                    rule.runOnIdle { assertTrue(model.addDraftImages(listOf(capture))) }
                    rule.waitUntil(15_000) { model.draft.imageUriStrings.isNotEmpty() }
                }
                saveMoment(model, "备份合并验证：原版本")
                val original = store.entries().single()
                saveMoment(model, "备份合并验证：待找回记录")
                val missing = store.entries().first { it.id != original.id }
                backup.outputStream().use { store.writeBackup(it, null) }
                encrypted.outputStream().use { store.writeBackup(it, PASSWORD) }
                val edited = original.copy(note = "备份合并验证：本机修改版本", mood = Mood.JOYFUL)
                store.update(edited, edited.imageFileNames)
                store.delete(missing.id, refreshEntries = false)
                store.finalizeDelete(missing.id)
                rule.waitUntil(15_000) { model.libraryState.totalCount == 1 }
                saveMoment(model, "备份合并验证：备份后的新增记录")
                assertTrue(
                    state
                        .edit()
                        .putString("originalId", original.id)
                        .putString("missingId", missing.id)
                        .putString("originalKey", contentKey(store, original))
                        .putString("before", fingerprint(store))
                        .commit()
                )
                source.delete()
            }
            "merge" -> {
                assertEquals(state.getString("before", null), fingerprint(store))
                val before = store.entries()
                val merged = backup.inputStream().use { store.restoreBackup(it, null) }
                assertEquals(4, merged.size)
                before.forEach { assertEquals(it, merged.single { entry -> entry.id == it.id }) }
                assertTrue(merged.any { it.id == state.getString("missingId", null) })
                val conflict = merged.single {
                    contentKey(store, it) == state.getString("originalKey", null)
                }
                assertNotEquals(state.getString("originalId", null), conflict.id)
                assertTrue(
                    state
                        .edit()
                        .putString("merged", fingerprint(store))
                        .putString("conflictId", conflict.id)
                        .commit()
                )
            }
            "repeat" -> {
                assertEquals(state.getString("merged", null), fingerprint(store))
                val filesBefore = mediaFiles(context)
                val undoBeforeImport =
                    context.filesDir
                        .listFiles()
                        .orEmpty()
                        .filter { it.name.startsWith("journal-restore-undo-") }
                        .map { it.name }
                        .toSet()
                encrypted.inputStream().use { store.restoreBackup(it, PASSWORD) }
                assertEquals(state.getString("merged", null), fingerprint(store))
                assertEquals(filesBefore, mediaFiles(context))
                assertEquals(
                    undoBeforeImport,
                    context.filesDir
                        .listFiles()
                        .orEmpty()
                        .filter { it.name.startsWith("journal-restore-undo-") }
                        .map { it.name }
                        .toSet(),
                )
                val conflict =
                    store.entries().single { it.id == state.getString("conflictId", null) }
                val audio = requireNotNull(conflict.audio)
                val file = File(context.filesDir, "journal-audios/${audio.fileName}")
                val held = File(directory, "held-audio")
                check(file.renameTo(held))
                val undoBefore = store.canUndoLastRestore()
                try {
                    assertTrue(
                        runCatching { backup.inputStream().use { store.restoreBackup(it, null) } }
                            .isFailure
                    )
                    assertEquals(undoBefore, store.canUndoLastRestore())
                } finally {
                    check(held.renameTo(file))
                }
                assertEquals(state.getString("merged", null), fingerprint(store))
            }
            "replace" -> {
                assertEquals(state.getString("merged", null), fingerprint(store))
                val replaced =
                    backup.inputStream().use {
                        store.restoreBackup(it, null, mode = BackupRestoreMode.REPLACE)
                    }
                assertEquals(2, replaced.size)
                assertTrue(
                    replaced.any {
                        it.id == state.getString("originalId", null) &&
                            contentKey(store, it) == state.getString("originalKey", null)
                    }
                )
                assertTrue(state.edit().putString("replaced", fingerprint(store)).commit())
            }
            "undo" -> {
                assertEquals(state.getString("replaced", null), fingerprint(store))
                store.undoLastRestore()
                assertEquals(state.getString("merged", null), fingerprint(store))
                assertFalse(store.canUndoLastRestore())
            }
            "verify" -> {
                assertEquals(state.getString("merged", null), fingerprint(store))
                val before = store.entries()
                val mergedBackup = File(directory, "merged.zip")
                mergedBackup.outputStream().use { store.writeBackup(it, null) }
                // A backup containing both versions remains idempotent after re-export.
                mergedBackup.inputStream().use { store.restoreBackup(it, null) }
                assertEquals(before.sortedBy { it.id }, store.entries().sortedBy { it.id })
                assertEquals(state.getString("merged", null), fingerprint(store))
            }
            "undoMerge" -> {
                // Return to the original local state, then verify undo of a merge that adds data.
                val originalId = checkNotNull(state.getString("originalId", null))
                store
                    .entries()
                    .filter {
                        it.id == state.getString("missingId", null) ||
                            it.id == state.getString("conflictId", null)
                    }
                    .forEach {
                        store.delete(it.id, refreshEntries = false)
                        store.finalizeDelete(it.id)
                    }
                assertEquals(state.getString("before", null), fingerprint(store))
                val local = store.entries().single { it.id == originalId }
                backup.inputStream().use { store.restoreBackup(it, null) }
                assertEquals(local, store.entries().single { it.id == originalId })
                assertEquals(state.getString("merged", null), fingerprint(store))
            }
            "verifyMergeUndo" -> {
                assertEquals(state.getString("merged", null), fingerprint(store))
                store.undoLastRestore()
                assertEquals(state.getString("before", null), fingerprint(store))
            }
            "finalRead" -> {
                assertEquals(state.getString("before", null), fingerprint(store))
                assertFalse(store.canUndoLastRestore())
            }
            "prepareGap" -> {
                backup.inputStream().use {
                    store.restoreBackup(it, null, mode = BackupRestoreMode.REPLACE)
                }
                val source = store.entries().single { it.id == state.getString("originalId", null) }
                store.update(
                    source.copy(note = source.note + " · 审核本机修改", mood = Mood.JOYFUL),
                    source.imageFileNames,
                )
                val missingId = requireNotNull(state.getString("missingId", null))
                store.delete(missingId)
                store.finalizeDelete(missingId)
                rule.waitUntil(15_000) { model.libraryState.totalCount == 1 }
                saveMoment(model, "审核边界：本机新增")
                assertTrue(state.edit().putString("before", fingerprint(store)).commit())
            }
            "collisionGap" -> {
                val first = store.entries().single { it.id == state.getString("conflictId", null) }
                store.update(first.copy(note = first.note + " · 副本修改"), first.imageFileNames)
                val before = store.entries().size
                backup.inputStream().use { store.restoreBackup(it, null) }
                assertEquals(before + 1, store.entries().size)
                val latest =
                    store.entries().single {
                        contentKey(store, it) == state.getString("originalKey", null)
                    }
                store.delete(first.id)
                store.finalizeDelete(first.id)
                val originalId = requireNotNull(state.getString("originalId", null))
                store.delete(originalId)
                store.finalizeDelete(originalId)
                val after = fingerprint(store)
                val files = mediaFiles(context)
                backup.inputStream().use { store.restoreBackup(it, null) }
                assertEquals(after, fingerprint(store))
                assertEquals(files, mediaFiles(context))
                assertEquals(latest, store.entries().single { it.id == latest.id })
                assertFalse(store.canUndoLastRestore())
                assertTrue(state.edit().putString("gapResult", after).commit())
            }
            "verifyGap" -> {
                assertEquals(state.getString("gapResult", null), fingerprint(store))
                val files = mediaFiles(context)
                encrypted.inputStream().use { store.restoreBackup(it, PASSWORD) }
                assertEquals(state.getString("gapResult", null), fingerprint(store))
                assertEquals(files, mediaFiles(context))
                assertFalse(store.canUndoLastRestore())
            }
            "legacyGap" -> {
                val current =
                    store.entries().single {
                        contentKey(store, it) == state.getString("originalKey", null)
                    }
                val legacyId =
                    java.util.UUID.nameUUIDFromBytes(
                            "xike-restore-conflict:${state.getString("originalId", null)}:${state.getString("originalKey", null)}:7"
                                .toByteArray(Charsets.UTF_8)
                        )
                        .toString()
                store.add(current.copy(id = legacyId))
                store.delete(current.id)
                store.finalizeDelete(current.id)
                val before = fingerprint(store)
                val files = mediaFiles(context)
                backup.inputStream().use { store.restoreBackup(it, null) }
                assertEquals(before, fingerprint(store))
                assertEquals(files, mediaFiles(context))
                assertFalse(store.canUndoLastRestore())
                assertTrue(state.edit().putString("legacyGapResult", before).commit())
            }
            "verifyLegacyGap" -> {
                assertEquals(state.getString("legacyGapResult", null), fingerprint(store))
                val files = mediaFiles(context)
                encrypted.inputStream().use { store.restoreBackup(it, PASSWORD) }
                assertEquals(state.getString("legacyGapResult", null), fingerprint(store))
                assertEquals(files, mediaFiles(context))
                assertFalse(store.canUndoLastRestore())
            }
            "prepareValidity" -> {
                backup.inputStream().use {
                    store.restoreBackup(it, null, mode = BackupRestoreMode.REPLACE)
                }
                assertTrue(store.canUndoLastRestore())
                rule.waitUntil(15_000) { model.canUndoRestore }
                val before = fingerprint(store)
                rule.runOnIdle { model.updateDraftNote("撤销资格验证草稿") }
                model.flushDraft()
                assertTrue(store.canUndoLastRestore())
                rule.runOnIdle { model.discardDraft() }
                assertEquals(before, fingerprint(store))
                val entry = store.entries().first()
                // Failed writes roll back the marker with the journal transaction.
                val dao = JournalDatabase.get(context).journalDao()
                assertTrue(runCatching { dao.insertJournal(entry.toBundle()) }.isFailure)
                assertTrue(
                    runCatching {
                        dao.updateJournal(
                            entry.copy(id = java.util.UUID.randomUUID().toString()).toBundle()
                        )
                    }
                        .isFailure
                )
                assertTrue(
                    runCatching { dao.deleteJournal(java.util.UUID.randomUUID().toString()) }
                        .isFailure
                )
                assertTrue(store.canUndoLastRestore())
                store.saveThemeName(store.savedThemeName() ?: model.selectedTheme.name)
                assertTrue(store.canUndoLastRestore())
                assertEquals(before, fingerprint(store))
            }
            "validityRestart" -> {
                assertTrue(store.canUndoLastRestore())
                assertTrue(model.canUndoRestore)
                rule.onNodeWithTag("navigation-settings").performClick()
                rule.onNodeWithText(localizedText("撤销上次恢复")).performScrollTo()
            }
            "invalidateAdd",
            "invalidateEdit",
            "invalidateDelete",
            "invalidateDeleteUndo",
            "invalidateDirect" -> {
                assertTrue(store.canUndoLastRestore())
                val dao = JournalDatabase.get(context).journalDao()
                val token = requireNotNull(dao.settingValue(RESTORE_UNDO_TOKEN_SETTING))
                val before = store.entries()
                val entry = before.first()
                when (args.getString("mergePhase")) {
                    "invalidateAdd" -> saveMoment(model, "撤销资格验证：恢复后新增")
                    "invalidateEdit" ->
                        store.update(
                            entry.copy(note = entry.note + " · 恢复后修改"),
                            entry.imageFileNames,
                        )
                    "invalidateDelete" -> {
                        store.delete(entry.id)
                        store.finalizeDelete(entry.id)
                    }
                    "invalidateDeleteUndo" -> {
                        store.delete(entry.id)
                        store.undoDelete(entry.id)
                    }
                    "invalidateDirect" -> {
                        // End this process after committing the mutation, without Store cleanup.
                        // The next process must reject the still-present snapshot via the marker.
                        rule.activityRule.scenario.close()
                        dao.updateJournal(entry.copy(note = entry.note + " · 事务提交后重启").toBundle())
                        assertTrue(File(context.filesDir, token).exists())
                        assertTrue(
                            state.edit().putString("afterMutation", fingerprint(store)).commit()
                        )
                        return
                    }
                }
                assertFalse(store.canUndoLastRestore())
                assertFalse(File(context.filesDir, token).exists())
                val after = fingerprint(store)
                assertTrue(runCatching { store.undoLastRestore() }.isFailure)
                // Exercise the commit-time guard even with an already captured stale token.
                assertTrue(
                    runCatching {
                        dao.replaceJournals(
                            before.map(JournalEntry::toBundle),
                            expectedUndoToken = token,
                        )
                    }
                        .isFailure
                )
                assertEquals(after, fingerprint(store))
                rule.waitUntil(15_000) { !model.canUndoRestore }
                rule.onNodeWithTag("navigation-settings").performClick()
                rule.onNodeWithText(localizedText("撤销上次恢复")).assertDoesNotExist()
                assertTrue(state.edit().putString("afterMutation", after).commit())
            }
            "verifyInvalidity" -> {
                assertFalse(store.canUndoLastRestore())
                assertFalse(model.canUndoRestore)
                assertEquals(state.getString("afterMutation", null), fingerprint(store))
                assertTrue(runCatching { store.undoLastRestore() }.isFailure)
                assertEquals(state.getString("afterMutation", null), fingerprint(store))
                assertTrue(
                    context.filesDir.listFiles().orEmpty().none {
                        it.name.startsWith("journal-restore-undo-")
                    }
                )
            }
            else -> error("Specify mergePhase")
        }
    }

    private fun saveMoment(model: JournalViewModel, note: String) {
        val count = model.libraryState.totalCount
        rule.onNodeWithTag("navigation-home").performClick()
        rule.onNodeWithText(Mood.CALM.label).performScrollTo().performClick()
        rule
            .onNodeWithContentDescription(localizedText("此刻的注脚"))
            .performScrollTo()
            .performTextInput(note)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText(localizedText("记下此刻")).performClick()
        rule.waitUntil(15_000) {
            model.libraryState.totalCount == count + 1 &&
                model.draft.isEmpty &&
                !model.isDraftSaving
        }
    }

    private fun contentKey(store: JournalStore, entry: JournalEntry): String {
        val videos = JournalVideoStore(ApplicationProvider.getApplicationContext())
        return restoreContentKey(
            entry,
            { requireNotNull(store.openImage(it)).use(::attachmentDigest) },
            { requireNotNull(store.openAudio(it)).use(::attachmentDigest) },
            { videos.open(it).use(::attachmentDigest) },
        )
    }

    private fun fingerprint(store: JournalStore) =
        store.entries().sortedBy { it.id }.joinToString("|") { "${it.id}:${contentKey(store, it)}" }

    private fun mediaFiles(context: Context) =
        listOf("journal-images", "journal-audios", "journal-videos")
            .flatMap { kind ->
                File(context.filesDir, kind).listFiles().orEmpty().map { "$kind/${it.name}" }
            }
            .toSet()

    private companion object {
        const val PASSWORD = "merge-process-test-password"
    }
}
