package com.xike.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.coroutines.delay

/** Activity-scoped feedback: a new message replaces the previous one, without a stale queue. */
internal class XikeNotice private constructor(
    activity: Activity?,
    private val message: String,
    private val duration: Int,
) {
    private val activity = WeakReference(activity)

    fun show() {
        val target = activity.get() ?: return
        mainHandler.post {
            if (!target.isFinishing && !target.isDestroyed) {
                stateFor(target).message = NoticeMessage(message, duration)
            }
        }
    }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        private val mainHandler = Handler(Looper.getMainLooper())
        private val states = WeakHashMap<Activity, NoticeState>()

        fun makeText(context: Context, message: CharSequence, duration: Int): XikeNotice =
            XikeNotice(context.noticeActivity(), message.toString(), duration)

        internal fun stateFor(activity: Activity): NoticeState =
            states.getOrPut(activity) { NoticeState() }
    }
}

internal class NoticeState {
    var message by mutableStateOf<NoticeMessage?>(null)
}

// Reference identity ensures that repeated identical messages restart their display time.
internal class NoticeMessage(val text: String, val duration: Int, val onDismiss: (() -> Unit)? = null)

internal val LocalRetainedNotice = staticCompositionLocalOf<NoticeMessage?> { null }

private fun Context.noticeActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
internal fun XikeNoticeHost() {
    val context = LocalContext.current
    val activity = remember(context) { context.noticeActivity() } ?: return
    val state = remember(activity) { XikeNotice.stateFor(activity) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.message = null
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.message = null
        }
    }
    val lifecycleState by lifecycle.currentStateAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return
    val retainedNotice = LocalRetainedNotice.current
    val message = state.message ?: retainedNotice ?: return
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message, accessibility) {
        val duration = if (message.duration == XikeNotice.LENGTH_LONG) 6000L else 3500L
        delay(accessibility?.calculateRecommendedTimeoutMillis(
            duration, containsIcons = true, containsText = true, containsControls = true,
        ) ?: duration)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        if (state.message === message) state.message = null
        else if (state.message == null && retainedNotice === message) message.onDismiss?.invoke()
    }
    Popup(
        alignment = Alignment.TopCenter,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = XikeContentMaxWidth).fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = XikeShapes.card,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(message.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = {
                    if (state.message === message) state.message = null
                    else if (state.message == null) message.onDismiss?.invoke()
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = localizedText("关闭"), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
