package com.xike.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.UUID

internal fun createVideoCaptureUri(context: Context): Uri {
    check(hasGalleryWriteAccess(context))
    val name = "Xike_video_${UUID.randomUUID()}.mp4"
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/息刻")
        } else {
            @Suppress("DEPRECATION")
            val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "息刻")
            check(album.isDirectory || album.mkdirs())
            @Suppress("DEPRECATION")
            put(MediaStore.Video.Media.DATA, File(album, name).absolutePath)
        }
    }
    return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: error(tr("无法准备视频拍摄，请重试。", "Unable to prepare video capture. Please retry."))
}

internal fun finishVideoCapture(context: Context, uri: Uri, captured: Boolean): Boolean {
    val readable = captured && context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length > 0 } == true
    if (!readable) { context.contentResolver.delete(uri, null, null); return false }
    return true
}
