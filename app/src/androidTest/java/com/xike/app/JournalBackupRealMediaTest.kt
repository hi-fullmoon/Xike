package com.xike.app

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.BackupRestoreMode
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class JournalBackupRealMediaTest {
    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun capturedScreenAndRecordedAudioSurvivePlainAndEncryptedRestore() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("initializationIsolated") == "true"
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = JournalStore(context).also { it.initialize() }
        check(store.entries().isEmpty()) { "This test requires an empty independent device." }
        val image = File.createTempFile("backup-screen-", ".png", context.cacheDir)
        val recording = File.createTempFile("backup-recording-", ".m4a", context.cacheDir)
        val backup = File.createTempFile("backup-roundtrip-", ".xike", context.cacheDir)
        val recorder = MediaRecorder(context)
        var entry: JournalEntry? = null
        try {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                image.outputStream().use {
                    check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                screenshot.recycle()
            }
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.RECORD_AUDIO,
            )
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(recording.absolutePath)
            recorder.prepare()
            recorder.start()
            SystemClock.sleep(600L)
            recorder.stop()
            val audio = store.importAudio(recording, 600L)
            entry =
                JournalEntry(
                    mood = Mood.ABOVE_AVERAGE,
                    tags = emptyList(),
                    note = "",
                    audio = audio,
                )
            store.add(entry, listOf(Uri.fromFile(image)), refreshEntries = false)
            val original = store.entries().single()
            listOf<String?>(null, "Xike-backup-roundtrip").forEach { password ->
                backup.outputStream().use { store.writeBackup(it, password) }
                val summary = backup.inputStream().use { store.inspectBackup(it, password) }
                assertEquals(1, summary.entryCount)
                assertEquals(1, summary.imageCount)
                assertEquals(1, summary.audioCount)
                val restored =
                    backup.inputStream().use {
                        store.restoreBackup(it, password, mode = BackupRestoreMode.REPLACE).single()
                    }
                assertEquals(original.id, restored.id)
                store.openImage(restored.imageFileNames.single()).use {
                    assertArrayEquals(image.readBytes(), requireNotNull(it).readBytes())
                }
                store.openAudio(requireNotNull(restored.audio).fileName).use {
                    assertArrayEquals(recording.readBytes(), requireNotNull(it).readBytes())
                }
                assertTrue(store.canUndoLastRestore())
                val undone = store.undoLastRestore().single()
                store.openImage(undone.imageFileNames.single()).use {
                    assertArrayEquals(image.readBytes(), requireNotNull(it).readBytes())
                }
                store.openAudio(requireNotNull(undone.audio).fileName).use {
                    assertArrayEquals(recording.readBytes(), requireNotNull(it).readBytes())
                }
            }
        } finally {
            recorder.release()
            entry?.let {
                if (store.entries().any { saved -> saved.id == it.id }) {
                    store.delete(it.id, refreshEntries = false)
                    store.finalizeDelete(it.id)
                }
                it.audio?.let { audio -> store.deleteUnreferencedAudio(audio.fileName) }
            }
            image.delete()
            recording.delete()
            backup.delete()
        }
    }
}
