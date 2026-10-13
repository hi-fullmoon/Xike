package com.xike.app.ui.components

import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.playbackAudioAttributes
import com.xike.app.claimPlaybackFocus
import com.xike.app.journalPlayback

import com.xike.app.data.JournalAudio

import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeShapes


import android.media.MediaPlayer
import android.media.AudioAttributes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.FileInputStream

import java.io.InputStream
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    showDeleteAtTopEnd: Boolean = false,
    useActionMenu: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = XikeShapes.card,
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
    val hasTopEndDelete = showDeleteAtTopEnd && onDelete != null
    val hasTopEndReplace = showDeleteAtTopEnd && onReplace != null
    var showActions by remember(audio.fileName, hasTopEndDelete, hasTopEndReplace) { mutableStateOf(false) }

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
        shape = shape,
        color = containerColor,
        border = if (showBorder) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else null,
    ) {
        Box {
            Column(Modifier.padding(XikeCardPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end =
                        if (useActionMenu && (hasTopEndDelete || hasTopEndReplace)) 32.dp
                        else (if (hasTopEndDelete) 32.dp else 0.dp) + (if (hasTopEndReplace) 48.dp else 0.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
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
                if ((onReplace != null && !hasTopEndReplace) || (onDelete != null && !hasTopEndDelete)) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    val actions: @Composable () -> Unit = {
                        if (onReplace != null && !hasTopEndReplace) {
                            TextButton(shape = XikeShapes.button, onClick = {
                                releasePlayer()
                                onReplace()
                            }) {
                                Text(localizedText("重新录制"), maxLines = if (showDeleteLabel) Int.MAX_VALUE else 1)
                            }
                        }
                        if (onDelete != null && !hasTopEndDelete) {
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
            Row(Modifier.align(Alignment.TopEnd)) {
                if (useActionMenu && (hasTopEndDelete || hasTopEndReplace)) {
                    Box {
                        IconButton(onClick = { showActions = true }, enabled = !isPreparing, modifier = Modifier.size(48.dp)) {
                            Surface(Modifier.size(32.dp), shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.MoreVert, tr("录音的更多操作", "More recording actions"), Modifier.size(16.dp))
                                }
                            }
                        }
                        DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                            if (hasTopEndReplace) DropdownMenuItem(
                                text = { Text(localizedText("重新录制")) },
                                enabled = !isPreparing,
                                onClick = {
                                    showActions = false
                                    if (!isPreparing) {
                                        releasePlayer()
                                        onReplace?.invoke()
                                    }
                                },
                            )
                            if (hasTopEndDelete) DropdownMenuItem(
                                text = { Text(tr("移除录音", "Remove recording")) },
                                enabled = !isPreparing,
                                onClick = {
                                    showActions = false
                                    if (!isPreparing) {
                                        releasePlayer()
                                        onDelete?.invoke()
                                    }
                                },
                            )
                        }
                    }
                }
                if (!useActionMenu && hasTopEndReplace) {
                    IconButton(
                        onClick = {
                            releasePlayer()
                            onReplace?.invoke()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Refresh, contentDescription = localizedText("重新录制"), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (!useActionMenu && hasTopEndDelete) {
                    IconButton(
                        onClick = {
                            releasePlayer()
                            onDelete()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = localizedText("删除语音"),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
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


internal fun formatAudioDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
