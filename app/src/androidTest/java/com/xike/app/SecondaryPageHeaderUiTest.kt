package com.xike.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.feature.insights.LocalReviewCard
import com.xike.app.ui.components.PaperCard
import com.xike.app.ui.components.SecondaryPageHeader
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecondaryPageHeaderUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun captureUnifiedComponentsAcrossStyles() {
        var style by mutableStateOf(AppStyle.BREATHE)
        composeRule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.width(360.dp).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(style.title, style = MaterialTheme.typography.labelLarge)
                        SecondaryPageHeader("这一刻", "关闭记录详情", {}) {
                            IconButton(onClick = {}) {
                                Icon(Icons.Outlined.Edit, contentDescription = "编辑记录")
                            }
                        }
                        SecondaryPageHeader("编辑这一刻", "取消编辑", {})
                        SecondaryPageHeader("原始记录", "关闭原始记录", {})
                        PaperCard {
                            Text("这一刻的照片", style = MaterialTheme.typography.titleSmall)
                        }
                        LocalReviewCard(enabled = false, periodName = "这段时间", onOpen = {})
                    }
                }
            }
        }
        AppStyle.entries.forEach { selectedStyle ->
            composeRule.runOnIdle { style = selectedStyle }
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.cacheDir, "unified-ui-${selectedStyle.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun narrowHeaderKeepsLongTitleAndActionsUsableAcrossStyles() {
        var style by mutableStateOf(AppStyle.BREATHE)
        var title by mutableStateOf("编辑这一刻")
        var closeEnabled by mutableStateOf(true)
        var closeCount = 0
        var editCount = 0
        composeRule.setContent {
            XikeTheme(AppTheme.PINE, style) {
                Column(Modifier.width(280.dp)) {
                    SecondaryPageHeader(
                        title = title,
                        closeDescription = "关闭",
                        onClose = { closeCount++ },
                        closeEnabled = closeEnabled,
                    ) {
                        IconButton(onClick = { editCount++ }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                        }
                    }
                }
            }
        }
        AppStyle.entries.forEach { selectedStyle ->
            listOf("编辑这一刻", "Entries: This month's mood records").forEach { selectedTitle ->
                composeRule.runOnIdle {
                    style = selectedStyle
                    title = selectedTitle
                    closeEnabled = true
                }
                val layouts = mutableListOf<TextLayoutResult>()
                composeRule
                    .onNodeWithText(selectedTitle)
                    .assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                assertFalse(
                    "$selectedStyle $selectedTitle: size=${layouts.single().size}, " +
                        "lines=${layouts.single().lineCount}, " +
                        "widthOverflow=${layouts.single().didOverflowWidth}, " +
                        "heightOverflow=${layouts.single().didOverflowHeight}",
                    layouts.single().hasVisualOverflow,
                )
                val textBounds =
                    composeRule.onNodeWithText(selectedTitle).fetchSemanticsNode().boundsInRoot
                val editBounds =
                    composeRule.onNodeWithContentDescription("编辑").fetchSemanticsNode().boundsInRoot
                val closeBounds =
                    composeRule.onNodeWithContentDescription("关闭").fetchSemanticsNode().boundsInRoot
                assertTrue(textBounds.right <= editBounds.left)
                assertTrue(editBounds.right <= closeBounds.left)
                composeRule.onNodeWithContentDescription("编辑").assertIsDisplayed().performClick()
                composeRule.onNodeWithContentDescription("关闭").assertIsDisplayed().performClick()
                composeRule.runOnIdle { closeEnabled = false }
                composeRule.onNodeWithContentDescription("关闭").assertIsNotEnabled()
            }
        }
        composeRule.runOnIdle {
            assertEquals(6, closeCount)
            assertEquals(6, editCount)
        }
    }
}
