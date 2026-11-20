package com.xike.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.xike.app.R
import com.xike.app.data.Mood

/** The nearby mood label provides the spoken description; the emoji is decorative. */
@Composable
internal fun MoodEmoji(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
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
}
