package com.xike.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeEyebrowStyle
import com.xike.app.ui.theme.XikePageTitleStyle
import com.xike.app.ui.theme.XikeShapes

@Composable
internal fun ScreenHeader(eyebrow: String? = null, title: String, supporting: String? = null) {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 5.dp)) {
        if (eyebrow != null) {
            Text(eyebrow, style = XikeEyebrowStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(9.dp))
        }
        Text(title, style = XikePageTitleStyle)
        if (supporting != null) {
            Spacer(Modifier.height(7.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PaperCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(XikeCardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}
