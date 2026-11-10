package com.xike.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiReviewPreviewUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun staleNotesClickAfterOpeningSettingsCannotChangeSendingScope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xike-ai-preview-${UUID.randomUUID()}"
        val operations = AiReviewOperations(AiSettingsStore(context, name))
        val summary = journalPeriodSummary(emptyList(), InsightsPeriod.WEEK)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        CompositionLocalProvider(LocalAiReviewOperations provides operations) {
                            XikeTheme(AppTheme.PINE, AppStyle.BREATHE) {
                                AiReviewDialog(
                                    summary = summary,
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
                rule.waitUntil(15_000) { runCatching { save.assertIsEnabled() }.isSuccess }
                save.performClick()
                val notes = rule.onNodeWithTag("ai-include-notes")
                rule.waitUntil(15_000) { runCatching { notes.assertIsOff() }.isSuccess }
                val notesClick =
                    requireNotNull(
                        notes.fetchSemanticsNode().config[SemanticsActions.OnClick].action
                    )
                val settingsClick =
                    requireNotNull(
                        rule
                            .onNodeWithText(tr("配置", "Settings"))
                            .fetchSemanticsNode()
                            .config[SemanticsActions.OnClick]
                            .action
                    )
                // Deliver both real callbacks before the next recomposition hides the old controls.
                rule.runOnIdle {
                    assertTrue(settingsClick())
                    assertTrue(notesClick())
                }
                rule.onNodeWithText(tr("AI 接口配置", "AI service settings")).assertIsDisplayed()
                rule.onNodeWithText(tr("取消", "Cancel")).performClick()
                notes.performScrollTo().assertIsOff()
                notes.performClick().assertIsOn()
                notes.performClick().assertIsOff()
            }
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
