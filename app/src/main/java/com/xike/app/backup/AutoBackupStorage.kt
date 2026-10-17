package com.xike.app.backup

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.xike.app.tr
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes

/** Public Downloads owns the default location; user-picked folders retain their SAF grants. */
internal object AutoBackupStorage {
    private const val FOLDER_NAME = "Xike"

    fun needsLegacyPermission(context: Context, directory: String): Boolean =
        directory == DEFAULT_AUTO_BACKUP_DIRECTORY &&
            Build.VERSION.SDK_INT < 29 &&
            listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
                .any {
                    ContextCompat.checkSelfPermission(context, it) !=
                        PackageManager.PERMISSION_GRANTED
                }

    fun displayName(context: Context, directory: String): String? =
        if (directory == DEFAULT_AUTO_BACKUP_DIRECTORY) tr("下载 / Xike", "Downloads / Xike")
        else DocumentFile.fromTreeUri(context, Uri.parse(directory))?.name

    fun requireWritable(context: Context, directory: String) {
        if (directory == DEFAULT_AUTO_BACKUP_DIRECTORY) {
            if (needsLegacyPermission(context, directory))
                throw SecurityException("Storage permission required")
            check(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)
            if (Build.VERSION.SDK_INT < 29) {
                val folder = legacyFolder()
                check((folder.isDirectory || folder.mkdirs()) && folder.canWrite())
            }
        } else {
            val folder =
                DocumentFile.fromTreeUri(context, Uri.parse(directory)) ?: throw SecurityException()
            if (!folder.isDirectory || !folder.canWrite()) throw SecurityException()
        }
    }

    fun create(context: Context, directory: String, name: String): Uri {
        require(File(name).name == name)
        requireWritable(context, directory)
        val mimeType = if (name.endsWith(".zip")) "application/zip" else "application/octet-stream"
        if (directory != DEFAULT_AUTO_BACKUP_DIRECTORY) {
            return requireNotNull(
                    DocumentFile.fromTreeUri(context, Uri.parse(directory))
                        ?.createFile(mimeType, name)
                )
                .uri
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME/",
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            return requireNotNull(
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            )
        }
        val file = File(legacyFolder(), name)
        check(file.createNewFile())
        return Uri.fromFile(file)
    }

    /** Make only the fully written and verified default download visible to other apps. */
    fun publish(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            check(context.contentResolver.update(uri, values, null, null) == 1)
        }
    }

    fun deleteTracked(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path)).canonicalFile
            require(file.parentFile == legacyFolder().canonicalFile)
            if (needsLegacyPermission(context, DEFAULT_AUTO_BACKUP_DIRECTORY))
                throw SecurityException()
            if (file.delete()) return true
            return try {
                Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                false
            } catch (_: NoSuchFileException) {
                true
            }
        }
        val mediaStore = uri.authority == MediaStore.AUTHORITY
        val deleted =
            if (mediaStore) context.contentResolver.delete(uri, null, null) > 0
            else DocumentFile.fromSingleUri(context, uri)?.delete() == true
        if (deleted) return true
        // Failed reads must never be interpreted as proof that a tracked file is absent.
        val id =
            if (mediaStore) MediaStore.MediaColumns._ID
            else DocumentsContract.Document.COLUMN_DOCUMENT_ID
        return context.contentResolver.query(uri, arrayOf(id), null, null, null)?.use {
            it.count == 0
        } ?: false
    }

    @Suppress("DEPRECATION")
    private fun legacyFolder() =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER_NAME,
        )
}
