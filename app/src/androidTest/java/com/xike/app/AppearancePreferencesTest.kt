package com.xike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearancePreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearBeforeTest() {
        clearPreferences()
    }

    @After
    fun clearAfterTest() {
        clearPreferences()
    }

    @Test
    fun choicesRemainAvailableBeforeTheJournalDatabaseIsOpened() {
        AppearancePreferences(context).apply {
            saveTheme(AppTheme.ROSE)
            saveStyle(AppStyle.PAPER)
        }

        assertEquals(
            AppearanceSettings(AppTheme.ROSE, AppStyle.PAPER),
            AppearancePreferences(context).current(),
        )
    }

    @Test
    fun databaseValuesMigrateOnceWithoutOverwritingANewerChoice() {
        val preferences = AppearancePreferences(context)
        assertEquals(
            AppearanceSettings(AppTheme.VIOLET, AppStyle.FOCUS),
            preferences.migrateFromDatabase(AppTheme.VIOLET.name, AppStyle.FOCUS.name),
        )

        preferences.saveTheme(AppTheme.AMBER)
        assertEquals(
            AppearanceSettings(AppTheme.AMBER, AppStyle.FOCUS),
            preferences.migrateFromDatabase(AppTheme.PINE.name, AppStyle.BREATHE.name),
        )
    }

    private fun clearPreferences() {
        context.getSharedPreferences(AppearancePreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
