package com.xike.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xike.app.backup.AutoBackup
import com.xike.app.backup.AutoBackupPreferences
import com.xike.app.backup.AutoBackupSettings
import com.xike.app.backup.AutoBackupStorage
import com.xike.app.backup.AutoBackupWorker
import com.xike.app.backup.DEFAULT_AUTO_BACKUP_DIRECTORY
import com.xike.app.backup.JournalStores
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Own fresh API 29+ device; use configure, write, verify, pending, resume as separate processes.
 */
class DefaultAutoBackupProcessTest {
    @Test
    fun defaultDownloadsAreEncryptedVerifiedAndRetainedAcrossProcesses() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("defaultBackupIsolated") == "true")
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AutoBackupPreferences(context)
        val manager = WorkManager.getInstance(context)
        val state =
            context.getSharedPreferences("xike-default-backup-verification", Context.MODE_PRIVATE)
        val phase = arguments.getString("defaultBackupPhase")
        if (phase == "preserveCustom") {
            val settings = preferences.settings()
            assertTrue(settings.directory.startsWith("content://"))
            assertNotNull(AutoBackupStorage.displayName(context, settings.directory))
            runBlocking { AutoBackup.save(context, settings, null) }
            assertEquals(settings, AutoBackupPreferences(context).settings())
            return
        }
        assertEquals(DEFAULT_AUTO_BACKUP_DIRECTORY, preferences.settings().directory)
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE),
        )
        assertFalse(AutoBackupStorage.needsLegacyPermission(context, DEFAULT_AUTO_BACKUP_DIRECTORY))
        if (phase == "configure") {
            assertFalse(preferences.settings().enabled)
            assertTrue(preferences.files().isEmpty())
            assertTrue(context.contentResolver.persistedUriPermissions.isEmpty())
            val password =
                Base64.getEncoder().encodeToString(ByteArray(24).also(SecureRandom()::nextBytes))
            runBlocking {
                AutoBackup.save(
                    context,
                    AutoBackupSettings(enabled = true, keepCount = 2),
                    password,
                )
            }
            return
        }
        val settings = preferences.settings()
        val password = requireNotNull(preferences.password)
        val store = JournalStores.get(context).also { it.initialize() }

        fun runWorker(due: Boolean) {
            if (due) preferences.save(settings, null, System.currentTimeMillis() - 60_000)
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            manager.enqueue(request).result.get()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (
                System.nanoTime() < deadline &&
                    manager.getWorkInfoById(request.id).get()?.state != WorkInfo.State.SUCCEEDED
            ) Thread.sleep(100)
            assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(request.id).get()?.state)
        }

        fun verifyFile(uri: Uri, pending: Int = 0) {
            requireNotNull(
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            MediaStore.MediaColumns.IS_PENDING,
                            MediaStore.MediaColumns.DISPLAY_NAME,
                        ),
                        null,
                        null,
                        null,
                    )
                )
                .use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Download/Xike/", it.getString(0))
                    assertEquals(pending, it.getInt(1))
                    assertTrue(it.getString(2).endsWith(".xike"))
                }
            assertTrue(
                requireNotNull(context.contentResolver.openInputStream(uri))
                    .use(store::backupRequiresPassword)
            )
            val summary =
                requireNotNull(context.contentResolver.openInputStream(uri)).use {
                    store.inspectBackup(it, password)
                }
            assertEquals(store.entries().size, summary.entryCount)
        }

        when (phase) {
            "write" -> {
                assertTrue(preferences.files().isEmpty())
                val manual =
                    AutoBackupStorage.create(
                        context,
                        settings.directory,
                        "manual-verification.xike",
                    )
                requireNotNull(context.contentResolver.openOutputStream(manual)).use {
                    store.writeEncryptedBackup(it, password)
                }
                AutoBackupStorage.publish(context, manual)
                check(state.edit().putString("manual", manual.toString()).commit())
                repeat(3) { runWorker(due = true) }
                assertEquals(2, preferences.files().size)
                preferences.files().forEach { verifyFile(Uri.parse(it.uri)) }
                verifyFile(manual)
                assertTrue(preferences.pending.isEmpty())
                assertTrue(preferences.nextAt > System.currentTimeMillis())
                assertTrue(preferences.lastSuccess > 0)
            }
            "verify" -> {
                assertEquals(2, preferences.files().size)
                preferences.files().forEach {
                    val uri = Uri.parse(it.uri)
                    verifyFile(uri)
                    assertTrue(
                        runCatching {
                            requireNotNull(context.contentResolver.openInputStream(uri)).use { input
                                ->
                                store.inspectBackup(input, "$password-wrong")
                            }
                        }
                            .isFailure
                    )
                }
                verifyFile(Uri.parse(requireNotNull(state.getString("manual", null))))
            }
            "pending" -> {
                val uri =
                    AutoBackupStorage.create(
                        context,
                        settings.directory,
                        "interrupted-verification.xike",
                    )
                preferences.pending = preferences.pending + uri.toString()
                requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                    store.writeEncryptedBackup(it, password)
                }
                verifyFile(uri, pending = 1)
                check(state.edit().putString("pending", uri.toString()).commit())
            }
            "resume" -> {
                val before = preferences.files()
                val success = preferences.lastSuccess
                runWorker(due = false)
                assertEquals(before, preferences.files())
                assertEquals(success, preferences.lastSuccess)
                assertTrue(preferences.pending.isEmpty())
                assertFalse(preferences.cleanupNeeded)
                val uri = Uri.parse(requireNotNull(state.getString("pending", null)))
                requireNotNull(
                        context.contentResolver.query(
                            uri,
                            arrayOf(MediaStore.MediaColumns._ID),
                            null,
                            null,
                            null,
                        )
                    )
                    .use { assertEquals(0, it.count) }
                preferences.files().forEach { verifyFile(Uri.parse(it.uri)) }
            }
            else -> error("Specify a documented default-backup verification phase")
        }
    }
}
