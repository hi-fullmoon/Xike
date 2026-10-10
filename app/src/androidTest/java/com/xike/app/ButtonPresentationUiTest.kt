package com.xike.app

import android.graphics.Bitmap
import android.app.LocaleManager
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ButtonPresentationUiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var originalLanguage: AppLanguage
    private var originalSystemLocales: LocaleList? = null
    private var originalQuickRecord: ShortcutInfo? = null
    private var languageStateCaptured = false

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun rememberLanguageState() {
        originalLanguage = AppLocale.language
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            originalSystemLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
        }
        originalQuickRecord = context.getSystemService(ShortcutManager::class.java)
            .dynamicShortcuts.firstOrNull { it.id == "quick_record" }
        languageStateCaptured = true
    }

    @After
    fun restoreLanguageState() {
        if (!languageStateCaptured) return
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                AppLocale.select(context, originalLanguage)
            } finally {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.getSystemService(LocaleManager::class.java).applicationLocales = requireNotNull(originalSystemLocales)
                    }
                } finally {
                    val shortcuts = context.getSystemService(ShortcutManager::class.java)
                    val original = originalQuickRecord
                    if (original == null) {
                        shortcuts.removeDynamicShortcuts(listOf("quick_record"))
                    } else {
                        // Queried ShortcutInfo omits its icon; update existing fields without replacing it.
                        check(shortcuts.updateShortcuts(listOf(original)))
                    }
                }
            }
            assertEquals(originalLanguage, AppLocale.language)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertEquals(originalSystemLocales, context.getSystemService(LocaleManager::class.java).applicationLocales)
            }
            val restoredShortcut = context.getSystemService(ShortcutManager::class.java)
                .dynamicShortcuts.firstOrNull { it.id == "quick_record" }
            assertEquals(originalQuickRecord?.shortLabel?.toString(), restoredShortcut?.shortLabel?.toString())
            assertEquals(originalQuickRecord?.longLabel?.toString(), restoredShortcut?.longLabel?.toString())
            assertEquals(originalQuickRecord?.disabledMessage?.toString(), restoredShortcut?.disabledMessage?.toString())
        }
    }

    @Test
    fun mediaActionsSeparateTextAndIconAndPreservePhotoAction() {
        val arguments = InstrumentationRegistry.getArguments()
        val language = if (arguments.getString("language") == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
        val style = AppStyle.entries.firstOrNull { it.name == arguments.getString("style") } ?: AppStyle.BREATHE
        rule.runOnUiThread { AppLocale.select(rule.activity, language) }
        var photoClicks = 0
        val photoLabel = localizedText("添加照片")
        rule.setContent {
            XikeTheme(AppTheme.PINE, style) {
                Column(Modifier.padding(16.dp)) {
                    VideoAddButton(enabled = true, onPicked = {})
                    Spacer(Modifier.height(16.dp))
                    AddPhotoTile(Modifier.size(92.dp), photoLabel) { photoClicks++ }
                }
            }
        }

        rule.onNodeWithText(photoLabel).assertDoesNotExist()
        rule.onNodeWithContentDescription(photoLabel)
            .assertIsDisplayed().assertHasClickAction()
            .assertWidthIsEqualTo(92.dp).assertHeightIsEqualTo(92.dp)
            .performClick()
        rule.runOnIdle { assertEquals(1, photoClicks) }
        rule.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(rule.activity.getExternalFilesDir(null), "buttons-${language.name}-${style.name}.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
        rule.onNodeWithText(tr("视频", "Video")).assertIsDisplayed().performClick()
        rule.onNodeWithText(tr("添加视频", "Add video")).assertIsDisplayed()
        rule.onNodeWithText(localizedText("取消")).performClick()
        rule.onNodeWithText(tr("视频", "Video")).assertIsDisplayed()
    }
}
