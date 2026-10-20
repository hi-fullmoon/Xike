package com.xike.app.outdoor

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Separate from journal storage and excluded from exported backups. Open only on an IO dispatcher.
 */
internal class AmapSettingsStore(
    context: Context,
    private val name: String = "xike-amap-settings",
) {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            name,
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun read(): AmapSettings =
        AmapSettings(
            apiKey = preferences.getString("key", "").orEmpty(),
            enabled = preferences.getBoolean("enabled", false),
            privacyAccepted = preferences.getBoolean("privacy", false),
        )

    fun save(settings: AmapSettings) {
        val normalized = settings.normalized()
        require(normalized.isValid())
        check(
            preferences
                .edit()
                .putString("key", normalized.apiKey)
                .putBoolean("enabled", normalized.enabled)
                .putBoolean("privacy", normalized.privacyAccepted)
                .commit()
        )
    }
}
