package com.xike.app

import com.xike.app.data.BackupCipher
import com.xike.app.data.readAvailable
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStreamingCipherTest {
    private val source = File("../docs/screenshots/moment.png")
    private val password = "Xike-streaming-regression"

    @Test
    fun encryptedSegmentsReachDiskBeforeClosingAndRoundTripRealImage() {
        val encrypted = File.createTempFile("xike-streaming-", ".xike")
        try {
            BackupCipher.encryptingStream(encrypted.outputStream(), password).use { output ->
                source.inputStream().use { it.copyTo(output) }
                output.flush()
                assertTrue(
                    "Encrypted media must leave memory before close",
                    encrypted.length() > 64 * 1024,
                )
            }
            assertArrayEquals(digest(source), decryptDigest(encrypted))
            assertThrows(Exception::class.java) { decryptDigest(encrypted, "wrong-password") }
        } finally {
            encrypted.delete()
        }
    }

    @Test
    fun truncatedAndModifiedFinalSegmentsAreRejected() {
        val encrypted = File.createTempFile("xike-streaming-", ".xike")
        try {
            BackupCipher.encryptingStream(encrypted.outputStream(), password).use { output ->
                source.inputStream().use { it.copyTo(output) }
            }
            java.io.RandomAccessFile(encrypted, "rw").use {
                it.seek(it.length() - 1)
                val last = it.read()
                it.seek(it.length() - 1)
                it.write(last xor 1)
            }
            assertThrows(Exception::class.java) { decryptDigest(encrypted) }
            java.io.RandomAccessFile(encrypted, "rw").use { it.setLength(it.length() - 1) }
            assertThrows(Exception::class.java) { decryptDigest(encrypted) }
        } finally {
            encrypted.delete()
        }
    }

    @Test
    fun previousWholeArchiveGcmFormatRemainsReadable() {
        val encrypted = File.createTempFile("xike-old-gcm-", ".xike")
        val random = java.security.SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = "LIUBAI".toByteArray() + byteArrayOf(4)
        val key =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(password.toCharArray(), salt, 210_000, 256))
                .encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        try {
            encrypted.outputStream().use { output ->
                output.write(header)
                output.write(salt)
                output.write(nonce)
                CipherOutputStream(output, cipher).use { destination ->
                    source.inputStream().use { it.copyTo(destination) }
                }
            }
            assertArrayEquals(digest(source), decryptDigest(encrypted))
        } finally {
            encrypted.delete()
        }
    }

    private fun decryptDigest(file: File, secret: String = password): ByteArray =
        file.inputStream().use {
            val header = ByteArray(BackupCipher.magicSize)
            check(it.readAvailable(header) == header.size)
            check(BackupCipher.hasStreamingMagic(header))
            BackupCipher.decryptingStream(it, secret, header).use(::digest)
        }

    private fun digest(file: File): ByteArray = file.inputStream().use(::digest)

    private fun digest(input: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
        return digest.digest()
    }
}
