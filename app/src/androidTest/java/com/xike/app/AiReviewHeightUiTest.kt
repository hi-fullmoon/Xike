package com.xike.app

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ai.AiReviewOperations
import com.xike.app.ai.AiSettingsStore
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.feature.insights.AiReviewDialog
import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.journalPeriodSummary
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiReviewHeightUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun windowAndActionsStayInPlaceWhenContentChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xike-ai-height-${UUID.randomUUID()}"
        val operations = AiReviewOperations(AiSettingsStore(context, name))
        val language =
            if (InstrumentationRegistry.getArguments().getString("language") == "en")
                AppLanguage.ENGLISH
            else AppLanguage.CHINESE
        val preferences = LanguagePreferences(context)
        val previousLanguage = preferences.language
        try {
            preferences.language = language
            AppLocale.select(context, language)
            // Android 13+ synchronizes app locales; older releases use the local context wrapper.
            if (Build.VERSION.SDK_INT >= 33) {
                rule.waitUntil(5_000) {
                    context.resources.configuration.locales[0].language == language.locale.language
                }
            }
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        CompositionLocalProvider(
                            LocalAiReviewOperations provides operations,
                            LocalContext provides activity.forLanguage(language),
                        ) {
                            XikeTheme(AppTheme.PINE, AppStyle.BREATHE) {
                                AiReviewDialog(
                                    summary =
                                        journalPeriodSummary(emptyList(), InsightsPeriod.WEEK),
                                    entries = emptyList(),
                                    readRepository = null,
                                    onReadError = { error("No journal read should be started") },
                                    onOpenSource = { error("No source should be opened") },
                                    sourceOpen = false,
                                    onDismiss = {},
                                )
                            }
                        }
                    }
                }
                val save = rule.onNodeWithText(tr("保存", "Save"))
                awaitEnabled(save)
                save.performClick()
                val notes = rule.onNodeWithTag("ai-include-notes")
                rule.waitUntil(15_000) { runCatching { notes.assertIsOff() }.isSuccess }
                val initialWindow = rule.onNode(isDialog()).fetchSemanticsNode().boundsInWindow
                val initialClose =
                    rule.onNodeWithText(tr("关闭", "Close")).fetchSemanticsNode().boundsInWindow
                capture("empty-settings")
                notes.performScrollTo().performClick().assertIsOn()
                assertStableBounds(initialWindow, initialClose)
                notes.performClick().assertIsOff()
                assertStableBounds(initialWindow, initialClose)

                // Use real settings controls, without credentials, journal records or network
                // calls.
                rule.onNodeWithText(tr("配置", "Settings")).performClick()
                rule.onNodeWithTag("ai-address").performScrollTo()
                awaitEnabled(rule.onNodeWithTag("ai-address"))
                rule
                    .onNodeWithTag("ai-address")
                    .performScrollTo()
                    .performTextReplacement("https://api.deepseek.com/v1")
                rule
                    .onNodeWithTag("ai-model")
                    .performScrollTo()
                    .performTextReplacement("deepseek-flash")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                save.performClick()
                assertStableBounds(initialWindow, initialClose)
                notes.performScrollTo().performClick().assertIsOn()
                assertStableBounds(initialWindow, initialClose)
                rule.onNodeWithTag("ai-review-scope").performScrollTo().assertIsDisplayed()
                assertStableBounds(initialWindow, initialClose)
                rule.onNodeWithTag("ai-review-content").performSemanticsAction(
                    SemanticsActions.ScrollBy
                ) {
                    it(0f, -100_000f)
                }
                rule.waitForIdle()
                capture("configured")
                rule.onNodeWithText(tr("预览发送内容", "Preview content")).assertIsNotEnabled()
            }
        } finally {
            context.deleteSharedPreferences(name)
            preferences.language = previousLanguage
            AppLocale.select(context, previousLanguage)
        }
    }

    private fun assertStableBounds(window: Rect, close: Rect) {
        rule.waitForIdle()
        assertEquals(window, rule.onNode(isDialog()).fetchSemanticsNode().boundsInWindow)
        assertEquals(
            close,
            rule.onNodeWithText(tr("关闭", "Close")).fetchSemanticsNode().boundsInWindow,
        )
        listOf(tr("关闭", "Close"), tr("配置", "Settings"), tr("预览发送内容", "Preview content")).forEach {
            label ->
            val node = rule.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(
                requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)
                    .invoke(layouts)
            )
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight)
            repeat(layout.lineCount) { assertFalse(layout.isLineEllipsized(it)) }
            assertTrue(window.contains(node.boundsInWindow.topLeft))
            assertTrue(node.boundsInWindow.bottom <= window.bottom)
        }
    }

    private fun awaitEnabled(node: SemanticsNodeInteraction) {
        try {
            rule.waitUntil(15_000) { runCatching { node.assertIsEnabled() }.isSuccess }
        } catch (failure: Throwable) {
            throw AssertionError(rule.onAllNodes(isRoot()).printToString(maxDepth = 20), failure)
        }
    }

    private fun capture(state: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        captureAiDialogWindow(
            File(
                context.getExternalFilesDir(null),
                "ai-height-${AppLocale.language.name}-${config.screenWidthDp}-${config.fontScale}-${config.uiMode}-$state.png",
            )
        )
    }
}
