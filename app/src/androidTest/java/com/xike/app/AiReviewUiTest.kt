package com.xike.app

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ai.AiReviewOperations
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSettingsStore
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.feature.insights.JournalInsightsScreen
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Real components and encrypted settings; explicit legacy payload checks use existing data. No AI
 * calls.
 */
class AiReviewUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun retiredLabelsNeverEnterAiInput() =
        kotlinx.coroutines.runBlocking {
            org.junit.Assume.assumeTrue(
                InstrumentationRegistry.getArguments().getString("aiLabelRemovalCheck") == "true"
            )
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val store = JournalStore(context).also { it.initialize(loadEntries = false) }
            val entries = store.entries()
            check(entries.any { it.labels.isNotEmpty() }) {
                "Requires existing legacy records; do not seed data"
            }
            val summary =
                com.xike.app.feature.insights.journalPeriodSummary(
                    entries,
                    com.xike.app.feature.insights.InsightsPeriod.YEAR,
                )
            val input =
                org.json.JSONObject(
                    com.xike.app.ai
                        .aiReviewInput(
                            entries.filter { it.id in summary.entryIds },
                            summary,
                            false,
                        )
                        .json
                )
            val rows = input.getJSONArray("entries")
            repeat(rows.length()) { index -> assertFalse(rows.getJSONObject(index).has("labels")) }
        }

    @Test
    fun hiddenEntryRequiresFreshTripleTapAndSettingsPersistWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xike-ai-ui-${UUID.randomUUID()}"
        val store = AiSettingsStore(context, name)
        val operations = AiReviewOperations(store)
        val args = InstrumentationRegistry.getArguments()
        val language =
            if (args.getString("language") == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
        val style =
            AppStyle.entries.firstOrNull { it.name == args.getString("style") } ?: AppStyle.BREATHE
        val preferences = LanguagePreferences(context)
        val previousLanguage = preferences.language
        try {
            preferences.language = language
            AppLocale.select(context, language)
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
                            XikeTheme(AppTheme.PINE, style) {
                                JournalInsightsScreen(
                                    padding = PaddingValues(0.dp),
                                    entries = emptyList(),
                                    openImage = { error("No attachments should be opened") },
                                )
                            }
                        }
                    }
                }
                rule.waitUntil(15_000) {
                    rule.onAllNodesWithTag("ai-review-entry").fetchSemanticsNodes().isNotEmpty()
                }
                val title = rule.onNodeWithTag("ai-review-entry")
                val configTitle = tr("AI 接口配置", "AI service settings")
                title.performClick()
                rule.onNodeWithText(configTitle).assertDoesNotExist()
                Thread.sleep(2_100)
                title.performClick().performClick()
                rule.onNodeWithText(configTitle).assertDoesNotExist()
                title.performClick()
                try {
                    rule.waitUntil(10_000) {
                        rule.onNodeWithText(configTitle).isDisplayed()
                    }
                } catch (failure: AssertionError) {
                    captureAiDialogWindow(
                        File(context.getExternalFilesDir(null), "ai-layout-failure.png")
                    )
                    throw AssertionError(rule.onNode(isDialog()).printToString(), failure)
                }
                rule.onNodeWithTag("ai-address").performScrollTo()
                // Encrypted settings are loaded asynchronously after the window is shown.
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithTag("ai-address").fetchSemanticsNodes().any {
                        !it.config.contains(SemanticsProperties.Disabled)
                    }
                }
                rule.onNodeWithTag("ai-address").assertIsEnabled()
                assertEquals(AiSettings(), store.read())
                rule
                    .onNodeWithText(tr("测试连接", "Test connection"))
                    .performScrollTo()
                    .assertIsNotEnabled()
                rule.onNodeWithTag("ai-enabled").performScrollTo().assertIsNotEnabled()
                assertConfigurationAlignment()
                rule
                    .onNodeWithTag("ai-address")
                    .performScrollTo()
                    .performTextReplacement("https://api.deepseek.com/v1")
                rule.onNodeWithTag("ai-consent").performScrollTo().performClick().assertIsOn()
                rule
                    .onNodeWithTag("ai-address")
                    .performScrollTo()
                    .performTextReplacement("https://api.deepseek.com")
                rule.onNodeWithTag("ai-consent").performScrollTo().assertIsOff()
                rule
                    .onNodeWithTag("ai-address")
                    .performScrollTo()
                    .performTextReplacement("http://api.deepseek.com")
                rule.onNodeWithText(tr("保存", "Save")).assertIsNotEnabled()
                rule
                    .onNodeWithTag("ai-address")
                    .performScrollTo()
                    .performTextReplacement("https://api.deepseek.com/v1")
                rule
                    .onNodeWithTag("ai-model")
                    .performScrollTo()
                    .performTextReplacement("deepseek-flash")
                rule.onNodeWithText(tr("保存", "Save")).assertIsEnabled()
                // Inspect the real configuration window before saving; it contains no credentials.
                assertCompleteText(tr("保存", "Save"))
                assertCompleteText(tr("取消", "Cancel"))
                if (Build.VERSION.SDK_INT >= 30 && args.getString("keyboard") == "true") {
                    assertButtonsAboveKeyboard()
                }
                val configuration = context.resources.configuration
                captureAiDialogWindow(
                    File(
                        context.getExternalFilesDir(null),
                        "ai-settings-${language.name}-${style.name}-${configuration.uiMode}-${configuration.screenWidthDp}-${configuration.fontScale}.png",
                    )
                )
                rule.onNodeWithText(tr("保存", "Save")).performClick()
                rule.onNodeWithText(tr("AI 回顾", "AI review")).assertIsDisplayed()
                assertEquals("https://api.deepseek.com/v1", store.read().address)
                assertFalse(store.read().enabled)
                assertEquals("", store.read().apiKey)
                rule.onNodeWithText(tr("预览发送内容", "Preview content")).assertIsNotEnabled()
                assertReviewEndpointSingleLine()
                assertReviewNotesAlignment()
                captureAiDialogWindow(
                    File(
                        context.getExternalFilesDir(null),
                        "ai-review-notes-${language.name}-${configuration.screenWidthDp}-${configuration.fontScale}.png",
                    )
                )
                rule.onNodeWithText(tr("关闭", "Close")).performClick()
                title.performClick().performClick()
                rule.onNodeWithText(configTitle).assertDoesNotExist()
                title.performClick()
                // Address and model are sufficient to reopen local preview without a Key.
                rule.waitUntil(10_000) {
                    rule.onNodeWithText(tr("AI 回顾", "AI review")).isDisplayed()
                }
                rule.waitUntil(10_000) {
                    runCatching {
                        rule.onNodeWithText(tr("配置", "Settings")).assertIsEnabled()
                    }
                        .isSuccess
                }
                rule.onNodeWithText(tr("配置", "Settings")).performClick()
                rule.onNodeWithText(configTitle).assertIsDisplayed()
                rule.onNodeWithText(tr("取消", "Cancel")).performClick()
                rule.onNodeWithText(tr("AI 回顾", "AI review")).assertIsDisplayed()
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                rule.onNodeWithText(tr("AI 回顾", "AI review")).assertDoesNotExist()
                rule.onNodeWithTag("ai-review-entry").assertIsDisplayed()
            }
        } finally {
            context.deleteSharedPreferences(name)
            preferences.language = previousLanguage
            AppLocale.select(context, previousLanguage)
        }
    }

    @androidx.annotation.RequiresApi(30)
    private fun assertButtonsAboveKeyboard() {
        var decor: View? = null
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .check { view, failure ->
                if (failure != null) throw failure
                decor = view
            }
        rule.onNodeWithTag("ai-model").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            var visible = false
            rule.runOnUiThread {
                visible = decor?.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
            }
            visible
        }
        rule.waitUntil(5_000) {
            var keyboardTop = 0
            val origin = IntArray(2)
            rule.runOnUiThread {
                val view = requireNotNull(decor)
                view.getLocationOnScreen(origin)
                val height =
                    view.context
                        .getSystemService(WindowManager::class.java)
                        .maximumWindowMetrics
                        .bounds
                        .bottom
                keyboardTop =
                    height - view.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
            }
            listOf(tr("保存", "Save"), tr("取消", "Cancel")).all {
                rule.onNodeWithText(it).fetchSemanticsNode().boundsInWindow.bottom + origin[1] <=
                    keyboardTop + 1
            }
        }
        assertCompleteText(tr("保存", "Save"))
        assertCompleteText(tr("取消", "Cancel"))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }

    private fun assertConfigurationAlignment() {
        rule.onNodeWithTag("ai-consent").performScrollTo()
        val checkbox =
            rule.onNodeWithTag("ai-consent-checkbox", useUnmergedTree = true).fetchSemanticsNode()
        val consent =
            rule.onNodeWithTag("ai-consent-text", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(
            requireNotNull(consent.config[SemanticsActions.GetTextLayoutResult].action)
                .invoke(layouts)
        )
        val text = layouts.single()
        val firstLineCenter =
            consent.boundsInRoot.top + (text.getLineTop(0) + text.getLineBottom(0)) / 2
        assertEquals(firstLineCenter, checkbox.boundsInRoot.center.y, 1.5f)
        val left = checkbox.boundsInRoot.left
        rule.onNodeWithTag("ai-enabled").performScrollTo()
        assertEquals(
            left,
            rule.onNodeWithTag("ai-enabled-label").fetchSemanticsNode().boundsInRoot.left,
            1f,
        )
        rule.onNodeWithText(tr("测试连接", "Test connection")).performScrollTo()
        assertEquals(
            left,
            rule
                .onNodeWithTag("ai-test-label", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .left,
            1f,
        )
    }

    private fun assertReviewEndpointSingleLine() {
        val endpoint = rule.onNodeWithTag("ai-review-endpoint").performScrollTo()
        endpoint.assertTextEquals(
            tr(
                "接收地址：https://api.deepseek.com/v1/chat/completions",
                "Destination: https://api.deepseek.com/v1/chat/completions",
            )
        )
        val layouts = mutableListOf<TextLayoutResult>()
        val node = endpoint.fetchSemanticsNode()
        assertTrue(
            requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(layouts)
        )
        assertEquals(1, layouts.single().lineCount)
        val range = node.config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(range.maxValue() > 0)
        endpoint.performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
        rule.waitUntil(5_000) {
            val current =
                endpoint.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
            current.value() >= current.maxValue()
        }
        endpoint.performSemanticsAction(SemanticsActions.ScrollBy) { it(-10_000f, 0f) }
        rule.waitUntil(5_000) {
            endpoint
                .fetchSemanticsNode()
                .config[SemanticsProperties.HorizontalScrollAxisRange]
                .value() == 0f
        }
    }

    private fun assertReviewNotesAlignment() {
        val row = rule.onNodeWithTag("ai-include-notes")
        row.performScrollTo().assertIsOff()
        val touchBounds = row.getUnclippedBoundsInRoot()
        assertTrue(touchBounds.bottom - touchBounds.top >= 48.dp)
        fun checkAlignment() {
            row.performScrollTo()
            val checkbox =
                rule
                    .onNodeWithTag("ai-include-notes-checkbox", useUnmergedTree = true)
                    .fetchSemanticsNode()
            val label =
                rule
                    .onNodeWithTag("ai-include-notes-text", useUnmergedTree = true)
                    .fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(
                requireNotNull(label.config[SemanticsActions.GetTextLayoutResult].action)
                    .invoke(layouts)
            )
            val text = layouts.single()
            val center = label.boundsInRoot.top + (text.getLineTop(0) + text.getLineBottom(0)) / 2
            assertEquals(center, checkbox.boundsInRoot.center.y, 1.5f)
            val left = checkbox.boundsInRoot.left
            rule.onNodeWithTag("ai-review-scope").performScrollTo()
            assertEquals(
                left,
                rule.onNodeWithTag("ai-review-scope").fetchSemanticsNode().boundsInRoot.left,
                1f,
            )
        }
        checkAlignment()
        row.performScrollTo().performClick().assertIsOn()
        rule
            .onNodeWithTag("ai-review-scope")
            .assertTextEquals(
                tr(
                    "将发送日期、心情、感受和日记文字。",
                    "Dates, moods, feelings and notes will be sent.",
                )
            )
        checkAlignment()
        row.performScrollTo().performClick().assertIsOff()
        row.performScrollTo()
    }

    private fun assertCompleteText(text: String) {
        val node = rule.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(
            requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(layouts)
        )
        val layout = layouts.single()
        assertFalse(layout.didOverflowHeight)
        repeat(layout.lineCount) { assertFalse(layout.isLineEllipsized(it)) }
    }
}
