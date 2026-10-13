package com.xike.app.ui.components

import com.xike.app.ui.theme.showWithXikeShape

import com.xike.app.localizedText
import com.xike.app.R


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.width
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun showRecordedAtPicker(
    context: Context,
    recordedAt: Long?,
    onSelected: (Long) -> Unit,
): DatePickerDialog {
    val nowMillis = System.currentTimeMillis()
    val zoneId = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val initial = recordedAt
        ?.coerceAtMost(nowMillis)
        ?.let { Instant.ofEpochMilli(it).atZone(zoneId) }
        ?: now

    val dialog = DatePickerDialog(
        context,
        R.style.PickerSurface,
        { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            showRecordedAtTimePicker(context, date, initial.hour, initial.minute, onSelected)
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).apply {
        datePicker.maxDate = nowMillis
        create()
        datePicker.layoutParams = datePicker.layoutParams.apply {
            width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        }
        datePicker.getChildAt(0)?.let { content ->
            content.layoutParams = content.layoutParams.apply {
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
    }
    dialog.showWithXikeShape()
    return dialog
}

internal fun showRecordedAtTimePicker(
    context: Context,
    date: LocalDate,
    initialHour: Int,
    initialMinute: Int,
    onSelected: (Long) -> Unit,
): TimePickerDialog {
    var rejectDismiss = false
    val dialog = object : TimePickerDialog(
        context,
        R.style.PickerSurface,
        { _, hour, minute ->
            val selected = date.atTime(hour, minute)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (selected > System.currentTimeMillis()) {
                rejectDismiss = true
                XikeNotice.makeText(context, localizedText("记录时间不能晚于现在"), XikeNotice.LENGTH_SHORT).show()
            } else {
                onSelected(selected)
            }
        },
        initialHour,
        initialMinute,
        true,
    ) {
        override fun dismiss() {
            // Keep the platform's input validation and only veto dismissal of a future time.
            if (rejectDismiss) {
                rejectDismiss = false
            } else {
                super.dismiss()
            }
        }
    }
    dialog.showWithXikeShape()
    return dialog
}
