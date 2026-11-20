package com.xike.app

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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Capture the app's own dialog on API 26+, keeping its secure-window policy intact. */
internal fun captureAiDialogWindow(output: File) {
    val copied = CountDownLatch(1)
    val status = AtomicInteger(PixelCopy.ERROR_UNKNOWN)
    lateinit var image: Bitmap
    Espresso.onView(ViewMatchers.isRoot()).inRoot(RootMatchers.isDialog()).check { view, failure ->
        if (failure != null) throw failure
        val window = requireNotNull(aiDialogWindow(view))
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
        check(status.get() == PixelCopy.SUCCESS) { "Dialog capture failed: ${status.get()}" }
        output.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally {
        image.recycle()
    }
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
