package com.xike.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/** The nearby mood label provides the spoken description; the emoji is decorative. */
@Composable
internal fun MoodEmoji(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    val textSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = mood.emoji,
        modifier = modifier.clearAndSetSemantics {},
        fontSize = textSize,
        lineHeight = textSize,
        maxLines = 1,
        softWrap = false,
    )
}
