package com.xike.app.feature.insights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xike.app.JournalReadRepository
import com.xike.app.LocalSystemActivityCallbacks
import com.xike.app.ai.AI_MAX_ENTRIES
import com.xike.app.ai.AiReviewInput
import com.xike.app.ai.AiReviewResult
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSource
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.ai.aiRequest
import com.xike.app.ai.aiReviewInput
import com.xike.app.data.JournalDataException
import com.xike.app.data.JournalEntry
import com.xike.app.feature.settings.AiSettingsDialog
import com.xike.app.tr
import com.xike.app.ui.components.DestructiveConfirmationDialog
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeDialogCompactGap
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikeShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AiSettingsEntry {
    INITIAL,
    REVIEW,
}

private class PreparedAiReview(val input: AiReviewInput, val body: String)

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
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<AiSettings?>(null) }
    var settingsEntry by remember { mutableStateOf<AiSettingsEntry?>(null) }
    var includeNotes by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreparedAiReview?>(null) }
    var result by remember { mutableStateOf<AiReviewResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dismiss = {
        job?.cancel()
        onDismiss()
    }
    LaunchedEffect(operations) {
        try {
            val loadedSettings = operations.load()
            if (!loadedSettings.canPreview) settingsEntry = AiSettingsEntry.INITIAL
            settings = loadedSettings
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            error = tr("无法读取 AI 配置，请关闭后重试。", "Unable to read AI settings. Close and try again.")
        }
    }
    // The journal UI can stay composed when the app locks. Going to the background alone keeps
    // the review and any running request, so a billed result is not lost by switching apps.
    LaunchedEffect(callbacks.isLocked) {
        if (callbacks.isLocked) dismiss()
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    val config = settings
    val prepared = preview
    val report = result
    var requestExpanded by remember(prepared) { mutableStateOf(false) }
    // A new stage starts at the top. Visiting settings or a citation preserves scroll position.
    val scroll = remember(report, prepared, busy) { ScrollState(0) }
    if (config == null && error == null) return

    BackHandler(enabled = !sourceOpen && settingsEntry == null && !confirmDiscard) {
        if (prepared != null && report == null && !busy) {
            preview = null
            error = null
        } else {
            dismiss()
        }
    }

    if (settingsEntry == AiSettingsEntry.INITIAL) {
        AiSettingsDialog(
            onDismiss = dismiss,
            onSaved = { saved ->
                val previous = settings
                if (previous?.endpoint() != saved.endpoint() || previous?.model != saved.model) {
                    preview = null
                    result = null
                }
                if (previous != saved) error = null
                settings = saved
                settingsEntry = null
            },
        )
        return
    }

    val canPrepare = config?.canPreview == true && summary.entryCount in 1..AI_MAX_ENTRIES
    val sendDisabledReason =
        if (prepared != null && report == null && !busy) config?.sendDisabledReason() else null
    val activeStep =
        when {
            report != null -> 3
            busy && prepared != null -> 2
            prepared != null -> 1
            else -> 0
        }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("ai-review-page"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Top page bar
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    modifier =
                        Modifier.widthIn(max = XikeContentMaxWidth)
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = dismiss) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = tr("关闭", "Close"),
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            if (report != null) tr("AI 回顾 · 特刊", "AI review · Edition")
                            else tr("AI 回顾", "AI review"),
                            modifier = Modifier.semantics { heading() },
                            style =
                                if (report != null) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.titleLarge,
                            color =
                                if (report != null) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        enabled = config != null && !busy,
                        onClick = {
                            if (!busy && settingsEntry == null && settings != null) {
                                settingsEntry = AiSettingsEntry.REVIEW
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = tr("配置", "Settings"))
                    }
                }
            }

            if (report == null) {
                AiReviewStepBar(activeStep = activeStep)
            }

            // Scrollable page body
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier =
                        Modifier.widthIn(max = XikeContentMaxWidth)
                            .fillMaxSize()
                            .clipToBounds()
                            .verticalScroll(scroll)
                            .testTag("ai-review-content")
                            .padding(
                                horizontal = XikeScreenHorizontalPadding,
                                vertical = XikeScreenVerticalPadding,
                            ),
                    verticalArrangement = Arrangement.spacedBy(XikeContentGap),
                ) {
                    error?.let {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = XikeShapes.inner,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                it,
                                modifier =
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    when {
                        report != null && prepared != null ->
                            AiReviewReport(summary, report, prepared.input.sources, onOpenSource)
                        busy -> AiReviewProgress(entryCount = prepared?.input?.sources?.size)
                        prepared != null && config != null ->
                            AiReviewConfirmation(
                                summary = summary,
                                config = config,
                                count = prepared.input.sources.size,
                                includeNotes = includeNotes,
                                requestBody = prepared.body,
                                requestExpanded = requestExpanded,
                                onToggleRequest = { requestExpanded = !requestExpanded },
                            )
                        config != null ->
                            AiReviewPreparation(
                                summary = summary,
                                config = config,
                                includeNotes = includeNotes,
                                onIncludeNotesChange = {
                                    if (
                                        !busy &&
                                            settingsEntry == null &&
                                            preview == null &&
                                            result == null
                                    ) {
                                        includeNotes = it
                                        error = null
                                    }
                                },
                                onOpenSettings = {
                                    if (!busy && settingsEntry == null && settings != null) {
                                        settingsEntry = AiSettingsEntry.REVIEW
                                    }
                                },
                            )
                    }
                }
            }

            // Pinned bottom action bar
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier =
                        Modifier.widthIn(max = XikeContentMaxWidth)
                            .fillMaxWidth()
                            .padding(
                                horizontal = XikeScreenHorizontalPadding,
                                vertical = 12.dp,
                            ),
                    verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap),
                ) {
                    if (sendDisabledReason != null) {
                        Text(
                            sendDisabledReason,
                            modifier = Modifier.testTag("ai-send-disabled"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when {
                            busy ->
                                OutlinedButton(
                                    onClick = { job?.cancel() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = XikeShapes.button,
                                ) {
                                    Text(
                                        if (prepared == null) tr("取消准备", "Cancel preview")
                                        else tr("取消请求", "Cancel request")
                                    )
                                }
                            report != null ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        summary.period.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(
                                        onClick = {
                                            if (
                                                !busy && settingsEntry == null && result === report
                                            ) {
                                                confirmDiscard = true
                                            }
                                        },
                                        shape = XikeShapes.button,
                                    ) {
                                        Text(tr("重新准备", "Prepare again"))
                                    }
                                }
                            prepared != null && config != null -> {
                                TextButton(
                                    onClick = {
                                        if (
                                            !busy &&
                                                settingsEntry == null &&
                                                result == null &&
                                                preview === prepared
                                        ) {
                                            preview = null
                                            error = null
                                        }
                                    },
                                    shape = XikeShapes.button,
                                ) {
                                    Text(tr("返回", "Back"))
                                }
                                Button(
                                    enabled = config.canUse,
                                    onClick = {
                                        if (
                                            busy ||
                                                settingsEntry != null ||
                                                result != null ||
                                                !config.canUse ||
                                                settings != config ||
                                                preview !== prepared ||
                                                !callbacks.canHandleResult()
                                        )
                                            return@Button

                                        busy = true
                                        job =
                                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                error = null
                                                try {
                                                    val response =
                                                        operations.analyze(
                                                            config,
                                                            prepared.input,
                                                            prepared.body,
                                                        )
                                                    ensureActive()
                                                    if (callbacks.canHandleResult()) {
                                                        response
                                                            .onSuccess { result = it }
                                                            .onFailure { error = it.message }
                                                    } else {
                                                        error =
                                                            tr(
                                                                "结果返回时应用无法显示（例如设备已锁屏），未予保留。可手动重新发送。",
                                                                "The result arrived when it could not be shown (for example, while the device was locked) and was not kept. You can send again.",
                                                            )
                                                    }
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                    },
                                    modifier = Modifier.testTag("ai-send"),
                                    shape = XikeShapes.button,
                                ) {
                                    Text(
                                        if (error == null) tr("发送并分析", "Send and analyze")
                                        else tr("重新发送", "Send again"),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            config != null ->
                                Button(
                                    enabled = canPrepare,
                                    onClick = {
                                        if (
                                            busy ||
                                                settingsEntry != null ||
                                                result != null ||
                                                preview != null ||
                                                !canPrepare ||
                                                settings != config ||
                                                !callbacks.canHandleResult()
                                        )
                                            return@Button

                                        val previewIncludeNotes = includeNotes
                                        busy = true
                                        job =
                                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                error = null
                                                try {
                                                    val scoped =
                                                        readRepository?.aiReviewEntries(
                                                            summary.startDate,
                                                            summary.endDate,
                                                        )
                                                            ?: entries.filter {
                                                                it.id in summary.entryIds
                                                            }
                                                    val preparedReview =
                                                        withContext(Dispatchers.Default) {
                                                            val input =
                                                                aiReviewInput(
                                                                    scoped,
                                                                    summary,
                                                                    previewIncludeNotes,
                                                                )
                                                            PreparedAiReview(
                                                                input,
                                                                aiRequest(config, input),
                                                            )
                                                        }
                                                    ensureActive()
                                                    preview = preparedReview
                                                } catch (failure: CancellationException) {
                                                    throw failure
                                                } catch (failure: JournalDataException) {
                                                    error = failure.message
                                                    onReadError(requireNotNull(error))
                                                } catch (failure: IllegalArgumentException) {
                                                    error = failure.message
                                                } catch (_: Exception) {
                                                    error =
                                                        tr(
                                                            "无法准备记录，请重试。",
                                                            "Unable to prepare entries. Try again.",
                                                        )
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = XikeShapes.button,
                                ) {
                                    Text(
                                        tr("预览发送内容", "Preview content"),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    if (settingsEntry == AiSettingsEntry.REVIEW) {
        AiSettingsDialog(
            onDismiss = { settingsEntry = null },
            onSaved = { saved ->
                val previous = settings
                // The request body depends only on the model and the destination on the address;
                // Key, consent, and enablement changes keep the prepared preview.
                if (previous?.endpoint() != saved.endpoint() || previous?.model != saved.model) {
                    preview = null
                    result = null
                }
                if (previous != saved) error = null
                settings = saved
                settingsEntry = null
            },
        )
    }

    if (confirmDiscard && report != null) {
        DestructiveConfirmationDialog(
            title = tr("清除本次回顾？", "Clear this review?"),
            confirmText = tr("清除并重新准备", "Clear and prepare again"),
            dismissText = tr("保留", "Keep"),
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                if (!busy && settingsEntry == null && result === report) {
                    result = null
                    preview = null
                    error = null
                }
            },
        ) {
            Text(
                tr(
                    "回顾结果不会保存。重新发送会再次请求服务，可能产生费用。",
                    "The review is not saved. Sending again makes a new request and may incur fees.",
                )
            )
        }
    }
}

@Composable
private fun AiReviewStepBar(activeStep: Int) {
    val steps =
        listOf(
            tr("01 范围", "01 Scope"),
            tr("02 确认", "02 Confirm"),
            tr("03 生成", "03 Analyze"),
        )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier =
                Modifier.widthIn(max = XikeContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = XikeScreenHorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            steps.forEachIndexed { index, label ->
                val reached = index <= activeStep
                val current = index == activeStep
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(
                                    if (reached) MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                )
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        color =
                            if (current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun AiSettings.sendDisabledReason(): String? =
    when {
        !canPreview -> tr("请在配置中检查服务地址和模型。", "Check the service address and model in Settings.")
        apiKey.isBlank() -> tr("请在配置中填写 API Key。", "Add an API Key in Settings.")
        !consent -> tr("请在配置中同意发送说明。", "Accept the data notice in Settings.")
        !enabled -> tr("请在配置中启用 AI 回顾。", "Enable AI review in Settings.")
        else -> null
    }
