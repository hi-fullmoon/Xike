package com.xike.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.JournalLabelKind
import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.journalPeriodSummary
import com.xike.app.feature.insights.localJournalReview
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Read-only acceptance against an existing app-created legacy record. Never seeds journal data. */
class JournalTopicRemovalUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun retiredTopicsStayHiddenAndHistoricalDataSurvivesAnotherProcess() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("topicRemovalPhase")
        assumeTrue(phase == "check" || phase == "verify")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context).also { it.initialize(loadEntries = false) }
        val entries = store.entries()
        val catalog = store.readRepository.libraryState(0).labels
        val preferences =
            context.getSharedPreferences("topic-removal-validation", Context.MODE_PRIVATE)
        fun fingerprint(): String {
            val source =
                store.entries().sortedBy { it.id }.joinToString { it.toJson().toString() } +
                    catalog.sortedBy { it.id }.joinToString { it.toJson().toString() }
            return MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString(
                ""
            ) {
                "%02x".format(it)
            }
        }
        if (phase == "verify") {
            assertEquals(preferences.getString("baseline", null), fingerprint())
            return@runBlocking
        }
        val original = entries.firstOrNull { entry ->
            entry.labels.any { it.kind == JournalLabelKind.TOPIC }
        }
        checkNotNull(original) {
            "Requires an existing app-created legacy record; do not inject data"
        }
        val baseline = fingerprint()
        check(preferences.edit().putString("baseline", baseline).commit())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            rule.onNodeWithText(localizedText("再留下一点")).assertDoesNotExist()
            rule.onNodeWithText(tr("活动", "Activities")).assertDoesNotExist()
            rule.onNodeWithText(tr("管理活动", "Manage activities")).assertDoesNotExist()
            rule.onNodeWithText(tr("管理主题", "Manage topics")).assertDoesNotExist()

            rule.onNodeWithTag("navigation-archive").performClick()
            rule.onNodeWithText(localizedText("时间流")).performClick()
            rule.onNodeWithContentDescription(localizedText("打开筛选")).performClick()
            rule.onNodeWithText(tr("活动", "Activities")).assertDoesNotExist()
            rule.onNodeWithText(localizedText("主题")).assertDoesNotExist()
            val card = "archive-entry:${original.id}"
            rule.onNodeWithTag("archive-list").performScrollToNode(hasTestTag(card))
            rule.onNodeWithTag(card).performClick()
            rule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
            rule.onNodeWithText(localizedText("主题")).assertDoesNotExist()
            rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
            rule.onNodeWithText(tr("活动", "Activities")).assertDoesNotExist()
            rule.onNodeWithText(localizedText("再留下一点")).assertDoesNotExist()
            rule.onNodeWithText(localizedText("主题")).assertDoesNotExist()
            rule.onNodeWithContentDescription(localizedText("取消编辑")).performClick()
            rule.onNodeWithContentDescription(localizedText("关闭记录详情")).performClick()

            rule.onNodeWithTag("navigation-insights").performClick()
            rule
                .onNodeWithTag("insights-list")
                .performScrollToNode(hasText(localizedText("不上传，不评判")))
            rule.onNodeWithText(localizedText("不上传，不评判")).assertIsDisplayed()
            rule.onNodeWithText(tr("记录中的活动", "Recorded activities")).assertDoesNotExist()
            rule.onNodeWithText(localizedText("反复出现的主题")).assertDoesNotExist()
            scenario.recreate()
            rule.waitUntil(20_000) {
                rule.onAllNodesWithTag("navigation-insights").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(localizedText("反复出现的主题")).assertDoesNotExist()
        }
        val summary = journalPeriodSummary(entries, InsightsPeriod.YEAR)
        assertFalse(localJournalReview(summary).text.contains(tr("主题", "topic"), ignoreCase = true))
        assertFalse(
            localJournalReview(summary).text.contains(tr("活动", "activit"), ignoreCase = true)
        )
        assertEquals(baseline, fingerprint())
        assertEquals(original.labels, store.readRepository.entry(original.id)?.labels)
    }
}
