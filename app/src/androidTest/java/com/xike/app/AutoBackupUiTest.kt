package com.xike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.xike.app.backup.AutoBackupPreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AutoBackupUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun invalidRetentionIsRejectedAndCancelPreservesSettings() {
        val preferences = AutoBackupPreferences(rule.activity)
        val before = preferences.settings()
        rule.onAllNodesWithText(localizedText("设置"))[0].performClick()
        rule.onNodeWithText(tr("自动备份", "Automatic backup")).performScrollTo().performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(tr("每天", "Daily")).fetchSemanticsNodes().isNotEmpty()
        }
        val passwordLabel =
            if (preferences.password != null) tr("新密码（留空沿用）", "New password (blank to keep)")
            else tr("备份密码（至少 8 位）", "Backup password (at least 8 characters)")
        rule.onNodeWithText(passwordLabel).performScrollTo().performClick()
        rule.onNodeWithText(tr("保存", "Save")).assertIsDisplayed()
        rule.onNodeWithText(tr("取消", "Cancel")).assertIsDisplayed()
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText(tr("每天", "Daily")).performScrollTo().performClick()
        rule
            .onNodeWithText(tr("保留数量（1–100）", "Copies to keep (1–100)"))
            .performScrollTo()
            .performTextReplacement("0")
        rule.onNodeWithText(tr("保存", "Save")).assertIsDisplayed().performClick()
        rule
            .onNodeWithText(tr("保留数量需为 1–100。", "Keep between 1 and 100 copies."))
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText(tr("取消", "Cancel")).assertIsDisplayed().performClick()
        assertEquals(before, preferences.settings())
    }
}
