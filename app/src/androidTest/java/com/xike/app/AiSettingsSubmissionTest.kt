package com.xike.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ai.AiReviewOperations
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSettingsStore
import com.xike.app.ai.LocalAiReviewOperations
import com.xike.app.feature.settings.AiSettingsDialog
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeTheme
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiSettingsSubmissionTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun cachedSettingsLoadAfterRepeatedDialogOpenings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xike-ai-reopen-${UUID.randomUUID()}"
        val operations = AiReviewOperations(AiSettingsStore(context, name))
        val settings = AiSettings(address = "https://api.deepseek.com/v1", model = "deepseek-flash")
        val visible = mutableStateOf(true)
        try {
            runBlocking { assertTrue(operations.save(settings).isSuccess) }
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        CompositionLocalProvider(LocalAiReviewOperations provides operations) {
                            XikeTheme(AppTheme.PINE, AppStyle.BREATHE) {
                                if (visible.value) {
                                    AiSettingsDialog(
                                        onDismiss = { visible.value = false },
                                        onSaved = { error("Cancelling must not save settings") },
                                    )
                                }
                            }
                        }
                    }
                }
                repeat(3) {
                    rule.runOnIdle { visible.value = true }
                    rule.waitUntil(15_000) {
                        runCatching {
                            rule.onNodeWithText(tr("保存", "Save")).assertIsEnabled()
                        }
                            .isSuccess
                    }
                    rule.onNodeWithTag("ai-address").assertTextContains(settings.address)
                    rule.onNodeWithTag("ai-model").assertTextContains(settings.model)
                    rule.onNodeWithText(tr("取消", "Cancel")).performClick()
                    rule.waitUntil(5_000) {
                        rule.onAllNodesWithTag("ai-settings-dialog").fetchSemanticsNodes().isEmpty()
                    }
                }
                assertEquals(settings, runBlocking { operations.load() })
            }
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    @Test
    fun settingsDismissOnBackgroundAndLockWhileCompositionIsRetained() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xike-ai-lifecycle-${UUID.randomUUID()}"
        val operations = AiReviewOperations(AiSettingsStore(context, name))
        val dismissed = AtomicInteger()
        val locked = mutableStateOf(false)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        CompositionLocalProvider(
                            LocalAiReviewOperations provides operations,
                            LocalSystemActivityCallbacks provides
                                SystemActivityCallbacks(isLocked = locked.value),
                        ) {
                            XikeTheme(AppTheme.PINE, AppStyle.BREATHE) {
                                AiSettingsDialog(
                                    onDismiss = { dismissed.incrementAndGet() },
                                    onSaved = { error("No settings should be saved") },
                                )
                            }
                        }
                    }
                }
                rule.waitUntil(15_000) {
                    runCatching {
                        rule.onNodeWithText(tr("保存", "Save")).assertIsEnabled()
                    }
                        .isSuccess
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(1, dismissed.get())
                scenario.moveToState(Lifecycle.State.RESUMED)
                rule.runOnIdle { locked.value = true }
                rule.waitUntil(5_000) { dismissed.get() == 2 }
                assertEquals(AiSettings(), runBlocking { operations.load() })
            }
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    @Test
    fun repeatedSaveBeforeRecompositionRunsOnceAndAllowsAnotherSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xike-ai-submission-${UUID.randomUUID()}"
        val store = AiSettingsStore(context, name)
        val operations = AiReviewOperations(store)
        val completed = AtomicInteger()
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        CompositionLocalProvider(LocalAiReviewOperations provides operations) {
                            XikeTheme(AppTheme.PINE, AppStyle.BREATHE) {
                                AiSettingsDialog(
                                    onDismiss = {},
                                    onSaved = { completed.incrementAndGet() },
                                )
                            }
                        }
                    }
                }
                val save = rule.onNodeWithText(tr("保存", "Save"))
                rule.waitUntil(15_000) { runCatching { save.assertIsEnabled() }.isSuccess }
                repeat(2) { batch ->
                    val action =
                        requireNotNull(
                            save
                                .assertIsEnabled()
                                .fetchSemanticsNode()
                                .config[SemanticsActions.OnClick]
                                .action
                        )
                    // Invoke the real button repeatedly within one UI event, before recomposition.
                    rule.runOnIdle { repeat(20) { assertTrue(action()) } }
                    rule.waitUntil(15_000) { completed.get() >= batch + 1 }
                    // Drain the real store's serialized writes, then their UI continuations.
                    runBlocking { assertTrue(operations.save(AiSettings()).isSuccess) }
                    rule.runOnIdle { assertEquals(batch + 1, completed.get()) }
                    save.assertIsEnabled()
                    assertEquals(AiSettings(), store.read())
                }
            }
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
