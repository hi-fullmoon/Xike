package com.xike.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xike.app.data.JournalLabel
import com.xike.app.data.JournalLabelKind

@Entity(tableName = "journal_labels")
internal data class JournalLabelEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean,
) {
    fun toLabel() =
        JournalLabel(
            id,
            JournalLabelKind.valueOf(kind),
            name,
            iconKey,
            sortOrder,
            isArchived,
            isBuiltin,
        )
}

internal fun JournalLabel.toEntity() =
    JournalLabelEntity(id, kind.name, name, iconKey, sortOrder, isArchived, isBuiltin)
