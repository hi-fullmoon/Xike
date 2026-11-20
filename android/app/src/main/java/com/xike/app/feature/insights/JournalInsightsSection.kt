package com.xike.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeSectionTitleStyle
import com.xike.app.ui.theme.XikeShapes

@Composable
internal fun InsightSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    index: String,
    title: String,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(XikeContentCardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        index,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(title, style = XikeSectionTitleStyle)
                }
                if (trailing != null) {
                    Text(
                        trailing,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
internal fun RatioBar(ratio: Double, description: String) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = description }
    ) {
        Box(
            Modifier.fillMaxWidth(ratio.toFloat().coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
internal fun InsufficientDataNote(message: String) {
    Surface(
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(XikeInnerCardPadding),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
        )
    }
}
