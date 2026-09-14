package com.xike.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    enabled: Boolean,
    onRecorded: (File, Long) -> Unit,
    onClosed: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember { XikeAudioRecorder(context.applicationContext) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isStopping by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    var totalPaused by remember { mutableLongStateOf(0L) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var amplitudes by remember { mutableStateOf(List(28) { 0.08f }) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var limitReached by remember { mutableLongStateOf(0L) }

    fun currentDuration(): Long {
        val now = SystemClock.elapsedRealtime()
        val activePause = if (isPaused) now - pausedAt else 0L
        return (now - startedAt - totalPaused - activePause)
            .coerceIn(0L, MAX_AUDIO_DURATION_MILLIS)
    }

    fun finishRecording() {
        if (!isRecording || isStopping) return
        isStopping = true
        val duration = currentDuration().coerceAtLeast(1L)
        elapsedMillis = duration
        if (duration < 500L) {
            recorder.cancel()
            isRecording = false
            errorMessage = "录音太短了，请再说一会儿。"
        } else {
            runCatching { recorder.stop() }
                .onSuccess { file ->
                    isRecording = false
                    onRecorded(file, duration)
                    onClosed()
                }
                .onFailure { error ->
                    isRecording = false
                    errorMessage = error.message ?: "录音没有完成，请重新录制。"
                }
        }
        isStopping = false
    }

    fun beginRecording() {
        if (!enabled || isRecording || isStopping) return
        errorMessage = null
        runCatching {
            recorder.start { limitReached = SystemClock.elapsedRealtime() }
        }.onSuccess {
            startedAt = SystemClock.elapsedRealtime()
            pausedAt = 0L
            totalPaused = 0L
            elapsedMillis = 0L
            amplitudes = List(28) { 0.08f }
            limitReached = 0L
            isPaused = false
            isRecording = true
        }.onFailure { error ->
            errorMessage = error.message ?: "无法开始录音，请检查麦克风。"
        }
    }

    LaunchedEffect(Unit) {
        beginRecording()
    }

    LaunchedEffect(isRecording, isPaused) {
        while (isRecording) {
            if (!isPaused) {
                elapsedMillis = currentDuration()
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
        if (isRecording && !isStopping) finishRecording()
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
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isStopping -> "正在结束录音"
                        isPaused -> "录音已暂停"
                        isRecording -> "正在录音"
                        else -> "录音未开始"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${formatAudioDuration(elapsedMillis)} / ${formatAudioDuration(MAX_AUDIO_DURATION_MILLIS)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = XikeShapes.inner,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
                    VoiceWaveform(amplitudes, isPaused)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "最长 5 分钟 · 离开应用时自动结束",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !isStopping,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    onClick = {
                        recorder.cancel()
                        isRecording = false
                        onClosed()
                    },
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.xikeInlineActionIcon(),
                    )
                    Spacer(Modifier.width(XikeInlineActionGap))
                    Text("取消", maxLines = 1)
                }
                OutlinedButton(
                    enabled = isRecording && !isStopping,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = null,
                        modifier = Modifier.xikeInlineActionIcon(),
                    )
                    Spacer(Modifier.width(XikeInlineActionGap))
                    Text(if (isPaused) "继续" else "暂停", maxLines = 1)
                }
            }
            Button(
                enabled = enabled && !isStopping,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = XikeShapes.button,
                elevation = xikeButtonElevation(),
                onClick = { if (isRecording) finishRecording() else beginRecording() },
            ) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (isStopping) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(
                        if (isRecording) Icons.Outlined.StopCircle else Icons.Outlined.MicNone,
                        contentDescription = null,
                        modifier = Modifier.xikeInlineActionIcon(),
                    )
                }
                Spacer(Modifier.width(XikeInlineActionGap))
                Text(if (isRecording) "结束录音" else "开始录音", maxLines = 1)
            }
        }
    }
}

@Composable
internal fun VoicePendingSaveCard(
    durationMillis: Long,
    isSaving: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    if (isSaving) "正在加密保存录音" else "录音尚未保存",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    formatAudioDuration(durationMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isSaving) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
                ) {
                    Text(
                        errorMessage ?: "录音暂存在本机，可以重试保存。",
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirmDiscard = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text("放弃录音")
                    }
                    Button(onClick = onRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text("重试保存")
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃这段录音？") },
            text = { Text("尚未保存的录音会被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDiscard()
                }) { Text("放弃录音") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续保存") }
            },
        )
    }
}

@Composable
internal fun VoicePlaybackCard(
    audio: JournalAudio,
    openAudio: (String) -> InputStream?,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onReplace: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember(audio.fileName) { mutableStateOf<MediaPlayer?>(null) }
    var tempFile by remember(audio.fileName) { mutableStateOf<File?>(null) }
    var isPreparing by remember(audio.fileName) { mutableStateOf(false) }
    var isPlaying by remember(audio.fileName) { mutableStateOf(false) }
    var position by remember(audio.fileName) { mutableLongStateOf(0L) }
    var isSeeking by remember(audio.fileName) { mutableStateOf(false) }
    var seekPosition by remember(audio.fileName) { mutableFloatStateOf(0f) }
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
                    if (position > 0L) seekTo(position.coerceAtMost(duration.toLong()).toInt())
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
            if (!isSeeking) position = player?.currentPosition?.toLong() ?: 0L
            delay(200)
        }
    }

    DisposableEffect(audio.fileName) {
        onDispose { releasePlayer() }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("声音片段", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    formatAudioDuration(audio.durationMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = !isPreparing,
                    onClick = ::togglePlayback,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        CircleShape,
                    ),
                ) {
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
                    Slider(
                        value = (if (isSeeking) seekPosition else position.toFloat())
                            .coerceIn(0f, audio.durationMillis.coerceAtLeast(1L).toFloat()),
                        onValueChange = {
                            isSeeking = true
                            seekPosition = it
                        },
                        onValueChangeFinished = {
                            position = seekPosition.toLong()
                            player?.seekTo(position.toInt())
                            isSeeking = false
                        },
                        valueRange = 0f..audio.durationMillis.coerceAtLeast(1L).toFloat(),
                        enabled = !isPreparing,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "语音播放进度" },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Text(
                        "${formatAudioDuration(if (isSeeking) seekPosition.toLong() else position)} / " +
                            formatAudioDuration(audio.durationMillis),
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
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "删除语音",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            playbackError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (onReplace != null) {
                TextButton(onClick = {
                    releasePlayer()
                    onReplace()
                }) {
                    Icon(
                        Icons.Outlined.MicNone,
                        contentDescription = null,
                        modifier = Modifier.xikeInlineActionIcon(),
                    )
                    Spacer(Modifier.width(XikeInlineActionGap))
                    Text("重新录制", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveform(values: List<Float>, isPaused: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { index, value ->
            Box(
                Modifier
                    .weight(1f)
                    .height((5f + value.coerceIn(0f, 1f) * 31f).dp)
                    .background(
                        if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = if (index < values.lastIndex - 7) 0.5f else 1f),
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
