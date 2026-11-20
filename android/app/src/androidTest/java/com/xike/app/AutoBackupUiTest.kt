package com.xike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.xike.app.backup.AutoBackupPreferences
import com.xike.app.backup.AutoBackupStorage
import java.time.DayOfWeek
import java.time.format.TextStyle
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
            rule.onAllNodesWithText(tr("保存", "Save")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(tr("每天", "Daily")).performScrollTo().assertIsDisplayed()
        if (before.directory.isNotBlank()) {
            val folder = AutoBackupStorage.displayName(rule.activity, before.directory)
            if (folder != null) {
                rule.onNodeWithText(tr("备份目录", "Backup folder")).performScrollTo()
                rule
                    .onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
                    .performScrollToNode(hasText(folder))
                rule.onNodeWithText(folder).performScrollTo().assertIsDisplayed()
            }
        }
        rule.onNodeWithText(tr("每周", "Weekly")).performScrollTo().performClick()
        val monday = DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, AppLocale.locale)
        val sunday = DayOfWeek.SUNDAY.getDisplayName(TextStyle.SHORT, AppLocale.locale)
        rule.onNodeWithText(monday).performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText(sunday).performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText(monday).assertIsNotSelected()
        val passwordLabel =
            if (preferences.password != null) tr("新密码（留空沿用）", "New password (blank to keep)")
            else tr("备份密码（至少 8 位）", "Backup password (at least 8 characters)")
        rule.onNodeWithText(tr("使用密码加密", "Encrypt with password")).performScrollTo()
        val encryptionSwitch =
            rule.onNodeWithContentDescription(tr("使用密码加密", "Encrypt with password"))
        if (before.encrypted) encryptionSwitch.performClick()
        rule.onNodeWithText(passwordLabel).assertDoesNotExist()
        rule
            .onNodeWithText(
                tr(
                    "未加密备份保存为 ZIP，无需密码。解压后可直接阅读，拿到文件的人也能看到全部内容。",
                    "Unencrypted backups are saved as ZIP files without a password. Anyone with the file can extract and read all contents.",
                )
            )
            .performScrollTo()
            .assertIsDisplayed()
        encryptionSwitch.performScrollTo().performClick()
        rule.onNodeWithText(passwordLabel).performScrollTo().performClick()
        rule.onNodeWithText(tr("保存", "Save")).assertIsDisplayed()
        rule.onNodeWithText(tr("取消", "Cancel")).assertIsDisplayed()
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText(tr("每天", "Daily")).performScrollTo().performClick()
        rule.onNodeWithText(tr("每天", "Daily")).assertIsSelected()
        rule.onNodeWithText(sunday).assertDoesNotExist()
        rule.onNodeWithText(tr("每周", "Weekly")).performClick()
        rule.onNodeWithText(sunday).performScrollTo().assertIsSelected()
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
