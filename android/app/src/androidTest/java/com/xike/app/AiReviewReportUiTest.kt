package com.xike.app

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ai.AiSource
import com.xike.app.ai.aiReviewInput
import com.xike.app.ai.parseAiReview
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.feature.insights.AiReviewReport
import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.journalPeriodSummary
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikeTheme
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AiReviewReportUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun editorialReportDisplaysCoverChaptersCitationsAndReflection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = LanguagePreferences(context)
        val previousLanguage = preferences.language
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun epoch(daysAgo: Long, hour: Int): Long =
            today.minusDays(daysAgo).atTime(hour, 15).atZone(zone).toInstant().toEpochMilli()

        val entries =
            listOf(
                JournalEntry(
                    id = "e1",
                    createdAt = epoch(2, 22),
                    mood = Mood.ABOVE_AVERAGE,
                    note = "晚间散步。",
                    moodScaleVersion = 1,
                ),
                JournalEntry(
                    id = "e2",
                    createdAt = epoch(0, 19),
                    mood = Mood.NEUTRAL,
                    note = "傍晚休息了一会儿。",
                    moodScaleVersion = 1,
                ),
            )
        val summary = journalPeriodSummary(entries, InsightsPeriod.WEEK, today)
        val input = aiReviewInput(entries, summary, includeNotes = true, zone = zone)
        val rawReview =
            JSONObject()
                .put("headline", "在忙碌缝隙里慢下来")
                .put("summary", "这周的记录主要集中在傍晚与晚间。")
                .put("keywords", JSONArray().put("晚间安顿").put("慢下来"))
                .put(
                    "observations",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("tag", "情绪节奏")
                                .put("title", "夜晚更容易松弛")
                                .put("text", "记录显示你在晚间状态更平稳。")
                                .put("references", JSONArray().put(1).put(2))
                        ),
                )
                .put("questions", JSONArray().put("这周哪一个停下来的瞬间让你觉得最轻松？"))
                .toString()
        val review = parseAiReview(rawReview, input)
        var openedSource: AiSource? = null

        try {
            preferences.language = AppLanguage.CHINESE
            AppLocale.select(context, AppLanguage.CHINESE)
            if (Build.VERSION.SDK_INT >= 33) {
                rule.waitUntil(5_000) {
                    context.resources.configuration.locales[0].language ==
                        AppLanguage.CHINESE.locale.language
                }
            }
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        CompositionLocalProvider(
                            LocalContext provides activity.forLanguage(AppLanguage.CHINESE)
                        ) {
                            XikeTheme(AppTheme.PINE, AppStyle.BREATHE) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                ) {
                                    Column(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .testTag("ai-review-content")
                                                .padding(
                                                    horizontal = XikeScreenHorizontalPadding,
                                                    vertical = XikeScreenVerticalPadding,
                                                ),
                                        verticalArrangement = Arrangement.spacedBy(XikeContentGap),
                                    ) {
                                        AiReviewReport(
                                            summary = summary,
                                            review = review,
                                            sources = input.sources,
                                            onOpenSource = { openedSource = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                rule.onNodeWithTag("ai-review-cover").assertIsDisplayed()
                rule.onNodeWithText("在忙碌缝隙里慢下来").assertIsDisplayed()
                rule.onNodeWithText("晚间安顿").assertIsDisplayed()
                rule.onNodeWithText("夜晚更容易松弛").performScrollTo().assertIsDisplayed()

                val firstCitation = "${input.sources.first().date} · #1"
                rule.onNodeWithText(firstCitation).performScrollTo().performClick()
                assertEquals(input.sources.first().entryId, openedSource?.entryId)

                rule.onNodeWithTag("ai-review-content").performSemanticsAction(
                    SemanticsActions.ScrollBy
                ) {
                    it(0f, 100_000f)
                }
                rule.onNodeWithTag("ai-review-questions").assertIsDisplayed()
            }
        } finally {
            preferences.language = previousLanguage
            AppLocale.select(context, previousLanguage)
        }
    }
}
