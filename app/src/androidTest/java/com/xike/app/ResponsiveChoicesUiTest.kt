package com.xike.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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

    private fun assertCompleteLabel(label: String) {
        val nodes = composeRule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Missing label: $label", nodes.isNotEmpty())
        nodes.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
            val result = results.single()
            assertEquals("Wrapped label: $label", 1, result.lineCount)
            assertEquals("Truncated label: $label", label.length, result.getLineEnd(0))
            assertFalse("Ellipsized label: $label", result.isLineEllipsized(0))
            // Paragraph metrics are fractional; layout bounds use integer pixels.
            assertTrue("Clipped label: $label, size=${result.size}, left=${result.getLineLeft(0)}, right=${result.getLineRight(0)}, paragraphWidth=${result.multiParagraph.width}, constraints=${result.layoutInput.constraints}", result.getLineRight(0) <= result.size.width + 1f)
            assertFalse("Vertically clipped label: $label", result.didOverflowHeight)
        }
    }
}
