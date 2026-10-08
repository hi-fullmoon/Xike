package com.xike.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.pow
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
        check(recorder == null) { localizedText("录音已经开始。") }
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VoiceCaptureSheet(
    enabled: Boolean,
    onRecorded: (File, Long) -> Unit,
    onClosed: () -> Unit,
) {
    var finishRequest by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden) {
                finishRequest++
                false
            } else true
        },
    )
    ModalBottomSheet(
        onDismissRequest = { finishRequest++ },
        sheetState = sheetState,
        sheetMaxWidth = XikeContentMaxWidth,
        shape = XikeShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(localizedText("录下这一刻"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                localizedText("录音结束后将加密保存"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            VoiceCaptureCard(enabled, onRecorded, onClosed, finishRequest)
        }
    }
}

@Composable
internal fun VoiceCaptureCard(
    enabled: Boolean,
    onRecorded: (File, Long) -> Unit,
    onClosed: () -> Unit,
    finishRequest: Int = 0,
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
    var waveform by remember { mutableStateOf(VoiceWaveformTimeline()) }
    var waveformOffset by remember { mutableFloatStateOf(0f) }
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
            errorMessage = localizedText("录音太短了，请再说一会儿。")
        } else {
            runCatching { recorder.stop() }
                .onSuccess { file ->
                    isRecording = false
                    onRecorded(file, duration)
                    onClosed()
                }
                .onFailure { error ->
                    isRecording = false
                    errorMessage = error.message ?: localizedText("录音没有完成，请重新录制。")
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
            waveform = VoiceWaveformTimeline()
            waveformOffset = 0f
            limitReached = 0L
            isPaused = false
            isRecording = true
        }.onFailure { error ->
            errorMessage = error.message ?: localizedText("无法开始录音，请检查麦克风。")
        }
    }

    LaunchedEffect(Unit) {
        beginRecording()
    }

    LaunchedEffect(finishRequest) {
        if (finishRequest > 0) {
            if (isRecording) finishRecording() else onClosed()
        }
    }

    LaunchedEffect(isRecording, isPaused) {
        while (isRecording && !isPaused) {
            val duration = currentDuration()
            elapsedMillis = duration
            if (waveform.isSampleDue(duration)) {
                val amplitude = recorder.maxAmplitude().coerceAtLeast(0)
                val normalized = (amplitude / 32768f).coerceIn(0f, 1f).pow(0.65f)
                waveform = waveform.append(duration, normalized)
            }
            waveformOffset = waveform.offset(duration)
            if (duration >= MAX_AUDIO_DURATION_MILLIS - 150L) finishRecording()
            delay(waveform.nextSampleDelay(currentDuration()))
        }
    }

    LaunchedEffect(isRecording, isPaused) {
        while (isRecording && !isPaused) {
            withFrameNanos {
                waveformOffset = waveform.offset(currentDuration())
            }
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
            modifier = Modifier.padding(XikeCardPadding),
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
                        isStopping -> localizedText("正在结束录音")
                        isPaused -> localizedText("录音已暂停")
                        isRecording -> localizedText("正在录音")
                        else -> localizedText("录音未开始")
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    formatAudioDuration(MAX_AUDIO_DURATION_MILLIS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                formatAudioDuration(elapsedMillis),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Surface(
                shape = XikeShapes.inner,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
            ) {
                Column(Modifier.fillMaxWidth().padding(XikeInnerCardPadding)) {
                    VoiceWaveform(waveform.values) { waveformOffset }
                }
            }
            Text(
                localizedText("最长 5 分钟 · 离开应用时自动结束"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isStopping,
                    modifier = Modifier.weight(1f),
                    shape = XikeShapes.button,
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
                    Text(localizedText("取消"), maxLines = 1)
                }
                OutlinedButton(
                    enabled = isRecording && !isStopping,
                    modifier = Modifier.weight(1f),
                    shape = XikeShapes.button,
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
                        }.onFailure { errorMessage = localizedText("暂时无法切换录音状态。") }
                    },
                ) {
                    Icon(
                        if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = null,
                        modifier = Modifier.xikeInlineActionIcon(),
                    )
                    Spacer(Modifier.width(XikeInlineActionGap))
                    Text(if (isPaused) localizedText("继续") else localizedText("暂停"), maxLines = 1)
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
                Text(if (isRecording) localizedText("结束录音") else localizedText("开始录音"), maxLines = 1)
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
            modifier = Modifier.padding(XikeCardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    if (isSaving) localizedText("正在加密保存录音") else localizedText("录音尚未保存"),
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
                    shape = XikeShapes.inner,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
                ) {
                    Text(
                        errorMessage ?: localizedText("录音暂存在本机，可以重试保存。"),
                        modifier = Modifier.fillMaxWidth().padding(XikeInnerCardPadding),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirmDiscard = true },
                        modifier = Modifier.weight(1f),
                        shape = XikeShapes.button,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(localizedText("放弃录音"))
                    }
                    Button(onClick = onRetry, modifier = Modifier.weight(1f), shape = XikeShapes.button) {
                        Text(localizedText("重试保存"))
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        DestructiveConfirmationDialog(
            onDismiss = { confirmDiscard = false },
            title = localizedText("放弃这段录音？"),
            confirmText = localizedText("放弃录音"),
            dismissText = localizedText("继续保存"),
            text = { Text(localizedText("尚未保存的录音会被删除，无法恢复。")) },
            onConfirm = {
                confirmDiscard = false
                onDiscard()
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
                            ?: error(localizedText("录音文件不可用。"))
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
                playbackError = error.message ?: localizedText("暂时无法播放这段录音。")
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
        Column(Modifier.padding(XikeCardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FilledIconButton(
                    enabled = !isPreparing,
                    onClick = ::togglePlayback,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (isPreparing) {
                        CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) localizedText("暂停语音") else localizedText("播放语音"),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(localizedText("声音片段"), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatAudioDuration(audio.durationMillis),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    VoiceProgressSlider(
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
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = localizedText("语音播放进度") },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatAudioDuration(if (isSeeking) seekPosition.toLong() else position),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "−${formatAudioDuration((audio.durationMillis - (if (isSeeking) seekPosition.toLong() else position)).coerceAtLeast(0L))}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            playbackError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (onReplace != null || onDelete != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onReplace != null) {
                        TextButton(shape = XikeShapes.button, onClick = {
                            releasePlayer()
                            onReplace()
                        }) {
                            Icon(
                                Icons.Outlined.MicNone,
                                contentDescription = null,
                                modifier = Modifier.xikeInlineActionIcon(),
                            )
                            Spacer(Modifier.width(XikeInlineActionGap))
                            Text(localizedText("重新录制"), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) {
                        IconButton(onClick = {
                            releasePlayer()
                            onDelete()
                        }) {
                            Icon(Icons.Outlined.DeleteOutline,
                                contentDescription = localizedText("删除语音"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VoiceProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
        thumb = {
            Box(Modifier.size(width = 12.dp, height = 16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).background(
                    if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape,
                ))
            }
        },
        track = { state ->
            val fraction = ((state.value - valueRange.start) /
                (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape,
                ))
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(
                    if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape,
                ))
            }
        },
    )
}

@Composable
private fun VoiceWaveform(values: List<Float?>, scrollOffset: () -> Float) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val center = size.height / 2f
        val step = 5.5.dp.toPx()
        val inset = 2.dp.toPx()
        val right = size.width - inset
        val offset = scrollOffset().coerceAtLeast(0f) * step
        for (age in values.indices) {
            val x = right - age * step - offset
            if (x < inset) break
            val value = values[values.lastIndex - age] ?: continue
            val halfHeight = 1.dp.toPx() + value.coerceIn(0f, 1f) * (center - 5.dp.toPx())
            val fade = ((x - inset) / 30.dp.toPx()).coerceIn(0f, 1f)
            val alpha = (0.38f + 0.55f * (x / size.width.coerceAtLeast(1f))) * fade
            drawLine(
                color.copy(alpha = alpha),
                Offset(x, center - halfHeight), Offset(x, center + halfHeight),
                strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
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
