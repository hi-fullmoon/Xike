package com.xike.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.xike.app.LocalJournalLabelServices
import com.xike.app.data.JournalLabel
import com.xike.app.data.JournalLabelKind
import com.xike.app.data.JournalLabelSelection
import com.xike.app.data.labelIconKeys
import com.xike.app.tr
import com.xike.app.ui.theme.XikeShapes
import java.util.UUID
import kotlinx.coroutines.launch

internal fun JournalLabelKind.title() =
    if (this == JournalLabelKind.TOPIC) tr("主题", "Topics") else tr("活动", "Activities")

// Editing may contain a new, blank label, so use a UI-state saver rather than backup validation.
internal val journalLabelEditingSaver =
    listSaver<JournalLabel?, Any>(
        save = { label ->
            if (label == null) emptyList()
            else
                listOf(
                    label.id,
                    label.kind.name,
                    label.name,
                    label.iconKey,
                    label.sortOrder,
                    label.isArchived,
                    label.isBuiltin,
                )
        },
        restore = { values ->
            if (values.isEmpty()) null
            else
                JournalLabel(
                    values[0] as String,
                    JournalLabelKind.valueOf(values[1] as String),
                    values[2] as String,
                    values[3] as String,
                    values[4] as Int,
                    values[5] as Boolean,
                    values[6] as Boolean,
                )
        },
    )

internal fun JournalLabel.asTopic(savedName: String = displayName): JournalTopic =
    journalTopics
        .first { it.label == iconKey }
        .copy(label = savedName, translateLabel = false, id = id)

@Composable
internal fun JournalLabelChoices(
    kind: JournalLabelKind,
    selections: List<JournalLabelSelection>,
    enabled: Boolean = true,
    onToggle: (JournalLabelSelection) -> Unit,
    header: (@Composable (onManage: () -> Unit) -> Unit)? = null,
) {
    val services = LocalJournalLabelServices.current
    var managing by rememberSaveable { mutableStateOf(false) }
    val visible =
        services.catalog.filter {
            it.kind == kind &&
                (!it.isArchived || selections.any { selected -> selected.id == it.id })
        }
    val choices =
        visible +
            selections
                .filter { selection ->
                    selection.kind == kind && services.catalog.none { it.id == selection.id }
                }
                .distinctBy { it.id }
                .map {
                    JournalLabel(
                        it.id,
                        it.kind,
                        it.savedName,
                        isArchived = true,
                        isBuiltin = it.isBuiltin,
                    )
                }
    if (header != null) header { managing = true }
    else
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { managing = true },
                enabled = enabled,
                shape = XikeShapes.button,
            ) {
                Text(
                    if (kind == JournalLabelKind.TOPIC) tr("管理主题", "Manage topics")
                    else tr("管理活动", "Manage activities")
                )
            }
        }
    TopicChoices(
        selections.map { it.id },
        topics =
            choices.map { label ->
                label.asTopic(
                    label.displayName + if (label.isArchived) tr("（已归档）", " (archived)") else ""
                )
            },
        enabled = enabled,
    ) { topic ->
        val label = choices.first { it.id == topic.id }
        onToggle(selections.firstOrNull { it.id == label.id } ?: label.selection())
    }
    if (managing) JournalLabelManager(kind) { managing = false }
}

@Composable
internal fun JournalActivities(
    selections: List<JournalLabelSelection>,
    enabled: Boolean = true,
    onToggle: (JournalLabelSelection) -> Unit,
) {
    var expanded by rememberSaveable {
        mutableStateOf(selections.any { it.kind == JournalLabelKind.ACTIVITY })
    }
    val count = selections.count { it.kind == JournalLabelKind.ACTIVITY }
    PaperCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
            ) {
                expanded = !expanded
            }
        ) {
            Text(
                tr("活动", "Activities"),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (expanded) tr("收起", "Collapse")
                else if (count == 0) tr("可选 · 展开", "Optional · Expand")
                else tr("已选 $count · 展开", "$count selected · Expand")
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                tr("这一刻做了什么、经历了什么？", "What did you do or experience?"),
                style = MaterialTheme.typography.bodySmall,
            )
            JournalLabelChoices(JournalLabelKind.ACTIVITY, selections, enabled, onToggle)
        }
    }
}

@Composable
internal fun JournalLabelManager(kind: JournalLabelKind, onDismiss: () -> Unit) {
    if (kind == JournalLabelKind.TOPIC) JournalTopicManager(onDismiss)
    else LegacyLabelManager(kind, onDismiss)
}

@Composable
private fun LegacyLabelManager(kind: JournalLabelKind, onDismiss: () -> Unit) {
    val services = LocalJournalLabelServices.current
    val scope = rememberCoroutineScope()
    var archived by rememberSaveable { mutableStateOf(false) }
    var editing by
        rememberSaveable(stateSaver = journalLabelEditingSaver) {
            mutableStateOf<JournalLabel?>(null)
        }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val values = services.catalog.filter { it.kind == kind && it.isArchived == archived }
    fun mutate(action: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            action().onFailure {
                error = it.message ?: tr("保存失败，请重试。", "Unable to save. Try again.")
            }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = XikeShapes.card,
        title = {
            Text(
                if (kind == JournalLabelKind.TOPIC) tr("管理主题", "Manage topics")
                else tr("管理活动", "Manage activities")
            )
        },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        !archived,
                        { archived = false },
                        label = { Text(tr("使用中", "Active")) },
                    )
                    FilterChip(
                        archived,
                        { archived = true },
                        label = { Text(tr("已归档", "Archived")) },
                    )
                }
                LazyColumn(
                    Modifier.heightIn(max = 380.dp).testTag("label-manager-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(values, key = { it.id }) { label ->
                        Column(Modifier.testTag("label-row:${label.id}")) {
                            Text(label.displayName, style = MaterialTheme.typography.titleSmall)
                            FlowRow {
                                if (!label.isBuiltin)
                                    TextButton({ editing = label }, enabled = !busy) {
                                        Text(tr("编辑", "Edit"))
                                    }
                                TextButton(
                                    { mutate { services.move(label.id, -1) } },
                                    enabled = !busy && values.firstOrNull()?.id != label.id,
                                ) {
                                    Text(tr("上移", "Move up"))
                                }
                                TextButton(
                                    { mutate { services.move(label.id, 1) } },
                                    enabled = !busy && values.lastOrNull()?.id != label.id,
                                ) {
                                    Text(tr("下移", "Move down"))
                                }
                                TextButton(
                                    {
                                        mutate {
                                            services.save(
                                                label.copy(isArchived = !label.isArchived)
                                            )
                                        }
                                    },
                                    enabled = !busy,
                                ) {
                                    Text(if (archived) tr("恢复", "Restore") else tr("归档", "Archive"))
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    editing =
                        JournalLabel(
                            UUID.randomUUID().toString(),
                            kind,
                            "",
                            sortOrder = services.catalog.count { it.kind == kind },
                        )
                },
                enabled = !busy,
            ) {
                Text(tr("新增", "Add"))
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(tr("完成", "Done")) } },
    )
    editing?.let { label ->
        JournalLabelEditor(label, busy, { busy = it }, services.save, { editing = null })
    }
}

@Composable
internal fun JournalLabelEditor(
    label: JournalLabel,
    busy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onSave: suspend (JournalLabel) -> Result<Unit>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(label.id) { mutableStateOf(label.name) }
    var icon by rememberSaveable(label.id) { mutableStateOf(label.iconKey) }
    var editError by remember(label.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        shape = XikeShapes.card,
        title = { Text(if (label.name.isEmpty()) tr("新增", "Add") else tr("编辑", "Edit")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(tr("名称", "Name")) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("图标", "Icon"), Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    labelIconKeys.forEach { key ->
                        FilterChip(
                            icon == key,
                            { icon = key },
                            label = {
                                androidx.compose.material3.Icon(
                                    journalTopics.first { it.label == key }.icon,
                                    com.xike.app.localizedText(key),
                                )
                            },
                        )
                    }
                }
                Text(
                    tr(
                        "改名只影响新记录，历史记录保留当时的名称。",
                        "Renaming affects new entries; existing entries keep their original names.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    onBusyChange(true)
                    scope.launch {
                        onSave(label.copy(name = name, iconKey = icon))
                            .onSuccess { onDismiss() }
                            .onFailure {
                                editError =
                                    it.message ?: tr("保存失败，请重试。", "Unable to save. Try again.")
                            }
                        onBusyChange(false)
                    }
                },
                enabled = !busy,
            ) {
                Text(tr("保存", "Save"))
            }
        },
        dismissButton = {
            TextButton(onDismiss, enabled = !busy) { Text(tr("取消", "Cancel")) }
        },
    )
}
