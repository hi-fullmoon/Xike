package com.xike.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.xike.app.R
import com.xike.app.data.Mood
import com.xike.app.ui.theme.MoodIconStyle
import com.xike.app.ui.theme.XikeCurrentMoodIconStyle

/** The nearby mood label provides the spoken description; the icon is decorative. */
@Composable
internal fun MoodEmoji(
    mood: Mood,
    size: Dp,
    modifier: Modifier = Modifier,
    style: MoodIconStyle = XikeCurrentMoodIconStyle,
    emphasized: Boolean = true,
) {
    if (style == MoodIconStyle.CLASSIC) {
        val drawable =
            when (mood.score) {
                1 -> R.drawable.mood_pensive_face
                2 -> R.drawable.mood_slightly_frowning_face
                3 -> R.drawable.mood_neutral_face
                4 -> R.drawable.mood_slightly_smiling_face
                else -> R.drawable.mood_smiling_face_with_smiling_eyes
            }
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = modifier.size(size).clearAndSetSemantics {},
        )
        return
    }

    val dark = isSystemInDarkTheme()
    val semanticColor = moodVisualPalette(mood.score, dark).accent
    val neutralLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        when (style) {
            MoodIconStyle.CLASSIC -> Unit
            MoodIconStyle.DUOTONE ->
                drawFaceMoodIcon(
                    score = mood.score,
                    color = semanticColor,
                    fillAlpha = if (emphasized) 0.16f else 0.12f,
                    strokeScale = 1.65f,
                )
            MoodIconStyle.LINE ->
                drawFaceMoodIcon(
                    score = mood.score,
                    color = if (emphasized) semanticColor else neutralLineColor,
                    fillAlpha = 0f,
                    strokeScale = 1.6f,
                )
            MoodIconStyle.RING ->
                drawRingMoodIcon(
                    score = mood.score,
                    color = semanticColor,
                    emphasized = emphasized,
                )
        }
    }
}

internal data class MoodVisualPalette(
    val accent: Color,
    val container: Color,
    val border: Color,
)

internal fun moodVisualPalette(score: Int, dark: Boolean): MoodVisualPalette =
    when (score) {
        1 ->
            if (dark) {
                MoodVisualPalette(
                    accent = Color(0xFF8ABBF0),
                    container = Color(0xFF4A8AD2).copy(alpha = 0.24f),
                    border = Color(0xFF8ABBF0).copy(alpha = 0.16f),
                )
            } else {
                MoodVisualPalette(
                    accent = Color(0xFF36689E),
                    container = Color(0xFFE1EEF9),
                    border = Color(0xFF36689E).copy(alpha = 0.16f),
                )
            }
        2 ->
            if (dark) {
                MoodVisualPalette(
                    accent = Color(0xFFD4A1E6),
                    container = Color(0xFFA566BC).copy(alpha = 0.24f),
                    border = Color(0xFFD4A1E6).copy(alpha = 0.16f),
                )
            } else {
                MoodVisualPalette(
                    accent = Color(0xFF854D96),
                    container = Color(0xFFF2E6F7),
                    border = Color(0xFF854D96).copy(alpha = 0.16f),
                )
            }
        3 ->
            if (dark) {
                MoodVisualPalette(
                    accent = Color(0xFFBDB8AE),
                    container = Color(0xFFB0AA9E).copy(alpha = 0.20f),
                    border = Color(0xFFBDB8AE).copy(alpha = 0.16f),
                )
            } else {
                MoodVisualPalette(
                    accent = Color(0xFF6E6A63),
                    container = Color(0xFFEFECE7),
                    border = Color(0xFF6E6A63).copy(alpha = 0.16f),
                )
            }
        4 ->
            if (dark) {
                MoodVisualPalette(
                    accent = Color(0xFF76D4AE),
                    container = Color(0xFF3EA87E).copy(alpha = 0.24f),
                    border = Color(0xFF76D4AE).copy(alpha = 0.16f),
                )
            } else {
                MoodVisualPalette(
                    accent = Color(0xFF277557),
                    container = Color(0xFFDCF2E8),
                    border = Color(0xFF277557).copy(alpha = 0.16f),
                )
            }
        else ->
            if (dark) {
                MoodVisualPalette(
                    accent = Color(0xFFF4C16A),
                    container = Color(0xFFDE9C30).copy(alpha = 0.24f),
                    border = Color(0xFFF4C16A).copy(alpha = 0.16f),
                )
            } else {
                MoodVisualPalette(
                    accent = Color(0xFFA8620E),
                    container = Color(0xFFFDEFD2),
                    border = Color(0xFFA8620E).copy(alpha = 0.16f),
                )
            }
    }

private fun DrawScope.drawFaceMoodIcon(
    score: Int,
    color: Color,
    fillAlpha: Float,
    strokeScale: Float,
) {
    val u = size.minDimension / 24f
    val stroke = Stroke(width = strokeScale * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val center = Offset(12f * u, 12f * u)
    val radius = 9.15f * u

    if (fillAlpha > 0f) {
        drawCircle(color = color.copy(alpha = fillAlpha), radius = radius, center = center)
    }
    drawCircle(color = color, radius = radius, center = center, style = stroke)

    when (score) {
        1 -> {
            drawQuadratic(color, stroke, u, 8.2f, 10.6f, 9.3f, 11.75f, 10.4f, 10.9f)
            drawQuadratic(color, stroke, u, 15.8f, 10.6f, 14.7f, 11.75f, 13.6f, 10.9f)
            drawQuadratic(color, stroke, u, 9.3f, 16.2f, 12f, 13.9f, 14.7f, 16.2f)
        }
        2 -> {
            drawCircle(color = color, radius = 1.05f * u, center = Offset(9.2f * u, 10.4f * u))
            drawCircle(color = color, radius = 1.05f * u, center = Offset(14.8f * u, 10.4f * u))
            drawQuadratic(color, stroke, u, 9.5f, 15.7f, 12f, 14.3f, 14.5f, 15.7f)
        }
        3 -> {
            drawCircle(color = color, radius = 1.05f * u, center = Offset(9.2f * u, 10.4f * u))
            drawCircle(color = color, radius = 1.05f * u, center = Offset(14.8f * u, 10.4f * u))
            drawLine(
                color = color,
                start = Offset(9.3f * u, 15f * u),
                end = Offset(14.7f * u, 15f * u),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
        4 -> {
            drawCircle(color = color, radius = 1.05f * u, center = Offset(9.2f * u, 10.2f * u))
            drawCircle(color = color, radius = 1.05f * u, center = Offset(14.8f * u, 10.2f * u))
            drawQuadratic(color, stroke, u, 9.2f, 14.3f, 12f, 16.5f, 14.8f, 14.3f)
        }
        else -> {
            drawQuadratic(color, stroke, u, 8.1f, 10.8f, 9.5f, 9.1f, 10.9f, 10.8f)
            drawQuadratic(color, stroke, u, 13.1f, 10.8f, 14.5f, 9.1f, 15.9f, 10.8f)
            drawQuadratic(color, stroke, u, 8.7f, 14.1f, 12f, 17.4f, 15.3f, 14.1f)
        }
    }
}

private fun DrawScope.drawRingMoodIcon(score: Int, color: Color, emphasized: Boolean) {
    val u = size.minDimension / 24f
    val center = Offset(12f * u, 12f * u)
    val glowAlpha = if (emphasized) 0.18f else 0.11f

    when (score) {
        1 -> {
            drawCircle(
                color = color,
                radius = 4.6f * u,
                center = center,
                style = Stroke(width = 1.65f * u, cap = StrokeCap.Round),
            )
        }
        2 -> {
            drawCircle(
                color = color,
                radius = 8.4f * u,
                center = center,
                style = Stroke(width = 1.55f * u),
            )
            drawCircle(
                color = color.copy(alpha = 0.55f),
                radius = 5.4f * u,
                center = center,
                style = Stroke(width = 1.25f * u),
            )
            drawCircle(color = color.copy(alpha = 0.72f), radius = 2.4f * u, center = center)
        }
        3 -> {
            drawCircle(
                color = color,
                radius = 8.4f * u,
                center = center,
                style = Stroke(width = 1.55f * u),
            )
            drawCircle(
                color = color.copy(alpha = 0.65f),
                radius = 5.4f * u,
                center = center,
                style = Stroke(width = 1.35f * u),
            )
            drawCircle(color = color.copy(alpha = 0.82f), radius = 2.6f * u, center = center)
        }
        4 -> {
            drawCircle(color = color.copy(alpha = glowAlpha), radius = 8.6f * u, center = center)
            drawCircle(
                color = color,
                radius = 8.4f * u,
                center = center,
                style = Stroke(width = 1.6f * u),
            )
            drawCircle(
                color = color.copy(alpha = 0.75f),
                radius = 5.4f * u,
                center = center,
                style = Stroke(width = 1.4f * u),
            )
            drawCircle(color = color, radius = 2.8f * u, center = center)
        }
        else -> {
            drawCircle(
                color = color.copy(alpha = glowAlpha + 0.04f),
                radius = 10.6f * u,
                center = center,
            )
            drawCircle(
                color = color,
                radius = 8.4f * u,
                center = center,
                style = Stroke(width = 1.65f * u),
            )
            drawCircle(
                color = color.copy(alpha = 0.78f),
                radius = 5.4f * u,
                center = center,
                style = Stroke(width = 1.45f * u),
            )
            drawCircle(color = color, radius = 2.6f * u, center = center)
        }
    }
}

private fun DrawScope.drawQuadratic(
    color: Color,
    stroke: Stroke,
    unit: Float,
    x0: Float,
    y0: Float,
    cx: Float,
    cy: Float,
    x1: Float,
    y1: Float,
) {
    val path =
        Path().apply {
            moveTo(x0 * unit, y0 * unit)
            quadraticTo(cx * unit, cy * unit, x1 * unit, y1 * unit)
        }
    drawPath(path = path, color = color, style = stroke)
}
