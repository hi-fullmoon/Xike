package com.xike.app.backup

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xike.app.AppLocale
import com.xike.app.JournalStore
import com.xike.app.R
import com.xike.app.tr
import java.io.File
import java.io.FilterOutputStream
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Share the store monitor with foreground edits, restore and attachment cleanup. */
internal object JournalStores {
    @Volatile private var instance: JournalStore? = null

    fun get(context: Context): JournalStore =
        instance
            ?: synchronized(this) {
                instance ?: JournalStore(context.applicationContext).also { instance = it }
            }
}

internal object AutoBackup {
    val mutex = Mutex()
    private const val WORK_NAME = "xike-auto-backup"
    private const val WAKE_NAME = "xike-auto-backup-deadline"

    fun ensurePeriodic(context: Context) {
        // Keep this independent of calendar deadlines and configuration changes. If the process
        // dies while saving or scheduling a deadline, persisted preferences remain authoritative.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(15, TimeUnit.MINUTES).build(),
            )
            .result
            .get()
    }

    fun schedule(context: Context, nextAt: Long) {
        ensurePeriodic(context)
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                WAKE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoBackupWorker>()
                    .setInitialDelay(
                        (nextAt - System.currentTimeMillis()).coerceAtLeast(0),
                        TimeUnit.MILLISECONDS,
                    )
                    .build(),
            )
            .result
            .get()
    }

    suspend fun save(context: Context, settings: AutoBackupSettings, password: String?) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val preferences = AutoBackupPreferences(context)
                val previous = preferences.settings()
                val nextAt =
                    if (
                        previous.enabled &&
                            settings.enabled &&
                            previous.weekday == settings.weekday &&
                            previous.hour == settings.hour &&
                            previous.minute == settings.minute &&
                            preferences.nextAt > 0
                    )
                        reconciledAutoBackupAt(settings, preferences.nextAt, ZonedDateTime.now())
                    else nextAutoBackupAt(settings, ZonedDateTime.now()).toInstant().toEpochMilli()
                if (settings.enabled) {
                    if (settings.encrypted)
                        require((password ?: preferences.password).orEmpty().length >= 8)
                    AutoBackupStorage.requireWritable(context, settings.directory)
                }
                // Arm durable polling before committing. A process interruption must not leave
                // newly saved preferences dependent on an old, distant calendar wake-up.
                if (settings.enabled) ensurePeriodic(context)
                preferences.save(
                    settings,
                    password,
                    nextAt,
                )
                if (settings.enabled) schedule(context, nextAt)
                else {
                    WorkManager.getInstance(context).cancelUniqueWork(WAKE_NAME).result.get()
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).result.get()
                }
            }
        }
}

class AutoBackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            AutoBackup.mutex.withLock {
                var preferences: AutoBackupPreferences? = null
                try {
                    preferences = AutoBackupPreferences(applicationContext)
                    val settings = preferences.settings()
                    if (!settings.enabled) return@withLock Result.success()
                    val pendingAt = preferences.nextAt
                    val nextAt = reconciledAutoBackupAt(settings, pendingAt, ZonedDateTime.now())
                    if (nextAt != pendingAt)
                        preferences.recordResult(preferences.status, nextAt = nextAt)
                    if (nextAt > System.currentTimeMillis()) {
                        if (preferences.cleanupNeeded)
                            cleanupAutoBackups(applicationContext, preferences, settings)
                        return@withLock Result.success()
                    }
                    AppLocale.initialize(applicationContext)
                    val process = ActivityManager.RunningAppProcessInfo()
                    ActivityManager.getMyMemoryState(process)
                    // A normal scheduled job does not grant Android 12+ permission to start an
                    // FGS. Run under the ordinary worker budget when the app is in the background.
                    if (
                        Build.VERSION.SDK_INT < 31 ||
                            process.importance <=
                                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    ) {
                        try {
                            setForeground(backupForegroundInfo(applicationContext))
                        } catch (error: Exception) {
                            // The app can move to the background between the check and promotion.
                            if (
                                Build.VERSION.SDK_INT < 31 ||
                                    error !is ForegroundServiceStartNotAllowedException
                            )
                                throw error
                        }
                    }
                    performAutoBackup(applicationContext, preferences, settings)
                    Result.success()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // Only fixed reason codes are persisted; provider errors can contain private
                    // paths.
                    runCatching {
                        preferences?.recordResult(
                            if (error is SecurityException) "directory" else "failed"
                        )
                    }
                    Result.retry()
                }
            }
        }
}

private fun backupForegroundInfo(context: Context): ForegroundInfo {
    val channel = "xike-auto-backup"
    context
        .getSystemService(NotificationManager::class.java)
        .createNotificationChannel(
            NotificationChannel(
                channel,
                tr("自动备份", "Automatic backup"),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    val notification =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr("自动备份", "Automatic backup"))
            .setContentText(tr("正在创建备份…", "Creating a backup…"))
            .setOngoing(true)
            .build()
    return ForegroundInfo(403, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}

internal suspend fun performAutoBackup(
    context: Context,
    preferences: AutoBackupPreferences,
    settings: AutoBackupSettings,
) {
    val password =
        if (settings.encrypted)
            requireNotNull(preferences.password).also { require(it.length >= 8) }
        else null
    AutoBackupStorage.requireWritable(context, settings.directory)
    val store = JournalStores.get(context)
    val coroutine = currentCoroutineContext()
    // A fixed, private staging path is overwritten after a process interruption.
    val staged = File(context.cacheDir, "xike-auto-backup-stage.xike")
    try {
        cleanupPendingBackups(context, preferences)
        synchronized(store) {
            store.initialize(loadEntries = false)
            staged.outputStream().use { output ->
                store.writeBackup(
                    object : FilterOutputStream(output) {
                        override fun write(bytes: ByteArray, offset: Int, length: Int) {
                            coroutine.ensureActive()
                            out.write(bytes, offset, length)
                        }

                        override fun write(value: Int) {
                            coroutine.ensureActive()
                            out.write(value)
                        }
                    },
                    password,
                )
            }
            staged.inputStream().use { store.inspectBackup(it, password) }
        }
        coroutine.ensureActive()
        val document =
            AutoBackupStorage.create(
                context,
                settings.directory,
                "xike-auto-${System.currentTimeMillis()}-${UUID.randomUUID()}.${if (settings.encrypted) "xike" else "zip"}",
            )
        preferences.pending = preferences.pending + document.toString()
        requireNotNull(context.contentResolver.openOutputStream(document, "wt")).use { output ->
            staged.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutine.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
        fun digest(input: java.io.InputStream): ByteArray = input.use {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutine.ensureActive()
                val count = it.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest()
        }
        check(
            MessageDigest.isEqual(
                digest(staged.inputStream()),
                digest(requireNotNull(context.contentResolver.openInputStream(document))),
            )
        )
        AutoBackupStorage.publish(context, document)
        val now = System.currentTimeMillis()
        preferences.completeBackup(
            AutoBackupFile(document.toString(), settings.directory, now),
            nextAutoBackupAt(settings, ZonedDateTime.now()).toInstant().toEpochMilli(),
        )
        cleanupAutoBackups(context, preferences, settings)
    } finally {
        staged.delete()
    }
}

private suspend fun cleanupPendingBackups(context: Context, preferences: AutoBackupPreferences) {
    for (pending in preferences.pending) {
        currentCoroutineContext().ensureActive()
        val removed = runCatching {
            if (preferences.files().any { it.uri == pending }) true
            else AutoBackupStorage.deleteTracked(context, Uri.parse(pending))
        }
            .getOrDefault(false)
        // An inaccessible old folder must not block backups to a newly selected folder.
        if (removed) preferences.pending = preferences.pending - pending
    }
}

private suspend fun cleanupAutoBackups(
    context: Context,
    preferences: AutoBackupPreferences,
    settings: AutoBackupSettings,
) {
    cleanupPendingBackups(context, preferences)
    // Only prune after the new file was verified and durably tracked.
    var cleanupFailed = preferences.pending.isNotEmpty()
    val obsolete =
        preferences
            .files()
            .filter { it.directory == settings.directory }
            .asReversed()
            .drop(settings.keepCount)
    for (old in obsolete) {
        currentCoroutineContext().ensureActive()
        val deleted = runCatching {
            AutoBackupStorage.deleteTracked(context, Uri.parse(old.uri))
        }
            .getOrDefault(false)
        if (deleted) preferences.saveFiles(preferences.files().filterNot { it.uri == old.uri })
        else cleanupFailed = true
    }
    preferences.recordCleanup(cleanupFailed)
}

class AutoBackupRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED))
            return
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "xike-auto-backup-reconcile",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoBackupReconcileWorker>().build(),
            )
    }
}

class AutoBackupReconcileWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            AutoBackup.mutex.withLock {
                try {
                    val preferences = AutoBackupPreferences(applicationContext)
                    val settings = preferences.settings()
                    if (settings.enabled) {
                        val nextAt =
                            reconciledAutoBackupAt(
                                settings,
                                preferences.nextAt,
                                ZonedDateTime.now(),
                            )
                        preferences.recordResult(preferences.status, nextAt = nextAt)
                        AutoBackup.schedule(applicationContext, nextAt)
                    }
                    Result.success()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Result.retry()
                }
            }
        }
}
