package com.xike.app

import android.content.Context
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Scale
import coil3.size.Size
import coil3.size.Precision
import coil3.decode.DataSource
import java.io.InputStream
import okio.FileSystem
import okio.buffer
import okio.source
import kotlin.math.sqrt

internal data class GalleryPhoto(val source: String, val openStream: () -> InputStream?)

/** Shared decoded-image cache, separate from list thumbnails. No plaintext disk cache. */
internal object GalleryImageLoader {
    @Volatile private var instance: ImageLoader? = null

    private fun memoryBudgetBytes(): Long = (Runtime.getRuntime().maxMemory() / 8)
        .coerceIn(8L * 1024 * 1024, 48L * 1024 * 1024)

    internal fun decodeSize(width: Int, height: Int): Size {
        require(width > 0 && height > 0)
        // Budget for three retained photos plus allocation/rounding headroom. HDR images
        // can use eight bytes per pixel, so do not assume every photo is ARGB_8888.
        val pixelBudget = memoryBudgetBytes() / 4 / 8
        val ratio = minOf(
            1.0,
            2048.0 / maxOf(width, height),
            sqrt(pixelBudget.toDouble() / (width.toDouble() * height)),
        )
        return Size(
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
        )
    }

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    internal fun create(context: Context): ImageLoader = ImageLoader.Builder(context)
        .diskCache(null)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizeBytes(memoryBudgetBytes())
                .build()
        }
        .components {
            add(Keyer<GalleryPhoto> { data, _ -> "gallery:${data.source}" })
            add(object : Fetcher.Factory<GalleryPhoto> {
                override fun create(data: GalleryPhoto, options: Options, imageLoader: ImageLoader): Fetcher =
                    Fetcher {
                        val stream = checkNotNull(data.openStream()) { "Photo is unavailable" }
                        SourceFetchResult(
                            source = ImageSource(stream.source().buffer(), FileSystem.SYSTEM),
                            mimeType = null,
                            dataSource = DataSource.DISK,
                        )
                    }
            })
            // Decode from the stream, including EXIF rotation, without plaintext temporary files.
            add(BitmapFactoryDecoder.Factory())
        }
        .build()

    fun request(context: Context, photo: GalleryPhoto): ImageRequest = ImageRequest.Builder(context)
        .data(photo)
        // Dialog insets settle after its first frame. Use a stable screen-sized request so
        // slightly different page constraints do not reject an otherwise valid cached image.
        .size(decodeSize(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels))
        .scale(Scale.FIT)
        .precision(Precision.INEXACT)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()
}
