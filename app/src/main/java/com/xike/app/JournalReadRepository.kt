package com.xike.app

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.ColumnInfo
import androidx.room.Relation
import androidx.sqlite.db.SimpleSQLiteQuery
import com.xike.app.data.JournalEntry
import com.xike.app.data.Mood
import com.xike.app.feature.insights.InsightsPeriod
import com.xike.app.feature.insights.JournalPeriodSummary
import com.xike.app.feature.insights.journalPeriodSummary
import com.xike.app.feature.insights.readDateRanges
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class JournalLibraryState(
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val revision: Long = 0,
)

data class JournalEntryIndex(val id: String, @ColumnInfo(name = "created_at") val createdAt: Long)

internal data class JournalDayCount(val day: Int, val count: Int)

internal data class JournalTopicCount(val tag: String, val count: Int)

internal data class JournalAnalyticsRecord(
    val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val mood: String,
    @Relation(parentColumn = "id", entityColumn = "entry_id") val tags: List<JournalTagEntity>,
) {
    fun toEntry() =
        JournalEntry(
            id = id,
            createdAt = createdAt,
            mood = Mood.fromName(mood),
            tags = tags.sortedBy { it.position }.map { it.tag },
            note = "",
        )
}

data class JournalIndexPage(val entries: List<JournalEntryIndex>, val totalCount: Int)

internal suspend fun <T> journalReadResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

/** Local read queries never retain the complete library or decrypt attachment bodies. */
class JournalReadRepository internal constructor(databaseProvider: () -> JournalDatabase) {
    internal constructor(database: JournalDatabase) : this({ database })

    private val database by lazy(databaseProvider)
    private val dao
        get() = database.journalDao()

    internal fun changes(): Flow<Set<String>> =
        database.invalidationTracker.createFlow(
            "journal_entries",
            "journal_tags",
            "journal_images",
            "journal_audios",
        )

    suspend fun libraryState(
        revision: Long,
        today: LocalDate = LocalDate.now(),
    ): JournalLibraryState = read {
        database.runInTransaction<JournalLibraryState> {
            JournalLibraryState(
                dao.entryCount(),
                count(JournalSearchQuery(startDate = today, endDate = today)),
                revision,
            )
        }
    }

    fun pages(
        query: JournalSearchQuery,
        ids: List<String>? = null,
    ): Flow<PagingData<JournalEntry>> =
        Pager(
                config =
                    PagingConfig(
                        pageSize = 60,
                        initialLoadSize = 60,
                        prefetchDistance = 20,
                        maxSize = 300,
                        enablePlaceholders = true,
                    ),
                pagingSourceFactory = {
                    dao.pagedRecords(sql(query, "e.*", ids = ids, ordered = true))
                },
            )
            .flow
            .map { page -> page.map(JournalEntryRecord::toJournalEntry) }

    suspend fun indexPage(
        query: JournalSearchQuery,
        offset: Int,
        knownCount: Int? = null,
    ): JournalIndexPage = read {
        require(offset >= 0)
        database.runInTransaction<JournalIndexPage> {
            JournalIndexPage(
                dao.entryIndex(sql(query, "e.id, e.created_at", ordered = true, offset = offset)),
                knownCount ?: count(query),
            )
        }
    }

    suspend fun entry(id: String): JournalEntry? = read { dao.record(id)?.toJournalEntry() }

    suspend fun topics(): List<String> = read {
        // Use the same topic normalization as selection; historical aliases in one entry count
        // once.
        val aliases = topicAliases()
        val args = aliases.flatMap { listOf(it.key, it.value) }
        val canonical =
            "CASE tag " + aliases.entries.joinToString(" ") { "WHEN ? THEN ?" } + " ELSE tag END"
        dao.topicCounts(
                SimpleSQLiteQuery(
                    "SELECT $canonical AS tag, COUNT(DISTINCT entry_id) AS count FROM journal_tags GROUP BY 1 ORDER BY count DESC, tag ASC",
                    args.toTypedArray(),
                )
            )
            .map { it.tag }
    }

    suspend fun calendar(
        query: JournalSearchQuery,
        month: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, Int> = read {
        val days = (1..month.lengthOfMonth()).map(month::atDay)
        val args = days.map { it.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() }
        val bucket =
            "CASE " +
                days.joinToString(" ") { "WHEN e.created_at < ? THEN ${it.dayOfMonth}" } +
                " END"
        val scoped = query.copy(startDate = month.atDay(1), endDate = month.atEndOfMonth())
        dao.dayCounts(
                sql(
                    scoped,
                    "$bucket AS day, COUNT(*) AS count",
                    prefixArgs = args,
                    zoneId = zoneId,
                    groupByDay = true,
                )
            )
            .associate { month.atDay(it.day) to it.count }
    }

    suspend fun insights(period: InsightsPeriod, today: LocalDate): JournalPeriodSummary = read {
        val ranges = period.readDateRanges(today)
        val zoneId = ZoneId.systemDefault()
        val records =
            database.runInTransaction<List<JournalAnalyticsRecord>> {
                ranges.flatMap { (start, end) ->
                    dao.analyticsRecords(
                        start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                        end.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    )
                }
            }
        withContext(Dispatchers.Default) {
            journalPeriodSummary(
                records.map(JournalAnalyticsRecord::toEntry),
                period,
                today,
                zoneId,
            )
        }
    }

    private fun count(query: JournalSearchQuery): Int = dao.queryCount(sql(query, "COUNT(*)"))

    private suspend fun <T> read(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw com.xike.app.data.JournalDataException(
                    localizedText("日记数据库暂时无法读取，原数据未被覆盖。"),
                    error,
                )
            }
        }

    internal fun sql(
        query: JournalSearchQuery,
        columns: String,
        ids: List<String>? = null,
        ordered: Boolean = false,
        offset: Int? = null,
        prefixArgs: List<Any> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        groupByDay: Boolean = false,
    ): SimpleSQLiteQuery {
        val args = prefixArgs.toMutableList()
        val conditions = mutableListOf<String>()
        val range = query.epochRange(zoneId)
        conditions += "e.created_at >= ? AND e.created_at < ?"
        args += range.startInclusive
        args += range.endExclusive
        if (query.normalizedText.isNotEmpty()) {
            conditions +=
                "e.id IN (SELECT entry_id FROM journal_entries_fts WHERE journal_entries_fts MATCH ?)"
            args += journalFtsQuery(query.normalizedText)
        }
        fun inList(column: String, values: List<String>) {
            conditions += "$column IN (${values.joinToString(",") { "?" }})"
            args.addAll(values)
        }
        if (query.moods.isNotEmpty()) inList("e.mood", query.moods.map { it.name })
        if (query.tags.isNotEmpty()) {
            val tags = expandedTopicLabels(query.tags)
            conditions +=
                "EXISTS (SELECT 1 FROM journal_tags t WHERE t.entry_id = e.id AND t.tag IN (${tags.joinToString(",") { "?" }}))"
            args.addAll(tags)
        }
        when (query.imageFilter) {
            JournalImageFilter.ANY -> Unit
            JournalImageFilter.WITH_IMAGES ->
                conditions += "EXISTS (SELECT 1 FROM journal_images i WHERE i.entry_id = e.id)"
            JournalImageFilter.WITHOUT_IMAGES ->
                conditions += "NOT EXISTS (SELECT 1 FROM journal_images i WHERE i.entry_id = e.id)"
        }
        // Android's escaping handles IDs without the bind-variable limit or a JSON1 requirement on
        // API 26.
        if (ids != null) {
            conditions +=
                if (ids.isEmpty()) "0"
                else
                    "e.id IN (${ids.joinToString(",", transform = android.database.DatabaseUtils::sqlEscapeString)})"
        }
        var statement =
            "SELECT $columns FROM journal_entries e WHERE ${conditions.joinToString(" AND ")}"
        if (groupByDay) statement += " GROUP BY day"
        if (ordered) statement += " ORDER BY e.created_at DESC, e.id DESC"
        if (offset != null) {
            statement += " LIMIT 60 OFFSET ?"
            args += offset
        }
        return SimpleSQLiteQuery(statement, args.toTypedArray())
    }
}
