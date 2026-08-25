package com.xike.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A small, purpose-built icon family for Xike. The rounded strokes echo a breath
 * and a weather map instead of borrowing the usual smiley-face journal language.
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

    val Storm: ImageVector by lazy {
        outlineIcon("XikeStorm") {
            weatherCloud()
            moveTo(9.5f, 14.8f)
            lineTo(8.3f, 17.1f)
            lineTo(10.1f, 17.1f)
            lineTo(8.9f, 19.6f)
            moveTo(14.5f, 15.0f)
            lineTo(13.5f, 17.0f)
            moveTo(17.5f, 15.0f)
            lineTo(16.5f, 17.0f)
        }
    }

    val Rain: ImageVector by lazy {
        outlineIcon("XikeRain") {
            weatherCloud()
            moveTo(8.0f, 15.1f)
            lineTo(7.0f, 17.1f)
            moveTo(12.0f, 15.1f)
            lineTo(11.0f, 17.1f)
            moveTo(16.0f, 15.1f)
            lineTo(15.0f, 17.1f)
        }
    }

    val Breeze: ImageVector by lazy {
        outlineIcon("XikeBreeze") {
            moveTo(4.0f, 9.0f)
            lineTo(15.5f, 9.0f)
            curveTo(17.2f, 9.0f, 18.2f, 8.1f, 18.2f, 6.8f)
            curveTo(18.2f, 5.7f, 17.3f, 4.9f, 16.2f, 4.9f)
            moveTo(4.0f, 12.5f)
            lineTo(18.0f, 12.5f)
            curveTo(19.3f, 12.5f, 20.1f, 13.3f, 20.1f, 14.4f)
            curveTo(20.1f, 15.7f, 19.1f, 16.6f, 17.6f, 16.6f)
            moveTo(4.0f, 16.0f)
            lineTo(12.5f, 16.0f)
            curveTo(13.9f, 16.0f, 14.7f, 16.8f, 14.7f, 17.9f)
            curveTo(14.7f, 19.0f, 13.9f, 19.7f, 12.8f, 19.7f)
        }
    }

    val PartlyBright: ImageVector by lazy {
        outlineIcon("XikePartlyBright") {
            moveTo(15.8f, 5.0f)
            lineTo(15.8f, 3.6f)
            moveTo(20.0f, 6.8f)
            lineTo(21.0f, 5.8f)
            moveTo(18.9f, 10.5f)
            curveTo(18.9f, 8.8f, 17.5f, 7.4f, 15.8f, 7.4f)
            curveTo(14.8f, 7.4f, 13.9f, 7.9f, 13.3f, 8.6f)
            moveTo(7.2f, 18.2f)
            lineTo(16.8f, 18.2f)
            curveTo(18.6f, 18.2f, 20.0f, 16.9f, 20.0f, 15.3f)
            curveTo(20.0f, 13.7f, 18.7f, 12.4f, 17.0f, 12.4f)
            curveTo(16.5f, 12.4f, 16.0f, 12.5f, 15.6f, 12.8f)
            curveTo(14.9f, 10.9f, 13.1f, 9.7f, 11.0f, 9.7f)
            curveTo(8.2f, 9.7f, 6.0f, 11.7f, 5.8f, 14.3f)
            curveTo(4.6f, 14.7f, 3.9f, 15.7f, 4.1f, 16.6f)
            curveTo(4.3f, 17.6f, 5.5f, 18.2f, 7.2f, 18.2f)
        }
    }

    val Sun: ImageVector by lazy {
        outlineIcon("XikeSun") {
            moveTo(12.0f, 8.0f)
            curveTo(9.8f, 8.0f, 8.0f, 9.8f, 8.0f, 12.0f)
            curveTo(8.0f, 14.2f, 9.8f, 16.0f, 12.0f, 16.0f)
            curveTo(14.2f, 16.0f, 16.0f, 14.2f, 16.0f, 12.0f)
            curveTo(16.0f, 9.8f, 14.2f, 8.0f, 12.0f, 8.0f)
            close()
            moveTo(12.0f, 3.0f)
            lineTo(12.0f, 5.0f)
            moveTo(12.0f, 19.0f)
            lineTo(12.0f, 21.0f)
            moveTo(3.0f, 12.0f)
            lineTo(5.0f, 12.0f)
            moveTo(19.0f, 12.0f)
            lineTo(21.0f, 12.0f)
            moveTo(5.6f, 5.6f)
            lineTo(7.0f, 7.0f)
            moveTo(17.0f, 17.0f)
            lineTo(18.4f, 18.4f)
            moveTo(18.4f, 5.6f)
            lineTo(17.0f, 7.0f)
            moveTo(7.0f, 17.0f)
            lineTo(5.6f, 18.4f)
        }
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.weatherCloud() {
        moveTo(7.0f, 13.0f)
        lineTo(17.2f, 13.0f)
        curveTo(19.0f, 13.0f, 20.3f, 11.7f, 20.3f, 10.1f)
        curveTo(20.3f, 8.5f, 19.0f, 7.2f, 17.3f, 7.2f)
        curveTo(16.7f, 7.2f, 16.2f, 7.3f, 15.8f, 7.6f)
        curveTo(15.0f, 5.7f, 13.2f, 4.5f, 11.0f, 4.5f)
        curveTo(8.2f, 4.5f, 6.0f, 6.5f, 5.8f, 9.1f)
        curveTo(4.5f, 9.5f, 3.7f, 10.5f, 4.0f, 11.5f)
        curveTo(4.3f, 12.5f, 5.4f, 13.0f, 7.0f, 13.0f)
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

internal fun Mood.weatherIcon(): ImageVector = when (this) {
    Mood.LOW -> XikeIcons.Storm
    Mood.TIRED -> XikeIcons.Rain
    Mood.CALM -> XikeIcons.Breeze
    Mood.GOOD -> XikeIcons.PartlyBright
    Mood.JOYFUL -> XikeIcons.Sun
}

internal fun Mood.weatherDescription(): String = when (this) {
    Mood.LOW -> "低落、难过，或有些不知所措"
    Mood.TIRED -> "疲惫、压抑，或有些提不起劲"
    Mood.CALM -> "平静、稳定，或没有明显起伏"
    Mood.GOOD -> "轻松、不错，或有一点期待"
    Mood.JOYFUL -> "愉悦、兴奋，或充满能量"
}
