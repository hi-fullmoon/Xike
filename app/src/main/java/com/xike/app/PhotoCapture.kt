package com.xike.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val CAMERA_ALBUM_NAME = "息刻"
private const val CAMERA_CAPTURE_PREFIX = "Xike_"
private const val CAMERA_CAPTURE_SUFFIX = ".jpg"
private const val ORPHAN_CAPTURE_MAX_AGE_SECONDS = 24L * 60L * 60L
private val cameraFileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

internal fun canTakePhoto(context: Context): Boolean {
    if (Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) == null) return false
    return runCatching {
        context.getSystemService(CameraManager::class.java)?.cameraIdList?.isNotEmpty() == true
    }.getOrDefault(false)
}

internal fun hasGalleryWriteAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

internal fun createCameraCaptureUri(context: Context): Uri {
    check(hasGalleryWriteAccess(context)) { "没有系统相册写入权限" }
    val displayName = CAMERA_CAPTURE_PREFIX +
        LocalDateTime.now().format(cameraFileNameFormatter) + "_" +
        UUID.randomUUID().toString().take(8) + CAMERA_CAPTURE_SUFFIX
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$CAMERA_ALBUM_NAME")
        } else {
            @Suppress("DEPRECATION")
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val album = File(pictures, CAMERA_ALBUM_NAME)
            check(album.isDirectory || album.mkdirs()) { "无法创建系统相册目录" }
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, File(album, displayName).absolutePath)
        }
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法在系统相册中创建照片")
}

internal fun isCameraCaptureUri(context: Context, uri: Uri): Boolean =
    uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY && runCatching {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.OWNER_PACKAGE_NAME)
        } else {
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        }
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                false
            } else {
                val ownedByApp = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    cursor.columnCount > 1 && cursor.getString(1) == context.packageName
                ownedByApp && cursor.getString(0)?.let { name ->
                    name.startsWith(CAMERA_CAPTURE_PREFIX) && name.endsWith(CAMERA_CAPTURE_SUFFIX)
                } == true
            }
        } == true
    }.getOrDefault(false)

internal fun cameraCaptureHasContent(context: Context, uri: Uri): Boolean =
    isCameraCaptureUri(context, uri) && runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length > 0L || context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } == true
        } == true
    }.getOrDefault(false)

internal fun finalizeCameraCapture(context: Context, uri: Uri): Boolean {
    return cameraCaptureHasContent(context, uri)
}

internal fun deleteCameraCapture(context: Context, uri: Uri) {
    if (isCameraCaptureUri(context, uri)) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}

internal fun pruneCameraCaptures(context: Context) {
    val staleBeforeSeconds = System.currentTimeMillis() / 1000L - ORPHAN_CAPTURE_MAX_AGE_SECONDS
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val selection = "${MediaStore.Images.Media.SIZE} = 0 AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND " +
        "${MediaStore.Images.Media.DATE_ADDED} < ?"
    val selectionArgs = arrayOf("$CAMERA_CAPTURE_PREFIX%", staleBeforeSeconds.toString())
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0),
                )
                context.contentResolver.delete(uri, null, null)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PhotoSourceDialog(
    cameraAvailable: Boolean,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = XikeContentMaxWidth,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("添加照片", style = MaterialTheme.typography.titleLarge)
            Surface(
                shape = XikeShapes.inner,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    PhotoSourceOption(
                        icon = Icons.Outlined.CameraAlt,
                        title = "拍照",
                        supporting = if (cameraAvailable) "拍摄后保存到系统相册" else "当前设备没有可用的相机",
                        enabled = cameraAvailable,
                        onClick = onTakePhoto,
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 72.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    PhotoSourceOption(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "从相册选择",
                        supporting = "可一次选择多张照片",
                        enabled = true,
                        onClick = onChoosePhotos,
                    )
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = XikeShapes.button,
            ) { Text("取消") }
        }
    }
}

@Composable
private fun PhotoSourceOption(
    icon: ImageVector,
    title: String,
    supporting: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 0.7f else 0.3f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                )
            }
        }
    }
}
