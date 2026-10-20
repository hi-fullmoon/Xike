package com.xike.app.outdoor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.xike.app.AppLocale
import com.xike.app.tr
import dalvik.system.BaseDexClassLoader
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class AmapPlace(
    val latitude: Double,
    val longitude: Double,
    val wgs84: Boolean,
    val labels: PlaceLabels,
    val systemAddressCompleted: Boolean,
)

/**
 * Resolve using the app's actual native-library search path, including translated ARM processes.
 */
internal fun requireAmapNativeLibrary() {
    val loader = AMapLocationClient::class.java.classLoader as? BaseDexClassLoader ?: return
    if (loader.findLibrary("apssdk") == null) {
        throw OutdoorContextException(
            tr(
                "当前应用进程找不到兼容的高德定位原生库。请使用 ARM 设备验证，或关闭高德定位以使用系统定位。Key 无需因此修改。",
                "The current app process has no compatible AMap location native library. Test on an ARM device, or disable AMap to use system location. This does not require changing your Key.",
            )
        )
    }
}

/**
 * SDK errors may contain credentials or coordinates: only expose our own message and numeric code.
 */
internal fun amapFailure(
    code: Int,
    diagnostic: AmapNetworkDiagnostic? = null,
): OutdoorContextException {
    val displayCode = if (code == 4 && diagnostic != null) "$code / ${diagnostic.tag}" else "$code"
    val reason =
        when (amapFailureKind(code)) {
            AmapFailureKind.NETWORK ->
                tr(
                    "高德网络请求失败（$displayCode）。请检查网络连接或代理后重试。",
                    "AMap network request failed ($displayCode). Check your connection or proxy and try again.",
                )
            AmapFailureKind.AUTH ->
                tr(
                    "高德 Key 鉴权失败（$code）。请检查 Android Key 绑定的包名和签名 SHA-1。",
                    "AMap Key authentication failed ($code). Check the Android Key's package and signing SHA-1.",
                )
            AmapFailureKind.PERMISSION ->
                tr(
                    "高德定位权限不足（$code）。请在系统设置中允许位置权限。",
                    "AMap location permission is missing ($code). Allow location access in system settings.",
                )
            AmapFailureKind.TIMEOUT ->
                tr(
                    "高德定位超时。请检查网络和定位信号后重试。",
                    "AMap location timed out. Check your connection and location signal, then try again.",
                )
            AmapFailureKind.OTHER ->
                tr(
                    "高德暂时无法取得位置（$code）。请稍后重试。",
                    "AMap could not obtain a location ($code). Please try again later.",
                )
        }
    return OutdoorContextException(
        reason +
            tr(
                "也可以在设置中关闭高德定位以使用系统定位。",
                " You can also disable AMap in Settings to use system location.",
            ),
        amapDiagnostic = diagnostic,
    )
}

/** Set transport before client construction, so authentication and location both use HTTPS. */
internal fun amapLocationOptions(locale: Locale = AppLocale.locale): AMapLocationClientOption {
    AMapLocationClientOption.setLocationProtocol(
        AMapLocationClientOption.AMapLocationProtocol.HTTPS
    )
    return AMapLocationClientOption().apply {
        isOnceLocation = true
        isOnceLocationLatest = true
        isNeedAddress = true
        isLocationCacheEnable = false
        setCacheCallBack(false)
        setNoLocReqCgiEnable(false)
        setLastLocationLifeCycle(0L)
        isSensorEnable = false
        httpTimeOut = 30_000L
        geoLanguage =
            if (locale.language == "en") AMapLocationClientOption.GeoLanguage.EN
            else AMapLocationClientOption.GeoLanguage.ZH
    }
}

internal class AmapLocator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun current(
        settings: AmapSettings,
        completeSystemAddress: suspend (Location) -> PlaceLabels?,
    ): AmapPlace {
        require(settings.canUseAmap)
        val locale = AppLocale.locale
        requireAmapNativeLibrary()
        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw OutdoorContextException(
                tr("需要位置权限才能测试或使用高德定位。", "Location permission is needed to test or use AMap.")
            )
        }
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            throw OutdoorContextException(tr("请先开启系统定位。", "Turn on system location first."))
        }
        // setApiKey is process-wide. Serialize SDK lifetimes, including settings tests.
        return sdkMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                var client: AMapLocationClient? = null
                try {
                    AMapLocationClient.updatePrivacyShow(appContext, true, true)
                    AMapLocationClient.updatePrivacyAgree(appContext, settings.privacyAccepted)
                    AMapLocationClient.setApiKey(settings.apiKey)
                    val option = amapLocationOptions(locale)
                    val activeClient = AMapLocationClient(appContext)
                    client = activeClient
                    activeClient.setLocationOption(option)
                    val result =
                        withTimeoutOrNull(35_000L) {
                            suspendCancellableCoroutine<AMapLocation> { continuation ->
                                activeClient.setLocationListener { location ->
                                    if (continuation.isActive) {
                                        if (location == null || location.errorCode != 0) {
                                            continuation.resumeWithException(
                                                amapFailure(
                                                    location?.errorCode ?: -1,
                                                    if (location?.errorCode == 4)
                                                        AmapNetworkDiagnostic.fromDetail(
                                                            location.locationDetail.orEmpty()
                                                        )
                                                    else null,
                                                )
                                            )
                                        } else continuation.resume(location)
                                    }
                                }
                                activeClient.startLocation()
                            }
                        } ?: throw amapFailure(-2)
                    asPlace(result, locale, completeSystemAddress)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: OutdoorContextException) {
                    throw error
                } catch (_: Exception) {
                    throw amapFailure(-1)
                } finally {
                    withContext(NonCancellable) {
                        client?.let {
                            runCatching { it.stopLocation() }
                            runCatching { it.onDestroy() }
                        }
                    }
                }
            }
        }
    }

    private suspend fun asPlace(
        location: AMapLocation,
        locale: Locale,
        completeSystemAddress: suspend (Location) -> PlaceLabels?,
    ): AmapPlace =
        withContext(Dispatchers.Default) {
            if (
                !location.latitude.isFinite() ||
                    location.latitude !in -90.0..90.0 ||
                    !location.longitude.isFinite() ||
                    location.longitude !in -180.0..180.0 ||
                    location.coordType !in
                        setOf(AMapLocation.COORD_TYPE_WGS84, AMapLocation.COORD_TYPE_GCJ02)
            )
                throw amapFailure(-3)
            val precise =
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            val includeStreet =
                precise &&
                    location.hasAccuracy() &&
                    location.accuracy.isFinite() &&
                    location.accuracy in 0f..100f
            val sdkLabels =
                amapPlaceLabels(
                    location.country.orEmpty(),
                    location.province.orEmpty(),
                    location.city.orEmpty(),
                    location.district.orEmpty(),
                    location.street.orEmpty(),
                    location.address.orEmpty(),
                    includeStreet,
                )
            val wgs84 = location.coordType == AMapLocation.COORD_TYPE_WGS84
            // GPS fixes can precede address lookup, particularly outside mainland China.
            // Only WGS84 fixes may be sent to the existing system geocoder.
            val labels =
                sdkLabels
                    ?: (if (wgs84) completeSystemAddress(location) else null)
                    ?: throw amapFailure(-4)
            AmapPlace(
                location.latitude,
                location.longitude,
                wgs84,
                PlaceLabels(
                    localizePlaceName(labels.label, locale),
                    localizePlaceName(labels.fullLabel, locale),
                ),
                systemAddressCompleted = sdkLabels == null,
            )
        }

    private companion object {
        val sdkMutex = Mutex()
    }
}
