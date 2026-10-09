package com.xike.app

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an isolated device with a video actually recorded by the system camera. */
@RunWith(AndroidJUnit4::class)
class JournalVideoWorkflowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun videoSurvivesProcessRestartPlaybackBackupAndDeleteUndo() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Requires an isolated device and a real recorded video", arguments.getString("videoIsolatedDevice") == "true")
        val store = JournalStore(context)
        store.initialize()
        val drafts = JournalDraftStore(context)
        when (arguments.getString("videoProcessPhase")) {
            "write" -> {
                check(store.entries().none { it.id == ENTRY_ID })
                val video = arguments.getString("videoTestUri")?.let { uri ->
                    check(drafts.load().isEmpty)
                    store.importVideo(Uri.parse(uri))
                } ?: requireNotNull(drafts.load().video) { "Add a real camera video through the application first" }
                assertTrue(video.isValid())
                assertTrue(video.durationMillis > 0)
                assertTrue(video.sizeBytes > 0)
                // This reference is committed before the instrumentation process exits.
                drafts.save(JournalDraft(video = video))
                val entry = JournalEntry(id = ENTRY_ID, mood = Mood.CALM, tags = emptyList(), note = "", video = video)
                store.add(entry)
                JournalVideoStore(context).saveEdit(entry, video)
            }
            "read" -> {
                val video = requireNotNull(drafts.load().video)
                assertFalse(drafts.hasPendingWrite)
                assertEquals(video, store.entries().single { it.id == ENTRY_ID }.video)
                val videos = JournalVideoStore(context)
                assertEquals(video, videos.editDraft(store.entries().single { it.id == ENTRY_ID })?.video)
                store.requireReadableVideo(video)
                assertThrows(IllegalArgumentException::class.java) { store.requireReadableVideo(video.copy(durationMillis = MAX_VIDEO_DURATION_MILLIS + 1)) }
                assertThrows(IllegalArgumentException::class.java) { store.requireReadableVideo(video.copy(sizeBytes = MAX_VIDEO_BYTES + 1)) }
                val digest = videos.open(video.fileName).use(::digest)
                // Decrypting by native MediaPlayer proves actual decoding without a plaintext copy.
                val done = CountDownLatch(1)
                var playbackError = false
                val player = MediaPlayer()
                val source = videos.dataSource(video)
                try {
                    player.setDataSource(source)
                    player.setOnCompletionListener { done.countDown() }
                    player.setOnErrorListener { _, _, _ -> playbackError = true; done.countDown(); true }
                    player.prepare()
                    assertTrue(player.duration > 0)
                    player.seekTo((video.durationMillis - 2_000).coerceAtLeast(0).toInt())
                    player.start()
                    assertTrue(done.await(30, TimeUnit.SECONDS))
                    assertFalse(playbackError)
                } finally { player.release(); source.close() }
                val plain = ByteArrayOutputStream().also { store.writeBackup(it, null) }.toByteArray()
                ZipInputStream(plain.inputStream()).use { archive ->
                    var found = false
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        if (entry.name == "videos/${video.fileName}") { assertArrayEquals(digest, digest(archive)); found = true }
                        archive.closeEntry()
                    }
                    assertTrue(found)
                }
                val password = "video-test-password"
                val missingVideo = ByteArrayOutputStream()
                java.util.zip.ZipOutputStream(missingVideo).use { output ->
                    ZipInputStream(plain.inputStream()).use { input ->
                        while (true) {
                            val entry = input.nextEntry ?: break
                            if (entry.name != "videos/${video.fileName}") {
                                output.putNextEntry(java.util.zip.ZipEntry(entry.name)); input.copyTo(output); output.closeEntry()
                            }
                            input.closeEntry()
                        }
                    }
                }
                assertThrows(IllegalArgumentException::class.java) { store.restoreBackup(missingVideo.toByteArray().inputStream(), null) }
                assertEquals(video, store.entries().single { it.id == ENTRY_ID }.video)
                val encrypted = ByteArrayOutputStream().also { store.writeBackup(it, password) }.toByteArray()
                val summary = store.inspectBackup(encrypted.inputStream(), password)
                assertEquals(1, summary.videoCount)
                assertEquals(video.sizeBytes, summary.videoBytes)
                val restored = store.restoreBackup(encrypted.inputStream(), password, retainedVideo = video).single { it.id == ENTRY_ID }
                assertTrue(videos.editReferences().isEmpty())
                assertNotEquals(video.fileName, restored.video!!.fileName)
                assertArrayEquals(digest, videos.open(restored.video.fileName).use(::digest))
                store.undoLastRestore(retainedVideo = video)
                val restoredAgain = store.restoreBackup(plain.inputStream(), null, retainedVideo = video).single { it.id == ENTRY_ID }
                val restoredVideo = requireNotNull(restoredAgain.video)
                assertArrayEquals(digest, videos.open(restoredVideo.fileName).use(::digest))
                store.delete(ENTRY_ID)
                store.undoDelete(ENTRY_ID)
                assertEquals(restoredVideo, store.entries().single { it.id == ENTRY_ID }.video)
                store.delete(ENTRY_ID)
                store.finalizeDelete(ENTRY_ID)
                assertFalse(File(context.filesDir, "journal-videos/${restoredVideo.fileName}").exists())
                drafts.save(JournalDraft())
                store.deleteUnreferencedVideo(video)
                assertFalse(File(context.filesDir, "journal-videos/${video.fileName}").exists())
            }
            else -> error("videoProcessPhase must be write or read")
        }
    }

    private fun digest(input: InputStream): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        return hash.digest()
    }

    private companion object { const val ENTRY_ID = "video-workflow-verification" }
}
