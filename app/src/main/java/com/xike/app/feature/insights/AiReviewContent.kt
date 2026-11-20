package com.xike.app.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xike.app.AppLocale
import com.xike.app.ai.AI_MAX_ENTRIES
import com.xike.app.ai.AiReviewResult
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSource
import com.xike.app.feature.ai.AiCheckboxRow
import com.xike.app.feature.ai.AiSection
import com.xike.app.tr
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeDialogCompactGap
import com.xike.app.ui.theme.XikeSectionTitleStyle
import com.xike.app.ui.theme.XikeShapes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

@Composable
internal fun AiReviewPreparation(
    summary: JournalPeriodSummary,
    config: AiSettings,
    includeNotes: Boolean,
    onIncludeNotesChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    AiReviewOverview(
        title = summary.period.label,
        dateRange = summary.dateRangeLabel(),
        detail =
            tr(
                "${summary.entryCount} 条记录 · ${summary.recordedDayCount} 天有记录",
                "${summary.entryCount} entries · ${summary.recordedDayCount} recorded days",
            ),
    )
    when {
        summary.entryCount == 0 ->
            Text(
                tr(
                    "这个周期还没有记录。可返回轨迹选择其他周期。",
                    "There are no entries in this period. Choose another period in Insights.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        summary.entryCount > AI_MAX_ENTRIES ->
            Text(
                tr(
                    "当前周期超过 $AI_MAX_ENTRIES 条记录，请选择更短的周期。",
                    "This period has over $AI_MAX_ENTRIES entries. Choose a shorter period.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
    }
    AiSection(tr("发送内容", "Content to send")) {
        Text(
            if (includeNotes)
                tr(
                    "将发送日期、时区、心情、感受、本机统计和日记文字。",
                    "Includes dates, time zone, moods, feelings, local statistics and journal notes.",
                )
            else
                tr(
                    "将发送日期、时区、心情、感受和本机统计。",
                    "Includes dates, time zone, moods, feelings and local statistics.",
                ),
            modifier = Modifier.testTag("ai-review-scope"),
            style = MaterialTheme.typography.bodyMedium,
        )
        AiCheckboxRow(
            label = tr("包含日记文字", "Include journal notes"),
            checked = includeNotes,
            tag = "ai-include-notes",
            onCheckedChange = onIncludeNotesChange,
        )
        Text(
            tr(
                "照片、录音、视频、地点和天气留在本机。",
                "Photos, audio, video, location and weather stay on this device.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AiSection(tr("AI 服务", "AI service")) {
        if (config.canPreview) {
            AiReviewDestination(config)
            config.sendDisabledReason()?.let { reason ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    tr("可先预览发送内容。$reason", "You can preview first. $reason"),
                    modifier = Modifier.testTag("ai-review-setup-hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                tr(
                    "填写服务地址和模型后，即可在本机预览发送内容。",
                    "Add a service address and model to preview the request on this device.",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!config.canUse) {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                contentPadding = PaddingValues(0.dp),
                shape = XikeShapes.button,
            ) {
                Text(tr("前往配置", "Open settings"))
            }
        }
    }
}

@Composable
internal fun AiReviewConfirmation(
    summary: JournalPeriodSummary,
    config: AiSettings,
    count: Int,
    includeNotes: Boolean,
    requestBody: String,
    requestExpanded: Boolean,
    onToggleRequest: () -> Unit,
) {
    AiSection(
        tr("发送确认", "Confirm what to send"),
        titleModifier = Modifier.testTag("ai-preview-heading"),
    ) {
        AiReviewDestination(config)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(summary.dateRangeLabel(), style = MaterialTheme.typography.titleSmall)
        Text(
            if (includeNotes) tr("$count 条记录 · 包含日记原文", "$count entries · Journal notes included")
            else tr("$count 条记录 · 不含日记原文", "$count entries · Journal notes excluded"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            tr(
                "确认后，以上所选内容会交给该服务处理。",
                "Sending passes the selected content to this service for analysis.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column {
        TextButton(
            onClick = onToggleRequest,
            shape = XikeShapes.button,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (requestExpanded) tr("收起完整请求", "Hide full request")
                else tr("查看完整请求", "View full request"),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (requestExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = XikeShapes.inner,
            ) {
                SelectionContainer {
                    Text(
                        requestBody,
                        modifier = Modifier.padding(XikeContentCardPadding).testTag("ai-preview"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiReviewDestination(config: AiSettings) {
    Text(
        tr("接收地址", "Destination"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        config.endpoint().toString(),
        modifier = Modifier.fillMaxWidth().testTag("ai-review-endpoint"),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        tr("模型 ${config.model}", "Model ${config.model}"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun AiReviewProgress(entryCount: Int?) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    if (entryCount != null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                elapsedSeconds++
            }
        }
    }
    AiSection(
        if (entryCount == null) tr("正在准备发送预览", "Preparing preview")
        else tr("正在生成回顾", "Generating review"),
        titleModifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            if (entryCount == null)
                tr("正在本机整理选定周期的记录。", "Preparing this period’s entries on your device.")
            else
                tr(
                    "正在等待服务返回 $entryCount 条记录的回顾。",
                    "Waiting for the service to review $entryCount entries.",
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (entryCount != null) {
            Text(
                tr(
                    "已等待 $elapsedSeconds 秒。取消后，已发出的请求仍可能产生费用。",
                    "Waiting for $elapsedSeconds s. Cancelling may not avoid fees for a request already sent.",
                ),
                modifier = Modifier.testTag("ai-review-waiting"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AiReviewReport(
    summary: JournalPeriodSummary,
    review: AiReviewResult,
    sources: List<AiSource>,
    onOpenSource: (AiSource) -> Unit,
) {
    val generatedAt =
        remember(review.generatedAt, AppLocale.locale) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(AppLocale.locale)
                .withZone(ZoneId.systemDefault())
                .format(review.generatedAt)
        }
    AiReviewOverview(
        title = tr("本次回顾", "Review report"),
        dateRange = summary.dateRangeLabel(),
        detail = tr("分析了 ${sources.size} 条记录", "Analyzed ${sources.size} entries"),
        metadata = tr("AI 生成 · $generatedAt", "AI-generated · $generatedAt"),
    )
    val sourcesByReference = remember(sources) { sources.associateBy(AiSource::reference) }
    review.observations.forEach { observation ->
        AiSection(observation.title) {
            SelectionContainer {
                Text(observation.text, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                tr("引用记录", "Referenced entries"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                observation.references.mapNotNull(sourcesByReference::get).forEach { source ->
                    OutlinedButton(
                        onClick = { onOpenSource(source) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = XikeShapes.button,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("${source.date} · #${source.reference}")
                    }
                }
            }
        }
    }
    if (review.questions.isNotEmpty()) {
        AiSection(tr("留给自己的问题", "Questions for yourself")) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap)) {
                    review.questions.forEachIndexed { index, question ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Text(question, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    Text(
        tr(
            "回顾结果仅在本次会话中显示。请结合原始记录理解 AI 的观察。",
            "This review is visible for this session. Check the original entries for context.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AiReviewOverview(
    title: String,
    dateRange: String,
    detail: String,
    metadata: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.padding(XikeContentCardPadding),
            verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(dateRange, style = XikeSectionTitleStyle)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (metadata != null) Text(metadata, style = MaterialTheme.typography.bodySmall)
        }
    }
}
