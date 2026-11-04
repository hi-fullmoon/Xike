package com.xike.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xike.app.LocalJournalLabelServices
import com.xike.app.data.JournalLabel
import com.xike.app.data.JournalLabelKind
import com.xike.app.tr
import com.xike.app.ui.theme.*
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalTopicManager(onDismiss: () -> Unit) {
    val services = LocalJournalLabelServices.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var archived by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    var editing by
        rememberSaveable(stateSaver = journalLabelEditingSaver) {
            mutableStateOf<JournalLabel?>(null)
        }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revealId by rememberSaveable { mutableStateOf<String?>(null) }
    val values =
        services.catalog.filter { it.kind == JournalLabelKind.TOPIC && it.isArchived == archived }

    fun mutate(action: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action().onFailure {
                    error = it.message ?: tr("保存失败，请重试。", "Unable to save. Try again.")
                }
            } finally {
                busy = false
            }
        }
    }
    val edit = editing
    if (edit != null) {
        JournalLabelEditor(
            edit,
            busy,
            { busy = it },
            onSave = { label ->
                services.save(label).onSuccess {
                    archived = label.isArchived
                    revealId = label.id
                }
            },
            onDismiss = { editing = null },
        )
        return
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.padding(
                horizontal = XikeSheetHorizontalPadding,
                vertical = XikeDialogContentGap,
            )
        ) {
            val shortWindow = maxHeight < 480.dp
            val compactFooter = shortWindow && maxWidth >= 480.dp
            LaunchedEffect(revealId, values, shortWindow) {
                val index = values.indexOfFirst { it.id == revealId }
                if (revealId != null && index >= 0) {
                    listState.animateScrollToItem(index + if (shortWindow) 1 else 0)
                    revealId = null
                }
            }
            Surface(
                modifier =
                    Modifier.widthIn(max = XikeContentMaxWidth)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.9f),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    val count =
                        tr(
                            "${values.size} 个主题",
                            "${values.size} ${if (values.size == 1) "topic" else "topics"}",
                        )
                    val sections: @Composable () -> Unit = {
                        if (shortWindow)
                            Text(
                                count,
                                Modifier.padding(horizontal = XikeCardPadding, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        TopicManagerSections(archived, busy) { isArchived ->
                            if (archived != isArchived) scope.launch { listState.scrollToItem(0) }
                            archived = isArchived
                            sorting = false
                            error = null
                        }
                        if (sorting)
                            Text(
                                tr("使用箭头调整主题顺序", "Use the arrows to reorder topics"),
                                Modifier.padding(horizontal = XikeCardPadding, vertical = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        else Spacer(Modifier.height(8.dp))
                    }
                    val footer: @Composable () -> Unit = {
                        error?.let {
                            Text(
                                it,
                                Modifier.padding(horizontal = XikeCardPadding, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        TopicManagerFooter(
                            sorting,
                            busy,
                            values.size > 1,
                            onSort = { sorting = !sorting },
                            onAdd = {
                                error = null
                                sorting = false
                                editing =
                                    JournalLabel(
                                        UUID.randomUUID().toString(),
                                        JournalLabelKind.TOPIC,
                                        "",
                                        sortOrder =
                                            (services.catalog
                                                .filter { it.kind == JournalLabelKind.TOPIC }
                                                .maxOfOrNull { it.sortOrder } ?: -1) + 1,
                                    )
                            },
                            compact = compactFooter,
                        )
                    }
                    SecondaryPageHeader(
                        title = tr("管理主题", "Manage topics"),
                        subtitle = if (shortWindow) null else count,
                        closeDescription = tr("关闭主题管理", "Close topic manager"),
                        onClose = onDismiss,
                        closeEnabled = !busy,
                        modifier =
                            Modifier.padding(
                                start = XikeCardPadding,
                                top = if (shortWindow) XikeDialogCompactGap else XikeCardPadding,
                                end = 4.dp,
                                bottom = if (shortWindow) XikeDialogCompactGap else 12.dp,
                            ),
                    )
                    if (!shortWindow) sections()
                    LazyColumn(
                        Modifier.weight(1f, fill = false).testTag("label-manager-list"),
                        state = listState,
                    ) {
                        if (shortWindow) item(key = "section-controls") { sections() }
                        if (values.isEmpty())
                            item {
                                Text(
                                    if (archived) tr("没有已归档的主题", "No archived topics")
                                    else
                                        tr(
                                            "还没有使用中的主题，可新增或从已归档中恢复。",
                                            "No active topics. Add one or restore an archived topic.",
                                        ),
                                    modifier = Modifier.fillMaxWidth().padding(XikeCardPadding),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        itemsIndexed(values, key = { _, label -> "label:${label.id}" }) {
                            index,
                            label ->
                            TopicManagerRow(
                                label,
                                sorting,
                                busy,
                                canMoveUp = index > 0,
                                canMoveDown = index < values.lastIndex,
                                onEdit = {
                                    error = null
                                    editing = label
                                },
                                onArchiveChange = {
                                    mutate {
                                        services.save(label.copy(isArchived = !label.isArchived))
                                    }
                                },
                                onMove = { direction ->
                                    mutate { services.move(label.id, direction) }
                                },
                            )
                            if (index < values.lastIndex)
                                HorizontalDivider(
                                    Modifier.padding(start = 72.dp, end = XikeCardPadding),
                                    color =
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                        }
                        if (shortWindow) item(key = "section-footer") { footer() }
                    }
                    if (!shortWindow) footer()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicManagerSections(archived: Boolean, busy: Boolean, onSelect: (Boolean) -> Unit) {
    if (LocalDensity.current.fontScale > 1.3f) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = XikeCardPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(false, true).forEach { value ->
                FilterChip(
                    selected = archived == value,
                    onClick = { onSelect(value) },
                    enabled = !busy,
                    label = { Text(if (value) tr("已归档", "Archived") else tr("使用中", "Active")) },
                )
            }
        }
    } else
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = XikeCardPadding)
        ) {
            listOf(false, true).forEachIndexed { index, value ->
                SegmentedButton(
                    selected = archived == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    enabled = !busy,
                    icon = {},
                    label = { Text(if (value) tr("已归档", "Archived") else tr("使用中", "Active")) },
                )
            }
        }
}

@Composable
private fun TopicManagerRow(
    label: JournalLabel,
    sorting: Boolean,
    busy: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onArchiveChange: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val topic = label.asTopic()
    val tint =
        if (isSystemInDarkTheme())
            androidx.compose.ui.graphics.lerp(topic.accent, Color.White, 0.45f)
        else topic.accent
    Row(
        modifier =
            Modifier.testTag("label-row:${label.id}")
                .then(
                    if (!label.isBuiltin && !sorting)
                        Modifier.clickable(
                            enabled = !busy,
                            onClickLabel =
                                tr("编辑${label.displayName}", "Edit ${label.displayName}"),
                            onClick = onEdit,
                        )
                    else Modifier
                )
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.padding(top = 4.dp),
            shape = XikeShapes.inner,
            color = topic.accent.copy(alpha = 0.13f),
        ) {
            Icon(
                topic.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(8.dp).size(24.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(label.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                if (label.isBuiltin) tr("内置", "Built-in") else tr("自定义", "Custom"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box {
            val actions: @Composable () -> Unit = {
                if (sorting) {
                    IconButton(onClick = { onMove(-1) }, enabled = !busy && canMoveUp) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            tr("上移${label.displayName}", "Move ${label.displayName} up"),
                        )
                    }
                    IconButton(onClick = { onMove(1) }, enabled = !busy && canMoveDown) {
                        Icon(
                            Icons.Outlined.ArrowDownward,
                            tr("下移${label.displayName}", "Move ${label.displayName} down"),
                        )
                    }
                } else {
                    if (label.isArchived)
                        TextButton(onClick = onArchiveChange, enabled = !busy) {
                            Text(tr("恢复", "Restore"))
                        }
                    if (!label.isArchived || !label.isBuiltin)
                        Box {
                            IconButton(onClick = { expanded = true }, enabled = !busy) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    tr(
                                        "${label.displayName}的更多操作",
                                        "More actions for ${label.displayName}",
                                    ),
                                )
                            }
                            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                if (!label.isBuiltin)
                                    DropdownMenuItem(
                                        text = { Text(tr("编辑", "Edit")) },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                        onClick = {
                                            expanded = false
                                            onEdit()
                                        },
                                    )
                                if (!label.isArchived)
                                    DropdownMenuItem(
                                        text = { Text(tr("归档", "Archive")) },
                                        leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                                        onClick = {
                                            expanded = false
                                            onArchiveChange()
                                        },
                                    )
                            }
                        }
                }
            }
            if (LocalDensity.current.fontScale > 1.3f)
                Column(horizontalAlignment = Alignment.End) { actions() }
            else Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

@Composable
private fun TopicManagerFooter(
    sorting: Boolean,
    busy: Boolean,
    canSort: Boolean,
    onSort: () -> Unit,
    onAdd: () -> Unit,
    compact: Boolean = false,
) {
    val stacked = LocalDensity.current.fontScale > 1.3f && !compact
    val sortButton: @Composable () -> Unit = {
        TextButton(
            onClick = onSort,
            enabled = !busy && canSort,
            modifier =
                Modifier.testTag("topic-manager-sort")
                    .then(if (stacked) Modifier.fillMaxWidth() else Modifier),
            shape = XikeShapes.button,
        ) {
            Icon(Icons.Outlined.SwapVert, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("排序", "Reorder"))
        }
    }
    val addButton: @Composable () -> Unit = {
        Button(
            onClick = onAdd,
            enabled = !busy,
            shape = XikeShapes.button,
            modifier =
                Modifier.testTag("topic-manager-add")
                    .then(if (stacked) Modifier.fillMaxWidth() else Modifier),
        ) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("新增主题", "Add topic"))
        }
    }
    if (sorting) {
        Button(
            onClick = onSort,
            enabled = !busy,
            shape = XikeShapes.button,
            modifier =
                Modifier.fillMaxWidth().padding(XikeCardPadding).testTag("topic-manager-sort-done"),
        ) {
            Text(tr("完成排序", "Done reordering"))
        }
    } else if (stacked) {
        Column(
            Modifier.fillMaxWidth().padding(XikeCardPadding),
            horizontalAlignment = Alignment.End,
        ) {
            addButton()
            sortButton()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(XikeCardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            sortButton()
            addButton()
        }
    }
}
