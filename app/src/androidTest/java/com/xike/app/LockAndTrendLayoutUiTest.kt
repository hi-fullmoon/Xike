package com.xike.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        (listOf(localizedText("愉悦"), localizedText("平静"), localizedText("低落")) + points.map { it.label }).forEach { label ->
            val node = composeRule.onNodeWithText(label)
            node.performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            val getLayout = requireNotNull(node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action)
            composeRule.runOnIdle {
                assertTrue(getLayout.invoke(layouts))
                val layout = layouts.single()
                assertFalse("Truncated label: $label size=${layout.size} width=${layout.multiParagraph.width} intrinsic=${layout.multiParagraph.maxIntrinsicWidth} right=${layout.getLineRight(0)} height=${layout.multiParagraph.height}", layout.hasVisualOverflow)
            }
        }
    }
}
