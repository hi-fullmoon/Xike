package com.xike.app.feature.insights

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xike.app.JournalReadRepository
import com.xike.app.LocalSystemActivityCallbacks
import com.xike.app.ai.AiReviewInput
import com.xike.app.ai.AiReviewResult
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSource
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.ai.aiRequest
import com.xike.app.ai.aiReviewInput
import com.xike.app.data.JournalEntry
import com.xike.app.feature.settings.AiSettingsDialog
import com.xike.app.tr
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.ui.theme.XikeShapes
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AiReviewDialog(
    summary: JournalPeriodSummary,
    entries: List<JournalEntry>,
    readRepository: JournalReadRepository?,
    onReadError: (String) -> Unit,
    onOpenSource: (AiSource) -> Unit,
    sourceOpen: Boolean,
    onDismiss: () -> Unit,
) {
    val operations = LocalAiReviewOperations.current ?: return
    val callbacks = LocalSystemActivityCallbacks.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<AiSettings?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var includeNotes by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf<AiReviewInput?>(null) }
    var request by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<AiReviewResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    // Session-only state: a new result must not restore the preview's saved scroll offset.
    val scroll = remember(result) { ScrollState(0) }

    LaunchedEffect(operations) {
        try {
            settings = operations.load()
            showSettings = settings?.canPreview != true
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            error = tr("无法读取 AI 配置，请关闭后重试。", "Unable to read AI settings. Close and try again.")
        }
    }
    // Lock changes may keep the underlying journal UI composed: cancel before dismissing it.
    LaunchedEffect(callbacks.isLocked) {
        if (callbacks.isLocked) {
            job?.cancel()
            onDismiss()
        }
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

    if (showSettings) {
        AiSettingsDialog(
            onDismiss = { showSettings = false },
            onSaved = {
                settings = it
                showSettings = false
            },
        )
        return
    }
    if (sourceOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        // Keep the window steady when content or action labels change. Short screens constrain
        // this height; the scrollable body gives space to the title and actions first.
        modifier = Modifier.height(660.dp),
        shape = XikeShapes.dialog,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(tr("AI 回顾", "AI review")) },
        text = {
            key(result) {
                Column(
                    Modifier.fillMaxHeight()
                        .clipToBounds()
                        .verticalScroll(scroll)
                        .testTag("ai-review-content"),
                    verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap),
                ) {
                    Text(summary.dateRangeLabel(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr(
                            "${summary.entryCount} 条记录 · ${summary.recordedDayCount} 个记录日",
                            "${summary.entryCount} entries · ${summary.recordedDayCount} recorded days",
                        )
                    )
                    Text(
                        tr(
                            "主动发送后才会联网。AI 解读可能有误，请结合原始记录理解。",
                            "The service is contacted only when you send. AI interpretations may be inaccurate; consult the original entries.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    settings?.let { config ->
                        Text(tr("模型：${config.model}", "Model: ${config.model}"))
                        config.endpoint()?.let {
                            Text(
                                tr("接收地址：$it", "Destination: $it"),
                                modifier =
                                    Modifier.testTag("ai-review-endpoint")
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        if (!config.canUse)
                            Text(
                                tr(
                                    "请在配置中同意发送说明并启用 AI 回顾。",
                                    "Accept the data notice and enable AI review in settings.",
                                )
                            )
                    }
                    if (result == null) {
                        var firstLineCenterPx by remember { mutableIntStateOf(0) }
                        Row(
                            modifier =
                                Modifier.testTag("ai-include-notes")
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(
                                        value = includeNotes,
                                        enabled = !busy,
                                        role = Role.Checkbox,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onValueChange = {
                                            if (
                                                !busy &&
                                                    !showSettings &&
                                                    result == null &&
                                                    includeNotes != it
                                            ) {
                                                includeNotes = it
                                                input = null
                                                request = null
                                                error = null
                                            }
                                        },
                                    ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = includeNotes,
                                enabled = !busy,
                                modifier =
                                    Modifier.testTag("ai-include-notes-checkbox")
                                        .size(20.dp)
                                        .alignBy {
                                            it.measuredHeight / 2
                                        },
                                onCheckedChange = null,
                            )
                            Text(
                                tr("包含日记文字", "Include journal notes"),
                                modifier =
                                    Modifier.testTag("ai-include-notes-text").alignBy {
                                        firstLineCenterPx
                                    },
                                onTextLayout = { layout ->
                                    firstLineCenterPx =
                                        ((layout.getLineTop(0) + layout.getLineBottom(0)) / 2)
                                            .roundToInt()
                                },
                            )
                        }
                        Text(
                            if (includeNotes)
                                tr(
                                    "将发送日期、心情、感受和日记文字。",
                                    "Dates, moods, feelings and notes will be sent.",
                                )
                            else
                                tr(
                                    "仅发送日期、心情和感受，不含日记文字。",
                                    "Only dates, moods and feelings will be sent; notes are excluded.",
                                ),
                            modifier = Modifier.testTag("ai-review-scope"),
                        )
                        Text(
                            tr(
                                "不发送照片、录音、视频、位置或天气。",
                                "Photos, audio, video, location and weather are excluded.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) Text(tr("处理中，可取消本次操作…", "Working. You can cancel this operation…"))
                    result?.let { review ->
                        Text(
                            tr(
                                "生成时间：${review.generatedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}",
                                "Generated: ${review.generatedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            tr(
                                "实际分析 ${input?.sources?.size ?: 0} 条记录",
                                "Analyzed ${input?.sources?.size ?: 0} entries",
                            )
                        )
                        review.observations.forEach { observation ->
                            Text(observation.title, style = MaterialTheme.typography.titleSmall)
                            Text(observation.text)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(XikeDialogContentGap)
                            ) {
                                observation.references.forEach { reference ->
                                    input
                                        ?.sources
                                        ?.firstOrNull { it.reference == reference }
                                        ?.let { source ->
                                            TextButton(
                                                onClick = { onOpenSource(source) },
                                                modifier = Modifier.heightIn(min = 48.dp),
                                                contentPadding =
                                                    PaddingValues(
                                                        horizontal = 0.dp,
                                                        vertical = 8.dp,
                                                    ),
                                            ) {
                                                Text("${source.date} · #${source.reference}")
                                            }
                                        }
                                }
                            }
                        }
                        Text(
                            tr("留给自己的问题", "Questions for yourself"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        review.questions.forEach { Text(it) }
                        Text(
                            tr(
                                "结果仅保留在本次会话，不会写入日记。",
                                "This result stays in this session and is not written to your journal.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (result == null)
                        request?.let { body ->
                            Text(
                                tr(
                                    "以下是将发送的完整请求正文（不含 Key）：",
                                    "Full request body to be sent (Key excluded):",
                                ),
                                modifier = Modifier.testTag("ai-preview-heading"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            SelectionContainer {
                                Text(
                                    body,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("ai-preview"),
                                )
                            }
                        }
                }
            }
        },
        confirmButton = {
            FlowRow {
                TextButton(
                    enabled = settings != null && !busy,
                    onClick = {
                        if (!busy && !showSettings && settings != null) {
                            job?.cancel()
                            input = null
                            request = null
                            result = null
                            error = null
                            showSettings = true
                        }
                    },
                ) {
                    Text(tr("配置", "Settings"))
                }
                if (busy) {
                    TextButton(
                        onClick = {
                            job?.cancel()
                        }
                    ) {
                        Text(tr("取消请求", "Cancel request"))
                    }
                } else if (result != null) {
                    TextButton(
                        onClick = {
                            if (!busy && !showSettings && result != null) {
                                result = null
                                input = null
                                request = null
                                error = null
                            }
                        }
                    ) {
                        Text(tr("重新准备", "Prepare again"))
                    }
                } else if (request == null) {
                    TextButton(
                        enabled = settings?.canPreview == true && summary.entryCount > 0,
                        onClick = {
                            val previewSettings = settings
                            val previewIncludeNotes = includeNotes
                            if (
                                !busy &&
                                    !showSettings &&
                                    result == null &&
                                    request == null &&
                                    previewSettings?.canPreview == true &&
                                    summary.entryCount > 0 &&
                                    callbacks.canHandleResult()
                            ) {
                                busy = true
                                job = scope.launch {
                                    error = null
                                    try {
                                        val scoped =
                                            readRepository?.aiReviewEntries(
                                                summary.startDate,
                                                summary.endDate,
                                            ) ?: entries.filter { it.id in summary.entryIds }
                                        val prepared =
                                            withContext(Dispatchers.Default) {
                                                val value =
                                                    aiReviewInput(
                                                        scoped,
                                                        summary,
                                                        previewIncludeNotes,
                                                    )
                                                value to aiRequest(previewSettings, value)
                                            }
                                        ensureActive()
                                        input = prepared.first
                                        request = prepared.second
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (failure: com.xike.app.data.JournalDataException) {
                                        error = failure.message
                                        onReadError(requireNotNull(error))
                                    } catch (failure: IllegalArgumentException) {
                                        error = failure.message
                                    } catch (_: Exception) {
                                        error =
                                            tr(
                                                "无法准备记录，请关闭后重试。",
                                                "Unable to prepare entries. Close and try again.",
                                            )
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                    ) {
                        Text(tr("预览发送内容", "Preview content"))
                    }
                } else {
                    val previewSettings = settings
                    val previewInput = input
                    val previewRequest = request
                    TextButton(
                        enabled = previewSettings?.canUse == true,
                        onClick = {
                            if (
                                !busy &&
                                    !showSettings &&
                                    result == null &&
                                    previewSettings?.canUse == true &&
                                    previewInput != null &&
                                    previewRequest != null &&
                                    settings == previewSettings &&
                                    input === previewInput &&
                                    request == previewRequest &&
                                    callbacks.canHandleResult()
                            ) {
                                busy = true
                                job = scope.launch {
                                    error = null
                                    try {
                                        val response =
                                            operations.analyze(
                                                previewSettings,
                                                previewInput,
                                                previewRequest,
                                            )
                                        ensureActive()
                                        if (callbacks.canHandleResult()) {
                                            response
                                                .onSuccess { result = it }
                                                .onFailure { error = it.message }
                                        }
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("ai-send"),
                    ) {
                        Text(tr("发送并分析", "Send and analyze"))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = XikeShapes.button) {
                Text(tr("关闭", "Close"))
            }
        },
    )
}
