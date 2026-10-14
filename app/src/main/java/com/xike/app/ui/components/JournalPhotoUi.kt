package com.xike.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeShapes
import java.io.InputStream

@Composable
internal fun AddPhotoTile(modifier: Modifier, label: String, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.AddPhotoAlternate,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun JournalPhotoMosaic(
    fileNames: List<String>,
    openImage: (String) -> InputStream?,
    onImageClick: (Int) -> Unit,
) {
    when (fileNames.size) {
        1 ->
            SavedJournalImage(
                fileName = fileNames.first(),
                openStream = { openImage(fileNames.first()) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1.75f),
                order = 1,
                onClick = { onImageClick(0) },
            )
        2 ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                fileNames.forEachIndexed { index, fileName ->
                    SavedJournalImage(
                        fileName = fileName,
                        openStream = { openImage(fileName) },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        order = index + 1,
                        onClick = { onImageClick(index) },
                    )
                }
            }
        else ->
            Row(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val first = fileNames[0]
                SavedJournalImage(
                    fileName = first,
                    openStream = { openImage(first) },
                    modifier = Modifier.weight(1.35f).fillMaxHeight(),
                    order = 1,
                    onClick = { onImageClick(0) },
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    fileNames.take(3).drop(1).forEachIndexed { index, fileName ->
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            SavedJournalImage(
                                fileName = fileName,
                                openStream = { openImage(fileName) },
                                modifier = Modifier.fillMaxSize(),
                                order = index + 2,
                                onClick = { onImageClick(index + 1) },
                            )
                            if (index == 1 && fileNames.size > 3) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.48f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "+${fileNames.size - 3}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
internal fun SavedJournalImage(
    fileName: String,
    openStream: () -> InputStream?,
    modifier: Modifier,
    order: Int,
    onClick: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
    maxDimension: Int = 960,
) {
    val bitmap = rememberPreviewBitmap(fileName, maxDimension, openStream)
    val imageAlpha =
        remember(fileName, maxDimension) {
            Animatable(if (bitmap != null) 1f else 0f)
        }
    LaunchedEffect(bitmap, imageAlpha) {
        if (bitmap != null && imageAlpha.value < 1f) {
            imageAlpha.animateTo(1f, animationSpec = tween(durationMillis = 180))
        }
    }
    val interactiveModifier =
        if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Box(
        modifier = interactiveModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = tr("日记第 $order 张照片", "Journal photo $order"),
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha.value },
                contentScale = contentScale,
            )
        } else {
            Icon(
                Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PhotoGalleryDialog(
    fileNames: List<String>,
    initialPage: Int,
    openImage: (String) -> InputStream?,
    onDismiss: () -> Unit,
) {
    if (fileNames.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = remember(context) { GalleryImageLoader.get(context) }
    val currentOpenImage by rememberUpdatedState(openImage)
    val thumbnailCache = LocalPreviewBitmapCache.current
    val pagerState =
        rememberPagerState(
            initialPage = initialPage.coerceIn(0, fileNames.lastIndex),
            pageCount = { fileNames.size },
        )

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().testTag("photo-gallery-pager"),
            color = Color.Black,
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val fileName = fileNames[page]
                    var retryAttempt by remember(fileName) { mutableIntStateOf(0) }
                    val request =
                        remember(context, fileName, retryAttempt) {
                            GalleryImageLoader.request(
                                context,
                                GalleryPhoto(fileName) { currentOpenImage(fileName) },
                            )
                        }
                    val placeholder =
                        remember(thumbnailCache, fileName) {
                            thumbnailCache?.get(fileName, 960)?.let { BitmapPainter(it) }
                        }
                    var showLoadingIcon by remember(request) { mutableStateOf(placeholder == null) }
                    var loadFailed by remember(request) { mutableStateOf(false) }
                    var isLoading by remember(request) { mutableStateOf(true) }
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = request,
                            imageLoader = imageLoader,
                            placeholder = placeholder,
                            onLoading = {
                                showLoadingIcon = placeholder == null
                                isLoading = true
                                loadFailed = false
                            },
                            onSuccess = {
                                showLoadingIcon = false
                                isLoading = false
                                loadFailed = false
                            },
                            onError = {
                                showLoadingIcon = false
                                isLoading = false
                                loadFailed = true
                            },
                            contentDescription =
                                tr("日记第 ${page + 1} 张照片", "Journal photo ${page + 1}"),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        if (showLoadingIcon || (isLoading && retryAttempt > 0)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    Modifier.testTag("photo-gallery-placeholder-$page")
                                )
                                Text(
                                    tr("正在加载照片…", "Loading photo…"),
                                    modifier = Modifier.padding(top = 12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (loadFailed) {
                            Surface(
                                modifier = Modifier.padding(24.dp),
                                shape = XikeShapes.inner,
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Column(
                                    modifier =
                                        Modifier.verticalScroll(rememberScrollState())
                                            .padding(XikeInnerCardPadding),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        tr(
                                            "照片暂时无法加载，请重试。",
                                            "Unable to load the photo. Please retry.",
                                        ),
                                        modifier = Modifier.testTag("photo-gallery-error-$page"),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    TextButton(
                                        enabled = !isLoading,
                                        onClick = { retryAttempt++ },
                                        shape = XikeShapes.button,
                                    ) {
                                        Text(tr("重试", "Retry"))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.56f)) {
                        Text(
                            "${pagerState.currentPage + 1} / ${fileNames.size}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.56f)) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = localizedText("关闭图片查看"),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberPreviewBitmap(
    key: String,
    maxDimension: Int = 720,
    openStream: () -> InputStream?,
): ImageBitmap? {
    val providedCache = LocalPreviewBitmapCache.current
    val cache = providedCache ?: remember { PreviewBitmapCache() }
    var bitmap by
        remember(cache, key, maxDimension) {
            mutableStateOf(cache.get(key, maxDimension))
        }
    LaunchedEffect(cache, key, maxDimension) {
        if (bitmap == null) {
            bitmap = cache.load(key, maxDimension, openStream)
        }
    }
    return bitmap
}
