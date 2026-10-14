package com.xike.app.feature.moment

import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.localizedText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties


@Composable
internal fun OutdoorCityDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var city by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        properties = DialogProperties(decorFitsSystemWindows = false),
        shape = XikeShapes.dialog,
        title = { Text(localizedText("选择城市")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap),
            ) {
                Text(
                    localizedText("不用提供位置权限。城市名称会发送给 Open-Meteo，用于查找城市和天气。"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextField(
                    value = city,
                    onValueChange = { city = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(localizedText("例如：上海、杭州")) },
                    shape = XikeShapes.inner,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("取消")) } },
        confirmButton = {
            TextButton(
                shape = XikeShapes.button,
                onClick = { onConfirm(city.trim()) },
                enabled = city.isNotBlank(),
            ) { Text(localizedText("获取天气")) }
        },
    )
}
