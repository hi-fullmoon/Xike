package com.xike.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.draft.JournalDraftStore
import com.xike.app.data.model.parseJournalDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Run write and read in separate instrumentation processes with draftProcessPhase. */
@RunWith(AndroidJUnit4::class)
class JournalDraftProcessTest {
    @Test
    fun draftSurvivesProcessRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments().getString("draftProcessPhase")
        assumeNotNull(phase)
        val application = ApplicationProvider.getApplicationContext<Application>()
        val backup =
            EncryptedSharedPreferences.create(
                application,
                "xike-draft-process-test",
                MasterKey.Builder(application).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        val store = JournalDraftStore(application)
        fun openViewModel(): JournalViewModel =
            ViewModelProvider(
                owner,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application),
            )[JournalViewModel::class.java]

        when (phase) {
            "write" -> {
                check(!backup.contains("original")) {
                    "A previous process test still requires restoration."
                }
                val original = store.load()
                val expected = "今天走了一段路，回来后心情平静。"
                check(
                    backup
                        .edit()
                        .putString("original", original.toJson().toString())
                        .putString("expected", expected)
                        .commit()
                )
                try {
                    val viewModel = openViewModel()
                    awaitCondition { !viewModel.isLoading }
                    instrumentation.runOnMainSync {
                        viewModel.updateDraftNote(expected)
                        viewModel.selectDraftMood(com.xike.app.data.Mood.ABOVE_AVERAGE)
                        viewModel.updateDraftFeelings(listOf("HOPEFUL", "ANXIOUS"))
                        owner.viewModelStore.clear()
                    }
                    awaitCondition { !store.hasPendingWrite }
                } catch (error: Throwable) {
                    JournalDraftStore(application).save(original)
                    check(backup.edit().clear().commit())
                    throw error
                } finally {
                    instrumentation.runOnMainSync { owner.viewModelStore.clear() }
                }
            }
            "read" -> {
                val original = parseJournalDraft(checkNotNull(backup.getString("original", null)))
                try {
                    val expected = checkNotNull(backup.getString("expected", null))
                    // A fresh process has no coordinator cache or pending write.
                    assertFalse(store.hasPendingWrite)
                    assertEquals(expected, store.load().note)
                    assertEquals(listOf("HOPEFUL", "ANXIOUS"), store.load().feelings)
                    assertEquals(1, store.load().moodScaleVersion)
                    val viewModel = openViewModel()
                    awaitCondition { !viewModel.isLoading }
                    assertEquals(expected, viewModel.draft.note)
                    assertEquals(listOf("HOPEFUL", "ANXIOUS"), viewModel.draft.feelings)
                    assertTrue(viewModel.hasResumedDraft)
                } finally {
                    instrumentation.runOnMainSync { owner.viewModelStore.clear() }
                    JournalDraftStore(application).save(original)
                    check(backup.edit().clear().commit())
                }
            }
            else -> error("draftProcessPhase must be write or read")
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25L)
        assertTrue("Timed out waiting for committed draft", condition())
    }
}
