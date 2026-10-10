package com.xike.app

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackupExportLifecycleTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun exportAndItsResultSurviveBackgroundAndRecreation() {
        val model = model()
        val output = File.createTempFile("export-lifecycle-", ".zip", rule.activity.cacheDir)
        val duplicate = File.createTempFile("export-duplicate-", ".zip", rule.activity.cacheDir)
        try {
            rule.activityRule.scenario.onActivity {
                model.startBackupExport(Uri.fromFile(output), null)
                assertTrue(model.isBackupExporting)
                model.startBackupExport(Uri.fromFile(duplicate), null)
            }
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.waitUntil(10_000) { !model.isBackupExporting }
            assertEquals(1, model.operationFeedback.size)
            assertFalse(model.operationFeedback.single().isError)
            assertEquals(0L, duplicate.length())
            ZipFile(output).use { archive ->
                assertNotNull(archive.getEntry("manifest.json"))
                assertNotNull(archive.getEntry("index.html"))
                assertNotNull(archive.getEntry("complete.txt"))
            }
            rule.activityRule.scenario.recreate()
            rule.activityRule.scenario.onActivity {
                assertTrue(model === ViewModelProvider(it)[JournalViewModel::class.java])
            }
            assertEquals(1, model.operationFeedback.size)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.onNodeWithText(localizedText("备份已保存")).assertIsDisplayed()
            rule.onNodeWithContentDescription(localizedText("关闭")).performClick()
            rule.runOnIdle { assertTrue(model.operationFeedback.isEmpty()) }
        } finally {
            output.delete()
            duplicate.delete()
        }
    }

    @Test
    fun realWriteFailureIsRetainedUntilTheNoticeIsDismissed() {
        val model = model()
        val directory = File(rule.activity.cacheDir, "export-directory-${System.nanoTime()}").apply { mkdirs() }
        try {
            rule.activityRule.scenario.onActivity { model.startBackupExport(Uri.fromFile(directory), null) }
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.waitUntil(10_000) { !model.isBackupExporting }
            val feedback = model.operationFeedback.single()
            assertTrue(feedback.isError)
            rule.activityRule.scenario.recreate()
            assertTrue(model.operationFeedback.single() === feedback)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.onNodeWithText(feedback.message).assertIsDisplayed()
            rule.onNodeWithContentDescription(localizedText("关闭")).performClick()
            rule.runOnIdle { assertTrue(model.operationFeedback.isEmpty()) }
        } finally {
            directory.delete()
        }
    }

    @Test
    fun transientNoticeDoesNotConsumeTheRetainedResult() {
        val model = model()
        val directory = File(rule.activity.cacheDir, "export-interruption-${System.nanoTime()}").apply { mkdirs() }
        try {
            rule.activityRule.scenario.onActivity { model.startBackupExport(Uri.fromFile(directory), null) }
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.waitUntil(10_000) { !model.isBackupExporting }
            val feedback = model.operationFeedback.single()
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.onNodeWithText(feedback.message).assertIsDisplayed()
            val transient = localizedText("请先完成或取消录音")
            rule.runOnIdle { XikeNotice.makeText(rule.activity, transient, XikeNotice.LENGTH_LONG).show() }
            rule.onNodeWithText(transient).assertIsDisplayed()
            rule.onNodeWithText(feedback.message).assertDoesNotExist()
            assertTrue(model.operationFeedback.single() === feedback)
            rule.onNodeWithContentDescription(localizedText("关闭")).performClick()
            rule.onNodeWithText(feedback.message).assertIsDisplayed()
            rule.waitUntil(12_000) { model.operationFeedback.isEmpty() }
            rule.onNodeWithText(feedback.message).assertDoesNotExist()
        } finally {
            directory.delete()
        }
    }

    private fun model(): JournalViewModel {
        lateinit var model: JournalViewModel
        rule.activityRule.scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
        rule.waitUntil(10_000) { !model.isLoading }
        return model
    }
}
