package com.xike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.outdoor.AmapSettings
import com.xike.app.outdoor.AmapSettingsStore
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pass amapPersistencePhase=write/read in separate invocations to verify across processes. */
@RunWith(AndroidJUnit4::class)
class AmapSettingsPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "xike-amap-persistence-verification-20261020"

    @Test
    fun configurationPersistsAndCanBeDisabled() {
        when (InstrumentationRegistry.getArguments().getString("amapPersistencePhase")) {
            "write" -> writeConfiguration()
            "read" -> readAfterProcessRestart(checkProcess = true)
            else -> {
                writeConfiguration()
                readAfterProcessRestart(checkProcess = false)
            }
        }
    }

    private fun writeConfiguration() {
        val key = UUID.randomUUID().toString().replace("-", "")
        val store = AmapSettingsStore(context, name)
        store.save(AmapSettings(key, true, true))
        val digest =
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        assertTrue(
            context
                .getSharedPreferences("$name-meta", Context.MODE_PRIVATE)
                .edit()
                .putString("digest", digest)
                .putInt("writer-pid", android.os.Process.myPid())
                .commit()
        )
        assertFalse(
            File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText().contains(key)
        )
    }

    private fun readAfterProcessRestart(checkProcess: Boolean) {
        val store = AmapSettingsStore(context, name)
        try {
            val restored = store.read()
            if (checkProcess)
                assertTrue(
                    context
                        .getSharedPreferences("$name-meta", Context.MODE_PRIVATE)
                        .getInt("writer-pid", -1) != android.os.Process.myPid()
                )
            val expectedDigest =
                context
                    .getSharedPreferences("$name-meta", Context.MODE_PRIVATE)
                    .getString("digest", null)
            assertTrue(expectedDigest != null)
            val actualDigest =
                MessageDigest.getInstance("SHA-256")
                    .digest(restored.apiKey.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            assertTrue(expectedDigest == actualDigest)
            assertTrue(restored.canUseAmap)
            store.save(restored.copy(enabled = false))
            val disabled = AmapSettingsStore(context, name).read()
            assertFalse(disabled.canUseAmap)
            assertTrue(disabled.apiKey == restored.apiKey)
            store.save(AmapSettings())
            assertTrue(AmapSettingsStore(context, name).read().apiKey.isEmpty())
        } finally {
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("$name-meta")
        }
    }
}
