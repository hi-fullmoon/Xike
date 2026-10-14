package com.xike.app.feature.insights

import com.xike.app.localizedText

import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeDialogContentGap


import android.content.Intent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun LocalReviewDialog(review: LocalJournalReview, scrollState: ScrollState, onOpenSource: (ReviewSource) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(localizedText("本地回顾")) },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState).testTag("local-review-scroll"), verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap)) {
                Text(review.title, style = MaterialTheme.typography.titleSmall)
                Text(review.dateRange, style = MaterialTheme.typography.bodySmall)
                review.sections.forEach { section ->
                    Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(section.text, style = MaterialTheme.typography.bodyMedium)
                    section.sources.forEach { source ->
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { onOpenSource(source) },
                                ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(source.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(
                    localizedText("分享会把以上文字交给你下一步选择的应用。息刻不会自动上传。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, review.text)
                    context.startActivity(Intent.createChooser(shareIntent, localizedText("分享息刻回顾")))
                },
                shape = XikeShapes.button,
            ) {
                Text(localizedText("选择分享应用"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("关闭")) } },
    )
}
