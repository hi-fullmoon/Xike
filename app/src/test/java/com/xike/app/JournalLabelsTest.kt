package com.xike.app

import com.xike.app.data.*
import com.xike.app.data.model.JournalDraft
import com.xike.app.data.model.parseJournalDraft
import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.journalPeriodSummary
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JournalLabelsTest {
    @Test
    fun `fresh defaults archive optional topics without changing builtin identities or activities`() {
        val topics = defaultJournalLabels.filter { it.kind == JournalLabelKind.TOPIC }
        assertEquals(
            setOf("工作", "学习", "关系", "身体", "兴趣", "自我"),
            topics.filterNot { it.isArchived }.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("家庭", "睡眠", "饮食", "金钱", "出行", "居住", "其他"),
            topics.filter { it.isArchived }.map { it.name }.toSet(),
        )
        assertEquals(builtinJournalLabels.map { it.id }, defaultJournalLabels.map { it.id })
        assertEquals(
            builtinJournalLabels.map { it.sortOrder },
            defaultJournalLabels.map { it.sortOrder },
        )
        assertEquals(
            builtinJournalLabels.filter { it.kind == JournalLabelKind.ACTIVITY },
            defaultJournalLabels.filter { it.kind == JournalLabelKind.ACTIVITY },
        )
        assertEquals(
            13,
            builtinJournalLabels.count { it.kind == JournalLabelKind.TOPIC && !it.isArchived },
        )
    }

    @Test
    fun `retired activity snapshots survive serialization without entering period reviews`() {
        val label = JournalLabel("activity-identity", JournalLabelKind.ACTIVITY, "夜跑")
        val today = LocalDate.of(2026, 11, 4)
        val zone = ZoneId.of("Asia/Shanghai")
        val entry =
            JournalEntry(
                id = "recorded-entry",
                createdAt = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                mood = Mood.CALM,
                note = "",
                labels = listOf(label.selection()),
            )
        val previous =
            entry.copy(
                id = "previous-entry",
                createdAt = today.minusDays(35).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        val summary =
            journalPeriodSummary(
                listOf(entry, previous),
                InsightsPeriod.DAYS_30,
                today,
                zone,
            )
        assertEquals("夜跑", JournalEntry.fromJson(entry.toJson()).labels.single().savedName)
        assertEquals(1, summary.entryCount)
        assertEquals(1, summary.comparison.entryCount)
        assertEquals(listOf(entry.id), summary.entryIds)
        assertFalse(com.xike.app.feature.insights.localReviewText(summary).contains("活动"))
        assertFalse(com.xike.app.feature.insights.localReviewText(summary).contains("夜跑"))
        assertFalse(com.xike.app.feature.insights.localReviewText(summary).contains("主题"))
        assertEquals(
            restoreContentKey(entry, { it }, { it }, { it }),
            restoreContentKey(entry.copy(), { it }, { it }, { it }),
        )
    }

    @Test
    fun `activity only draft is retained and round trips without legacy alias normalization`() {
        val activity = builtinJournalLabels.first {
            it.kind == JournalLabelKind.ACTIVITY && it.name == "运动"
        }
        val draft = JournalDraft(labels = listOf(activity.selection()), updatedAt = 123L)
        assertFalse(draft.isEmpty)
        assertEquals(draft, parseJournalDraft(draft.toJson().toString()))
        assertEquals(JournalLabelKind.ACTIVITY, draft.labels.single().kind)
        assertTrue(draft.tags.isEmpty())
        assertEquals(
            "身体",
            legacyTopicSelection("运动").let { selection ->
                builtinJournalLabels.first { it.id == selection.id }.name
            },
        )
    }

    @Test
    fun `legacy draft and backup labels remain topics and preserve recorded names`() {
        val legacy =
            JSONObject()
                .put("id", "legacy-entry")
                .put("createdAt", 1L)
                .put("mood", "CALM")
                .put("tags", org.json.JSONArray(listOf("运动", "身体", "个人项目")))
                .put("note", "")
        val entry = JournalEntry.fromJson(legacy)
        assertEquals(listOf("运动", "身体", "个人项目"), entry.tags)
        assertEquals(entry.labels[0].id, entry.labels[1].id)
        assertTrue(entry.labels.all { it.kind == JournalLabelKind.TOPIC })
        assertEquals(entry, JournalEntry.fromJson(entry.toJson()))
    }

    @Test
    fun `topic OR and activity OR are combined with AND`() {
        val work = builtinJournalLabels.first {
            it.kind == JournalLabelKind.TOPIC && it.name == "工作"
        }
        val walk = builtinJournalLabels.first {
            it.kind == JournalLabelKind.ACTIVITY && it.name == "散步"
        }
        val reading = builtinJournalLabels.first {
            it.kind == JournalLabelKind.ACTIVITY && it.name == "阅读"
        }
        val matching =
            JournalEntry(
                id = "both",
                mood = Mood.CALM,
                note = "",
                labels = listOf(work.selection(), walk.selection()),
            )
        val entries =
            listOf(
                matching,
                matching.copy(id = "topic-only", labels = listOf(work.selection())),
                matching.copy(id = "activity-only", labels = listOf(reading.selection())),
            )
        val query =
            JournalSearchQuery(tags = setOf(work.id), activities = setOf(walk.id, reading.id))
        assertEquals(listOf("both"), filterJournalEntries(entries, query).map { it.id })
        assertEquals(
            listOf("both"),
            filterJournalEntries(entries, JournalSearchQuery(text = "Walking")).map { it.id },
        )
        assertEquals(
            listOf("activity-only"),
            filterJournalEntries(entries, JournalSearchQuery(text = "阅读")).map { it.id },
        )
    }

    @Test
    fun `restore kind conflicts remap consistently and same names retain separate identity`() {
        val local = JournalLabel("shared-id", JournalLabelKind.TOPIC, "项目")
        val incoming = JournalLabel("shared-id", JournalLabelKind.ACTIVITY, "项目")
        val entry =
            JournalEntry(
                id = "incoming-record",
                mood = Mood.CALM,
                note = "",
                labels = listOf(incoming.selection()),
            )
        val first = planLabelRestore(listOf(local), listOf(incoming), listOf(entry), true)
        val second = planLabelRestore(first.catalog, listOf(incoming), listOf(entry), true)
        assertEquals(first, second)
        assertNotEquals(local.id, first.entries.single().labels.single().id)
        assertEquals(2, first.catalog.count { it.name == "项目" })
        val replacement =
            planLabelRestore(
                listOf(local),
                listOf(incoming),
                listOf(entry),
                false,
                listOf(local.selection()),
            )
        assertTrue(replacement.catalog.any { it.id == local.id && it.isArchived })
    }

    @Test
    fun `builtin identities reject incompatible definitions and custom names remain untranslated`() {
        val builtin = builtinJournalLabels.first()
        assertThrows(IllegalArgumentException::class.java) {
            JournalLabel.fromJson(builtin.copy(kind = JournalLabelKind.ACTIVITY).toJson())
        }
        val label = JournalLabel("user-defined", JournalLabelKind.TOPIC, "工作")
        assertEquals("工作", label.displayName)
        assertEquals("工作", label.selection().displayName)
        assertEquals("work", normalizedLabelName(" ＷＯＲＫ "))
        assertTrue(
            builtinJournalLabels
                .first { it.name == "工作" }
                .searchNames
                .any { normalizedLabelName(it) == "work" }
        )
        assertThrows(IllegalArgumentException::class.java) { validateLabelName(" ") }
        assertThrows(IllegalArgumentException::class.java) { validateLabelName("名".repeat(25)) }
        assertThrows(IllegalArgumentException::class.java) { validateLabelName("两\n行") }
    }
}
