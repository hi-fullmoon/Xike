package com.xike.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xike.app.data.Feeling
import com.xike.app.data.Mood
import com.xike.app.data.feelingLabel
import com.xike.app.data.overallLabel
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeShapes

@Composable
internal fun MoodPicker(
    selectedMood: Mood?,
    enabled: Boolean,
    feelings: List<String> = emptyList(),
    onFeelingsChange: (List<String>) -> Unit = {},
    overallScale: Boolean = true,
    onSelected: (Mood?) -> Unit,
) {
    var showFeelings by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(localizedText("此刻的心情"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Surface(shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(XikeContentCardPadding)) {
                MoodChoices(
                    selectedMood,
                    enabled,
                    { onSelected(if (it == selectedMood) null else it) },
                    quietStyle = overallScale,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showFeelings = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = XikeShapes.button,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    val summary =
                        when {
                            feelings.isEmpty() -> tr("添加具体感受", "Add feelings")
                            feelings.size <= 3 ->
                                feelings.joinToString(tr("、", ", "), transform = ::feelingLabel)
                            else ->
                                tr(
                                    "${feelings.take(2).joinToString("、", transform = ::feelingLabel)}等 ${feelings.size} 种感受",
                                    "${feelings.take(2).joinToString(", ", transform = ::feelingLabel)} +${feelings.size - 2}",
                                )
                        }
                    Text(
                        summary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (feelings.isNotEmpty()) {
                            Text(
                                tr("编辑", "Edit"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (showFeelings && enabled) {
        FeelingSheet(
            feelings,
            onDismiss = { showFeelings = false },
            onConfirm = {
                onFeelingsChange(it)
                showFeelings = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FeelingSheet(
    feelings: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(feelings) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                tr("此刻还有哪些感受？", "What else are you feeling?"),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr("可以同时选择不同的感受", "Choose any feelings that fit"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Feeling.entries.forEach { feeling ->
                    val selected = feeling.name in draft
                    FeelingChip(
                        key = feeling.name,
                        selected = selected,
                        onClick = {
                            draft =
                                if (feeling.name in draft) draft - feeling.name
                                else draft + feeling.name
                        },
                    )
                }
                draft
                    .filter { key -> Feeling.entries.none { it.name == key } }
                    .forEach { key ->
                        FilterChip(
                            selected = true,
                            onClick = { draft = draft - key },
                            label = { Text(feelingLabel(key)) },
                        )
                    }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onConfirm(draft) },
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.button,
            ) {
                Text(tr("完成", "Done"))
            }
        }
    }
}

@Composable
internal fun FeelingChip(
    key: String,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null,
    filled: Boolean = true,
) {
    val feeling = Feeling.entries.find { it.name == key }
    val dark = isSystemInDarkTheme()
    val label = feelingLabel(key)
    val ink = feeling?.labelColor(dark)
    val selectedInk = feeling?.selectedLabelColor(dark)
    val container =
        ink?.copy(alpha = .14f)?.compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    val colors =
        if (ink != null && selectedInk != null && container != null) {
            FilterChipDefaults.filterChipColors(
                labelColor = ink,
                selectedLabelColor = selectedInk,
                selectedContainerColor = container,
                disabledLabelColor = selectedInk,
                disabledSelectedContainerColor = container,
            )
        } else {
            FilterChipDefaults.filterChipColors(
                disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                disabledSelectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        }
    FilterChip(
        selected = selected,
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier =
            if (onClick == null) Modifier.clearAndSetSemantics { text = AnnotatedString(label) }
            else Modifier,
        label = { Text(label) },
        shape = if (feeling != null) XikeShapes.button else FilterChipDefaults.shape,
        colors =
            if (filled) colors
            else
                colors.copy(
                    containerColor = Color.Transparent,
                    selectedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    disabledSelectedContainerColor = Color.Transparent,
                ),
        border =
            if (ink != null)
                BorderStroke(
                    if (selected && filled) 1.5.dp else 1.dp,
                    ink.copy(alpha = if (selected) .65f else .28f),
                )
            else FilterChipDefaults.filterChipBorder(enabled = true, selected = selected),
    )
}

private fun Feeling.labelColor(dark: Boolean): Color =
    when (this) {
        Feeling.HAPPY,
        Feeling.EXCITED,
        Feeling.HOPEFUL,
        Feeling.ENERGETIC -> if (dark) Color(0xFFE4B77D) else Color(0xFF805A2B)
        Feeling.PEACEFUL,
        Feeling.RELAXED,
        Feeling.SAFE,
        Feeling.CONTENT,
        Feeling.GRATEFUL -> if (dark) Color(0xFFA5C7AF) else Color(0xFF466650)
        Feeling.SAD,
        Feeling.DISAPPOINTED,
        Feeling.HURT,
        Feeling.LONELY -> if (dark) Color(0xFFAEC4DD) else Color(0xFF506881)
        Feeling.ANXIOUS,
        Feeling.IRRITATED,
        Feeling.ANGRY -> if (dark) Color(0xFFE0ADA2) else Color(0xFF8C574C)
        Feeling.TIRED,
        Feeling.BORED -> if (dark) Color(0xFFCAB5D7) else Color(0xFF766084)
    }

private fun Feeling.selectedLabelColor(dark: Boolean): Color =
    if (dark) labelColor(dark = true)
    else
        when (this) {
            Feeling.SAD,
            Feeling.DISAPPOINTED,
            Feeling.HURT,
            Feeling.LONELY -> Color(0xFF40566D)
            Feeling.ANXIOUS,
            Feeling.IRRITATED,
            Feeling.ANGRY -> Color(0xFF77463D)
            Feeling.TIRED,
            Feeling.BORED -> Color(0xFF624D70)
            else -> labelColor(dark = false)
        }

@Composable
internal fun MoodChoice(
    mood: Mood,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quietStyle: Boolean = false,
) {
    val visual = mood.visualStyle()
    val dark = isSystemInDarkTheme()
    val ink = if (dark) visual.container else visual.accent
    Column(
        modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = XikeShapes.inner,
            color =
                if (selected) {
                    if (dark) visual.accent.copy(alpha = .35f) else visual.container
                } else Color.Transparent,
        ) {
            Box(contentAlignment = Alignment.Center) { MoodEmoji(mood, size = 34.dp) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (quietStyle) mood.overallLabel else mood.label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) ink else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(5.dp))
        Surface(
            Modifier.size(4.dp),
            shape = CircleShape,
            color = if (selected) ink else Color.Transparent,
        ) {}
    }
}

@Composable
internal fun MoodChoices(
    selectedMood: Mood?,
    enabled: Boolean,
    onSelected: (Mood) -> Unit,
    quietStyle: Boolean = false,
) {
    val choices = if (quietStyle) Mood.overallChoices else Mood.legacyChoices
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    val minimumWidths =
        with(LocalDensity.current) {
            choices.map {
                maxOf(
                    48.dp,
                    textMeasurer
                        .measure(
                            if (quietStyle) it.overallLabel else it.label,
                            style,
                            softWrap = false,
                        )
                        .size
                        .width
                        .toDp() + 8.dp,
                )
            }
        }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth - 3.dp * (choices.size - 1)
        val equalWidth = availableWidth / choices.size
        val minimumTotalWidth = minimumWidths.fold(0.dp) { total, width -> total + width }
        val extraWidth =
            maxOf(
                0.dp,
                availableWidth - minimumTotalWidth,
            ) / choices.size
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .width(maxOf(maxWidth, minimumTotalWidth + 3.dp * (choices.size - 1)))
                .selectableGroup()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            choices.forEachIndexed { index, mood ->
                val choiceWidth =
                    if (minimumWidths.all { it <= equalWidth }) equalWidth
                    else minimumWidths[index] + extraWidth
                MoodChoice(
                    mood,
                    selectedMood == mood,
                    enabled,
                    { onSelected(mood) },
                    Modifier.weight(choiceWidth.value).fillMaxHeight(),
                    quietStyle,
                )
            }
        }
    }
}

private data class MoodVisualStyle(val accent: Color, val container: Color)

private fun Mood.visualStyle(): MoodVisualStyle =
    when (this) {
        Mood.LOW,
        Mood.VERY_LOW -> MoodVisualStyle(Color(0xFF506881), Color(0xFFE6EDF5))
        Mood.TIRED,
        Mood.BELOW_AVERAGE -> MoodVisualStyle(Color(0xFF766084), Color(0xFFEEE8F3))
        Mood.CALM,
        Mood.NEUTRAL -> MoodVisualStyle(Color(0xFF626C65), Color(0xFFEDF0ED))
        Mood.GOOD,
        Mood.ABOVE_AVERAGE -> MoodVisualStyle(Color(0xFF427461), Color(0xFFE0EEE8))
        Mood.JOYFUL,
        Mood.VERY_GOOD -> MoodVisualStyle(Color(0xFF8B6A2C), Color(0xFFFAF0DA))
    }
