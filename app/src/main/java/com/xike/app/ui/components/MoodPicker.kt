package com.xike.app.ui.components

import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeDialogCompactGap

import com.xike.app.localizedText
import com.xike.app.data.Mood


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MoodPicker(
    selectedMood: Mood?,
    enabled: Boolean,
    onSelected: (Mood?) -> Unit,
) {
    var showGuide by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(localizedText("此刻的心情"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = XikeShapes.card,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(XikeContentCardPadding)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val textMeasurer = rememberTextMeasurer()
                    val supportingStyle = MaterialTheme.typography.bodySmall
                    val guideStyle = MaterialTheme.typography.labelSmall
                    val requiredWidth = with(LocalDensity.current) {
                        (textMeasurer.measure(localizedText("选最接近的感受，没有标准答案"), supportingStyle, softWrap = false).size.width +
                            textMeasurer.measure(localizedText("选择参考"), guideStyle, softWrap = false).size.width).toDp()
                    } + 16.dp
                    val stackGuide = LocalDensity.current.fontScale >= 1.5f && requiredWidth > maxWidth
                    if (stackGuide) {
                        Column {
                            Text(localizedText("选最接近的感受，没有标准答案"), style = supportingStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showGuide = true }, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), shape = XikeShapes.button) {
                                Text(localizedText("选择参考"), style = guideStyle)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                localizedText("选最接近的感受，没有标准答案"),
                                modifier = Modifier.weight(1f),
                                style = supportingStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { showGuide = true },
                                shape = XikeShapes.button,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            ) { Text(localizedText("选择参考"), style = guideStyle) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MoodChoices(selectedMood, enabled, onSelected, quietStyle = true)
                if (selectedMood != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = XikeShapes.inner,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(XikeInnerCardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MoodEmoji(selectedMood, size = 20.dp)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                selectedMood.momentAffirmation(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGuide) {
        MoodGuideDialog(onDismiss = { showGuide = false })
    }
}

private fun Mood.momentAffirmation(): String = when (this) {
    Mood.LOW -> localizedText("低落，也值得被好好看见。")
    Mood.TIRED -> localizedText("疲惫时，可以慢一点。")
    Mood.CALM -> localizedText("平静，也是一种很好的答案。")
    Mood.GOOD -> localizedText("轻松，就记住这一刻。")
    Mood.JOYFUL -> localizedText("愉悦，和自己分享一下。")
}

@Composable
private fun MoodGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(localizedText("五种心情，怎么选？")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap),
            ) {
                Text(
                    localizedText("选择最接近此刻的一种即可，不必把复杂的感受说得很准确。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Mood.entries.forEach { mood ->
                    val visual = mood.visualStyle()
                    val isDark = isSystemInDarkTheme()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = XikeShapes.inner,
                        color = visual.container.copy(alpha = if (isDark) 0.16f else 0.58f),
                    ) {
                        Row(
                            modifier = Modifier.padding(XikeInnerCardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MoodEmoji(mood, size = 24.dp)
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(mood.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    mood.moodDescription(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("知道了")) } },
    )
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
    val isDark = isSystemInDarkTheme()
    val moodColor = if (quietStyle) MaterialTheme.colorScheme.primary
    else if (isDark) visual.container else visual.accent
    val containerColor = when {
        quietStyle && selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        quietStyle -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        selected && isDark -> visual.accent.copy(alpha = 0.28f)
        selected -> visual.container
        isDark -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        else -> visual.container.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) moodColor else MaterialTheme.colorScheme.outlineVariant,
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                MoodEmoji(mood, size = 24.dp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            mood.label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) moodColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MoodChoices(
    selectedMood: Mood?,
    enabled: Boolean,
    onSelected: (Mood) -> Unit,
    quietStyle: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val minWidth = with(LocalDensity.current) {
        maxOf(44.dp, Mood.entries.maxOf { textMeasurer.measure(it.label, labelStyle, softWrap = false).size.width }.toDp() + 8.dp)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + 3.dp) / (minWidth + 3.dp)).toInt().coerceIn(1, Mood.entries.size)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Mood.entries.chunked(columns).forEach { moods ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    moods.forEach { mood ->
                        MoodChoice(
                            mood = mood,
                            selected = selectedMood == mood,
                            enabled = enabled,
                            onClick = { onSelected(mood) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            quietStyle = quietStyle,
                        )
                    }
                    repeat(columns - moods.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}


private data class MoodVisualStyle(val accent: Color, val container: Color)

private fun Mood.visualStyle(): MoodVisualStyle = when (this) {
    Mood.LOW -> MoodVisualStyle(
        accent = Color(0xFF596779),
        container = Color(0xFFE1E5EA),
    )
    Mood.TIRED -> MoodVisualStyle(
        accent = Color(0xFF596F81),
        container = Color(0xFFE0E8ED),
    )
    Mood.CALM -> MoodVisualStyle(
        accent = Color(0xFF4F7D75),
        container = Color(0xFFDDEAE5),
    )
    Mood.GOOD -> MoodVisualStyle(
        accent = Color(0xFF8D5636),
        container = Color(0xFFF1E4D7),
    )
    Mood.JOYFUL -> MoodVisualStyle(
        accent = Color(0xFF8A611B),
        container = Color(0xFFF5E7C7),
    )
}
