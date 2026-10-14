package com.xike.app

import com.xike.app.data.draft.JournalDraftStore

import com.xike.app.data.Mood

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real ViewModel, encrypted media receipts, database and network; independent device only. */
class MomentRaceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pendingPhotoReceiptBlocksSaveUntilTheRealPhotoIsInTheSnapshot() = runBlocking {
        val model = readyModel()
        val note = enterDraft()
        val uri = captureScreenPhoto()
        val originalIds = JournalStore(rule.activity).entries().map { it.id }.toSet()
        try {
            val state = photoResultState()
            withContext(Dispatchers.Main) {
                state.receiveFromSystem(listOf(uri), emptyList())
                assertTrue("Photo receipt did not block save before IO", model.isDraftImageResultPending)
                assertTrue(model.saveDraft().isFailure)
            }
            rule.waitUntil(15_000) { !model.isDraftImageResultPending && uri.toString() in model.draft.imageUriStrings }
            withContext(Dispatchers.Main) { model.saveDraft().getOrThrow() }
            rule.waitUntil(15_000) { model.libraryState.totalCount == originalIds.size + 1 }
            val saved = JournalStore(rule.activity).entries().single { it.id !in originalIds && it.note == note }
            assertEquals(1, saved.imageFileNames.size)
            JournalStore(rule.activity).openImage(saved.imageFileNames.single()).use { input ->
                val decoded = requireNotNull(BitmapFactory.decodeStream(requireNotNull(input)))
                assertTrue(decoded.width > 0 && decoded.height > 0)
                decoded.recycle()
            }
            assertTrue(model.draft.isEmpty)
            assertTrue(JournalDraftStore(rule.activity).load().isEmpty)
        } finally {
            cleanup(model, originalIds, note)
            deleteCameraCapture(rule.activity, uri)
        }
    }

    @Test
    fun photoArrivingDuringSaveCannotRestoreTheSavedTextAndMood() = runBlocking {
        val model = readyModel()
        val note = enterDraft()
        val uri = captureScreenPhoto()
        val originalIds = JournalStore(rule.activity).entries().map { it.id }.toSet()
        try {
            val state = photoResultState()
            withContext(Dispatchers.Main) {
                val snapshot = model.draft
                val saving = launch(start = CoroutineStart.UNDISPATCHED) { model.saveDraft().getOrThrow() }
                assertTrue(model.isDraftSaving)
                assertFalse("Save allowed an in-flight photo mutation", model.addDraftImages(listOf(uri)))
                assertEquals(snapshot, model.draft)
                state.receiveFromSystem(listOf(uri), emptyList())
                saving.join()
            }
            rule.waitUntil(15_000) { !model.isDraftImageResultPending && uri.toString() in model.draft.imageUriStrings }
            rule.waitUntil(15_000) { model.libraryState.totalCount == originalIds.size + 1 }
            assertEquals(1, JournalStore(rule.activity).entries().count { it.id !in originalIds && it.note == note })
            assertEquals("", model.draft.note)
            assertEquals(null, model.draft.mood)
            assertEquals(listOf(uri.toString()), model.draft.imageUriStrings)
            assertEquals(model.draft, JournalDraftStore(rule.activity).load())
        } finally {
            cleanup(model, originalIds, note)
            deleteCameraCapture(rule.activity, uri)
        }
    }

    @Test fun removingWeatherUnblocksSaveBeforeTheOldRequestFinishes() = verifyWeatherInvalidation { it.clearDraftOutdoor() }
    @Test fun backdatingUnblocksSaveBeforeTheOldRequestFinishes() = verifyWeatherInvalidation { it.updateDraftRecordedAt(System.currentTimeMillis() - 86_400_000L) }
    @Test fun discardingUnblocksSaveAndTheOldRequestCannotRestoreTheDraft() = verifyWeatherInvalidation { it.discardDraft() }

    private fun verifyWeatherInvalidation(invalidate: (JournalViewModel) -> Unit) = runBlocking {
        val model = readyModel()
        val note = enterDraft()
        val originalIds = JournalStore(rule.activity).entries().map { it.id }.toSet()
        try {
            withContext(Dispatchers.Main) {
                val request = launch(start = CoroutineStart.UNDISPATCHED) { model.attachOutdoorForCity("上海") }
                try {
                    assertTrue(model.isDraftOutdoorLoading)
                    invalidate(model)
                    assertFalse("Invalidated weather still blocked save", model.isDraftOutdoorLoading)
                    if (model.draft.recordedAt != null) {
                        val rejected = launch(start = CoroutineStart.UNDISPATCHED) {
                            assertTrue(model.attachOutdoorForCity("上海").isFailure)
                        }
                        assertTrue("Backdated weather started an unnecessary request", rejected.isCompleted)
                        assertFalse(model.isDraftOutdoorLoading)
                    }
                    if (model.draft.isEmpty) {
                        model.selectDraftMood(Mood.CALM)
                        model.updateDraftNote(note)
                    }
                    model.saveDraft().getOrThrow()
                } finally {
                    request.cancelAndJoin()
                }
                assertTrue(model.draft.isEmpty)
                assertEquals(null, model.draft.outdoor)
            }
            assertTrue(JournalDraftStore(rule.activity).load().isEmpty)
        } finally { cleanup(model, originalIds, note) }
    }

    private fun readyModel(): JournalViewModel {
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) {
            !model.isLoading && rule.onAllNodesWithContentDescription(localizedText("此刻的注脚")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(15_000) { !model.isDraftImageResultPending }
        check(model.draft.isEmpty) { "Use an independent device with an empty draft." }
        return model
    }

    private fun enterDraft(): String {
        val note = "Moment race ${UUID.randomUUID()}"
        rule.onNodeWithText(Mood.CALM.label).performScrollTo().performClick()
        rule.onNodeWithContentDescription(localizedText("此刻的注脚")).performScrollTo().performTextInput(note)
        rule.onNodeWithText(localizedText("完成")).performScrollTo().performClick()
        return note
    }

    private fun captureScreenPhoto(): Uri {
        val uri = createCameraCaptureUri(rule.activity)
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            rule.activity.contentResolver.openOutputStream(uri).use { output ->
                check(screenshot.compress(Bitmap.CompressFormat.JPEG, 85, requireNotNull(output)))
            }
            check(finalizeCameraCapture(rule.activity, uri))
            return uri
        } finally { screenshot.recycle() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun photoResultState(): SystemActivityResultState<List<Uri>> =
        ViewModelProvider(rule.activity)["system-activity-result/photo/draft/picker", SystemActivityResultState::class.java] as SystemActivityResultState<List<Uri>>

    private suspend fun cleanup(model: JournalViewModel, originalIds: Set<String>, note: String) {
        withContext(Dispatchers.Main) { model.discardDraft() }
        withContext(Dispatchers.IO) {
            val store = JournalStore(rule.activity)
            store.entries().filter { it.id !in originalIds && it.note == note }.forEach {
                store.delete(it.id, refreshEntries = false)
                store.finalizeDelete(it.id)
            }
        }
    }
}
