package com.xike.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the SQLCipher passphrase wrapped by a non-exportable Android Keystore key. */
internal class DatabaseKeyManager(private val context: Context) {
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val preferences = context.getSharedPreferences(KEY_PREFERENCES, Context.MODE_PRIVATE)
        val stored = preferences.getString(WRAPPED_PASSPHRASE, null)
        if (stored != null) {
            return runCatching { unwrap(Base64.decode(stored, Base64.NO_WRAP)) }
                .getOrElse { error ->
                    throw JournalDataException("数据库密钥无法读取，原数据未被覆盖。", error)
                }
        }

        if (context.getDatabasePath(JournalDatabase.DATABASE_NAME).isFile) {
            throw JournalDataException(
                "数据库密钥缺失，已停止打开数据库以保护原数据。",
                IllegalStateException("Missing wrapped database passphrase"),
            )
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        return runCatching {
            val encoded = Base64.encodeToString(wrap(passphrase), Base64.NO_WRAP)
            check(preferences.edit().putString(WRAPPED_PASSPHRASE, encoded).commit()) {
                "无法保存数据库密钥。"
            }
            passphrase
        }.getOrElse { error ->
            passphrase.fill(0)
            throw JournalDataException("数据库密钥初始化失败，未创建数据库。", error)
        }
    }

    private fun wrap(passphrase: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(passphrase)
        return ByteBuffer.allocate(2 + cipher.iv.size + encrypted.size)
            .put(FORMAT_VERSION)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
    }

    private fun unwrap(payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(payload)
        require(buffer.remaining() >= 2) { "Invalid wrapped database key" }
        require(buffer.get() == FORMAT_VERSION) { "Unsupported wrapped database key version" }
        val nonceSize = buffer.get().toInt() and 0xff
        require(nonceSize in 12..32 && buffer.remaining() > nonceSize) { "Invalid database key nonce" }
        val nonce = ByteArray(nonceSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(AAD)
        return cipher.doFinal(encrypted).also {
            require(it.size == PASSPHRASE_BYTES) { "Invalid database passphrase length" }
        }
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "xike-room-wrapping-key"
        const val KEY_PREFERENCES = "xike-database-key"
        const val WRAPPED_PASSPHRASE = "wrapped-passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val FORMAT_VERSION: Byte = 1
        val AAD = "xike-room-passphrase-v1".toByteArray(Charsets.UTF_8)
    }
}
