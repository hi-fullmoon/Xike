package com.xike.app.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal val LocalPreviewBitmapCache = staticCompositionLocalOf<PreviewBitmapCache?> { null }

/** Retains decoded previews across lazy item disposal, within the current UI session. */
internal class PreviewBitmapCache(
    context: Context,
    maxBytes: Int =
        (Runtime.getRuntime().maxMemory() / 16)
            .coerceIn(4L * 1024 * 1024, 24L * 1024 * 1024)
            .toInt(),
) {
    private val context = context.applicationContext

    private data class Key(val source: String, val maxDimension: Int)

    init {
        require(maxBytes >= 4)
    }

    // Reserve three quarters for thumbnails; gallery previews cannot evict them.
    private val galleryImages = bitmapCache(maxBytes / 4)
    private val thumbnailImages = bitmapCache(maxBytes - maxBytes / 4)

    private fun bitmapCache(byteLimit: Int) =
        object : LruCache<Key, ImageBitmap>(byteLimit) {
            override fun sizeOf(key: Key, value: ImageBitmap): Int =
                value.asAndroidBitmap().allocationByteCount
        }

    private fun imagesFor(maxDimension: Int) =
        if (maxDimension > 960) galleryImages else thumbnailImages

    // Serialize decoding to bound transient memory and recheck queued duplicate requests.
    private val decodeMutex = Mutex()

    fun get(source: String, maxDimension: Int): ImageBitmap? =
        imagesFor(maxDimension).get(Key(source, maxDimension))

    suspend fun load(
        source: String,
        maxDimension: Int,
        openStream: () -> InputStream?,
    ): ImageBitmap? =
        withContext(Dispatchers.IO) {
            decodeMutex.withLock {
                val key = Key(source, maxDimension)
                val images = imagesFor(maxDimension)
                images.get(key)
                    ?: decodeScaledPreview(source, openStream, maxDimension)?.also {
                        // An oversized entry would evict every cached image, including itself.
                        if (it.asAndroidBitmap().allocationByteCount <= images.maxSize()) {
                            images.put(key, it)
                        }
                    }
            }
        }

    private suspend fun decodeScaledPreview(
        source: String,
        openStream: () -> InputStream?,
        maxDimension: Int,
    ): ImageBitmap? {
        require(maxDimension > 0)
        return try {
            val request =
                ImageRequest.Builder(context)
                    .data(GalleryPhoto(source, openStream))
                    .size(maxDimension, maxDimension)
                    .scale(Scale.FIT)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    // PreviewBitmapCache owns this budget; do not duplicate gallery cache entries.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            GalleryImageLoader.get(context).execute(request).image?.toBitmap()?.asImageBitmap()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
