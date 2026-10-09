package com.xike.app

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Window
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.pow
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val recorder = remember { XikeAudioRecorder(context.applicationContext) }
    var isClosed by remember { mutableStateOf(false) }
    fun closeCapture() {
        if (isClosed) return
        isClosed = true
        recorder.cancel()
        onClosed()
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden) {
                closeCapture()
                false
            } else true
        },
    )
    ModalBottomSheet(
        onDismissRequest = { closeCapture() },
        sheetState = sheetState,
        sheetMaxWidth = XikeContentMaxWidth,
        shape = XikeShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        VoiceCaptureDismissHandler { closeCapture() }
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = XikeSheetHorizontalPadding).padding(bottom = XikeSheetBottomPadding),
        ) {
            Text(localizedText("录下这一刻"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                localizedText("录音结束后将加密保存"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            VoiceCaptureCard(enabled, onRecorded, { closeCapture() }, recorder, { isClosed })
        }
    }
}

@Composable
private fun VoiceCaptureDismissHandler(onDismiss: () -> Unit) {
    val view = LocalView.current
    val dismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val original = window?.callback
        if (window == null || original == null) return@DisposableEffect onDispose {}
        val callback = object : Window.Callback by original {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) dismiss()
                original.onWindowFocusChanged(hasFocus)
            }
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    return true
                }
                return original.dispatchKeyEvent(event)
            }
        }
        window.callback = callback
        val backCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OnBackInvokedCallback { dismiss() }.also {
                window.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, it)
            }
        } else null
        onDispose {
            if (window.callback === callback) window.callback = original
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
                window.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
            }
        }
    }
}

@Composable
private fun VoiceCaptureLayout(content: @Composable (androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer(cacheSize = 16)
        val typography = MaterialTheme.typography
        fun textHeight(text: String, style: TextStyle, width: Int): Int =
            textMeasurer.measure(text, style, constraints = Constraints(maxWidth = width.coerceAtLeast(1))).size.height
        val panelWidth = with(density) { (maxWidth - 32.dp).roundToPx() }
        val timerWidth = textMeasurer.measure("5:00", typography.titleLarge.copy(fontFamily = FontFamily.Monospace)).size.width
        val statusWidth = panelWidth - timerWidth - with(density) { 28.dp.roundToPx() }
        val recordingHeight = maxOf(
            textHeight(localizedText("正在录音"), typography.labelMedium, statusWidth),
            textHeight("5:00", typography.titleLarge.copy(fontFamily = FontFamily.Monospace), panelWidth),
        ) + with(density) { 72.dp.roundToPx() } + maxOf(
            textHeight(tr("松手完成，上滑取消", "Release to finish · Slide up to cancel"), typography.labelMedium, panelWidth),
            textHeight(tr("松开取消", "Release to cancel"), typography.labelMedium, panelWidth),
        )
        val idleHeight = with(density) { 68.dp.roundToPx() } +
            textHeight(tr("按住下方按钮开始录音", "Hold the button below to record"), typography.bodyMedium, panelWidth) +
            textHeight(tr("最长 5 分钟", "Up to 5 minutes"), typography.labelSmall, panelWidth)
        val panelHeight = with(density) { maxOf(164.dp.roundToPx(), maxOf(idleHeight, recordingHeight) + 24.dp.roundToPx()).toDp() }
        val buttonTextWidth = with(density) { (maxWidth - 52.dp - XikeInlineActionGap).roundToPx() }
        val buttonHeight = with(density) {
            maxOf(56.dp.roundToPx(), listOf(
                tr("按住说话", "Hold to record"), tr("松手完成", "Release to finish"), tr("松开取消", "Release to cancel"),
            ).maxOf { textHeight(it, typography.labelLarge, buttonTextWidth) } + 24.dp.roundToPx()).toDp()
        }
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content(panelHeight, buttonHeight)
        }
    }
}

@Composable
internal fun VoiceCaptureCard(
    enabled: Boolean,
    onRecorded: (File, Long) -> Unit,
    onClosed: () -> Unit,
    recorder: XikeAudioRecorder,
    isClosed: () -> Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isRecording by remember { mutableStateOf(false) }
    var isStopping by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var waveform by remember { mutableStateOf(VoiceWaveformTimeline()) }
    var waveformOffset by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var limitReached by remember { mutableLongStateOf(0L) }
    var cancelRequested by remember { mutableStateOf(false) }
    var cancelBounds by remember { mutableStateOf(Rect.Zero) }
    var buttonOrigin by remember { mutableStateOf(Offset.Zero) }

    fun cancelRecording() {
        recorder.cancel()
        isRecording = false
        cancelRequested = false
        elapsedMillis = 0L
        waveform = VoiceWaveformTimeline()
        waveformOffset = 0f
    }

    fun currentDuration(): Long {
        val now = SystemClock.elapsedRealtime()
        return (now - startedAt)
            .coerceIn(0L, MAX_AUDIO_DURATION_MILLIS)
    }

    fun finishRecording() {
        if (isClosed() || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            cancelRecording()
            onClosed()
            return
        }
        if (!isRecording || isStopping) return
        if (cancelRequested) {
            cancelRecording()
            onClosed()
            return
        }
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
        if (!enabled || isRecording || isStopping || isClosed() ||
            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return
        errorMessage = null
        runCatching {
            recorder.start { limitReached = SystemClock.elapsedRealtime() }
        }.onSuccess {
            startedAt = SystemClock.elapsedRealtime()
            elapsedMillis = 0L
            waveform = VoiceWaveformTimeline()
            waveformOffset = 0f
            limitReached = 0L
            isRecording = true
        }.onFailure { error ->
            errorMessage = error.message ?: localizedText("无法开始录音，请检查麦克风。")
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
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

    LaunchedEffect(isRecording) {
        while (isRecording) {
            withFrameNanos {
                waveformOffset = waveform.offset(currentDuration())
            }
        }
    }

    LaunchedEffect(limitReached) {
        if (limitReached > 0L && isRecording) finishRecording()
    }

    val finishWhenBackgrounded by rememberUpdatedState {
        cancelRecording()
        onClosed()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) finishWhenBackgrounded()
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
        VoiceCaptureLayout { panelHeight, buttonHeight ->
            Surface(
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { cancelBounds = it.boundsInRoot() },
                shape = XikeShapes.inner,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                color = when {
                    cancelRequested -> MaterialTheme.colorScheme.errorContainer
                    isRecording -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(panelHeight).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRecording) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    localizedText("正在录音"),
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    formatAudioDuration(elapsedMillis),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                                )
                            }
                            VoiceWaveform(waveform.values) { waveformOffset }
                            Text(
                                if (cancelRequested) tr("松开取消", "Release to cancel")
                                else tr("松手完成，上滑取消", "Release to finish · Slide up to cancel"),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (cancelRequested) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.MicNone,
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                tr("按住下方按钮开始录音", "Hold the button below to record"),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                tr("最长 5 分钟", "Up to 5 minutes"),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            val beginHeldRecording by rememberUpdatedState { beginRecording() }
            val finishHeldRecording by rememberUpdatedState { finishRecording() }
            val cancelHeldRecording by rememberUpdatedState { cancelRecording() }
            Surface(
                modifier = Modifier.fillMaxWidth().height(buttonHeight)
                    .onGloballyPositioned { buttonOrigin = it.positionInRoot() }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = tr("长按录音，上滑取消，松手完成", "Hold to record, slide up to cancel, release to finish")
                        stateDescription = if (isRecording) localizedText("正在录音") else localizedText("录音未开始")
                        if (!enabled || isStopping) disabled()
                        onClick(label = if (isRecording) localizedText("结束录音") else localizedText("开始录音")) {
                            if (!enabled || isStopping) false else {
                                if (isRecording) finishRecording() else beginRecording()
                                true
                            }
                        }
                        onLongClick(label = localizedText("开始录音")) {
                            if (!enabled || isRecording || isStopping) false else { beginRecording(); true }
                        }
                        customActions = if (isRecording && !isStopping) listOf(
                            CustomAccessibilityAction(localizedText("取消")) { cancelRecording(); onClosed(); true },
                        ) else emptyList()
                    }
                    .pointerInput(enabled) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!enabled) return@awaitEachGesture
                            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            held.consume()
                            beginHeldRecording()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == held.id }
                                    if (change == null || change.isConsumed) {
                                        cancelHeldRecording()
                                        break
                                    }
                                    val position = buttonOrigin + change.position
                                    cancelRequested = position.y < cancelBounds.bottom - 24.dp.toPx()
                                    change.consume()
                                    if (!change.pressed) {
                                        finishHeldRecording()
                                        break
                                    }
                                }
                            } finally {
                                cancelHeldRecording()
                            }
                        }
                    },
                shape = XikeShapes.button,
                color = if (cancelRequested) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (cancelRequested) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            ) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        if (isStopping) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(
                            Icons.Outlined.MicNone,
                            contentDescription = null,
                            modifier = Modifier.xikeInlineActionIcon(),
                        )
                    }
                    Spacer(Modifier.width(XikeInlineActionGap))
                    Text(when {
                        cancelRequested -> tr("松开取消", "Release to cancel")
                        isRecording -> tr("松手完成", "Release to finish")
                        else -> tr("按住说话", "Hold to record")
                    }, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
                }
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
    showBorder: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    showDeleteLabel: Boolean = false,
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
    val playbackOwner = remember(audio.fileName) { Any() }
    val disposed = remember(audio.fileName) { java.util.concurrent.atomic.AtomicBoolean(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle

    val playbackAttributes = remember(audio.fileName) { playbackAudioAttributes(AudioAttributes.CONTENT_TYPE_SPEECH) }

    fun pausePlayback() {
        player?.let { active ->
            if (isPlaying) runCatching {
                active.pause()
                position = active.currentPosition.toLong()
            }
        }
        isPlaying = false
        journalPlayback.release(playbackOwner)
    }

    fun claimPlayback(): Boolean {
        val granted = claimPlaybackFocus(context, playbackOwner, playbackAttributes, ::pausePlayback)
        if (!granted) playbackError = tr("暂时无法播放，请稍后再试。", "Unable to play right now. Please try again shortly.")
        return granted
    }

    fun releasePlayer() {
        player?.release()
        journalPlayback.release(playbackOwner)
        player = null
        isPlaying = false
        tempFile?.delete()
        tempFile = null
    }

    fun togglePlayback() {
        val active = player
        if (active != null) {
            runCatching {
                if (isPlaying) pausePlayback() else {
                    if (!claimPlayback()) return@runCatching
                    playbackError = null
                    active.start()
                    isPlaying = true
                }
            }.onFailure {
                releasePlayer()
                playbackError = localizedText("暂时无法播放这段录音。")
            }
            return
        }
        if (isPreparing) return
        if (!claimPlayback()) return
        isPreparing = true
        playbackError = null
        scope.launch {
            var openedFile: File? = null
            runCatching {
                val preparedFile = withContext(Dispatchers.IO) {
                    val target = File.createTempFile("xike-playback-", ".m4a", context.cacheDir)
                    openedFile = target
                    try {
                        openAudio(audio.fileName)?.use { input -> target.outputStream().use(input::copyTo) }
                            ?: error(localizedText("录音文件不可用。"))
                        target
                    } catch (error: Throwable) {
                        target.delete()
                        throw error
                    }
                }
                if (disposed.get()) return@runCatching
                tempFile = preparedFile
                val preparedPlayer = MediaPlayer()
                player = preparedPlayer
                preparedPlayer.apply {
                    setAudioAttributes(playbackAttributes)
                    FileInputStream(preparedFile).use { source ->
                        setDataSource(source.fd)
                    }
                    setOnPreparedListener {
                        preparedFile.delete()
                        tempFile = null
                        isPreparing = false
                        runCatching {
                            if (position > 0L) seekTo(position.coerceAtMost(duration.toLong()).toInt())
                            if (journalPlayback.owns(playbackOwner) &&
                                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                                start()
                                isPlaying = true
                            }
                        }.onFailure {
                            releasePlayer()
                            playbackError = localizedText("暂时无法播放这段录音。")
                        }
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        position = 0L
                        journalPlayback.release(playbackOwner)
                        seekTo(0)
                    }
                    setOnErrorListener { _, _, _ ->
                        releasePlayer()
                        isPreparing = false
                        playbackError = localizedText("暂时无法播放这段录音。")
                        true
                    }
                    prepareAsync()
                }
            }.onFailure {
                releasePlayer()
                if (!disposed.get()) playbackError = localizedText("暂时无法播放这段录音。")
                isPreparing = false
            }
            if (tempFile !== openedFile) openedFile?.delete()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isSeeking) position = player?.currentPosition?.toLong() ?: 0L
            delay(200)
        }
    }

    val currentPause by rememberUpdatedState(::pausePlayback)
    DisposableEffect(audio.fileName, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) currentPause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            disposed.set(true)
            lifecycle.removeObserver(observer)
            releasePlayer()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = containerColor,
        border = if (showBorder) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else null,
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
                    VoicePlaybackIndicator(isPlaying)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    PlaybackProgressSlider(
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
                            formatAudioDuration(audio.durationMillis),
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
                Spacer(Modifier.height(4.dp))
                val actions: @Composable () -> Unit = {
                    if (onReplace != null) {
                        TextButton(shape = XikeShapes.button, onClick = {
                            releasePlayer()
                            onReplace()
                        }) {
                            if (!showDeleteLabel) {
                                Icon(
                                    Icons.Outlined.MicNone,
                                    contentDescription = null,
                                    modifier = Modifier.xikeInlineActionIcon(),
                                )
                                Spacer(Modifier.width(XikeInlineActionGap))
                            }
                            Text(localizedText("重新录制"), maxLines = if (showDeleteLabel) Int.MAX_VALUE else 1)
                        }
                    }
                    if (onDelete != null) {
                        if (showDeleteLabel) {
                            TextButton(shape = XikeShapes.button, onClick = {
                                releasePlayer()
                                onDelete()
                            }) {
                                Text(localizedText("删除语音"))
                            }
                        } else IconButton(onClick = {
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
                if (showDeleteLabel) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) { actions() }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (onReplace == null) Arrangement.End else Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { actions() }
                }
            }
        }
    }
}

@Composable
private fun VoicePlaybackIndicator(isPlaying: Boolean) {
    val color by animateColorAsState(
        if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(240),
        label = "Voice indicator color",
    )
    val activity by animateFloatAsState(
        if (isPlaying) 1f else 0f,
        animationSpec = tween(280),
        label = "Voice indicator activity",
    )
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        val motionDurationScale = currentCoroutineContext()[MotionDurationScale]
        var previousFrame = withFrameNanos { it }
        while (true) {
            val durationScale = motionDurationScale?.scaleFactor ?: 1f
            if (durationScale == 0f) {
                snapshotFlow { motionDurationScale?.scaleFactor ?: 1f }.first { it > 0f }
                previousFrame = withFrameNanos { it }
                continue
            }
            withInfiniteAnimationFrameNanos { frame ->
                val elapsed = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                phase = (phase + elapsed / (1.6f * durationScale)) % 1f
                previousFrame = frame
            }
        }
    }
    // A playback activity indicator, not an invented recording waveform.
    Canvas(Modifier.size(64.dp, 16.dp)) {
        val cycle = phase * (2 * PI).toFloat()
        repeat(12) { index ->
            val envelope = sin(PI * (index + 0.5) / 12).toFloat()
            val pulse = (sin(cycle - index * 0.65f) + 1f) / 2f
            val restingHeight = 0.16f + envelope * 0.22f
            val playingHeight = 0.2f + envelope * (0.25f + pulse * 0.42f)
            val height = size.height * (restingHeight + (playingHeight - restingHeight) * activity)
            val x = size.width * (index + 0.5f) / 12
            drawLine(color.copy(alpha = 0.65f + envelope * 0.35f),
                Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlaybackProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    activeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    inactiveColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
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
                    if (enabled) activeColor else inactiveColor,
                    CircleShape,
                ))
            }
        },
        track = { state ->
            val fraction = ((state.value - valueRange.start) /
                (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(
                    inactiveColor, CircleShape,
                ))
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(
                    if (enabled) activeColor else inactiveColor,
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
