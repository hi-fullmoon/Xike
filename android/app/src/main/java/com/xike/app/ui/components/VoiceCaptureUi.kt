package com.xike.app.ui.components

import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Window
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xike.app.VoiceWaveformTimeline
import com.xike.app.XikeAudioRecorder
import com.xike.app.data.MAX_AUDIO_DURATION_MILLIS
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.components.DestructiveConfirmationDialog
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeDialogCompactGap
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.ui.theme.XikeInlineActionGap
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeSheetBottomPadding
import com.xike.app.ui.theme.XikeSheetHorizontalPadding
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VoiceCaptureSheet(
    enabled: Boolean,
    onRecorded: (File, Long) -> Unit,
    onClosed: () -> Unit,
    recordingDirectory: File? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recorder =
        remember(recordingDirectory) {
            XikeAudioRecorder(context.applicationContext, recordingDirectory ?: context.cacheDir)
        }
    var isClosed by remember { mutableStateOf(false) }
    fun closeCapture() {
        if (isClosed) return
        isClosed = true
        recorder.cancel()
        onClosed()
    }
    val sheetState =
        rememberModalBottomSheetState(
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
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = XikeSheetHorizontalPadding)
                    .padding(bottom = XikeSheetBottomPadding)
        ) {
            Text(localizedText("录下这一刻"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(XikeDialogCompactGap))
            Text(
                localizedText("录音结束后将加密保存"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(XikeDialogContentGap))
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
        val callback =
            object : Window.Callback by original {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (!hasFocus) dismiss()
                    original.onWindowFocusChanged(hasFocus)
                }

                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (
                        event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
                    ) {
                        dismiss()
                        return true
                    }
                    return original.dispatchKeyEvent(event)
                }
            }
        window.callback = callback
        val backCallback =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OnBackInvokedCallback { dismiss() }
                    .also {
                        window.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                            it,
                        )
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
private fun VoiceCaptureLayout(
    content: @Composable (androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer(cacheSize = 16)
        val typography = MaterialTheme.typography
        fun textHeight(text: String, style: TextStyle, width: Int): Int =
            textMeasurer
                .measure(text, style, constraints = Constraints(maxWidth = width.coerceAtLeast(1)))
                .size
                .height
        val panelWidth = with(density) { (maxWidth - 32.dp).roundToPx() }
        val timerWidth =
            textMeasurer
                .measure("5:00", typography.titleLarge.copy(fontFamily = FontFamily.Monospace))
                .size
                .width
        val statusWidth = panelWidth - timerWidth - with(density) { 28.dp.roundToPx() }
        val recordingHeight =
            maxOf(
                textHeight(localizedText("正在录音"), typography.labelMedium, statusWidth),
                textHeight(
                    "5:00",
                    typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    panelWidth,
                ),
            ) +
                with(density) { 72.dp.roundToPx() } +
                maxOf(
                    textHeight(
                        tr("松手完成，上滑取消", "Release to finish · Slide up to cancel"),
                        typography.labelMedium,
                        panelWidth,
                    ),
                    textHeight(tr("松开取消", "Release to cancel"), typography.labelMedium, panelWidth),
                )
        val idleHeight =
            with(density) { 68.dp.roundToPx() } +
                textHeight(
                    tr("按住下方按钮开始录音", "Hold the button below to record"),
                    typography.bodyMedium,
                    panelWidth,
                ) +
                textHeight(tr("最长 5 分钟", "Up to 5 minutes"), typography.labelSmall, panelWidth)
        val panelHeight =
            with(density) {
                maxOf(164.dp.roundToPx(), maxOf(idleHeight, recordingHeight) + 24.dp.roundToPx())
                    .toDp()
            }
        val buttonTextWidth = with(density) { (maxWidth - 52.dp - XikeInlineActionGap).roundToPx() }
        val buttonHeight =
            with(density) {
                maxOf(
                        56.dp.roundToPx(),
                        listOf(
                                tr("按住说话", "Hold to record"),
                                tr("松手完成", "Release to finish"),
                                tr("松开取消", "Release to cancel"),
                            )
                            .maxOf { textHeight(it, typography.labelLarge, buttonTextWidth) } +
                            24.dp.roundToPx(),
                    )
                    .toDp()
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
        return (now - startedAt).coerceIn(0L, MAX_AUDIO_DURATION_MILLIS)
    }

    fun finishRecording() {
        if (
            isClosed() || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
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
        if (
            !enabled ||
                isRecording ||
                isStopping ||
                isClosed() ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        errorMessage = null
        runCatching {
            recorder.start { limitReached = SystemClock.elapsedRealtime() }
        }
            .onSuccess {
                startedAt = SystemClock.elapsedRealtime()
                elapsedMillis = 0L
                waveform = VoiceWaveformTimeline()
                waveformOffset = 0f
                limitReached = 0L
                isRecording = true
            }
            .onFailure { error ->
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
                modifier =
                    Modifier.fillMaxWidth().onGloballyPositioned {
                        cancelBounds = it.boundsInRoot()
                    },
                shape = XikeShapes.inner,
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ),
                color =
                    when {
                        cancelRequested -> MaterialTheme.colorScheme.errorContainer
                        isRecording ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
                    },
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth().height(panelHeight).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRecording) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
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
                                    style =
                                        MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                )
                            }
                            VoiceWaveform(waveform.values) { waveformOffset }
                            Text(
                                if (cancelRequested) tr("松开取消", "Release to cancel")
                                else tr("松手完成，上滑取消", "Release to finish · Slide up to cancel"),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (cancelRequested) MaterialTheme.colorScheme.onErrorContainer
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
                                color =
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
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
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val beginHeldRecording by rememberUpdatedState { beginRecording() }
            val finishHeldRecording by rememberUpdatedState { finishRecording() }
            val cancelHeldRecording by rememberUpdatedState { cancelRecording() }
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(buttonHeight)
                        .onGloballyPositioned { buttonOrigin = it.positionInRoot() }
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription =
                                tr(
                                    "长按录音，上滑取消，松手完成",
                                    "Hold to record, slide up to cancel, release to finish",
                                )
                            stateDescription =
                                if (isRecording) localizedText("正在录音") else localizedText("录音未开始")
                            if (!enabled || isStopping) disabled()
                            onClick(
                                label =
                                    if (isRecording) localizedText("结束录音")
                                    else localizedText("开始录音")
                            ) {
                                if (!enabled || isStopping) false
                                else {
                                    if (isRecording) finishRecording() else beginRecording()
                                    true
                                }
                            }
                            onLongClick(label = localizedText("开始录音")) {
                                if (!enabled || isRecording || isStopping) false
                                else {
                                    beginRecording()
                                    true
                                }
                            }
                            customActions =
                                if (isRecording && !isStopping)
                                    listOf(
                                        CustomAccessibilityAction(localizedText("取消")) {
                                            cancelRecording()
                                            onClosed()
                                            true
                                        }
                                    )
                                else emptyList()
                        }
                        .pointerInput(enabled) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (!enabled) return@awaitEachGesture
                                val held =
                                    awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
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
                                        cancelRequested =
                                            position.y < cancelBounds.bottom - 24.dp.toPx()
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
                color =
                    if (cancelRequested) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                contentColor =
                    if (cancelRequested) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimary,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isStopping) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(XikeInlineActionGap))
                    }
                    Text(
                        when {
                            cancelRequested -> tr("松开取消", "Release to cancel")
                            isRecording -> tr("松手完成", "Release to finish")
                            else -> tr("按住说话", "Hold to record")
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelLarge,
                    )
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
                        color =
                            if (errorMessage != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { confirmDiscard = true },
                        modifier = Modifier.weight(1f),
                        shape = XikeShapes.button,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(localizedText("放弃录音"))
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        shape = XikeShapes.button,
                    ) {
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
                Offset(x, center - halfHeight),
                Offset(x, center + halfHeight),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
