package com.xike.app.data

import com.xike.app.canonicalTopic
import com.xike.app.localizedText
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class JournalLabelKind {
    TOPIC,
    ACTIVITY,
}

data class JournalLabel(
    val id: String,
    val kind: JournalLabelKind,
    val name: String,
    val iconKey: String = "其他",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val isBuiltin: Boolean = false,
) {
    val displayName: String
        get() = if (isBuiltin) builtinLabelName(kind, name) else name

    fun selection() = JournalLabelSelection(id, kind, name, isBuiltin)

    val searchNames: List<String>
        get() = selection().searchNames

    fun toJson() =
        JSONObject()
            .put("id", id)
            .put("kind", kind.name)
            .put("name", name)
            .put("iconKey", iconKey)
            .put("sortOrder", sortOrder)
            .put("isArchived", isArchived)
            .put("isBuiltin", isBuiltin)

    companion object {
        fun fromJson(json: JSONObject): JournalLabel {
            val value =
                JournalLabel(
                    json.getString("id"),
                    JournalLabelKind.valueOf(json.getString("kind")),
                    json.getString("name"),
                    json.getString("iconKey"),
                    json.getInt("sortOrder"),
                    json.getBoolean("isArchived"),
                    json.getBoolean("isBuiltin"),
                )
            require(
                value.id.isNotBlank() && value.name.isNotBlank() && value.iconKey in labelIconKeys
            )
            val builtin = builtinJournalLabels.firstOrNull { it.id == value.id }
            require(
                if (builtin != null)
                    value.isBuiltin && value.kind == builtin.kind && value.name == builtin.name
                else !value.isBuiltin && !value.id.startsWith("builtin:")
            )
            return value
        }
    }
}

data class JournalLabelSelection(
    val id: String,
    val kind: JournalLabelKind,
    val savedName: String,
    val isBuiltin: Boolean = false,
) {
    val displayName: String
        get() = if (isBuiltin) builtinLabelName(kind, savedName) else savedName

    val searchNames: List<String>
        get() =
            if (isBuiltin && kind == JournalLabelKind.TOPIC)
                com.xike.app.builtinTopicSearchNames(savedName)
            else
                listOfNotNull(
                    savedName,
                    if (isBuiltin && kind == JournalLabelKind.ACTIVITY)
                        activityEnglishNames[savedName]
                    else null,
                )

    fun toJson() =
        JSONObject()
            .put("id", id)
            .put("kind", kind.name)
            .put("savedName", savedName)
            .put("isBuiltin", isBuiltin)

    companion object {
        fun fromJson(json: JSONObject): JournalLabelSelection {
            val value =
                JournalLabelSelection(
                    json.getString("id"),
                    JournalLabelKind.valueOf(json.getString("kind")),
                    json.getString("savedName"),
                    json.getBoolean("isBuiltin"),
                )
            require(value.id.isNotBlank() && value.savedName.isNotBlank())
            val builtin = builtinJournalLabels.firstOrNull { it.id == value.id }
            require(
                if (builtin != null)
                    value.isBuiltin &&
                        value.kind == builtin.kind &&
                        (if (builtin.kind == JournalLabelKind.TOPIC)
                            canonicalTopic(value.savedName) == builtin.name
                        else value.savedName == builtin.name)
                else !value.isBuiltin && !value.id.startsWith("builtin:")
            )
            return value
        }
    }
}

val labelIconKeys =
    listOf("工作", "学习", "关系", "家庭", "身体", "睡眠", "饮食", "金钱", "自我", "兴趣", "出行", "居住", "其他")

val builtinJournalLabels: List<JournalLabel> =
    labelIconKeys.mapIndexed { index, name ->
        JournalLabel(
            "builtin:topic:$index",
            JournalLabelKind.TOPIC,
            name,
            name,
            index,
            isBuiltin = true,
        )
    } +
        listOf(
                "散步" to "身体",
                "运动" to "身体",
                "阅读" to "学习",
                "休息" to "睡眠",
                "独处" to "自我",
                "见朋友" to "关系",
                "加班" to "工作",
                "创作" to "兴趣",
            )
            .mapIndexed { index, (name, icon) ->
                JournalLabel(
                    "builtin:activity:$index",
                    JournalLabelKind.ACTIVITY,
                    name,
                    icon,
                    index,
                    isBuiltin = true,
                )
            }

// Keep the full builtin catalog and its persisted IDs separate from fresh-install preferences.
val defaultJournalLabels: List<JournalLabel> = builtinJournalLabels.map { label ->
    if (
        label.kind == JournalLabelKind.TOPIC &&
            label.name !in setOf("工作", "学习", "关系", "身体", "兴趣", "自我")
    )
        label.copy(isArchived = true)
    else label
}

fun legacyTopicSelection(name: String): JournalLabelSelection {
    val canonical = canonicalTopic(name)
    val builtin = builtinJournalLabels.firstOrNull {
        it.kind == JournalLabelKind.TOPIC && it.name == canonical
    }
    return if (builtin != null) builtin.selection().copy(savedName = name)
    else
        JournalLabelSelection(
            "legacy:" + UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)),
            JournalLabelKind.TOPIC,
            name,
        )
}

fun JSONArray.labelSelections(): List<JournalLabelSelection> =
    List(length()) { JournalLabelSelection.fromJson(getJSONObject(it)) }

fun normalizedLabelName(name: String): String =
    Normalizer.normalize(name.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

fun validateLabelName(name: String) {
    require(name.trim().isNotEmpty()) { com.xike.app.tr("请输入名称。", "Enter a name.") }
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(name.trim()) }
    var count = 0
    iterator.first()
    while (iterator.next() != BreakIterator.DONE) count++
    require(count <= 24 && name.none { Character.isISOControl(it) }) {
        com.xike.app.tr(
            "名称最多 24 个字符，不能包含换行或控制字符。",
            "Use up to 24 characters without line breaks or control characters.",
        )
    }
}

fun catalogWithSelections(
    catalog: List<JournalLabel>,
    selections: List<JournalLabelSelection>,
): List<JournalLabel> {
    val known = catalog.associateBy { it.id }.toMutableMap()
    selections.forEach { selection ->
        val existing = known[selection.id]
        require(existing == null || existing.kind == selection.kind)
        if (existing == null)
            known[selection.id] =
                JournalLabel(
                    selection.id,
                    selection.kind,
                    selection.savedName,
                    isArchived = true,
                    isBuiltin = selection.isBuiltin,
                )
    }
    return known.values.toList()
}

private val activityEnglishNames =
    mapOf(
        "散步" to "Walking",
        "运动" to "Exercise",
        "阅读" to "Reading",
        "休息" to "Resting",
        "独处" to "Time alone",
        "见朋友" to "Seeing friends",
        "加班" to "Overtime",
        "创作" to "Creating",
    )

private fun builtinLabelName(kind: JournalLabelKind, name: String): String {
    if (kind == JournalLabelKind.TOPIC) return localizedText(name)
    return activityEnglishNames[name]?.let { com.xike.app.tr(name, it) } ?: name
}

internal data class LabelRestorePlan(
    val catalog: List<JournalLabel>,
    val entries: List<JournalEntry>,
)

internal fun planLabelRestore(
    local: List<JournalLabel>,
    incoming: List<JournalLabel>,
    entries: List<JournalEntry>,
    merge: Boolean,
    protected: List<JournalLabelSelection> = emptyList(),
): LabelRestorePlan {
    val known = local.associateBy { it.id }
    val mapping = incoming.associate { label ->
        val conflict = known[label.id]?.kind?.let { it != label.kind } == true
        label.id to
            if (conflict)
                "import:" +
                    UUID.nameUUIDFromBytes("${label.id}:${label.kind}".toByteArray(Charsets.UTF_8))
            else label.id
    }
    val definitions = incoming.map { it.copy(id = mapping.getValue(it.id)) }
    val combined = (if (merge) local else emptyList()).associateBy { it.id }.toMutableMap()
    definitions.forEach { label ->
        val existing = combined[label.id] ?: known[label.id]?.takeIf { merge }
        require(existing == null || existing.kind == label.kind)
        if (existing == null) combined[label.id] = label
    }
    builtinJournalLabels.forEach { combined.putIfAbsent(it.id, it) }
    val catalog = catalogWithSelections(combined.values.toList(), protected)
    return LabelRestorePlan(
        catalog,
        entries.map { entry ->
            entry.copy(labels = entry.labels.map { it.copy(id = mapping[it.id] ?: it.id) })
        },
    )
}
