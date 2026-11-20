package com.xike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.ai.AiSettings
import com.xike.app.ai.AiSettingsStore
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Separate write/read invocations prove disk persistence; generated secrets are never sent. */
class AiSettingsPersistenceTest {
    @Test
    fun encryptedConfigurationPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "xike-ai-persistence-20261104"
        val metadata = context.getSharedPreferences("$name-meta", Context.MODE_PRIVATE)
        when (InstrumentationRegistry.getArguments().getString("aiPersistencePhase")) {
            "write" -> {
                val secret = UUID.randomUUID().toString()
                val settings =
                    AiSettings("https://api.deepseek.com/v1", secret, "deepseek-flash", true, true)
                AiSettingsStore(context, name).save(settings)
                assertTrue(
                    metadata
                        .edit()
                        .putString("digest", digest(secret))
                        .putInt("pid", android.os.Process.myPid())
                        .commit()
                )
                val disk =
                    File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
                assertFalse(disk.contains(secret))
                assertFalse(disk.contains(settings.address))
                assertFalse(disk.contains(settings.model))
            }
            "read" ->
                try {
                    assertNotEquals(metadata.getInt("pid", -1), android.os.Process.myPid())
                    val restored = AiSettingsStore(context, name).read()
                    assertEquals(metadata.getString("digest", null), digest(restored.apiKey))
                    assertEquals(
                        "https://api.deepseek.com/v1/chat/completions",
                        restored.endpoint().toString(),
                    )
                    assertEquals("deepseek-flash", restored.model)
                    assertTrue(restored.canUse)
                    AiSettingsStore(context, name).save(restored.copy(enabled = false))
                    assertFalse(AiSettingsStore(context, name).read().canUse)
                    assertEquals(restored.apiKey, AiSettingsStore(context, name).read().apiKey)
                    AiSettingsStore(context, name).save(AiSettings())
                    assertEquals("", AiSettingsStore(context, name).read().apiKey)
                } finally {
                    context.deleteSharedPreferences(name)
                    context.deleteSharedPreferences("$name-meta")
                }
            else ->
                try {
                    val settings =
                        AiSettings(
                            "https://api.deepseek.com/v1",
                            UUID.randomUUID().toString(),
                            "deepseek-flash",
                            true,
                            true,
                        )
                    AiSettingsStore(context, name).save(settings)
                    assertEquals(settings, AiSettingsStore(context, name).read())
                    assertFalse(
                        File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
                            .readText()
                            .contains(settings.apiKey)
                    )
                } finally {
                    context.deleteSharedPreferences(name)
                    context.deleteSharedPreferences("$name-meta")
                }
        }
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
}
