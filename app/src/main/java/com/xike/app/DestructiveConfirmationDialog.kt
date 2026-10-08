package com.xike.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DestructiveConfirmationDialog(
    title: String,
    confirmText: String,
    dismissText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isProcessing: Boolean = false,
    text: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        shape = XikeShapes.dialog,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        icon = {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        title = { Text(title) },
        text = {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                text()
            }
        },
        dismissButton = {
            TextButton(enabled = !isProcessing, onClick = onDismiss) { Text(dismissText) }
        },
        confirmButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Text(confirmText)
            }
        },
    )
}
