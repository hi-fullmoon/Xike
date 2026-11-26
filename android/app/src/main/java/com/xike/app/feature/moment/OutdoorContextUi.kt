package com.xike.app.feature.moment

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xike.app.AppLocale
import com.xike.app.data.model.OutdoorSnapshot
import com.xike.app.feature.settings.AmapSettingsDialog
import com.xike.app.localizedText
import com.xike.app.outdoor.OutdoorCityCandidate
import com.xike.app.outdoor.TripleTapGate
import com.xike.app.outdoor.locationSourceName
import com.xike.app.tr
import com.xike.app.ui.components.OutdoorWeatherIcon
import com.xike.app.ui.components.XikeIcons
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeDialogCompactGap
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeSheetBottomPadding
import com.xike.app.ui.theme.XikeSheetHorizontalPadding
import com.xike.app.ui.theme.xikeButtonElevation
import com.xike.app.weatherConditionLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun suggestedCityQuery(placeName: String?): String {
    val normalized = placeName?.trim().orEmpty()
    if (
        normalized.isBlank() ||
            normalized == "当前位置" ||
            normalized.equals("Current location", ignoreCase = true)
    ) {
        return ""
    }
    return normalized.split('·').firstOrNull { it.trim().isNotBlank() }?.trim()?.take(40).orEmpty()
}

@Composable
internal fun OutdoorContextBar(
    snapshot: OutdoorSnapshot?,
    isBackdated: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    enabled: Boolean,
    onOpenSheet: () -> Unit,
    onRetryLocate: () -> Unit,
    onRetryWeather: () -> Unit,
    onCancelLoading: () -> Unit,
    onRemove: () -> Unit,
) {
    when {
        isLoading -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(18.dp).semantics {
                                contentDescription = tr("正在获取地点与天气", "Getting location and weather")
                            },
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text =
                            if (snapshot != null && !snapshot.hasWeather) {
                                "${snapshot.placeName} · ${tr("正在获取天气…", "Getting weather…")}"
                            } else {
                                tr("正在获取位置与天气…", "Getting location and weather…")
                            },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = onCancelLoading,
                        shape = XikeShapes.button,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(localizedText("取消"))
                    }
                }
            }
        }
        snapshot != null -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier =
                                Modifier.weight(1f)
                                    .clickable(
                                        enabled = enabled,
                                        role = Role.Button,
                                        onClickLabel =
                                            tr("查看或更换地点与天气", "View or edit location and weather"),
                                        onClick = onOpenSheet,
                                    )
                                    .padding(
                                        start = 12.dp,
                                        top = 10.dp,
                                        bottom = 10.dp,
                                        end = 4.dp,
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color =
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val weatherCode = snapshot.weatherCode
                                    if (snapshot.hasWeather && weatherCode != null) {
                                        OutdoorWeatherIcon(
                                            weatherCode = weatherCode,
                                            size = 20.dp,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    snapshot.placeName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val temperature = snapshot.temperatureCelsius
                                val weatherCode = snapshot.weatherCode
                                if (
                                    snapshot.hasWeather &&
                                        temperature != null &&
                                        weatherCode != null
                                ) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        itemVerticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${temperature.roundToInt()}°",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            weatherConditionLabel(weatherCode),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    Text(
                                        if (errorMessage != null && !isBackdated) {
                                            tr("天气暂未获取到", "Weather unavailable")
                                        } else {
                                            tr("仅记录地点", "Place only")
                                        },
                                        modifier = Modifier.padding(top = 2.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onRemove,
                            enabled = enabled,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = tr("移除地点与天气", "Remove location and weather"),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (errorMessage != null) {
                        FlowRow(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(start = 14.dp, end = 10.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (!snapshot.hasWeather && !isBackdated) {
                                TextButton(
                                    onClick = onRetryWeather,
                                    enabled = enabled,
                                    shape = XikeShapes.button,
                                    contentPadding =
                                        PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(tr("重试天气", "Retry weather"))
                                }
                            }
                        }
                    }
                }
            }
        }
        errorMessage != null -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
                border =
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            errorMessage,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        TextButton(
                            onClick = onOpenSheet,
                            enabled = enabled,
                            shape = XikeShapes.button,
                        ) {
                            Text(tr("搜索城市或写地点", "Search or enter place"))
                        }
                        if (!isBackdated) {
                            TextButton(
                                onClick = onRetryLocate,
                                enabled = enabled,
                                shape = XikeShapes.button,
                            ) {
                                Text(localizedText("重新尝试"))
                            }
                        }
                    }
                }
            }
        }
        else -> {
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onOpenSheet,
                        ),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isBackdated) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                OutdoorWeatherIcon(
                                    weatherCode = 1,
                                    size = 16.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Text(
                        text =
                            if (isBackdated) {
                                tr("添加当时地点", "Add place for this moment")
                            } else {
                                tr("添加地点与天气", "Add location and weather")
                            },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        XikeIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutdoorContextSheet(
    snapshot: OutdoorSnapshot?,
    isBackdated: Boolean,
    isLoading: Boolean,
    permissionDenied: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onSearchCities: suspend (String) -> Result<List<OutdoorCityCandidate>>,
    onSelectCityCandidate: (OutdoorCityCandidate) -> Unit,
    onSaveManualPlace: (String) -> Unit,
    onRetryWeather: () -> Unit,
    onRemove: () -> Unit,
) {
    val initialQuery =
        if (snapshot != null && !snapshot.hasWeather && !isBackdated) {
            suggestedCityQuery(snapshot.placeName)
        } else {
            ""
        }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var candidates by remember { mutableStateOf<List<OutdoorCityCandidate>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val taps = remember { TripleTapGate() }
    var showAmapConfig by rememberSaveable { mutableStateOf(false) }

    val triggerSearch: () -> Unit = {
        val trimmed = query.trim()
        if (trimmed.isNotBlank() && !isBackdated && enabled) {
            searchJob?.cancel()
            isSearching = true
            searchError = null
            hasSearched = true
            searchJob = scope.launch {
                try {
                    onSearchCities(trimmed)
                        .onSuccess { results ->
                            candidates = results
                            if (results.isEmpty()) {
                                searchError =
                                    tr(
                                        "没有找到匹配的城市，也可以直接记录这个地点。",
                                        "No matching city found. You can still save this place directly.",
                                    )
                            }
                        }
                        .onFailure { error ->
                            candidates = emptyList()
                            searchError =
                                error.message
                                    ?: tr(
                                        "暂时无法搜索城市，请检查网络或直接记录地点。",
                                        "Unable to search cities. Check your connection or save the place directly.",
                                    )
                        }
                } finally {
                    isSearching = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            searchJob?.cancel()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = XikeContentMaxWidth,
        shape = XikeShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = XikeSheetHorizontalPadding)
                    .padding(bottom = XikeSheetBottomPadding),
            verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (isBackdated) {
                            tr("当时地点", "Place for this moment")
                        } else {
                            localizedText("此刻窗外")
                        },
                    style = MaterialTheme.typography.titleLarge,
                    modifier =
                        Modifier.weight(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (taps.tap(SystemClock.elapsedRealtime())) showAmapConfig = true
                        },
                )
                IconButton(
                    onClick = {
                        searchJob?.cancel()
                        onDismiss()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = tr("关闭", "Close"),
                    )
                }
            }

            if (snapshot != null) {
                OutdoorSnapshotDetailsCard(
                    snapshot = snapshot,
                    isBackdated = isBackdated,
                    enabled = enabled && !isLoading,
                    onRefresh = {
                        searchJob?.cancel()
                        onDismiss()
                        onRequestCurrentLocation()
                    },
                    onRetryWeather = {
                        searchJob?.cancel()
                        onDismiss()
                        onRetryWeather()
                    },
                    onRemove = {
                        searchJob?.cancel()
                        onRemove()
                        onDismiss()
                    },
                )
            }

            if (isBackdated) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = XikeShapes.inner,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            tr(
                                "补记过去日期不获取今日实时天气，但可以记下当时的地点。",
                                "Backdated entries skip today's live weather, while you can still record the place.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (snapshot == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = XikeShapes.inner,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                searchJob?.cancel()
                                onDismiss()
                                onRequestCurrentLocation()
                            },
                            enabled = enabled && !isLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = XikeShapes.button,
                            elevation = xikeButtonElevation(),
                        ) {
                            Icon(
                                Icons.Outlined.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tr("使用当前位置获取", "Use current location"))
                        }
                        Text(
                            text =
                                if (permissionDenied) {
                                    tr(
                                        "未开启位置权限，可以在下方搜索城市或直接写下地点。",
                                        "Location permission is off. You can search a city or enter a place below.",
                                    )
                                } else {
                                    tr(
                                        "仅在点击后获取一次前台位置，支持粗略定位，坐标不会保存；粗略经纬度发往 Open-Meteo 查询天气。",
                                        "Requests your location once when tapped. Approximate location is supported and coordinates are never saved; approximate coordinates are sent to Open-Meteo for weather.",
                                    )
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap),
            ) {
                Text(
                    text =
                        if (isBackdated) {
                            tr("输入当时所在地点", "Enter the place")
                        } else {
                            tr("搜索城市或自定义地点", "Search city or enter place")
                        },
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { updated ->
                        query = updated.take(40)
                        if (updated.isBlank()) {
                            searchJob?.cancel()
                            isSearching = false
                            candidates = emptyList()
                            searchError = null
                            hasSearched = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = XikeShapes.inner,
                    placeholder = {
                        Text(
                            if (isBackdated) {
                                tr("例如：京都鸭川、家里书房", "e.g. Kyoto, Home study")
                            } else {
                                tr("例如：杭州、朝阳，或家里阳台", "e.g. Hangzhou, Home balcony")
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isBackdated) Icons.Outlined.LocationOn else Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (!isBackdated && query.isNotBlank()) {
                            TextButton(
                                onClick = triggerSearch,
                                enabled = enabled && !isSearching,
                                shape = XikeShapes.button,
                            ) {
                                Text(tr("搜索", "Search"))
                            }
                        } else if (query.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    query = ""
                                    candidates = emptyList()
                                    searchError = null
                                    hasSearched = false
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = tr("清空输入", "Clear input"),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = if (isBackdated) ImeAction.Done else ImeAction.Search
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onSearch = { triggerSearch() },
                            onDone = {
                                val trimmed = query.trim()
                                if (trimmed.isNotBlank() && enabled) {
                                    searchJob?.cancel()
                                    onSaveManualPlace(trimmed)
                                    onDismiss()
                                }
                            },
                        ),
                )

                if (!isBackdated) {
                    Text(
                        tr(
                            "点「搜索」查找城市并获取天气（城市名发送给 Open-Meteo）；也可直接离线记录地点。",
                            "Tap Search to find a city and fetch weather via Open-Meteo, or save the place offline below.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            tr("正在查找城市…", "Searching cities…"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!isSearching && searchError != null && hasSearched) {
                    Text(
                        searchError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!isBackdated && candidates.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        candidates.forEach { candidate ->
                            Surface(
                                modifier =
                                    Modifier.fillMaxWidth().clickable(
                                        enabled = enabled && !isLoading,
                                        role = Role.Button,
                                    ) {
                                        searchJob?.cancel()
                                        onDismiss()
                                        onSelectCityCandidate(candidate)
                                    },
                                shape = XikeShapes.inner,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            candidate.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (candidate.subtitle.isNotBlank()) {
                                            Text(
                                                candidate.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Text(
                                        tr("获取天气", "Weather"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                val trimmedQuery = query.trim()
                if (trimmedQuery.isNotBlank()) {
                    Surface(
                        modifier =
                            Modifier.fillMaxWidth().clickable(
                                enabled = enabled && !isLoading,
                                role = Role.Button,
                            ) {
                                searchJob?.cancel()
                                onSaveManualPlace(trimmedQuery)
                                onDismiss()
                            },
                        shape = XikeShapes.inner,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tr(
                                        "仅记录「$trimmedQuery」",
                                        "Save \"$trimmedQuery\" only",
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    tr("离线可用，不请求天气服务", "Works offline without weather"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                tr("使用", "Use"),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAmapConfig) {
        AmapSettingsDialog(onDismiss = { showAmapConfig = false })
    }
}

@Composable
private fun OutdoorSnapshotDetailsCard(
    snapshot: OutdoorSnapshot,
    isBackdated: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onRetryWeather: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val weatherCode = snapshot.weatherCode
                        if (snapshot.hasWeather && weatherCode != null) {
                            OutdoorWeatherIcon(
                                weatherCode = weatherCode,
                                size = 22.dp,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        snapshot.placeName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val fullAddress = snapshot.fullPlaceName
                    if (!fullAddress.isNullOrBlank() && fullAddress != snapshot.placeName) {
                        Text(
                            fullAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val temperature = snapshot.temperatureCelsius
                    val weatherCode = snapshot.weatherCode
                    if (snapshot.hasWeather && temperature != null && weatherCode != null) {
                        FlowRow(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${temperature.roundToInt()}°",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                weatherConditionLabel(weatherCode),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (
                snapshot.locationSource != null || (snapshot.hasWeather && snapshot.source != null)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    snapshot.locationSource?.let { source ->
                        val name = locationSourceName(source, AppLocale.locale)
                        Text(
                            tr("地点来源：$name", "Location source: $name"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (snapshot.hasWeather && snapshot.source != null) {
                        Text(
                            tr(
                                "天气来源：${snapshot.source} · 获取于 ${snapshot.capturedAt.asCapturedTime()}",
                                "Weather source: ${snapshot.source} · Fetched at ${snapshot.capturedAt.asCapturedTime()}",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!snapshot.hasWeather && !isBackdated) {
                    TextButton(
                        onClick = onRetryWeather,
                        enabled = enabled,
                        shape = XikeShapes.button,
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(tr("重试天气", "Retry weather"))
                    }
                }
                if (!isBackdated) {
                    TextButton(
                        onClick = onRefresh,
                        enabled = enabled,
                        shape = XikeShapes.button,
                    ) {
                        Icon(
                            Icons.Outlined.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(tr("刷新当前位置", "Refresh location"))
                    }
                }
                TextButton(
                    onClick = onRemove,
                    enabled = enabled,
                    shape = XikeShapes.button,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text(tr("移除", "Remove"))
                }
            }
        }
    }
}

private fun Long.asCapturedTime(): String =
    DateTimeFormatter.ofPattern("HH:mm", AppLocale.locale)
        .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
