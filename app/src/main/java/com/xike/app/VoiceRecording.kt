package com.xike.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class XikeAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    var isPaused: Boolean = false
        private set

    fun start(onLimitReached: () -> Unit): File {
        check(recorder == null) { "录音已经开始。" }
        val target = File.createTempFile("xike-recording-", ".m4a", context.cacheDir)
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
        val activeRecorder = checkNotNull(recorder) { "当前没有录音。" }
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

@Composable
internal fun VoiceCaptureCard(
    startRequest: Int,
    enabled: Boolean,
    onRecorded: suspend (File, Long) -> Result<Unit>,
    onClosed: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val recorder = remember { XikeAudioRecorder(context.applicationContext) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    var totalPaused by remember { mutableLongStateOf(0L) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var amplitudes by remember { mutableStateOf(List(28) { 0.08f }) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var limitReached by remember { mutableLongStateOf(0L) }

    suspend fun finishRecording() {
        if (!isRecording || isSaving) return
        isSaving = true
        val duration = elapsedMillis.coerceAtLeast(1L).coerceAtMost(MAX_AUDIO_DURATION_MILLIS)
        val result = if (duration < 500L) {
            recorder.cancel()
            Result.failure(IllegalStateException("录音太短了，请再说一会儿。"))
        } else runCatching { recorder.stop() }.mapCatching { file ->
            try {
                onRecorded(file, duration).getOrThrow()
            } finally {
                file.delete()
            }
        }
        result.onSuccess {
            isRecording = false
            onClosed()
        }.onFailure { error ->
            isRecording = false
            errorMessage = error.message ?: "录音保存失败，请重试。"
        }
        isSaving = false
    }

    fun beginRecording() {
        if (!enabled || isRecording || isSaving) return
        errorMessage = null
        runCatching {
            recorder.start { limitReached = SystemClock.elapsedRealtime() }
        }.onSuccess {
            startedAt = SystemClock.elapsedRealtime()
            pausedAt = 0L
            totalPaused = 0L
            elapsedMillis = 0L
            amplitudes = List(28) { 0.08f }
            isPaused = false
            isRecording = true
        }.onFailure { error ->
            errorMessage = error.message ?: "无法开始录音，请检查麦克风。"
        }
    }

    LaunchedEffect(startRequest) {
        if (startRequest > 0) beginRecording()
    }

    LaunchedEffect(isRecording, isPaused) {
        while (isRecording) {
            if (!isPaused) {
                val now = SystemClock.elapsedRealtime()
                elapsedMillis = (now - startedAt - totalPaused).coerceAtLeast(0L)
                val amplitude = recorder.maxAmplitude().coerceAtLeast(0)
                val normalized = if (amplitude == 0) 0.08f else {
                    (ln(amplitude.toFloat() + 1f) / ln(32768f)).coerceIn(0.08f, 1f)
                }
                amplitudes = (amplitudes.drop(1) + normalized)
                if (elapsedMillis >= MAX_AUDIO_DURATION_MILLIS - 150L) finishRecording()
            }
            delay(90)
        }
    }

    LaunchedEffect(limitReached) {
        if (limitReached > 0L && isRecording) finishRecording()
    }

    val finishWhenBackgrounded by rememberUpdatedState {
        if (isRecording && !isSaving) scope.launch { finishRecording() }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) finishWhenBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recorder.cancel()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(if (isPaused) MaterialTheme.colorScheme.outline else Color(0xFFC4504D), CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isSaving -> "正在加密保存"
                        isPaused -> "录音已暂停"
                        isRecording -> "正在录音"
                        else -> "录音没有完成"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(formatAudioDuration(elapsedMillis), style = MaterialTheme.typography.labelLarge)
            }

            VoiceWaveform(amplitudes)

            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        recorder.cancel()
                        isRecording = false
                        onClosed()
                    },
                ) { Text("取消") }
                TextButton(
                    enabled = isRecording && !isSaving,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        runCatching {
                            if (isPaused) {
                                recorder.resume()
                                totalPaused += SystemClock.elapsedRealtime() - pausedAt
                                pausedAt = 0L
                                isPaused = false
                            } else {
                                recorder.pause()
                                pausedAt = SystemClock.elapsedRealtime()
                                isPaused = true
                            }
                        }.onFailure { errorMessage = "暂时无法切换录音状态。" }
                    },
                ) {
                    Icon(
                        if (isPaused) Icons.Outlined.MicNone else Icons.Outlined.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (isPaused) "继续" else "暂停")
                }
                Button(
                    enabled = isRecording && !isSaving,
                    modifier = Modifier.weight(1f),
                    elevation = xikeButtonElevation(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 10.dp,
                    ),
                    onClick = { scope.launch { finishRecording() } },
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("完成", maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun VoicePlaybackCard(
    audio: JournalAudio,
    openAudio: (String) -> InputStream?,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember(audio.fileName) { mutableStateOf<MediaPlayer?>(null) }
    var tempFile by remember(audio.fileName) { mutableStateOf<File?>(null) }
    var isPreparing by remember(audio.fileName) { mutableStateOf(false) }
    var isPlaying by remember(audio.fileName) { mutableStateOf(false) }
    var position by remember(audio.fileName) { mutableLongStateOf(0L) }
    var playbackError by remember(audio.fileName) { mutableStateOf<String?>(null) }

    fun releasePlayer() {
        player?.release()
        player = null
        isPlaying = false
        tempFile?.delete()
        tempFile = null
    }

    fun togglePlayback() {
        val active = player
        if (active != null) {
            if (active.isPlaying) {
                active.pause()
                isPlaying = false
            } else {
                active.start()
                isPlaying = true
            }
            return
        }
        if (isPreparing) return
        isPreparing = true
        playbackError = null
        scope.launch {
            runCatching {
                val preparedFile = withContext(Dispatchers.IO) {
                    val target = File.createTempFile("xike-playback-", ".m4a", context.cacheDir)
                    try {
                        openAudio(audio.fileName)?.use { input -> target.outputStream().use(input::copyTo) }
                            ?: error("录音文件不可用。")
                        target
                    } catch (error: Throwable) {
                        target.delete()
                        throw error
                    }
                }
                tempFile = preparedFile
                MediaPlayer().apply {
                    FileInputStream(preparedFile).use { source ->
                        setDataSource(source.fd)
                        prepare()
                    }
                    preparedFile.delete()
                    tempFile = null
                    setOnCompletionListener {
                        isPlaying = false
                        position = 0L
                        seekTo(0)
                    }
                    start()
                }.also {
                    player = it
                    isPlaying = true
                }
            }.onFailure { error ->
                releasePlayer()
                playbackError = error.message ?: "暂时无法播放这段录音。"
            }
            isPreparing = false
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player?.currentPosition?.toLong() ?: 0L
            delay(200)
        }
    }

    DisposableEffect(audio.fileName) {
        onDispose { releasePlayer() }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !isPreparing, onClick = ::togglePlayback) {
                    if (isPreparing) {
                        CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "暂停语音" else "播放语音",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    VoiceWaveform(
                        values = List(28) { index -> 0.16f + ((index * 17 + 11) % 13) / 18f },
                        progress = if (audio.durationMillis <= 0L) 0f else {
                            (position.toFloat() / audio.durationMillis).coerceIn(0f, 1f)
                        },
                    )
                    Text(
                        if (position > 0L) {
                            "${formatAudioDuration(position)} / ${formatAudioDuration(audio.durationMillis)}"
                        } else {
                            "语音 · ${formatAudioDuration(audio.durationMillis)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = {
                            releasePlayer()
                            onDelete()
                        },
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除语音")
                    }
                }
            }
            playbackError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun VoiceWaveform(values: List<Float>, progress: Float? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { index, value ->
            val played = progress == null || index.toFloat() / values.size <= progress
            Box(
                Modifier
                    .weight(1f)
                    .height((5f + value.coerceIn(0f, 1f) * 25f).dp)
                    .background(
                        if (played) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

internal fun formatAudioDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

internal fun pruneVoiceTemporaryFiles(context: Context) {
    context.cacheDir.listFiles()
        ?.filter { it.name.startsWith("xike-recording-") || it.name.startsWith("xike-playback-") }
        ?.forEach(File::delete)
}
