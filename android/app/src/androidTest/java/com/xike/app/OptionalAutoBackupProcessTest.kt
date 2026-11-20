package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xike.app.backup.AutoBackup
import com.xike.app.backup.AutoBackupPreferences
import com.xike.app.backup.AutoBackupWorker
import com.xike.app.backup.JournalStores
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run configure, plain, verifyPlain, encrypt, verifyEncrypted on an owned isolated device. */
class OptionalAutoBackupProcessTest {
    @Test
    fun optionalPasswordProducesReadableZipAndCanReturnToEncryption() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("optionalBackupIsolated") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AutoBackupPreferences(context)
        val phase = arguments.getString("optionalBackupPhase")
        if (phase == "configure") {
            assertTrue(preferences.settings().encrypted)
            assertNull(preferences.password)
            runBlocking {
                AutoBackup.save(
                    context,
                    preferences.settings().copy(enabled = true, encrypted = false, keepCount = 2),
                    null,
                )
            }
            assertFalse(AutoBackupPreferences(context).settings().encrypted)
            assertNull(preferences.password)
            return
        }
        val store = JournalStores.get(context).also { it.initialize() }
        fun runBackup() {
            val expectedCount = (preferences.files().size + 1).coerceAtMost(2)
            preferences.save(preferences.settings(), null, System.currentTimeMillis() - 60_000)
            val manager = WorkManager.getInstance(context)
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            manager.enqueue(request).result.get()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (
                System.nanoTime() < deadline &&
                    manager.getWorkInfoById(request.id).get()?.state != WorkInfo.State.SUCCEEDED
            ) Thread.sleep(100)
            assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(request.id).get()?.state)
            assertEquals(expectedCount, preferences.files().size)
            assertTrue(preferences.pending.isEmpty())
            assertFalse(preferences.cleanupNeeded)
        }
        fun verifyPlain(uri: Uri) {
            assertTrue(
                requireNotNull(DocumentFile.fromSingleUri(context, uri)?.name).endsWith(".zip")
            )
            assertEquals("application/zip", context.contentResolver.getType(uri))
            assertFalse(
                requireNotNull(context.contentResolver.openInputStream(uri))
                    .use(store::backupRequiresPassword)
            )
            val summary =
                requireNotNull(context.contentResolver.openInputStream(uri)).use {
                    store.inspectBackup(it, null)
                }
            assertEquals(store.entries().size, summary.entryCount)
            val names = mutableSetOf<String>()
            ZipInputStream(requireNotNull(context.contentResolver.openInputStream(uri))).use { zip
                ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    names += entry.name
                    zip.closeEntry()
                }
            }
            assertTrue(names.containsAll(listOf("manifest.json", "index.html", "complete.txt")))
        }
        when (phase) {
            "plain" -> {
                assertFalse(preferences.settings().encrypted)
                assertNull(preferences.password)
                repeat(3) { runBackup() }
                preferences.files().forEach { verifyPlain(Uri.parse(it.uri)) }
            }
            "verifyPlain" -> {
                assertFalse(preferences.settings().encrypted)
                assertNull(preferences.password)
                preferences.files().forEach { verifyPlain(Uri.parse(it.uri)) }
                val before = preferences.settings()
                assertTrue(
                    runCatching {
                        runBlocking {
                            AutoBackup.save(context, before.copy(encrypted = true), null)
                        }
                    }
                        .isFailure
                )
                assertEquals(before, preferences.settings())
            }
            "encrypt" -> {
                val password =
                    Base64.getEncoder()
                        .encodeToString(ByteArray(24).also(SecureRandom()::nextBytes))
                runBlocking {
                    AutoBackup.save(
                        context,
                        preferences.settings().copy(encrypted = true),
                        password,
                    )
                }
                runBackup()
                assertNotNull(preferences.password)
            }
            "verifyEncrypted" -> {
                assertTrue(preferences.settings().encrypted)
                val password = requireNotNull(preferences.password)
                val files = preferences.files()
                verifyPlain(Uri.parse(files.first().uri))
                val uri = Uri.parse(files.last().uri)
                assertTrue(
                    requireNotNull(DocumentFile.fromSingleUri(context, uri)?.name).endsWith(".xike")
                )
                assertTrue(
                    requireNotNull(context.contentResolver.openInputStream(uri))
                        .use(store::backupRequiresPassword)
                )
                assertEquals(
                    store.entries().size,
                    requireNotNull(context.contentResolver.openInputStream(uri))
                        .use { store.inspectBackup(it, password) }
                        .entryCount,
                )
                val settings = preferences.settings()
                runBlocking { AutoBackup.save(context, settings.copy(encrypted = false), null) }
                assertEquals(password, preferences.password)
                runBackup()
                verifyPlain(Uri.parse(preferences.files().last().uri))
                runBlocking { AutoBackup.save(context, settings, null) }
                assertEquals(password, preferences.password)
            }
            "plainWithSavedPassword" -> {
                val settings = preferences.settings()
                val password = requireNotNull(preferences.password)
                runBlocking { AutoBackup.save(context, settings.copy(encrypted = false), null) }
                runBackup()
                verifyPlain(Uri.parse(preferences.files().last().uri))
                runBlocking { AutoBackup.save(context, settings, null) }
                assertEquals(password, preferences.password)
            }
            "verifyPlainWithSavedPassword" -> {
                assertTrue(preferences.settings().encrypted)
                assertNotNull(preferences.password)
                verifyPlain(Uri.parse(preferences.files().last().uri))
            }
            else -> error("Specify a documented optional-backup verification phase")
        }
    }
}
