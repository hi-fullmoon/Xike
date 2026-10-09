package com.xike.app

import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaSourceDialogUiTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val originalLanguage = LanguagePreferences(context).language
    private val style = mutableStateOf(AppStyle.BREATHE)
    private val showPhotos = mutableStateOf(false)

    @After
    fun restoreLanguage() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { AppLocale.select(context, originalLanguage) }
    }

    @Test
    fun photoSourcesKeepLabelsAndCancelReachableInEveryStyleAndLanguage() {
        for (language in AppLanguage.entries) {
            withLanguage(language, content = {
                XikeTheme(AppTheme.OCEAN, style.value) {
                    if (showPhotos.value) {
                        PhotoSourceDialog(
                            cameraAvailable = canTakePhoto(context),
                            onTakePhoto = { showPhotos.value = false },
                            onChoosePhotos = { showPhotos.value = false },
                            onDismiss = { showPhotos.value = false },
                        )
                    }
                }
            }) {
                for (appStyle in AppStyle.entries) {
                    composeRule.runOnIdle {
                        style.value = appStyle
                        showPhotos.value = true
                    }
                    verifySources(localizedText("添加照片"), localizedText("拍照"), "photo", language, appStyle)
                    composeRule.onNodeWithText(localizedText("取消")).performClick()
                    composeRule.onNodeWithText(localizedText("添加照片")).assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun videoSourcesUseTheSameReachableSheetInEveryStyleAndLanguage() {
        for (language in AppLanguage.entries) {
            withLanguage(language, content = {
                XikeTheme(AppTheme.OCEAN, style.value) {
                    VideoAddButton(enabled = true, onPicked = {}) { chooseVideo ->
                        TextButton(onClick = chooseVideo, shape = XikeShapes.button) {
                            Text(tr("视频", "Video"))
                        }
                    }
                }
            }) {
                for (appStyle in AppStyle.entries) {
                    composeRule.runOnIdle { style.value = appStyle }
                    composeRule.onNodeWithText(tr("视频", "Video")).performClick()
                    verifySources(tr("添加视频", "Add video"), tr("拍摄视频", "Record video"), "video", language, appStyle)
                    composeRule.onNodeWithText(localizedText("取消")).performClick()
                    composeRule.onNodeWithText(tr("添加视频", "Add video")).assertDoesNotExist()
                }
            }
        }
    }

    private fun withLanguage(language: AppLanguage, content: @Composable () -> Unit, verify: () -> Unit) {
        // Android recreates activities when applicationLocales changes; select before launching.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { AppLocale.select(context, language) }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent(content = content) }
            verify()
        }
    }

    private fun verifySources(title: String, capture: String, kind: String, language: AppLanguage, appStyle: AppStyle) {
        assertCompleteLabel(title)
        saveScreenshot("$kind-$language-$appStyle-top")
        assertCompleteLabel(capture)
        val captureAction = if (kind == "photo") MediaStore.ACTION_IMAGE_CAPTURE else MediaStore.ACTION_VIDEO_CAPTURE
        val captureOption = composeRule.onNodeWithText(capture)
        if (canCaptureMedia(context, captureAction)) captureOption.assertIsEnabled()
        else captureOption.assertIsNotEnabled()
        assertCompleteLabel(localizedText("从相册选择"))
        assertCompleteLabel(localizedText("取消"))
        saveScreenshot("$kind-$language-$appStyle-actions")
    }

    private fun assertCompleteLabel(label: String) {
        val node = composeRule.onNodeWithText(label, useUnmergedTree = true).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        assertTrue(results.isNotEmpty())
        results.forEach { result ->
            assertFalse("Vertically clipped label: $label", result.didOverflowHeight)
            for (line in 0 until result.lineCount) {
                assertFalse("Ellipsized label: $label", result.isLineEllipsized(line))
                // Paragraph metrics are fractional; layout bounds are rounded to whole pixels.
                assertTrue("Clipped label: $label", result.getLineRight(line) <= result.size.width + 1f)
            }
        }
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val config = context.resources.configuration
        val folder = File(context.getExternalFilesDir(null), "dialog-audit").apply { mkdirs() }
        val file = File(folder, "$name-${config.screenWidthDp}-${config.fontScale}-${config.uiMode}.png")
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }
}
