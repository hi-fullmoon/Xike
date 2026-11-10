package com.xike.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.attachmentDigest
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.media.JournalVideoStore
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals

/** Only digests enter test preferences/reports; journals and backup passwords are never logged. */
internal class RetiredLabelValidation {
    val context: Context = ApplicationProvider.getApplicationContext()
    val store: JournalStore
    private val preferences =
        context.getSharedPreferences("retired-label-validation-v2", Context.MODE_PRIVATE)

    init {
        check(InstrumentationRegistry.getArguments().getString("labelsIsolated") == "true") {
            "Run only on an explicitly selected isolated device with -e labelsIsolated true"
        }
        store = JournalStore(context).also { it.initialize(loadEntries = false) }
    }

    fun catalog() = runBlocking { store.readRepository.libraryState(0).labels }

    fun digest(): String {
        val draft = JournalDraftStore(context).load()
        val videos by lazy { JournalVideoStore(context) }
        // Restore deliberately changes internal filenames. Compare every record field and the
        // actual decrypted attachment bytes instead; never ignore media or historical labels.
        val entries =
            store
                .entries()
                .sortedBy { it.id }
                .map { entry ->
                    entry
                        .copy(
                            imageFileNames =
                                entry.imageFileNames.map { name ->
                                    checkNotNull(store.openImage(name)) {
                                            "Historical image is unreadable"
                                        }
                                        .use(::attachmentDigest)
                                },
                            audio =
                                entry.audio?.let { audio ->
                                    audio.copy(
                                        fileName =
                                            checkNotNull(store.openAudio(audio.fileName)) {
                                                    "Historical audio is unreadable"
                                                }
                                                .use(::attachmentDigest)
                                    )
                                },
                            video =
                                entry.video?.let { video ->
                                    video.copy(
                                        fileName =
                                            videos.open(video.fileName).use(::attachmentDigest),
                                        coverFileName =
                                            videos
                                                .open(video.coverFileName)
                                                .use(::attachmentDigest),
                                    )
                                },
                        )
                        .toJson()
                }
        val snapshot =
            JSONObject()
                .put("entries", JSONArray(entries))
                .put("catalog", JSONArray(catalog().sortedBy { it.id }.map { it.toJson() }))
                .put("draft", if (draft.isEmpty) JSONObject() else draft.toJson())
        return MessageDigest.getInstance("SHA-256")
            .digest(snapshot.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun checkpoint(name: String) {
        check(preferences.edit().putString(name, digest()).commit())
    }

    fun verify(name: String) {
        val expected =
            checkNotNull(preferences.getString(name, null)) {
                "Run the initial phase on this same isolated device first"
            }
        assertEquals("Historical entries, catalog or draft changed", expected, digest())
    }
}

internal fun ComposeTestRule.withRetiredLabelActivity(
    block: (ActivityScenario<MainActivity>) -> Unit
) {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        var model: JournalViewModel? = null
        scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
        waitUntil(20_000) { model?.systemActivityResultsReady == true }
        block(scenario)
    }
}

internal fun ComposeTestRule.assertRetiredLabelControlsAbsent() {
    onNodeWithText(localizedText("再留下一点")).assertDoesNotExist()
    onNodeWithText(localizedText("主题")).assertDoesNotExist()
    onNodeWithText(tr("活动", "Activities")).assertDoesNotExist()
    onNodeWithText(tr("管理主题", "Manage topics")).assertDoesNotExist()
    onNodeWithText(tr("管理活动", "Manage activities")).assertDoesNotExist()
}
