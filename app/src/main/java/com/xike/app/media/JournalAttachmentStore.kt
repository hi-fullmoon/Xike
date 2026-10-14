package com.xike.app.media

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.xike.app.data.JournalAudio
import com.xike.app.data.MAX_AUDIO_BYTES
import com.xike.app.data.MAX_AUDIO_DURATION_MILLIS
import com.xike.app.data.MAX_IMAGES_PER_ENTRY
import com.xike.app.data.readAvailable
import com.xike.app.data.validateBackup
import com.xike.app.localizedText
import com.xike.app.tr
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID

private const val MAX_FILE_NAME_LENGTH = 160
internal const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
internal const val MAX_BACKUP_IMAGE_BYTES = 1024L * 1024L * 1024L
internal const val MAX_BACKUP_AUDIO_BYTES = 1024L * 1024L * 1024L

internal fun isSafeImageFileName(fileName: String): Boolean =
    fileName.isNotBlank() &&
        fileName.length <= MAX_FILE_NAME_LENGTH &&
        File(fileName).name == fileName &&
        !fileName.contains('/') &&
        !fileName.contains('\\')

internal fun isSafeAudioFileName(fileName: String): Boolean =
    isSafeImageFileName(fileName) && (fileName.endsWith(".xike-audio") || fileName.endsWith(".m4a"))

internal class JournalAttachmentStore(
    private val appContext: Context,
    private val masterKey: MasterKey,
) {
    val imagesDirectory = File(appContext.filesDir, "journal-images")
    val audiosDirectory = File(appContext.filesDir, "journal-audios")

    fun initializeDirectories() {
        check(imagesDirectory.isDirectory || imagesDirectory.mkdirs()) {
            localizedText("无法创建图片存储目录。")
        }
        check(audiosDirectory.isDirectory || audiosDirectory.mkdirs()) {
            localizedText("无法创建录音存储目录。")
        }
    }

    fun openEncrypted(file: File): InputStream = encryptedFile(file).openFileInput()

    fun importImages(uris: List<Uri>): List<String> {
        require(uris.size <= MAX_IMAGES_PER_ENTRY) { localizedText("每条记录最多添加 9 张图片。") }
        val imported = mutableListOf<String>()
        return runCatching {
            uris.forEach { imported += importImage(it) }
            imported.toList()
        }
            .onFailure {
                imported.forEach(::deleteImage)
            }
            .getOrThrow()
    }

    private fun importImage(uri: Uri): String {
        val fileName = "${UUID.randomUUID()}.xike-image"
        val target = File(imagesDirectory, fileName)

        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                writeEncryptedImage(target, input, MAX_IMAGE_BYTES, null)
            } ?: error(localizedText("无法读取所选图片"))
        }
            .onFailure {
                target.delete()
                throw it
            }
        return fileName
    }

    fun openImage(fileName: String): InputStream? =
        fileName
            .takeIf(::isSafeImageFileName)
            ?.let { File(imagesDirectory, it) }
            ?.takeIf(File::isFile)
            ?.let { file -> runCatching { encryptedFile(file).openFileInput() }.getOrNull() }

    fun importAudio(source: File, durationMillis: Long): JournalAudio {
        require(source.isFile && source.length() > 0L) { localizedText("录音文件不可用。") }
        return FileInputStream(source).use { input -> importAudio(input, durationMillis) }
    }

    fun importAudio(input: InputStream, durationMillis: Long): JournalAudio {
        require(durationMillis in 1..MAX_AUDIO_DURATION_MILLIS) { localizedText("录音时长需要在 5 分钟以内。") }
        val fileName = "${UUID.randomUUID()}.xike-audio"
        val target = File(audiosDirectory, fileName)
        runCatching {
            writeEncryptedAttachment(
                target = target,
                input = input,
                byteLimit = MAX_AUDIO_BYTES,
                totalBytes = null,
                itemName = localizedText("录音"),
                totalName = localizedText("备份录音"),
                totalLimit = MAX_BACKUP_AUDIO_BYTES,
            )
        }
            .onFailure {
                target.delete()
                throw it
            }
        return JournalAudio(fileName = fileName, durationMillis = durationMillis)
    }

    fun openAudio(fileName: String): InputStream? =
        fileName
            .takeIf(::isSafeAudioFileName)
            ?.let { File(audiosDirectory, it) }
            ?.takeIf(File::isFile)
            ?.let { file -> runCatching { encryptedFile(file).openFileInput() }.getOrNull() }

    internal fun requireReadableAudio(fileName: String) {
        require(isSafeAudioFileName(fileName)) { localizedText("草稿录音引用无效，原数据已保留。") }
        val file = File(audiosDirectory, fileName)
        check(file.isFile) { localizedText("草稿录音文件暂时不可用，原引用已保留。") }
        encryptedFile(file).openFileInput().use { input ->
            val buffer = ByteArray(8192)
            while (input.read(buffer) != -1) {
                /* Validate every encrypted segment. */
            }
        }
    }

    fun writeEncryptedImage(
        target: File,
        input: InputStream,
        byteLimit: Long,
        totalBytes: LongArray?,
    ) {
        writeEncryptedAttachment(
            target = target,
            input = input,
            byteLimit = byteLimit,
            totalBytes = totalBytes,
            itemName = localizedText("图片"),
            totalName = localizedText("备份图片"),
            totalLimit = MAX_BACKUP_IMAGE_BYTES,
        )
    }

    fun writeEncryptedAttachment(
        target: File,
        input: InputStream,
        byteLimit: Long,
        totalBytes: LongArray?,
        itemName: String,
        totalName: String,
        totalLimit: Long,
    ) {
        runCatching {
            encryptedFile(target).openFileOutput().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var itemBytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    itemBytes += count
                    validateBackup(itemBytes <= byteLimit) {
                        tr("$itemName 超过大小限制。", "$itemName exceeds the size limit.")
                    }
                    if (totalBytes != null) {
                        totalBytes[0] += count
                        validateBackup(totalBytes[0] <= totalLimit) {
                            tr("$totalName 总大小超过限制。", "$totalName exceed the total size limit.")
                        }
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
            .onFailure {
                target.delete()
                throw it
            }
    }

    private fun encryptedFile(file: File): EncryptedFile =
        EncryptedFile.Builder(
                appContext,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
            )
            .build()

    fun deleteImage(fileName: String) {
        if (isSafeImageFileName(fileName)) File(imagesDirectory, fileName).delete()
    }

    fun deleteAudio(fileName: String) {
        if (isSafeAudioFileName(fileName)) File(audiosDirectory, fileName).delete()
    }

    fun validateAudioReference(audio: JournalAudio?) {
        if (audio == null) return
        require(audio.durationMillis in 1..MAX_AUDIO_DURATION_MILLIS) {
            localizedText("录音时长需要在 5 分钟以内。")
        }
        require(
            isSafeAudioFileName(audio.fileName) && File(audiosDirectory, audio.fileName).isFile
        ) {
            localizedText("录音文件不可用。")
        }
    }

    fun imageExtension(input: InputStream): String {
        val header = ByteArray(16)
        val size = input.readAvailable(header)
        return when {
            size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> "jpg"
            size >= 8 &&
                header
                    .copyOfRange(0, 8)
                    .contentEquals(
                        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                    ) -> "png"
            size >= 6 && String(header, 0, 3, Charsets.US_ASCII) == "GIF" -> "gif"
            size >= 12 &&
                String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            size >= 12 &&
                String(header, 4, 4, Charsets.US_ASCII) == "ftyp" &&
                String(header, 8, 4, Charsets.US_ASCII) in setOf("heic", "heix", "hevc", "mif1") ->
                "heic"
            else -> "bin"
        }
    }
}
