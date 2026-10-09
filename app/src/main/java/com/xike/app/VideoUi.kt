package com.xike.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.net.Uri
import android.provider.MediaStore
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

internal data class VideoServices(
    val import: suspend (Uri, (Float) -> Unit) -> Result<JournalVideo> = { _, _ -> Result.failure(IllegalStateException("Video service unavailable")) },
    val release: (JournalVideo) -> Unit = {},
    val source: (JournalVideo) -> MediaDataSource = { error("Video service unavailable") },
    val cover: (String) -> InputStream? = { null },
    val addDraft: (Uri) -> Unit = {},
    val removeDraft: () -> Unit = {},
    val draftSaving: Boolean = false,
    val draftProgress: Float? = null,
    val editDraft: (JournalEntry) -> VideoEditDraft? = { null },
    val saveEdit: suspend (JournalEntry, JournalVideo?) -> Result<Unit> = { _, _ -> Result.failure(IllegalStateException("Video service unavailable")) },
    val clearEdit: suspend (String) -> Result<Unit> = { Result.failure(IllegalStateException("Video service unavailable")) },
)
internal val LocalVideoServices = compositionLocalOf { VideoServices() }

@Composable
internal fun VideoAddButton(
    enabled: Boolean,
    onPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable (() -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val system = LocalSystemActivityCallbacks.current
    val scope = rememberCoroutineScope()
    var choosing by rememberSaveable { mutableStateOf(false) }
    val cameraAvailable = if (choosing) remember(context) {
        canCaptureMedia(context, MediaStore.ACTION_VIDEO_CAPTURE)
    } else false
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    fun failure(error: Throwable) { Toast.makeText(context, error.message ?: tr("无法添加视频。", "Unable to add video."), Toast.LENGTH_LONG).show() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        system.onResult(); uri?.let(onPicked)
    }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        system.onResult()
        val uri = captureUri?.let(Uri::parse)
        captureUri = null
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { finishVideoCapture(context, uri, result.resultCode == Activity.RESULT_OK) } }
                .onSuccess { if (it) onPicked(uri) }.onFailure(::failure)
        }
    }
    val shoot: () -> Unit = {
        scope.launch {
            runCatching {
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                    .putExtra(MediaStore.EXTRA_DURATION_LIMIT, 300)
                    .putExtra(MediaStore.EXTRA_SIZE_LIMIT, MAX_VIDEO_BYTES)
                    .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
                check(intent.resolveActivity(context.packageManager) != null) { tr("没有可用的摄像应用。", "No camera app is available.") }
                val uri = withContext(Dispatchers.IO) { createVideoCaptureUri(context) }
                captureUri = uri.toString()
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                intent.clipData = android.content.ClipData.newRawUri("video", uri)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                system.onLaunch()
                try { capture.launch(intent) } catch (error: Throwable) {
                    system.onResult(); captureUri = null
                    withContext(Dispatchers.IO) { finishVideoCapture(context, uri, false) }
                    throw error
                }
            }.onFailure(::failure)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) shoot() else failure(IllegalStateException(tr("需要存储权限才能保存拍摄的视频。", "Storage permission is required to save captured videos.")))
    }
    val choose = { focus.clearFocus(); keyboard?.hide(); choosing = true }
    if (content != null) content(choose) else {
        MomentQuickAction(icon = Icons.Outlined.Videocam, label = tr("视频", "Video"), enabled = enabled, modifier = modifier, onClick = choose)
    }
    if (choosing) MediaSourceDialog(
        title = tr("添加视频", "Add video"),
        description = tr("每条日记 1 段 · 最长 5 分钟 · 最大 500 MB", "1 video per entry · Up to 5 minutes · Up to 500 MB"),
        captureIcon = Icons.Outlined.Videocam,
        captureTitle = tr("拍摄视频", "Record video"),
        cameraAvailable = cameraAvailable,
        captureSupporting = if (cameraAvailable) null
            else localizedText("当前设备没有可用的相机"),
        onCapture = {
            choosing = false
            if (hasGalleryWriteAccess(context)) shoot() else permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        },
        onChooseMedia = {
            choosing = false
            runCatching { system.onLaunch(); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                .onFailure { system.onResult(); failure(it) }
        },
        onDismiss = { choosing = false },
    )
}

@Composable
internal fun VideoCard(video: JournalVideo, onRemove: (() -> Unit)? = null, previewOnly: Boolean = false, onOpen: (() -> Unit)? = null) {
    key(video.fileName) {
        VideoCardContent(video, onRemove, previewOnly, onOpen)
    }
}

@Composable
private fun VideoCardContent(video: JournalVideo, onRemove: (() -> Unit)?, previewOnly: Boolean, onOpen: (() -> Unit)?) {
    val services = LocalVideoServices.current
    var playing by remember { mutableStateOf(false) }
    val cover by produceState<android.graphics.Bitmap?>(null, video.coverFileName) {
        value = withContext(Dispatchers.IO) { runCatching { services.cover(video.coverFileName)?.use(BitmapFactory::decodeStream) }.getOrNull() }
    }
    Surface(shape = XikeShapes.inner, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds().clickable { if (onOpen != null) onOpen() else playing = true }, contentAlignment = Alignment.Center) {
                cover?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Surface(Modifier.size(48.dp), shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), contentColor = Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PlayArrow, tr("播放视频", "Play video"), Modifier.size(28.dp))
                    }
                }
                Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = XikeShapes.inner,
                    color = Color.Black.copy(alpha = 0.65f), contentColor = Color.White) {
                    Text(
                        videoTime(video.durationMillis),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
            if (onRemove != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = XikeCardPadding)) {
                    TextButton(onClick = onRemove, shape = XikeShapes.button) {
                        Text(tr("移除视频", "Remove video"))
                    }
                }
            }
        }
    }
    if (playing && !previewOnly) VideoPlayerDialog(video) { playing = false }
}

@Composable
internal fun VideoImportStatus(progress: Float?) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            tr("正在加密导入视频，请稍候", "Encrypting and importing video, please wait"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

private fun videoTime(millis: Long): String = "%d:%02d".format(millis / 60_000, millis / 1000 % 60)

@Composable
private fun VideoPlayerDialog(video: JournalVideo, onDismiss: () -> Unit) {
    val services = LocalVideoServices.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(video.fileName) { MediaPlayer() }
    val disposed = remember(player) { java.util.concurrent.atomic.AtomicBoolean(false) }
    var source by remember { mutableStateOf<MediaDataSource?>(null) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    var preparing by remember { mutableStateOf(false) }
    var surfaceAvailable by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    val playbackOwner = remember(player) { Any() }
    val accessibility = LocalAccessibilityManager.current
    val nativeAccessibility = LocalContext.current.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val playbackAttributes = remember(player) { playbackAudioAttributes(AudioAttributes.CONTENT_TYPE_MOVIE) }
    var focusUnavailable by remember { mutableStateOf(false) }

    fun pausePlayback() {
        if (ready && playing) runCatching {
            player.pause()
            if (!dragging) position = player.currentPosition.toFloat()
        }
        playing = false
        controlsVisible = true
        journalPlayback.release(playbackOwner)
    }

    fun claimPlayback(): Boolean {
        val granted = claimPlaybackFocus(context, playbackOwner, playbackAttributes, ::pausePlayback)
        focusUnavailable = !granted
        return granted
    }

    fun startPlayback() {
        if (!surfaceAvailable || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        runCatching {
            if (!claimPlayback()) return@runCatching
            if (position >= video.durationMillis) {
                player.seekTo(0)
                position = 0f
            }
            player.start()
            playing = true
            controlsVisible = true
            interaction++
        }.onFailure {
            pausePlayback()
            error = tr("视频暂时无法播放，请关闭后重试。", "Unable to play the video. Close and retry.")
            ready = false
        }
    }

    val currentPause by rememberUpdatedState(::pausePlayback)
    DisposableEffect(player, lifecycle) {
        focusUnavailable = !claimPlaybackFocus(context, playbackOwner, playbackAttributes) { currentPause() }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) currentPause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            disposed.set(true)
            lifecycle.removeObserver(observer)
            player.release()
            journalPlayback.release(playbackOwner)
            source?.close()
        }
    }
    LaunchedEffect(ready, playing) {
        while (ready && playing) { if (!dragging) position = player.currentPosition.toFloat(); delay(250) }
    }
    LaunchedEffect(playing, dragging, controlsVisible, interaction, accessibility) {
        if (playing && !dragging && controlsVisible && !nativeAccessibility.isTouchExplorationEnabled) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(3_000, containsIcons = true, containsText = true, containsControls = true) ?: 3_000)
            controlsVisible = false
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                    val videoWidth = minOf(maxWidth, maxHeight * aspect)
                    AndroidView(modifier = Modifier.size(videoWidth, videoWidth / aspect), factory = { context ->
                        SurfaceView(context).apply { holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                if (disposed.get()) return
                                surfaceAvailable = true
                                player.setDisplay(holder)
                                if (!ready && !preparing && error == null) {
                                    preparing = true
                                    scope.launch {
                                        var opened: MediaDataSource? = null
                                        try {
                                            runCatching {
                                                withContext(Dispatchers.IO) { opened = services.source(video) }
                                                if (disposed.get()) return@runCatching
                                                source = opened
                                                player.setAudioAttributes(playbackAttributes)
                                                player.setDataSource(requireNotNull(opened))
                                                player.setOnPreparedListener {
                                                    if (!disposed.get()) {
                                                        preparing = false
                                                        ready = true
                                                        aspect = if (it.videoHeight > 0) it.videoWidth.toFloat() / it.videoHeight else aspect
                                                        if (journalPlayback.owns(playbackOwner)) startPlayback()
                                                    }
                                                }
                                                player.setOnCompletionListener {
                                                    playing = false
                                                    position = video.durationMillis.toFloat()
                                                    controlsVisible = true
                                                    journalPlayback.release(playbackOwner)
                                                }
                                                player.setOnErrorListener { _, _, _ ->
                                                    journalPlayback.release(playbackOwner)
                                                    error = tr("视频无法播放，文件或编码可能不受支持。", "Unable to play this video. Its file or encoding may be unsupported.")
                                                    ready = false
                                                    preparing = false
                                                    playing = false
                                                    controlsVisible = true
                                                    true
                                                }
                                                player.prepareAsync()
                                            }.onFailure {
                                                journalPlayback.release(playbackOwner)
                                                preparing = false
                                                if (!disposed.get()) error = tr("视频暂时无法读取，原文件已保留。", "Unable to read the video. The original file is preserved.")
                                            }
                                        } finally {
                                            if (source !== opened) opened?.close()
                                        }
                                    }
                                }
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surfaceAvailable = false
                                if (!disposed.get()) {
                                    pausePlayback()
                                    runCatching { player.setDisplay(null) }
                                }
                            }
                        }) }
                    }, update = { it.keepScreenOn = playing })
                }
                Box(Modifier.matchParentSize().clickable {
                    controlsVisible = if (playing) !controlsVisible else true
                    interaction++
                }.semantics { contentDescription = tr("视频播放控制", "Video playback controls") })
                if (!ready && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                AnimatedVisibility(visible = controlsVisible || !playing, modifier = Modifier.align(Alignment.TopEnd)) {
                    Surface(color = Color.Black.copy(alpha = 0.65f), shape = XikeShapes.button, modifier = Modifier.padding(16.dp)) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, localizedText("关闭"), tint = Color.White) }
                    }
                }
                AnimatedVisibility(visible = controlsVisible || !playing, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Surface(color = Color.Black.copy(alpha = 0.65f), contentColor = Color.White) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                            if (focusUnavailable) Text(tr("暂时无法播放，请稍后再试。", "Unable to play right now. Please try again shortly."), Modifier.padding(bottom = 8.dp))
                            PlaybackProgressSlider(value = position.coerceIn(0f, video.durationMillis.toFloat()), onValueChange = {
                                dragging = true; position = it; interaction++
                            }, onValueChangeFinished = {
                                runCatching { player.seekTo(position.toInt()) }.onFailure {
                                    pausePlayback()
                                    error = tr("视频暂时无法播放，请关闭后重试。", "Unable to play the video. Close and retry.")
                                    ready = false
                                }
                                dragging = false; interaction++
                            }, valueRange = 0f..video.durationMillis.coerceAtLeast(1).toFloat(), enabled = ready && error == null,
                                activeColor = Color.White, inactiveColor = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = tr("视频播放进度", "Video playback progress") })
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(enabled = ready && error == null, onClick = {
                                    if (playing) pausePlayback() else startPlayback()
                                    interaction++
                                }) {
                                    Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                        if (playing) tr("暂停视频", "Pause video") else tr("播放视频", "Play video"), tint = Color.White)
                                }
                                Text("${videoTime(position.toLong())} / ${videoTime(video.durationMillis)}", Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
