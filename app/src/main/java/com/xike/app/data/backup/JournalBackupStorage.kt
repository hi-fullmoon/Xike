package com.xike.app.data.backup

import android.content.Context
import com.xike.app.BackupHtml
import com.xike.app.data.BackupCipher
import com.xike.app.data.BackupSummary
import com.xike.app.data.BackupValidationException
import com.xike.app.data.JournalDataException
import com.xike.app.data.JournalEntry
import com.xike.app.data.MAX_AUDIO_BYTES
import com.xike.app.data.MAX_BACKUP_MANIFEST_BYTES
import com.xike.app.data.model.files
import com.xike.app.data.normalizeRestoredEntries
import com.xike.app.data.parseBackupEntries
import com.xike.app.data.readAvailable
import com.xike.app.data.readUtf8Limited
import com.xike.app.data.validateBackup
import com.xike.app.localizedText
import com.xike.app.media.JournalAttachmentStore
import com.xike.app.media.JournalVideoStore
import com.xike.app.media.MAX_BACKUP_AUDIO_BYTES
import com.xike.app.media.MAX_BACKUP_VIDEO_BYTES
import com.xike.app.media.MAX_IMAGE_BYTES
import com.xike.app.media.isSafeAudioFileName
import com.xike.app.media.isSafeImageFileName
import com.xike.app.tr
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal const val RESTORE_STAGING_PREFIX = "journal-images-restore-"

internal data class PreparedBackup(
    val stagingDirectory: File,
    val entries: List<JournalEntry>,
)

/** Reads and writes validated backups; database replacement and undo stay in JournalStore. */
internal class JournalBackupStorage(
    private val appContext: Context,
    private val attachments: JournalAttachmentStore,
    private val videos: JournalVideoStore,
) {
    fun writeBackup(currentEntries: List<JournalEntry>, output: OutputStream, password: String?) {
        val imageNames = currentEntries.flatMap { it.imageFileNames }.distinct()
        val audioNames = currentEntries.mapNotNull { it.audio?.fileName }.distinct()
        val videoNames = currentEntries.flatMap { it.video.files() }.distinct()
        require(
            currentEntries
                .mapNotNull { it.video }
                .distinctBy { it.fileName }
                .sumOf { it.sizeBytes } <= MAX_BACKUP_VIDEO_BYTES
        ) {
            tr("备份视频超过 10 GB。", "Backup videos exceed 10 GB.")
        }
        val imageExportNames = imageNames.associateWith { name ->
            val extension =
                attachments.openImage(name)?.use(attachments::imageExtension)
                    ?: error(tr("备份图片无法读取：$name", "Unable to read backup photo: $name"))
            "${name.removeSuffix(".xike-image")}.$extension"
        }
        val audioExportNames = audioNames.associateWith { name ->
            "${name.removeSuffix(".xike-audio")}.m4a"
        }
        val exportedEntries = currentEntries.map { entry ->
            entry.copy(
                imageFileNames = entry.imageFileNames.map { imageExportNames.getValue(it) },
                audio =
                    entry.audio?.let { it.copy(fileName = audioExportNames.getValue(it.fileName)) },
            )
        }
        val manifest =
            JSONObject()
                .put("format", "xike")
                .put("version", STREAMING_BACKUP_VERSION)
                .put("entries", JSONArray(exportedEntries.map { it.toJson() }))
                .toString()
        val html = BackupHtml.render(exportedEntries)
        val manifestBytes = manifest.toByteArray(Charsets.UTF_8)
        val htmlBytes = html.toByteArray(Charsets.UTF_8)
        require(manifestBytes.size <= MAX_MANIFEST_BYTES && htmlBytes.size <= MAX_HTML_BYTES) {
            localizedText("日记内容超出单个备份文件的大小限制。")
        }

        val destination =
            if (password == null) output else BackupCipher.encryptingStream(output, password)
        ZipOutputStream(BufferedOutputStream(destination)).use { archive ->
            archive.setLevel(Deflater.BEST_SPEED)
            archive.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            archive.write(manifestBytes)
            archive.closeEntry()

            archive.putNextEntry(ZipEntry(HTML_ENTRY))
            archive.write(htmlBytes)
            archive.closeEntry()

            imageNames.forEach { fileName ->
                (attachments.openImage(fileName)
                        ?: error(
                            tr("备份图片无法读取：$fileName", "Unable to read backup photo: $fileName")
                        ))
                    .use { image ->
                        archive.putNextEntry(
                            ZipEntry("$IMAGES_PREFIX${imageExportNames.getValue(fileName)}")
                        )
                        image.copyTo(archive)
                        archive.closeEntry()
                    }
            }
            audioNames.forEach { fileName ->
                (attachments.openAudio(fileName)
                        ?: error(
                            tr("备份录音无法读取：$fileName", "Unable to read backup audio: $fileName")
                        ))
                    .use { audio ->
                        archive.putNextEntry(
                            ZipEntry("$AUDIOS_PREFIX${audioExportNames.getValue(fileName)}")
                        )
                        audio.copyTo(archive)
                        archive.closeEntry()
                    }
            }
            val videoArchive =
                object : java.io.FilterOutputStream(archive) {
                    private var totalBytes = 0L

                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        totalBytes += length
                        require(totalBytes <= MAX_BACKUP_VIDEO_BYTES) {
                            tr("备份视频超过 10 GB。", "Backup videos exceed 10 GB.")
                        }
                        out.write(bytes, offset, length)
                    }
                }
            videoNames.forEach { fileName ->
                videos.open(fileName).use { input ->
                    archive.putNextEntry(ZipEntry("videos/$fileName"))
                    input.copyTo(videoArchive)
                    archive.closeEntry()
                }
            }
            archive.putNextEntry(ZipEntry(COMPLETION_ENTRY))
            archive.write(COMPLETION_MARKER.toByteArray(Charsets.US_ASCII))
            archive.closeEntry()
        }
    }

    fun backupRequiresPassword(input: InputStream): Boolean {
        val prefix = ByteArray(BackupCipher.magicSize)
        val count = input.readAvailable(prefix)
        return when {
            count == prefix.size && BackupCipher.hasStreamingMagic(prefix) -> true
            count >= 4 &&
                prefix[0] == 'P'.code.toByte() &&
                prefix[1] == 'K'.code.toByte() &&
                prefix[2] == 3.toByte() &&
                prefix[3] == 4.toByte() -> false
            count > 0 && prefix[0] == '{'.code.toByte() -> true
            else -> throw IllegalArgumentException(localizedText("这不是息刻备份文件。"))
        }
    }

    fun inspectBackup(input: InputStream, password: String?): BackupSummary {
        val prepared = readBackup(input, password)
        return try {
            val createdAtValues = prepared.entries.map { it.createdAt }
            BackupSummary(
                entryCount = prepared.entries.size,
                imageCount = prepared.entries.flatMap { it.imageFileNames }.distinct().size,
                audioCount = prepared.entries.mapNotNull { it.audio?.fileName }.distinct().size,
                videoCount = prepared.entries.count { it.video != null },
                videoBytes = prepared.entries.sumOf { it.video?.sizeBytes ?: 0L },
                oldestCreatedAt = createdAtValues.minOrNull(),
                newestCreatedAt = createdAtValues.maxOrNull(),
            )
        } finally {
            prepared.stagingDirectory.deleteRecursively()
        }
    }

    fun readBackup(input: InputStream, password: String?): PreparedBackup {
        val source = PushbackInputStream(BufferedInputStream(input), BackupCipher.magicSize)
        val prefix = ByteArray(BackupCipher.magicSize)
        val prefixSize = source.readAvailable(prefix)

        return if (prefixSize == BackupCipher.magicSize && BackupCipher.hasStreamingMagic(prefix)) {
            require(!password.isNullOrEmpty()) { localizedText("请输入备份密码。") }
            readStreamingBackup(source, password, encrypted = true)
        } else if (
            prefixSize >= 4 &&
                prefix[0] == 'P'.code.toByte() &&
                prefix[1] == 'K'.code.toByte() &&
                prefix[2] == 3.toByte() &&
                prefix[3] == 4.toByte()
        ) {
            source.unread(prefix, 0, prefixSize)
            readStreamingBackup(source, null, encrypted = false)
        } else {
            require(!password.isNullOrEmpty()) { localizedText("请输入备份密码。") }
            if (prefixSize > 0) source.unread(prefix, 0, prefixSize)
            readLegacyBackup(source.readUtf8Limited(MAX_LEGACY_BACKUP_BYTES), password)
        }
    }

    private fun readStreamingBackup(
        source: InputStream,
        password: String?,
        encrypted: Boolean,
    ): PreparedBackup {
        val stagingDirectory = createStagingDirectory()
        return try {
            var parsedEntries: List<JournalEntry>? = null
            val restoredImages = linkedSetOf<String>()
            val restoredAudios = linkedSetOf<String>()
            val restoredVideos = linkedSetOf<String>()
            var referencedImages = emptySet<String>()
            var referencedAudios = emptySet<String>()
            var referencedVideos = emptySet<String>()
            val totalVideoBytes = longArrayOf(0L)
            val totalBytes = longArrayOf(0L)
            val totalAudioBytes = longArrayOf(0L)
            var version = 0
            var htmlSeen = false
            var completionSeen = false

            val archiveSource =
                if (encrypted) BackupCipher.decryptingStream(source, requireNotNull(password))
                else source
            ZipInputStream(BufferedInputStream(archiveSource)).use { archive ->
                val manifestEntry = archive.nextEntry
                validateBackup(manifestEntry?.name == MANIFEST_ENTRY) {
                    localizedText("备份清单缺失或顺序不正确。")
                }
                val manifest = JSONObject(archive.readUtf8Limited(MAX_MANIFEST_BYTES))
                archive.closeEntry()
                validateBackup(manifest.optString("format") == "xike") {
                    localizedText("这不是息刻备份文件。")
                }
                version = manifest.optInt("version")
                validateBackup(version in MIN_STREAMING_BACKUP_VERSION..STREAMING_BACKUP_VERSION) {
                    localizedText("暂不支持这个版本的息刻备份。")
                }
                validateBackup(encrypted || version == STREAMING_BACKUP_VERSION) {
                    localizedText("未加密备份格式不受支持。")
                }

                parsedEntries = parseEntries(manifest.getJSONArray("entries"))
                referencedImages =
                    parsedEntries
                        .orEmpty()
                        .flatMap { it.imageFileNames }
                        .filter(::isSafeImageFileName)
                        .toSet()
                validateBackup(referencedImages.size <= MAX_BACKUP_IMAGES) {
                    localizedText("备份包含过多图片。")
                }
                referencedAudios =
                    parsedEntries
                        .orEmpty()
                        .mapNotNull { it.audio?.fileName }
                        .filter(::isSafeAudioFileName)
                        .toSet()
                validateBackup(referencedAudios.size <= MAX_BACKUP_AUDIOS) {
                    localizedText("备份包含过多录音。")
                }
                validateBackup(referencedImages.intersect(referencedAudios).isEmpty()) {
                    localizedText("备份中的图片和录音文件名重复。")
                }

                referencedVideos =
                    requireNotNull(parsedEntries).flatMap { it.video.files() }.toSet()
                validateBackup(version >= 8 || referencedVideos.isEmpty()) {
                    tr("备份视频版本无效。", "Invalid video backup version.")
                }
                validateBackup(
                    (referencedImages + referencedAudios).intersect(referencedVideos).isEmpty()
                ) {
                    tr("备份附件文件名重复。", "Duplicate attachment names.")
                }
                var entry = archive.nextEntry
                while (entry != null) {
                    validateBackup(!entry.isDirectory) { localizedText("备份中包含未知内容。") }
                    when {
                        entry.name == HTML_ENTRY && version >= 7 -> {
                            validateBackup(!htmlSeen) { localizedText("备份中包含重复的 HTML。") }
                            archive.readUtf8Limited(MAX_HTML_BYTES)
                            htmlSeen = true
                        }
                        entry.name == COMPLETION_ENTRY && version >= 7 -> {
                            validateBackup(!completionSeen) { localizedText("备份中包含重复的结束标记。") }
                            validateBackup(archive.readUtf8Limited(64) == COMPLETION_MARKER) {
                                localizedText("备份文件不完整。")
                            }
                            completionSeen = true
                        }
                        entry.name.startsWith(IMAGES_PREFIX) -> {
                            val fileName = entry.name.removePrefix(IMAGES_PREFIX)
                            validateBackup(fileName in referencedImages) {
                                localizedText("备份中包含未引用的图片。")
                            }
                            validateBackup(fileName !in restoredImages) {
                                localizedText("备份中包含重复图片。")
                            }
                            attachments.writeEncryptedImage(
                                File(stagingDirectory, fileName),
                                archive,
                                MAX_IMAGE_BYTES,
                                totalBytes,
                            )
                            restoredImages += fileName
                        }
                        entry.name.startsWith(AUDIOS_PREFIX) -> {
                            val fileName = entry.name.removePrefix(AUDIOS_PREFIX)
                            validateBackup(fileName in referencedAudios) {
                                localizedText("备份中包含未引用的录音。")
                            }
                            validateBackup(fileName !in restoredAudios) {
                                localizedText("备份中包含重复录音。")
                            }
                            attachments.writeEncryptedAttachment(
                                target = File(stagingDirectory, fileName),
                                input = archive,
                                byteLimit = MAX_AUDIO_BYTES,
                                totalBytes = totalAudioBytes,
                                itemName = localizedText("录音"),
                                totalName = localizedText("备份录音"),
                                totalLimit = MAX_BACKUP_AUDIO_BYTES,
                            )
                            restoredAudios += fileName
                        }
                        entry.name.startsWith("videos/") && version >= 8 -> {
                            val fileName = entry.name.removePrefix("videos/")
                            validateBackup(
                                fileName in referencedVideos && restoredVideos.add(fileName)
                            ) {
                                tr("备份视频引用无效或重复。", "Invalid or duplicate backup video.")
                            }
                            videos.stageBackup(
                                File(stagingDirectory, fileName),
                                archive,
                                totalVideoBytes,
                            )
                        }
                        else -> validateBackup(false) { localizedText("备份中包含未知内容。") }
                    }
                    archive.closeEntry()
                    entry = archive.nextEntry
                    validateBackup(!completionSeen || entry == null) {
                        localizedText("备份结束标记后包含多余内容。")
                    }
                }
            }

            if (version >= 7) {
                validateBackup(htmlSeen && completionSeen) { localizedText("备份文件不完整。") }
                validateBackup(
                    restoredImages == referencedImages && restoredAudios == referencedAudios
                ) {
                    localizedText("备份缺少图片或录音。")
                }
            }

            val restored =
                normalizeRestoredEntries(
                    requireNotNull(parsedEntries) { localizedText("备份清单缺失。") },
                    restoredImages,
                    restoredAudios,
                    restoredVideos,
                    preserveConflicts = true,
                )
            validateBackup(restoredVideos == referencedVideos) {
                tr("备份缺少视频或封面。", "Backup video or cover is missing.")
            }
            restored.forEach {
                it.video?.let { video -> videos.validateStaged(stagingDirectory, video) }
            }
            PreparedBackup(stagingDirectory, restored)
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            throw normalizedBackupError(error)
        }
    }

    private fun readLegacyBackup(payload: String, password: String): PreparedBackup {
        val stagingDirectory = createStagingDirectory()
        return try {
            val backup = JSONObject(BackupCipher.decryptLegacy(payload, password))
            validateBackup(backup.optString("format") == "xike") { localizedText("这不是息刻备份文件。") }
            validateBackup(backup.optInt("version", 1) in 1..3) { localizedText("暂不支持这个版本的息刻备份。") }
            val parsedEntries = parseEntries(backup.getJSONArray("entries"))
            val referencedImages =
                parsedEntries.flatMap { it.imageFileNames }.filter(::isSafeImageFileName).toSet()
            validateBackup(referencedImages.size <= MAX_BACKUP_IMAGES) {
                localizedText("备份包含过多图片。")
            }

            val restoredImages = linkedSetOf<String>()
            val totalBytes = longArrayOf(0L)
            val images = backup.optJSONObject("images")
            if (images != null) {
                val keys = images.keys()
                while (keys.hasNext()) {
                    val fileName = keys.next()
                    if (fileName in referencedImages && isSafeImageFileName(fileName)) {
                        val encoded = images.getString(fileName)
                        Base64.getDecoder().wrap(encoded.byteInputStream(Charsets.US_ASCII)).use {
                            decoded ->
                            attachments.writeEncryptedImage(
                                target = File(stagingDirectory, fileName),
                                input = decoded,
                                byteLimit = MAX_IMAGE_BYTES,
                                totalBytes = totalBytes,
                            )
                        }
                        restoredImages += fileName
                    }
                }
            }

            val restored =
                normalizeRestoredEntries(
                    parsedEntries,
                    restoredImages,
                    emptySet(),
                    preserveConflicts = true,
                )
            PreparedBackup(stagingDirectory, restored)
        } catch (error: Throwable) {
            stagingDirectory.deleteRecursively()
            throw normalizedBackupError(error)
        }
    }

    private fun normalizedBackupError(error: Throwable): Throwable =
        when (error) {
            is BackupValidationException -> IllegalArgumentException(error.message, error)
            is JournalDataException -> error
            else -> IllegalArgumentException(localizedText("密码错误或备份文件已损坏。"), error)
        }

    private fun parseEntries(values: JSONArray): List<JournalEntry> =
        parseBackupEntries(
            values,
            MAX_BACKUP_ENTRIES,
            ::isSafeImageFileName,
            ::isSafeAudioFileName,
        )

    private fun createStagingDirectory(): File =
        File(
                appContext.filesDir,
                "$RESTORE_STAGING_PREFIX${UUID.randomUUID()}",
            )
            .also { directory ->
                check(directory.mkdir()) { localizedText("无法创建恢复临时目录。") }
            }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val HTML_ENTRY = "index.html"
        const val COMPLETION_ENTRY = "complete.txt"
        const val COMPLETION_MARKER = "XIKE_BACKUP_COMPLETE"
        const val IMAGES_PREFIX = "images/"
        const val AUDIOS_PREFIX = "audios/"
        const val MIN_STREAMING_BACKUP_VERSION = 4
        const val STREAMING_BACKUP_VERSION = 8
        const val MAX_BACKUP_ENTRIES = 100_000
        const val MAX_BACKUP_IMAGES = 10_000
        const val MAX_BACKUP_AUDIOS = 100_000
        const val MAX_MANIFEST_BYTES = MAX_BACKUP_MANIFEST_BYTES
        const val MAX_HTML_BYTES = 64L * 1024L * 1024L
        const val MAX_LEGACY_BACKUP_BYTES = 320L * 1024L * 1024L
    }
}
