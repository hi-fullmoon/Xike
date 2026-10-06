package com.xike.app

import android.content.Context
import android.app.LocaleManager
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LanguageUiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("xike-language", Context.MODE_PRIVATE)
    private val originalLanguage = preferences.getString("language", null)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun restoreLanguage() {
        preferences.edit().apply {
            if (originalLanguage == null) remove("language") else putString("language", originalLanguage)
        }.commit()
        composeRule.runOnUiThread { AppLocale.initialize(context) }
    }

    @Test
    fun switchingLanguagesPersistsAcrossRecreationAndPreservesTheDraft() {
        composeRule.waitUntil(15_000) {
            !ViewModelProvider(composeRule.activity)[JournalViewModel::class.java].isLoading
        }
        composeRule.runOnUiThread {
            LanguagePreferences(context).language = AppLanguage.CHINESE
            AppLocale.initialize(context)
        }
        val draft = ViewModelProvider(composeRule.activity)[JournalViewModel::class.java].draft
        composeRule.onNodeWithTag("navigation-settings").performClick()
        composeRule.onNodeWithText("应用语言").performScrollTo().performClick()
        composeRule.onNodeWithText("English").performClick()
        composeRule.onNodeWithText("App language").assertIsDisplayed()
        assertEquals(AppLanguage.ENGLISH, LanguagePreferences(context).language)
        assertSystemLanguage(AppLanguage.ENGLISH, "Keep this moment")
        assertEquals("Calm", Mood.CALM.label)
        assertEquals("Pine", AppTheme.PINE.title)
        assertEquals("Work", localizedText("工作"))
        assertEquals("工作", journalTopics.first().label)
        assertEquals(draft, ViewModelProvider(composeRule.activity)[JournalViewModel::class.java].draft)

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("App language").assertIsDisplayed()
        assertEquals(AppLanguage.ENGLISH, AppLocale.language)
        composeRule.onNodeWithTag("navigation-home").performClick()
        composeRule.onNodeWithText("How you feel now").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("navigation-archive").performClick()
        composeRule.onNodeWithText("Calendar").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation-insights").performClick()
        composeRule.onNodeWithText("This week").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation-settings").performClick()
        composeRule.onNodeWithText("App language").performScrollTo().performClick()
        composeRule.onNodeWithText("中文").performClick()
        composeRule.onNodeWithText("应用语言").assertIsDisplayed()
        assertEquals(AppLanguage.CHINESE, LanguagePreferences(context).language)
        assertSystemLanguage(AppLanguage.CHINESE, "记录此刻")
    }

    @Test
    fun defaultIsChineseAndEveryLocalQuestionHasAnEnglishTranslation() {
        preferences.edit().remove("language").commit()
        assertEquals(AppLanguage.CHINESE, LanguagePreferences(context).language)
        composeRule.runOnUiThread { AppLocale.initialize(context) }
        assertEquals("平静", Mood.CALM.label)
        composeRule.runOnUiThread {
            LanguagePreferences(context).language = AppLanguage.ENGLISH
            AppLocale.initialize(context)
        }
        val english = context.forLanguage(AppLanguage.ENGLISH)
        val chinese = context.forLanguage(AppLanguage.CHINESE)
        localizedStringIds.forEach { (source, id) ->
            assertTrue(source, english.getString(id).isNotBlank())
            assertTrue(source, chinese.getString(id).isNotBlank())
        }
        DailyPromptStyle.entries.forEach { style ->
            style.questions.forEach { question ->
                assertFalse(question, localizedText(question).any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN })
            }
            assertFalse(dailyQuestion(LocalDate.now(), style).any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN })
        }
    }

    private fun assertSystemLanguage(language: AppLanguage, shortcutLabel: String) {
        val shortcut = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts
            .single { it.id == "quick_record" }
        assertEquals(shortcutLabel, shortcut.shortLabel.toString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertEquals(language.locale, context.getSystemService(LocaleManager::class.java).applicationLocales[0])
        }
    }
}
