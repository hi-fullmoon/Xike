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
import java.time.LocalDate
import java.time.ZonedDateTime

const val ACTION_QUICK_RECORD = "com.xike.app.action.QUICK_RECORD"

internal object ReminderScheduler {
    fun reconcile(
        context: Context,
        settings: ReminderSettings = HabitPreferences(context).reminder,
        now: ZonedDateTime = ZonedDateTime.now(),
    ) {
        val appContext = context.applicationContext
        setRescheduleReceiverEnabled(appContext, settings.enabled)
        cancelPendingAlarm(appContext)
        if (!settings.enabled) return

        createNotificationChannel(appContext)
        val next = nextReminderAt(settings, now) ?: return
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            reminderPendingIntent(appContext),
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        cancelPendingAlarm(appContext)
        setRescheduleReceiverEnabled(appContext, false)
    }

    fun showReminder(context: Context) {
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            QUICK_RECORD_REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_QUICK_RECORD)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("给今天留一刻")
            .setContentText("如果愿意，看看此刻的心情。没有连续打卡，也不会催促你。")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(REMINDER_NOTIFICATION_ID, notification)
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
                "记录提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "仅按你选择的本地时间，温和提醒你记录此刻。"
            },
        )
    }

    private fun reminderPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REMINDER_REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_SHOW_REMINDER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelPendingAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            ?.cancel(reminderPendingIntent(context))
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
    private const val REMINDER_NOTIFICATION_ID = 402
    private const val REMINDER_REQUEST_CODE = 402
    private const val QUICK_RECORD_REQUEST_CODE = 403
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val settings = HabitPreferences(context).reminder
        val now = ZonedDateTime.now()
        val pausedUntil = settings.pausedUntilEpochDay?.let(LocalDate::ofEpochDay)
        val canShowToday = settings.enabled &&
            (pausedUntil == null || !now.toLocalDate().isBefore(pausedUntil))

        if (canShowToday) ReminderScheduler.showReminder(context)
        ReminderScheduler.reconcile(context, settings, now.plusSeconds(1))
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action in SUPPORTED_ACTIONS) ReminderScheduler.reconcile(context)
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
