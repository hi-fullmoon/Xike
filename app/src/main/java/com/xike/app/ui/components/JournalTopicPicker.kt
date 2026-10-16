package com.xike.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xike.app.containsTopic
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.theme.XikeInlineActionGap
import com.xike.app.ui.theme.XikeShapes

internal data class JournalTopic(val label: String, val accent: Color, val icon: ImageVector)

internal val journalTopics =
    listOf(
        JournalTopic("工作", Color(0xFF56758B), Icons.Outlined.BusinessCenter),
        JournalTopic("学习", Color(0xFF766B98), Icons.AutoMirrored.Outlined.MenuBook),
        JournalTopic("关系", Color(0xFF9A6173), Icons.Outlined.FavoriteBorder),
        JournalTopic("家庭", Color(0xFF976A50), Icons.Outlined.Home),
        JournalTopic("身体", Color(0xFF568063), Icons.Outlined.SelfImprovement),
        JournalTopic("睡眠", Color(0xFF637197), Icons.Outlined.Bedtime),
        JournalTopic("饮食", Color(0xFFA87548), Icons.Outlined.Restaurant),
        JournalTopic("金钱", Color(0xFF987838), Icons.Outlined.AccountBalanceWallet),
        JournalTopic("自我", Color(0xFF447E78), Icons.Outlined.PersonOutline),
        JournalTopic("兴趣", Color(0xFF8A6190), Icons.Outlined.Interests),
        JournalTopic("出行", Color(0xFF4C7F8C), Icons.Outlined.Commute),
        JournalTopic("居住", Color(0xFF8B7057), Icons.Outlined.HomeWork),
        JournalTopic("其他", Color(0xFF6F766F), Icons.Outlined.MoreHoriz),
    )

@Composable
internal fun MomentTopicsHeader(selectedCount: Int, highlightSelection: Boolean = true) {
    val title = localizedText("主题")
    val count =
        if (selectedCount == 0) localizedText("可多选")
        else tr("已选 $selectedCount", "$selectedCount selected")
    val titleStyle = MaterialTheme.typography.titleSmall
    val countStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val requiredWidth =
        with(LocalDensity.current) {
            (measurer.measure(title, titleStyle, softWrap = false).size.width +
                    measurer.measure(count, countStyle, softWrap = false).size.width)
                .toDp() + 8.dp
        }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackCount = requiredWidth > maxWidth
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = if (stackCount) 1 else 2,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = titleStyle)
                Text(
                    localizedText("这一刻与什么有关？"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                if (stackCount) Modifier.fillMaxWidth() else Modifier,
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    count,
                    style = countStyle,
                    color =
                        if (selectedCount == 0 || !highlightSelection)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun TopicChoices(selectedTags: Collection<String>, onToggle: (JournalTopic) -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val minWidth =
        with(LocalDensity.current) {
            journalTopics
                .maxOf {
                    textMeasurer
                        .measure(localizedText(it.label), labelStyle, softWrap = false)
                        .size
                        .width
                }
                .toDp() + 42.dp
        }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + 7.dp) / (minWidth + 7.dp)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            journalTopics.chunked(columns).forEach { topics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    topics.forEach { topic ->
                        TopicChip(
                            topic = topic,
                            selected = selectedTags.containsTopic(topic.label),
                            onClick = { onToggle(topic) },
                            modifier = Modifier.weight(1f),
                            minHeight = 36.dp,
                        )
                    }
                    repeat(columns - topics.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun TopicChip(
    topic: JournalTopic,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    labelMaxLines: Int = 1,
    filled: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier =
            modifier
                .clip(XikeShapes.inner)
                .then(
                    if (onClick != null)
                        Modifier.selectable(
                            selected = selected,
                            role = Role.Checkbox,
                            onClick = onClick,
                        )
                    else Modifier
                ),
        shape = XikeShapes.inner,
        color =
            if (!filled) Color.Transparent
            else if (selected) topic.accent.copy(alpha = if (isDark) 0.26f else 0.13f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border =
            BorderStroke(
                1.dp,
                if (selected) topic.accent.copy(alpha = 0.58f) else Color.Transparent,
            ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier.heightIn(min = minHeight).padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = topic.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint =
                    if (isDark) androidx.compose.ui.graphics.lerp(topic.accent, Color.White, 0.45f)
                    else topic.accent.copy(alpha = if (selected) 1f else 0.72f),
            )
            Spacer(Modifier.width(XikeInlineActionGap))
            Text(
                localizedText(topic.label),
                maxLines = labelMaxLines,
                style = MaterialTheme.typography.labelLarge,
                color =
                    when {
                        selected && isDark -> MaterialTheme.colorScheme.onSurface
                        selected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
