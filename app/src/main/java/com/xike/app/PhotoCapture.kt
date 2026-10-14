package com.xike.app

import com.xike.app.data.MAX_IMAGES_PER_ENTRY

import com.xike.app.ui.components.XikeNotice

import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeSheetHorizontalPadding
import com.xike.app.ui.theme.XikeSheetBottomPadding
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.ui.theme.XikeContentMaxWidth



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
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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

internal fun canTakePhoto(context: Context): Boolean = canCaptureMedia(context, MediaStore.ACTION_IMAGE_CAPTURE)

internal fun canCaptureMedia(context: Context, captureAction: String): Boolean {
    if (Intent(captureAction).resolveActivity(context.packageManager) == null) return false
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
    check(hasGalleryWriteAccess(context)) { localizedText("没有系统相册写入权限") }
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
            check(album.isDirectory || album.mkdirs()) { localizedText("无法创建系统相册目录") }
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, File(album, displayName).absolutePath)
        }
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error(localizedText("无法在系统相册中创建照片"))
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

internal class JournalImagePickers(
    val openPhotoPicker: () -> Unit,
    val openCamera: () -> Unit,
    val pendingCameraUri: () -> String?,
)

@Composable
internal fun rememberJournalImagePickers(
    ownerKey: String = "draft",
    availableSlots: Int = MAX_IMAGES_PER_ENTRY,
    resultsReady: Boolean = true,
    retainRejectedCapture: Boolean = false,
    onPendingChange: (String, Boolean) -> Unit = { _, _ -> },
    onImagesPicked: (List<Uri>, SystemActivityReadAccess) -> Boolean,
): JournalImagePickers {
    val context = LocalContext.current
    var pendingCameraUriString by rememberSaveable(ownerKey) { mutableStateOf<String?>(null) }
    val photoPicker = rememberMediaActivityResultLauncher(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_ENTRY),
        "photo/$ownerKey/picker",
        resultsReady = resultsReady,
        onPendingChange = { onPendingChange("photo/$ownerKey/picker", it) },
    ) { uris, access ->
        onImagesPicked(uris, access)
    }
    val singlePhotoPicker = rememberMediaActivityResultLauncher(
        ActivityResultContracts.PickVisualMedia(),
        "photo/$ownerKey/single-picker",
        resultsReady = resultsReady,
        onPendingChange = { onPendingChange("photo/$ownerKey/single-picker", it) },
    ) { uri, access ->
        uri == null || onImagesPicked(listOf(uri), access)
    }
    val photoDocumentPicker = rememberMediaActivityResultLauncher(
        ActivityResultContracts.OpenMultipleDocuments(),
        "photo/$ownerKey/document",
        resultsReady = resultsReady,
        onPendingChange = { onPendingChange("photo/$ownerKey/document", it) },
    ) { uris, access ->
        onImagesPicked(uris, access)
    }
    val cameraLauncher = rememberSystemActivityResultLauncherWhenReady(
        ActivityResultContracts.TakePicture(), "photo/$ownerKey/capture",
        resultsReady = resultsReady,
        onPendingChange = { onPendingChange("photo/$ownerKey/capture", it) },
    ) { captured ->
        val captureUri = pendingCameraUriString?.let(Uri::parse)
        if (captureUri != null && finalizeCameraCapture(context, captureUri)) {
            val accepted = onImagesPicked(listOf(captureUri), SystemActivityReadAccess(context, emptyList()))
            if (accepted || !retainRejectedCapture) {
                pendingCameraUriString = null
                XikeNotice.makeText(context, localizedText("照片已添加并保存到系统相册"), XikeNotice.LENGTH_SHORT).show()
            }
            accepted || !retainRejectedCapture
        } else {
            pendingCameraUriString = null
            if (captureUri != null) {
                deleteCameraCapture(context, captureUri)
                if (captured) XikeNotice.makeText(context, localizedText("照片保存失败，请重试"), XikeNotice.LENGTH_SHORT).show()
            }
            true
        }
    }
    val openPhotoPicker = {
        val openDocuments = {
            val input = arrayOf("image/*")
            val contract = ActivityResultContracts.OpenMultipleDocuments()
            val canOpen = contract.createIntent(context, input)
                .resolveActivity(context.packageManager) != null
            if (canOpen) {
                photoDocumentPicker.launch(input)
            }
            canOpen
        }
        val opened = if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            runCatching {
                if (availableSlots == 1) {
                    singlePhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    photoPicker.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            .setMaxItems(availableSlots.coerceIn(2, MAX_IMAGES_PER_ENTRY))
                            .build(),
                    )
                }
                true
            }.onFailure { error ->
                Log.w("XikePhotoPicker", "Photo picker launch failed", error)
            }.getOrElse {
                runCatching(openDocuments).onFailure { error ->
                    Log.w("XikePhotoPicker", "Document picker launch failed", error)
                }.getOrDefault(false)
            }
        } else {
            runCatching(openDocuments).onFailure { error ->
                Log.w("XikePhotoPicker", "Document picker launch failed", error)
            }.getOrDefault(false)
        }
        if (!opened) {
            XikeNotice.makeText(context, localizedText("无法打开系统照片选择器"), XikeNotice.LENGTH_LONG).show()
        }
    }
    val launchSystemCamera = {
        runCatching { createCameraCaptureUri(context) }
            .onSuccess { captureUri ->
                pendingCameraUriString = captureUri.toString()
                runCatching { cameraLauncher.launch(captureUri) }
                    .onFailure { error ->
                        pendingCameraUriString = null
                        deleteCameraCapture(context, captureUri)
                        Log.w("XikeCamera", "Camera launch failed", error)
                        XikeNotice.makeText(context, localizedText("无法打开系统相机"), XikeNotice.LENGTH_LONG).show()
                    }
            }
            .onFailure { error ->
                Log.w("XikeCamera", "Camera capture file creation failed", error)
                XikeNotice.makeText(context, localizedText("无法准备拍照，请重试"), XikeNotice.LENGTH_LONG).show()
            }
    }
    val galleryWritePermissionLauncher = rememberSystemActivityResultLauncher(
        ActivityResultContracts.RequestPermission(),
        "photo/$ownerKey/storage-permission",
    ) { granted ->
        if (granted) {
            launchSystemCamera()
        } else {
            XikeNotice.makeText(context, localizedText("需要存储权限才能把照片保存到系统相册"), XikeNotice.LENGTH_LONG).show()
        }
    }
    return JournalImagePickers(
        openPhotoPicker = { if (availableSlots > 0) openPhotoPicker() },
        openCamera = {
            if (availableSlots <= 0) {
                Unit
            } else if (hasGalleryWriteAccess(context)) {
                launchSystemCamera()
            } else {
                galleryWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        },
        pendingCameraUri = { pendingCameraUriString },
    )
}

@Composable
internal fun PhotoSourceDialog(
    cameraAvailable: Boolean,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onDismiss: () -> Unit,
) {
    MediaSourceDialog(
        title = localizedText("添加照片"),
        captureIcon = Icons.Outlined.CameraAlt,
        captureTitle = localizedText("拍照"),
        captureSupporting = if (cameraAvailable) localizedText("拍摄后保存到系统相册") else localizedText("当前设备没有可用的相机"),
        gallerySupporting = localizedText("可一次选择多张照片"),
        cameraAvailable = cameraAvailable,
        onCapture = onTakePhoto,
        onChooseMedia = onChoosePhotos,
        onDismiss = onDismiss,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MediaSourceDialog(
    title: String,
    captureIcon: ImageVector,
    captureTitle: String,
    cameraAvailable: Boolean,
    onCapture: () -> Unit,
    onChooseMedia: () -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    captureSupporting: String? = null,
    gallerySupporting: String? = null,
) {
    val density = LocalDensity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = XikeContentMaxWidth,
        shape = XikeShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = {
            val safeInsets = WindowInsets.safeDrawing
            // Only reserve the part of the top safe area that the sheet actually overlaps.
            val sheetTop = runCatching { sheetState.requireOffset() }.getOrDefault(Float.POSITIVE_INFINITY)
            WindowInsets(
                left = safeInsets.getLeft(density, LocalLayoutDirection.current),
                top = (safeInsets.getTop(density) - sheetTop).coerceAtLeast(0f).toInt(),
                right = safeInsets.getRight(density, LocalLayoutDirection.current),
                bottom = safeInsets.getBottom(density),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = XikeSheetHorizontalPadding).padding(bottom = XikeSheetBottomPadding),
            verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = XikeShapes.inner,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    MediaSourceOption(
                        icon = captureIcon,
                        title = captureTitle,
                        supporting = captureSupporting,
                        enabled = cameraAvailable,
                        onClick = onCapture,
                    )
                    HorizontalDivider(
                        Modifier.padding(
                            start = XikeInnerCardPadding + 44.dp + 12.dp,
                            end = XikeInnerCardPadding,
                        ),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    MediaSourceOption(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = localizedText("从相册选择"),
                        supporting = gallerySupporting,
                        enabled = true,
                        onClick = onChooseMedia,
                    )
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = XikeShapes.button,
            ) { Text(localizedText("取消")) }
        }
    }
}

@Composable
private fun MediaSourceOption(
    icon: ImageVector,
    title: String,
    supporting: String?,
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
            modifier = Modifier.padding(XikeInnerCardPadding),
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
                supporting?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                    )
                }
            }
        }
    }
}
