package com.xike.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.FileNotFoundException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoCaptureTest {
    @Test
    fun cameraCaptureUriCanBeWrittenReadAndDeleted() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assumeTrue("System gallery is not writable on this test device", hasGalleryWriteAccess(context))
        val uri = createCameraCaptureUri(context)
        val expected = "camera result".toByteArray()

        try {
            assertTrue(isCameraCaptureUri(context, uri))
            context.contentResolver.openOutputStream(uri)?.use { it.write(expected) }
                ?: error("Capture URI was not writable")
            assertTrue(cameraCaptureHasContent(context, uri))
            assertTrue(finalizeCameraCapture(context, uri))
            val actual = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Capture URI was not readable")
            assertArrayEquals(expected, actual)
        } finally {
            deleteCameraCapture(context, uri)
        }

        val stillReadable = runCatching {
            context.contentResolver.openInputStream(uri)?.use { true } == true
        }.recover { error ->
            if (error is FileNotFoundException) false else throw error
        }.getOrThrow()
        assertFalse(stillReadable)
    }
}
