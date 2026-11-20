package com.xike.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.builtinJournalLabels
import com.xike.app.ui.theme.AppStyle
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Retains coverage for legacy catalog identities without relying on the removed picker. */
class JournalTopicDefaultsUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun historicalCatalogAndDraftSurviveRestartWithoutVisiblePickers() {
        val phase = InstrumentationRegistry.getArguments().getString("topicDefaultsPhase")
        assumeTrue(phase in listOf("preview", "fresh", "verify"))
        val validation = RetiredLabelValidation()
        val catalog = validation.catalog()
        assertEquals(catalog.size, catalog.distinctBy { it.id }.size)
        builtinJournalLabels.forEach { builtin ->
            val persisted = catalog.singleOrNull { it.id == builtin.id }
            assertNotNull("A legacy builtin identity is missing", persisted)
            assertEquals(builtin.kind, persisted!!.kind)
        }
        if (phase == "verify") validation.verify("defaults") else validation.checkpoint("defaults")
        rule.withRetiredLabelActivity { scenario ->
            rule.assertRetiredLabelControlsAbsent()
            scenario.recreate()
            rule.waitUntil(20_000) {
                rule.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty()
            }
            rule.assertRetiredLabelControlsAbsent()
        }
        validation.verify("defaults")
    }

    @Test
    fun retiredPickersStayAbsentAtConfiguredLanguageAndSize() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("topicHeaderPreview") == "true")
        val validation = RetiredLabelValidation()
        val before = validation.digest()
        LanguagePreferences(validation.context).language =
            if (arguments.getString("previewLanguage") == "en") AppLanguage.ENGLISH
            else AppLanguage.CHINESE
        arguments.getString("previewStyle")?.let {
            AppearancePreferences(validation.context).saveStyle(AppStyle.valueOf(it))
        }
        rule.withRetiredLabelActivity {
            rule.assertRetiredLabelControlsAbsent()
            rule
                .onNodeWithContentDescription(tr("添加内容", "Add content"))
                .performScrollTo()
                .assertIsDisplayed()
        }
        assertEquals("Opening home changed legacy data", before, validation.digest())
    }
}
