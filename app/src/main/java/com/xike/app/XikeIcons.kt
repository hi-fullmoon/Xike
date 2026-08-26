package com.xike.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A small, purpose-built icon family for Xike. Rounded strokes and a shared,
 * softly organic face outline keep the mood set calm and visually consistent.
 */
object XikeIcons {
    val Mark: ImageVector by lazy {
        outlineIcon("XikeMark") {
            moveTo(17.8f, 6.2f)
            curveTo(15.9f, 4.6f, 13.3f, 3.8f, 10.8f, 4.2f)
            curveTo(7.0f, 4.8f, 4.2f, 8.1f, 4.2f, 12.0f)
            curveTo(4.2f, 16.3f, 7.7f, 19.8f, 12.0f, 19.8f)
            curveTo(15.9f, 19.8f, 19.2f, 16.9f, 19.8f, 13.1f)
            moveTo(10.0f, 9.0f)
            lineTo(10.0f, 15.0f)
            moveTo(14.0f, 8.0f)
            lineTo(14.0f, 14.0f)
        }
    }

    val Moment: ImageVector by lazy {
        outlineIcon("XikeMoment") {
            moveTo(18.6f, 7.0f)
            curveTo(16.9f, 4.9f, 14.2f, 3.8f, 11.5f, 4.0f)
            curveTo(7.2f, 4.3f, 3.9f, 7.9f, 4.0f, 12.2f)
            curveTo(4.1f, 16.5f, 7.7f, 19.9f, 12.0f, 19.9f)
            curveTo(16.0f, 19.9f, 19.4f, 16.9f, 19.9f, 13.0f)
            moveTo(12.0f, 9.1f)
            curveTo(10.4f, 9.1f, 9.1f, 10.4f, 9.1f, 12.0f)
            curveTo(9.1f, 13.6f, 10.4f, 14.9f, 12.0f, 14.9f)
            curveTo(13.6f, 14.9f, 14.9f, 13.6f, 14.9f, 12.0f)
        }
    }

    val Insights: ImageVector by lazy {
        outlineIcon("XikeInsights") {
            moveTo(5.0f, 18.8f)
            lineTo(5.0f, 13.8f)
            curveTo(5.0f, 12.9f, 5.7f, 12.2f, 6.6f, 12.2f)
            curveTo(7.5f, 12.2f, 8.2f, 12.9f, 8.2f, 13.8f)
            lineTo(8.2f, 18.8f)
            moveTo(10.4f, 18.8f)
            lineTo(10.4f, 9.9f)
            curveTo(10.4f, 9.0f, 11.1f, 8.3f, 12.0f, 8.3f)
            curveTo(12.9f, 8.3f, 13.6f, 9.0f, 13.6f, 9.9f)
            lineTo(13.6f, 18.8f)
            moveTo(15.8f, 18.8f)
            lineTo(15.8f, 6.0f)
            curveTo(15.8f, 5.1f, 16.5f, 4.4f, 17.4f, 4.4f)
            curveTo(18.3f, 4.4f, 19.0f, 5.1f, 19.0f, 6.0f)
            lineTo(19.0f, 18.8f)
            moveTo(3.8f, 19.0f)
            lineTo(20.2f, 19.0f)
        }
    }

    val Archive: ImageVector by lazy {
        outlineIcon("XikeArchive") {
            moveTo(5.0f, 7.0f)
            curveTo(5.0f, 5.9f, 5.9f, 5.0f, 7.0f, 5.0f)
            lineTo(17.0f, 5.0f)
            curveTo(18.1f, 5.0f, 19.0f, 5.9f, 19.0f, 7.0f)
            lineTo(19.0f, 16.0f)
            curveTo(19.0f, 17.1f, 18.1f, 18.0f, 17.0f, 18.0f)
            lineTo(7.0f, 18.0f)
            curveTo(5.9f, 18.0f, 5.0f, 17.1f, 5.0f, 16.0f)
            close()
            moveTo(8.0f, 9.0f)
            lineTo(16.0f, 9.0f)
            moveTo(8.0f, 12.0f)
            lineTo(14.0f, 12.0f)
            moveTo(8.0f, 15.0f)
            lineTo(12.0f, 15.0f)
            moveTo(8.0f, 3.5f)
            lineTo(16.0f, 3.5f)
        }
    }

    val Settings: ImageVector by lazy {
        outlineIcon("XikeSettings") {
            moveTo(4.0f, 7.0f)
            lineTo(20.0f, 7.0f)
            moveTo(4.0f, 12.0f)
            lineTo(20.0f, 12.0f)
            moveTo(4.0f, 17.0f)
            lineTo(20.0f, 17.0f)
            moveTo(8.0f, 5.0f)
            lineTo(8.0f, 9.0f)
            moveTo(16.0f, 10.0f)
            lineTo(16.0f, 14.0f)
            moveTo(10.0f, 15.0f)
            lineTo(10.0f, 19.0f)
        }
    }

    val MoodLow: ImageVector by lazy {
        outlineIcon("XikeMoodLow") {
            moodFace()
            moveTo(7.5f, 9.0f)
            curveTo(8.3f, 8.5f, 9.2f, 8.5f, 10.0f, 9.0f)
            moveTo(14.0f, 9.0f)
            curveTo(14.8f, 8.5f, 15.7f, 8.5f, 16.5f, 9.0f)
            moveTo(8.2f, 11.1f)
            lineTo(9.4f, 11.4f)
            moveTo(14.6f, 11.4f)
            lineTo(15.8f, 11.1f)
            moveTo(8.6f, 16.8f)
            curveTo(10.3f, 14.8f, 13.7f, 14.8f, 15.4f, 16.8f)
            moveTo(17.3f, 11.4f)
            curveTo(16.7f, 12.3f, 16.4f, 12.8f, 16.4f, 13.3f)
            curveTo(16.4f, 13.9f, 16.8f, 14.3f, 17.3f, 14.3f)
            curveTo(17.8f, 14.3f, 18.2f, 13.9f, 18.2f, 13.3f)
            curveTo(18.2f, 12.8f, 17.9f, 12.3f, 17.3f, 11.4f)
        }
    }

    val MoodTired: ImageVector by lazy {
        outlineIcon("XikeMoodTired") {
            moodFace()
            moveTo(7.4f, 10.0f)
            curveTo(8.2f, 10.7f, 9.3f, 10.8f, 10.1f, 10.2f)
            moveTo(13.9f, 10.2f)
            curveTo(14.7f, 10.8f, 15.8f, 10.7f, 16.6f, 10.0f)
            moveTo(8.0f, 12.4f)
            lineTo(9.6f, 12.4f)
            moveTo(14.4f, 12.4f)
            lineTo(16.0f, 12.4f)
            moveTo(9.0f, 16.1f)
            curveTo(10.8f, 16.6f, 13.2f, 16.6f, 15.0f, 16.1f)
        }
    }

    val MoodCalm: ImageVector by lazy {
        outlineIcon("XikeMoodCalm") {
            moodFace()
            moveTo(7.4f, 10.4f)
            curveTo(8.2f, 11.0f, 9.3f, 11.0f, 10.1f, 10.4f)
            moveTo(13.9f, 10.4f)
            curveTo(14.7f, 11.0f, 15.8f, 11.0f, 16.6f, 10.4f)
            moveTo(8.9f, 15.3f)
            curveTo(10.7f, 16.8f, 13.3f, 16.8f, 15.1f, 15.3f)
        }
    }

    val MoodGood: ImageVector by lazy {
        outlineIcon("XikeMoodGood") {
            moodFace()
            moveTo(8.8f, 9.8f)
            lineTo(8.8f, 10.7f)
            moveTo(15.2f, 9.8f)
            lineTo(15.2f, 10.7f)
            moveTo(8.5f, 14.8f)
            curveTo(10.3f, 17.0f, 13.7f, 17.0f, 15.5f, 14.8f)
        }
    }

    val MoodJoyful: ImageVector by lazy {
        outlineIcon("XikeMoodJoyful") {
            moodFace()
            moveTo(7.3f, 10.8f)
            curveTo(8.0f, 9.5f, 9.5f, 9.5f, 10.3f, 10.8f)
            moveTo(13.7f, 10.8f)
            curveTo(14.5f, 9.5f, 16.0f, 9.5f, 16.7f, 10.8f)
            moveTo(8.0f, 14.2f)
            curveTo(9.6f, 17.6f, 14.4f, 17.6f, 16.0f, 14.2f)
            moveTo(6.5f, 13.5f)
            lineTo(7.4f, 13.7f)
            moveTo(16.6f, 13.7f)
            lineTo(17.5f, 13.5f)
        }
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.moodFace() {
        moveTo(12.0f, 3.2f)
        curveTo(7.1f, 3.2f, 3.5f, 6.8f, 3.5f, 11.8f)
        curveTo(3.5f, 17.0f, 7.2f, 20.8f, 12.0f, 20.8f)
        curveTo(16.8f, 20.8f, 20.5f, 17.0f, 20.5f, 11.8f)
        curveTo(20.5f, 6.8f, 16.9f, 3.2f, 12.0f, 3.2f)
        close()
    }

    private inline fun outlineIcon(
        name: String,
        block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()
}

internal fun Mood.moodIcon(): ImageVector = when (this) {
    Mood.LOW -> XikeIcons.MoodLow
    Mood.TIRED -> XikeIcons.MoodTired
    Mood.CALM -> XikeIcons.MoodCalm
    Mood.GOOD -> XikeIcons.MoodGood
    Mood.JOYFUL -> XikeIcons.MoodJoyful
}

internal fun Mood.moodDescription(): String = when (this) {
    Mood.LOW -> "难过、无助，或有些不知所措"
    Mood.TIRED -> "疲倦、压抑，或有些提不起劲"
    Mood.CALM -> "安稳、放松，或没有明显起伏"
    Mood.GOOD -> "舒展、不错，或有一点期待"
    Mood.JOYFUL -> "开心、兴奋，或充满能量"
}
