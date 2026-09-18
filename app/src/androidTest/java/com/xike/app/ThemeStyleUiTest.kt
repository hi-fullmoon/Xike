package com.xike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class ThemeStyleUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun paperStyleNavigationKeepsSelectionSemantics() {
        composeRule.setContent {
            XikeTheme(AppTheme.ROSE, AppStyle.PAPER) {
                XikeNavigationBar(selected = AppScreen.ARCHIVE, onSelected = {})
            }
        }

        composeRule.onNodeWithTag("navigation-archive").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun focusStyleNavigationKeepsSelectionSemantics() {
        composeRule.setContent {
            XikeTheme(AppTheme.AMBER, AppStyle.FOCUS) {
                XikeNavigationBar(selected = AppScreen.SETTINGS, onSelected = {})
            }
        }

        composeRule.onNodeWithTag("navigation-settings").assertIsDisplayed().assertIsSelected()
    }
}
