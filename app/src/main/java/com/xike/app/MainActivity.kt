package com.xike.app

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            val journalViewModel: JournalViewModel = viewModel()

            XikeTheme(journalViewModel.selectedTheme) {
                XikeApp(
                    entries = journalViewModel.entries,
                    selectedTheme = journalViewModel.selectedTheme,
                    onThemeChange = journalViewModel::selectTheme,
                    onSave = journalViewModel::save,
                    onExportBackup = journalViewModel::exportBackup,
                    onRestoreBackup = journalViewModel::restoreBackup,
                    openImage = journalViewModel::openImage,
                )

                journalViewModel.dataError?.let { message ->
                    DataErrorDialog(message, journalViewModel::dismissDataError)
                }

                if (journalViewModel.isLoading) {
                    ProcessingDialog("正在读取加密日记…")
                }
            }
        }
    }
}

private enum class BackupAction { EXPORT, IMPORT }

@Composable
private fun XikeApp(
    entries: List<JournalEntry>,
    selectedTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onSave: suspend (JournalEntry, List<Uri>) -> Result<Unit>,
    onExportBackup: suspend (Uri, String) -> Result<Unit>,
    onRestoreBackup: suspend (Uri, String) -> Result<Int>,
    openImage: (String) -> InputStream?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var backupAction by remember { mutableStateOf<BackupAction?>(null) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && password != null) {
            scope.launch {
                busyMessage = "正在创建加密备份…"
                onExportBackup(uri, password)
                    .onSuccess {
                        Toast.makeText(context, "加密备份已保存", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(context, it.message ?: "备份失败", Toast.LENGTH_SHORT).show()
                    }
                busyMessage = null
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri.toString()
            backupAction = BackupAction.IMPORT
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            XikeNavigationBar(
                selected = screen,
                onSelected = { screen = it },
            )
        },
    ) { innerPadding ->
        when (screen) {
            AppScreen.HOME -> MomentScreen(innerPadding, entries, onSave)
            AppScreen.INSIGHTS -> WeeklyInsightsScreen(innerPadding, entries)
            AppScreen.ARCHIVE -> JournalArchiveScreen(innerPadding, entries, openImage)
            AppScreen.SETTINGS -> ProfileSettingsScreen(
                padding = innerPadding,
                selectedTheme = selectedTheme,
                onThemeChange = onThemeChange,
                onExport = { backupAction = BackupAction.EXPORT },
                onImport = { importLauncher.launch(arrayOf("application/octet-stream", "application/json")) },
            )
        }
    }

    if (backupAction == BackupAction.EXPORT) {
        BackupPasswordDialog(
            title = "创建加密备份",
            confirm = "选择保存位置",
            description = "备份会流式加密，可安全保存到本地或系统接入的云盘。密码只属于你，我们不会保存。",
            onDismiss = { backupAction = null },
            onConfirm = { password ->
                pendingExportPassword = password
                backupAction = null
                val date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())
                exportLauncher.launch("xike-backup-$date.xike")
            },
        )
    }

    if (backupAction == BackupAction.IMPORT) {
        BackupPasswordDialog(
            title = "恢复加密备份",
            confirm = "验证并恢复",
            description = "应用会先完整验证备份，确认无误后才替换此设备上的内容。",
            onDismiss = {
                backupAction = null
                pendingImportUri = null
            },
            onConfirm = { password ->
                val uri = pendingImportUri?.let(Uri::parse)
                pendingImportUri = null
                backupAction = null
                if (uri != null) {
                    scope.launch {
                        busyMessage = "正在验证并恢复备份…"
                        onRestoreBackup(uri, password)
                            .onSuccess { count ->
                                Toast.makeText(context, "已恢复 $count 条日记", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, it.message ?: "恢复失败", Toast.LENGTH_LONG).show()
                            }
                        busyMessage = null
                    }
                }
            },
        )
    }

    busyMessage?.let { message -> ProcessingDialog(message) }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    description: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("备份密码（至少 8 位）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.length >= 8,
                onClick = { onConfirm(password) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ProcessingDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        shape = XikeShapes.dialog,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DataErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text("数据需要检查") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("我知道了") }
        },
    )
}
