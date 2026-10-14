package com.xike.app.feature.archive

import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeSectionTitleStyle
import com.xike.app.data.JournalEntry
import com.xike.app.monthCalendarCells
import com.xike.app.AppLocale
import com.xike.app.localizedText
import com.xike.app.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter


@Composable
internal fun JournalMonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    countsByDate: Map<LocalDate, Int>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val monthTitle = month.format(DateTimeFormatter.ofPattern(tr("yyyy年 M月", "MMMM yyyy"), AppLocale.locale))
    val cells = remember(month) { monthCalendarCells(month) }
    val monthEntries = countsByDate.filterKeys { YearMonth.from(it) == month }
    val monthEntryCount = monthEntries.values.sum()
    val dayDiameter = 32.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val dayHeight = dayDiameter + 16.dp
    Surface(
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(XikeCardPadding)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(monthTitle, style = XikeSectionTitleStyle)
                    Text(
                        if (monthEntryCount == 0) localizedText("这个月还没有留下记录") else tr("${monthEntries.size} 天 · $monthEntryCount 条记录", "${monthEntries.size} days · $monthEntryCount entries"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = XikeShapes.button,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                ) {
                    Row {
                        IconButton(onClick = onPreviousMonth) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = localizedText("上个月"))
                        }
                        IconButton(onClick = onNextMonth) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = localizedText("下个月"))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.horizontalScroll(rememberScrollState())
                        .width(maxOf(maxWidth, (dayDiameter + 4.dp) * 7)),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf(localizedText("一"), localizedText("二"), localizedText("三"), localizedText("四"), localizedText("五"), localizedText("六"), localizedText("日")).forEach { weekday ->
                            Text(
                                weekday,
                                modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }

                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(Modifier.weight(1f).height(dayHeight))
                                } else {
                                    val dayCount = countsByDate[date] ?: 0
                                    CalendarDay(
                                        date = date,
                                        count = dayCount,
                                        isToday = date == today,
                                        isSelected = date == selectedDate,
                                        enabled = dayCount > 0,
                                        diameter = dayDiameter,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelectDate(date) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    diameter: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val spokenDate = date.format(DateTimeFormatter.ofPattern(tr("yyyy年M月d日 EEEE", "EEEE, MMM d, yyyy"), AppLocale.locale))
    Surface(
        modifier = modifier
            .height(diameter + 16.dp)
            .padding(horizontal = 2.dp)
            .semantics {
                contentDescription = "$spokenDate，${if (count == 0) localizedText("没有记录") else tr("$count 条记录", "$count entries")}"
                selected = isSelected
            }
            .clickable(enabled = enabled, onClick = onClick),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(diameter),
                shape = CircleShape,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    enabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    else -> Color.Transparent
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        date.dayOfMonth.toString(),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Box(
                modifier = Modifier.height(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (count > 0) {
                    Box(
                        Modifier
                            .size(width = if (count > 1) 10.dp else 4.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                            ),
                    )
                }
            }
        }
    }
}
