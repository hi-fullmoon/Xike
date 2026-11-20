package com.xike.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xike.app.backup.AutoBackup
import com.xike.app.backup.AutoBackupPreferences
import com.xike.app.backup.AutoBackupSettings
import com.xike.app.backup.AutoBackupWorker
import com.xike.app.backup.JournalStores
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Uses a real folder selected in the system picker on an explicitly isolated device. */
class AutoBackupProcessTest {
    @Test
    fun encryptedBackupsRetentionAndPermissionsAcrossProcesses() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("autoBackupIsolated") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AutoBackupPreferences(context)
        if (arguments.getString("autoBackupPhase") == "configure") {
            // The grant comes from the real system picker, not a test document provider.
            val grant =
                context.contentResolver.persistedUriPermissions.single {
                    it.isReadPermission && it.isWritePermission
                }
            val password =
                Base64.getEncoder().encodeToString(ByteArray(24).also(SecureRandom()::nextBytes))
            val settings = AutoBackupSettings(enabled = true, directory = grant.uri.toString())
            runBlocking { AutoBackup.save(context, settings, password) }
            assertEquals(settings, AutoBackupPreferences(context).settings())
            return
        }
        val settings = preferences.settings()
        check(settings.enabled && settings.directory.isNotBlank())
        val password = requireNotNull(preferences.password)
        val directory =
            requireNotNull(DocumentFile.fromTreeUri(context, Uri.parse(settings.directory)))
        val store = JournalStores.get(context).also { it.initialize() }
        val manager = WorkManager.getInstance(context)

        fun runDueWorker(expectSuccess: Boolean, makeDue: Boolean = true) {
            if (makeDue)
                preferences.save(
                    settings.copy(keepCount = 2),
                    null,
                    System.currentTimeMillis() - 60_000,
                )
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            manager.enqueue(request).result.get(30, TimeUnit.SECONDS)
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
                while (System.nanoTime() < deadline) {
                    val info = requireNotNull(manager.getWorkInfoById(request.id).get())
                    if (expectSuccess && info.state == WorkInfo.State.SUCCEEDED) return
                    if (!expectSuccess && preferences.status == "directory") return
                    check(info.state != WorkInfo.State.FAILED)
                    Thread.sleep(100)
                }
                error("Automatic backup did not reach the expected observable state")
            } finally {
                manager.cancelWorkById(request.id).result.get()
            }
        }

        when (arguments.getString("autoBackupPhase")) {
            "write" -> {
                check(preferences.files().isEmpty())
                // An actual manually exported backup must survive automatic retention cleanup.
                val manual =
                    requireNotNull(
                        directory.createFile("application/octet-stream", "manual-keep.xike")
                    )
                requireNotNull(context.contentResolver.openOutputStream(manual.uri)).use {
                    store.writeEncryptedBackup(it, password)
                }
                repeat(3) { runDueWorker(expectSuccess = true) }
                assertEquals(2, preferences.files().size)
                assertEquals(3, directory.listFiles().size)
                assertTrue(manual.exists())
                assertTrue(preferences.lastSuccess > 0)
                assertTrue(preferences.nextAt > System.currentTimeMillis())
                assertTrue(preferences.pending.isEmpty())
            }
            "verify" -> {
                // A fresh instrumentation process reopens encrypted settings and external files.
                assertEquals(2, settings.keepCount)
                assertEquals(2, preferences.files().size)
                assertNotNull(directory.findFile("manual-keep.xike"))
                preferences.files().forEach { file ->
                    val uri = Uri.parse(file.uri)
                    assertTrue(
                        requireNotNull(context.contentResolver.openInputStream(uri))
                            .use(store::backupRequiresPassword)
                    )
                    val summary =
                        requireNotNull(context.contentResolver.openInputStream(uri)).use {
                            store.inspectBackup(it, password)
                        }
                    assertEquals(store.entries().size, summary.entryCount)
                    assertTrue(
                        runCatching {
                            requireNotNull(context.contentResolver.openInputStream(uri)).use {
                                store.inspectBackup(it, "$password-wrong")
                            }
                        }
                            .isFailure
                    )
                }
            }
            "interruptCleanup" -> {
                check(preferences.files().size == 2)
                // Stop at the actual durable boundary after a verified export, before pruning.
                val document =
                    requireNotNull(
                        directory.createFile("application/octet-stream", "interrupted-cleanup.xike")
                    )
                requireNotNull(context.contentResolver.openOutputStream(document.uri)).use {
                    store.writeEncryptedBackup(it, password)
                }
                requireNotNull(context.contentResolver.openInputStream(document.uri)).use {
                    store.inspectBackup(it, password)
                }
                preferences.pending = preferences.pending + document.uri.toString()
                val now = System.currentTimeMillis()
                preferences.completeBackup(
                    com.xike.app.backup.AutoBackupFile(
                        document.uri.toString(),
                        settings.directory,
                        now,
                    ),
                    com.xike.app.backup
                        .nextAutoBackupAt(settings, java.time.ZonedDateTime.now())
                        .toInstant()
                        .toEpochMilli(),
                )
                assertEquals(now, preferences.lastSuccess)
                assertTrue(preferences.cleanupNeeded)
                assertTrue(preferences.pending.isEmpty())
                assertEquals(3, preferences.files().size)
            }
            "resumeCleanup" -> {
                assertTrue(preferences.cleanupNeeded)
                assertEquals(3, preferences.files().size)
                val success = preferences.lastSuccess
                val newest = preferences.files().last()
                runDueWorker(expectSuccess = true, makeDue = false)
                assertEquals(success, preferences.lastSuccess)
                assertTrue(preferences.files().contains(newest))
                assertEquals(2, preferences.files().size)
                assertEquals(3, directory.listFiles().size)
                assertTrue(!preferences.cleanupNeeded)
            }
            "failedCommit" -> {
                // Keep the real deadline in the future to distinguish cleanup from a due export.
                runBlocking {
                    AutoBackup.save(context, settings.copy(hour = (settings.hour + 1) % 24), null)
                }
                val before = preferences.files()
                val success = preferences.lastSuccess
                val nextAt = preferences.nextAt
                val document =
                    requireNotNull(
                        directory.createFile("application/octet-stream", "uncommitted-backup.xike")
                    )
                requireNotNull(context.contentResolver.openOutputStream(document.uri)).use {
                    store.writeEncryptedBackup(it, password)
                }
                requireNotNull(context.contentResolver.openInputStream(document.uri)).use {
                    store.inspectBackup(it, password)
                }
                val sharedPreferencesDirectory =
                    java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                val mode = android.system.Os.stat(sharedPreferencesDirectory.path).st_mode and 511
                runBlocking {
                    AutoBackup.mutex.withLock {
                        preferences.pending = preferences.pending + document.uri.toString()
                        try {
                            // A real filesystem failure, not a mocked commit result.
                            android.system.Os.chmod(sharedPreferencesDirectory.path, 365)
                            val failed = runCatching {
                                preferences.completeBackup(
                                    com.xike.app.backup.AutoBackupFile(
                                        document.uri.toString(),
                                        settings.directory,
                                        System.currentTimeMillis(),
                                    ),
                                    nextAt + TimeUnit.DAYS.toMillis(1),
                                )
                            }
                            assertTrue(failed.isFailure)
                            assertEquals(before, preferences.files())
                            assertEquals(success, preferences.lastSuccess)
                            assertEquals(nextAt, preferences.nextAt)
                            assertTrue(preferences.pending.contains(document.uri.toString()))
                        } finally {
                            android.system.Os.chmod(sharedPreferencesDirectory.path, mode)
                        }
                    }
                }
                runDueWorker(expectSuccess = true, makeDue = false)
                assertEquals(before, preferences.files())
                assertEquals(success, preferences.lastSuccess)
                assertEquals(3, directory.listFiles().size)
                assertTrue(preferences.pending.isEmpty())
            }
            "polling" -> {
                val before =
                    manager.getWorkInfosForUniqueWork("xike-auto-backup").get().single {
                        !it.state.isFinished
                    }
                runBlocking {
                    AutoBackup.save(context, settings.copy(hour = (settings.hour + 1) % 24), null)
                }
                val after =
                    manager.getWorkInfosForUniqueWork("xike-auto-backup").get().single {
                        !it.state.isFinished
                    }
                assertEquals(before.id, after.id)
                assertTrue(
                    after.nextScheduleTimeMillis <=
                        System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(16)
                )
            }
            "concurrent" -> {
                val before = preferences.files().map { it.uri }.toSet()
                preferences.save(settings, null, System.currentTimeMillis() - 60_000)
                val requests = List(2) { OneTimeWorkRequestBuilder<AutoBackupWorker>().build() }
                manager.enqueue(requests).result.get()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
                while (
                    System.nanoTime() < deadline &&
                        requests.any {
                            manager.getWorkInfoById(it.id).get()?.state != WorkInfo.State.SUCCEEDED
                        }
                ) Thread.sleep(100)
                requests.forEach {
                    assertEquals(
                        WorkInfo.State.SUCCEEDED,
                        manager.getWorkInfoById(it.id).get()?.state,
                    )
                }
                val added = preferences.files().map { it.uri }.toSet() - before
                assertEquals(1, added.size)
                assertEquals(2, preferences.files().size)
            }
            "revoke" -> {
                val before = preferences.files()
                val success = preferences.lastSuccess
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(settings.directory),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                runDueWorker(expectSuccess = false)
                assertEquals(before, preferences.files())
                assertEquals(success, preferences.lastSuccess)
                assertEquals("directory", preferences.status)
            }
            "schedule" -> {
                val next = java.time.ZonedDateTime.now().plusMinutes(1)
                val nextSettings =
                    settings.copy(weekday = 0, hour = next.hour, minute = next.minute)
                check(
                    context
                        .getSharedPreferences("auto-backup-verification", Context.MODE_PRIVATE)
                        .edit()
                        .putLong("baseline", preferences.lastSuccess)
                        .commit()
                )
                runBlocking { AutoBackup.save(context, nextSettings, null) }
                assertEquals(next.hour, preferences.settings().hour)
                assertEquals(next.minute, preferences.settings().minute)
            }
            "verifyScheduled" -> {
                val baseline =
                    context
                        .getSharedPreferences("auto-backup-verification", Context.MODE_PRIVATE)
                        .getLong("baseline", 0)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
                while (preferences.lastSuccess <= baseline && System.nanoTime() < deadline) Thread
                    .sleep(250)
                assertTrue(preferences.lastSuccess > baseline)
                assertEquals(2, preferences.files().size)
                assertTrue(preferences.nextAt > System.currentTimeMillis())
            }
            else -> error("Specify a documented automatic-backup verification phase")
        }
    }
}
