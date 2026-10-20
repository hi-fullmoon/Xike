package com.xike.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xike.app.outdoor.AmapSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real settings screen; does not create journals, credentials, or SDK results. */
@RunWith(AndroidJUnit4::class)
class AmapSettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun versionTripleTapOpensConfigurationAndCancelKeepsItClosed() {
        val store = AmapSettingsStore(ApplicationProvider.getApplicationContext<Context>())
        val original = runBlocking { withContext(Dispatchers.IO) { store.read() } }
        val settings = tr("设置", "Settings")
        rule.waitUntil(30_000) {
            rule
                .onAllNodes(androidx.compose.ui.test.hasText(settings))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText(settings).performClick()
        val version = tr("息刻 · ${BuildConfig.VERSION_NAME}", "Xike · ${BuildConfig.VERSION_NAME}")
        val title = tr("高德定位配置", "AMap location settings")
        rule.onNodeWithText(version).performScrollTo().performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithText(version).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithText(version).performClick()
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.waitUntil(15_000) {
            rule
                .onAllNodes(androidx.compose.ui.test.hasText("PackageName:", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText(tr("保存", "Save")).assertIsEnabled()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performScrollTo().performClick()
        rule.onNodeWithText(tr("保存", "Save")).assertIsDisplayed()
        rule.onNodeWithText(tr("取消", "Cancel")).assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        val testLocation = rule.onNodeWithText(tr("测试定位", "Test location")).performScrollTo()
        if (original.apiKey.isNotBlank() && original.privacyAccepted) testLocation.assertIsEnabled()
        else testLocation.assertIsNotEnabled()
        rule.onNodeWithText(tr("取消", "Cancel")).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        val restored = runBlocking { withContext(Dispatchers.IO) { store.read() } }
        // Compare without including either credential in assertion diagnostics.
        assertTrue("Cancel must preserve the saved configuration", original == restored)
    }
}
