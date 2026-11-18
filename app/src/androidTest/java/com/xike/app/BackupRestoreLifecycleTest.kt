package com.xike.app

import android.Manifest
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.Mood
import com.xike.app.data.attachmentDigest
import com.xike.app.data.overallLabel
import com.xike.app.data.restoreContentKey
import com.xike.app.media.JournalVideoStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Only run on a fresh, explicitly isolated device. Uses real microphone audio and backup files. */
class BackupRestoreLifecycleTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun confirmedRestoreUsesPreviewedBackupAfterSourceChanges(): Unit = runBlocking {
        val model = model()
        val context = rule.activity.application
        val store = com.xike.app.backup.JournalStores.get(context)
        val originalIds = store.entries().map { it.id }.toSet()
        val selected = File.createTempFile("selected-backup-", ".zip", context.cacheDir)
        val replacement = File.createTempFile("replacement-backup-", ".zip", context.cacheDir)
        try {
            saveMoment(model, "恢复确认 " + java.util.UUID.randomUUID())
            val previewedContents = contents(store)
            selected.outputStream().use { store.writeBackup(it, null) }
            saveMoment(model, "当前文件已变化 " + java.util.UUID.randomUUID())
            val localContents = contents(store)
            replacement.outputStream().use { store.writeBackup(it, null) }
            val existingStages = stages(context)
            withContext(Dispatchers.Main) {
                model
                    .previewBackup(Uri.fromFile(selected), null) { summary ->
                        assertEquals(previewedContents.size, summary.entryCount)
                        null
                    }
                    .getOrThrow()
            }
            assertEquals(existingStages, stages(context))
            assertEquals(localContents, contents(store))

            withContext(Dispatchers.Main) {
                model
                    .previewBackup(Uri.fromFile(selected), null) { summary ->
                        assertEquals(previewedContents.size, summary.entryCount)
                        withContext(Dispatchers.IO) {
                            val stage = (stages(context) - existingStages).single()
                            replacement.copyTo(selected, overwrite = true)
                            store.removeOrphanedMedia()
                            assertTrue(stage.isDirectory)
                        }
                        BackupRestoreMode.REPLACE
                    }
                    .getOrThrow()
            }
            assertEquals(previewedContents, contents(store))
            assertEquals(existingStages, stages(context))
        } finally {
            store
                .entries()
                .filterNot { it.id in originalIds }
                .forEach {
                    store.delete(it.id, refreshEntries = false)
                    store.finalizeDelete(it.id)
                }
            selected.delete()
            replacement.delete()
        }
    }

    @Test
    fun audioPublicationAndRestoreAreSerializedAcrossRecreation(): Unit = runBlocking {
        val model = model()
        check(model.draft.isEmpty)
        val context = rule.activity.application
        val store = JournalStore(context)
        val originalIds = store.entries().map { it.id }.toSet()
        saveMoment(model, "恢复前记录 ${java.util.UUID.randomUUID()}")
        val backedUpContents = contents(store)
        val backup = File.createTempFile("restore-real-", ".zip", context.cacheDir)
        backup.outputStream().use { store.writeBackup(it, null) }
        saveMoment(model, "撤销恢复核对 ${java.util.UUID.randomUUID()}")
        val beforeRestore = store.entries()
        val beforeRestoreContents = contents(store)
        val recorder = XikeAudioRecorder(context)
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        recorder.start {}
        SystemClock.sleep(600)
        val audio = recorder.stop()
        val bytes = audio.readBytes()
        val editingAudio = store.importEditAudio(audio, 600)
        val mutex = mutationMutex(model)
        val owner = Any()
        val requester = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var publishedAudioName: String? = null
        var replacement: File? = null
        mutex.lock(owner)
        try {
            rule.runOnIdle { model.queueDraftAudio(audio, 600) }
            rule.waitUntil(15_000) {
                model.pendingDraftAudio?.stagedFileName != null && model.isDraftAudioSaving
            }
            val restore = requester.async {
                model.previewBackup(Uri.fromFile(backup), null) { BackupRestoreMode.REPLACE }
            }
            rule.waitUntil(10_000) {
                model.backupRestoreState.operation == RestoreOperation.RESTORE
            }
            val duplicate = requester.async { model.restoreBackup(Uri.fromFile(backup), null) }
            assertTrue(duplicate.await().isFailure)
            rule.activityRule.scenario.recreate()
            assertSame(model, ViewModelProvider(rule.activity)[JournalViewModel::class.java])
            rule.onNodeWithText(localizedText("正在创建安全快照并恢复…")).assertIsDisplayed()
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            // The old page's awaiting coroutine is cancelled when its composition is destroyed.
            restore.cancel()
            mutex.unlock(owner)
            rule.waitUntil(15_000) {
                model.backupRestoreState.operation == null &&
                    model.backupRestoreState.outcome != null
            }
            assertTrue(requireNotNull(model.backupRestoreState.outcome).result.isSuccess)
            assertEquals(backedUpContents, contents(store))
            rule.waitUntil(15_000) { !model.isDraftAudioSaving && model.pendingDraftAudio == null }
            val savedAudio = requireNotNull(model.draft.audio)
            publishedAudioName = savedAudio.fileName
            assertArrayEquals(bytes, store.openAudio(savedAudio.fileName)!!.use { it.readBytes() })
            assertArrayEquals(
                bytes,
                store.openAudio(editingAudio.fileName)!!.use { it.readBytes() },
            )
            assertEquals(
                savedAudio,
                com.xike.app.data.draft.JournalDraftStore(context).load().audio,
            )
            assertNotNull(model.backupRestoreState.outcome)
            rule.activityRule.scenario.recreate()
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText(localizedText("撤销")).fetchSemanticsNodes().isNotEmpty()
            }
            recorder.start {}
            SystemClock.sleep(600)
            replacement = recorder.stop()
            val replacementBytes = replacement.readBytes()
            mutex.lock(owner)
            rule.onNodeWithText(localizedText("撤销")).performClick()
            rule.waitUntil(10_000) { model.backupRestoreState.operation == RestoreOperation.UNDO }
            rule.runOnIdle { model.queueDraftAudio(requireNotNull(replacement), 600) }
            rule.waitUntil(15_000) {
                model.pendingDraftAudio?.stagedFileName != null && model.isDraftAudioSaving
            }
            mutex.unlock(owner)
            rule.waitUntil(15_000) {
                !model.canUndoRestore && model.backupRestoreState.operation == null
            }
            rule.waitUntil(15_000) { !model.isDraftAudioSaving && model.pendingDraftAudio == null }
            val replacementAudio = requireNotNull(model.draft.audio)
            publishedAudioName = replacementAudio.fileName
            assertNotEquals(savedAudio.fileName, replacementAudio.fileName)
            assertArrayEquals(
                replacementBytes,
                store.openAudio(replacementAudio.fileName)!!.use { it.readBytes() },
            )
            assertEquals(beforeRestoreContents, contents(store))
            assertArrayEquals(
                bytes,
                store.openAudio(editingAudio.fileName)!!.use { it.readBytes() },
            )
        } finally {
            if (mutex.holdsLock(owner)) mutex.unlock(owner)
            requester.cancel()
            recorder.cancel()
            rule.runOnIdle {
                if (publishedAudioName != null && model.draft.audio?.fileName == publishedAudioName)
                    model.removeDraftAudio()
            }
            store.releaseEditAudio(editingAudio)
            val ownedIds = beforeRestore.map { it.id }.toSet() - originalIds
            store
                .entries()
                .filter { it.id in ownedIds }
                .forEach {
                    store.delete(it.id, refreshEntries = false)
                    store.finalizeDelete(it.id)
                }
            audio.delete()
            replacement?.delete()
            backup.delete()
        }
    }

    @Test
    fun failedRestoreRetainsItsResultAfterRecreation(): Unit = runBlocking {
        val model = model()
        val before = model.libraryState.totalCount
        val store = JournalStore(rule.activity)
        val beforeEntries = store.entries()
        val directory =
            File(rule.activity.cacheDir, "unreadable-backup-${System.nanoTime()}").apply {
                mkdirs()
            }
        val mutex = mutationMutex(model)
        val owner = Any()
        val requester = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        mutex.lock(owner)
        try {
            val restore = requester.async { model.restoreBackup(Uri.fromFile(directory), null) }
            rule.waitUntil(10_000) { model.backupRestoreState.operation != null }
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText(localizedText("正在创建安全快照并恢复…")).assertIsDisplayed()
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            mutex.unlock(owner)
            assertTrue(restore.await().isFailure)
            val feedback = model.operationFeedback.last()
            assertTrue(feedback.isError)
            assertEquals(before, model.libraryState.totalCount)
            assertEquals(beforeEntries, store.entries())
            rule.activityRule.scenario.recreate()
            assertTrue(model.operationFeedback.contains(feedback))
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.onNodeWithText(feedback.message).assertIsDisplayed()
        } finally {
            if (mutex.holdsLock(owner)) mutex.unlock(owner)
            requester.cancel()
            directory.delete()
        }
    }

    private fun model(): JournalViewModel {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restoreIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(20_000) { !model.isLoading }
        return model
    }

    private fun saveMoment(model: JournalViewModel, note: String) {
        val count = model.libraryState.totalCount
        val moodLabel =
            if (model.draft.moodScaleVersion > 0) Mood.NEUTRAL.overallLabel else Mood.CALM.label
        rule.onNodeWithTag("navigation-home").performClick()
        rule.onNodeWithText(moodLabel).performScrollTo().performClick()
        rule
            .onNodeWithContentDescription(localizedText("此刻的注脚"))
            .performScrollTo()
            .performTextInput(note)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText(localizedText("记下此刻")).performClick()
        rule.waitUntil(15_000) {
            model.libraryState.totalCount == count + 1 &&
                !model.isDraftSaving &&
                model.draft.isEmpty
        }
    }

    private fun mutationMutex(model: JournalViewModel): Mutex =
        JournalViewModel::class.java.getDeclaredField("mediaMutationMutex").let {
            it.isAccessible = true
            it.get(model) as Mutex
        }

    private fun stages(context: android.content.Context): Set<File> =
        context.filesDir
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith("journal-images-restore-") }
            .toSet()

    // Replacement and undo legitimately rename private attachment copies. Compare every
    // record field and the decrypted media, so real existing photos/audio remain covered.
    private fun contents(store: JournalStore): Map<String, String> {
        val videos = JournalVideoStore(rule.activity)
        return store.entries().associate { entry ->
            entry.id to
                restoreContentKey(
                    entry,
                    { requireNotNull(store.openImage(it)).use(::attachmentDigest) },
                    { requireNotNull(store.openAudio(it)).use(::attachmentDigest) },
                    { videos.open(it).use(::attachmentDigest) },
                )
        }
    }
}
