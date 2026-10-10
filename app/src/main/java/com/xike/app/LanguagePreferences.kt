package com.xike.app

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.app.LocaleManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

enum class AppLanguage(val tag: String, val nativeName: String) {
    CHINESE("zh-CN", "中文"),
    ENGLISH("en", "English");

    val locale: Locale get() = Locale.forLanguageTag(tag)
}

internal class LanguagePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("xike-language", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.tag == preferences.getString("language", null) }
            ?: AppLanguage.CHINESE
        set(value) {
            check(preferences.edit().putString("language", value.tag).commit()) {
                "Unable to save language preference"
            }
        }
}

internal object AppLocale {
    var language by mutableStateOf(AppLanguage.CHINESE)
        private set

    val locale: Locale get() = language.locale
    private var localizedContext: Context? = null

    fun initialize(context: Context) = select(context, LanguagePreferences(context).language)

    fun select(context: Context, selected: AppLanguage) {
        localizedContext = context.applicationContext.forLanguage(selected)
        language = selected
        synchronizeSystemLanguage(context.applicationContext, selected)
    }

    fun text(source: String): String {
        // Only application-owned labels call this function. Journal text and stored identifiers
        // are never translated. Chinese also works in pure JVM domain tests without Android.
        val id = localizedStringIds[source] ?: return source
        return localizedContext?.getString(id) ?: source
    }
}

private fun synchronizeSystemLanguage(context: Context, language: AppLanguage) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val manager = context.getSystemService(LocaleManager::class.java)
        val locales = LocaleList.forLanguageTags(language.tag)
        if (manager.applicationLocales != locales) manager.applicationLocales = locales
    }
    // Dynamic shortcuts accept explicit labels on Android 8–12 as well as Android 13+.
    // Manifest shortcuts are immutable and otherwise follow the system locale.
    val localized = context.forLanguage(language)
    val shortcut = ShortcutInfo.Builder(context, "quick_record")
        .setShortLabel(localized.getString(R.string.shortcut_quick_record_short))
        .setLongLabel(localized.getString(R.string.shortcut_quick_record_long))
        .setDisabledMessage(localized.getString(R.string.shortcut_quick_record_disabled))
        .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
        .setIntent(Intent(context, MainActivity::class.java).setAction(ACTION_QUICK_RECORD))
        .build()
    val shortcuts = context.getSystemService(ShortcutManager::class.java)
    // Skip publishing when the existing labels already match.
    val current = shortcuts.dynamicShortcuts.firstOrNull { it.id == shortcut.id }
    if (current == null || current.shortLabel != shortcut.shortLabel || current.longLabel != shortcut.longLabel) {
        shortcuts.addDynamicShortcuts(listOf(shortcut))
    }
}

internal fun Context.forLanguage(language: AppLanguage): Context {
    val configuration = Configuration(resources.configuration).apply { setLocale(language.locale) }
    // Retain the Activity in the wrapper chain so native dialogs keep their window token.
    return android.view.ContextThemeWrapper(this, 0).apply { applyOverrideConfiguration(configuration) }
}

internal fun localizedText(source: String): String {
    AppLocale.language // Observe changes in Compose, including labels held in enums.
    return AppLocale.text(source)
}

internal fun tr(chinese: String, english: String): String =
    if (AppLocale.language == AppLanguage.CHINESE) chinese else english

internal val LocalLanguageChange = staticCompositionLocalOf<(AppLanguage) -> Unit> { {} }

internal fun ComponentActivity.setLocalizedContent(content: @Composable () -> Unit) {
    setContent {
        val language = AppLocale.language
        val languageContext = remember(language) { forLanguage(language) }
        CompositionLocalProvider(
            LocalContext provides languageContext,
            LocalLanguageChange provides { selected ->
                runCatching { LanguagePreferences(this).language = selected }
                    .onSuccess { AppLocale.select(this, selected) }
                    .onFailure {
                        XikeNotice.makeText(this, tr("语言设置保存失败，请重试。", "Unable to save language. Please try again."), XikeNotice.LENGTH_LONG).show()
                    }
            },
            content = content,
        )
    }
}
