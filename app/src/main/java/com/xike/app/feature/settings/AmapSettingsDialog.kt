package com.xike.app.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xike.app.outdoor.AmapConfiguration
import com.xike.app.outdoor.AmapSettings
import com.xike.app.outdoor.LocalAmapSettingsOperations
import com.xike.app.rememberSystemActivityResultLauncher
import com.xike.app.tr
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.xikeSwitchColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AmapSettingsDialog(onDismiss: () -> Unit) {
    val operations = LocalAmapSettingsOperations.current ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var configuration by remember { mutableStateOf<AmapConfiguration?>(null) }
    // Credentials deliberately stay out of rememberSaveable / Activity saved state.
    var key by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val settings = AmapSettings(key, enabled, accepted).normalized()

    fun testLocation() {
        testing = scope.launch {
            busy = true
            message = null
            try {
                val result = operations.test(settings.copy(enabled = true))
                isError = result.isFailure
                message =
                    result.fold(
                        { tr("定位成功：$it", "Location found: $it") },
                        { it.message },
                    )
            } finally {
                busy = false
            }
        }
    }

    val permissions =
        rememberSystemActivityResultLauncher(
            ActivityResultContracts.RequestMultiplePermissions(),
            "amap-settings-location-permission",
        ) { grants ->
            if (
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
            ) {
                testLocation()
            } else {
                isError = true
                message =
                    tr(
                        "未获得位置权限。可在系统设置中开启，或继续使用手动选城市。",
                        "Location permission was not granted. Enable it in system settings or choose a city manually.",
                    )
            }
        }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) testing?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            testing?.cancel()
        }
    }

    LaunchedEffect(operations) {
        try {
            val loaded = operations.load()
            key = loaded.settings.apiKey
            enabled = loaded.settings.enabled
            accepted = loaded.settings.privacyAccepted
            configuration = loaded
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            isError = true
            message = tr("无法读取高德配置，请关闭后重试。", "Unable to read AMap settings. Close and try again.")
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = XikeShapes.dialog,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(tr("高德定位配置", "AMap location settings")) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap),
            ) {
                Text(
                    tr(
                        "关闭时使用原有系统定位；开启后，仅在主动添加窗外信息或测试时请求一次高德定位。高德已取得 WGS84 位置但未返回地址时，由系统地址服务补全。天气仍由 Open-Meteo 提供。",
                        "When disabled, system location is used. AMap is requested once only when you add outdoor information or test. If AMap returns a WGS84 position without an address, the system address service completes it. Weather is still provided by Open-Meteo.",
                    )
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = {
                        key = it.take(256)
                        message = null
                    },
                    label = { Text(tr("Android SDK Key", "Android SDK Key")) },
                    enabled = configuration != null && !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation =
                        if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) tr("隐藏", "Hide") else tr("显示", "Show"))
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("使用高德定位", "Use AMap location"), modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        enabled =
                            !busy &&
                                configuration != null &&
                                (enabled || (key.isNotBlank() && accepted)),
                        colors = xikeSwitchColors(),
                    )
                }
                Text(
                    tr(
                        "高德会处理经纬度及相关网络、设备信息以提供定位。Key 仅在本机加密保存，不进入日记备份。",
                        "AMap processes coordinates and related network/device information to provide location. Your Key is encrypted on this device and excluded from journal backups.",
                    )
                )
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://lbs.amap.com/pages/privacy/"),
                                )
                            )
                        }
                            .onFailure {
                                isError = true
                                message =
                                    tr(
                                        "无法打开浏览器，请访问 lbs.amap.com/pages/privacy/。",
                                        "Unable to open a browser. Visit lbs.amap.com/pages/privacy/.",
                                    )
                            }
                    }
                ) {
                    Text(tr("查看高德隐私政策", "AMap privacy policy"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = accepted,
                        enabled = !busy && configuration != null,
                        onCheckedChange = {
                            accepted = it
                            if (!it) enabled = false
                        },
                    )
                    Text(
                        tr("我已阅读并同意使用高德定位服务", "I have read and agree to use AMap location services")
                    )
                }
                configuration?.let { config ->
                    Text(
                        "PackageName: ${config.packageName}\nSHA-1: ${config.sha1}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString("${config.packageName}\n${config.sha1}")
                            )
                        }
                    ) {
                        Text(tr("复制包名与 SHA-1", "Copy package and SHA-1"))
                    }
                }
                Row {
                    TextButton(
                        enabled = !busy && configuration != null && key.isNotBlank() && accepted,
                        onClick = {
                            permissions.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                    ) {
                        Text(if (busy) tr("处理中…", "Working…") else tr("测试定位", "Test location"))
                    }
                    TextButton(
                        enabled = !busy && configuration != null,
                        onClick = {
                            key = ""
                            enabled = false
                            accepted = false
                            message = null
                        },
                    ) {
                        Text(tr("清除 Key", "Clear Key"))
                    }
                }
                if (configuration == null && message == null)
                    Text(tr("正在读取配置…", "Loading settings…"))
                message?.let {
                    Text(
                        it,
                        color =
                            if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss, shape = XikeShapes.button) {
                Text(tr("取消", "Cancel"))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && configuration != null && settings.isValid(),
                shape = XikeShapes.button,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val result = operations.save(settings)
                            if (result.isSuccess) onDismiss()
                            else {
                                isError = true
                                message = result.exceptionOrNull()?.message
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                Text(tr("保存", "Save"))
            }
        },
    )
}
