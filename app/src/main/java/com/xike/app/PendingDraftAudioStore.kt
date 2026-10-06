package com.xike.app

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID

internal data class StagedDraftAudio(val fileName: String, val durationMillis: Long)

internal class PendingDraftAudioStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "pending-draft-audio")
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    init {
        check(directory.isDirectory || directory.mkdirs()) { localizedText("无法创建录音暂存目录。") }
    }

    fun stage(source: File, durationMillis: Long): StagedDraftAudio {
        require(durationMillis in 1..MAX_AUDIO_DURATION_MILLIS) { localizedText("录音时长需要在 5 分钟以内。") }
        require(source.isFile && source.length() > 0L) { localizedText("录音文件不可用。") }
        val file = File(directory, "$FILE_PREFIX${UUID.randomUUID()}$FILE_SUFFIX")
        try {
            DataOutputStream(encryptedFile(file).openFileOutput().buffered()).use { output ->
                output.writeLong(durationMillis)
                FileInputStream(source).use { input -> copyBounded(input) { buffer, count -> output.write(buffer, 0, count) } }
            }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        return StagedDraftAudio(file.name, durationMillis)
    }

    fun recover(): StagedDraftAudio? {
        val candidates = checkNotNull(directory.listFiles()) { "录音暂存目录暂时无法读取，原文件已保留。" }
            .filter { it.isFile && isStagedFileName(it.name) }
            .sortedByDescending(File::lastModified)
        val validated = try {
            candidates.map(::validate)
        } catch (error: Exception) {
            throw IllegalStateException("录音暂存文件暂时无法读取，原文件已保留。", error)
        }
        // Do not remove any candidate until all files have been read successfully.
        candidates.drop(1).forEach(File::delete)
        return validated.firstOrNull()
    }

    fun open(staged: StagedDraftAudio): InputStream {
        val input = DataInputStream(encryptedFile(resolve(staged.fileName)).openFileInput().buffered())
        try {
            check(input.readLong() == staged.durationMillis) { localizedText("录音暂存信息不匹配。") }
            return input
        } catch (error: Throwable) {
            input.close()
            throw error
        }
    }

    fun delete(staged: StagedDraftAudio): Boolean {
        val file = resolve(staged.fileName)
        return !file.exists() || file.delete()
    }

    private fun validate(file: File): StagedDraftAudio {
        val durationMillis = DataInputStream(encryptedFile(file).openFileInput().buffered()).use { input ->
            val duration = input.readLong()
            require(duration in 1..MAX_AUDIO_DURATION_MILLIS) { localizedText("录音暂存时长无效。") }
            copyBounded(input) { _, _ -> }
            duration
        }
        return StagedDraftAudio(file.name, durationMillis)
    }

    private fun copyBounded(input: InputStream, consume: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_AUDIO_BYTES) { localizedText("录音超过大小限制。") }
            consume(buffer, count)
        }
        require(total > 0L) { localizedText("录音文件不可用。") }
    }

    private fun resolve(fileName: String): File {
        require(isStagedFileName(fileName)) { localizedText("录音暂存文件名无效。") }
        return File(directory, fileName)
    }

    private fun isStagedFileName(fileName: String): Boolean =
        fileName.startsWith(FILE_PREFIX) && fileName.endsWith(FILE_SUFFIX) &&
            fileName.length <= 80 && File(fileName).name == fileName

    private fun encryptedFile(file: File): EncryptedFile = EncryptedFile.Builder(
        appContext,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

    private companion object {
        const val FILE_PREFIX = "xike-pending-"
        const val FILE_SUFFIX = ".enc"
    }
}
