package com.xike.app

import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Uses an app-created record and real database operations on an explicitly isolated device. */
class JournalEditorLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun controlsStayVisibleAfterScrollingAndARealSaveFailure() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("editorLayoutIsolated") == "true")
        val context = rule.activity
        val store = JournalStore(context).also { it.initialize() }
        val entries = store.entries()
        assumeTrue("Requires exactly one record created through the real app", entries.size == 1)
        val entry = entries.single()
        val videos = JournalVideoStore(context)
        val application = context.applicationContext as android.app.Application
        val model = androidx.lifecycle.ViewModelProvider(context,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application))[JournalViewModel::class.java]
        val language = if (arguments.getString("language") == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
        val style = AppStyle.valueOf(arguments.getString("style") ?: "BREATHE")
        val keyboardVisible = AtomicBoolean(false)
        rule.runOnUiThread { AppLocale.select(context, language) }
        rule.setContent {
            XikeTheme(AppTheme.OCEAN, style) {
                val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
                SideEffect { keyboardVisible.set(keyboardHeight > 0) }
                CompositionLocalProvider(
                    LocalJournalEditOperations provides model.editOperations,
                    LocalAudioEditServices provides AudioEditServices(
                        import = { file, duration -> withContext(Dispatchers.IO) { runCatching { store.importEditAudio(file, duration) } } },
                        release = store::releaseEditAudio,
                    ),
                    LocalVideoServices provides VideoServices(
                        import = { uri, progress -> withContext(Dispatchers.IO) { runCatching { store.importVideo(uri, progress) } } },
                        release = store::deleteUnreferencedVideo,
                        source = videos::dataSource,
                        cover = videos::open,
                        editDraft = videos::editDraft,
                        saveEdit = { original, video -> withContext(Dispatchers.IO) { runCatching { videos.saveEdit(original, video) } } },
                        clearEdit = { id -> withContext(Dispatchers.IO) { runCatching { videos.clearEdit(id) } } },
                    ),
                ) {
                JournalArchiveScreen(
                    padding = PaddingValues(), entries = entries,
                    onSearch = { query, offset, limit -> withContext(Dispatchers.IO) { runCatching { store.search(query, offset, limit) } } },
                    onUpdate = { updated, retained, added -> withContext(Dispatchers.IO) { runCatching { store.update(updated, retained, added).single { it.id == updated.id } } } },
                    onDelete = { withContext(Dispatchers.IO) { runCatching { store.delete(it.id); Unit } } },
                    onUndoDelete = { withContext(Dispatchers.IO) { runCatching { store.undoDelete(it); Unit } } },
                    onFinalizeDelete = { withContext(Dispatchers.IO) { runCatching { store.finalizeDelete(it) } } },
                    openImage = store::openImage, openAudio = store::openAudio,
                )
                }
            }
        }
        rule.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasContentDescription(localizedText("打开筛选"))))
            .performScrollToNode(hasText(entry.mood.label))
        rule.onAllNodesWithText(entry.mood.label)[0].performClick()
        rule.waitUntil(5_000) { rule.onNodeWithContentDescription(localizedText("关闭记录详情")).isDisplayed() }
        rule.onNodeWithTag("journal-detail-body").performScrollToNode(hasText(localizedText("删除记录")))
        rule.onNodeWithContentDescription(localizedText("关闭记录详情")).assertIsDisplayed()
        capture("preview")
        rule.onNodeWithContentDescription(localizedText("编辑记录")).assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { rule.onNodeWithContentDescription(localizedText("取消编辑")).isDisplayed() }
        rule.onNodeWithTag("journal-edit-body").performScrollToNode(hasContentDescription(tr("添加内容", "Add content")))
        rule.onNodeWithContentDescription(tr("添加内容", "Add content")).performClick()
        rule.waitUntil(5_000) { rule.onNodeWithTag("journal-edit-add-content").isDisplayed() }
        rule.onNodeWithTag("journal-edit-add-content").performScrollToNode(hasText(tr("视频", "Video")))
        rule.onNodeWithText(tr("视频", "Video")).assertIsDisplayed()
        capture("add-content")
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4"),
        ).use { it.readBytes() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("journal-edit-add-content").fetchSemanticsNodes().isEmpty() }
        if (arguments.getString("keyboardCheck") == "true") {
            rule.onNodeWithContentDescription(localizedText("此刻的注脚")).performScrollTo().performClick()
            rule.waitUntil(5_000) { keyboardVisible.get() }
            rule.onNodeWithText(localizedText("保存")).assertIsDisplayed()
            rule.onNodeWithContentDescription(localizedText("取消编辑")).assertIsDisplayed()
            capture("keyboard")
            rule.onNodeWithTag("journal-edit-body").performScrollToNode(hasText(localizedText("完成")))
            rule.onNodeWithText(localizedText("完成")).performClick()
            rule.waitUntil(5_000) { !keyboardVisible.get() }
            rule.onNodeWithText(localizedText("完成")).assertDoesNotExist()
        }
        rule.onNodeWithTag("journal-edit-body").performScrollToNode(hasText(localizedText("再留下一点")))
        rule.onNodeWithText(localizedText("再留下一点")).performClick()
        rule.onNodeWithTag("journal-edit-body").performScrollToNode(hasText(localizedText("主题")))
        rule.onNodeWithText(localizedText("主题")).assertIsDisplayed()
        rule.onNodeWithContentDescription(tr("添加内容", "Add content")).assertExists()
        rule.onNodeWithContentDescription(localizedText("取消编辑")).assertIsDisplayed()
        rule.onNodeWithText(localizedText("保存")).assertIsDisplayed()
        capture("editor")
        // Removing the actual record causes the production update path to return its real error.
        // Undo restores the same record and attachments even if the assertion fails.
        store.delete(entry.id, refreshEntries = false)
        try {
            rule.onNodeWithText(localizedText("保存")).performClick()
            rule.waitUntil(5_000) { rule.onNodeWithTag("journal-edit-error").isDisplayed() }
            rule.onNodeWithTag("journal-edit-error").assertIsDisplayed()
            rule.onNodeWithText(localizedText("保存")).assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithTag("journal-edit-body").assertHeightIsAtLeast(48.dp)
            capture("error")
            val noteField = rule.onNodeWithContentDescription(localizedText("此刻的注脚"))
                .performScrollTo().assertIsDisplayed().performClick()
            noteField.performTextInput(" ")
            rule.waitUntil(5_000) { keyboardVisible.get() }
            noteField.assertIsDisplayed()
            noteField.assertTextContains(entry.note + " ")
            rule.onNodeWithText(localizedText("保存")).assertIsDisplayed().assertIsEnabled()
            capture("editable")
            rule.onNodeWithText(localizedText("保存")).performClick()
            rule.waitUntil(5_000) { rule.onNodeWithTag("journal-edit-error").isDisplayed() }
            rule.onNodeWithTag("journal-edit-body").assertHeightIsAtLeast(48.dp)
        } finally {
            store.undoDelete(entry.id, refreshEntries = false)
        }
        // The same footer must also reach the real successful update path and return to preview.
        rule.onNodeWithText(localizedText("保存")).performClick()
        rule.waitUntil(5_000) {
            rule.onNodeWithContentDescription(localizedText("编辑记录")).isDisplayed() &&
                rule.onAllNodesWithContentDescription(localizedText("取消编辑")).fetchSemanticsNodes().isEmpty()
        }
        org.junit.Assert.assertEquals(entry.note.trim(), store.entries().single().note)
        rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
        rule.waitUntil(5_000) { rule.onNodeWithContentDescription(localizedText("取消编辑")).isDisplayed() }
        rule.onNodeWithContentDescription(localizedText("此刻的注脚")).performScrollTo().performClick().performTextInput(" ")
        rule.onNodeWithContentDescription(localizedText("取消编辑")).performClick()
        rule.onNodeWithText(localizedText("放弃未保存的修改？")).assertIsDisplayed()
        rule.onNodeWithText(localizedText("放弃修改")).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription(localizedText("取消编辑")).fetchSemanticsNodes().isEmpty() }
        org.junit.Assert.assertEquals(entry.note.trim(), store.entries().single().note)
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureLayout") != "true") return
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(rule.activity.getExternalFilesDir(null), "layout-$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
