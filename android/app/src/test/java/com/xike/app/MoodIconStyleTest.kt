package com.xike.app

import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.MoodIconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodIconStyleTest {
    @Test
    fun appearanceSettingsDefaultsToClassicMoodIcons() {
        val settings = AppearanceSettings(AppTheme.OCEAN, AppStyle.BREATHE)
        assertEquals(MoodIconStyle.CLASSIC, settings.moodIconStyle)
    }

    @Test
    fun allMoodIconStylesExposeDistinctNamesAndNonBlankDescriptions() {
        val styles = MoodIconStyle.entries
        assertEquals(
            listOf(
                MoodIconStyle.CLASSIC,
                MoodIconStyle.DUOTONE,
                MoodIconStyle.LINE,
                MoodIconStyle.RING,
            ),
            styles,
        )
        assertEquals(styles.size, styles.map { it.name }.toSet().size)
        styles.forEach { style ->
            assertTrue(style.title.isNotBlank())
            assertTrue(style.subtitle.isNotBlank())
        }
    }
}
