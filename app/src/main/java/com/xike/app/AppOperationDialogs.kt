package com.xike.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ProcessingDialog(message: String) {
    BasicAlertDialog(
        onDismissRequest = {},
    ) {
        Surface(
            shape = XikeShapes.dialog,
            color = AlertDialogDefaults.containerColor,
            contentColor = AlertDialogDefaults.textContentColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Row(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun DataErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(localizedText("数据需要检查")) },
        text = { Text(message, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("我知道了")) }
        },
    )
}
