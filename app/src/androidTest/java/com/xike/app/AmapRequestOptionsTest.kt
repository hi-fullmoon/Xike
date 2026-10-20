package com.xike.app

import android.content.Context
import android.os.Bundle
import android.security.NetworkSecurityPolicy
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.xike.app.outdoor.OutdoorContextException
import com.xike.app.outdoor.amapFailure
import com.xike.app.outdoor.amapLocationOptions
import com.xike.app.outdoor.requireAmapNativeLibrary
import dalvik.system.BaseDexClassLoader
import java.util.Locale
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real SDK options without constructing a client or supplying any credentials. */
@RunWith(AndroidJUnit4::class)
class AmapRequestOptionsTest {
    @Test
    fun addressLanguageFollowsTheRequestedAppLocale() {
        assertEquals(
            AMapLocationClientOption.GeoLanguage.EN,
            amapLocationOptions(Locale.ENGLISH).geoLanguage,
        )
        assertEquals(
            AMapLocationClientOption.GeoLanguage.ZH,
            amapLocationOptions(Locale.SIMPLIFIED_CHINESE).geoLanguage,
        )
    }

    @Test
    fun packagedSdkNativeLibrariesMatchTheAppProcessOrFailWithAnActionableMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packagedAbis =
            ZipFile(context.applicationInfo.sourceDir).use { apk ->
                apk.entries()
                    .asSequence()
                    .map { it.name }
                    .filter { it.startsWith("lib/") && it.endsWith("/libapssdk.so") }
                    .map { it.split('/')[1] }
                    .toSet()
            }
        assertTrue(packagedAbis.contains("arm64-v8a"))
        assertTrue(packagedAbis.contains("armeabi-v7a"))
        val loader = AMapLocationClient::class.java.classLoader as BaseDexClassLoader
        val resolvable = loader.findLibrary("apssdk") != null
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                0,
                Bundle().apply {
                    putString("amapPackagedAbis", packagedAbis.sorted().joinToString(","))
                    putBoolean("amapNativeResolvable", resolvable)
                },
            )
        if (resolvable) {
            requireAmapNativeLibrary()
        } else {
            val failure = runCatching { requireAmapNativeLibrary() }.exceptionOrNull()
            assertTrue(failure is OutdoorContextException)
            assertTrue(failure?.message.orEmpty().contains("ARM"))
            // A real load attempt on this process verifies the missing library, without a Key.
            assertTrue(
                runCatching { System.loadLibrary("apssdk") }.exceptionOrNull()
                    is UnsatisfiedLinkError
            )
        }
    }

    @Test
    fun locationUsesHttpsAndAllowsTheSdkNetworkTimeoutToComplete() {
        val options = amapLocationOptions()
        assertEquals(AMapLocationClientOption.AMapLocationProtocol.HTTPS, options.locationProtocol)
        assertEquals(30_000L, options.httpTimeOut)
        assertTrue(options.isOnceLocation)
        assertTrue(options.isNeedAddress)
        assertFalse(options.isLocationCacheEnable)
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @Test
    fun connectionErrorsDoNotTellTheUserToReplaceTheKey() {
        assertFalse(amapFailure(4).message.orEmpty().contains("Key"))
        assertTrue(amapFailure(7).message.orEmpty().contains("Key"))
    }
}
