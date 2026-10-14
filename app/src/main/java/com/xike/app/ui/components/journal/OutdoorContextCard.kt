package com.xike.app.ui.components.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.xike.app.AppLocale
import com.xike.app.data.model.OutdoorSnapshot
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.components.PaperCard
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.weatherConditionLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun OutdoorContextCard(
    snapshot: OutdoorSnapshot?,
    isBackdated: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    enabled: Boolean,
    onAdd: () -> Unit,
    onChooseCity: () -> Unit,
    onRemove: () -> Unit,
    showRefresh: Boolean = true,
    contentPadding: Dp = XikeCardPadding,
    minContentHeight: Dp = 0.dp,
    showLocationIconBackground: Boolean = false,
) {
    val isEmpty = snapshot == null && !isBackdated && !isLoading && errorMessage == null
    var showActions by remember(snapshot, enabled) { mutableStateOf(false) }
    var showFullAddress by
        remember(snapshot?.placeName, snapshot?.fullPlaceName) { mutableStateOf(false) }
    val cardModifier =
        if (isEmpty && enabled) {
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onAdd)
        } else {
            Modifier.fillMaxWidth()
        }

    PaperCard(
        modifier = cardModifier,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (snapshot != null) {
                Column(Modifier.fillMaxWidth().padding(contentPadding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = XikeShapes.inner,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().padding(end = 34.dp)) {
                                Text(
                                    snapshot.placeName,
                                    modifier =
                                        Modifier.align(Alignment.CenterStart).clickable(
                                            role = Role.Button,
                                            onClickLabel = tr("查看完整地址", "View full address"),
                                        ) {
                                            showFullAddress = true
                                        },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${snapshot.temperatureCelsius.roundToInt()}°",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    weatherConditionLabel(snapshot.weatherCode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        tr(
                            "获取于 ${snapshot.capturedAt.asOutdoorCapturedTime()} · ${snapshot.source}",
                            "Captured at ${snapshot.capturedAt.asOutdoorCapturedTime()} · ${snapshot.source}",
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isLoading && errorMessage != null) {
                        Text(
                            errorMessage,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(contentPadding)) {
                    Row(
                        modifier = Modifier.heightIn(min = minContentHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape =
                                if (showLocationIconBackground && isEmpty) CircleShape
                                else XikeShapes.inner,
                            color =
                                if (showLocationIconBackground && isEmpty) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when {
                                    isLoading ->
                                        CircularProgressIndicator(
                                            Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    isBackdated ->
                                        Icon(
                                            Icons.Outlined.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    errorMessage != null ->
                                        Icon(
                                            Icons.Outlined.LocationOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    else ->
                                        Icon(
                                            Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    isLoading -> localizedText("正在获取地点与天气")
                                    isBackdated -> localizedText("补记不使用今天的天气")
                                    errorMessage != null -> localizedText("暂时无法添加地点与天气")
                                    else -> tr("地点与天气", "Location and weather")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    isLoading -> localizedText("只获取一次，不会在后台持续定位")
                                    isBackdated -> localizedText("避免把现在的环境误记到过去")
                                    errorMessage != null -> localizedText(errorMessage)
                                    else -> localizedText("地点与天气 · 可选")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (errorMessage == null) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isEmpty) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    localizedText("添加"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (errorMessage != null) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            OutdoorCardAction(localizedText("手动选城市"), enabled, onChooseCity)
                            OutdoorCardAction(localizedText("重新尝试"), enabled && !isLoading, onAdd)
                        }
                    }
                }
            }
            if (snapshot != null) {
                Row(Modifier.align(Alignment.TopEnd)) {
                    Box {
                        IconButton(
                            onClick = { showActions = true },
                            enabled = enabled,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Surface(
                                Modifier.size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier =
                                                Modifier.size(16.dp).semantics {
                                                    contentDescription =
                                                        tr(
                                                            "正在获取地点与天气",
                                                            "Getting location and weather",
                                                        )
                                                },
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.MoreVert,
                                            contentDescription =
                                                tr(
                                                    "地点与天气的更多操作",
                                                    "More location and weather actions",
                                                ),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = showActions,
                            onDismissRequest = { showActions = false },
                        ) {
                            if (showRefresh)
                                DropdownMenuItem(
                                    text = {
                                        Text(tr("重新获取地点与天气", "Get location and weather again"))
                                    },
                                    enabled = enabled && !isLoading,
                                    onClick = {
                                        showActions = false
                                        if (enabled && !isLoading) onAdd()
                                    },
                                )
                            DropdownMenuItem(
                                text = { Text(tr("移除地点与天气", "Remove location and weather")) },
                                enabled = enabled,
                                onClick = {
                                    showActions = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showFullAddress && snapshot != null) {
        OutdoorAddressDialog(
            address = snapshot.fullPlaceName ?: snapshot.placeName,
            onDismiss = { showFullAddress = false },
        )
    }
}

@Composable
internal fun OutdoorAddressDialog(address: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        properties = DialogProperties(decorFitsSystemWindows = false),
        shape = XikeShapes.dialog,
        title = { Text(tr("完整地址", "Full address")) },
        text = {
            Text(
                address,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = XikeShapes.button,
            ) {
                Text(tr("关闭", "Close"))
            }
        },
    )
}

@Composable
internal fun OutdoorCardAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled, shape = XikeShapes.button) {
        Text(label)
    }
}

private fun Long.asOutdoorCapturedTime(): String =
    DateTimeFormatter.ofPattern("HH:mm", AppLocale.locale)
        .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
