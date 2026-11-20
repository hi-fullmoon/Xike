package com.xike.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

const val ACTION_QUICK_RECORD = "com.xike.app.action.QUICK_RECORD"

internal object ReminderScheduler {
    fun reconcile(
        context: Context,
        settings: ReminderSettings = HabitPreferences(context).reminder,
        now: ZonedDateTime = ZonedDateTime.now(),
        restorePending: Boolean = false,
    ) {
        AppLocale.initialize(context)
        val appContext = context.applicationContext
        setRescheduleReceiverEnabled(appContext, settings.enabled)
        val preferences =
            appContext.getSharedPreferences("xike-reminder-schedule", Context.MODE_PRIVATE)
        if (!settings.enabled) {
            cancelPendingAlarm(appContext)
            check(preferences.edit().clear().commit())
            return
        }

        createNotificationChannel(appContext)
        val snapshot = settings.scheduleSnapshot()
        val canRestore = restorePending && preferences.getString("settings", null) == snapshot
        val pendingAt = preferences.getLong("pending-at", 0L).takeIf { canRestore && it > 0L }
        val restored = restoreReminderAt(settings, now, pendingAt)
        if (restored == null) {
            cancelPendingAlarm(appContext)
            check(preferences.edit().clear().commit())
            return
        }
        val deliverNow = !restored.isAfter(now)
        val next = if (deliverNow) nextReminderAt(settings, now) ?: return else restored
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val token =
            if (canRestore)
                preferences.getString("alarm-token", null) ?: UUID.randomUUID().toString()
            else UUID.randomUUID().toString()
        val retainedDeliveryId =
            preferences.getString("delivery-id", null).takeIf {
                canRestore &&
                    pendingAt != null &&
                    java.time.Instant.ofEpochMilli(pendingAt).atZone(now.zone).toLocalDate() ==
                        now.toLocalDate()
            }
        val deliveryId =
            retainedDeliveryId ?: if (deliverNow) UUID.randomUUID().toString() else null
        val afterDelivery =
            if (deliverNow) next else nextReminderAt(settings, restored.plusSeconds(1)) ?: next
        check(
            preferences
                .edit()
                .putLong(
                    "pending-at",
                    (if (deliverNow) restored else next).toInstant().toEpochMilli(),
                )
                .putString("alarm-token", token)
                .putString("settings", snapshot)
                .putString("delivery-id", deliveryId)
                .putLong("delivery-next-at", afterDelivery.toInstant().toEpochMilli())
                .commit()
        )
        // Arm recovery before crossing the notification/persistence boundary. The same token
        // remains valid if the process dies before replacing this retry with the next alarm.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            (if (deliverNow) now.plusMinutes(15) else next).toInstant().toEpochMilli(),
            reminderPendingIntent(appContext, token),
        )
        if (deliverNow && deliveryId != null && showReminder(appContext, deliveryId)) {
            check(
                preferences
                    .edit()
                    .putLong("pending-at", next.toInstant().toEpochMilli())
                    .remove("delivery-id")
                    .commit()
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next.toInstant().toEpochMilli(),
                reminderPendingIntent(appContext, token),
            )
        }
    }

    private fun ReminderSettings.scheduleSnapshot(): String =
        JSONObject()
            .put("enabled", enabled)
            .put("hour", hour)
            .put("minute", minute)
            .put("weekdays", JSONArray(weekdays.sortedBy { it.value }.map { it.name }))
            .put("pausedUntil", pausedUntilEpochDay ?: JSONObject.NULL)
            .put("quietHoursEnabled", quietHoursEnabled)
            .put("quietHoursStart", quietHoursStart)
            .put("quietHoursEnd", quietHoursEnd)
            .toString()

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        cancelPendingAlarm(appContext)
        check(
            appContext
                .getSharedPreferences("xike-reminder-schedule", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        )
        setRescheduleReceiverEnabled(appContext, false)
    }

    private fun showReminder(context: Context, deliveryId: String): Boolean {
        AppLocale.initialize(context)
        if (!canPostNotifications(context)) return false
        createNotificationChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (
            manager.activeNotifications.any {
                it.tag == deliveryId && it.id == REMINDER_NOTIFICATION_ID
            }
        )
            return true
        if (
            !manager.areNotificationsEnabled() ||
                manager.getNotificationChannel(CHANNEL_ID)?.importance ==
                    NotificationManager.IMPORTANCE_NONE
        )
            return false
        val openApp =
            PendingIntent.getActivity(
                context,
                QUICK_RECORD_REQUEST_CODE,
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_QUICK_RECORD)
                    .setData(android.net.Uri.parse("xike://reminder/$deliveryId"))
                    .putExtra("delivery-id", deliveryId)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(localizedText("给今天留一刻"))
                .setContentText(localizedText("如果愿意，看看此刻的心情。没有连续打卡，也不会催促你。"))
                .setContentIntent(openApp)
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        context,
                        REMINDER_REQUEST_CODE,
                        Intent(context, ReminderReceiver::class.java)
                            .setAction(ACTION_ACKNOWLEDGE_REMINDER)
                            .setData(android.net.Uri.parse("xike://reminder/$deliveryId"))
                            .putExtra("delivery-id", deliveryId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .build()
        manager.notify(deliveryId, REMINDER_NOTIFICATION_ID, notification)
        return true
    }

    fun receive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_ACKNOWLEDGE_REMINDER) {
            acknowledge(context, intent)
            return
        }
        if (intent?.action != ACTION_SHOW_REMINDER) return
        val preferences =
            context.getSharedPreferences("xike-reminder-schedule", Context.MODE_PRIVATE)
        val token = intent.getStringExtra("alarm-token") ?: return
        if (token != preferences.getString("alarm-token", null)) {
            // A previous alarm may survive a crash between committing the new schedule and
            // registering it. Recover only the current saved state, never the stale payload.
            reconcile(context, restorePending = true)
            return
        }
        reconcile(context, restorePending = true)
    }

    fun acknowledge(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra("delivery-id") ?: return
        val preferences =
            context.getSharedPreferences("xike-reminder-schedule", Context.MODE_PRIVATE)
        if (id != preferences.getString("delivery-id", null)) return
        val next = preferences.getLong("delivery-next-at", 0L)
        if (next <= 0L) return
        check(preferences.edit().putLong("pending-at", next).remove("delivery-id").commit())
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                    CHANNEL_ID,
                    localizedText("记录提醒"),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                .apply {
                    description = localizedText("仅按你选择的本地时间，温和提醒你记录此刻。")
                }
        )
    }

    private fun reminderPendingIntent(context: Context, token: String? = null): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SHOW_REMINDER).apply {
                if (token != null) putExtra("alarm-token", token)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelPendingAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(reminderPendingIntent(context))
    }

    private fun setRescheduleReceiverEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, ReminderRescheduleReceiver::class.java),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    private const val CHANNEL_ID = "journal-reminder"
    private const val ACTION_SHOW_REMINDER = "com.xike.app.action.SHOW_REMINDER"
    private const val ACTION_ACKNOWLEDGE_REMINDER = "com.xike.app.action.ACKNOWLEDGE_REMINDER"
    private const val REMINDER_NOTIFICATION_ID = 402
    private const val REMINDER_REQUEST_CODE = 402
    private const val QUICK_RECORD_REQUEST_CODE = 403
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReminderScheduler.receive(context, intent)
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action in SUPPORTED_ACTIONS) {
            ReminderScheduler.reconcile(
                context,
                restorePending =
                    intent?.action != Intent.ACTION_TIME_CHANGED &&
                        intent?.action != Intent.ACTION_TIMEZONE_CHANGED,
            )
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
    }
}
