package com.xike.app.feature.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xike.app.AppLanguage
import com.xike.app.AppLocale
import com.xike.app.BuildConfig
import com.xike.app.DailyPromptSettings
import com.xike.app.DailyPromptStyle
import com.xike.app.LocalLanguageChange
import com.xike.app.R
import com.xike.app.ReminderSettings
import com.xike.app.displayName
import com.xike.app.feature.lock.AppLockTimeout
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.components.ScreenHeader
import com.xike.app.ui.theme.AppStyle
import com.xike.app.ui.theme.AppTheme
import com.xike.app.ui.theme.LocalRecordedAtPickerContext
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeDialogContentGap
import com.xike.app.ui.theme.XikeDialogOptionPadding
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikeSectionTitleStyle
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.cardCorner
import com.xike.app.ui.theme.showWithXikeShape
import com.xike.app.ui.theme.xikeSwitchColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val XikeSettingsRowPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)

@Composable
fun ProfileSettingsScreen(
    padding: PaddingValues,
    selectedTheme: AppTheme,
    selectedStyle: AppStyle,
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    authenticationAvailable: Boolean,
    reminderSettings: ReminderSettings,
    dailyPromptSettings: DailyPromptSettings,
    notificationPermissionGranted: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onStyleChange: (AppStyle) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onAppLockTimeoutChange: (AppLockTimeout) -> Unit,
    onLockNow: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderSettingsChange: (ReminderSettings) -> Unit,
    onDailyPromptSettingsChange: (DailyPromptSettings) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    canUndoRestore: Boolean,
    onUndoRestore: () -> Unit,
) {
    var showTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showReminderDialog by rememberSaveable { mutableStateOf(false) }
    var showPromptStyleDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showAutoBackupDialog by rememberSaveable { mutableStateOf(false) }
    val onLanguageChange = LocalLanguageChange.current

    ScreenColumn(padding) {
        ScreenHeader(
            eyebrow = tr("只属于你的空间", "A SPACE OF YOUR OWN"),
            title = localizedText("设置"),
            supporting = localizedText("让记录保持你的节奏。"),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = XikeShapes.card,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ) {
            Row(Modifier.padding(XikeCardPadding), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(localizedText("核心记录离线且加密"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        localizedText("无需账号；窗外天气仅在主动添加时联网"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(index = tr("偏好", "Preferences"), title = localizedText("语言"))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                SettingsAction(
                    icon = Icons.Outlined.Language,
                    title = localizedText("应用语言"),
                    subtitle = AppLocale.selectedLanguage.displayName,
                    onClick = { showLanguageDialog = true },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(index = localizedText("隐私"), title = localizedText("应用锁"))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    AppLockToggleRow(
                        enabled = appLockEnabled,
                        authenticationAvailable = authenticationAvailable,
                        onEnabledChange = onAppLockChange,
                    )
                    if (appLockEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SettingsAction(
                            icon = Icons.Outlined.Timer,
                            title = localizedText("自动锁定"),
                            subtitle = appLockTimeout.label,
                            onClick = { showTimeoutDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SettingsAction(
                            icon = Icons.Outlined.Lock,
                            title = localizedText("立即锁定"),
                            subtitle = localizedText("隐藏当前内容并返回锁定页"),
                            onClick = onLockNow,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(index = localizedText("节奏"), title = localizedText("温和陪伴"))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Outlined.NotificationsNone,
                        title = localizedText("记录提醒"),
                        subtitle =
                            when {
                                reminderSettings.enabled && !notificationPermissionGranted ->
                                    localizedText("系统通知已关闭；记录功能不受影响")
                                reminderSettings.enabled ->
                                    reminderSettings.pauseLabel() ?: reminderSettings.summary()
                                else -> localizedText("默认关闭，仅在选定的本地时间提醒")
                            },
                        enabled = reminderSettings.enabled,
                        onEnabledChange = onReminderEnabledChange,
                    )
                    if (reminderSettings.enabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SettingsAction(
                            icon = Icons.Outlined.Schedule,
                            title = localizedText("提醒时间"),
                            subtitle = reminderSettings.summary(),
                            onClick = { showReminderDialog = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        val pauseLabel = reminderSettings.pauseLabel()
                        SettingsAction(
                            icon = Icons.Outlined.PauseCircle,
                            title =
                                if (pauseLabel == null) localizedText("暂停一周")
                                else localizedText("继续提醒"),
                            subtitle = pauseLabel ?: localizedText("临时安静下来，不改变原来的时间"),
                            onClick = {
                                onReminderSettingsChange(
                                    reminderSettings.copy(
                                        pausedUntilEpochDay =
                                            if (pauseLabel == null) {
                                                LocalDate.now().plusDays(7).toEpochDay()
                                            } else {
                                                null
                                            }
                                    )
                                )
                            },
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 70.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    SettingsToggleRow(
                        icon = Icons.Outlined.Quiz,
                        title = localizedText("每日一问"),
                        subtitle =
                            if (dailyPromptSettings.enabled) {
                                dailyPromptSettings.style.label
                            } else {
                                localizedText("默认关闭，问题全部来自本地题库")
                            },
                        enabled = dailyPromptSettings.enabled,
                        onEnabledChange = { enabled ->
                            onDailyPromptSettingsChange(dailyPromptSettings.copy(enabled = enabled))
                        },
                    )
                    if (dailyPromptSettings.enabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SettingsAction(
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            title = localizedText("本地问题库"),
                            subtitle = dailyPromptSettings.style.description,
                            onClick = { showPromptStyleDialog = true },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(
                index = localizedText("外观"),
                title = localizedText("界面风格"),
                trailing = localizedText("布局与质感"),
            )
            AppStyle.entries.forEach { style ->
                StyleTile(
                    style = style,
                    selected = selectedStyle == style,
                    onClick = { onStyleChange(style) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                index = localizedText("主色"),
                title = localizedText("强调色"),
                trailing = localizedText("可自由搭配"),
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val titleStyle = MaterialTheme.typography.titleSmall
                val subtitleStyle = MaterialTheme.typography.labelSmall
                val textWidth =
                    AppTheme.entries.maxOf { theme ->
                        maxOf(
                            textMeasurer
                                .measure(theme.title, titleStyle, softWrap = false)
                                .size
                                .width,
                            textMeasurer
                                .measure(theme.subtitle, subtitleStyle, softWrap = false)
                                .size
                                .width,
                        )
                    }
                val minTileWidth =
                    maxOf(80.dp, with(density) { textWidth.toDp() } + XikeInnerCardPadding * 2)
                val columns = ((maxWidth + 8.dp) / (minTileWidth + 8.dp)).toInt().coerceIn(1, 3)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTheme.entries.chunked(columns).forEach { themes ->
                        Row(
                            Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            themes.forEach { theme ->
                                ThemeTile(
                                    theme = theme,
                                    selected = selectedTheme == theme,
                                    onClick = { onThemeChange(theme) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                            repeat(columns - themes.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(index = localizedText("数据"), title = localizedText("备份与迁移"))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    SettingsAction(
                        icon = Icons.Outlined.FileUpload,
                        title = localizedText("导出日记备份"),
                        subtitle = localizedText("可选密码加密，附离线阅读页"),
                        onClick = onExport,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 70.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    SettingsAction(
                        icon = Icons.Outlined.Restore,
                        title = localizedText("从备份恢复"),
                        subtitle = localizedText("支持加密和未加密备份"),
                        onClick = onImport,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 70.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    SettingsAction(
                        icon = Icons.Outlined.Schedule,
                        title = tr("自动备份", "Automatic backup"),
                        subtitle =
                            tr(
                                "自定义时间、目录与保留数量，密码加密",
                                "Custom schedule, folder and retention; password encrypted",
                            ),
                        onClick = { showAutoBackupDialog = true },
                    )
                    if (canUndoRestore) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SettingsAction(
                            icon = Icons.AutoMirrored.Outlined.Undo,
                            title = localizedText("撤销上次恢复"),
                            subtitle =
                                tr(
                                    "仅在恢复后未改动日记时可撤销一次",
                                    "Undo once, before making any changes to entries",
                                ),
                            onClick = onUndoRestore,
                        )
                    }
                }
            }
        }

        Text(
            tr("息刻 · ${BuildConfig.VERSION_NAME}", "Xike · ${BuildConfig.VERSION_NAME}"),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showAutoBackupDialog) {
        AutoBackupDialog(onDismiss = { showAutoBackupDialog = false })
    }

    if (showLanguageDialog) {
        AppLanguageDialog(
            selected = AppLocale.selectedLanguage,
            onSelected = {
                onLanguageChange(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showTimeoutDialog) {
        AppLockTimeoutDialog(
            selected = appLockTimeout,
            onSelected = {
                onAppLockTimeoutChange(it)
                showTimeoutDialog = false
            },
            onDismiss = { showTimeoutDialog = false },
        )
    }

    if (showReminderDialog) {
        ReminderScheduleDialog(
            settings = reminderSettings,
            onConfirm = {
                onReminderSettingsChange(it)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false },
        )
    }

    if (showPromptStyleDialog) {
        DailyPromptStyleDialog(
            selected = dailyPromptSettings.style,
            onSelected = {
                onDailyPromptSettingsChange(dailyPromptSettings.copy(style = it))
                showPromptStyleDialog = false
            },
            onDismiss = { showPromptStyleDialog = false },
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                .padding(XikeSettingsRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = null, colors = xikeSwitchColors())
    }
}

@Composable
internal fun AppLanguageDialog(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(localizedText("应用语言")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = selected == language,
                                    role = Role.RadioButton,
                                    onClick = { onSelected(language) },
                                )
                                .padding(vertical = XikeDialogOptionPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == language, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(language.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("取消")) }
        },
    )
}

@Composable
internal fun ReminderScheduleDialog(
    settings: ReminderSettings,
    onConfirm: (ReminderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalRecordedAtPickerContext.current
    var selectedHour by rememberSaveable(settings.hour) { mutableStateOf(settings.hour) }
    var selectedMinute by rememberSaveable(settings.minute) { mutableStateOf(settings.minute) }
    var selectedDays by
        rememberSaveable(
            settings.weekdays,
            stateSaver =
                androidx.compose.runtime.saveable.listSaver<Set<DayOfWeek>, String>(
                    save = { days -> days.map(DayOfWeek::name) },
                    restore = { names -> names.map(DayOfWeek::valueOf).toSet() },
                ),
        ) {
            mutableStateOf(settings.weekdays)
        }
    var quietHoursEnabled by
        rememberSaveable(settings.quietHoursEnabled) {
            mutableStateOf(settings.quietHoursEnabled)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(localizedText("提醒时间")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(XikeDialogContentGap),
            ) {
                Surface(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            TimePickerDialog(
                                    context,
                                    R.style.PickerSurface,
                                    { _, hour, minute ->
                                        selectedHour = hour
                                        selectedMinute = minute
                                    },
                                    selectedHour,
                                    selectedMinute,
                                    true,
                                )
                                .showWithXikeShape()
                        },
                    shape = XikeShapes.inner,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ) {
                    Row(
                        Modifier.padding(XikeInnerCardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "%02d:%02d".format(Locale.ROOT, selectedHour, selectedMinute),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            localizedText("修改"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    localizedText("为减少耗电，系统可能稍后送达，但不会早于所选时间。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("提醒星期"), style = MaterialTheme.typography.titleSmall)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val textMeasurer = rememberTextMeasurer()
                        val density = LocalDensity.current
                        val labelStyle = MaterialTheme.typography.labelLarge
                        val labelWidth =
                            DayOfWeek.entries.maxOf { day ->
                                textMeasurer
                                    .measure(
                                        day.getDisplayName(DateTextStyle.SHORT, AppLocale.locale),
                                        style = labelStyle,
                                        softWrap = false,
                                    )
                                    .size
                                    .width
                            }
                        val minimumWidth = maxOf(48.dp, with(density) { labelWidth.toDp() } + 8.dp)
                        val columns =
                            ((maxWidth + 7.dp) / (minimumWidth + 7.dp)).toInt().coerceIn(1, 4)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            DayOfWeek.entries.chunked(columns).forEach { days ->
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    days.forEach { day ->
                                        val selected = day in selectedDays
                                        Surface(
                                            modifier =
                                                Modifier.weight(1f)
                                                    .toggleable(
                                                        value = selected,
                                                        role = Role.Checkbox,
                                                        onValueChange = {
                                                            selectedDays =
                                                                if (selected) selectedDays - day
                                                                else selectedDays + day
                                                        },
                                                    )
                                                    .semantics {
                                                        contentDescription =
                                                            day.getDisplayName(
                                                                DateTextStyle.FULL,
                                                                AppLocale.locale,
                                                            )
                                                    },
                                            shape = XikeShapes.button,
                                            color =
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier.fillMaxWidth()
                                                        .heightIn(min = 48.dp)
                                                        .padding(
                                                            horizontal = 4.dp,
                                                            vertical = 8.dp,
                                                        ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    day.getDisplayName(
                                                        DateTextStyle.SHORT,
                                                        AppLocale.locale,
                                                    ),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textAlign =
                                                        androidx.compose.ui.text.style.TextAlign
                                                            .Center,
                                                    style = labelStyle,
                                                    maxLines = 1,
                                                    color =
                                                        if (selected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme
                                                                .onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    repeat(columns - days.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (selectedDays.isEmpty()) {
                        Text(
                            localizedText("至少选择一天"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .toggleable(
                                value = quietHoursEnabled,
                                role = Role.Switch,
                                onValueChange = { quietHoursEnabled = it },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(localizedText("夜间勿扰"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            localizedText("%02d:00–%02d:00 内的提醒会延后")
                                .format(
                                    Locale.ROOT,
                                    settings.quietHoursStart,
                                    settings.quietHoursEnd,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = quietHoursEnabled,
                        onCheckedChange = null,
                        colors = xikeSwitchColors(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        settings.copy(
                            hour = selectedHour,
                            minute = selectedMinute,
                            weekdays = selectedDays,
                            quietHoursEnabled = quietHoursEnabled,
                        )
                    )
                },
                enabled = selectedDays.isNotEmpty(),
                shape = XikeShapes.button,
            ) {
                Text(localizedText("保存"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("取消")) }
        },
    )
}

@Composable
internal fun DailyPromptStyleDialog(
    selected: DailyPromptStyle,
    onSelected: (DailyPromptStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(localizedText("本地问题库")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                DailyPromptStyle.entries.forEach { style ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = style == selected,
                                    role = Role.RadioButton,
                                    onClick = { onSelected(style) },
                                )
                                .padding(vertical = XikeDialogOptionPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = style == selected, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(style.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                style.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("取消")) }
        },
    )
}

@Composable
private fun AppLockToggleRow(
    enabled: Boolean,
    authenticationAvailable: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                .padding(XikeSettingsRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.EnhancedEncryption,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(localizedText("应用锁"), style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    enabled && authenticationAvailable -> localizedText("离开后使用系统身份验证解锁")
                    enabled -> localizedText("设备验证不可用，请恢复系统屏幕锁")
                    authenticationAvailable -> localizedText("使用面容、指纹或设备密码保护")
                    else -> localizedText("开启时需要先设置设备屏幕锁")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = null, colors = xikeSwitchColors())
    }
}

@Composable
internal fun AppLockTimeoutDialog(
    selected: AppLockTimeout,
    onSelected: (AppLockTimeout) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text(localizedText("自动锁定")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                AppLockTimeout.entries.forEach { timeout ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = selected == timeout,
                                    role = Role.RadioButton,
                                    onClick = { onSelected(timeout) },
                                )
                                .padding(vertical = XikeDialogOptionPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == timeout, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(timeout.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, shape = XikeShapes.button) { Text(localizedText("取消")) }
        },
    )
}

@Composable
private fun ThemeTile(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier.selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(Modifier.padding(XikeInnerCardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(theme.primary))
                Spacer(Modifier.weight(1f))
                if (selected)
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
            }
            Spacer(Modifier.height(8.dp))
            Text(theme.title, style = MaterialTheme.typography.titleSmall)
            Text(
                theme.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StyleTile(style: AppStyle, selected: Boolean, onClick: () -> Unit) {
    val cardCorner = style.cardCorner()
    // 预览缩略图用的迷你圆角，约为实际 innerCorner 的七成，不与真实组件形状一一对应。
    val innerCorner =
        when (style) {
            AppStyle.BREATHE -> 14.dp
            AppStyle.PAPER -> 2.dp
            AppStyle.FOCUS -> 7.dp
        }
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(cardCorner),
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(XikeCardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StylePreview(style = style, corner = innerCorner)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        style.title,
                        modifier = Modifier.weight(1f),
                        style =
                            when (style) {
                                AppStyle.PAPER ->
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Serif
                                    )
                                AppStyle.FOCUS ->
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                AppStyle.BREATHE -> MaterialTheme.typography.titleMedium
                            },
                    )
                    if (selected) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    style.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StylePreview(style: AppStyle, corner: Dp) {
    Surface(
        modifier = Modifier.size(width = 78.dp, height = 62.dp),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        when (style) {
            AppStyle.BREATHE ->
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth(0.62f)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Box(
                        Modifier.fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    )
                }
            AppStyle.PAPER ->
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth(0.56f)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    repeat(3) {
                        Box(
                            Modifier.fillMaxWidth(if (it == 2) 0.7f else 1f)
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary)
                }
            AppStyle.FOCUS ->
                Column(
                    modifier = Modifier.padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(3) { index ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (index == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.padding(start = 5.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == 0) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(XikeSettingsRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScreenColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier =
                Modifier.widthIn(max = XikeContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = XikeScreenHorizontalPadding,
                        vertical = XikeScreenVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(XikeContentGap),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(index: String, title: String, trailing: String? = null) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall
    val titleStyle = XikeSectionTitleStyle
    val requiredWidth =
        with(density) {
            (textMeasurer.measure(index, labelStyle, softWrap = false).size.width +
                    textMeasurer.measure(title, titleStyle, softWrap = false).size.width +
                    (trailing?.let {
                        textMeasurer.measure(it, labelStyle, softWrap = false).size.width
                    } ?: 0))
                .toDp()
        } + 12.dp + if (trailing != null) 8.dp else 0.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackTrailing = trailing != null && requiredWidth > maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    index,
                    modifier = Modifier.alignByBaseline(),
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(title, modifier = Modifier.weight(1f).alignByBaseline(), style = titleStyle)
                if (trailing != null && !stackTrailing) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        trailing,
                        modifier = Modifier.alignByBaseline(),
                        maxLines = 1,
                        style = labelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (stackTrailing) {
                Text(
                    trailing.orEmpty(),
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
