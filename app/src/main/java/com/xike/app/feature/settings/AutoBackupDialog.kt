package com.xike.app.feature.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.xike.app.AppLocale
import com.xike.app.R
import com.xike.app.backup.AutoBackup
import com.xike.app.backup.AutoBackupPreferences
import com.xike.app.backup.AutoBackupSettings
import com.xike.app.backup.AutoBackupStorage
import com.xike.app.backup.DEFAULT_AUTO_BACKUP_DIRECTORY
import com.xike.app.rememberSystemActivityResultLauncher
import com.xike.app.tr
import com.xike.app.ui.theme.LocalRecordedAtPickerContext
import com.xike.app.ui.theme.XikeDialogCompactGap
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.showWithXikeShape
import com.xike.app.ui.theme.xikeSwitchColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AutoBackupUiState(
    val settings: AutoBackupSettings,
    val hasPassword: Boolean,
    val nextAt: Long,
    val lastSuccess: Long,
    val status: String?,
)

@Composable
internal fun AutoBackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var initial by remember { mutableStateOf<AutoBackupUiState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val loaded =
                withContext(Dispatchers.IO) {
                    AutoBackupPreferences(context).let {
                        AutoBackupUiState(
                            it.settings(),
                            it.password != null,
                            it.nextAt,
                            it.lastSuccess,
                            it.status,
                        )
                    }
                }
            initial = loaded
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = tr("无法读取自动备份设置，请重试。", "Unable to read automatic backup settings. Try again.")
        }
    }
    val settings = initial
    if (settings != null) {
        AutoBackupSettingsDialog(settings, onDismiss)
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("自动备份", "Automatic backup")) },
            text = { Text(error ?: tr("正在读取设置…", "Loading settings…")) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(tr("关闭", "Close")) } },
        )
    }
}

@Composable
private fun AutoBackupSettingsDialog(
    loaded: AutoBackupUiState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pickerContext = LocalRecordedAtPickerContext.current
    val scope = rememberCoroutineScope()
    val initial = loaded.settings
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var encrypted by rememberSaveable { mutableStateOf(initial.encrypted) }
    var weekday by rememberSaveable { mutableStateOf(initial.weekday) }
    var weeklyDay by rememberSaveable { mutableStateOf(initial.weekday.takeIf { it > 0 } ?: 7) }
    var hour by rememberSaveable { mutableStateOf(initial.hour) }
    var minute by rememberSaveable { mutableStateOf(initial.minute) }
    var keep by rememberSaveable { mutableStateOf(initial.keepCount.toString()) }
    var directory by rememberSaveable { mutableStateOf(initial.directory) }
    var directoryName by remember(directory) { mutableStateOf<String?>(null) }
    var directoryReading by
        remember(directory) {
            mutableStateOf(directory.isNotBlank() && directory != DEFAULT_AUTO_BACKUP_DIRECTORY)
        }
    LaunchedEffect(directory) {
        directoryName = null
        if (directory.isNotBlank() && directory != DEFAULT_AUTO_BACKUP_DIRECTORY) {
            directoryReading = true
            try {
                directoryName =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            AutoBackupStorage.displayName(context, directory)
                        }
                            .getOrNull()
                    }
            } finally {
                directoryReading = false
            }
        }
    }
    // Secrets stay in memory until explicitly saved to encrypted device-local preferences.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val hasPassword = loaded.hasPassword
    val nextAt = loaded.nextAt
    val lastSuccess = loaded.lastSuccess
    val status = loaded.status
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val directoryLauncher =
        rememberSystemActivityResultLauncher(
            ActivityResultContracts.OpenDocumentTree(),
            "auto-backup-directory",
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    directory = uri.toString()
                    error = null
                } catch (_: Exception) {
                    error =
                        tr("无法获得目录访问权限，请重新选择。", "Unable to access this folder. Select it again.")
                }
            }
        }
    fun displayTime(value: Long): String =
        Instant.ofEpochMilli(value)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", AppLocale.locale))

    fun validationError(): String? =
        when {
            (keep.toIntOrNull() ?: 0) !in 1..100 ->
                tr("保留数量需为 1–100。", "Keep between 1 and 100 copies.")
            enabled && directory.isBlank() -> tr("请先选择备份目录。", "Choose a backup folder first.")
            encrypted &&
                (password.isNotEmpty() || (enabled && !hasPassword)) &&
                password.length < 8 ->
                tr("备份密码至少需要 8 位。", "Use at least 8 characters for the password.")
            encrypted && password.isNotEmpty() && password != confirmation ->
                tr("两次输入的密码不一致。", "Passwords do not match.")
            else -> null
        }

    fun saveSettings() {
        error = validationError()
        val count = keep.toIntOrNull()
        if (error != null || count == null) return
        scope.launch {
            saving = true
            try {
                AutoBackup.save(
                    context,
                    AutoBackupSettings(enabled, weekday, hour, minute, count, directory, encrypted),
                    password.takeIf { encrypted && it.isNotEmpty() },
                )
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error =
                    tr(
                        "无法保存，请检查目录权限和可用空间后重试。",
                        "Unable to save. Check folder access and free space, then try again.",
                    )
            } finally {
                saving = false
            }
        }
    }

    val storagePermissionLauncher =
        rememberSystemActivityResultLauncher(
            ActivityResultContracts.RequestMultiplePermissions(),
            "auto-backup-storage-permission",
        ) {
            if (!AutoBackupStorage.needsLegacyPermission(context, directory)) saveSettings()
            else
                error =
                    tr(
                        "需要存储权限才能使用默认目录，也可以更改为系统授权目录。",
                        "Storage permission is required for the default folder. You can also choose a folder through the system picker.",
                    )
        }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        properties = DialogProperties(decorFitsSystemWindows = false),
        shape = XikeShapes.dialog,
        title = { Text(tr("自动备份", "Automatic backup")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        tr("启用自动备份", "Enable automatic backup"),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        enabled = !saving,
                        colors = xikeSwitchColors(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("备份计划", "Schedule"), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            Modifier.selectable(
                                    weekday == 0,
                                    enabled = !saving,
                                    role = Role.RadioButton,
                                    onClick = { weekday = 0 },
                                )
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = weekday == 0, onClick = null)
                            Text(tr("每天", "Daily"))
                        }
                        Row(
                            Modifier.selectable(
                                    selected = weekday != 0,
                                    enabled = !saving,
                                    role = Role.RadioButton,
                                    onClick = { weekday = weeklyDay },
                                )
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = weekday != 0, onClick = null)
                            Text(tr("每周", "Weekly"))
                        }
                    }
                    if (weekday != 0) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(XikeDialogCompactGap),
                            maxItemsInEachRow = 4,
                        ) {
                            DayOfWeek.entries.forEach { day ->
                                Row(
                                    Modifier.widthIn(min = 56.dp)
                                        .selectable(
                                            weekday == day.value,
                                            enabled = !saving,
                                            role = Role.RadioButton,
                                            onClick = {
                                                weeklyDay = day.value
                                                weekday = day.value
                                            },
                                        )
                                        .heightIn(min = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = weekday == day.value, onClick = null)
                                    Text(day.getDisplayName(TextStyle.SHORT, AppLocale.locale))
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(tr("备份时间", "Time"), Modifier.weight(1f))
                        TextButton(
                            enabled = !saving,
                            onClick = {
                                TimePickerDialog(
                                        pickerContext,
                                        R.style.PickerSurface,
                                        { _, h, m ->
                                            hour = h
                                            minute = m
                                        },
                                        hour,
                                        minute,
                                        true,
                                    )
                                    .showWithXikeShape()
                            },
                        ) {
                            Text("%02d:%02d".format(hour, minute))
                        }
                    }
                    Text(
                        tr(
                            "按本地时间备份，系统可能延后执行。",
                            "Uses local time. The system may delay execution.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tr("备份目录", "Backup folder"),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        TextButton(
                            enabled = !saving,
                            onClick = {
                                directoryLauncher.launch(
                                    directory
                                        .takeIf {
                                            it.isNotEmpty() && it != DEFAULT_AUTO_BACKUP_DIRECTORY
                                        }
                                        ?.let(Uri::parse)
                                )
                            },
                        ) {
                            Text(
                                if (directory.isEmpty()) tr("选择", "Choose") else tr("更改", "Change")
                            )
                        }
                    }
                    Text(
                        when {
                            directory == DEFAULT_AUTO_BACKUP_DIRECTORY ->
                                tr("下载 / Xike", "Downloads / Xike")
                            directory.isBlank() -> tr("尚未选择目录", "No folder selected")
                            directoryReading -> tr("正在读取目录…", "Reading folder…")
                            !directoryName.isNullOrBlank() -> directoryName.orEmpty()
                            else -> tr("目录暂时不可用，请重新选择。", "Folder unavailable. Choose it again.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (directory == DEFAULT_AUTO_BACKUP_DIRECTORY) {
                        Text(
                            tr("首次备份时自动创建", "Created with the first backup"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(
                            enabled = !saving,
                            onClick = { directory = DEFAULT_AUTO_BACKUP_DIRECTORY },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(tr("使用默认目录", "Use default folder"))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap)) {
                    OutlinedTextField(
                        value = keep,
                        shape = XikeShapes.inner,
                        onValueChange = { keep = it },
                        label = { Text(tr("保留数量（1–100）", "Copies to keep (1–100)")) },
                        singleLine = true,
                        enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        tr(
                            "新备份校验成功后再清理旧自动备份。",
                            "Older automatic backups are removed after the new copy is verified.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            tr("使用密码加密", "Encrypt with password"),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Switch(
                            checked = encrypted,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = tr("使用密码加密", "Encrypt with password")
                                },
                            onCheckedChange = {
                                encrypted = it
                                error = null
                            },
                            enabled = !saving,
                            colors = xikeSwitchColors(),
                        )
                    }
                    if (encrypted) {
                        OutlinedTextField(
                            value = password,
                            shape = XikeShapes.inner,
                            onValueChange = { password = it },
                            label = {
                                Text(
                                    if (hasPassword) tr("新密码（留空沿用）", "New password (blank to keep)")
                                    else
                                        tr(
                                            "备份密码（至少 8 位）",
                                            "Backup password (at least 8 characters)",
                                        )
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (password.isNotEmpty())
                            OutlinedTextField(
                                value = confirmation,
                                shape = XikeShapes.inner,
                                onValueChange = { confirmation = it },
                                label = { Text(tr("确认密码", "Confirm password")) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !saving,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        Text(
                            tr(
                                "换机恢复需要此密码，请妥善保管。",
                                "Keep this password for recovery on another device.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            tr(
                                "未加密备份保存为 ZIP，无需密码。解压后可直接阅读，拿到文件的人也能看到全部内容。",
                                "Unencrypted backups are saved as ZIP files without a password. Anyone with the file can extract and read all contents.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if ((initial.enabled && nextAt > 0) || lastSuccess > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (initial.enabled && nextAt > 0)
                            Text(tr("下次计划：", "Next scheduled: ") + displayTime(nextAt))
                        if (lastSuccess > 0)
                            Text(tr("上次成功：", "Last successful: ") + displayTime(lastSuccess))
                    }
                }
                status?.let {
                    Text(
                        when (it) {
                            "directory" ->
                                tr("目录无法访问，请重新选择目录。", "Folder unavailable. Select it again.")
                            "cleanup" ->
                                tr(
                                    "备份已保存，部分旧备份未能清理。",
                                    "Backup saved. Some older copies could not be removed.",
                                )
                            else ->
                                tr(
                                    "上次备份失败，将自动重试。请检查目录权限和可用空间。",
                                    "Backup failed and will retry. Check folder access and free space.",
                                )
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    error = validationError()
                    if (error == null) {
                        if (
                            enabled && AutoBackupStorage.needsLegacyPermission(context, directory)
                        ) {
                            storagePermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                )
                            )
                        } else saveSettings()
                    }
                },
            ) {
                Text(if (saving) tr("正在保存…", "Saving…") else tr("保存", "Save"))
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(tr("取消", "Cancel")) }
        },
    )
}
