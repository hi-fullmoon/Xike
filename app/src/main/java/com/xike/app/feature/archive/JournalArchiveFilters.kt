package com.xike.app.feature.archive

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.xike.app.AppLocale
import com.xike.app.JournalImageFilter
import com.xike.app.containsTopic
import com.xike.app.data.Mood
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.components.MoodEmoji
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeShapes
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class ArchiveDatePreset(private val sourceLabel: String) {
    ALL("全部日期"),
    LAST_7_DAYS("近 7 天"),
    LAST_30_DAYS("近 30 天"),
    THIS_YEAR("今年");

    val label: String
        get() = localizedText(sourceLabel)

    fun range(today: LocalDate): Pair<LocalDate?, LocalDate?> =
        when (this) {
            ALL -> null to null
            LAST_7_DAYS -> today.minusDays(6) to today
            LAST_30_DAYS -> today.minusDays(29) to today
            THIS_YEAR -> today.withDayOfYear(1) to today
        }
}

@Composable
internal fun ArchiveFilters(
    selectedMoodNames: List<String>,
    availableTags: List<String>,
    selectedTags: List<String>,
    imageFilter: JournalImageFilter,
    datePreset: ArchiveDatePreset,
    selectedDate: LocalDate?,
    onToggleMood: (Mood) -> Unit,
    onToggleTag: (String) -> Unit,
    onImageFilterChange: (JournalImageFilter) -> Unit,
    onDatePresetChange: (ArchiveDatePreset) -> Unit,
    onClearDate: () -> Unit,
    onClearAll: () -> Unit,
) {
    val selectedContentColor =
        if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary
                .copy(alpha = 0.92f)
                .compositeOver(MaterialTheme.colorScheme.onPrimaryContainer)
        }
    val filterColors =
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = selectedContentColor,
            selectedLeadingIconColor = selectedContentColor,
            selectedTrailingIconColor = selectedContentColor,
        )
    Surface(
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = XikeCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = XikeCardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(localizedText("筛选这段记忆"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        localizedText("条件可以叠加使用"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClearAll, shape = XikeShapes.button) {
                    Text(localizedText("全部清除"))
                }
            }
            FilterTitle(localizedText("心情"))
            LazyRow(
                contentPadding = PaddingValues(horizontal = XikeCardPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Mood.entries, key = Mood::name) { mood ->
                    FilterChip(
                        shape = XikeShapes.inner,
                        selected = mood.name in selectedMoodNames,
                        colors = filterColors,
                        onClick = { onToggleMood(mood) },
                        label = { Text(mood.label) },
                        leadingIcon = {
                            MoodEmoji(mood, size = 18.dp)
                        },
                    )
                }
            }

            if (availableTags.isNotEmpty()) {
                FilterTitle(localizedText("主题"))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = XikeCardPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(availableTags, key = { it }) { tag ->
                        FilterChip(
                            shape = XikeShapes.inner,
                            selected = selectedTags.containsTopic(tag),
                            colors = filterColors,
                            onClick = { onToggleTag(tag) },
                            label = { Text(localizedText(tag)) },
                        )
                    }
                }
            }

            FilterTitle(localizedText("日期"))
            LazyRow(
                contentPadding = PaddingValues(horizontal = XikeCardPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedDate != null) {
                    item(key = "selected-day") {
                        FilterChip(
                            shape = XikeShapes.inner,
                            selected = true,
                            colors = filterColors,
                            onClick = onClearDate,
                            label = { Text(selectedDate.asShortChineseDate()) },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    localizedText("清除指定日期"),
                                    Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
                items(ArchiveDatePreset.entries, key = ArchiveDatePreset::name) { preset ->
                    FilterChip(
                        shape = XikeShapes.inner,
                        selected = selectedDate == null && datePreset == preset,
                        colors = filterColors,
                        onClick = { onDatePresetChange(preset) },
                        label = { Text(preset.label) },
                    )
                }
            }

            FilterTitle(localizedText("照片"))
            LazyRow(
                contentPadding = PaddingValues(horizontal = XikeCardPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(JournalImageFilter.entries, key = JournalImageFilter::name) { option ->
                    FilterChip(
                        shape = XikeShapes.inner,
                        selected = imageFilter == option,
                        colors = filterColors,
                        onClick = { onImageFilterChange(option) },
                        label = { Text(option.label, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = XikeCardPadding),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun LocalDate.asShortChineseDate(): String =
    format(DateTimeFormatter.ofPattern(tr("M月d日", "MMM d"), AppLocale.locale))
