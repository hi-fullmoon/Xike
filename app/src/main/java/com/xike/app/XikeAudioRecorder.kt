package com.xike.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

internal class XikeAudioRecorder(
    private val context: Context,
    private val recordingDirectory: File = context.cacheDir,
) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    var isPaused: Boolean = false
        private set

    fun start(onLimitReached: () -> Unit): File {
        check(recorder == null) { localizedText("录音已经开始。") }
        check(recordingDirectory.isDirectory || recordingDirectory.mkdirs()) { localizedText("无法创建录音暂存目录。") }
        val target = File.createTempFile("xike-recording-", ".m4a", recordingDirectory)
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        runCatching {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(64_000)
            mediaRecorder.setMaxDuration(MAX_AUDIO_DURATION_MILLIS.toInt())
            mediaRecorder.setOutputFile(target.absolutePath)
            mediaRecorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    onLimitReached()
                }
            }
            mediaRecorder.prepare()
            mediaRecorder.start()
        }.onFailure {
            mediaRecorder.release()
            target.delete()
            throw it
        }
        recorder = mediaRecorder
        output = target
        isPaused = false
        return target
    }

    fun pause() {
        recorder?.pause()
        isPaused = true
    }

    fun resume() {
        recorder?.resume()
        isPaused = false
    }

    fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun stop(): File {
        val activeRecorder = checkNotNull(recorder) { localizedText("当前没有录音。") }
        val target = checkNotNull(output)
        recorder = null
        output = null
        isPaused = false
        try {
            activeRecorder.stop()
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            activeRecorder.release()
        }
        return target
    }

    fun cancel() {
        val activeRecorder = recorder
        val target = output
        recorder = null
        output = null
        isPaused = false
        if (activeRecorder != null) {
            runCatching { activeRecorder.stop() }
            activeRecorder.release()
        }
        target?.delete()
    }
}
