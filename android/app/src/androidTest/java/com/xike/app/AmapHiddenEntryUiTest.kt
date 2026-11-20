package com.xike.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.model.JournalDraft
import com.xike.app.feature.lock.AppLockTimeout
import com.xike.app.feature.moment.MomentScreen
import com.xike.app.feature.settings.ProfileSettingsScreen
import com.xike.app.outdoor.AmapSettingsOperations
import com.xike.app.outdoor.AmapSettingsStore
import com.xike.app.outdoor.LocalAmapSettingsOperations
import com.xike.app.ui.theme.XikeTheme
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/** Real screens and configuration dialog; only an isolated encrypted settings file is read. */
class AmapHiddenEntryUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun momentTitleRequiresThreeTapsAndResetsAfterTimeoutAndDismissal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "amap-hidden-entry-${UUID.randomUUID()}"
        val appearance = AppearancePreferences(context).current()
        val operations = AmapSettingsOperations(context, AmapSettingsStore(context, name), {})
        try {
            for (loading in listOf(false, true)) {
                ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.setContent {
                            CompositionLocalProvider(
                                LocalAmapSettingsOperations provides operations
                            ) {
                                XikeTheme(appearance.theme, appearance.style) {
                                    MomentScreen(
                                        padding = PaddingValues(0.dp),
                                        entries = emptyList(),
                                        draft = JournalDraft(),
                                        dailyPromptSettings = DailyPromptSettings(),
                                        onDraftMoodChange = {},
                                        onDraftNoteChange = {},
                                        onDraftTagToggle = {},
                                        onDraftImagesAdded = { false },
                                        onDraftImageRemoved = {},
                                        onSave = { _, _ -> error("Saving is outside this test") },
                                        isDraftOutdoorLoading = loading,
                                        onAttachCurrentOutdoor = {
                                            error("Title taps must not request location")
                                        },
                                    )
                                }
                            }
                        }
                    }
                    val title = rule.onNodeWithText(localizedText("此刻窗外"))
                    val dialog = rule.onNodeWithText(tr("高德定位配置", "AMap location settings"))
                    title.performScrollTo().performClick()
                    dialog.assertDoesNotExist()
                    Thread.sleep(2_100)
                    title.performClick().performClick()
                    dialog.assertDoesNotExist()
                    title.performClick()
                    dialog.assertIsDisplayed()
                    rule.onNodeWithText(tr("取消", "Cancel")).performClick()
                    title.performClick().performClick()
                    dialog.assertDoesNotExist()
                    title.performClick()
                    dialog.assertIsDisplayed()
                    rule.onNodeWithText(tr("取消", "Cancel")).performClick()
                }
            }
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    @Test
    fun settingsVersionIsPlainTextWithoutAHiddenClickAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appearance = AppearancePreferences(context).current()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    XikeTheme(appearance.theme, appearance.style) {
                        ProfileSettingsScreen(
                            padding = PaddingValues(0.dp),
                            selectedTheme = appearance.theme,
                            selectedStyle = appearance.style,
                            appLockEnabled = false,
                            appLockTimeout = AppLockTimeout.IMMEDIATELY,
                            authenticationAvailable = false,
                            reminderSettings = ReminderSettings(),
                            dailyPromptSettings = DailyPromptSettings(),
                            notificationPermissionGranted = false,
                            onThemeChange = {},
                            onStyleChange = {},
                            onAppLockChange = {},
                            onAppLockTimeoutChange = {},
                            onLockNow = {},
                            onReminderEnabledChange = {},
                            onReminderSettingsChange = {},
                            onDailyPromptSettingsChange = {},
                            onExport = {},
                            onImport = {},
                            canUndoRestore = false,
                            onUndoRestore = {},
                        )
                    }
                }
            }
            rule
                .onNodeWithText(
                    tr("息刻 · ${BuildConfig.VERSION_NAME}", "Xike · ${BuildConfig.VERSION_NAME}")
                )
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasNoClickAction()
        }
    }
}
