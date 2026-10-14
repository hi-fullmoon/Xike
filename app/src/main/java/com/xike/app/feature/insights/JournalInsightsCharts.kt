package com.xike.app.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.components.MoodEmoji
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeShapes
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
internal fun TrendCard(
    summary: JournalPeriodSummary,
    today: LocalDate,
    onPointClick: (MoodTrendPoint) -> Unit,
) {
    InsightSectionCard(
        icon = Icons.AutoMirrored.Outlined.ShowChart,
        index = localizedText("趋势"),
        title = summary.period.trendTitle,
        trailing = if (summary.period == InsightsPeriod.YEAR) localizedText("按月") else null,
    ) {
        if (!summary.evidence.canDescribePatterns) {
            InsufficientDataNote(summary.evidence.level.description)
            Spacer(Modifier.height(12.dp))
        }
        Surface(
            shape = XikeShapes.inner,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
        ) {
            MoodTrendChart(summary.trendPoints, today, onPointClick)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            localizedText("折线表示五档心情的平均位置；没有记录的时段会断开。点按时间段可查看原始记录。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MoodTrendChart(
    points: List<MoodTrendPoint>,
    today: LocalDate,
    onPointClick: (MoodTrendPoint) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val axisLabels = listOf(localizedText("愉悦"), localizedText("平静"), localizedText("低落"))
    val axisStyle = MaterialTheme.typography.labelMedium
    val pointStyle = axisStyle
    val axisLabelHeight =
        with(density) {
            axisLabels
                .maxOf { textMeasurer.measure(it, axisStyle, softWrap = false).size.height }
                .toDp()
        }
    val chartHeight = maxOf(142.dp, axisLabelHeight * 3 + 12.dp)
    val plotTop = maxOf(16.dp, axisLabelHeight / 2 + 6.dp)
    val plotBottom = chartHeight - maxOf(14.dp, axisLabelHeight / 2 + 6.dp)
    val scrollState = rememberScrollState()
    var renderedAxisWidth by remember(axisLabels, axisStyle, density) { mutableStateOf(0.dp) }
    var renderedPointWidth by
        remember(points.map { it.label }, pointStyle, density) { mutableStateOf(0.dp) }
    val axisWidth =
        with(density) {
            maxOf(
                renderedAxisWidth,
                if (fontScale >= 1.5f) 52.dp else 36.dp,
                axisLabels
                    .maxOf {
                        textMeasurer
                            .measure(
                                it,
                                axisStyle,
                                softWrap = false,
                                maxLines = 1,
                                density = density,
                            )
                            .multiParagraph
                            .maxIntrinsicWidth
                    }
                    .toDp() + 8.dp,
            )
        }
    val pointWidth =
        with(density) {
            maxOf(
                48.dp,
                renderedPointWidth,
                (points.maxOfOrNull {
                        textMeasurer
                            .measure(
                                it.label,
                                pointStyle.copy(fontWeight = FontWeight.Bold),
                                softWrap = false,
                                maxLines = 1,
                                density = density,
                            )
                            .multiParagraph
                            .maxIntrinsicWidth
                    } ?: 0f)
                    .toDp() + 8.dp,
            )
        }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(XikeInnerCardPadding)) {
        val viewportWidth = (maxWidth - axisWidth).coerceAtLeast(0.dp)
        val plotWidth = maxOf(viewportWidth, pointWidth * points.size)
        LaunchedEffect(
            points.map { it.startDate to it.endDateExclusive },
            today,
            plotWidth,
            viewportWidth,
            density,
        ) {
            val currentIndex = points.indexOfFirst {
                !today.isBefore(it.startDate) && today.isBefore(it.endDateExclusive)
            }
            if (currentIndex >= 0 && viewportWidth > 0.dp) {
                withFrameNanos {}
                val target =
                    with(density) {
                        (plotWidth / points.size * (currentIndex + 0.5f) - viewportWidth / 2)
                            .toPx()
                            .roundToInt()
                    }
                scrollState.scrollTo(target.coerceAtLeast(0))
            }
        }
        Box(Modifier.width(axisWidth).height(chartHeight)) {
            axisLabels.forEachIndexed { index, label ->
                val tickPosition = plotTop + (plotBottom - plotTop) * (index / 2f)
                Text(
                    label,
                    modifier =
                        Modifier.offset(y = tickPosition - axisLabelHeight / 2)
                            .height(axisLabelHeight),
                    style = axisStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { layout ->
                        val width =
                            with(density) { layout.multiParagraph.maxIntrinsicWidth.toDp() } + 8.dp
                        if (width > renderedAxisWidth) renderedAxisWidth = width
                    },
                )
            }
        }
        Column(Modifier.padding(start = axisWidth).horizontalScroll(scrollState).width(plotWidth)) {
            Box(Modifier.fillMaxWidth().height(chartHeight).testTag("mood-trend-plot")) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (points.isEmpty()) return@Canvas
                        val step = size.width / points.size
                        val top = plotTop.toPx()
                        val bottom = plotBottom.toPx()
                        fun position(index: Int, score: Double): Offset =
                            Offset(
                                x = step * (index + 0.5f),
                                y =
                                    bottom -
                                        ((score.coerceIn(1.0, 5.0) - 1.0) / 4.0).toFloat() *
                                            (bottom - top),
                            )
                        listOf(1.0, 3.0, 5.0).forEach { score ->
                            val y = position(0, score).y
                            drawLine(
                                guide,
                                Offset(0f, y),
                                Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        var runStart = 0
                        while (runStart < points.size) {
                            if (points[runStart].averageScore == null) {
                                runStart++
                                continue
                            }
                            var runEnd = runStart
                            while (
                                runEnd + 1 < points.size && points[runEnd + 1].averageScore != null
                            ) runEnd++
                            if (runEnd > runStart) {
                                val first =
                                    position(
                                        runStart,
                                        requireNotNull(points[runStart].averageScore),
                                    )
                                val path = Path().apply { moveTo(first.x, first.y) }
                                for (index in runStart until runEnd) {
                                    val from =
                                        position(index, requireNotNull(points[index].averageScore))
                                    val to =
                                        position(
                                            index + 1,
                                            requireNotNull(points[index + 1].averageScore),
                                        )
                                    val midX = (from.x + to.x) / 2f
                                    path.cubicTo(midX, from.y, midX, to.y, to.x, to.y)
                                }
                                val area =
                                    Path().apply {
                                        addPath(path)
                                        lineTo(
                                            position(
                                                    runEnd,
                                                    requireNotNull(points[runEnd].averageScore),
                                                )
                                                .x,
                                            bottom,
                                        )
                                        lineTo(first.x, bottom)
                                        close()
                                    }
                                drawPath(
                                    area,
                                    Brush.verticalGradient(
                                        listOf(
                                            primary.copy(alpha = 0.18f),
                                            primary.copy(alpha = 0.01f),
                                        )
                                    ),
                                )
                                drawPath(
                                    path,
                                    primary.copy(alpha = 0.8f),
                                    style = Stroke(width = 2.5.dp.toPx()),
                                )
                            }
                            runStart = runEnd + 1
                        }
                        points.forEachIndexed { index, point ->
                            val score = point.averageScore
                            if (score != null) {
                                val current =
                                    !today.isBefore(point.startDate) &&
                                        today.isBefore(point.endDateExclusive)
                                val center = position(index, score)
                                if (current)
                                    drawCircle(
                                        primary.copy(alpha = 0.15f),
                                        radius = 10.dp.toPx(),
                                        center = center,
                                    )
                                drawCircle(
                                    primary,
                                    radius = if (current) 5.dp.toPx() else 4.dp.toPx(),
                                    center = center,
                                )
                                drawCircle(Color.White, radius = 1.7.dp.toPx(), center = center)
                            }
                        }
                    }
                    Row(Modifier.fillMaxSize()) {
                        points.forEach { point ->
                            val description = buildString {
                                append(point.dateRangeLabel())
                                append(
                                    tr("，${point.entryCount} 条记录", ", ${point.entryCount} entries")
                                )
                                point.averageScore?.let {
                                    append(
                                        tr(
                                            "，心情平均位置 ${it.oneDecimal()}",
                                            ", average mood position ${it.oneDecimal()}",
                                        )
                                    )
                                }
                            }
                            Box(
                                Modifier.weight(1f)
                                    .fillMaxSize()
                                    .semantics { contentDescription = description }
                                    .clickable(enabled = point.entryCount > 0) {
                                        onPointClick(point)
                                    }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                points.forEachIndexed { index, point ->
                    val current =
                        !today.isBefore(point.startDate) && today.isBefore(point.endDateExclusive)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (points.size <= 9 || index % 2 == 0 || current) {
                            Text(
                                point.label,
                                style = pointStyle,
                                color =
                                    if (current) primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                onTextLayout = { layout ->
                                    val width =
                                        with(density) {
                                            layout.multiParagraph.maxIntrinsicWidth.toDp()
                                        } + 8.dp
                                    if (width > renderedPointWidth) renderedPointWidth = width
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MoodDistributionCard(
    distribution: List<MoodDistributionItem>,
    onClick: (MoodDistributionItem) -> Unit,
) {
    InsightSectionCard(
        icon = Icons.Outlined.DataUsage,
        index = localizedText("分布"),
        title = localizedText("心情出现次数"),
    ) {
        distribution
            .sortedByDescending { it.mood.score }
            .forEach { item ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = item.entryCount > 0) { onClick(item) }
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoodEmoji(item.mood, size = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val label = item.mood.label
                            val statistics =
                                tr(
                                    "${item.entryCount} 次 · ${(item.ratio * 100).roundToInt()}%",
                                    "${item.entryCount} entries · ${(item.ratio * 100).roundToInt()}%",
                                )
                            val labelStyle = MaterialTheme.typography.labelLarge
                            val statisticsStyle = MaterialTheme.typography.labelSmall
                            val textMeasurer = rememberTextMeasurer()
                            val requiredWidth =
                                with(LocalDensity.current) {
                                    (textMeasurer
                                            .measure(label, labelStyle, softWrap = false)
                                            .size
                                            .width +
                                            textMeasurer
                                                .measure(
                                                    statistics,
                                                    statisticsStyle,
                                                    softWrap = false,
                                                )
                                                .size
                                                .width)
                                        .toDp()
                                } + 8.dp
                            if (requiredWidth > maxWidth) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, style = labelStyle)
                                    Text(
                                        statistics,
                                        style = statisticsStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, style = labelStyle)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        statistics,
                                        style = statisticsStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        RatioBar(
                            item.ratio,
                            tr(
                                "${item.mood.label}占 ${(item.ratio * 100).roundToInt()}%，共 ${item.entryCount} 条",
                                "${item.mood.label}: ${(item.ratio * 100).roundToInt()}%, ${item.entryCount} entries",
                            ),
                        )
                    }
                    if (item.entryCount > 0) {
                        Spacer(Modifier.width(7.dp))
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
    }
}
