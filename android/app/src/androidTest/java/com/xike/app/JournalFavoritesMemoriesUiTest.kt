package com.xike.app

import android.content.Context
import android.view.View
import android.widget.DatePicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.Mood
import com.xike.app.ui.theme.AppStyle
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Run phases on an isolated device. Records are created through the real form and native picker.
 */
class JournalFavoritesMemoriesUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun realMemoriesAndFavoritesSurviveRestart() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("favoriteMemoryPhase")
        assumeTrue(phase in listOf("write", "verify", "unfavorite", "verify-unfavorite", "preview"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JournalStore(context).also { it.initialize(loadEntries = false) }
        val prefs =
            context.getSharedPreferences("favorites-memory-validation", Context.MODE_PRIVATE)
        val english = phase == "preview" && args.getString("previewLanguage") == "en"
        LanguagePreferences(context).language =
            if (english) AppLanguage.ENGLISH else AppLanguage.CHINESE
        AppearancePreferences(context)
            .saveStyle(
                args.getString("previewStyle")?.let(AppStyle::valueOf)
                    ?: if (english) AppStyle.PAPER else AppStyle.BREATHE
            )
        if (phase == "write") check(store.entries().isEmpty())
        else check(prefs.getBoolean("created", false))
        if (phase == "verify" || phase == "verify-unfavorite") {
            val id = checkNotNull(prefs.getString("favorite-id", null))
            assertEquals(phase == "verify", store.entries().single { it.id == id }.isFavorite)
            assertEquals(2, store.entries().size)
            return
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            if (phase == "write") {
                if (!model!!.draft.isEmpty) {
                    rule.onNodeWithText("放弃草稿").performScrollTo().performClick()
                    rule.onNode(hasText("放弃草稿") and hasAnyAncestor(isDialog())).performClick()
                }
                createAt(LocalDate.now().minusMonths(1))
                rule.waitUntil(15_000) { store.entries().size == 1 && model!!.draft.isEmpty }
                createAt(LocalDate.now().minusYears(1))
                rule.waitUntil(15_000) { store.entries().size == 2 && model!!.draft.isEmpty }
                check(prefs.edit().putBoolean("created", true).commit())
            }
            rule.onNodeWithText(localizedText("回望")).performClick()
            rule
                .onNodeWithText(if (english) "Past moments" else "往日回望")
                .performScrollTo()
                .performClick()
            val monthDate = LocalDate.now().minusMonths(1)
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("memory-day:$monthDate").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(if (english) "One month ago" else "一个月前").assertIsDisplayed()
            rule
                .onNodeWithTag("memories-dates")
                .performScrollToNode(hasText(if (english) "On this day" else "往年今天"))
            rule.onNodeWithText(if (english) "On this day" else "往年今天").assertIsDisplayed()
            capture(context, "${phase}-dates")
            rule
                .onNode(
                    hasText(if (english) "View entries" else "查看记录") and
                        hasAnyAncestor(hasTestTag("memory-day:$monthDate"))
                )
                .performScrollTo()
                .performClick()
            val entry =
                store.entries().single {
                    java.time.Instant.ofEpochMilli(it.createdAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate() == monthDate
                }
            rule.waitUntil(10_000) {
                rule
                    .onAllNodesWithTag("memory-entry:${entry.id}")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            rule.onNodeWithTag("memory-entry:${entry.id}").performClick()
            if (phase == "write") {
                rule.onNodeWithContentDescription("收藏这一刻").performClick()
                rule.waitUntil(10_000) { store.entries().single { it.id == entry.id }.isFavorite }
                rule.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
                rule.onNodeWithContentDescription("编辑记录").assertDoesNotExist()
                check(prefs.edit().putString("favorite-id", entry.id).commit())
                scenario.recreate()
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithContentDescription("取消收藏").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
            }
            capture(context, "${phase}-detail")
            rule
                .onNodeWithContentDescription(if (english) "Close entry details" else "关闭记录详情")
                .performClick()
            rule
                .onNodeWithContentDescription(if (english) "Back to memory dates" else "返回往日日期")
                .performClick()
            rule
                .onNodeWithContentDescription(if (english) "Close memories" else "关闭往日回望")
                .performClick()
            if (phase == "preview") {
                rule
                    .onNodeWithTag("archive-list")
                    .performScrollToNode(hasTestTag("archive-entry:${entry.id}"))
                capture(context, "preview-archive-card")
                rule.onNodeWithTag("archive-entry:${entry.id}").performClick()
                rule
                    .onNodeWithContentDescription(if (english) "Remove favorite" else "取消收藏")
                    .assertIsDisplayed()
                if (english && context.resources.configuration.fontScale > 1.3f) {
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    val title = rule.onNodeWithText(localizedText("这一刻"))
                    title.performSemanticsAction(
                        androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
                    ) {
                        it(layouts)
                    }
                    val layout = layouts.single()
                    assertFalse(
                        (0 until layout.lineCount).any { line ->
                            layout.layoutInput.text.text
                                .substring(layout.getLineStart(line), layout.getLineEnd(line))
                                .trim()
                                .length == 1
                        }
                    )
                    assertTrue(
                        rule
                            .onNodeWithContentDescription("Remove favorite")
                            .getBoundsInRoot()
                            .top >= title.getBoundsInRoot().bottom
                    )
                }
                capture(context, "preview-archive-detail")
                rule.onNodeWithContentDescription(localizedText("关闭记录详情")).performClick()
            }
            if (phase == "write" || phase == "unfavorite") {
                rule.onNodeWithContentDescription("打开筛选").performClick()
                rule.onNodeWithText("仅看收藏").performScrollTo().performClick()
                rule
                    .onNodeWithTag("archive-list")
                    .performScrollToNode(hasTestTag("archive-entry:${entry.id}"))
                rule.onNodeWithTag("archive-entry:${entry.id}").performScrollTo().performClick()
                rule.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
                if (phase == "unfavorite") {
                    rule.onNodeWithContentDescription("取消收藏").performClick()
                    rule.waitUntil(10_000) {
                        !store.entries().single { it.id == entry.id }.isFavorite
                    }
                    rule.onNodeWithContentDescription("收藏这一刻").assertIsDisplayed()
                    rule.onNodeWithContentDescription("关闭记录详情").performClick()
                    rule.waitUntil(10_000) {
                        runCatching {
                            rule
                                .onNodeWithTag("archive-list")
                                .performScrollToNode(hasText("当前条件下没有收藏的记录"))
                        }
                            .isSuccess
                    }
                    rule.onNodeWithText("当前条件下没有收藏的记录").assertIsDisplayed()
                    capture(context, "unfavorite-empty")
                    rule.onNodeWithText("清除收藏筛选").performScrollTo().performClick()
                } else {
                    capture(context, "favorite-archive-detail")
                    rule.onNodeWithContentDescription("关闭记录详情").performClick()
                    rule.onNodeWithTag("archive-entry:${entry.id}").assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun prominentEntryIsVisibleAndKeepsArchiveState() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("memoryEntryPreview") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val english = args.getString("previewLanguage") == "en"
        LanguagePreferences(context).language =
            if (english) AppLanguage.ENGLISH else AppLanguage.CHINESE
        AppearancePreferences(context)
            .saveStyle(args.getString("previewStyle")?.let(AppStyle::valueOf) ?: AppStyle.BREATHE)
        val store = JournalStore(context).also { it.initialize(loadEntries = false) }
        check(store.entries().isEmpty()) { "Use the isolated preview device" }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            rule.onNodeWithText(localizedText("回望")).performClick()
            val entry = rule.onNodeWithTag("archive-memories-entry")
            entry.assertIsDisplayed().assertHasClickAction()
            val full = entry.getUnclippedBoundsInRoot()
            val visible = entry.getBoundsInRoot()
            assertTrue(full.bottom.value - full.top.value >= 48f)
            val searchTop =
                rule.onNodeWithTag("archive-search").getUnclippedBoundsInRoot().top.value
            assertEquals(8f, searchTop - full.bottom.value, 0.5f)
            assertTrue(
                full.top.value >= visible.top.value - 0.5f &&
                    full.bottom.value <= visible.bottom.value + 0.5f
            )
            val hint = if (english) "(One month ago · On this day)" else "（一个月前 · 往年今天）"
            if (
                args.getString("expectMemoryHint") == "true" ||
                    rule.onAllNodesWithText(hint).fetchSemanticsNodes().isNotEmpty()
            ) {
                rule.onNodeWithText(hint).assertIsDisplayed()
            } else {
                entry.assert(hasContentDescription(hint, substring = true))
            }
            val titleLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule
                .onNodeWithText(if (english) "Past moments" else "往日回望", useUnmergedTree = true)
                .performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
                ) {
                    it(titleLayouts)
                }
            assertEquals(1, titleLayouts.single().lineCount)
            assertFalse(titleLayouts.single().isLineEllipsized(0))
            capture(context, "entry-first-screen")
            entry.performClick()
            val close = if (english) "Close memories" else "关闭往日回望"
            rule.waitUntil(10_000) {
                rule
                    .onAllNodesWithText(
                        if (english) "No entries from these dates yet" else "这些日子还没有留下记录"
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            capture(context, "entry-opened")
            rule.onNodeWithContentDescription(close).performClick()
            entry.assertIsDisplayed()
            rule
                .onNodeWithTag("archive-list")
                .performScrollToNode(hasContentDescription(localizedText("打开筛选")))
            rule.onNodeWithContentDescription(localizedText("打开筛选")).performClick()
            val favorites = if (english) "Favorites only" else "仅看收藏"
            rule.onNodeWithTag("archive-list").performScrollToNode(hasText(favorites))
            rule.onNodeWithText(favorites).performClick()
            rule
                .onNodeWithTag("archive-list")
                .performScrollToNode(hasTestTag("archive-memories-entry"))
            val before = entry.getBoundsInRoot().top.value
            entry.performClick()
            rule.onNodeWithContentDescription(close).performClick()
            assertEquals(before, entry.getBoundsInRoot().top.value, 0.5f)
            rule.onNodeWithTag("archive-list").performScrollToNode(hasText(favorites))
            rule.onNodeWithText(favorites).assertIsSelected()
            capture(context, "archive-mood-filter")
            assertTrue(store.entries().isEmpty())
        }
    }

    @Test
    fun historicalDayLoadsBeyondTheFirstPage() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memoryPagingCheck") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        LanguagePreferences(context).language = AppLanguage.CHINESE
        AppearancePreferences(context).saveStyle(AppStyle.BREATHE)
        val store = JournalStore(context).also { it.initialize(loadEntries = false) }
        check(store.entries().isEmpty()) { "Use this task's isolated device" }
        val date = LocalDate.now().minusMonths(1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            repeat(65) { index ->
                createAt(date, "很好")
                rule.waitUntil(15_000) {
                    store.entries().size == index + 1 && model!!.draft.isEmpty
                }
                if ((index + 1) % 10 == 0)
                    println("Saved ${index + 1} paging validation entries through the form")
            }
            val oldest =
                store
                    .entries()
                    .minWith(
                        compareBy<com.xike.app.data.JournalEntry> { it.createdAt }.thenBy { it.id }
                    )
            rule.onNodeWithText("回望").performClick()
            rule
                .onNodeWithTag("archive-list")
                .performScrollToNode(hasTestTag("archive-memories-entry"))
            rule.onNodeWithTag("archive-memories-entry").performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("memory-day:$date").fetchSemanticsNodes().isNotEmpty()
            }
            rule
                .onNode(hasText("查看记录") and hasAnyAncestor(hasTestTag("memory-day:$date")))
                .performClick()
            val list = rule.onNodeWithTag("memories-entries")
            rule.waitUntil(10_000) {
                rule
                    .onAllNodes(
                        hasTestTag("memories-entries") and hasAnyDescendant(hasClickAction())
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            list.performScrollToIndex(59)
            rule.waitUntil(20_000) {
                runCatching { list.performScrollToNode(hasTestTag("memory-entry:${oldest.id}")) }
                    .isSuccess
            }
            rule.onNodeWithTag("memory-entry:${oldest.id}").assertIsDisplayed().performClick()
            rule.onNodeWithContentDescription("收藏这一刻").performClick()
            rule.waitUntil(10_000) { store.entries().single { it.id == oldest.id }.isFavorite }
            assertEquals(65, store.entries().size)
            capture(context, "memory-paging-detail")
        }
    }

    @Test
    fun pagedFavoriteKeepsItsTimeReadableAtLargeFont() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("memoryMetadataPreview") == "true"
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        LanguagePreferences(context).language = AppLanguage.ENGLISH
        AppearancePreferences(context).saveStyle(AppStyle.PAPER)
        val store = JournalStore(context).also { it.initialize(loadEntries = false) }
        check(store.entries().size == 65 && store.entries().all { it.mood == Mood.VERY_GOOD })
        val oldest =
            store
                .entries()
                .minWith(
                    compareBy<com.xike.app.data.JournalEntry> { it.createdAt }.thenBy { it.id }
                )
        check(oldest.isFavorite)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: JournalViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[JournalViewModel::class.java] }
            rule.waitUntil(20_000) { model?.systemActivityResultsReady == true }
            rule.onNodeWithText(localizedText("回望")).performClick()
            rule
                .onNodeWithTag("archive-list")
                .performScrollToNode(hasTestTag("archive-memories-entry"))
            rule.onNodeWithTag("archive-memories-entry").performClick()
            val date = LocalDate.now().minusMonths(1)
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("memory-day:$date").fetchSemanticsNodes().isNotEmpty()
            }
            rule
                .onNode(hasText("View entries") and hasAnyAncestor(hasTestTag("memory-day:$date")))
                .performClick()
            val list = rule.onNodeWithTag("memories-entries")
            rule.waitUntil(10_000) {
                rule
                    .onAllNodes(
                        hasTestTag("memories-entries") and hasAnyDescendant(hasClickAction())
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            list.performScrollToIndex(59)
            rule.waitUntil(20_000) {
                runCatching { list.performScrollToNode(hasTestTag("memory-entry:${oldest.id}")) }
                    .isSuccess
            }
            capture(context, "memory-paging-metadata")
            val time =
                java.time.Instant.ofEpochMilli(oldest.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule
                .onNode(
                    hasText(time) and hasAnyAncestor(hasTestTag("memory-entry:${oldest.id}")),
                    useUnmergedTree = true,
                )
                .performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
                ) {
                    it(layouts)
                }
            assertEquals(1, layouts.single().lineCount)
            val layout = layouts.single()
            // Paragraph width can retain its measurement budget; inspect the actual text line.
            assertTrue(layout.getLineLeft(0) >= -1f)
            assertTrue(layout.getLineRight(0) <= layout.size.width + 1f)
        }
    }

    private fun createAt(date: LocalDate, moodLabel: String = "还不错") {
        rule.onNodeWithText(moodLabel).performScrollTo().performClick()
        rule.onNodeWithText("今天 · 修改时间").performScrollTo().performClick()
        onView(isAssignableFrom(DatePicker::class.java))
            .perform(
                object : ViewAction {
                    override fun getConstraints(): Matcher<View> =
                        isAssignableFrom(DatePicker::class.java)

                    override fun getDescription() = "Choose a historical date in the native picker"

                    override fun perform(controller: UiController, view: View) {
                        (view as DatePicker).updateDate(
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth,
                        )
                        controller.loopMainThreadUntilIdle()
                    }
                }
            )
        onView(withId(android.R.id.button1)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        rule.onNodeWithText("补记这一刻").performClick()
    }

    private fun capture(context: Context, name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory =
            File(context.getExternalFilesDir(null), "favorites-memories-validation").apply {
                mkdirs()
            }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
