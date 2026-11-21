package com.xike.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Capture the app's own dialog on API 26+, keeping its secure-window policy intact. */
internal fun captureAiDialogWindow(output: File) {
    captureWindow(output, inDialog = true)
}

/** Capture the full-screen AI review page from the host Activity window on API 26+. */
internal fun captureAiPageWindow(output: File) {
    captureWindow(output, inDialog = false)
}

private fun captureWindow(output: File, inDialog: Boolean) {
    val copied = CountDownLatch(1)
    val status = AtomicInteger(PixelCopy.ERROR_UNKNOWN)
    lateinit var image: Bitmap
    val interaction =
        if (inDialog) Espresso.onView(ViewMatchers.isRoot()).inRoot(RootMatchers.isDialog())
        else Espresso.onView(ViewMatchers.isRoot())
    interaction.check { view, failure ->
        if (failure != null) throw failure
        val window =
            requireNotNull(if (inDialog) aiDialogWindow(view) else hostActivityWindow(view))
        image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            window,
            image,
            {
                status.set(it)
                copied.countDown()
            },
            Handler(Looper.getMainLooper()),
        )
    }
    try {
        check(copied.await(5, TimeUnit.SECONDS))
        check(status.get() == PixelCopy.SUCCESS) { "Window capture failed: ${status.get()}" }
        output.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally {
        image.recycle()
    }
}

private fun hostActivityWindow(view: View): Window? {
    fun unwrap(context: Context?): Window? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current.window
            current = current.baseContext
        }
        return (current as? Activity)?.window
    }
    return unwrap(view.findViewById<View>(android.R.id.content)?.context)
        ?: unwrap(view.context)
        ?: ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .firstOrNull()
            ?.window
}

private fun aiDialogWindow(view: View): Window? {
    if (view is DialogWindowProvider) return view.window
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            aiDialogWindow(view.getChildAt(index))?.let {
                return it
            }
        }
    }
    return null
}
