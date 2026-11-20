package com.xike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xike.app.data.BackupCipher
import com.xike.app.data.readAvailable
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStreamingCipherDeviceTest {
    @Test
    fun installedApkEncryptsIncrementallyAndRejectsDamagedTail() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.applicationInfo.sourceDir)
        val encrypted = File.createTempFile("xike-streaming-device-", ".xike", context.cacheDir)
        val password = "Xike-actual-android-provider"
        fun decryptDigest(): ByteArray =
            encrypted.inputStream().use { input ->
                val header = ByteArray(BackupCipher.magicSize)
                check(input.readAvailable(header) == header.size)
                BackupCipher.decryptingStream(input, password, header).use(::digest)
            }
        try {
            BackupCipher.encryptingStream(encrypted.outputStream(), password).use { output ->
                source.inputStream().use { it.copyTo(output) }
                output.flush()
                assertTrue(
                    "Android must write encrypted segments before close",
                    encrypted.length() > source.length() / 2,
                )
            }
            assertArrayEquals(source.inputStream().use(::digest), decryptDigest())
            java.io.RandomAccessFile(encrypted, "rw").use { it.setLength(it.length() - 1) }
            assertThrows(Exception::class.java) { decryptDigest() }
        } finally {
            encrypted.delete()
        }
    }

    private fun digest(input: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest()
    }
}
