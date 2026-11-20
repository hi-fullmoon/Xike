package com.xike.app

import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.exifinterface.media.ExifInterface
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.xike.app.media.JournalAttachmentStore
import com.xike.app.media.MAX_IMAGE_BYTES
import com.xike.app.ui.components.GalleryImageLoader
import com.xike.app.ui.components.GalleryPhoto
import com.xike.app.ui.components.PreviewBitmapCache
import com.xike.app.ui.components.rememberPreviewBitmap
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PreviewBitmapCacheTest {
    @get:Rule val composeRule = createComposeRule()
    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun openImage() =
        InstrumentationRegistry.getInstrumentation().context.assets.open("moment.png")

    @Test
    fun thumbnailMatchesGalleryForEveryExifRotationAndReflection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("xike-exif-", ".jpg", context.cacheDir)
        val encrypted = File(file.parentFile, "${file.name}.encrypted")
        val attachments =
            JournalAttachmentStore(
                context,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            )
        val original = openImage().use { android.graphics.BitmapFactory.decodeStream(it) }!!
        val loader = GalleryImageLoader.create(context)
        try {
            file.outputStream().use {
                check(original.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it))
            }
            for (orientation in 1..8) {
                ExifInterface(file).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                encrypted.delete()
                file.inputStream().use {
                    attachments.writeEncryptedImage(encrypted, it, MAX_IMAGE_BYTES, null)
                }
                val thumbnail =
                    PreviewBitmapCache(context, 4)
                        .load("exif-$orientation", 4096) { attachments.openEncrypted(encrypted) }!!
                        .asAndroidBitmap()
                val result =
                    loader.execute(
                        ImageRequest.Builder(context)
                            .data(GalleryPhoto("exif-$orientation", file::inputStream))
                            .size(Size.ORIGINAL)
                            .allowHardware(false)
                            .build()
                    )
                val gallery = requireNotNull(result.image).toBitmap()
                try {
                    assertEquals("EXIF $orientation width", gallery.width, thumbnail.width)
                    assertEquals("EXIF $orientation height", gallery.height, thumbnail.height)
                    assertTrue("EXIF $orientation pixels", gallery.sameAs(thumbnail))
                } finally {
                    thumbnail.recycle()
                }
            }
        } finally {
            loader.shutdown()
            original.recycle()
            file.delete()
            encrypted.delete()
        }
    }

    @Test
    fun disposedPhotoReturnsWithoutOpeningItsSourceAgain() {
        val visible = mutableStateOf(true)
        val reads = AtomicInteger()
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                if (visible.value) {
                    val bitmap =
                        rememberPreviewBitmap("platform-icon", 360) {
                            reads.incrementAndGet()
                            openImage()
                        }
                    bitmap?.let { Image(it, "photo") }
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("photo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible.value = true }
        composeRule.waitForIdle()
        assertEquals(1, reads.get())
    }

    @Test
    fun concurrentRequestsReuseTheSameDecodedBitmap() = runBlocking {
        val cache = PreviewBitmapCache(context)
        val reads = AtomicInteger()
        coroutineScope {
            val requests =
                List(8) {
                    async {
                        cache.load("platform-icon", 360) {
                            reads.incrementAndGet()
                            openImage()
                        }
                    }
                }
            val bitmaps = requests.map { it.await() }
            assertNotNull(bitmaps.first())
            bitmaps.forEach { assertSame(bitmaps.first(), it) }
        }
        assertEquals(1, reads.get())
    }

    @Test
    fun evictsLeastRecentlyUsedPreviewByActualByteSize() = runBlocking {
        val bitmap = PreviewBitmapCache(context).load("size", 360, ::openImage)!!
        val cache = PreviewBitmapCache(context, bitmap.asAndroidBitmap().allocationByteCount * 3)
        cache.load("first", 360, ::openImage)
        cache.load("second", 360, ::openImage)
        assertNotNull(cache.get("first", 360))
        cache.load("third", 360, ::openImage)
        assertNull(cache.get("second", 360))
        assertNotNull(cache.get("first", 360))
        assertNotNull(cache.get("third", 360))
        assertNull(cache.get("first", 720))
    }

    @Test
    fun oversizedGalleryPreviewStillLoadsWithoutEvictingExistingImages() = runBlocking {
        val probe = PreviewBitmapCache(context)
        val small = probe.load("moment.png", 1024, ::openImage)!!
        val cache = PreviewBitmapCache(context, small.asAndroidBitmap().allocationByteCount * 4)
        val reads = AtomicInteger()
        val openSmall = {
            reads.incrementAndGet()
            openImage()
        }
        val existing = cache.load("moment.png", 1024, openSmall)
        assertNotNull(existing)
        val oversized = cache.load("moment.png", 2048, ::openImage)
        assertNotNull(oversized)
        assertNull(cache.get("moment.png", 2048))
        assertSame(existing, cache.get("moment.png", 1024))
        assertSame(existing, cache.load("moment.png", 1024, openSmall))
        assertEquals(1, reads.get())
    }

    @Test
    fun galleryEvictionKeepsTheListThumbnailAndDoesNotReadItAgain() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val probe = PreviewBitmapCache(context)
        val firstGallery = probe.load("moment.png", 2048) { assets.open("moment.png") }!!
        val secondGallery = probe.load("archive.png", 2048) { assets.open("archive.png") }!!
        val galleryBudget =
            maxOf(
                firstGallery.asAndroidBitmap().allocationByteCount,
                secondGallery.asAndroidBitmap().allocationByteCount,
            )
        val cache = PreviewBitmapCache(context, galleryBudget * 4)
        val reads = AtomicInteger()
        val openThumbnail = {
            reads.incrementAndGet()
            assets.open("moment.png")
        }
        val thumbnail = cache.load("moment.png", 960, openThumbnail)
        assertNotNull(thumbnail)
        cache.load("moment.png", 2048) { assets.open("moment.png") }
        cache.load("archive.png", 2048) { assets.open("archive.png") }
        assertNull(cache.get("moment.png", 2048))
        assertNotNull(cache.get("archive.png", 2048))
        assertSame(thumbnail, cache.get("moment.png", 960))
        assertSame(thumbnail, cache.load("moment.png", 960, openThumbnail))
        assertEquals(1, reads.get())
    }
}
