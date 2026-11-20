package com.xike.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only run on a disposable device; the host debugger kills the real process at a breakpoint. */
@RunWith(AndroidJUnit4::class)
class ReminderProcessRecoveryTest {
    @Test
    fun recoverAcrossProcessDeath() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("dedicatedReminderDevice") == "yes")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = context.getSystemService(NotificationManager::class.java)
        val state = context.getSharedPreferences("xike-reminder-schedule", Context.MODE_PRIVATE)
        when (args.getString("reminderProcessPhase")) {
            "settings-change" -> {
                ReminderScheduler.cancel(context)
                manager.cancelAll()
                val due = ZonedDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
                val original =
                    ReminderSettings(
                        enabled = true,
                        hour = due.hour,
                        minute = due.minute,
                        quietHoursEnabled = false,
                    )
                HabitPreferences(context).reminder = original
                instrumentation.runOnMainSync { ReminderScheduler.reconcile(context, original) }
                val oldToken = state.getString("alarm-token", null)
                // Save through the real preferences path, without updating the old alarm.
                val changedTime = due.plusHours(1)
                val changed = original.copy(hour = changedTime.hour, minute = changedTime.minute)
                HabitPreferences(context).reminder = changed
                instrumentation.runOnMainSync {
                    ReminderScheduler.reconcile(context, restorePending = true)
                }
                assertEquals(
                    nextReminderAt(changed, ZonedDateTime.now())!!.toInstant().toEpochMilli(),
                    state.getLong("pending-at", 0L),
                )
                assertTrue(oldToken != state.getString("alarm-token", null))
                assertEquals(0, manager.activeNotifications.count { it.id == 402 })
                assertFalse(state.contains("delivery-id"))
                instrumentation.runOnMainSync {
                    ReminderScheduler.receive(
                        context,
                        Intent("com.xike.app.action.SHOW_REMINDER")
                            .putExtra("alarm-token", oldToken),
                    )
                }
                assertEquals(
                    nextReminderAt(changed, ZonedDateTime.now())!!.toInstant().toEpochMilli(),
                    state.getLong("pending-at", 0L),
                )
                assertEquals(0, manager.activeNotifications.count { it.id == 402 })
            }
            "arm",
            "submit" -> {
                ReminderScheduler.cancel(context)
                manager.cancelAll()
                val due = ZonedDateTime.now().plusMinutes(1).withSecond(0).withNano(0)
                val settings =
                    ReminderSettings(
                        enabled = true,
                        hour = due.hour,
                        minute = due.minute,
                        quietHoursEnabled = false,
                    )
                HabitPreferences(context).reminder = settings
                instrumentation.runOnMainSync { ReminderScheduler.reconcile(context, settings) }
                assertEquals(due.toInstant().toEpochMilli(), state.getLong("pending-at", 0L))
                println("REMINDER_ARMED due=$due pid=${android.os.Process.myPid()}")
                if (args.getString("reminderProcessPhase") == "arm") return
                while (ZonedDateTime.now().isBefore(due)) SystemClock.sleep(50)
                instrumentation.runOnMainSync {
                    ReminderScheduler.reconcile(context, restorePending = true)
                }
                // If the debugger did not kill this process, this phase must not pass silently.
                error("The host must kill the process at the selected production breakpoint")
            }
            "verify" -> {
                val before = manager.activeNotifications.filter { it.id == 402 }
                val submitted = args.getString("submitted") == "yes"
                assertEquals(if (submitted) 1 else 0, before.size)
                val pendingId = state.getString("delivery-id", null)
                if (args.getString("acknowledged") != "yes") assertNotNull(pendingId)
                val expectedId = pendingId ?: before.single().tag
                val postTime = before.firstOrNull()?.postTime
                instrumentation.runOnMainSync {
                    ReminderScheduler.reconcile(context, restorePending = true)
                }
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (
                    manager.activeNotifications.none { it.id == 402 } &&
                        SystemClock.elapsedRealtime() < deadline
                ) SystemClock.sleep(25)
                val recovered = manager.activeNotifications.filter { it.id == 402 }
                assertEquals(1, recovered.size)
                assertEquals(expectedId, recovered.single().tag)
                if (postTime != null) assertEquals(postTime, recovered.single().postTime)
                assertFalse(state.contains("delivery-id"))
                assertTrue(state.getLong("pending-at", 0L) > System.currentTimeMillis())
                val originalPostTime = recovered.single().postTime
                repeat(3) {
                    instrumentation.runOnMainSync {
                        ReminderScheduler.reconcile(context, restorePending = true)
                        ReminderScheduler.receive(
                            context,
                            Intent("com.xike.app.action.SHOW_REMINDER")
                                .putExtra("alarm-token", state.getString("alarm-token", null)),
                        )
                    }
                    assertEquals(1, manager.activeNotifications.count { it.id == 402 })
                    assertEquals(
                        originalPostTime,
                        manager.activeNotifications.single { it.id == 402 }.postTime,
                    )
                }
                println(
                    "REMINDER_RECOVERED tag=$expectedId unchangedPostTime=$originalPostTime pid=${android.os.Process.myPid()}"
                )
            }
            else -> error("reminderProcessPhase must be submit or verify")
        }
    }
}
