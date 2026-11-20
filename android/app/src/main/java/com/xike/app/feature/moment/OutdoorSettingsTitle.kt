package com.xike.app.feature.moment

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xike.app.feature.settings.AmapSettingsDialog
import com.xike.app.localizedText
import com.xike.app.outdoor.TripleTapGate

/** Hidden entry stays separate from the card's location and weather actions. */
@Composable
internal fun OutdoorSettingsTitle() {
    val taps = remember { TripleTapGate() }
    var showConfiguration by rememberSaveable { mutableStateOf(false) }
    Text(
        localizedText("此刻窗外"),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (taps.tap(SystemClock.elapsedRealtime())) showConfiguration = true
            },
    )
    if (showConfiguration) {
        AmapSettingsDialog(onDismiss = { showConfiguration = false })
    }
}
