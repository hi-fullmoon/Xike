package com.xike.app

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

/** Independent device only; prepare real diaries, photos and audio through the app first. */
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
                encrypted.inputStream().use { store.restoreBackup(it, password) }
                assertTrue(original == fingerprint(store))
                assertTrue(store.canUndoLastRestore())
                store.undoLastRestore()
                assertTrue(changed == fingerprint(store))
                plain.inputStream().use { store.restoreBackup(it, null) }
                assertTrue(original == fingerprint(store))
            }
            "verify" -> {
                assertTrue(checkNotNull(state.getString("original", null)) == fingerprint(store))
                assertEquals(state.getInt("count", -1), store.entries().size)
                plain.delete()
                encrypted.delete()
                directory.delete()
                check(state.edit().clear().commit())
            }
            else -> error("Specify backupProcessPhase write, restore or verify.")
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
