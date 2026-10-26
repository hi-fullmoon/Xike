package com.xike.app

import android.app.Application
import android.os.StrictMode
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.Mood
import com.xike.app.data.draft.JournalDraftStore
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftBackgroundWriteTest {
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun choicesUpdateImmediatelyWithoutMainThreadDiskWrites() = withModel { application, model ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var violations: List<Any> = emptyList()
        instrumentation.runOnMainSync {
            violations = DraftDiskWriteObserver.observe {
                model.selectDraftMood(Mood.CALM)
                model.toggleDraftTag("自我")
                model.updateDraftFeelings(listOf("HOPEFUL"))
                assertEquals(Mood.CALM, model.draft.mood)
                assertEquals(setOf("自我"), model.draft.tags)
                assertEquals(listOf("HOPEFUL"), model.draft.feelings)
            }
        }
        instrumentation.waitForIdleSync()
        await { !JournalDraftStore(application).hasPendingWrite }
        assertTrue("Draft choices performed a main-thread disk write", violations.isEmpty())
        val persisted = JournalDraftStore(application).load()
        assertEquals(model.draft, persisted)
    }

    @Test
    fun failedDiskCommitRetainsPendingChoicesAndRetryPersistsThem() =
        withModel { application, model ->
            val preferences = File(application.applicationInfo.dataDir, "shared_prefs")
            try {
                check(preferences.setWritable(false, false))
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    model.selectDraftMood(Mood.CALM)
                }
                await { model.dataError != null }
                assertTrue(JournalDraftStore(application).hasPendingWrite)
                assertEquals(Mood.CALM, model.draft.mood)
                check(preferences.setWritable(true, true))
                InstrumentationRegistry.getInstrumentation().runOnMainSync { model.flushDraft() }
                await { !JournalDraftStore(application).hasPendingWrite }
                assertEquals(Mood.CALM, JournalDraftStore(application).load().mood)
            } finally {
                preferences.setWritable(true, true)
            }
        }

    private fun withModel(work: (Application, JournalViewModel) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val original = JournalDraftStore(application).load()
        check(original.isEmpty) { "Use an isolated device with an empty draft." }
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var model: JournalViewModel
        instrumentation.runOnMainSync {
            model =
                ViewModelProvider(
                    owner,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(application),
                )[JournalViewModel::class.java]
        }
        try {
            await { !model.isLoading }
            work(application, model)
        } finally {
            instrumentation.runOnMainSync { model.discardDraft() }
            await { !JournalDraftStore(application).hasPendingWrite }
            instrumentation.runOnMainSync { owner.viewModelStore.clear() }
            JournalDraftStore(application).save(original)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("Timed out waiting for real draft persistence", condition())
    }
}

// Keep API 28 listener types out of the test class's reflected method signatures on API 26.
@androidx.annotation.RequiresApi(28)
private object DraftDiskWriteObserver {
    fun observe(work: () -> Unit): List<Any> {
        val violations = CopyOnWriteArrayList<Any>()
        val previous = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskWrites()
                .penaltyListener({ it.run() }) { violations.add(it) }
                .build()
        )
        try {
            work()
        } finally {
            StrictMode.setThreadPolicy(previous)
        }
        return violations
    }
}
