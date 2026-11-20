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
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ai.AiReviewOperations
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSettingsStore
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.feature.insights.AiReviewDialog
import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.JournalPeriodSummary
import com.xike.app.feature.insights.dateRangeLabel
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
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("existingAiPreview") == "true")
        val language =
            if (arguments.getString("language") == "en") AppLanguage.ENGLISH
            else AppLanguage.CHINESE
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
            languagePreferences.language = language
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
                val preview = rule.onNodeWithText(tr("预览发送内容", "Preview content"))
                val settings = rule.onNodeWithContentDescription(tr("配置", "Settings"))
                val save = rule.onNodeWithText(tr("保存", "Save"))
                val confirmation = rule.onNodeWithTag("ai-preview-heading")
                val reason = rule.onNodeWithTag("ai-send-disabled")
                val fullRequest = rule.onNodeWithTag("ai-preview")
                val expand = rule.onNodeWithText(tr("查看完整请求", "View full request"))
                val collapse = rule.onNodeWithText(tr("收起完整请求", "Hide full request"))
                fun preparePreview() {
                    preview.assertIsEnabled().performClick()
                    rule.waitUntil(15_000) { confirmation.isDisplayed() }
                }
                fun saveSettings() {
                    rule.waitUntil(5_000) { runCatching { save.assertIsEnabled() }.isSuccess }
                    save.performClick()
                }
                rule.waitUntil(15_000) { runCatching { preview.assertIsEnabled() }.isSuccess }
                val window = rule.onNode(isDialog()).fetchSemanticsNode().boundsInWindow
                val close =
                    rule
                        .onNodeWithContentDescription(tr("关闭", "Close"))
                        .fetchSemanticsNode()
                        .boundsInWindow
                capture("before")
                preparePreview()
                rule
                    .onNodeWithTag("ai-review-endpoint")
                    .assertIsDisplayed()
                    .assertTextEquals("https://api.deepseek.com/v1/chat/completions")
                rule.onNodeWithText(summary.dateRangeLabel()).performScrollTo().assertIsDisplayed()
                rule
                    .onNodeWithText(
                        tr(
                            "${summary.entryCount} 条记录 · 不含日记原文",
                            "${summary.entryCount} entries · Journal notes excluded",
                        )
                    )
                    .performScrollTo()
                    .assertIsDisplayed()
                reason
                    .assertIsDisplayed()
                    .assertTextEquals(tr("请在配置中填写 API Key。", "Add an API Key in Settings."))
                expand.performScrollTo().assertIsDisplayed()
                fullRequest.assertDoesNotExist()
                assertStable(window, close)
                capture("confirmation")

                settings.performClick()
                rule.onNodeWithText(tr("取消", "Cancel")).performClick()
                confirmation.performScrollTo().assertIsDisplayed()
                fullRequest.assertDoesNotExist()
                settings.performClick()
                saveSettings()
                confirmation.performScrollTo().assertIsDisplayed()
                fullRequest.assertDoesNotExist()
                assertStable(window, close)

                expand.performScrollTo().performClick()
                collapse.assertExists()
                assertRequestScope(summary, includeNotes = false)
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
                reason.assertIsDisplayed()
                // Do not capture the end of the request: it contains private journal fields.
                content.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -1_000_000f) }
                rule.waitUntil(5_000) {
                    content
                        .fetchSemanticsNode()
                        .config[SemanticsProperties.VerticalScrollAxisRange]
                        .value() == 0f
                }
                assertStable(window, close)
                collapse.performScrollTo().performClick()
                expand.assertExists()
                fullRequest.assertDoesNotExist()
                assertFalse(operations.load().canUse)
                assertEquals("", operations.load().apiKey)
                rule.onNodeWithText(tr("返回", "Back")).performClick()
                confirmation.assertDoesNotExist()
                rule.onNodeWithTag("ai-send").assertDoesNotExist()
                rule.onNodeWithTag("ai-include-notes").performScrollTo().performClick()
                preparePreview()
                rule
                    .onNodeWithText(
                        tr(
                            "${summary.entryCount} 条记录 · 包含日记原文",
                            "${summary.entryCount} entries · Journal notes included",
                        )
                    )
                    .performScrollTo()
                    .assertIsDisplayed()
                fullRequest.assertDoesNotExist()
                expand.performScrollTo().performClick()
                assertRequestScope(summary, includeNotes = true)

                settings.performClick()
                val model = rule.onNodeWithTag("ai-model")
                rule.waitUntil(5_000) { runCatching { model.assertIsEnabled() }.isSuccess }
                model.performScrollTo().performTextReplacement("deepseek-reasoner")
                // A local random value exercises the encrypted settings without contacting a
                // service.
                rule
                    .onNodeWithTag("ai-key")
                    .performScrollTo()
                    .performTextReplacement(UUID.randomUUID().toString())
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                saveSettings()
                confirmation.assertDoesNotExist()
                preparePreview()
                reason
                    .assertIsDisplayed()
                    .assertTextEquals(tr("请在配置中同意发送说明。", "Accept the data notice in Settings."))
                rule.onNodeWithTag("ai-send").assertIsNotEnabled()

                settings.performClick()
                rule.waitUntil(5_000) { runCatching { save.assertIsEnabled() }.isSuccess }
                rule.onNodeWithTag("ai-consent").performScrollTo().performClick().assertIsOn()
                rule
                    .onNodeWithTag("ai-enabled")
                    .performScrollTo()
                    .assertIsOn()
                    .performClick()
                    .assertIsOff()
                saveSettings()
                confirmation.performScrollTo().assertIsDisplayed()
                reason
                    .assertIsDisplayed()
                    .assertTextEquals(tr("请在配置中启用 AI 回顾。", "Enable AI review in Settings."))
                rule.onNodeWithTag("ai-send").assertIsNotEnabled()

                settings.performClick()
                rule.waitUntil(5_000) { runCatching { save.assertIsEnabled() }.isSuccess }
                rule.onNodeWithTag("ai-enabled").performScrollTo().performClick().assertIsOn()
                saveSettings()
                confirmation.performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("ai-send").assertIsEnabled()
                reason.assertDoesNotExist()
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
        val currentClose =
            rule.onNodeWithContentDescription(tr("关闭", "Close")).fetchSemanticsNode().boundsInWindow
        assertEquals(close.top, currentClose.top, 1f)
        assertEquals(close.bottom, currentClose.bottom, 1f)
        rule.onNodeWithContentDescription(tr("配置", "Settings")).assertIsDisplayed()
        rule.onNodeWithTag("ai-send").assertIsDisplayed().assertIsNotEnabled()
    }

    private fun assertRequestScope(summary: JournalPeriodSummary, includeNotes: Boolean) {
        val node = rule.onNodeWithTag("ai-preview").fetchSemanticsNode()
        val body = node.config[SemanticsProperties.Text].single().text
        val payload =
            JSONObject(
                JSONObject(body).getJSONArray("messages").getJSONObject(1).getString("content")
            )
        assertEquals(summary.startDate.toString(), payload.getString("startDate"))
        assertEquals(summary.endDate.toString(), payload.getString("endDate"))
        assertEquals(summary.entryCount, payload.getInt("entryCount"))
        assertEquals(includeNotes, payload.getBoolean("includesNotes"))
        val records = payload.getJSONArray("entries")
        repeat(records.length()) {
            assertEquals(includeNotes, records.getJSONObject(it).has("note"))
        }
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
