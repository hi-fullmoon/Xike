package com.xike.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.data.model.LocationSource
import com.xike.app.outdoor.OutdoorContextRepository
import com.xike.app.ui.components.JournalEntryCard
import com.xike.app.ui.components.journal.JournalEntryDetailDialog
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real public weather saved and read through an isolated encrypted database, without user data. */
class ArchiveLocationSourceUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun savedLocationKeepsSourcesInDetailAndStillOpensFullAddress() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("isolatedArchiveSource") == "true"
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val appearance = AppearancePreferences(context).current()
        val databaseName = "archive-source-${UUID.randomUUID()}"
        System.loadLibrary("sqlcipher")
        val database =
            Room.databaseBuilder(context, JournalDatabase::class.java, databaseName)
                .openHelperFactory(
                    SupportOpenHelperFactory(DatabaseKeyManager(context).getOrCreatePassphrase())
                )
                .build()
        try {
            val weather = runBlocking { OutdoorContextRepository(context).city("Hangzhou") }
            assertEquals(LocationSource.MANUAL, weather.locationSource)
            val entry =
                JournalEntry(mood = Mood.CALM, tags = emptyList(), note = "", outdoor = weather)
            database.journalDao().insertJournal(entry.toBundle())
            val saved = database.journalDao().record(entry.id)!!.toJournalEntry()
            for (language in listOf(AppLanguage.CHINESE, AppLanguage.ENGLISH)) {
                instrumentation.runOnMainSync { AppLocale.select(context, language) }
                ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.setContent {
                            XikeTheme(appearance.theme, appearance.style) {
                                var showDetail by remember { mutableStateOf(false) }
                                Column(
                                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                ) {
                                    JournalEntryCard(
                                        saved,
                                        compactMedia = true,
                                        moodIconSize = 36.dp,
                                        compactMoodOnly = true,
                                        compactLabels = true,
                                        modifier = Modifier.testTag("archive-source-card"),
                                        openImage = { null },
                                        onImageClick = {},
                                        onClick = { showDetail = true },
                                    )
                                }
                                if (showDetail)
                                    JournalEntryDetailDialog(
                                        saved,
                                        showLocationSource = true,
                                        openImage = { null },
                                        onDismiss = { showDetail = false },
                                    )
                            }
                        }
                    }
                    val source = tr("地点来源：手动选择", "Location source: Manually selected")
                    rule.onNodeWithText(source, useUnmergedTree = true).assertDoesNotExist()
                    rule.onNode(hasText("Open-Meteo", substring = true)).assertDoesNotExist()
                    rule.onNode(hasText(weather.placeName, substring = true)).assertIsDisplayed()
                    if (
                        InstrumentationRegistry.getArguments()
                            .getString("archiveSourceScreenshots") == "true"
                    ) {
                        val folder = requireNotNull(context.getExternalFilesDir("verification"))
                        File(folder, "archive-source-${language.tag}.png").outputStream().use {
                            assertTrue(
                                rule
                                    .onNodeWithTag("archive-source-card")
                                    .captureToImage()
                                    .asAndroidBitmap()
                                    .compress(Bitmap.CompressFormat.PNG, 100, it)
                            )
                        }
                    }
                    rule.onNode(hasText(weather.placeName, substring = true)).performClick()
                    val detailSource = rule.onNode(hasText(source) and hasAnyAncestor(isDialog()))
                    detailSource.performScrollTo().assertIsDisplayed()
                    if (
                        InstrumentationRegistry.getArguments()
                            .getString("archiveSourceScreenshots") == "true"
                    ) {
                        val folder = requireNotNull(context.getExternalFilesDir("verification"))
                        File(folder, "archive-source-detail-${language.tag}.png")
                            .outputStream()
                            .use {
                                assertTrue(
                                    rule
                                        .onNode(isDialog())
                                        .captureToImage()
                                        .asAndroidBitmap()
                                        .compress(Bitmap.CompressFormat.PNG, 100, it)
                                )
                            }
                    }
                    rule.onNode(hasText("Open-Meteo", substring = true)).assertIsDisplayed()
                    rule
                        .onNode(hasText(weather.placeName) and hasAnyAncestor(isDialog()))
                        .performScrollTo()
                        .performClick()
                    rule.onNodeWithText(tr("完整地址", "Full address")).assertIsDisplayed()
                    rule
                        .onNodeWithText(weather.fullPlaceName ?: weather.placeName)
                        .assertIsDisplayed()
                    rule
                        .onNode(
                            hasText(tr("关闭", "Close")) and
                                hasAnyAncestor(
                                    isDialog() and
                                        hasAnyDescendant(hasText(tr("完整地址", "Full address")))
                                )
                        )
                        .performClick()
                    detailSource.assertIsDisplayed()
                }
            }
        } finally {
            instrumentation.runOnMainSync { AppLocale.initialize(context) }
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
