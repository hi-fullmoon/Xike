package com.xike.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run raw, stage, import, stale and receipt in separate instrumentation processes. */
class JournalEditProcessTest {
    @Test fun recordingAndImportReceiptSurviveProcessRestart() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("editIsolated") == "true")
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = JournalStore(application).also { it.initialize() }
        assumeTrue("Requires one app-created record on an isolated device", store.entries().size == 1)
        val id = store.entries().single().id
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[JournalViewModel::class.java]
        }
        val operations = model.editOperations
        val metadata = EncryptedSharedPreferences.create(application, "edit-process-test",
            MasterKey.Builder(application).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        fun digest(bytes: ByteArray) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
        try {
            when (arguments.getString("editPhase")) {
                "raw" -> {
                    check(!metadata.contains("hash"))
                    val recorder = XikeAudioRecorder(application, operations.recordingDirectory(id))
                    var source: File? = null
                    try {
                        source = recorder.start {}
                        Thread.sleep(2_000)
                        val completed = recorder.stop()
                        assertEquals(operations.recordingDirectory(id), completed.parentFile)
                        val expected = digest(completed.readBytes())
                        check(metadata.edit().putString("hash", expected).commit())
                    } catch (error: Throwable) {
                        source?.delete()
                        throw error
                    } finally { recorder.cancel() }
                }
                "stage" -> {
                    pruneVoiceTemporaryFiles(application)
                    val recovered = operations.recoverAudio(id) as EditOutcome.Recovered
                    val raw = requireNotNull(recovered.raw)
                    assertTrue(digest(raw.first.readBytes()) == metadata.getString("hash", null))
                    operations.stageAudio(id, raw)
                    assertFalse(raw.first.exists())
                }
                "import" -> {
                    val recovered = operations.recoverAudio(id) as EditOutcome.Recovered
                    assertNull(recovered.raw)
                    val staged = requireNotNull(recovered.staged)
                    val key = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(id.toByteArray()))
                    val stagedFile = File(application.filesDir, "pending-edit-audio/$key/${staged.fileName}")
                    val encryptedBackup = stagedFile.readBytes()
                    val outcome = operations.importAudio(id, null, staged) as EditOutcome.AudioReady
                    assertTrue(store.openAudio(outcome.audio.fileName)!!.use { digest(it.readBytes()) } == metadata.getString("hash", null))
                    // Exercise an actual missing imported file with its intact encrypted backup.
                    stagedFile.writeBytes(encryptedBackup)
                    store.releaseEditAudio(outcome.audio)
                }
                "stale" -> {
                    val recovered = operations.recoverAudio(id) as EditOutcome.Recovered
                    assertNull(recovered.raw)
                    val outcome = operations.importAudio(id, null, requireNotNull(recovered.staged)) as EditOutcome.AudioReady
                    assertTrue(store.openAudio(outcome.audio.fileName)!!.use { digest(it.readBytes()) } == metadata.getString("hash", null))
                    // Leave the durable receipt without consuming any UI result.
                }
                "receipt" -> {
                    val recovered = operations.recoverAudio(id) as EditOutcome.AudioReady
                    try {
                        assertTrue(store.openAudio(recovered.audio.fileName)!!.use { digest(it.readBytes()) } == metadata.getString("hash", null))
                    } finally {
                        store.releaseEditAudio(recovered.audio)
                        operations.discardAudio(id)
                        check(metadata.edit().clear().commit())
                    }
                    val empty = operations.recoverAudio(id) as EditOutcome.Recovered
                    assertNull(empty.raw); assertNull(empty.staged)
                }
                else -> error("Specify editPhase raw, stage, import, stale or receipt")
            }
        } finally { withContext(Dispatchers.Main) { owner.viewModelStore.clear() } }
    }
}
