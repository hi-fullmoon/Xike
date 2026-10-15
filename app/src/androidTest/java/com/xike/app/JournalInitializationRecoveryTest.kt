package com.xike.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.Mood
import com.xike.app.data.draft.JournalDraftStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Faults real storage only on an explicitly selected independent verification device. */
class JournalInitializationRecoveryTest {
    @Test
    fun unreadableDraftRejectsWritesUntilRecoverySucceeds() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("initializationIsolated") == "true"
        )
        val application = ApplicationProvider.getApplicationContext<Application>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = JournalDraftStore(application).load()
        val preferences =
            EncryptedSharedPreferences.create(
                application,
                "xike-journal-draft",
                MasterKey.Builder(application).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        val owner = owner()
        val serialized = original.toJson().toString()
        // Truncate the actual persisted draft, then exercise the normal ViewModel entry points.
        val damaged = serialized.dropLast(1)
        try {
            check(preferences.edit().putString("draft", damaged).commit())
            val viewModel = viewModel(application, owner)
            awaitLoaded(viewModel)
            assertNotNull(viewModel.dataError)
            assertFalse(viewModel.systemActivityResultsReady)
            instrumentation.runOnMainSync {
                viewModel.dismissDataError()
                viewModel.updateDraftNote("尚未恢复时输入的文字")
                viewModel.selectDraftMood(Mood.ABOVE_AVERAGE)
                viewModel.toggleDraftTag("自我")
                viewModel.discardDraft()
            }
            awaitLoaded(viewModel)
            instrumentation.runOnMainSync {
                viewModel.updateDraftNote("读取失败后再次输入")
                viewModel.selectDraftMood(Mood.ABOVE_AVERAGE)
                viewModel.discardDraft()
                viewModel.flushDraft()
            }
            SystemClock.sleep(350L)
            assertEquals(damaged, preferences.getString("draft", null))
            assertFalse(JournalDraftStore(application).hasPendingWrite)
            check(preferences.edit().putString("draft", serialized).commit())
            instrumentation.runOnMainSync { viewModel.dismissDataError() }
            awaitLoaded(viewModel)
            assertEquals(original, viewModel.draft)
            assertTrue(viewModel.systemActivityResultsReady)
            instrumentation.runOnMainSync { viewModel.updateDraftNote("草稿恢复后可以继续记录") }
            awaitCondition { !JournalDraftStore(application).hasPendingWrite }
            assertEquals("草稿恢复后可以继续记录", JournalDraftStore(application).load().note)
        } finally {
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            JournalDraftStore(application).save(original)
        }
    }

    @Test
    fun unavailableDatabaseStillPublishesRecoveredDraftAndCanRetry() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("initializationIsolated") == "true"
        )
        val application = ApplicationProvider.getApplicationContext<Application>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = JournalDraftStore(application).load()
        val firstOwner = owner()
        val failedOwner = owner()
        val database = JournalDatabase.get(application)
        var renamed = false
        try {
            val first = viewModel(application, firstOwner)
            awaitLoaded(first)
            instrumentation.runOnMainSync {
                first.updateDraftNote("数据库暂时不可用时也要保留草稿")
                first.selectDraftMood(Mood.ABOVE_AVERAGE)
                firstOwner.viewModelStore.clear()
            }
            val expected = JournalDraftStore(application).load()
            database.openHelper.writableDatabase.execSQL(
                "ALTER TABLE journal_entries RENAME TO unavailable_journal_entries"
            )
            renamed = true
            val failed = viewModel(application, failedOwner)
            awaitLoaded(failed)
            assertNotNull(failed.dataError)
            assertEquals(expected, failed.draft)
            assertFalse(failed.systemActivityResultsReady)
            database.openHelper.writableDatabase.execSQL(
                "ALTER TABLE unavailable_journal_entries RENAME TO journal_entries"
            )
            renamed = false
            instrumentation.runOnMainSync { failed.dismissDataError() }
            awaitLoaded(failed)
            assertTrue(failed.systemActivityResultsReady)
            assertEquals(expected, failed.draft)
            assertEquals(expected, JournalDraftStore(application).load())
        } finally {
            if (renamed)
                database.openHelper.writableDatabase.execSQL(
                    "ALTER TABLE unavailable_journal_entries RENAME TO journal_entries"
                )
            instrumentation.runOnMainSync {
                firstOwner.viewModelStore.clear()
                failedOwner.viewModelStore.clear()
            }
            JournalDraftStore(application).save(original)
        }
    }

    private fun owner() =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    private fun viewModel(application: Application, owner: ViewModelStoreOwner): JournalViewModel {
        var result: JournalViewModel? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result =
                ViewModelProvider(
                    owner,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(application),
                )[JournalViewModel::class.java]
        }
        return requireNotNull(result)
    }

    private fun awaitLoaded(viewModel: JournalViewModel) = awaitCondition { !viewModel.isLoading }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25L)
        assertTrue("Timed out waiting for recovery", condition())
    }
}
