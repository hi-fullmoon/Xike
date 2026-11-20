package com.xike.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.xike.app.localizedText
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeNavigationCorner
import com.xike.app.ui.theme.XikeNavigationTreatment
import com.xike.app.ui.theme.XikeNavigationTreatmentValue

enum class AppScreen(private val sourceTitle: String) {
    HOME("此刻"),
    INSIGHTS("轨迹"),
    ARCHIVE("回望"),
    SETTINGS("设置");

    val title: String
        get() = localizedText(sourceTitle)
}

@Composable
fun XikeNavigationBar(selected: AppScreen, onSelected: (AppScreen) -> Unit) {
    val treatment = XikeNavigationTreatmentValue
    Surface(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        color =
            when (treatment) {
                XikeNavigationTreatment.CAPSULE -> MaterialTheme.colorScheme.surfaceContainerLow
                XikeNavigationTreatment.UNDERLINE -> MaterialTheme.colorScheme.surface
                XikeNavigationTreatment.SOLID -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
        tonalElevation = 0.dp,
    ) {
        Column {
            if (treatment == XikeNavigationTreatment.UNDERLINE) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val preferredPadding =
                    if (treatment == XikeNavigationTreatment.SOLID) 10.dp else 14.dp
                val viewportWidth = minOf(maxWidth, XikeContentMaxWidth)
                val density = LocalDensity.current
                val selectedItemRequester = remember { BringIntoViewRequester() }
                val textMeasurer = rememberTextMeasurer()
                val labelStyle =
                    MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                val itemWidths =
                    with(density) {
                        AppScreen.entries.map { item ->
                            maxOf(
                                48.dp,
                                textMeasurer
                                    .measure(item.title, labelStyle, softWrap = false)
                                    .size
                                    .width
                                    .toDp() + 8.dp,
                            )
                        }
                    }
                val labelsWidth = itemWidths.fold(0.dp) { total, width -> total + width }
                val horizontalPadding =
                    minOf(preferredPadding, maxOf(0.dp, (viewportWidth - labelsWidth) / 2))
                val availableWidth = viewportWidth - horizontalPadding * 2
                val equalWidths = itemWidths.max() * AppScreen.entries.size <= availableWidth
                val requiredWidth = labelsWidth + horizontalPadding * 2
                LaunchedEffect(
                    selected,
                    viewportWidth,
                    itemWidths,
                    horizontalPadding,
                    density,
                    treatment,
                ) {
                    withFrameNanos {}
                    selectedItemRequester.bringIntoView()
                }
                Row(
                    modifier =
                        Modifier.width(viewportWidth)
                            .align(Alignment.Center)
                            .horizontalScroll(rememberScrollState())
                            .width(maxOf(viewportWidth, requiredWidth))
                            .height(IntrinsicSize.Min)
                            .padding(
                                horizontal = horizontalPadding,
                                vertical =
                                    if (treatment == XikeNavigationTreatment.UNDERLINE) 4.dp
                                    else 6.dp,
                            )
                            .selectableGroup()
                ) {
                    AppScreen.entries.forEachIndexed { index, item ->
                        val isSelected = selected == item
                        val selectedBackground =
                            when (treatment) {
                                XikeNavigationTreatment.CAPSULE ->
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                XikeNavigationTreatment.UNDERLINE -> Color.Transparent
                                XikeNavigationTreatment.SOLID -> MaterialTheme.colorScheme.primary
                            }
                        val selectedForeground =
                            if (treatment == XikeNavigationTreatment.SOLID) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        Column(
                            modifier =
                                Modifier.weight(if (equalWidths) 1f else itemWidths[index].value)
                                    .fillMaxHeight()
                                    .testTag("navigation-${item.name.lowercase()}")
                                    .then(
                                        if (isSelected)
                                            Modifier.bringIntoViewRequester(selectedItemRequester)
                                        else Modifier
                                    )
                                    .clip(RoundedCornerShape(XikeNavigationCorner))
                                    .background(
                                        if (isSelected) selectedBackground else Color.Transparent
                                    )
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.Tab,
                                        onClick = { onSelected(item) },
                                    )
                                    .padding(
                                        vertical =
                                            if (treatment == XikeNavigationTreatment.SOLID) 8.dp
                                            else 6.dp
                                    ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector =
                                    when (item) {
                                        AppScreen.HOME -> XikeIcons.Moment
                                        AppScreen.INSIGHTS -> XikeIcons.Insights
                                        AppScreen.ARCHIVE -> XikeIcons.Archive
                                        AppScreen.SETTINGS -> XikeIcons.Settings
                                    },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint =
                                    if (isSelected) selectedForeground
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.title,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color =
                                    if (isSelected) selectedForeground
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (treatment == XikeNavigationTreatment.UNDERLINE) {
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    Modifier.width(26.dp)
                                        .height(2.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
