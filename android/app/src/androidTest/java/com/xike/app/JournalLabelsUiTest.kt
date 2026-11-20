package com.xike.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.BackupRestoreMode
import com.xike.app.ui.theme.AppStyle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real legacy data only. Each phase runs in a separate process on an isolated device. */
class JournalLabelsUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun legacyLabelsSurviveSavingBackupRestoreAndRestart() {
        val phase = InstrumentationRegistry.getArguments().getString("labelsPhase")
        assumeTrue(phase in listOf("write", "read", "verify"))
        val validation = RetiredLabelValidation()
        if (phase == "verify") {
            validation.verify("labels")
            return
        }
        if (phase == "write") {
            val entry =
                validation.store.entries().firstOrNull {
                    it.labels.isNotEmpty() && it.note == it.note.trim()
                }
            checkNotNull(entry) {
                "Requires an existing app-created legacy record; do not seed records"
            }
            validation.checkpoint("labels")
            rule.withRetiredLabelActivity {
                rule.assertRetiredLabelControlsAbsent()
                rule.onNodeWithTag("navigation-archive").performClick()
                rule.onNodeWithText(localizedText("时间流")).performClick()
                val card = "archive-entry:${entry.id}"
                rule.onNodeWithTag("archive-list").performScrollToNode(hasTestTag(card))
                rule.onNodeWithTag(card).performClick()
                rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
                rule.assertRetiredLabelControlsAbsent()
                rule.onNodeWithText(localizedText("保存")).performClick()
                rule.waitUntil(20_000) {
                    rule.onAllNodesWithTag("journal-edit-body").fetchSemanticsNodes().isEmpty()
                }
                rule.onNodeWithContentDescription(localizedText("编辑记录")).assertIsDisplayed()
                rule.onNodeWithContentDescription(localizedText("关闭记录详情")).performClick()
            }
            validation.verify("labels")
            return
        }
        validation.verify("labels")
        val output = ByteArrayOutputStream()
        val password = UUID.randomUUID().toString()
        validation.store.writeBackup(output, password)
        val backup = output.toByteArray()
        validation.store.restoreBackup(
            ByteArrayInputStream(backup),
            password,
            mode = BackupRestoreMode.REPLACE,
        )
        validation.verify("labels")
        assertTrue(validation.store.canUndoLastRestore())
        validation.store.restoreBackup(ByteArrayInputStream(backup), password)
        validation.verify("labels")
        assertTrue(validation.store.canUndoLastRestore())
        validation.store.undoLastRestore()
        validation.verify("labels")
    }

    @Test
    fun previewCurrentActivityAtConfiguredLanguageAndSize() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("labelsPreview") == "true")
        val validation = RetiredLabelValidation()
        val before = validation.digest()
        LanguagePreferences(validation.context).language =
            if (arguments.getString("previewLanguage") == "en") AppLanguage.ENGLISH
            else AppLanguage.CHINESE
        arguments.getString("previewStyle")?.let {
            AppearancePreferences(validation.context).saveStyle(AppStyle.valueOf(it))
        }
        rule.withRetiredLabelActivity {
            rule.assertRetiredLabelControlsAbsent()
            rule
                .onNodeWithContentDescription(tr("添加内容", "Add content"))
                .performScrollTo()
                .assertIsDisplayed()
        }
        assertEquals("Preview changed historical data", before, validation.digest())
    }

    @Test
    fun cancelledEditingKeepsHistoricalLabelsAndDraft() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("managerCancelCheck") == "true")
        val validation = RetiredLabelValidation()
        val before = validation.digest()
        val entry = validation.store.entries().firstOrNull { it.labels.isNotEmpty() }
        checkNotNull(entry) { "Requires an existing app-created legacy record" }
        rule.withRetiredLabelActivity {
            rule.onNodeWithTag("navigation-archive").performClick()
            rule.onNodeWithText(localizedText("时间流")).performClick()
            val card = "archive-entry:${entry.id}"
            rule.onNodeWithTag("archive-list").performScrollToNode(hasTestTag(card))
            rule.onNodeWithTag(card).performClick()
            rule.onNodeWithContentDescription(localizedText("编辑记录")).performClick()
            rule.assertRetiredLabelControlsAbsent()
            rule.onNodeWithContentDescription(localizedText("取消编辑")).performClick()
            rule.onNodeWithContentDescription(localizedText("关闭记录详情")).performClick()
        }
        assertEquals("Cancel changed historical data", before, validation.digest())
    }

    @Test
    fun homeDoesNotExposeRetiredCatalogManagement() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("managerEmptyCheck") == "true")
        val validation = RetiredLabelValidation()
        val before = validation.digest()
        rule.withRetiredLabelActivity { rule.assertRetiredLabelControlsAbsent() }
        assertEquals("Opening home changed historical data", before, validation.digest())
    }
}
