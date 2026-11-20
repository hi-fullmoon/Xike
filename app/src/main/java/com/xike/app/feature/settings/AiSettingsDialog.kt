package com.xike.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xike.app.LocalSystemActivityCallbacks
import com.xike.app.ai.AiSettings
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.feature.ai.AiCheckboxRow
import com.xike.app.feature.ai.AiPanel
import com.xike.app.feature.ai.AiSection
import com.xike.app.tr
import com.xike.app.ui.theme.XikeDialogCompactGap
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.xikeSwitchColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class AiSettingsAction {
    TEST_CONNECTION,
    SAVE,
}

@Composable
internal fun AiSettingsDialog(onDismiss: () -> Unit, onSaved: (AiSettings) -> Unit) {
    val operations = LocalAiReviewOperations.current ?: return
    val callbacks = LocalSystemActivityCallbacks.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AiSettings()) }
    var savedSettings by remember { mutableStateOf<AiSettings?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<AiSettingsAction?>(null) }

    val dismiss = {
        job?.cancel()
        onDismiss()
    }
    // Stays open in the background so a Key can be copied from another app; locking still closes.
    LaunchedEffect(callbacks.isLocked) {
        if (callbacks.isLocked) dismiss()
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    val editable = loaded && action == null
    val normalized = settings.normalized()
    val endpoint = normalized.endpoint()
    val invalidAddress = normalized.address.isNotEmpty() && endpoint == null
    val unsaved = loaded && normalized != savedSettings
    AiPanel(
        onDismiss = dismiss,
        scrollState = rememberScrollState(),
        modifier = Modifier.testTag("ai-settings-dialog"),
        dismissOnOutsideTap = action == null && !unsaved,
        header = {
            Text(
                tr("AI 接口配置", "AI service settings"),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        actions = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap),
            ) {
                val status =
                    when (action) {
                        AiSettingsAction.TEST_CONNECTION -> tr("正在测试连接…", "Testing connection…")
                        AiSettingsAction.SAVE -> tr("正在保存…", "Saving…")
                        null -> message
                    }
                if (status != null) {
                    Text(
                        status,
                        modifier =
                            Modifier.testTag("ai-settings-message").semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (action != null) LinearProgressIndicator(Modifier.fillMaxWidth())
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = dismiss, shape = XikeShapes.button) {
                        Text(tr("取消", "Cancel"))
                    }
                    Button(
                        enabled = editable && normalized.isValid(),
                        shape = XikeShapes.button,
                        onClick = {
                            val value = settings.normalized()
                            if (
                                !loaded ||
                                    action != null ||
                                    !value.isValid() ||
                                    !callbacks.canHandleResult()
                            ) {
                                return@Button
                            }

                            action = AiSettingsAction.SAVE
                            job =
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    message = null
                                    try {
                                        operations
                                            .save(value)
                                            .onSuccess {
                                                savedSettings = value
                                                onSaved(value)
                                            }
                                            .onFailure { message = it.message }
                                    } finally {
                                        action = null
                                    }
                                }
                        },
                    ) {
                        Text(tr("保存", "Save"))
                    }
                }
            }
        },
    ) {
        // Read only after the dialog content is mounted.
        LaunchedEffect(operations) {
            try {
                val stored = operations.load()
                settings = stored
                savedSettings = stored.normalized()
                loaded = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message =
                    tr("无法读取 AI 配置，请关闭后重试。", "Unable to read AI settings. Close and try again.")
            }
        }
        if (!loaded && message == null) {
            Text(
                tr("正在读取配置…", "Loading settings…"),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        AiSection(tr("服务连接", "Service connection")) {
            Text(
                tr(
                    "使用兼容 Chat Completions 的接口，可填写基础地址或完整接口地址。",
                    "Use a Chat Completions compatible API. Enter a base URL or the full endpoint.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = settings.address,
                onValueChange = {
                    settings = settings.withAddress(it)
                    if (settings.apiKey.isEmpty()) showKey = false
                    message = null
                },
                label = { Text(tr("HTTPS 服务地址", "HTTPS service address")) },
                singleLine = true,
                enabled = editable,
                isError = invalidAddress,
                supportingText =
                    if (invalidAddress) {
                        { Text(tr("请输入有效的 HTTPS 地址。", "Enter a valid HTTPS address.")) }
                    } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("ai-address"),
            )
            OutlinedTextField(
                value = settings.model,
                onValueChange = {
                    settings = settings.copy(model = it)
                    message = null
                },
                label = { Text(tr("模型名称", "Model name")) },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth().testTag("ai-model"),
            )
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = {
                    settings = settings.copy(apiKey = it)
                    if (it.isEmpty()) showKey = false
                    message = null
                },
                label = { Text("API Key") },
                singleLine = true,
                enabled = editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation =
                    if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(
                        onClick = { showKey = !showKey },
                        enabled = editable && settings.apiKey.isNotEmpty(),
                        shape = XikeShapes.button,
                    ) {
                        Text(if (showKey) tr("隐藏", "Hide") else tr("显示", "Show"))
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("ai-key"),
            )
            Text(
                tr(
                    "API Key 加密保存在此设备，独立于日记备份。",
                    "The API key is encrypted on this device and kept outside journal backups.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (endpoint != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    tr("实际接收地址", "Request destination"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(endpoint.toString(), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                tr(
                    "测试连接只发送固定文字，不含日记内容，可能产生服务费用。",
                    "A connection test sends fixed text without journal content and may incur service fees.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                contentPadding = PaddingValues(0.dp),
                enabled = editable && normalized.configured,
                shape = XikeShapes.button,
                onClick = {
                    val value = settings.normalized()
                    if (
                        !loaded ||
                            action != null ||
                            !value.configured ||
                            !callbacks.canHandleResult()
                    ) {
                        return@TextButton
                    }

                    action = AiSettingsAction.TEST_CONNECTION
                    job =
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            message = null
                            try {
                                message =
                                    operations
                                        .test(value)
                                        .fold(
                                            { tr("连接成功。", "Connection successful.") },
                                            { it.message },
                                        )
                            } finally {
                                action = null
                            }
                        }
                },
            ) {
                Text(tr("测试连接", "Test connection"), Modifier.testTag("ai-test-label"))
            }
            if (settings.apiKey.isNotEmpty() || settings.consent) {
                TextButton(
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                    contentPadding = PaddingValues(0.dp),
                    enabled = editable,
                    onClick = {
                        settings = settings.copy(apiKey = "", enabled = false, consent = false)
                        showKey = false
                        message = null
                    },
                    shape = XikeShapes.button,
                ) {
                    Text(tr("清除 Key", "Clear Key"))
                }
            }
        }
        AiSection(tr("发送授权", "Data permission")) {
            Text(
                tr(
                    "发送后，所选内容由此服务处理，适用该服务的隐私政策。",
                    "When you send a review request, the selected content is handled under this service’s privacy policy.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AiCheckboxRow(
                label =
                    tr(
                        "我理解并同意向以上服务发送所选内容",
                        "I understand and agree to send selected content to this service",
                    ),
                checked = settings.consent,
                tag = "ai-consent",
                enabled = editable,
                onCheckedChange = {
                    // Agreeing is the decision to use AI review, so enable it whenever the
                    // connection is complete; the switch can still turn it off afterwards.
                    val updated = settings.copy(consent = it)
                    settings = updated.copy(enabled = it && updated.normalized().configured)
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("启用 AI 回顾", "Enable AI review"),
                    Modifier.weight(1f).testTag("ai-enabled-label"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { settings = settings.copy(enabled = it) },
                    enabled =
                        editable &&
                            (settings.enabled || (settings.consent && normalized.configured)),
                    colors = xikeSwitchColors(),
                    modifier = Modifier.testTag("ai-enabled"),
                )
            }
        }
    }
}
