package com.xike.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import java.time.format.DateTimeFormatter

internal data class PendingExport(val password: String?)

internal class BackupExportState : ViewModel() {
    private var pending: PendingExport? = null

    fun begin(password: String?) { pending = PendingExport(password) }

    fun take(): PendingExport? = pending.also { pending = null }
}


@Composable
internal fun RestoreConfirmationDialog(
    summary: BackupSummary,
    localEntryCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val formatter = remember(AppLocale.language) {
        DateTimeFormatter.ofPattern(tr("yyyy年M月d日", "MMM d, yyyy"), AppLocale.locale)
    }
    val oldestCreatedAt = summary.oldestCreatedAt
    val newestCreatedAt = summary.newestCreatedAt
    val dateRange = if (oldestCreatedAt == null || newestCreatedAt == null) {
        localizedText("无记录日期")
    } else {
        val zone = java.time.ZoneId.systemDefault()
        val oldest = java.time.Instant.ofEpochMilli(oldestCreatedAt).atZone(zone).toLocalDate()
        val newest = java.time.Instant.ofEpochMilli(newestCreatedAt).atZone(zone).toLocalDate()
        if (oldest == newest) {
            formatter.format(oldest)
        } else {
            "${formatter.format(oldest)} – ${formatter.format(newest)}"
        }
    }

    DestructiveConfirmationDialog(
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        title = localizedText("确认替换设备内容？"),
        confirmText = localizedText("替换并恢复"),
        dismissText = localizedText("取消"),
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    tr("备份包含 ${summary.entryCount} 条日记、${summary.imageCount} 张图片", "The backup contains ${summary.entryCount} entries and ${summary.imageCount} photos") +
                        if (summary.audioCount > 0) tr("、${summary.audioCount} 段语音。", ", and ${summary.audioCount} voice notes.") else localizedText("。"),
                )
                Text(dateRange, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.videoCount > 0) {
                    val videoSize = android.text.format.Formatter.formatShortFileSize(LocalContext.current, summary.videoBytes)
                    Text(tr("${summary.videoCount} 段视频 · $videoSize", "${summary.videoCount} videos · $videoSize"))
                }
                Text(
                    tr("当前设备的 $localEntryCount 条日记将被替换。恢复前会创建加密安全快照，可撤销一次。", "The $localEntryCount entries on this device will be replaced. An encrypted safety snapshot is created first, allowing one undo."),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
internal fun BackupExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var encrypted by rememberSaveable { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        properties = DialogProperties(decorFitsSystemWindows = false),
        shape = XikeShapes.dialog,
        title = { Text(localizedText("导出日记备份"), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(tr("备份包含全部日记、照片、录音和视频。", "The backup includes all entries, photos, recordings and videos."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(localizedText("使用密码加密"), style = MaterialTheme.typography.titleSmall)
                        Text(localizedText("建议开启；密码不会保存在应用中"), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = encrypted, onCheckedChange = { encrypted = it }, colors = xikeSwitchColors())
                }
                if (encrypted) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        shape = XikeShapes.inner,
                        label = { Text(localizedText("备份密码（至少 8 位）")) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passwordVisible) localizedText("隐藏密码") else localizedText("显示密码"),
                                )
                            }
                        },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        shape = XikeShapes.inner,
                        label = { Text(localizedText("再次输入备份密码")) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirmation.isNotEmpty() && confirmation != password,
                        supportingText = if (confirmation.isNotEmpty() && confirmation != password) {
                            { Text(localizedText("两次密码不一致")) }
                        } else null,
                        singleLine = true,
                    )
                    Text(localizedText("加密文件保存为 .xike；在应用内输入密码后可恢复，不能直接用浏览器打开 HTML。"), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        localizedText("未加密文件保存为 .zip。解压后打开 index.html 即可离线查看；拿到文件的人也能看到全部内容。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                shape = XikeShapes.button,
                enabled = !encrypted || (password.length >= 8 && confirmation == password),
                onClick = { onConfirm(password.takeIf { encrypted }) },
            ) { Text(localizedText("选择保存位置")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("取消")) } },
    )
}

@Composable
internal fun BackupPasswordDialog(
    title: String,
    description: String,
    confirm: String,
    requireConfirmation: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        properties = DialogProperties(decorFitsSystemWindows = false),
        shape = XikeShapes.dialog,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    shape = XikeShapes.inner,
                    label = { Text(localizedText("备份密码（至少 8 位）")) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) localizedText("隐藏密码") else localizedText("显示密码"),
                            )
                        }
                    },
                    singleLine = true,
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        shape = XikeShapes.inner,
                        label = { Text(localizedText("再次输入备份密码")) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirmation.isNotEmpty() && confirmation != password,
                        supportingText = if (confirmation.isNotEmpty() && confirmation != password) {
                            { Text(localizedText("两次密码不一致")) }
                        } else null,
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                shape = XikeShapes.button,
                enabled = password.length >= 8 && (!requireConfirmation || confirmation == password),
                onClick = { onConfirm(password) },
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("取消")) } },
    )
}
