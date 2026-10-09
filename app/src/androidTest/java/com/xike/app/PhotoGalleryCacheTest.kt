package com.xike.app

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import coil3.decode.DataSource
import coil3.request.SuccessResult
import coil3.request.ImageRequest
import coil3.size.Scale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoGalleryCacheTest {
    @get:Rule val composeRule = createComposeRule()
    private val photos = listOf("moment.png", "archive.png", "insights.png")
    private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun unavailablePhotoShowsFailureAndRetryLoadsRestoredFile() = checkPhotoRetry(AppLanguage.CHINESE)

    @Test
    fun unavailablePhotoCanRetryInEnglish() = checkPhotoRetry(AppLanguage.ENGLISH)

    private fun checkPhotoRetry(language: AppLanguage) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalLanguage = AppLocale.language
        val file = File(context.cacheDir, "gallery-retry-${System.nanoTime()}.png")
        val heldFile = File(context.cacheDir, "${file.name}.held")
        val loader = GalleryImageLoader.get(context)
        context.resources.openRawResource(android.R.drawable.ic_menu_crop).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        assertTrue(file.renameTo(heldFile))
        try {
            composeRule.runOnUiThread { AppLocale.select(context, language) }
            composeRule.setContent {
                XikeTheme(AppTheme.OCEAN) {
                    PhotoGalleryDialog(listOf(file.name), 0, { file.inputStream() }, {})
                }
            }
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("photo-gallery-error-0").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("photo-gallery-error-0").assertIsDisplayed()
            composeRule.onNodeWithTag("photo-gallery-pager").captureToImage().asAndroidBitmap().let { bitmap ->
                File(context.cacheDir, "gallery-retry-error-${language.tag}.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            assertTrue(heldFile.renameTo(file))
            composeRule.onNodeWithText(if (language == AppLanguage.CHINESE) "重试" else "Retry").performClick()
            composeRule.waitUntil(10_000) {
                loader.memoryCache?.keys?.any { it.key == "gallery:${file.name}" } == true &&
                    composeRule.onAllNodesWithTag("photo-gallery-error-0").fetchSemanticsNodes().isEmpty() &&
                    composeRule.onAllNodesWithTag("photo-gallery-placeholder-0").fetchSemanticsNodes().isEmpty()
            }
        } finally {
            file.delete()
            heldFile.delete()
            composeRule.runOnUiThread { AppLocale.select(context, originalLanguage) }
        }
    }

    @Test
    fun highResolutionPhotosStayCachedAfterGarbageCollection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = GalleryImageLoader.create(context)
        val reads = AtomicInteger()
        val requests = photos.map { name ->
            GalleryImageLoader.request(context, GalleryPhoto(name) {
                reads.incrementAndGet()
                assets.open(name)
            }).newBuilder().size(GalleryImageLoader.decodeSize(1440, 3200)).build()
        }
        suspend fun loadPhotos(expectCached: Boolean) {
            requests.forEach { request ->
                val result = loader.execute(request)
                assertTrue(result is SuccessResult)
                if (expectCached) assertEquals(DataSource.MEMORY_CACHE, (result as SuccessResult).dataSource)
            }
        }
        try {
            loadPhotos(false)
            repeat(3) { System.gc(); delay(200) }
            loadPhotos(true)
            assertEquals(3, reads.get())
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun transparentPhotoDoesNotShowTheLoadingIconAfterSuccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = GalleryImageLoader.get(context)
        loader.memoryCache?.clear()
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                PhotoGalleryDialog(listOf("platform-crop"), 0, {
                    context.resources.openRawResource(android.R.drawable.ic_menu_crop)
                }, {})
            }
        }
        composeRule.waitUntil(10_000) {
            loader.memoryCache?.keys?.isNotEmpty() == true &&
                composeRule.onAllNodesWithTag("photo-gallery-placeholder-0").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("photo-gallery-placeholder-0").assertDoesNotExist()
        val screenshot = composeRule.onNodeWithTag("photo-gallery-pager").captureToImage()
        File(context.cacheDir, "gallery-transparent-fixed.png").outputStream().use {
            screenshot.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun encryptedPhotoDecodesDirectlyAndReturnsFromMemory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "gallery-encrypted-${System.nanoTime()}")
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val encrypted = EncryptedFile.Builder(context, file, masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        val loader = GalleryImageLoader.create(context)
        val reads = AtomicInteger()
        try {
            encrypted.openFileOutput().use { output -> assets.open("moment.png").use { it.copyTo(output) } }
            val request = GalleryImageLoader.request(context, GalleryPhoto(file.name) {
                reads.incrementAndGet()
                encrypted.openFileInput()
            }).newBuilder().size(1080, 1920).build()
            assertTrue(loader.execute(request) is SuccessResult)
            val cached = loader.execute(request) as SuccessResult
            assertEquals(DataSource.MEMORY_CACHE, cached.dataSource)
            assertEquals(1, reads.get())
            assertEquals(null, loader.diskCache)
        } finally {
            loader.shutdown()
            file.delete()
        }
    }

    @Test
    fun decodedPhotosReturnFromMemoryWithoutReopeningStreams() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = GalleryImageLoader.create(context)
        val reads = AtomicInteger()
        try {
            val requests = photos.map { name ->
                ImageRequest.Builder(context)
                    .data(GalleryPhoto(name) { reads.incrementAndGet(); assets.open(name) })
                    .size(1080, 1920).scale(Scale.FIT).build()
            }
            requests.forEach { assertTrue(loader.execute(it) is SuccessResult) }
            repeat(3) {
                requests.reversed().forEach { request ->
                    val result = loader.execute(request)
                    assertTrue(result is SuccessResult)
                    assertEquals(DataSource.MEMORY_CACHE, (result as SuccessResult).dataSource)
                }
            }
            assertEquals(3, reads.get())
            assertEquals(null, loader.diskCache)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun swipingThreePhotosAndReopeningGalleryDoesNotReadAgain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = GalleryImageLoader.get(context)
        loader.memoryCache?.clear()
        val visible = mutableStateOf(true)
        val reads = AtomicInteger()
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                if (visible.value) PhotoGalleryDialog(photos, 1, { name ->
                    reads.incrementAndGet()
                    assets.open(name)
                }, { visible.value = false })
            }
        }
        composeRule.waitUntil(15_000) { loader.memoryCache?.keys?.size == 3 }
        composeRule.waitForIdle()
        val screenshot = composeRule.onNodeWithTag("photo-gallery-pager").captureToImage()
        File(context.cacheDir, "gallery-cache-check.png").outputStream().use {
            screenshot.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        repeat(3) {
            composeRule.onNodeWithTag("photo-gallery-pager").performTouchInput { swipeLeft() }
            composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
            composeRule.onNodeWithTag("photo-gallery-pager").performTouchInput { swipeRight() }
            composeRule.onNodeWithTag("photo-gallery-pager").performTouchInput { swipeRight() }
            composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
            composeRule.onNodeWithTag("photo-gallery-pager").performTouchInput { swipeLeft() }
        }
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        System.gc()
        composeRule.runOnIdle { visible.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
        assertEquals(3, reads.get())
    }
}
