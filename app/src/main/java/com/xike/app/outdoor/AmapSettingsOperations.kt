package com.xike.app.outdoor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import com.xike.app.tr
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal val LocalAmapSettingsOperations =
    staticCompositionLocalOf<AmapSettingsOperations?> { null }

internal data class AmapConfiguration(
    val settings: AmapSettings,
    val packageName: String,
    val sha1: String,
)

/** Business/persistence boundary for the settings dialog; no SDK initialization on read or save. */
internal class AmapSettingsOperations(
    context: Context,
    private val store: AmapSettingsStore,
    private val onChanging: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val writes = Mutex()

    suspend fun load(): AmapConfiguration =
        withContext(Dispatchers.IO) {
            val pm = appContext.packageManager
            @Suppress("DEPRECATION")
            val info =
                pm.getPackageInfo(
                    appContext.packageName,
                    if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                    else PackageManager.GET_SIGNATURES,
                )
            @Suppress("DEPRECATION")
            val signatures =
                if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
                else info.signatures
            val sha1 =
                signatures
                    ?.firstOrNull()
                    ?.toByteArray()
                    ?.let {
                        MessageDigest.getInstance("SHA-1").digest(it).joinToString(":") { byte ->
                            "%02X".format(byte.toInt() and 0xff)
                        }
                    }
                    .orEmpty()
            AmapConfiguration(store.read(), appContext.packageName, sha1)
        }

    suspend fun save(settings: AmapSettings): Result<Unit> = writes.withLock {
        onChanging()
        try {
            withContext(Dispatchers.IO) { store.save(settings) }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.failure(
                IllegalStateException(
                    tr("高德配置保存失败，请重试。", "Unable to save AMap settings. Please try again.")
                )
            )
        }
    }

    suspend fun test(settings: AmapSettings): Result<String> =
        try {
            Result.success(OutdoorContextRepository(appContext, store).testAmapLocation(settings))
        } catch (error: CancellationException) {
            throw error
        } catch (error: OutdoorContextException) {
            Result.failure(error)
        } catch (_: Exception) {
            Result.failure(amapFailure(-1))
        }
}
