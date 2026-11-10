package com.xike.app

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ai.AiReviewOperations
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSettingsStore
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.feature.insights.AiReviewDialog
import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Read existing records only. Run explicitly on an isolated copy; never sends a request. */
class AiReviewLocalPreviewUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun localPreviewWithoutKeyPreservesWindowAndScrollsToBothEnds() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("existingAiPreview") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = JournalReadRepository { JournalDatabase.get(context) }
        val summary =
            InsightsPeriod.entries
                .map { repository.insights(it, LocalDate.now()) }
                .firstOrNull { it.entryCount in 1..500 }
        checkNotNull(summary) {
            "Requires existing records in a supported period; do not seed data"
        }
        val name = "xike-local-preview-${UUID.randomUUID()}"
        val operations = AiReviewOperations(AiSettingsStore(context, name))
        val languagePreferences = LanguagePreferences(context)
        val previousLanguage = languagePreferences.language
        try {
            // Real encrypted configuration with no Key, consent or AI enablement.
            check(
                operations
                    .save(
                        AiSettings(
                            address = "https://api.deepseek.com/v1",
                            model = "deepseek-flash",
                        )
                    )
                    .isSuccess
            )
            languagePreferences.language = AppLanguage.CHINESE
            AppLocale.select(context, AppLanguage.CHINESE)
            if (Build.VERSION.SDK_INT >= 33) {
                rule.waitUntil(5_000) {
                    context.resources.configuration.locales[0].language == "zh"
                }
            }
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        CompositionLocalProvider(
                            LocalAiReviewOperations provides operations,
                            LocalContext provides activity.forLanguage(AppLanguage.CHINESE),
                        ) {
                            XikeTheme(AppTheme.PINE, AppStyle.BREATHE) {
                                AiReviewDialog(
                                    summary = summary,
                                    entries = emptyList(),
                                    readRepository = repository,
                                    onReadError = { error("Existing records could not be read") },
                                    onOpenSource = { error("No source should be opened") },
                                    sourceOpen = false,
                                    onDismiss = {},
                                )
                            }
                        }
                    }
                }
                val preview = rule.onNodeWithText("预览发送内容")
                rule.waitUntil(15_000) { runCatching { preview.assertIsEnabled() }.isSuccess }
                val window = rule.onNode(isDialog()).fetchSemanticsNode().boundsInWindow
                val close = rule.onNodeWithText("关闭").fetchSemanticsNode().boundsInWindow
                capture("before")
                preview.performClick()
                rule.waitUntil(15_000) {
                    rule.onAllNodesWithTag("ai-preview").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithTag("ai-send").assertIsNotEnabled()
                assertStable(window, close)
                captureStaticPreview("after-top")

                rule.onNodeWithTag("ai-preview-heading").performScrollTo()
                assertStable(window, close)
                captureStaticPreview("request-heading")
                rule.onNodeWithTag("ai-review-content").performTouchInput {
                    swipeUp(startY = height * 0.75f, endY = height * 0.35f)
                }
                assertStable(window, close)
                captureStaticPreview("request-scroll")

                val content = rule.onNodeWithTag("ai-review-content")
                content.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }
                rule.waitUntil(5_000) {
                    val range =
                        content
                            .fetchSemanticsNode()
                            .config[SemanticsProperties.VerticalScrollAxisRange]
                    range.maxValue() > 0 && range.value() == range.maxValue()
                }
                assertStable(window, close)
                // Do not capture the end of the request: it contains private journal fields.
                content.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -1_000_000f) }
                rule.waitUntil(5_000) {
                    content
                        .fetchSemanticsNode()
                        .config[SemanticsProperties.VerticalScrollAxisRange]
                        .value() == 0f
                }
                assertStable(window, close)
                assertFalse(operations.load().canUse)
                assertEquals("", operations.load().apiKey)
                rule.onNodeWithTag("ai-include-notes").performScrollTo().performClick()
                assertTrue(rule.onAllNodesWithTag("ai-preview").fetchSemanticsNodes().isEmpty())
                preview.assertIsEnabled()
            }
        } catch (failure: Throwable) {
            // Compose assertion messages may include the full request. Preserve locations only.
            throw AssertionError(
                    "Local AI preview verification failed (${failure.javaClass.simpleName}); journal content omitted"
                )
                .apply { stackTrace = failure.stackTrace }
        } finally {
            context.deleteSharedPreferences(name)
            languagePreferences.language = previousLanguage
            AppLocale.select(context, previousLanguage)
        }
        Unit
    }

    private fun assertStable(window: Rect, close: Rect) {
        rule.waitForIdle()
        assertEquals(window, rule.onNode(isDialog()).fetchSemanticsNode().boundsInWindow)
        val currentClose = rule.onNodeWithText("关闭").fetchSemanticsNode().boundsInWindow
        assertEquals(close.top, currentClose.top, 1f)
        assertEquals(close.bottom, currentClose.bottom, 1f)
        rule.onNodeWithText("配置").assertIsDisplayed()
        rule.onNodeWithTag("ai-send").assertIsDisplayed().assertIsNotEnabled()
    }

    private fun captureStaticPreview(state: String) {
        rule.waitForIdle()
        val preview = rule.onNodeWithTag("ai-preview")
        val node = preview.fetchSemanticsNode()
        val body = node.config[SemanticsProperties.Text].single().text
        val privateContent =
            JSONObject(body).getJSONArray("messages").getJSONObject(1).getString("content")
        val privateOffset = body.indexOf(JSONObject.quote(privateContent))
        check(privateOffset >= 0)
        val layouts = mutableListOf<TextLayoutResult>()
        check(
            requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(layouts)
        )
        val layout = layouts.single()
        val density =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .resources
                .displayMetrics
                .density
        val privateTop =
            preview.getUnclippedBoundsInRoot().top.value * density +
                layout.getLineTop(layout.getLineForOffset(privateOffset))
        val viewportBottom =
            rule.onNodeWithTag("ai-review-content").fetchSemanticsNode().boundsInRoot.bottom
        check(privateTop >= viewportBottom) {
            "Private journal content must stay outside screenshots"
        }
        capture(state)
    }

    private fun capture(state: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        captureAiDialogWindow(
            File(
                context.getExternalFilesDir(null),
                "ai-local-preview-${config.screenWidthDp}-${config.fontScale}-$state.png",
            )
        )
    }
}
