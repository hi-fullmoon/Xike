package com.xike.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Retains launch completion across locale changes, rotation and process restoration. */
internal class WelcomeLaunchState(private val savedState: SavedStateHandle) : ViewModel() {
    var visible by mutableStateOf(savedState.get<Boolean>("welcomeVisible") ?: true)
        private set

    fun finish() {
        savedState["welcomeVisible"] = false
        visible = false
    }
}
