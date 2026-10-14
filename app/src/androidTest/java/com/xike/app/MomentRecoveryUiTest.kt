package com.xike.app

import com.xike.app.media.JournalVideoStore
import com.xike.app.data.draft.JournalDraftStore

import com.xike.app.data.Mood
import com.xike.app.data.MAX_IMAGES_PER_ENTRY
import com.xike.app.data.JournalEntry

import android.net.Uri
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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

/** Requires an independent device. Media comes from an actual screen recording. */
class MomentRecoveryUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun photoOverflowKeepsExistingPhotosAndReportsTheSkippedSelection() {
        val model = model()
        check(model.draft.isEmpty) { "Use an independent device with an empty draft." }
        val context = rule.activity
        val created = mutableListOf<Uri>()
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            repeat(MAX_IMAGES_PER_ENTRY + 1) {
                val uri = createCameraCaptureUri(context)
                created += uri
                context.contentResolver.openOutputStream(uri).use { output ->
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.JPEG, 85, requireNotNull(output)))
                }
                assertTrue(finalizeCameraCapture(context, uri))
            }
            val existing = created.take(MAX_IMAGES_PER_ENTRY - 1)
            rule.runOnIdle { assertTrue(model.addDraftImages(existing)) }
            rule.runOnIdle {
                assertTrue(model.addDraftImages(created.takeLast(2)))
                assertTrue(model.operationFeedback.any {
                    it.message == tr("本次已添加 1 张照片，其余照片因数量上限未添加。", "Added 1 photos. The remaining photos were not added because of the limit.")
                })
            }
            assertEquals(existing.map(Uri::toString) + created[created.lastIndex - 1].toString(), model.draft.imageUriStrings)
            assertEquals(model.draft.imageUriStrings, JournalDraftStore(context).load().imageUriStrings)
        } finally {
            screenshot.recycle()
            rule.runOnIdle { model.discardDraft() }
            created.forEach { deleteCameraCapture(context, it) }
        }
    }

    @Test
    fun savingDuringRealWeatherRequestIsRejectedAndCancellationReleasesTheGuard() = runBlocking {
        val model = model()
        val originalDraft = model.draft
        val originalCount = model.libraryState.totalCount
        withContext(Dispatchers.Main) {
            val request = launch(start = CoroutineStart.UNDISPATCHED) { model.attachOutdoorForCity("上海") }
            try {
                assertTrue(model.isDraftOutdoorLoading)
                val result = model.save(JournalEntry(mood = Mood.CALM, tags = emptyList(), note = ""), emptyList())
                assertTrue(result.isFailure)
                assertFalse(model.isDraftSaving)
                assertEquals(originalDraft, model.draft)
                assertEquals(originalCount, model.libraryState.totalCount)
            } finally {
                request.cancelAndJoin()
            }
            assertFalse(model.isDraftOutdoorLoading)
        }
    }

    @Test
    fun failedVideoReplacementCanBeCancelledAndOriginalRemainsReadable() {
        val model = model()
        check(model.draft.isEmpty) { "Use an independent device with an empty draft." }
        val source = File(rule.activity.getExternalFilesDir(null), "moment-recovery-${SystemClock.elapsedRealtime()}.mp4")
        var captureUri: Uri? = null
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screenrecord --time-limit 3 ${source.absolutePath}")
                .use { descriptor ->
                    // A static emulator screen can yield a zero-duration video. Record an actual interaction.
                    SystemClock.sleep(500L)
                    rule.onNodeWithText(localizedText("再留下一点")).performScrollTo().performClick()
                    java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                }
            assertTrue("Actual screen recording is unavailable", source.isFile && source.length() > 0L)
            val uri = createVideoCaptureUri(rule.activity)
            captureUri = uri
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat ${source.absolutePath}").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { input ->
                    rule.activity.contentResolver.openOutputStream(uri).use { output ->
                        input.copyTo(requireNotNull(output))
                    }
                }
            }
            assertTrue(finishVideoCapture(rule.activity, uri, true))
            rule.runOnIdle { assertTrue(model.queueDraftVideo(uri)) }
            rule.waitUntil(30_000) { !model.isDraftVideoSaving }
            val imported = checkNotNull(model.draft.video) { model.dataError ?: "Actual video import failed" }
            assertEquals(1, rule.activity.contentResolver.delete(uri, null, null))
            captureUri = null
            rule.runOnIdle { assertTrue(model.queueDraftVideo(uri)) }
            rule.waitUntil(30_000) { !model.isDraftVideoSaving }
            assertEquals(imported, model.draft.video)
            assertEquals(uri.toString(), model.draft.pendingVideoUri)
            // Dismiss the operation error before exercising the real recovery action.
            rule.runOnIdle { model.dismissDataError() }
            rule.onNodeWithText(tr("取消本次导入", "Cancel this import")).performScrollTo().assertIsDisplayed().performClick()
            rule.runOnIdle {
                assertEquals(null, model.draft.pendingVideoUri)
                assertEquals(imported, model.draft.video)
                assertEquals(imported, JournalDraftStore(rule.activity).load().video)
            }
            val store = JournalStore(rule.activity)
            store.requireReadableVideo(imported)
            assertTrue(JournalVideoStore(rule.activity).dataSource(imported).use { it.size > 0L })
        } finally {
            rule.runOnIdle { model.cancelPendingDraftVideo(); model.discardDraft() }
            captureUri?.let { rule.activity.contentResolver.delete(it, null, null) }
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("rm -f ${source.absolutePath}").close()
        }
    }

    private fun model(): JournalViewModel {
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) { !model.isLoading }
        return model
    }
}
