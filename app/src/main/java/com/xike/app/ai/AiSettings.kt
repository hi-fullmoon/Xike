package com.xike.app.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Device-local credentials. Never log, save in Activity state, or include in journal backups. */
internal data class AiSettings(
    val address: String = "",
    val apiKey: String = "",
    val model: String = "",
    val enabled: Boolean = false,
    val consent: Boolean = false,
) {
    fun normalized() = copy(address = address.trim(), apiKey = apiKey.trim(), model = model.trim())

    fun endpoint(): HttpUrl? {
        val url = address.toHttpUrlOrNull() ?: return null
        if (
            !url.isHttps ||
                url.username.isNotEmpty() ||
                url.password.isNotEmpty() ||
                url.query != null ||
                url.fragment != null
        )
            return null
        val path = url.encodedPath.trimEnd('/')
        return url.newBuilder()
            .encodedPath(if (path.endsWith("/chat/completions")) path else "$path/chat/completions")
            .build()
    }

    val canPreview: Boolean
        get() = endpoint() != null && model.isNotBlank() && validFields()

    val configured: Boolean
        get() = canPreview && apiKey.isNotBlank()

    val canUse: Boolean
        get() = enabled && consent && configured

    private fun validFields(): Boolean =
        address.length <= 2048 &&
            apiKey.length <= 4096 &&
            model.length <= 256 &&
            listOf(address, apiKey, model).none { value -> value.any { it.isISOControl() } } &&
            apiKey.all { it.code in 33..126 }

    fun isValid(): Boolean =
        validFields() && (address.isBlank() || endpoint() != null) && (!enabled || canUse)

    override fun toString() = "AiSettings(configured=$configured, enabled=$enabled)"
}

/** Open/read/write on IO only. Separate from all exported journal data. */
internal class AiSettingsStore(context: Context, private val name: String = "xike-ai-settings") {
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

    fun read(): AiSettings =
        AiSettings(
                preferences.getString("address", "").orEmpty(),
                preferences.getString("key", "").orEmpty(),
                preferences.getString("model", "").orEmpty(),
                preferences.getBoolean("enabled", false),
                preferences.getBoolean("consent", false),
            )
            .also { check(it.isValid()) }

    fun save(settings: AiSettings) {
        val value = settings.normalized()
        require(value.isValid())
        check(
            preferences
                .edit()
                .putString("address", value.address)
                .putString("key", value.apiKey)
                .putString("model", value.model)
                .putBoolean("enabled", value.enabled)
                .putBoolean("consent", value.consent)
                .commit()
        )
    }
}
