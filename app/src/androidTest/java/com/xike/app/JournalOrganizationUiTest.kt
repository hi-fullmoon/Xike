package com.xike.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.Mood
import com.xike.app.ui.theme.AppStyle
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real app and app-created records only; configuration matrix uses a dedicated device. */
class JournalOrganizationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sharedComponentsAndArchiveStateSurviveRecreationAndNavigation() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("refactoringIsolated") == "true")
        val model = ViewModelProvider(rule.activity)[JournalViewModel::class.java]
        rule.waitUntil(20_000) { !model.isLoading }
        check(model.draft.isEmpty) { "Use an independent environment with an empty draft." }
        val entry = model.entries.single { it.imageFileNames.isNotEmpty() && it.audio != null }
        check(entry.note.isNotBlank()) { "Run the real editor acceptance first." }
        val language = if (arguments.getString("language") == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
        val style = AppStyle.valueOf(arguments.getString("style") ?: "BREATHE")
        rule.onNodeWithTag("navigation-settings").performClick()
        rule.onNodeWithText(localizedText("应用语言")).performScrollTo().performClick()
        rule.onNode(hasText(language.nativeName) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        rule.onNodeWithText(style.title).performScrollTo().performClick()
        assertEquals(style, model.selectedStyle)

        rule.onNodeWithTag("navigation-home").performClick()
        rule.onNodeWithText(Mood.CALM.label).performScrollTo().performClick()
        try {
            rule.onNodeWithText(localizedText("添加")).performScrollTo().assertIsDisplayed()
            capture("outdoor")
            rule.onNodeWithText(localizedText("添加")).performScrollTo().performClick()
            rule.onNodeWithText(localizedText("手动选城市")).performClick()
            rule.onNodeWithText(localizedText("获取天气")).assertIsDisplayed().assertIsNotEnabled()
            capture("city")
            rule.onNodeWithText(localizedText("取消")).performClick()

            rule.onNodeWithTag("navigation-archive").performClick()
            rule.onNodeWithContentDescription(localizedText("打开筛选")).performScrollTo().performClick()
            val calm = hasText(Mood.CALM.label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
            showInArchive(calm).performClick().assertIsSelected()
            showInArchive(hasText(JournalImageFilter.WITH_IMAGES.label)).performClick().assertIsSelected()
            capture("filters")
            showInArchive(hasTestTag("archive-mode-calendar")).performClick().assertIsSelected()
            val date = Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val spokenDate = date.format(DateTimeFormatter.ofPattern(tr("yyyy年M月d日 EEEE", "EEEE, MMM d, yyyy"), AppLocale.locale))
            val day = "$spokenDate，${tr("1 条记录", "1 entries")}"
            showDay(day).performClick()
            rule.activityRule.scenario.recreate()
            showInArchive(hasTestTag("archive-mode-calendar")).assertIsSelected()
            showInArchive(calm).assertIsSelected()
            showInArchive(hasText(JournalImageFilter.WITH_IMAGES.label)).assertIsSelected()
            showDay(day).assertIsSelected()
            capture("calendar")
            rule.onNodeWithTag("navigation-insights").performClick()
            rule.onNodeWithTag("navigation-archive").performClick()
            showDay(day).assertIsSelected()
            rule.onNode(hasScrollToIndexAction() and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(hasText(entry.note))
            rule.onNodeWithText(entry.note, useUnmergedTree = true).performScrollTo().performClick()
            rule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
            capture("detail")
            rule.activityRule.scenario.recreate()
            rule.onNode(hasText(entry.note) and hasAnyAncestor(hasTestTag("journal-detail-body")))
                .performScrollTo().assertIsDisplayed()
            rule.onNodeWithContentDescription(localizedText("关闭记录详情")).performClick()
            assertEquals(entry, JournalStore(rule.activity).entries().single { it.id == entry.id })
        } finally {
            rule.runOnIdle { model.discardDraft() }
        }
    }

    private fun showDay(description: String): SemanticsNodeInteraction {
        return showInArchive(hasContentDescription(description))
    }

    private fun showInArchive(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        val list = rule.onNode(hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        list.performScrollToNode(matcher)
        val node = rule.onNode(matcher)
        node.performScrollTo()
        repeat(6) {
            val bounds = node.getUnclippedBoundsInRoot()
            val viewport = list.getBoundsInRoot()
            val bottom = minOf(viewport.bottom.value,
                rule.onNodeWithTag("navigation-archive").getBoundsInRoot().top.value) - 8f
            val top = viewport.top.value + 8f
            val delta = when {
                bounds.bottom.value > bottom -> bounds.bottom.value - bottom
                bounds.top.value < top -> bounds.top.value - top
                else -> return node.assertIsDisplayed()
            }
            list.performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, delta * rule.activity.resources.displayMetrics.density)
            }
        }
        error("The archive target did not enter its unobscured viewport.")
    }

    private fun capture(part: String) {
        val configuration = rule.activity.resources.configuration
        val night = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val style = ViewModelProvider(rule.activity)[JournalViewModel::class.java].selectedStyle
        val name = "organization-${AppLocale.language.name}-$night-${configuration.fontScale}-${style.name}-$part.png"
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(rule.activity.getExternalFilesDir(null), name).outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
    }
}
