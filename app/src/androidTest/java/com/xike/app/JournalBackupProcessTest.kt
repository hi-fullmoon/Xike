package com.xike.app

import com.xike.app.data.JournalDataException

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Independent device only; run write, restore, undo, reinstall and verify in separate processes. */
class JournalBackupProcessTest {
    @Test
    fun realRecordsAndMediaSurviveBackupRestoreUndoAcrossProcesses() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("backupProcessIsolated") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context).also { it.initialize() }
        val directory = File(context.cacheDir, "backup-process-verification").apply { mkdirs() }
        val plain = File(directory, "records.zip")
        val encrypted = File(directory, "records.xike")
        val state = EncryptedSharedPreferences.create(context, "xike-backup-process-verification",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        when (args.getString("backupProcessPhase")) {
            "write" -> {
                check(!state.contains("original")) { "An earlier verification still needs recovery." }
                val entries = store.entries()
                check(entries.size > 1 && entries.any { it.imageFileNames.isNotEmpty() && it.audio != null })
                val password = Base64.getEncoder().encodeToString(ByteArray(24).also(SecureRandom()::nextBytes))
                val original = fingerprint(store)
                plain.outputStream().use { store.writeBackup(it, null) }
                encrypted.outputStream().use { store.writeBackup(it, password) }
                check(state.edit().putString("original", original).putString("password", password)
                    .putInt("count", entries.size).commit())
                // Remove only one app-created diary from this independent verification device.
                store.delete(entries.last().id, refreshEntries = false)
                store.finalizeDelete(entries.last().id)
                check(state.edit().putString("changed", fingerprint(store)).commit())
            }
            "restore" -> {
                val original = checkNotNull(state.getString("original", null))
                val changed = checkNotNull(state.getString("changed", null))
                val password = checkNotNull(state.getString("password", null))
                assertTrue(changed == fingerprint(store))
                assertTrue(encrypted.inputStream().use(store::backupRequiresPassword))
                assertFalse(plain.inputStream().use(store::backupRequiresPassword))
                val summary = encrypted.inputStream().use { store.inspectBackup(it, password) }
                assertEquals(state.getInt("count", -1), summary.entryCount)
                assertTrue(summary.imageCount > 0 && summary.audioCount > 0)
                assertTrue(runCatching { encrypted.inputStream().use { store.inspectBackup(it, "$password-invalid") } }.isFailure)
                assertTrue(changed == fingerprint(store))
                val undoAvailable = store.canUndoLastRestore()
                assertTrue(runCatching { encrypted.inputStream().use { store.restoreBackup(it, "$password-invalid") } }.isFailure)
                assertTrue(changed == fingerprint(store))
                assertEquals(undoAvailable, store.canUndoLastRestore())

                // A missing existing attachment must stop undo-snapshot creation before replacement.
                val audio = requireNotNull(store.entries().firstNotNullOfOrNull { it.audio })
                val existing = File(context.filesDir, "journal-audios/${audio.fileName}")
                val held = File(directory, audio.fileName)
                check(!held.exists())
                assertTrue(existing.renameTo(held))
                try {
                    val failed = runCatching { encrypted.inputStream().use { store.restoreBackup(it, password) } }
                    assertTrue(failed.exceptionOrNull() is JournalDataException)
                    assertEquals(undoAvailable, store.canUndoLastRestore())
                } finally {
                    assertTrue(held.renameTo(existing))
                }
                assertTrue(changed == fingerprint(store))
                encrypted.inputStream().use { store.restoreBackup(it, password) }
                assertTrue(original == fingerprint(store))
                assertTrue(store.canUndoLastRestore())
            }
            "undo" -> {
                assertTrue(checkNotNull(state.getString("original", null)) == fingerprint(store))
                assertTrue(store.canUndoLastRestore())
                store.undoLastRestore()
                assertTrue(checkNotNull(state.getString("changed", null)) == fingerprint(store))
                assertFalse(store.canUndoLastRestore())
            }
            "reinstall" -> {
                assertTrue(checkNotNull(state.getString("changed", null)) == fingerprint(store))
                assertFalse(store.canUndoLastRestore())
                plain.inputStream().use { store.restoreBackup(it, null) }
                assertTrue(checkNotNull(state.getString("original", null)) == fingerprint(store))
            }
            "verify" -> {
                assertTrue(checkNotNull(state.getString("original", null)) == fingerprint(store))
                assertEquals(state.getInt("count", -1), store.entries().size)
                assertTrue(store.canUndoLastRestore())
                plain.delete()
                encrypted.delete()
                directory.delete()
                check(state.edit().clear().commit())
            }
            else -> error("Specify backupProcessPhase write, restore, undo, reinstall or verify.")
        }
    }

    private fun fingerprint(store: JournalStore): String {
        val entries = JSONArray()
        store.entries().sortedBy { it.id }.forEach { entry ->
            val json = entry.toJson()
            json.put("imageFileNames", JSONArray(entry.imageFileNames.map { name ->
                requireNotNull(store.openImage(name)).use(::digest)
            }))
            entry.audio?.let { audio ->
                json.put("audio", audio.toJson().put("fileName", requireNotNull(store.openAudio(audio.fileName)).use(::digest)))
            }
            check(entry.video == null) { "This verification fixture covers app-created photos and audio." }
            entries.put(json)
        }
        return digest(entries.toString().byteInputStream())
    }

    private fun digest(input: InputStream): String = input.use {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            hash.update(buffer, 0, count)
        }
        Base64.getEncoder().encodeToString(hash.digest())
    }
}
