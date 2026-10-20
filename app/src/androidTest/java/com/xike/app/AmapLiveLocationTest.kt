package com.xike.app

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amap.apis.utils.core.api.AMapUtilCoreApi
import com.amap.apis.utils.core.api.NetProxy
import com.xike.app.outdoor.AmapSettingsOperations
import com.xike.app.outdoor.AmapSettingsStore
import com.xike.app.outdoor.OutdoorContextException
import java.util.concurrent.ConcurrentSkipListSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit live integration check: uses a previously configured Key, never writes it or journals.
 */
@RunWith(AndroidJUnit4::class)
class AmapLiveLocationTest {
    @Test
    fun configuredKeyCanObtainALiveLocation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AmapSettingsStore(context)
        val settings = runBlocking { withContext(Dispatchers.IO) { store.read() } }
        assumeTrue("Requires a saved and enabled Key with consent", settings.canUseAmap)
        val previousProxy = AMapUtilCoreApi.getNetProxy()
        val hosts = ConcurrentSkipListSet<String>()
        val endpointKinds = ConcurrentSkipListSet<String>()
        // Observe SDK-owned host names only. Preserve the existing routing and never record paths,
        // query parameters, request headers, bodies, addresses, or the configured Key.
        AMapUtilCoreApi.setNetProxy(
            NetProxy { host, path ->
                if (
                    host.endsWith(".amap.com") ||
                        host.endsWith(".autonavi.com") ||
                        host.endsWith(".opnavi.com")
                ) {
                    hosts.add(host)
                    val kind =
                        when {
                            path.contains("regeo", ignoreCase = true) -> "REVERSE_GEOCODE"
                            path.contains("auth", ignoreCase = true) -> "AUTH"
                            host.contains("apilocate") -> "POSITION"
                            else -> "OTHER"
                        }
                    endpointKinds.add("$host:$kind")
                }
                previousProxy?.onHostProxy(host, path)
            }
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val result = runBlocking {
                    AmapSettingsOperations(context, store, {}).test(settings)
                }
                val diagnostic =
                    (result.exceptionOrNull() as? OutdoorContextException)?.amapDiagnostic?.name
                        ?: "NONE"
                InstrumentationRegistry.getInstrumentation()
                    .sendStatus(
                        0,
                        Bundle().apply {
                            putBoolean("amapLiveSucceeded", result.isSuccess)
                            putString("amapSafeDiagnostic", diagnostic)
                            putString("amapServiceHosts", hosts.joinToString(","))
                            putString("amapEndpointKinds", endpointKinds.joinToString(","))
                            putBoolean(
                                "amapCoreRegionMainland",
                                AMapUtilCoreApi.getCountryCode() == "ZH",
                            )
                        },
                    )
                // Do not put the returned address, Key, or raw SDK error detail in assertion
                // output.
                assertTrue(
                    "Live AMap request failed; sanitized diagnostic=$diagnostic",
                    result.isSuccess,
                )
            }
        } finally {
            AMapUtilCoreApi.setNetProxy(previousProxy)
        }
    }
}
