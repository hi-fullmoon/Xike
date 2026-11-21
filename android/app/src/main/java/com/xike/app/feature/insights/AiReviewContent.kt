package com.xike.app.feature.insights

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xike.app.AppLocale
import com.xike.app.ai.AI_MAX_ENTRIES
import com.xike.app.ai.AiObservation
import com.xike.app.ai.AiReviewResult
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSource
import com.xike.app.feature.ai.AiCheckboxRow
import com.xike.app.feature.ai.AiSection
import com.xike.app.tr
import com.xike.app.ui.components.MoodEmoji
import com.xike.app.ui.components.PaperCard
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeDialogCompactGap
import com.xike.app.ui.theme.XikeEyebrowStyle
import com.xike.app.ui.theme.XikePageTitleStyle
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            tr("本期范围", "PERIOD SCOPE"),
            style = XikeEyebrowStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            summary.period.label,
            modifier = Modifier.semantics { heading() },
            style = XikePageTitleStyle,
        )
        Text(
            summary.dateRangeLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditorialMetaChip(tr("${summary.entryCount} 条记录", "${summary.entryCount} entries"))
            EditorialMetaChip(
                tr("${summary.recordedDayCount} 天有记录", "${summary.recordedDayCount} recorded days")
            )
        }
    }

    when {
        summary.entryCount == 0 ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.inner,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    tr(
                        "这个周期还没有记录。可返回轨迹选择其他周期。",
                        "There are no entries in this period. Choose another period in Insights.",
                    ),
                    modifier = Modifier.padding(XikeContentCardPadding),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        summary.entryCount > AI_MAX_ENTRIES ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.inner,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    tr(
                        "当前周期超过 $AI_MAX_ENTRIES 条记录，请选择更短的周期。",
                        "This period has over $AI_MAX_ENTRIES entries. Choose a shorter period.",
                    ),
                    modifier = Modifier.padding(XikeContentCardPadding),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }

    AiSection(tr("AI 服务", "AI service")) {
        if (config.canPreview) {
            AiReviewDestination(config)
            config.sendDisabledReason()?.let { reason ->
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            tr("发送前核对", "READY TO SEND"),
            style = XikeEyebrowStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            tr("发送确认", "Confirm what to send"),
            modifier = Modifier.testTag("ai-preview-heading").semantics { heading() },
            style = XikeSectionTitleStyle,
        )
        Text(
            tr(
                "确认后，以下所选内容会交给该服务处理。",
                "Sending passes the selected content to this service for analysis.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap)) {
            AiReviewDestination(config)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Text(summary.dateRangeLabel(), style = MaterialTheme.typography.titleSmall)
            Text(
                if (includeNotes)
                    tr("$count 条记录 · 包含日记原文", "$count entries · Journal notes included")
                else tr("$count 条记录 · 不含日记原文", "$count entries · Journal notes excluded"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = XikeShapes.inner,
                border =
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
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
    val pulseTransition = rememberInfiniteTransition(label = "ai-progress-pulse")
    val auraAlpha by
        pulseTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec =
                infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
            label = "ai-aura-alpha",
        )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier =
                Modifier.size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = auraAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            if (entryCount == null) tr("正在准备发送预览", "Preparing preview")
            else tr("正在整理这段时间的片段", "Composing your review"),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = XikeSectionTitleStyle,
            textAlign = TextAlign.Center,
        )
        Text(
            if (entryCount == null)
                tr("正在本机整理选定周期的记录。", "Preparing this period’s entries on your device.")
            else
                tr(
                    "正在等待服务返回 $entryCount 条记录的回顾。",
                    "Waiting for the service to review $entryCount entries.",
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.72f).height(4.dp).clip(CircleShape),
            strokeCap = StrokeCap.Round,
        )
        if (entryCount != null) {
            Text(
                tr(
                    "已等待 $elapsedSeconds 秒。取消后，已发出的请求仍可能产生费用。",
                    "Waiting for $elapsedSeconds s. Cancelling may not avoid fees for a request already sent.",
                ),
                modifier = Modifier.testTag("ai-review-waiting").padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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
    val sourcesByReference = remember(sources) { sources.associateBy(AiSource::reference) }

    // 1. Editorial Cover Hero
    AiReviewCoverHero(
        summary = summary,
        review = review,
        analyzedCount = sources.size,
        generatedAt = generatedAt,
    )

    // 2. Numbered Editorial Observation Chapters
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr("观察与线索", "OBSERVATIONS"),
            style = XikeEyebrowStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            tr("共 ${review.observations.size} 节", "${review.observations.size} chapters"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    review.observations.forEachIndexed { index, observation ->
        AiObservationChapter(
            index = index + 1,
            observation = observation,
            sourcesByReference = sourcesByReference,
            onOpenSource = onOpenSource,
        )
    }

    // 3. Quiet Reflection Postscript Card
    if (review.questions.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        AiReflectionPostscript(questions = review.questions)
    }

    // 4. Editorial Colophon
    Text(
        tr(
            "回顾结果仅在本次会话中显示。请结合原始记录理解 AI 的观察。",
            "This review is visible for this session. Check the original entries for context.",
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AiReviewCoverHero(
    summary: JournalPeriodSummary,
    review: AiReviewResult,
    analyzedCount: Int,
    generatedAt: String,
) {
    val coverGradient =
        Brush.linearGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    MaterialTheme.colorScheme.surfaceContainerLow,
                )
        )
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("ai-review-cover"),
        shape = XikeShapes.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier =
                Modifier.background(coverGradient).padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr(
                        "息刻特刊 · ${summary.dateRangeLabel()}",
                        "XIKE EDITION · ${summary.dateRangeLabel()}",
                    ),
                    modifier = Modifier.weight(1f),
                    style = XikeEyebrowStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            SelectionContainer {
                Text(
                    review.headline
                        ?: tr("${summary.period.label}的片刻回响", "Echoes of ${summary.period.label}"),
                    modifier = Modifier.semantics { heading() },
                    style = XikePageTitleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            review.summary?.let { lead ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.width(3.dp)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                    )
                    SelectionContainer {
                        Text(
                            lead,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        )
                    }
                }
            }

            if (review.keywords.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    review.keywords.forEach { keyword ->
                        Surface(
                            shape = XikeShapes.button,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            border =
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                ),
                        ) {
                            Text(
                                keyword,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            val activeMoods =
                remember(summary.moodDistribution) {
                    summary.moodDistribution
                        .filter { it.entryCount > 0 }
                        .sortedByDescending { it.mood.score }
                }
            if (activeMoods.isNotEmpty()) {
                AiReviewMoodSpectrum(activeMoods)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Text(
                tr(
                    "分析了 $analyzedCount 条记录 · ${summary.recordedDayCount} 天留痕 · AI 生成 · $generatedAt",
                    "Analyzed $analyzedCount entries · ${summary.recordedDayCount} days · AI-generated · $generatedAt",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiReviewMoodSpectrum(items: List<MoodDistributionItem>) {
    val palette =
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { index, item ->
                val weight = item.entryCount.toFloat().coerceAtLeast(1f)
                Box(
                    modifier =
                        Modifier.weight(weight)
                            .fillMaxHeight()
                            .background(palette[index % palette.size])
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoodEmoji(item.mood, size = 16.dp)
                    Text(
                        "${item.mood.label} ${item.entryCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiObservationChapter(
    index: Int,
    observation: AiObservation,
    sourcesByReference: Map<Int, AiSource>,
    onOpenSource: (AiSource) -> Unit,
) {
    val chapterNumber = remember(index) { index.toString().padStart(2, '0') }
    PaperCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chapterNumber,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 1.sp,
                        ),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                )
                observation.tag?.let { tag ->
                    Surface(
                        shape = XikeShapes.button,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    ) {
                        Text(
                            tag,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Text(
                observation.title,
                modifier = Modifier.semantics { heading() },
                style = XikeSectionTitleStyle,
            )

            SelectionContainer {
                Text(
                    observation.text,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }

            val resolvedSources =
                remember(observation.references, sourcesByReference) {
                    observation.references.mapNotNull(sourcesByReference::get)
                }
            if (resolvedSources.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
                Text(
                    tr("溯源原记录", "Referenced entries"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    resolvedSources.forEach { source ->
                        AiCitationTextLink(source = source, onClick = { onOpenSource(source) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCitationTextLink(source: AiSource, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.heightIn(min = 36.dp)
                .clip(XikeShapes.button)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "${source.date} · #${source.reference}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AiReflectionPostscript(questions: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("ai-review-questions"),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tr("留白与提问", "FOR QUIET REFLECTION"),
                    style = XikeEyebrowStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    tr("留给自己的问题", "Questions for yourself"),
                    modifier = Modifier.semantics { heading() },
                    style = XikeSectionTitleStyle,
                )
                Text(
                    tr(
                        "不必急着回答，留在心里就好。",
                        "No rush to answer—just let them sit with you.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    questions.forEachIndexed { index, question ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                "${index + 1}",
                                modifier = Modifier.padding(top = 2.dp).alpha(0.65f),
                                style =
                                    MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = FontFamily.Serif
                                    ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                question,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialMetaChip(text: String) {
    Surface(
        shape = XikeShapes.button,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
