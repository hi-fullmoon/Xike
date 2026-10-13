package com.xike.app

import com.xike.app.data.draft.JournalDraftStore

import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.journalPeriodSummary

import com.xike.app.data.Mood
import com.xike.app.ui.components.formatAudioDuration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Independent device only. Prepare the photo and recording through the real app first. */
class JournalRefactoringUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun realMomentDraftSurvivesRecreationNavigationAndSave() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("refactoringIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(20_000) { !model.isLoading }
        check(model.draft.isEmpty) { "Use the independent device with an empty draft." }
        val note = "Xike moment acceptance ${UUID.randomUUID()}"
        val originalIds = model.entries.map { it.id }.toSet()
        rule.onNodeWithTag("navigation-home").performClick()
        rule.onNodeWithText(Mood.CALM.label).performScrollTo().performClick()
        rule.onNodeWithContentDescription(localizedText("此刻的注脚"))
            .performScrollTo().performTextInput(note)
        rule.onNodeWithText(localizedText("完成")).performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription(localizedText("此刻的注脚"))
            .performScrollTo().assertTextContains(note)
        assertEquals(note, JournalDraftStore(rule.activity).load().note)
        rule.onNodeWithTag("navigation-archive").performClick()
        rule.onNodeWithTag("navigation-home").performClick()
        rule.onNodeWithContentDescription(localizedText("此刻的注脚"))
            .performScrollTo().assertTextContains(note)
        rule.onNodeWithText(localizedText("记下此刻")).performClick()
        rule.waitUntil(15_000) { model.entries.any { it.id !in originalIds && it.note == note } }
        val saved = JournalStore(rule.activity).entries().single { it.id !in originalIds && it.note == note }
        assertEquals(Mood.CALM, saved.mood)
        assertTrue(model.draft.isEmpty)
        assertTrue(JournalDraftStore(rule.activity).load().isEmpty)
    }

    @Test
    fun appCreatedMediaSurvivesEditorRecreationSaveAndCancel() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("refactoringIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(20_000) { !model.isLoading }
        val entry = model.entries.single { it.imageFileNames.isNotEmpty() && it.audio != null }
        val entryLabel = entry.note.ifBlank { entry.mood.label }
        val entryCard = hasText(entryLabel) and hasText(tr(
            "语音 · ${formatAudioDuration(requireNotNull(entry.audio).durationMillis)}",
            "Audio · ${formatAudioDuration(requireNotNull(entry.audio).durationMillis)}",
        ))
        rule.onNodeWithTag("navigation-archive").performClick()
        rule.onNode(hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(entryCard)
        rule.onNode(entryCard).performScrollTo().performClick()
        rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
        val savedNote = "Xike editor acceptance ${UUID.randomUUID()}"
        rule.onNodeWithContentDescription(localizedText("此刻的注脚"))
            .performScrollTo().performTextReplacement(savedNote)
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription(localizedText("此刻的注脚"))
            .performScrollTo().assertTextContains(savedNote)
        rule.onNodeWithContentDescription(tr("已有照片 1", "Existing photo 1"))
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(localizedText("保存")).performClick()
        awaitEditorClosed()
        val saved = JournalStore(rule.activity).entries().single { it.id == entry.id }
        assertEquals(savedNote, saved.note)
        assertEquals(entry.imageFileNames, saved.imageFileNames)
        assertEquals(entry.audio, saved.audio)

        rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
        rule.onNodeWithContentDescription(localizedText("此刻的注脚"))
            .performScrollTo().performTextReplacement("This change should be cancelled")
        rule.onNodeWithContentDescription(localizedText("取消编辑")).performClick()
        rule.onNodeWithText(localizedText("放弃未保存的修改？")).assertIsDisplayed()
        rule.onNodeWithText(localizedText("放弃修改")).performClick()
        awaitEditorClosed()
        assertEquals(saved, JournalStore(rule.activity).entries().single { it.id == entry.id })
        rule.onNode(hasText(savedNote) and hasAnyAncestor(hasTestTag("journal-detail-body")))
            .performScrollTo().assertIsDisplayed()
        assertTrue(JournalStore(rule.activity).openAudio(requireNotNull(saved.audio).fileName)!!.use { it.read() >= 0 })
    }

    @Test
    fun insightsOpensSharedMediaDetailAndRestoresIt() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("refactoringIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(15_000) { !model.isLoading }
        val entry = model.entries.single { it.imageFileNames.isNotEmpty() && it.audio != null }
        assertTrue(journalPeriodSummary(model.entries, InsightsPeriod.WEEK).entryIds.contains(entry.id))
        rule.onNodeWithText(localizedText("轨迹")).performClick()
        rule.onNodeWithTag("insights-list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("insights-overview-headline"))
        rule.onNodeWithTag("insights-overview-headline", useUnmergedTree = true)
            .performTouchInput { click() }
        rule.onNode(hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            !hasTestTag("insights-list"))
            .performScrollToNode(hasTestTag("insight-source-${entry.id}"))
        rule.onNodeWithTag("insight-source-${entry.id}").performClick()
        rule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
        rule.onNode(hasText(entry.note) and hasAnyAncestor(hasTestTag("journal-detail-body")))
            .performScrollTo().assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
        rule.onNode(hasContentDescription(tr("日记第 1 张照片", "Journal photo 1")) and
            hasAnyAncestor(hasTestTag("journal-detail-body")))
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(localizedText("关闭记录详情")).performClick()
        rule.onNodeWithTag("insight-source-${entry.id}").assertIsDisplayed()
        assertEquals(entry, JournalStore(rule.activity).entries().single { it.id == entry.id })
    }

    private fun awaitEditorClosed() {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithContentDescription(localizedText("取消编辑")).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithContentDescription(localizedText("编辑记录")).assertIsDisplayed()
    }
}
