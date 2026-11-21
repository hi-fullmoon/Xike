package com.xike.app.feature.archive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.XikeShapes
import java.time.LocalDate

internal enum class ArchiveViewMode {
    CALENDAR,
    TIMELINE,
}

@Composable
internal fun ArchiveToolbox(
    queryText: String,
    viewMode: ArchiveViewMode,
    activeFilterCount: Int,
    showFilters: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onViewModeChange: (ArchiveViewMode) -> Unit,
    onToggleFilters: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(XikeContentGap)) {
        ArchiveSearchBar(
            value = queryText,
            onValueChange = onQueryChange,
            onClear = onClearQuery,
        )
        ArchiveControls(
            viewMode = viewMode,
            activeFilterCount = activeFilterCount,
            showFilters = showFilters,
            onViewModeChange = onViewModeChange,
            onToggleFilters = onToggleFilters,
        )
    }
}

@Composable
internal fun ArchiveMemoriesCard(
    today: LocalDate,
    days: List<MemoryDay>,
    onClick: () -> Unit,
) {
    val totalCount = days.sumOf { it.count }
    val hasMonthAgo = days.any { it.kind == MemoryKind.MONTH_AGO }
    val previousYears = days.filter { it.kind == MemoryKind.PREVIOUS_YEAR }
    val title = tr("往日回望", "Past moments")
    val summary =
        when {
            hasMonthAgo && previousYears.isNotEmpty() ->
                tr(
                    "一个月前与往年今天 · 共 $totalCount 条",
                    "One month ago & past years · $totalCount entries",
                )
            hasMonthAgo -> tr("一个月前的今天 · 共 $totalCount 条", "One month ago · $totalCount entries")
            previousYears.size == 1 && previousYears.first().date.year == today.year - 1 ->
                tr("去年的今天 · 共 $totalCount 条", "One year ago · $totalCount entries")
            else -> tr("往年的今天 · 共 $totalCount 条", "On this day in past years · $totalCount entries")
        }
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("archive-memories-entry")
                .clickable(role = Role.Button, onClick = onClick),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = XikeCardPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ArchiveSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("archive-search")
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    XikeShapes.inner,
                ),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle =
            MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                shape = XikeShapes.inner,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        tr("搜索记录", "Search entries"),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                prefix = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = localizedText("清空搜索"),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
            )
        },
    )
}

@Composable
internal fun ArchiveControls(
    viewMode: ArchiveViewMode,
    activeFilterCount: Int,
    showFilters: Boolean,
    onViewModeChange: (ArchiveViewMode) -> Unit,
    onToggleFilters: () -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val labelWidthPixels =
        maxOf(
            textMeasurer.measure(localizedText("月历"), labelStyle, softWrap = false).size.width,
            textMeasurer.measure(localizedText("时间流"), labelStyle, softWrap = false).size.width,
        )
    val minimumModesWidth =
        with(density) {
            // Match each padding/gap's pixel rounding when reserving single-line label space.
            ((labelWidthPixels + 4.dp.roundToPx() * 2) * 2 +
                    4.dp.roundToPx() * 2 +
                    3.dp.roundToPx())
                .toDp()
        }
    val filterWidth =
        if (activeFilterCount == 0) 48.dp
        else {
            maxOf(
                48.dp,
                40.dp +
                    with(density) {
                        textMeasurer
                            .measure(
                                activeFilterCount.toString(),
                                MaterialTheme.typography.labelSmall,
                                softWrap = false,
                            )
                            .size
                            .width
                            .toDp()
                    },
            )
        }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < minimumModesWidth + filterWidth + 6.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArchiveModeGroup(
                    viewMode,
                    minimumModesWidth,
                    onViewModeChange,
                    Modifier.fillMaxWidth(),
                )
                ArchiveFilterToggle(
                    activeFilterCount,
                    showFilters,
                    onToggleFilters,
                    Modifier.align(Alignment.End),
                )
            }
        } else {
            var modeGroupHeightPx by remember { mutableIntStateOf(0) }
            val matchedHeight = maxOf(48.dp, with(density) { modeGroupHeightPx.toDp() })
            val visualFilterSize = maxOf(40.dp, matchedHeight - 8.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArchiveModeGroup(
                    viewMode,
                    minimumModesWidth,
                    onViewModeChange,
                    Modifier.weight(1f).onSizeChanged { modeGroupHeightPx = it.height },
                )
                ArchiveFilterToggle(
                    activeFilterCount,
                    showFilters,
                    onToggleFilters,
                    visualSize = visualFilterSize,
                    modifier = Modifier.heightIn(min = matchedHeight),
                )
            }
        }
    }
}

@Composable
private fun ArchiveModeGroup(
    viewMode: ArchiveViewMode,
    minimumWidth: Dp,
    onViewModeChange: (ArchiveViewMode) -> Unit,
    modifier: Modifier,
) {
    val selectedItemRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    Surface(
        modifier = modifier,
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints {
            LaunchedEffect(viewMode, maxWidth, minimumWidth, density) {
                withFrameNanos {}
                selectedItemRequester.bringIntoView()
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .width(maxOf(maxWidth, minimumWidth))
                    .padding(horizontal = 4.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ArchiveModeButton(
                    label = localizedText("月历"),
                    selected = viewMode == ArchiveViewMode.CALENDAR,
                    modifier =
                        Modifier.weight(1f)
                            .testTag("archive-mode-calendar")
                            .then(
                                if (viewMode == ArchiveViewMode.CALENDAR)
                                    Modifier.bringIntoViewRequester(selectedItemRequester)
                                else Modifier
                            ),
                    onClick = { onViewModeChange(ArchiveViewMode.CALENDAR) },
                )
                ArchiveModeButton(
                    label = localizedText("时间流"),
                    selected = viewMode == ArchiveViewMode.TIMELINE,
                    modifier =
                        Modifier.weight(1f)
                            .testTag("archive-mode-timeline")
                            .then(
                                if (viewMode == ArchiveViewMode.TIMELINE)
                                    Modifier.bringIntoViewRequester(selectedItemRequester)
                                else Modifier
                            ),
                    onClick = { onViewModeChange(ArchiveViewMode.TIMELINE) },
                )
            }
        }
    }
}

@Composable
private fun ArchiveFilterToggle(
    activeFilterCount: Int,
    showFilters: Boolean,
    onToggleFilters: () -> Unit,
    modifier: Modifier = Modifier,
    visualSize: Dp = 40.dp,
) {
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(onClick = onToggleFilters),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier.sizeIn(minWidth = visualSize, minHeight = visualSize),
            shape = XikeShapes.button,
            border =
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            color =
                if (showFilters || activeFilterCount > 0) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription =
                        if (activeFilterCount == 0) {
                            localizedText("打开筛选")
                        } else {
                            tr(
                                "筛选，已启用 $activeFilterCount 个条件",
                                "Filters: $activeFilterCount active",
                            )
                        },
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (showFilters || activeFilterCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activeFilterCount > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        activeFilterCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    Box(
        modifier =
            modifier
                .sizeIn(minHeight = 48.dp)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            shape = XikeShapes.button,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        ) {
            Box(
                Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = labelStyle,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    color =
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
