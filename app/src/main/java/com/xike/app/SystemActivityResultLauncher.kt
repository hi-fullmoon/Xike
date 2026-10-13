package com.xike.app

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class PendingSystemActivityResult<T>(val value: T)

/** Stores only standard contract results, never backup passwords or journal contents. */
class SystemActivityResultState<T>(private val savedState: SavedStateHandle) : ViewModel() {
    private var context: Context? = null
    private var store: Deferred<PendingMediaResultStore>? = null
    private var storeKey: String? = null
    private val deliveryMutex = Mutex()

    internal fun bind(context: Context, key: String, durable: Boolean) {
        this.context = context.applicationContext
        if (!durable || store != null) return
        storeKey = key
        val applicationContext = context.applicationContext
        val loading = viewModelScope.async(Dispatchers.IO) { PendingMediaResultStore(applicationContext) }
        store = loading
        viewModelScope.launch {
            deliveryMutex.withLock {
                runCatching { withContext(Dispatchers.IO) { loading.await().load(key) } }
                    .onSuccess { restored ->
                        if (restored != null) {
                            @Suppress("UNCHECKED_CAST")
                            receive(restored.value as T, restored.ownedReadUris)
                        }
                    }.onFailure {
                        XikeNotice.makeText(applicationContext, tr("无法读取待处理的媒体选择，请稍后重试。", "Unable to read the pending media selection. Please try again later."), XikeNotice.LENGTH_LONG).show()
                    }
            }
        }
    }

    internal fun receiveFromSystem(value: T, ownedReadUris: List<Uri>) {
        if (store == null) {
            receive(value, ownedReadUris)
            return
        }
        viewModelScope.launch {
            deliveryMutex.withLock {
                runCatching {
                    withContext(Dispatchers.IO) { store!!.await().save(storeKey!!, value, ownedReadUris) }
                }.onSuccess { receive(value, ownedReadUris) }
                    .onFailure {
                        // Keep the selection and grants in memory on a transient write failure.
                        receive(value, ownedReadUris)
                        context?.let { applicationContext ->
                            XikeNotice.makeText(applicationContext, tr("媒体选择暂时无法持久保存，请保持应用开启。", "The media selection could not be preserved yet. Keep the app open."), XikeNotice.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    internal suspend fun acknowledgeDurableResult() {
        deliveryMutex.withLock {
            store?.let { loading -> withContext(Dispatchers.IO) { loading.await().remove(storeKey!!) } }
        }
    }
    @Suppress("UNCHECKED_CAST")
    internal var pending by mutableStateOf(
        if (savedState.get<Boolean>("hasResult") == true) {
            PendingSystemActivityResult(savedState.get<Any?>("result") as T)
        } else null,
    )
        private set

    internal fun receive(value: T, ownedReadUris: List<Uri> = emptyList()) {
        // Lists returned by photo contracts are normalized to Bundle's supported list type.
        savedState["result"] = if (value is List<*>) ArrayList(value) else value
        savedState["hasResult"] = true
        savedState["ownedReadUris"] = ArrayList(ownedReadUris.map(Uri::toString))
        pending = PendingSystemActivityResult(value)
    }

    internal fun consume(): PendingSystemActivityResult<T>? {
        val result = pending
        savedState.remove<Any?>("result")
        savedState.remove<Boolean>("hasResult")
        pending = null
        return result
    }

    internal fun acceptWhenReady(ready: Boolean, accept: (T) -> Boolean): Boolean {
        val result = pending ?: return false
        if (!ready || !accept(result.value)) return false
        consume()
        return true
    }

    internal fun takeReadAccess(context: Context): SystemActivityReadAccess {
        val uris = savedState.remove<ArrayList<String>>("ownedReadUris").orEmpty().map(Uri::parse)
        return SystemActivityReadAccess(context, uris)
    }

    internal fun pendingReadAccess(context: Context): SystemActivityReadAccess =
        SystemActivityReadAccess(context, savedState.get<ArrayList<String>>("ownedReadUris").orEmpty().map(Uri::parse))

    override fun onCleared() {
        // Durable pending results retain their grants until restored and handed to a consumer.
        if (store == null) context?.let { takeReadAccess(it).release() }
        super.onCleared()
    }
}

/** Retains the result across recreation and delivers it to the current callback after unlock. */
@Composable
internal fun <I, O> rememberSystemActivityResultLauncher(
    contract: ActivityResultContract<I, O>,
    resultKey: String,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> = rememberActivityResultWithAccess(contract, resultKey, false, true) { result, _ -> onResult(result); true }

@Composable
internal fun <I, O> rememberMediaActivityResultLauncher(
    contract: ActivityResultContract<I, O>,
    resultKey: String,
    resultsReady: Boolean = true,
    onResult: (O, SystemActivityReadAccess) -> Boolean,
): ActivityResultLauncher<I> = rememberActivityResultWithAccess(contract, resultKey, true, resultsReady, onResult)

@Composable
private fun <I, O> rememberActivityResultWithAccess(
    contract: ActivityResultContract<I, O>,
    resultKey: String,
    retainReadAccess: Boolean,
    resultsReady: Boolean,
    onResult: (O, SystemActivityReadAccess) -> Boolean,
): ActivityResultLauncher<I> {
    val context = LocalContext.current
    val callbacks by rememberUpdatedState(LocalSystemActivityCallbacks.current)
    val currentOnResult by rememberUpdatedState(onResult)
    val key = "system-activity-result/$resultKey"
    val resultState: SystemActivityResultState<O> = viewModel(key = key)
    resultState.bind(context, key, retainReadAccess)
    val launcher = rememberLauncherForActivityResult(contract) { result ->
        callbacks.onResult(key)
        val ownedReadUris = if (retainReadAccess) retainSystemActivityReadAccess(context, result) else emptyList()
        resultState.receiveFromSystem(result, ownedReadUris)
    }
    LaunchedEffect(callbacks.isLocked, callbacks.resultsReady, resultsReady, resultState.pending) {
        val result = resultState.pending
        if (resultsReady && !callbacks.isLocked && callbacks.canHandleResult() && result != null) {
            val access = resultState.pendingReadAccess(context)
            var accepted = false
            try {
                accepted = resultState.acceptWhenReady(resultsReady && callbacks.canHandleResult()) {
                    currentOnResult(it, access)
                }
                if (!accepted) return@LaunchedEffect
                resultState.takeReadAccess(context)
                withContext(NonCancellable) {
                    runCatching { resultState.acknowledgeDurableResult() }.onFailure {
                        XikeNotice.makeText(context, tr("媒体选择确认暂时无法保存，请稍后重试。", "Unable to save the media selection acknowledgement. Please try again later."), XikeNotice.LENGTH_LONG).show()
                    }
                }
            } finally { if (accepted) access.closeUnlessDeferred() }
        }
    }
    return remember(launcher, context) {
        object : ActivityResultLauncher<I>() {
            override fun launch(input: I, options: ActivityOptionsCompat?) {
                var started = false
                try {
                    check(callbacks.canHandleResult()) { localizedText("解锁息刻") }
                    val intent = launcher.contract.createIntent(context, input)
                    val info = intent.resolveActivityInfo(context.packageManager, 0)
                    // A camera may run in its own task. Its hidden caller task does not imply
                    // that the user left the capture flow; Home/recents/screen events still apply.
                    val sameTask = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0 &&
                        (info?.launchMode == ActivityInfo.LAUNCH_MULTIPLE ||
                            info?.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP)
                    started = true
                    callbacks.onLaunch(key, sameTask)
                    launcher.launch(input, options)
                } catch (error: Exception) {
                    if (started) callbacks.onLaunchFailed(key)
                    throw error
                }
            }

            override val contract: ActivityResultContract<I, *> get() = launcher.contract
            override fun unregister() = launcher.unregister()
        }
    }
}
