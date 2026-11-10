package com.xike.app.feature.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xike.app.LocalSystemActivityCallbacks
import com.xike.app.ai.AiSettings
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.tr
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.xikeSwitchColors
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AiSettingsDialog(onDismiss: () -> Unit, onSaved: (AiSettings) -> Unit) {
    val operations = LocalAiReviewOperations.current ?: return
    val callbacks = LocalSystemActivityCallbacks.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AiSettings()) }
    var loaded by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dialogBounds by remember { mutableStateOf(Rect.Zero) }

    val dismiss = {
        job?.cancel()
        onDismiss()
    }
    LaunchedEffect(callbacks.isLocked) {
        if (callbacks.isLocked) dismiss()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                job?.cancel()
                onDismiss()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            job?.cancel()
        }
    }

    Dialog(
        onDismissRequest = dismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                securePolicy = SecureFlagPolicy.SecureOn,
                decorFitsSystemWindows = false,
            ),
    ) {
        // Start after the dialog content is mounted, including when local reads finish immediately.
        LaunchedEffect(operations) {
            try {
                settings = operations.load()
                loaded = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message =
                    tr("无法读取 AI 配置，请关闭后重试。", "Unable to read AI settings. Close and try again.")
            }
        }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(onDismiss) {
                    detectTapGestures { position ->
                        if (!dialogBounds.contains(position)) dismiss()
                    }
                }
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier.widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .onGloballyPositioned { dialogBounds = it.boundsInRoot() }
                        .testTag("ai-settings-dialog"),
                shape = XikeShapes.dialog,
                color = AlertDialogDefaults.containerColor,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        tr("AI 接口配置", "AI service settings"),
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap),
                        ) {
                            Text(
                                tr(
                                    "使用兼容 Chat Completions 的接口。基础地址会自动补全 /chat/completions；也可填写完整接口地址。",
                                    "Use a Chat Completions compatible API. A base address is extended with /chat/completions; a full endpoint is also accepted.",
                                )
                            )
                            OutlinedTextField(
                                value = settings.address,
                                onValueChange = {
                                    settings =
                                        settings.copy(
                                            address = it,
                                            consent = false,
                                            enabled = false,
                                        )
                                    message = null
                                },
                                label = { Text(tr("HTTPS 服务地址", "HTTPS service address")) },
                                singleLine = true,
                                enabled = loaded && !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth().testTag("ai-address"),
                            )
                            OutlinedTextField(
                                value = settings.apiKey,
                                onValueChange = {
                                    settings = settings.copy(apiKey = it)
                                    message = null
                                },
                                label = { Text("API Key") },
                                singleLine = true,
                                enabled = loaded && !busy,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation =
                                    if (showKey) VisualTransformation.None
                                    else PasswordVisualTransformation(),
                                trailingIcon = {
                                    TextButton(onClick = { showKey = !showKey }) {
                                        Text(if (showKey) tr("隐藏", "Hide") else tr("显示", "Show"))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("ai-key"),
                            )
                            OutlinedTextField(
                                value = settings.model,
                                onValueChange = {
                                    settings = settings.copy(model = it)
                                    message = null
                                },
                                label = { Text(tr("模型名称", "Model name")) },
                                singleLine = true,
                                enabled = loaded && !busy,
                                modifier = Modifier.fillMaxWidth().testTag("ai-model"),
                            )
                            settings.normalized().endpoint()?.let {
                                Text(
                                    tr("接收地址：$it", "Destination: $it"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                tr(
                                    "Key 仅在本机加密保存，不进入日记备份。分析时，所选内容会发送给以上服务，数据处理方式以该服务的隐私政策为准。测试连接只发送固定测试文字，可能产生服务费用。",
                                    "The Key is encrypted on this device and excluded from journal backups. Selected content is sent to this service for analysis under its privacy policy. Connection tests send only fixed test text and may incur service fees.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            var firstLineCenterPx by remember { mutableIntStateOf(0) }
                            Row(
                                modifier =
                                    Modifier.testTag("ai-consent")
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .toggleable(
                                            value = settings.consent,
                                            enabled = loaded && !busy,
                                            role = Role.Checkbox,
                                            interactionSource =
                                                remember { MutableInteractionSource() },
                                            indication = null,
                                            onValueChange = {
                                                settings =
                                                    settings.copy(
                                                        consent = it,
                                                        enabled = settings.enabled && it,
                                                    )
                                            },
                                        ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = settings.consent,
                                    enabled = loaded && !busy,
                                    onCheckedChange = null,
                                    modifier =
                                        Modifier.testTag("ai-consent-checkbox")
                                            .size(20.dp)
                                            .alignBy { it.measuredHeight / 2 },
                                )
                                Text(
                                    tr(
                                        "我理解并同意向以上服务发送所选内容",
                                        "I understand and agree to send selected content to this service",
                                    ),
                                    modifier =
                                        Modifier.testTag("ai-consent-text").alignBy {
                                            firstLineCenterPx
                                        },
                                    onTextLayout = { layout ->
                                        firstLineCenterPx =
                                            ((layout.getLineTop(0) + layout.getLineBottom(0)) / 2)
                                                .roundToInt()
                                    },
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    tr("启用 AI 回顾", "Enable AI review"),
                                    Modifier.weight(1f).testTag("ai-enabled-label"),
                                )
                                Switch(
                                    checked = settings.enabled,
                                    onCheckedChange = { settings = settings.copy(enabled = it) },
                                    enabled =
                                        loaded &&
                                            !busy &&
                                            (settings.enabled ||
                                                (settings.consent &&
                                                    settings.normalized().configured)),
                                    colors = xikeSwitchColors(),
                                    modifier = Modifier.testTag("ai-enabled"),
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    enabled =
                                        loaded &&
                                            !busy &&
                                            settings.normalized().configured &&
                                            settings.consent,
                                    onClick = {
                                        if (!busy && callbacks.canHandleResult()) {
                                            busy = true
                                            job = scope.launch {
                                                try {
                                                    val result =
                                                        operations.test(settings.normalized())
                                                    message =
                                                        result.fold(
                                                            {
                                                                tr(
                                                                    "连接成功。",
                                                                    "Connection successful.",
                                                                )
                                                            },
                                                            { it.message },
                                                        )
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    Text(
                                        tr("测试连接", "Test connection"),
                                        Modifier.testTag("ai-test-label"),
                                    )
                                }
                                TextButton(
                                    modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    enabled = loaded && !busy,
                                    onClick = {
                                        settings =
                                            settings.copy(
                                                apiKey = "",
                                                enabled = false,
                                                consent = false,
                                            )
                                        message = null
                                    },
                                ) {
                                    Text(tr("清除 Key", "Clear Key"))
                                }
                            }
                            if (!loaded && message == null) Text(tr("正在读取配置…", "Loading settings…"))
                            if (busy) Text(tr("处理中…", "Working…"))
                            message?.let { Text(it, Modifier.testTag("ai-settings-message")) }
                        }
                    }
                    FlowRow(
                        modifier = Modifier.align(Alignment.End).padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(onClick = dismiss, shape = XikeShapes.button) {
                            Text(tr("取消", "Cancel"))
                        }
                        TextButton(
                            enabled = loaded && !busy && settings.normalized().isValid(),
                            shape = XikeShapes.button,
                            onClick = {
                                if (!busy && callbacks.canHandleResult()) {
                                    busy = true
                                    job = scope.launch {
                                        try {
                                            val value = settings.normalized()
                                            operations
                                                .save(value)
                                                .onSuccess { onSaved(value) }
                                                .onFailure { message = it.message }
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(tr("保存", "Save"))
                        }
                    }
                }
            }
        }
    }
}
