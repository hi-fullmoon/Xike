package com.xike.app

import android.content.Context
import android.media.MediaRecorder
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Use an isolated device; audio phases must run in separate instrumentation processes. */
class EditRecoveryTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun importedRecordingSurvivesProcessCleanup() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("editRecoveryIsolated") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context).also { it.initialize() }
        val state = EncryptedSharedPreferences.create(
            context, "edit-recovery-test",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        when (arguments.getString("editRecoveryPhase")) {
            "write" -> {
                check(!state.contains("audio"))
                val source = File.createTempFile("edit-recovery-", ".m4a", context.cacheDir)
                val recorder = MediaRecorder(context)
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setOutputFile(source.absolutePath)
                    recorder.prepare()
                    recorder.start()
                    Thread.sleep(2_000)
                    recorder.stop()
                    val audio = store.importEditAudio(source, 2_000)
                    check(state.edit().putString("audio", audio.toJson().toString()).commit())
                    store.requireReadableAudio(audio.fileName)
                } finally {
                    recorder.release()
                    source.delete()
                }
            }
            "read" -> {
                val audio = requireNotNull(JournalAudio.fromJson(JSONObject(requireNotNull(state.getString("audio", null)))))
                try {
                    store.removeOrphanedMedia()
                    store.requireReadableAudio(audio.fileName)
                    assertTrue(store.openAudio(audio.fileName)!!.use { it.readBytes().isNotEmpty() })
                } finally {
                    store.releaseEditAudio(audio)
                    check(state.edit().clear().commit())
                }
                assertNull(store.openAudio(audio.fileName))
            }
            else -> error("Specify editRecoveryPhase write or read")
        }
    }

    @Test
    fun detailRemainsOpenWhileRealEntriesReload() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("editRecoveryIsolated") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context).also { it.initialize() }
        val entry = store.entries().firstOrNull()
        assumeTrue("Requires a record created through the real app", entry != null)
        val entries = mutableStateOf(store.entries())
        val loading = mutableStateOf(false)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            XikeTheme(AppTheme.OCEAN) {
                JournalArchiveScreen(
                    PaddingValues(), entries.value, storeSearch(store),
                    onUpdate = { updated, retained, added -> runCatching { store.update(updated, retained, added).first { it.id == updated.id } } },
                    onDelete = { runCatching { store.delete(it.id); Unit } },
                    onUndoDelete = { runCatching { store.undoDelete(it); Unit } },
                    onFinalizeDelete = { runCatching { store.finalizeDelete(it) } },
                    openImage = store::openImage, openAudio = store::openAudio,
                    entriesLoading = loading.value,
                )
            }
        }
        composeRule.onAllNodesWithText(requireNotNull(entry).mood.label)[0].performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onNodeWithContentDescription(localizedText("关闭记录详情")).isDisplayed()
        }
        composeRule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
        composeRule.runOnIdle { loading.value = true; entries.value = emptyList() }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitUntil(5_000) {
            composeRule.onNodeWithContentDescription(localizedText("关闭记录详情")).isDisplayed()
        }
        composeRule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
        val reloadedEntries = store.entries()
        composeRule.runOnIdle { entries.value = reloadedEntries; loading.value = false }
        composeRule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
    }

    private fun storeSearch(store: JournalStore): suspend (JournalSearchQuery, Int, Int) -> Result<JournalSearchPage> =
        { query, offset, limit -> runCatching { store.search(query, offset, limit) } }
}
