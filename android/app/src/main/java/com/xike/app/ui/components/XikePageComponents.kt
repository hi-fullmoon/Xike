package com.xike.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeEyebrowStyle
import com.xike.app.ui.theme.XikePageTitleStyle
import com.xike.app.ui.theme.XikeShapes

@Composable
internal fun ScreenHeader(
    eyebrow: String? = null,
    title: String,
    supporting: String? = null,
    titleModifier: Modifier = Modifier,
) {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 5.dp)) {
        if (eyebrow != null) {
            Text(eyebrow, style = XikeEyebrowStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(9.dp))
        }
        Text(title, style = XikePageTitleStyle, modifier = titleModifier)
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
internal fun SecondaryPageHeader(
    title: String,
    closeDescription: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeEnabled: Boolean = true,
    subtitle: String? = null,
    actionsBelowTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val heading: @Composable () -> Unit = {
        Text(
            title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val buttons: @Composable RowScope.() -> Unit = {
        actions()
        IconButton(onClick = onClose, enabled = closeEnabled) {
            Icon(Icons.Outlined.Close, contentDescription = closeDescription)
        }
    }
    if (actionsBelowTitle) {
        Column(modifier.fillMaxWidth()) {
            heading()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = buttons,
            )
        }
    } else {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { heading() }
            buttons()
        }
    }
}

@Composable
internal fun PaperCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(XikeContentCardPadding),
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
