package com.xike.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.security.crypto.MasterKey
import androidx.security.crypto.EncryptedSharedPreferences
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.UUID

const val MAX_VIDEO_DURATION_MILLIS = 300_000L
internal const val MAX_VIDEO_BYTES = 500L * 1024 * 1024
internal const val MAX_BACKUP_VIDEO_BYTES = 10L * 1024 * 1024 * 1024
private const val MAX_COVER_BYTES = 2L * 1024 * 1024

data class JournalVideo(
    val fileName: String,
    val coverFileName: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val mimeType: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fileName", fileName).put("coverFileName", coverFileName)
        .put("durationMillis", durationMillis).put("sizeBytes", sizeBytes).put("mimeType", mimeType)

    internal fun isValid(): Boolean = durationMillis in 1..MAX_VIDEO_DURATION_MILLIS &&
        sizeBytes in 1..MAX_VIDEO_BYTES && mimeType in videoExtensions &&
        safeVideoName(fileName) && fileName.endsWith(".${videoExtensions[mimeType]}") &&
        safeVideoName(coverFileName) && coverFileName.endsWith(".jpg") && fileName != coverFileName

    companion object {
        fun fromJson(json: JSONObject?): JournalVideo? = json?.let {
            JournalVideo(it.getString("fileName"), it.getString("coverFileName"),
                it.getLong("durationMillis"), it.getLong("sizeBytes"), it.getString("mimeType"))
        }
    }
}

internal fun JournalVideo?.files(): Set<String> = this?.let { setOf(it.fileName, it.coverFileName) }.orEmpty()
private val videoExtensions = mapOf("video/mp4" to "mp4", "video/3gpp" to "3gp", "video/webm" to "webm", "video/quicktime" to "mov", "video/x-matroska" to "mkv")
private fun safeVideoName(name: String): Boolean = name.isNotBlank() && name.length <= 160 &&
    File(name).name == name && '/' !in name && '\\' !in name

internal class JournalVideoStore(context: Context) {
    private val context = context.applicationContext
    private val directory = File(this.context.filesDir, "journal-videos")
    private val key = MasterKey.Builder(this.context, VIDEO_MASTER_KEY_ALIAS).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val edits by lazy {
        EncryptedSharedPreferences.create(this.context, "xike-video-edit-drafts", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    fun editDraft(entry: JournalEntry): VideoEditDraft? = edits.getString(entry.id, null)?.let {
        val json = JSONObject(it)
        val original = json.optString("original").takeIf { name -> name != "null" && name.isNotBlank() }
        VideoEditDraft(original, JournalVideo.fromJson(json.optJSONObject("video")))
    }?.takeIf { it.originalFileName == entry.video?.fileName }

    fun saveEdit(entry: JournalEntry, video: JournalVideo?) {
        val json = JSONObject().put("original", entry.video?.fileName ?: JSONObject.NULL).put("video", video?.toJson() ?: JSONObject.NULL)
        check(edits.edit().putString(entry.id, json.toString()).commit()) { tr("视频编辑草稿保存失败。", "Unable to save the video edit draft.") }
    }
    fun clearEdit(entryId: String) { check(edits.edit().remove(entryId).commit()) }
    fun discardStaleEdits(entries: List<JournalEntry>): List<JournalVideo> {
        val current = entries.associateBy { it.id }
        val removed = mutableListOf<JournalVideo>()
        val editor = edits.edit()
        edits.all.forEach { (id, serialized) ->
            val json = JSONObject(serialized as String)
            val original = json.optString("original").takeIf { it != "null" && it.isNotBlank() }
            if (id !in current || current[id]?.video?.fileName != original) {
                editor.remove(id)
                JournalVideo.fromJson(json.optJSONObject("video"))?.let(removed::add)
            }
        }
        check(editor.commit())
        return removed
    }
    fun editReferences(): Set<String> = edits.all.values.flatMap { serialized ->
        JournalVideo.fromJson(JSONObject(serialized as String).optJSONObject("video")).files()
    }.toSet()
    private val cipher: StreamingAead by lazy {
        synchronized(keyLock) {
            StreamingAeadConfig.register()
            AndroidKeysetManager.Builder()
                .withSharedPref(this.context, "video-streaming-key", "xike-video-keyset")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
                .withMasterKeyUri("android-keystore://$VIDEO_MASTER_KEY_ALIAS")
                .build().keysetHandle.getPrimitive(StreamingAead::class.java)
        }
    }
    private fun open(file: File): InputStream = cipher.newDecryptingStream(FileInputStream(file), file.name.toByteArray(Charsets.UTF_8))
    private fun output(file: File): java.io.OutputStream {
        check(!file.exists())
        return cipher.newEncryptingStream(FileOutputStream(file), file.name.toByteArray(Charsets.UTF_8))
    }
    private fun source(file: File, length: Long): MediaDataSource {
        val channel = FileInputStream(file).channel
        return try { EncryptedVideoSource(length, cipher.newSeekableDecryptingChannel(channel, file.name.toByteArray(Charsets.UTF_8))) }
        catch (error: Throwable) { channel.close(); throw error }
    }

    fun open(name: String): InputStream {
        require(safeVideoName(name))
        return open(File(directory, name))
    }

    fun dataSource(video: JournalVideo): MediaDataSource {
        require(video.isValid())
        return source(File(directory, video.fileName), video.sizeBytes)
    }

    fun import(uri: Uri, onProgress: (Float) -> Unit): JournalVideo {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase()
        require(mime in videoExtensions) { tr("暂不支持这个视频格式。", "This video format is not supported.") }
        val expectedSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(expectedSize <= MAX_VIDEO_BYTES) { tr("视频最大支持 500 MB。", "Videos must be at most 500 MB.") }
        if (expectedSize > 0) check(context.filesDir.usableSpace > expectedSize + 8L * 1024 * 1024) {
            tr("本机空间不足，无法保存视频。", "Not enough space to save the video.")
        }
        val probe = MediaMetadataRetriever()
        try {
            probe.setDataSource(context, uri)
            require(probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { tr("文件没有可播放的视频画面。", "The file has no playable video.") }
            val duration = probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(duration in 1..MAX_VIDEO_DURATION_MILLIS) { tr("视频最长支持 5 分钟，请裁剪后重新选择。", "Videos must be at most 5 minutes. Please trim and select again.") }
        } finally { probe.release() }
        val id = UUID.randomUUID().toString()
        val fileName = "$id.${videoExtensions.getValue(requireNotNull(mime))}"
        val coverName = "$id-cover.jpg"
        val target = File(directory, fileName)
        val cover = File(directory, coverName)
        try {
            val size = resolver.openInputStream(uri)?.use { input ->
                write(target, input, MAX_VIDEO_BYTES, null) { bytes ->
                    if (expectedSize > 0) onProgress((bytes.toFloat() / expectedSize).coerceIn(0f, 1f))
                }
            } ?: error(tr("视频无法读取，请重新选择。", "Unable to read the video. Please select it again."))
            val metadata = inspect(source(target, size), cover)
            return JournalVideo(fileName, coverName, metadata, size, mime)
        } catch (error: Throwable) {
            target.delete()
            cover.delete()
            throw error
        }
    }

    private fun inspect(source: MediaDataSource, cover: File? = null): Long = source.use {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(source)
            check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") {
                tr("文件没有可播放的视频画面。", "The file has no playable video.")
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(duration in 1..MAX_VIDEO_DURATION_MILLIS) {
                tr("视频最长支持 5 分钟，请裁剪后重新选择。", "Videos must be at most 5 minutes. Please trim and select again.")
            }
            if (cover != null) {
                val raw = (if (android.os.Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 640)
                    else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                    ?: error(tr("无法读取视频封面，请选择其他视频。", "Unable to read the video cover. Please select another video."))
                val scale = minOf(1f, 640f / maxOf(raw.width, raw.height))
                val frame = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true) else raw
                if (frame !== raw) raw.recycle()
                try {
                    output(cover).use { output ->
                        check(frame.compress(Bitmap.CompressFormat.JPEG, 80, output))
                    }
                } finally { frame.recycle() }
            }
            duration
        } finally { retriever.release() }
    }

    fun requireReadable(video: JournalVideo?) {
        if (video == null) return
        require(video.isValid()) { tr("视频信息无效，原引用已保留。", "Invalid video metadata. The original reference is preserved.") }
        video.files().forEach { name -> open(name).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (input.read(buffer) != -1) { /* Authenticate every encrypted segment. */ }
        } }
    }

    fun stageBackup(target: File, input: InputStream, totalBytes: LongArray) {
        require(safeVideoName(target.name))
        write(target, input, if (target.name.endsWith(".jpg")) MAX_COVER_BYTES else MAX_VIDEO_BYTES, totalBytes)
    }

    fun validateStaged(staging: File, video: JournalVideo) {
        require(video.isValid())
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(File(staging, video.coverFileName)).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..640 && bounds.outHeight in 1..640) { tr("备份视频封面无效。", "Invalid backup video cover.") }
        val duration = inspect(source(File(staging, video.fileName), video.sizeBytes))
        require(duration == video.durationMillis) { tr("备份视频时长不匹配。", "Backup video duration does not match.") }
        open(File(staging, video.fileName)).use { input ->
            val buffer = ByteArray(64 * 1024)
            var size = 0L
            while (true) { val count = input.read(buffer); if (count < 0) break; size += count }
            require(size == video.sizeBytes) { tr("备份视频大小不匹配。", "Backup video size does not match.") }
        }
    }

    fun install(staging: File, video: JournalVideo): JournalVideo {
        val id = UUID.randomUUID().toString()
        val installed = video.copy(fileName = "$id.${videoExtensions.getValue(video.mimeType)}", coverFileName = "$id-cover.jpg")
        try {
            listOf(video.fileName to installed.fileName, video.coverFileName to installed.coverFileName).forEach { (old, new) ->
                open(File(staging, old)).use { input -> stageBackup(File(directory, new), input, longArrayOf(0L)) }
            }
            return installed
        } catch (error: Throwable) { filesOnDisk(installed).forEach(File::delete); throw error }
    }

    fun filesOnDisk(video: JournalVideo): List<File> = video.files().map { File(directory, it) }
    fun deleteExcept(video: JournalVideo, retained: Set<String>) = video.files().filterNot { it in retained }.forEach {
        if (safeVideoName(it)) File(directory, it).delete()
    }
    fun prune(retained: Set<String>) { directory.listFiles()?.filterNot { it.name in retained }?.forEach(File::delete) }

    private fun write(target: File, input: InputStream, limit: Long, total: LongArray?, progress: (Long) -> Unit = {}): Long {
        check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        var size = 0L
        try {
            output(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= limit) { tr("视频最大支持 500 MB，封面最大支持 2 MB。", "Videos must be at most 500 MB; covers at most 2 MB.") }
                    total?.let { it[0] += count; require(it[0] <= MAX_BACKUP_VIDEO_BYTES) { tr("备份视频超过 10 GB。", "Backup videos exceed 10 GB.") } }
                    output.write(buffer, 0, count)
                    progress(size)
                }
            }
            require(size > 0)
            return size
        } catch (error: Throwable) { target.delete(); throw error }
    }
    private companion object {
        val keyLock = Any()
        const val VIDEO_MASTER_KEY_ALIAS = "xike-video-master-key"
    }
}

/** Tink authenticates and decrypts only the requested segments, including after seeking. */
internal data class VideoEditDraft(val originalFileName: String?, val video: JournalVideo?)

internal class EncryptedVideoSource(private val length: Long, private val channel: SeekableByteChannel) : MediaDataSource() {
    @Synchronized override fun readAt(position: Long, buffer: ByteArray, destinationOffset: Int, size: Int): Int {
        check(channel.isOpen)
        if (position < 0 || position >= length) return -1
        if (size == 0) return 0
        channel.position(position)
        return channel.read(ByteBuffer.wrap(buffer, destinationOffset, minOf(size.toLong(), length - position).toInt()))
    }
    override fun getSize(): Long = length
    @Synchronized override fun close() { channel.close() }
}
