package com.xike.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xike.app.AppLocale
import com.xike.app.data.model.MAX_DRAFT_NOTE_LENGTH
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeShapes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun MomentNoteCard(
    note: String,
    enabled: Boolean,
    onNoteChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    placeholderAlpha: Float = 0.78f,
    toolbar: @Composable () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = XikeContentCardPadding)
                .padding(top = 16.dp, bottom = 4.dp)
        ) {
            BasicTextField(
                value = note,
                onValueChange = onNoteChange,
                enabled = enabled,
                modifier =
                    Modifier.fillMaxWidth()
                        .semantics { contentDescription = localizedText("此刻的注脚") }
                        .onFocusChanged { onFocusChange(it.isFocused) },
                minLines = 3,
                maxLines = 5,
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.SansSerif,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth()) {
                            if (note.isEmpty()) {
                                Text(
                                    localizedText("这一刻，有什么想留下？"),
                                    style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.SansSerif
                                        ),
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = placeholderAlpha
                                        ),
                                )
                            }
                            innerTextField()
                        }
                        if (note.isNotEmpty()) {
                            Text(
                                "${note.length} / $MAX_DRAFT_NOTE_LENGTH",
                                modifier =
                                    Modifier.align(Alignment.End)
                                        .padding(top = 4.dp, bottom = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            toolbar()
        }
    }
}

@Composable
internal fun MomentContentToolbar(
    recordedAt: Long?,
    enabled: Boolean,
    onAdd: () -> Unit,
    onChooseTime: () -> Unit,
    onDone: (() -> Unit)? = null,
    isEditing: Boolean = false,
    addIconSize: Dp = 24.dp,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(Modifier.offset(x = (-8).dp).size(48.dp), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onAdd,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    XikeIcons.Add,
                    contentDescription = tr("添加内容", "Add content"),
                    modifier = Modifier.size(addIconSize),
                    tint =
                        if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
        TextButton(
            onClick = onDone ?: onChooseTime,
            enabled = enabled,
            shape = XikeShapes.button,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
        ) {
            Text(
                if (onDone != null) localizedText("完成")
                else if (recordedAt == null) tr("今天 · 修改时间", "Today · Change time")
                else {
                    val moment = Instant.ofEpochMilli(recordedAt).atZone(ZoneId.systemDefault())
                    val pattern =
                        if (moment.year == LocalDate.now().year) {
                            tr("M月d日 HH:mm", "MMM d, HH:mm")
                        } else {
                            tr("yyyy年M月d日 HH:mm", "MMM d, yyyy HH:mm")
                        }
                    DateTimeFormatter.ofPattern(pattern, AppLocale.locale).format(moment) +
                        if (isEditing) tr(" · 修改时间", " · Change time")
                        else tr(" · 补记", " · Backdated")
                },
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                    ),
            )
        }
    }
}

@Composable
internal fun MomentMediaAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = XikeShapes.inner,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors =
            ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor =
                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
            ),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        )
    }
}
