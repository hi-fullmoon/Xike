package com.xike.app

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/** Observes only the caller's task while a system picker is open. Holds no Activity reference. */
internal class SystemActivityLockMonitor(
    context: Context,
    private val onInterrupted: (Long) -> Unit,
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var taskId = -1
    private val receiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            val userLeft = intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS &&
                intent.getStringExtra("reason") in setOf("homekey", "recentapps")
            if (intent.action == Intent.ACTION_SCREEN_OFF || userLeft) interrupt()
        }
    }
    private val checkVisibility = object : Runnable {
        override fun run() {
            if (!registered || Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return
            val visible = runCatching {
                context.getSystemService(ActivityManager::class.java).appTasks
                    .firstOrNull { it.taskInfo.taskId == taskId }?.taskInfo?.isVisible == true
            }.getOrDefault(false)
            if (!visible) interrupt()
            else handler.postDelayed(this, 100L)
        }
    }

    @Suppress("DEPRECATION")
    fun start(taskId: Int) {
        stop()
        this.taskId = taskId
        // These actions originate from the system; CLOSE_SYSTEM_DIALOGS restricts senders.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS) },
            ContextCompat.RECEIVER_EXPORTED,
        )
        registered = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) handler.post(checkVisibility)
    }

    fun stop() {
        handler.removeCallbacks(checkVisibility)
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
    }

    private fun interrupt() {
        if (!registered) return
        stop()
        onInterrupted(SystemClock.elapsedRealtime())
    }
}
