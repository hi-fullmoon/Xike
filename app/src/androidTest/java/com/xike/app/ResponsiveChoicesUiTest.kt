package com.xike.app

import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.journalPeriodSummary

import com.xike.app.data.Mood

import com.xike.app.feature.insights.MoodDistributionCard

import com.xike.app.feature.archive.ArchiveViewMode
import com.xike.app.feature.archive.ArchiveHeader
import com.xike.app.feature.archive.ArchiveSearchBar
import com.xike.app.feature.archive.ArchiveControls

import com.xike.app.ui.components.MoodChoices
import com.xike.app.ui.components.JournalTopic
import com.xike.app.ui.components.journalTopics
import com.xike.app.ui.components.MomentTopicsHeader
import com.xike.app.ui.components.TopicChoices
import com.xike.app.ui.components.AppScreen
import com.xike.app.ui.components.XikeNavigationBar

import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeTheme




import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ResponsiveChoicesUiTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val originalLanguage = LanguagePreferences(context).language

    @Before
    fun selectEnglish() {
        composeRule.runOnIdle { AppLocale.select(context, AppLanguage.ENGLISH) }
    }

    @After
    fun restoreLanguage() {
        composeRule.runOnIdle { AppLocale.select(context, originalLanguage) }
    }

    @Test
    fun narrowLargeFontDistributionKeepsEnglishStatisticsReadable() {
        assertDistributionLayout()
    }

    @Test
    fun narrowLargeFontDistributionKeepsChineseStatisticsReadable() {
        composeRule.runOnIdle { AppLocale.select(context, AppLanguage.CHINESE) }
        composeRule.waitForIdle()
        assertDistributionLayout()
    }

    private fun assertDistributionLayout() {
        val selectedStyle = mutableStateOf(AppStyle.BREATHE)
        val viewportWidth = mutableStateOf(240.dp)
        val distribution = journalPeriodSummary(emptyList(), InsightsPeriod.WEEK).moodDistribution
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                XikeTheme(AppTheme.OCEAN, selectedStyle.value) {
                    Column(Modifier.width(viewportWidth.value).verticalScroll(rememberScrollState())
                        .padding(horizontal = XikeScreenHorizontalPadding)) {
                        MoodDistributionCard(distribution) {}
                    }
                }
            }
        }
        listOf(240.dp, 320.dp).forEach { width ->
            AppStyle.entries.forEach { style ->
                composeRule.runOnIdle {
                    viewportWidth.value = width
                    selectedStyle.value = style
                }
                val statistics = tr("0 次 · 0%", "0 entries · 0%")
                assertCompleteLabel(statistics, allowWrap = true)
                Mood.entries.forEach { assertCompleteLabel(it.label) }
                composeRule.onAllNodesWithText(statistics, useUnmergedTree = true)
                    .fetchSemanticsNodes().forEach { node ->
                        val results = mutableListOf<TextLayoutResult>()
                        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
                        assertTrue("Statistics became a narrow text column for $style at $width",
                            results.single().lineCount <= 2)
                    }
            }
        }
    }

    @Test
    fun narrowLargeFontTopicsHeaderKeepsCountAndExplanationComplete() {
        assertTopicsHeaderLayout()
    }

    @Test
    fun narrowLargeFontChineseTopicsHeaderKeepsCountAndExplanationComplete() {
        composeRule.runOnIdle { AppLocale.select(context, AppLanguage.CHINESE) }
        composeRule.waitForIdle()
        assertTopicsHeaderLayout()
    }

    @Test
    fun narrowLargeFontEditingTopicsHeaderKeepsCountAndExplanationComplete() {
        assertTopicsHeaderLayout(highlightSelection = false)
    }

    @Test
    fun narrowLargeFontChineseEditingTopicsHeaderKeepsCountAndExplanationComplete() {
        composeRule.runOnIdle { AppLocale.select(context, AppLanguage.CHINESE) }
        composeRule.waitForIdle()
        assertTopicsHeaderLayout(highlightSelection = false)
    }

    private fun assertTopicsHeaderLayout(highlightSelection: Boolean = true) {
        val selectedStyle = mutableStateOf(AppStyle.BREATHE)
        val selectedTopics = mutableStateOf(emptyList<JournalTopic>())
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                XikeTheme(AppTheme.OCEAN, selectedStyle.value) {
                    Column(Modifier.width(240.dp)) { MomentTopicsHeader(selectedTopics.value.size, highlightSelection) }
                }
            }
        }
        AppStyle.entries.forEach { style ->
            listOf(emptyList(), journalTopics).forEach { selection ->
                composeRule.runOnIdle {
                    selectedStyle.value = style
                    selectedTopics.value = selection
                }
                val countLabel = if (selection.isEmpty()) localizedText("可多选")
                    else tr("已选 ${selection.size}", "${selection.size} selected")
                assertCompleteLabel(localizedText("主题"))
                assertCompleteLabel(localizedText("这一刻与什么有关？"), allowWrap = true)
                assertCompleteLabel(countLabel)
                val explanation = composeRule.onNodeWithText(localizedText("这一刻与什么有关？")).getUnclippedBoundsInRoot()
                val count = composeRule.onNodeWithText(countLabel).getUnclippedBoundsInRoot()
                assertTrue("Explanation overlaps the count for $style",
                    explanation.right + 8.dp <= count.left || explanation.bottom + 8.dp <= count.top)
                assertTrue("Count escapes the header for $style", count.right <= 240.dp)
            }
        }
    }

    @Test
    fun narrowTopicChoicesKeepEveryEnglishLabelComplete() {
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                Column(Modifier.width(320.dp)) { TopicChoices(emptyList()) {} }
            }
        }
        journalTopics.forEach { assertCompleteLabel(localizedText(it.label)) }
    }

    @Test
    fun largeFontMoodChoicesKeepEveryEnglishLabelComplete() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                XikeTheme(AppTheme.OCEAN) {
                    Column(Modifier.width(320.dp)) { MoodChoices(null, true, {}) }
                }
            }
        }
        Mood.entries.forEach { assertCompleteLabel(it.label) }
    }

    @Test
    fun largeFontArchiveModesKeepCompleteLabelsBeforeAndAfterSelectionChanges() {
        val selected = mutableStateOf(ArchiveViewMode.TIMELINE)
        val viewportWidth = mutableStateOf(320.dp)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Column(Modifier.width(viewportWidth.value)) {
                    AppStyle.entries.forEach { style ->
                        XikeTheme(AppTheme.OCEAN, style) {
                            Column(Modifier.width(viewportWidth.value)) {
                                ArchiveControls(selected.value, 1, false, { selected.value = it }, {})
                            }
                        }
                    }
                }
            }
        }
        assertCompleteLabel(localizedText("月历"))
        assertCompleteLabel(localizedText("时间流"))
        composeRule.runOnIdle { viewportWidth.value = 240.dp }
        assertCompleteLabel(localizedText("月历"))
        assertCompleteLabel(localizedText("时间流"))
        assertSelectedArchiveModeFullyVisible("timeline")
        composeRule.runOnIdle { selected.value = ArchiveViewMode.CALENDAR }
        assertCompleteLabel(localizedText("月历"))
        assertCompleteLabel(localizedText("时间流"))
        assertSelectedArchiveModeFullyVisible("calendar")
    }

    @Test
    fun longSearchResultCountDoesNotSplitArchiveTitleInAnyStyle() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    AppStyle.entries.forEach { style ->
                        XikeTheme(AppTheme.OCEAN, style) {
                            ArchiveHeader(tr("找到 0 条", "0 entries found"))
                        }
                    }
                }
            }
        }
        assertCompleteLabel(localizedText("回望"))
    }

    @Test
    fun archiveSearchKeepsItsHeightWhenTextIsEnteredAndCleared() {
        val selectedStyle = mutableStateOf(AppStyle.BREATHE)
        val fontScale = mutableStateOf(1f)
        val query = mutableStateOf("")
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                XikeTheme(AppTheme.OCEAN, selectedStyle.value) {
                    Column(Modifier.width(272.dp)) {
                        ArchiveSearchBar(query.value, { query.value = it }, { query.value = "" })
                    }
                }
            }
        }
        listOf(1f, 2f).forEach { scale ->
            AppStyle.entries.forEach { style ->
                composeRule.runOnIdle {
                    fontScale.value = scale
                    selectedStyle.value = style
                }
                val search = composeRule.onNodeWithTag("archive-search")
                val emptyBounds = search.getUnclippedBoundsInRoot()
                val emptyHeight = (emptyBounds.bottom - emptyBounds.top).value
                search.performTextInput("UI")
                val filledBounds = search.getUnclippedBoundsInRoot()
                assertTrue("Search changed height for $style at $scale", kotlin.math.abs(
                    (filledBounds.bottom - filledBounds.top).value - emptyHeight,
                ) < 0.5f)
                search.performTextClearance()
                val clearedBounds = search.getUnclippedBoundsInRoot()
                assertTrue("Clearing search changed height for $style at $scale", kotlin.math.abs(
                    (clearedBounds.bottom - clearedBounds.top).value - emptyHeight,
                ) < 0.5f)
            }
        }
    }

    @Test
    fun largeFontNavigationKeepsCompleteLabelsInEveryStyle() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Column(Modifier.width(320.dp)) {
                    AppStyle.entries.forEach { style ->
                        XikeTheme(AppTheme.OCEAN, style) {
                            XikeNavigationBar(AppScreen.HOME) {}
                        }
                    }
                }
            }
        }
        AppScreen.entries.forEach { assertCompleteLabel(it.title) }
    }

    @Test
    fun initialSettingsSelectionIsFullyInsideEveryNavigationViewport() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Column(Modifier.width(320.dp)) {
                    AppStyle.entries.forEach { style ->
                        XikeTheme(AppTheme.OCEAN, style) {
                            XikeNavigationBar(AppScreen.SETTINGS) {}
                        }
                    }
                }
            }
        }
        assertSelectedNavigationFullyVisible(AppScreen.SETTINGS)
    }

    @Test
    fun selectionRemainsVisibleAfterFontWidthAndPageChanges() {
        val fontScale = mutableStateOf(1f)
        val viewportWidth = mutableStateOf(320.dp)
        val selected = mutableStateOf(AppScreen.SETTINGS)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                Column(Modifier.width(viewportWidth.value)) {
                    AppStyle.entries.forEach { style ->
                        XikeTheme(AppTheme.OCEAN, style) {
                            XikeNavigationBar(selected.value) { selected.value = it }
                        }
                    }
                }
            }
        }
        assertSelectedNavigationFullyVisible(AppScreen.SETTINGS)
        composeRule.runOnIdle { fontScale.value = 2f }
        assertSelectedNavigationFullyVisible(AppScreen.SETTINGS)
        composeRule.runOnIdle { viewportWidth.value = 240.dp }
        assertSelectedNavigationFullyVisible(AppScreen.SETTINGS)
        composeRule.runOnIdle { selected.value = AppScreen.HOME }
        assertSelectedNavigationFullyVisible(AppScreen.HOME)
        composeRule.runOnIdle { selected.value = AppScreen.SETTINGS }
        assertSelectedNavigationFullyVisible(AppScreen.SETTINGS)
    }

    private fun assertSelectedArchiveModeFullyVisible(mode: String) {
        val nodes = composeRule.onAllNodesWithTag("archive-mode-$mode")
        assertEquals(3, nodes.fetchSemanticsNodes().size)
        repeat(3) { index ->
            val node = nodes[index].assertIsDisplayed().assertIsSelected()
            val full = node.getUnclippedBoundsInRoot()
            val visible = node.getBoundsInRoot()
            assertTrue("Selected $mode is clipped on the left in style $index", full.left >= visible.left - 0.5.dp)
            assertTrue("Selected $mode is clipped on the right in style $index", full.right <= visible.right + 0.5.dp)
        }
    }

    private fun assertSelectedNavigationFullyVisible(screen: AppScreen) {
        val nodes = composeRule.onAllNodesWithTag("navigation-${screen.name.lowercase()}")
        assertEquals(3, nodes.fetchSemanticsNodes().size)
        repeat(3) { index ->
            val node = nodes[index].assertIsDisplayed().assertIsSelected()
            val full = node.getUnclippedBoundsInRoot()
            val visible = node.getBoundsInRoot()
            assertTrue("Selected $screen is clipped on the left in style $index", full.left >= visible.left - 0.5.dp)
            assertTrue("Selected $screen is clipped on the right in style $index", full.right <= visible.right + 0.5.dp)
        }
    }

    private fun assertCompleteLabel(label: String, allowWrap: Boolean = false) {
        val nodes = composeRule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Missing label: $label", nodes.isNotEmpty())
        nodes.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
            val result = results.single()
            if (!allowWrap) assertEquals("Wrapped label: $label", 1, result.lineCount)
            assertEquals("Truncated label: $label", label.length, result.getLineEnd(result.lineCount - 1))
            repeat(result.lineCount) { line ->
                assertFalse("Ellipsized label: $label", result.isLineEllipsized(line))
                assertTrue("Clipped label: $label", result.getLineRight(line) <= result.size.width + 1f)
            }
            // Paragraph metrics are fractional; layout bounds use integer pixels.
            assertTrue("Clipped label: $label, size=${result.size}, left=${result.getLineLeft(0)}, right=${result.getLineRight(0)}, paragraphWidth=${result.multiParagraph.width}, constraints=${result.layoutInput.constraints}", result.getLineRight(0) <= result.size.width + 1f)
            assertFalse("Vertically clipped label: $label", result.didOverflowHeight)
        }
    }
}
