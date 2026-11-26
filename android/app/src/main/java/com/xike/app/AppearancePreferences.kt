package com.xike.app

import android.content.Context
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.MoodIconStyle

internal data class AppearanceSettings(
    val theme: AppTheme,
    val style: AppStyle,
    val moodIconStyle: MoodIconStyle = MoodIconStyle.CLASSIC,
)

/**
 * Keeps the non-sensitive appearance choice available before the encrypted journal is opened. The
 * database remains a compatibility mirror for installs upgrading from the original theme setting.
 */
internal class AppearancePreferences(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun current(): AppearanceSettings =
        AppearanceSettings(
            theme =
                AppTheme.entries.firstOrNull { it.name == preferences.getString(THEME_KEY, null) }
                    ?: AppTheme.OCEAN,
            style =
                AppStyle.entries.firstOrNull { it.name == preferences.getString(STYLE_KEY, null) }
                    ?: AppStyle.BREATHE,
            moodIconStyle =
                MoodIconStyle.entries.firstOrNull {
                    it.name == preferences.getString(MOOD_ICON_STYLE_KEY, null)
                } ?: MoodIconStyle.CLASSIC,
        )

    fun saveTheme(theme: AppTheme) {
        preferences.edit().putString(THEME_KEY, theme.name).apply()
    }

    fun saveStyle(style: AppStyle) {
        preferences.edit().putString(STYLE_KEY, style.name).apply()
    }

    fun saveMoodIconStyle(moodIconStyle: MoodIconStyle) {
        preferences.edit().putString(MOOD_ICON_STYLE_KEY, moodIconStyle.name).apply()
    }

    fun migrateFromDatabase(themeName: String?, styleName: String?): AppearanceSettings {
        val editor = preferences.edit()
        if (!preferences.contains(THEME_KEY)) {
            AppTheme.entries
                .firstOrNull { it.name == themeName }
                ?.let { editor.putString(THEME_KEY, it.name) }
        }
        if (!preferences.contains(STYLE_KEY)) {
            AppStyle.entries
                .firstOrNull { it.name == styleName }
                ?.let { editor.putString(STYLE_KEY, it.name) }
        }
        editor.apply()
        return current()
    }

    internal companion object {
        const val PREFERENCES_NAME = "xike-appearance"
        const val THEME_KEY = "theme"
        const val STYLE_KEY = "style"
        const val MOOD_ICON_STYLE_KEY = "mood_icon_style"
    }
}
