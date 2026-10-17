package com.xike.app.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

internal data class AutoBackupFile(val uri: String, val directory: String, val createdAt: Long)

/** Device-local state and password never enter exported backups. */
internal class AutoBackupPreferences(context: Context) {
    private val preferences =
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "xike-auto-backup",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun settings() =
        AutoBackupSettings(
            enabled = preferences.getBoolean("enabled", false),
            weekday = preferences.getInt("weekday", 7),
            hour = preferences.getInt("hour", 0),
            minute = preferences.getInt("minute", 0),
            keepCount = preferences.getInt("keep", 5),
            directory =
                preferences.getString("directory", null)?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_AUTO_BACKUP_DIRECTORY,
            encrypted = preferences.getBoolean("encrypted", true),
        )

    fun save(settings: AutoBackupSettings, password: String?, nextAt: Long) {
        update {
            this.putBoolean("enabled", settings.enabled)
                .putBoolean("encrypted", settings.encrypted)
                .putInt("weekday", settings.weekday)
                .putInt("hour", settings.hour)
                .putInt("minute", settings.minute)
                .putInt("keep", settings.keepCount)
                .putString("directory", settings.directory)
                .putString("next-local", localDeadline(nextAt))
                .apply {
                    if (settings.encrypted && password != null) putString("password", password)
                }
        }
    }

    val password: String?
        get() = preferences.getString("password", null)

    val nextAt: Long
        get() =
            preferences.getString("next-local", null)?.let {
                LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } ?: 0L

    private fun localDeadline(value: Long): String =
        Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()

    val lastSuccess: Long
        get() = preferences.getLong("success", 0)

    val status: String?
        get() = preferences.getString("status", null)

    val cleanupNeeded: Boolean
        get() = preferences.getBoolean("cleanup-needed", false)

    fun recordCleanup(failed: Boolean) {
        update {
            this.putBoolean("cleanup-needed", failed)
                .putString("status", if (failed) "cleanup" else null)
        }
    }

    fun recordResult(status: String?, nextAt: Long? = null, success: Long? = null) {
        update {
            this.putString("status", status).apply {
                if (nextAt != null) putString("next-local", localDeadline(nextAt))
                if (success != null) putLong("success", success)
            }
        }
    }

    var pending: Set<String>
        get() = preferences.getStringSet("pending-files", emptySet())!!.toSet()
        set(value) {
            update {
                this.putStringSet("pending-files", value).apply {
                    if (value.isNotEmpty()) putBoolean("cleanup-needed", true)
                }
            }
        }

    fun files(): List<AutoBackupFile> {
        val array = JSONArray(preferences.getString("files", "[]"))
        return (0 until array.length()).map {
            val item = array.getJSONObject(it)
            AutoBackupFile(item.getString("uri"), item.getString("directory"), item.getLong("at"))
        }
    }

    fun saveFiles(files: List<AutoBackupFile>) {
        update { putString("files", encodeFiles(files)) }
    }

    /** Commit the verified file, deadline and success together before any destructive cleanup. */
    fun completeBackup(file: AutoBackupFile, nextAt: Long) {
        update {
            this.putString("files", encodeFiles(files() + file))
                .putStringSet("pending-files", pending - file.uri)
                .putLong("success", file.createdAt)
                .putBoolean("cleanup-needed", true)
                .putString("next-local", localDeadline(nextAt))
                .remove("status")
        }
    }

    private fun update(change: SharedPreferences.Editor.() -> Unit) {
        val before = preferences.all
        if (preferences.edit().apply(change).commit()) return
        // SharedPreferences changes its in-process cache even when commit() returns false.
        // Restore the last known durable values before another worker can decide to delete files.
        val rollback = preferences.edit()
        for (key in before.keys + preferences.all.keys) {
            when (val value = before[key]) {
                null -> rollback.remove(key)
                is String -> rollback.putString(key, value)
                is Boolean -> rollback.putBoolean(key, value)
                is Int -> rollback.putInt(key, value)
                is Long -> rollback.putLong(key, value)
                is Float -> rollback.putFloat(key, value)
                is Set<*> -> rollback.putStringSet(key, value.map { it as String }.toSet())
                else -> error("Unsupported automatic backup preference type")
            }
        }
        // Even if storage remains unwritable, this restores the cache synchronously.
        rollback.commit()
        error("Unable to persist automatic backup state")
    }

    private fun encodeFiles(files: List<AutoBackupFile>): String {
        val array = JSONArray()
        files.forEach {
            array.put(
                JSONObject()
                    .put("uri", it.uri)
                    .put("directory", it.directory)
                    .put("at", it.createdAt)
            )
        }
        return array.toString()
    }
}
