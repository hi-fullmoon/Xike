package com.xike.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class BackupValidationException(message: String) : IllegalArgumentException(message)

internal inline fun validateBackup(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw BackupValidationException(lazyMessage())
}

internal fun InputStream.readAvailable(target: ByteArray): Int {
    var offset = 0
    while (offset < target.size) {
        val count = read(target, offset, target.size - offset)
        if (count < 0) break
        offset += count
    }
    return offset
}

internal fun InputStream.readUtf8Limited(limit: Long): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        validateBackup(total <= limit) { localizedText("备份文件过大。") }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal object BackupCipher {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private val streamingMagic = byteArrayOf('L'.code.toByte(), 'I'.code.toByte(), 'U'.code.toByte(), 'B'.code.toByte(), 'A'.code.toByte(), 'I'.code.toByte(), 4)

    val magicSize: Int get() = streamingMagic.size

    fun hasStreamingMagic(value: ByteArray): Boolean = value.contentEquals(streamingMagic)

    fun encryptingStream(output: OutputStream, password: String): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        output.write(streamingMagic)
        output.write(salt)
        output.write(nonce)
        output.flush()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return CipherOutputStream(output, cipher)
    }

    fun decryptingStream(input: InputStream, password: String): InputStream {
        val salt = ByteArray(SALT_BYTES)
        val nonce = ByteArray(NONCE_BYTES)
        validateBackup(input.readAvailable(salt) == salt.size) { localizedText("备份文件头不完整。") }
        validateBackup(input.readAvailable(nonce) == nonce.size) { localizedText("备份文件头不完整。") }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return CipherInputStream(input, cipher)
    }

    fun encryptLegacy(plainText: String, password: String): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", "xike-encrypted")
            .put("version", 1)
            .put("salt", Base64.getEncoder().encodeToString(salt))
            .put("nonce", Base64.getEncoder().encodeToString(nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(encrypted))
            .toString()
    }

    fun decryptLegacy(payload: String, password: String): String {
        val backup = JSONObject(payload)
        validateBackup(backup.optString("format") == "xike-encrypted") { localizedText("备份未加密或文件格式不正确。") }
        val salt = Base64.getDecoder().decode(backup.getString("salt"))
        val nonce = Base64.getDecoder().decode(backup.getString("nonce"))
        val ciphertext = Base64.getDecoder().decode(backup.getString("ciphertext"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)).encoded
        return SecretKeySpec(derived, "AES")
    }
}


internal fun parseBackupEntries(
    values: JSONArray,
    maximumEntryCount: Int,
    isSafeImageFileName: (String) -> Boolean,
    isSafeAudioFileName: (String) -> Boolean,
): List<JournalEntry> {
    validateBackup(values.length() <= maximumEntryCount) { localizedText("备份包含过多日记。") }
    return List(values.length()) { index ->
        val entry = JournalEntry.fromJson(values.getJSONObject(index))
        entry.video?.let { validateBackup(it.isValid()) { tr("备份视频信息无效。", "Invalid backup video metadata.") } }
        entry.copy(
            imageFileNames = entry.imageFileNames
                .filter(isSafeImageFileName)
                .distinct()
                .take(MAX_IMAGES_PER_ENTRY),
            audio = entry.audio?.takeIf {
                isSafeAudioFileName(it.fileName) && it.durationMillis in 1..MAX_AUDIO_DURATION_MILLIS
            },
        )
    }
}
