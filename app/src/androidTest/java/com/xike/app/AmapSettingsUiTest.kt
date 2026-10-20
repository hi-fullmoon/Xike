package com.xike.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.outdoor.AmapSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real settings screen; does not create journals, credentials, or SDK results. */
@RunWith(AndroidJUnit4::class)
class AmapSettingsUiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val languagePreferences =
        context.getSharedPreferences("xike-language", Context.MODE_PRIVATE)
    private val originalLanguage = languagePreferences.getString("language", null)
    private val requestedLanguage =
        InstrumentationRegistry.getArguments().getString("amapUiLanguage")
    private var originalAutomationFlags = 0

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun selectRequestedLanguage() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val serviceInfo = automation.serviceInfo
        originalAutomationFlags = serviceInfo.flags
        serviceInfo.flags =
            serviceInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        val language =
            when (requestedLanguage) {
                null -> return
                "en" -> AppLanguage.ENGLISH
                "zh-CN" -> AppLanguage.CHINESE
                else -> error("Unsupported test language")
            }
        rule.runOnUiThread {
            LanguagePreferences(context).language = language
            AppLocale.initialize(context)
        }
        rule.waitForIdle()
    }

    @After
    fun restoreAutomationFlags() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val serviceInfo = automation.serviceInfo
        serviceInfo.flags = originalAutomationFlags
        automation.serviceInfo = serviceInfo
    }

    @After
    fun restoreLanguage() {
        if (requestedLanguage == null) return
        languagePreferences
            .edit()
            .apply {
                if (originalLanguage == null) remove("language")
                else putString("language", originalLanguage)
            }
            .commit()
        rule.runOnUiThread { AppLocale.initialize(context) }
    }

    @Test
    fun versionTripleTapOpensConfigurationAndCancelKeepsItClosed() {
        val store = AmapSettingsStore(ApplicationProvider.getApplicationContext<Context>())
        val original = runBlocking { withContext(Dispatchers.IO) { store.read() } }
        val settings = tr("设置", "Settings")
        rule.waitUntil(30_000) {
            rule
                .onAllNodes(androidx.compose.ui.test.hasText(settings))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText(settings).performClick()
        val version = tr("息刻 · ${BuildConfig.VERSION_NAME}", "Xike · ${BuildConfig.VERSION_NAME}")
        val title = tr("高德定位配置", "AMap location settings")
        rule.onNodeWithText(version).performScrollTo().performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithText(version).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithText(version).performClick()
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.waitUntil(15_000) {
            rule
                .onAllNodes(androidx.compose.ui.test.hasText("PackageName:", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText(tr("保存", "Save")).assertIsEnabled()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).assertIsDisplayed()
        assertCompactActions("before-keyboard")
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performScrollTo().performClick()
        rule.waitUntil(10_000) { keyboardVisible() }
        rule.onNodeWithText(tr("保存", "Save")).assertIsDisplayed()
        rule.onNodeWithText(tr("取消", "Cancel")).assertIsDisplayed()
        assertCompactActions("with-keyboard")
        androidx.test.espresso.Espresso.pressBack()
        assertConsentFirstLineAlignment()
        rule.onNodeWithTag("amap-signing-information").performScrollTo()
        rule.onNodeWithText("SHA-1:").assertIsDisplayed()
        val sha1 = rule.onNodeWithTag("amap-signing-sha1")
        sha1.assertIsDisplayed()
        val sha1TitleBounds = rule.onNodeWithText("SHA-1:").fetchSemanticsNode().boundsInRoot
        val sha1Bounds = sha1.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "SHA-1 title and fingerprint must share a row",
            kotlin.math.abs(sha1TitleBounds.center.y - sha1Bounds.center.y) <=
                rule.activity.resources.displayMetrics.density &&
                sha1Bounds.left >= sha1TitleBounds.right,
        )
        val layouts = mutableListOf<TextLayoutResult>()
        sha1.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Signing fingerprint must occupy one line", layouts.single().lineCount == 1)
        val horizontalRange =
            sha1.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        if (horizontalRange.maxValue() > 0f) {
            sha1.performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
            rule.waitForIdle()
            assertTrue(
                "Overflowing fingerprint must scroll horizontally",
                horizontalRange.value() > 0f,
            )
        }
        rule
            .onNodeWithText(tr("复制包名与 SHA-1", "Copy package and SHA-1"))
            .performScrollTo()
            .assertIsDisplayed()
        val testLocation = rule.onNodeWithText(tr("测试定位", "Test location")).performScrollTo()
        if (original.apiKey.isNotBlank() && original.privacyAccepted) testLocation.assertIsEnabled()
        else testLocation.assertIsNotEnabled()
        rule.onNodeWithText(tr("取消", "Cancel")).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        val restored = runBlocking { withContext(Dispatchers.IO) { store.read() } }
        // Compare without including either credential in assertion diagnostics.
        assertTrue("Cancel must preserve the saved configuration", original == restored)
    }

    private fun assertConsentFirstLineAlignment() {
        rule.onNodeWithTag("amap-consent-row").performScrollTo()
        val checkbox = rule.onNodeWithTag("amap-consent-checkbox", useUnmergedTree = true)
        val text = rule.onNodeWithTag("amap-consent-text", useUnmergedTree = true)
        checkbox.assertIsDisplayed()
        text.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        rule.waitForIdle()
        val layout = layouts.single()
        val firstLineCenter =
            text.fetchSemanticsNode().boundsInRoot.top +
                (layout.getLineTop(0) + layout.getLineBottom(0)) / 2
        val checkboxCenter = checkbox.fetchSemanticsNode().boundsInRoot.center.y
        val row = rule.onNodeWithTag("amap-consent-row").fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        assertTrue(
            "Consent checkbox must align with the content left edge",
            kotlin.math.abs(checkbox.fetchSemanticsNode().boundsInRoot.left - row.left) <= density,
        )
        assertTrue("Consent row must retain its touch height", row.height / density >= 48f)
        assertTrue(
            "Consent checkbox must align with the first text line",
            kotlin.math.abs(firstLineCenter - checkboxCenter) <=
                rule.activity.resources.displayMetrics.density,
        )
    }

    private fun assertCompactActions(phase: String) {
        val cancel = rule.onNodeWithText(tr("取消", "Cancel")).fetchSemanticsNode().boundsInRoot
        val save = rule.onNodeWithText(tr("保存", "Save")).fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        val gapDp = (save.left - cancel.right) / density
        assertTrue("Footer actions must remain adjacent", gapDp in 0f..8f)
        assertTrue(
            "Footer actions must share a row",
            kotlin.math.abs(save.top - cancel.top) <= density,
        )
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                0,
                Bundle().apply {
                    putString("amapLayoutPhase", phase)
                    putBoolean("amapKeyboardVisible", keyboardVisible())
                    putString("amapLayoutLanguage", AppLocale.language.tag)
                    putFloat("amapLayoutFontScale", rule.activity.resources.configuration.fontScale)
                    putInt("amapLayoutWidthDp", rule.activity.resources.configuration.screenWidthDp)
                    putInt("amapLayoutUiMode", rule.activity.resources.configuration.uiMode)
                    putFloat("amapFooterGapDp", gapDp)
                },
            )
    }

    private fun keyboardVisible(): Boolean =
        InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
}
