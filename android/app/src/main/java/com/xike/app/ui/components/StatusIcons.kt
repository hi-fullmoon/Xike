package com.xike.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.xike.app.data.Feeling
import com.xike.app.ui.components.journal.weatherConditionIcon
import com.xike.app.ui.theme.MoodIconStyle
import com.xike.app.ui.theme.XikeCurrentMoodIconStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun FeelingIcon(
    feeling: Feeling,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    style: MoodIconStyle = XikeCurrentMoodIconStyle,
) {
    if (style == MoodIconStyle.CLASSIC) return
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        val u = this.size.minDimension / 24f
        val fillAlpha =
            when (style) {
                MoodIconStyle.DUOTONE -> 0.22f
                MoodIconStyle.RING -> 0.16f
                else -> 0f
            }
        val stroke =
            Stroke(
                width = (if (style == MoodIconStyle.LINE) 1.75f else 1.7f) * u,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        if (style == MoodIconStyle.RING) {
            drawRingFeelingGlyph(feeling, color, u, fillAlpha)
            return@Canvas
        }
        when (feeling) {
            Feeling.HAPPY,
            Feeling.EXCITED,
            Feeling.HOPEFUL,
            Feeling.ENERGETIC -> drawSparkleGlyph(color, stroke, u, fillAlpha)
            Feeling.PEACEFUL,
            Feeling.RELAXED,
            Feeling.SAFE,
            Feeling.CONTENT,
            Feeling.GRATEFUL -> drawLeafGlyph(color, stroke, u, fillAlpha)
            Feeling.SAD,
            Feeling.DISAPPOINTED,
            Feeling.HURT,
            Feeling.LONELY -> drawDropletGlyph(color, stroke, u, fillAlpha)
            Feeling.ANXIOUS,
            Feeling.IRRITATED,
            Feeling.ANGRY -> drawWaveDiamondGlyph(color, stroke, u, fillAlpha)
            Feeling.TIRED,
            Feeling.BORED -> drawCrescentGlyph(color, stroke, u, fillAlpha)
        }
    }
}

@Composable
internal fun OutdoorWeatherIcon(
    weatherCode: Int,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    style: MoodIconStyle = XikeCurrentMoodIconStyle,
) {
    if (style == MoodIconStyle.CLASSIC) {
        Icon(
            imageVector = weatherConditionIcon(weatherCode),
            contentDescription = null,
            modifier = modifier.size(size),
            tint = tint,
        )
        return
    }
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        val u = this.size.minDimension / 24f
        val fillAlpha =
            when (style) {
                MoodIconStyle.DUOTONE -> 0.20f
                MoodIconStyle.RING -> 0.14f
                else -> 0f
            }
        val stroke =
            Stroke(
                width = (if (style == MoodIconStyle.LINE) 1.6f else 1.65f) * u,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        when (weatherCode) {
            0 -> drawSunWeather(tint, stroke, u, fillAlpha, ringGlow = style == MoodIconStyle.RING)
            1,
            2 -> drawPartlyCloudyWeather(tint, stroke, u, fillAlpha)
            3 -> drawCloudWeather(tint, stroke, u, fillAlpha)
            45,
            48 -> drawFogWeather(tint, stroke, u, fillAlpha)
            51,
            53,
            55,
            56,
            57,
            61,
            63,
            65,
            66,
            67,
            80,
            81,
            82 -> drawRainWeather(tint, stroke, u, fillAlpha)
            71,
            73,
            75,
            77,
            85,
            86 -> drawSnowWeather(tint, stroke, u, fillAlpha)
            95,
            96,
            99 -> drawStormWeather(tint, stroke, u, fillAlpha)
            else -> drawUnknownWeather(tint, stroke, u, fillAlpha)
        }
    }
}

private fun DrawScope.drawRingFeelingGlyph(
    feeling: Feeling,
    color: Color,
    u: Float,
    fillAlpha: Float,
) {
    val center = Offset(12f * u, 12f * u)
    when (feeling) {
        Feeling.HAPPY,
        Feeling.EXCITED,
        Feeling.HOPEFUL,
        Feeling.ENERGETIC -> {
            drawCircle(color.copy(alpha = fillAlpha), radius = 9.2f * u, center = center)
            drawCircle(color, radius = 6.2f * u, center = center, style = Stroke(1.6f * u))
            drawCircle(color, radius = 2.4f * u, center = center)
        }
        Feeling.PEACEFUL,
        Feeling.RELAXED,
        Feeling.SAFE,
        Feeling.CONTENT,
        Feeling.GRATEFUL -> {
            drawCircle(color.copy(alpha = fillAlpha), radius = 8.4f * u, center = center)
            drawCircle(color, radius = 8.0f * u, center = center, style = Stroke(1.6f * u))
            drawCircle(color.copy(alpha = 0.65f), radius = 3.8f * u, center = center)
        }
        Feeling.SAD,
        Feeling.DISAPPOINTED,
        Feeling.HURT,
        Feeling.LONELY -> {
            drawCircle(
                color = color,
                radius = 5.2f * u,
                center = Offset(12f * u, 14f * u),
                style = Stroke(1.65f * u),
            )
        }
        Feeling.ANXIOUS,
        Feeling.IRRITATED,
        Feeling.ANGRY -> {
            drawCircle(color, radius = 8.0f * u, center = center, style = Stroke(1.6f * u))
            drawCircle(
                color.copy(alpha = 0.72f),
                radius = 4.5f * u,
                center = center,
                style = Stroke(1.4f * u),
            )
        }
        Feeling.TIRED,
        Feeling.BORED -> {
            drawCircle(color.copy(alpha = fillAlpha), radius = 7.4f * u, center = center)
            drawCircle(
                color.copy(alpha = 0.8f),
                radius = 7.4f * u,
                center = center,
                style = Stroke(1.55f * u),
            )
            drawLine(
                color = color,
                start = Offset(8.5f * u, 12f * u),
                end = Offset(15.5f * u, 12f * u),
                strokeWidth = 1.6f * u,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawSparkleGlyph(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val path =
        Path().apply {
            moveTo(12f * u, 3.8f * u)
            quadraticTo(13.2f * u, 10.8f * u, 20.2f * u, 12f * u)
            quadraticTo(13.2f * u, 13.2f * u, 12f * u, 20.2f * u)
            quadraticTo(10.8f * u, 13.2f * u, 3.8f * u, 12f * u)
            quadraticTo(10.8f * u, 10.8f * u, 12f * u, 3.8f * u)
            close()
        }
    if (fillAlpha > 0f) drawPath(path, color.copy(alpha = fillAlpha))
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawLeafGlyph(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val path =
        Path().apply {
            moveTo(19f * u, 5f * u)
            cubicTo(11f * u, 5f * u, 6f * u, 9.5f * u, 6f * u, 17.5f * u)
            cubicTo(14f * u, 17.5f * u, 19f * u, 13f * u, 19f * u, 5f * u)
            close()
        }
    if (fillAlpha > 0f) drawPath(path, color.copy(alpha = fillAlpha))
    drawPath(path, color, style = stroke)
    drawLine(
        color = color,
        start = Offset(5.5f * u, 18.5f * u),
        end = Offset(13.5f * u, 10.5f * u),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawDropletGlyph(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val path =
        Path().apply {
            moveTo(12f * u, 4.2f * u)
            cubicTo(12f * u, 4.2f * u, 6.2f * u, 10.8f * u, 6.2f * u, 14.6f * u)
            cubicTo(6.2f * u, 17.8f * u, 8.8f * u, 20f * u, 12f * u, 20f * u)
            cubicTo(15.2f * u, 20f * u, 17.8f * u, 17.8f * u, 17.8f * u, 14.6f * u)
            cubicTo(17.8f * u, 10.8f * u, 12f * u, 4.2f * u, 12f * u, 4.2f * u)
            close()
        }
    if (fillAlpha > 0f) drawPath(path, color.copy(alpha = fillAlpha))
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawWaveDiamondGlyph(
    color: Color,
    stroke: Stroke,
    u: Float,
    fillAlpha: Float,
) {
    val path =
        Path().apply {
            moveTo(12f * u, 4.5f * u)
            lineTo(19.5f * u, 12f * u)
            lineTo(12f * u, 19.5f * u)
            lineTo(4.5f * u, 12f * u)
            close()
        }
    if (fillAlpha > 0f) drawPath(path, color.copy(alpha = fillAlpha))
    drawPath(path, color, style = stroke)
    drawCircle(color = color, radius = 1.6f * u, center = Offset(12f * u, 12f * u))
}

private fun DrawScope.drawCrescentGlyph(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val path =
        Path().apply {
            moveTo(18.2f * u, 14.8f * u)
            cubicTo(16.6f * u, 17.8f * u, 13.0f * u, 19.4f * u, 9.6f * u, 18.2f * u)
            cubicTo(6.1f * u, 17.0f * u, 4.5f * u, 13.1f * u, 5.8f * u, 9.6f * u)
            cubicTo(6.6f * u, 7.3f * u, 8.6f * u, 5.7f * u, 11.0f * u, 5.3f * u)
            cubicTo(9.4f * u, 8.2f * u, 10.2f * u, 11.9f * u, 12.9f * u, 13.8f * u)
            cubicTo(14.5f * u, 14.9f * u, 16.4f * u, 15.2f * u, 18.2f * u, 14.8f * u)
            close()
        }
    if (fillAlpha > 0f) drawPath(path, color.copy(alpha = fillAlpha))
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawSunWeather(
    color: Color,
    stroke: Stroke,
    u: Float,
    fillAlpha: Float,
    ringGlow: Boolean,
) {
    val center = Offset(12f * u, 12f * u)
    if (ringGlow) {
        drawCircle(color.copy(alpha = 0.14f), radius = 9.6f * u, center = center)
    }
    if (fillAlpha > 0f)
        drawCircle(color.copy(alpha = fillAlpha), radius = 4.6f * u, center = center)
    drawCircle(color, radius = 4.6f * u, center = center, style = stroke)
    for (i in 0 until 8) {
        val angle = i * PI / 4.0
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            color = color,
            start = Offset((12f + dx * 7.2f) * u, (12f + dy * 7.2f) * u),
            end = Offset((12f + dx * 9.3f) * u, (12f + dy * 9.3f) * u),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.cloudPath(u: Float, offsetY: Float = 0f, scale: Float = 1f): Path {
    fun x(v: Float) = (12f + (v - 12f) * scale) * u
    fun y(v: Float) = (12f + (v - 12f) * scale + offsetY) * u
    return Path().apply {
        moveTo(x(7.4f), y(17.5f))
        lineTo(x(16.6f), y(17.5f))
        cubicTo(x(19.2f), y(17.5f), x(20.6f), y(15.6f), x(20.4f), y(13.4f))
        cubicTo(x(20.2f), y(11.3f), x(18.5f), y(10.0f), x(16.4f), y(10.1f))
        cubicTo(x(15.7f), y(7.5f), x(13.4f), y(6.0f), x(10.8f), y(6.4f))
        cubicTo(x(8.4f), y(6.8f), x(6.8f), y(8.7f), x(6.8f), y(11.0f))
        cubicTo(x(4.9f), y(11.3f), x(3.7f), y(12.8f), x(3.9f), y(14.7f))
        cubicTo(x(4.1f), y(16.4f), x(5.5f), y(17.5f), x(7.4f), y(17.5f))
        close()
    }
}

private fun DrawScope.drawPartlyCloudyWeather(
    color: Color,
    stroke: Stroke,
    u: Float,
    fillAlpha: Float,
) {
    val sunCenter = Offset(15.8f * u, 8.4f * u)
    if (fillAlpha > 0f) {
        drawCircle(color.copy(alpha = fillAlpha), radius = 3.2f * u, center = sunCenter)
    }
    drawCircle(color, radius = 3.2f * u, center = sunCenter, style = stroke)
    listOf(-135.0, -90.0, -45.0, 0.0).forEach { deg ->
        val rad = deg * PI / 180.0
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        drawLine(
            color = color,
            start = Offset((15.8f + dx * 5.0f) * u, (8.4f + dy * 5.0f) * u),
            end = Offset((15.8f + dx * 6.3f) * u, (8.4f + dy * 6.3f) * u),
            strokeWidth = stroke.width * 0.9f,
            cap = StrokeCap.Round,
        )
    }
    val cloud = cloudPath(u, offsetY = 2.0f, scale = 0.88f)
    if (fillAlpha > 0f) drawPath(cloud, color.copy(alpha = fillAlpha))
    drawPath(cloud, color, style = stroke)
}

private fun DrawScope.drawCloudWeather(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val cloud = cloudPath(u, offsetY = 0.5f, scale = 0.96f)
    if (fillAlpha > 0f) drawPath(cloud, color.copy(alpha = fillAlpha))
    drawPath(cloud, color, style = stroke)
}

private fun DrawScope.drawFogWeather(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val cloud = cloudPath(u, offsetY = -2.2f, scale = 0.82f)
    if (fillAlpha > 0f) drawPath(cloud, color.copy(alpha = fillAlpha))
    drawPath(cloud, color, style = stroke)
    drawLine(
        color = color.copy(alpha = 0.85f),
        start = Offset(5.5f * u, 17.5f * u),
        end = Offset(18.5f * u, 17.5f * u),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color.copy(alpha = 0.6f),
        start = Offset(7.5f * u, 20.3f * u),
        end = Offset(16.5f * u, 20.3f * u),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawRainWeather(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val cloud = cloudPath(u, offsetY = -2.4f, scale = 0.84f)
    if (fillAlpha > 0f) drawPath(cloud, color.copy(alpha = fillAlpha))
    drawPath(cloud, color, style = stroke)
    listOf(8.6f, 12.2f, 15.8f).forEachIndexed { index, x ->
        val top = if (index == 1) 17.2f else 16.4f
        drawLine(
            color = color,
            start = Offset(x * u, top * u),
            end = Offset((x - 1.1f) * u, (top + 3.2f) * u),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnowWeather(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val cloud = cloudPath(u, offsetY = -2.4f, scale = 0.84f)
    if (fillAlpha > 0f) drawPath(cloud, color.copy(alpha = fillAlpha))
    drawPath(cloud, color, style = stroke)
    listOf(Offset(8.4f, 18.0f), Offset(12.0f, 19.6f), Offset(15.6f, 18.0f)).forEach { pt ->
        drawCircle(color = color, radius = 1.15f * u, center = Offset(pt.x * u, pt.y * u))
    }
}

private fun DrawScope.drawStormWeather(color: Color, stroke: Stroke, u: Float, fillAlpha: Float) {
    val cloud = cloudPath(u, offsetY = -2.4f, scale = 0.84f)
    if (fillAlpha > 0f) drawPath(cloud, color.copy(alpha = fillAlpha))
    drawPath(cloud, color, style = stroke)
    val bolt =
        Path().apply {
            moveTo(12.6f * u, 14.5f * u)
            lineTo(10.0f * u, 18.2f * u)
            lineTo(12.8f * u, 18.2f * u)
            lineTo(11.0f * u, 21.4f * u)
        }
    drawPath(bolt, color, style = stroke)
}

private fun DrawScope.drawUnknownWeather(
    color: Color,
    stroke: Stroke,
    u: Float,
    fillAlpha: Float,
) {
    val center = Offset(12f * u, 12f * u)
    if (fillAlpha > 0f)
        drawCircle(color.copy(alpha = fillAlpha), radius = 8.4f * u, center = center)
    drawCircle(color, radius = 8.4f * u, center = center, style = stroke)
    drawCircle(color, radius = 1.2f * u, center = Offset(12f * u, 16.0f * u))
    val q =
        Path().apply {
            moveTo(9.8f * u, 9.8f * u)
            cubicTo(9.8f * u, 8.2f * u, 11.0f * u, 7.4f * u, 12.2f * u, 7.6f * u)
            cubicTo(13.6f * u, 7.8f * u, 14.3f * u, 9.0f * u, 13.5f * u, 10.4f * u)
            cubicTo(12.8f * u, 11.4f * u, 12.0f * u, 11.8f * u, 12.0f * u, 13.0f * u)
        }
    drawPath(q, color, style = stroke)
}
