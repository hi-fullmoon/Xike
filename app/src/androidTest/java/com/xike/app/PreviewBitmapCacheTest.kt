package com.xike.app

import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class PreviewBitmapCacheTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun openImage() = InstrumentationRegistry.getInstrumentation().context.assets.open("moment.png")

    @Test
    fun disposedPhotoReturnsWithoutOpeningItsSourceAgain() {
        val visible = mutableStateOf(true)
        val reads = AtomicInteger()
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                if (visible.value) {
                    val bitmap = rememberPreviewBitmap("platform-icon", 360) {
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
        assertEquals(2, reads.get())
    }

    @Test
    fun concurrentRequestsReuseTheSameDecodedBitmap() = runBlocking {
        val cache = PreviewBitmapCache()
        val reads = AtomicInteger()
        coroutineScope {
            val requests = List(8) {
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
        assertEquals(2, reads.get())
    }

    @Test
    fun evictsLeastRecentlyUsedPreviewByActualByteSize() = runBlocking {
        val bitmap = PreviewBitmapCache().load("size", 360, ::openImage)!!
        val cache = PreviewBitmapCache(bitmap.asAndroidBitmap().allocationByteCount * 3)
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
        val probe = PreviewBitmapCache()
        val small = probe.load("moment.png", 1024, ::openImage)!!
        val cache = PreviewBitmapCache(small.asAndroidBitmap().allocationByteCount * 4)
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
        assertEquals(2, reads.get())
    }

    @Test
    fun galleryEvictionKeepsTheListThumbnailAndDoesNotReadItAgain() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val probe = PreviewBitmapCache()
        val firstGallery = probe.load("moment.png", 2048) { assets.open("moment.png") }!!
        val secondGallery = probe.load("archive.png", 2048) { assets.open("archive.png") }!!
        val galleryBudget = maxOf(
            firstGallery.asAndroidBitmap().allocationByteCount,
            secondGallery.asAndroidBitmap().allocationByteCount,
        )
        val cache = PreviewBitmapCache(galleryBudget * 4)
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
        assertEquals(2, reads.get())
    }
}
