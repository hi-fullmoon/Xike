package com.xike.app

import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.journalPeriodSummary

import com.xike.app.feature.lock.AppLockScreen

import com.xike.app.feature.insights.MoodTrendChart

import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeTheme


import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LockAndTrendLayoutUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val originalLanguage = LanguagePreferences(context).language

    @Before fun selectEnglish() {
        composeRule.runOnIdle { AppLocale.select(context, AppLanguage.ENGLISH) }
    }

    @After fun restoreLanguage() {
        composeRule.runOnIdle { AppLocale.select(context, originalLanguage) }
    }

    @Test fun shortLockScreenKeepsUnlockReachableAndClearOfPrivacyNotice() {
        var unlocked = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                XikeTheme(AppTheme.OCEAN) {
                    Box(Modifier.width(320.dp).height(280.dp)) {
                        AppLockScreen(true, { unlocked = true }, {})
                    }
                }
            }
        }
        val button = composeRule.onNodeWithText(localizedText("解锁息刻"))
        button.performScrollTo().assertIsDisplayed()
        val notice = composeRule.onNodeWithText(localizedText("身份信息仅由 Android 系统验证，息刻不会读取或保存。"))
        assertTrue(button.fetchSemanticsNode().boundsInRoot.bottom <= notice.fetchSemanticsNode().boundsInRoot.top)
        button.performClick()
        composeRule.runOnIdle { assertTrue(unlocked) }
    }

    @Test fun narrowLargeFontTrendKeepsAxisAndDateLabelsComplete() {
        val today = LocalDate.now()
        val points = journalPeriodSummary(emptyList(), InsightsPeriod.DAYS_30, today).trendPoints
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                XikeTheme(AppTheme.OCEAN) {
                    Box(Modifier.width(240.dp)) { MoodTrendChart(points, today) {} }
                }
            }
        }
        val axisLabels = listOf(localizedText("愉悦"), localizedText("平静"), localizedText("低落"))
        (axisLabels + points.map { it.label }).forEach { label ->
            val node = composeRule.onNodeWithText(label)
            if (label !in axisLabels) node.performScrollTo()
            node.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            val getLayout = requireNotNull(node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action)
            composeRule.runOnIdle {
                assertTrue(getLayout.invoke(layouts))
                val layout = layouts.single()
                assertFalse("Truncated label: $label size=${layout.size} width=${layout.multiParagraph.width} intrinsic=${layout.multiParagraph.maxIntrinsicWidth} right=${layout.getLineRight(0)} height=${layout.multiParagraph.height}", layout.hasVisualOverflow)
            }
        }
    }

    @Test fun yearlyTrendKeepsEveryTimeSlotWideEnoughAndReachable() {
        val today = LocalDate.now()
        val points = journalPeriodSummary(emptyList(), InsightsPeriod.YEAR, today).trendPoints
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                Box(Modifier.width(240.dp)) { MoodTrendChart(points, today) {} }
            }
        }
        val axes = listOf(localizedText("愉悦"), localizedText("平静"), localizedText("低落"))
        val initialBounds = axes.associateWith { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot() }
        points.forEach { point ->
            val rangeStart = point.startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", AppLocale.locale))
            val slot = composeRule.onNode(hasContentDescription(rangeStart, substring = true))
                .performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            axes.forEach { label ->
                val axis = composeRule.onNodeWithText(label).assertIsDisplayed()
                assertEquals("Axis moved after scrolling to $rangeStart", initialBounds[label], axis.getUnclippedBoundsInRoot())
            }
            val dateLabels = composeRule.onAllNodesWithText(point.label)
            if (dateLabels.fetchSemanticsNodes().isNotEmpty()) {
                val labelBounds = dateLabels[0].getUnclippedBoundsInRoot()
                val slotBounds = slot.getUnclippedBoundsInRoot()
                assertTrue("Date lost alignment with its time slot", kotlin.math.abs(
                    (labelBounds.left.value + labelBounds.right.value) / 2f -
                        (slotBounds.left.value + slotBounds.right.value) / 2f,
                ) <= 0.5f)
            }
        }
    }

    @Test fun axisLabelsAlignWithRenderedGuidesAtEveryFontScale() {
        assertAxisLabelAlignment()
    }

    @Test fun chineseAxisLabelsAlignWithRenderedGuidesAtEveryFontScale() {
        composeRule.runOnIdle { AppLocale.select(context, AppLanguage.CHINESE) }
        composeRule.waitForIdle()
        assertAxisLabelAlignment()
    }

    private fun assertAxisLabelAlignment() {
        val today = LocalDate.now()
        val points = journalPeriodSummary(emptyList(), InsightsPeriod.DAYS_30, today).trendPoints
        val fontScale = mutableStateOf(1f)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                XikeTheme(AppTheme.OCEAN) {
                    Box(Modifier.width(240.dp)) { MoodTrendChart(points, today) {} }
                }
            }
        }
        listOf(1f, 2f, 3f).forEach { scale ->
            composeRule.runOnIdle { fontScale.value = scale }
            val plot = composeRule.onNodeWithTag("mood-trend-plot")
            val pixels = plot.captureToImage().toPixelMap()
            val background = pixels[pixels.width / 2, 0]
            val lines = mutableListOf<MutableList<Int>>()
            for (y in 0 until pixels.height) {
                val color = pixels[pixels.width / 2, y]
                if (maxOf(kotlin.math.abs(color.red - background.red), kotlin.math.abs(color.green - background.green),
                        kotlin.math.abs(color.blue - background.blue), kotlin.math.abs(color.alpha - background.alpha)) > 0.03f) {
                    if (lines.isEmpty() || lines.last().last() != y - 1) lines.add(mutableListOf())
                    lines.last().add(y)
                }
            }
            assertEquals("Expected three rendered guides at font scale $scale", 3, lines.size)
            val top = plot.getBoundsInRoot().top.value
            listOf(localizedText("愉悦"), localizedText("平静"), localizedText("低落")).forEachIndexed { index, label ->
                val bounds = composeRule.onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot()
                val center = ((bounds.top.value + bounds.bottom.value) / 2f - top) * context.resources.displayMetrics.density
                assertTrue("Axis label does not align with guide at font scale $scale", kotlin.math.abs(center - lines[index].average()) <= 2)
            }
        }
    }

    @Test fun currentTimeSlotIsInitiallyVisibleAfterPeriodChangesAndManualScrollIsRetained() {
        val today = LocalDate.now()
        val period = mutableStateOf(InsightsPeriod.YEAR)
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN) {
                val points = journalPeriodSummary(emptyList(), period.value, today).trendPoints
                Box(Modifier.width(240.dp)) { MoodTrendChart(points, today) {} }
            }
        }
        listOf(InsightsPeriod.YEAR, InsightsPeriod.DAYS_90, InsightsPeriod.YEAR).forEach { selected ->
            composeRule.runOnIdle { period.value = selected }
            val points = journalPeriodSummary(emptyList(), selected, today).trendPoints
            val current = points.single { !today.isBefore(it.startDate) && today.isBefore(it.endDateExclusive) }
            val currentLabel = current.startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", AppLocale.locale))
            val slot = composeRule.onNode(hasContentDescription(currentLabel, substring = true)).assertIsDisplayed()
            assertEquals("Current slot is initially clipped", slot.getUnclippedBoundsInRoot(), slot.getBoundsInRoot())
        }
        val first = journalPeriodSummary(emptyList(), InsightsPeriod.YEAR, today).trendPoints.first()
        val firstLabel = first.startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", AppLocale.locale))
        val slot = composeRule.onNode(hasContentDescription(firstLabel, substring = true)).performScrollTo().assertIsDisplayed()
        val original = slot.getUnclippedBoundsInRoot()
        composeRule.waitForIdle()
        assertEquals("Manual scrolling was overridden", original, slot.getUnclippedBoundsInRoot())
    }
}
