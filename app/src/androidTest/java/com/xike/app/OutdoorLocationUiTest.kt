package com.xike.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Run on an isolated device without precise permission or an outdoor draft. */
class OutdoorLocationUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun disclosureExplainsDetailedLocationAndManualCityDoesNotRequestPermission() {
        rule.waitUntil(15_000) {
            !ViewModelProvider(rule.activity)[JournalViewModel::class.java].isLoading
        }
        val context = rule.activity
        assertEquals(PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION))
        val originalCoarsePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val requested = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in requested)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in requested)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in requested)
        val viewModel = ViewModelProvider(context)[JournalViewModel::class.java]
        val originalDraft = viewModel.draft
        rule.onNodeWithText(tr("地点与天气", "Location and weather")).performScrollTo().performClick()
        rule.onNodeWithText(localizedText("允许定位")).assertIsDisplayed()
        rule.onNodeWithText(localizedText("息刻只会在你点击后获取一次位置，用于显示区、街道；也可以只允许粗略定位。系统地址服务会使用坐标查询地点，粗略经纬度会发送给 Open-Meteo 查询天气。") +
            localizedText("经纬度不会保存，也不会上传日记内容；地点和天气快照会随草稿与日记加密保存在本机。"))
            .assertIsDisplayed()
        rule.onNodeWithText(localizedText("手动选城市")).performClick()
        rule.onNodeWithText(localizedText("选择城市")).assertIsDisplayed()
        rule.onNodeWithText(localizedText("取消")).performClick()
        assertEquals(originalDraft, viewModel.draft)
        assertEquals(originalCoarsePermission,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION))
    }
}
