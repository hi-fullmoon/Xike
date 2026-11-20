import CryptoKit
import Foundation
import SQLCipher

private enum BindValue {
    case text(String), integer(Int64), blob(Data)
}

private final class EncryptedDatabase {
    private let handle: OpaquePointer
    private static let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    init(_ url: URL, key: Data) throws {
        var result: OpaquePointer?
        guard sqlite3_open_v2(url.path, &result, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX,
                              nil) == SQLITE_OK, let result else {
            if let result { sqlite3_close(result) }
            throw JournalError.storage
        }
        handle = result
        let status = key.withUnsafeBytes { sqlite3_key(result, $0.baseAddress, Int32($0.count)) }
        guard status == SQLITE_OK else { throw JournalError.key }
        guard try firstInteger("SELECT count(*) FROM sqlite_master") != nil,
              try rows("PRAGMA cipher_version", read: { sqlite3_column_text($0, 0) != nil }).first == true
        else { throw JournalError.key }
        sqlite3_busy_timeout(handle, 3_000)
        try execute("PRAGMA temp_store=MEMORY")
        try execute("PRAGMA journal_mode=WAL")
        try execute("PRAGMA synchronous=FULL")
        switch try firstInteger("PRAGMA user_version") {
        case 0:
            try transaction {
                try execute("""
                    CREATE TABLE journal_entry (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, payload BLOB NOT NULL);
                    CREATE INDEX journal_entry_date ON journal_entry (created_at DESC, id);
                    CREATE TABLE journal_state (name TEXT PRIMARY KEY, payload BLOB NOT NULL);
                    PRAGMA user_version=1;
                    """)
            }
        case 1, 2: break
        default: throw JournalError.storage
        }
    }

    deinit { sqlite3_close(handle) }

    func execute(_ sql: String, _ arguments: [BindValue] = []) throws {
        if arguments.isEmpty {
            guard sqlite3_exec(handle, sql, nil, nil, nil) == SQLITE_OK else { throw JournalError.storage }
        } else {
            let statement = try prepare(sql, arguments)
            defer { sqlite3_finalize(statement) }
            guard sqlite3_step(statement) == SQLITE_DONE else { throw JournalError.storage }
        }
    }

    func transaction<T>(_ block: () throws -> T) throws -> T {
        try execute("BEGIN IMMEDIATE TRANSACTION")
        do {
            let result = try block()
            try execute("COMMIT")
            return result
        } catch {
            try? execute("ROLLBACK")
            throw error
        }
    }

    func rows<T>(_ sql: String, _ arguments: [BindValue] = [], read: (OpaquePointer) throws -> T) throws -> [T] {
        var values: [T] = []
        try forEachRow(sql, arguments) { values.append(try read($0)) }
        return values
    }

    func forEachRow(_ sql: String, _ arguments: [BindValue] = [], read: (OpaquePointer) throws -> Void) throws {
        let statement = try prepare(sql, arguments)
        defer { sqlite3_finalize(statement) }
        while true {
            switch sqlite3_step(statement) {
            case SQLITE_ROW: try read(statement)
            case SQLITE_DONE: return
            default: throw JournalError.storage
            }
        }
    }

    func firstInteger(_ sql: String, _ arguments: [BindValue] = []) throws -> Int64? {
        try rows(sql, arguments) { sqlite3_column_int64($0, 0) }.first
    }

    private func prepare(_ sql: String, _ arguments: [BindValue]) throws -> OpaquePointer {
        var result: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &result, nil) == SQLITE_OK, let result
        else { throw JournalError.storage }
        for (index, value) in arguments.enumerated() {
            let position = Int32(index + 1)
            let status: Int32
            switch value {
            case .text(let text):
                status = text.withCString { sqlite3_bind_text(result, position, $0, Int32(text.utf8.count), Self.transient) }
            case .integer(let number):
                status = sqlite3_bind_int64(result, position, number)
            case .blob(let bytes):
                status = bytes.withUnsafeBytes {
                    sqlite3_bind_blob(result, position, $0.baseAddress, Int32($0.count), Self.transient)
                }
            }
            if status != SQLITE_OK {
                sqlite3_finalize(result)
                throw JournalError.storage
            }
        }
        return result
    }

    static func data(_ row: OpaquePointer) throws -> Data {
        let size = Int(sqlite3_column_bytes(row, 0))
        guard size > 0, let value = sqlite3_column_blob(row, 0) else { throw JournalError.storage }
        return Data(bytes: value, count: size)
    }
}

struct LoadedJournal {
    let library: JournalLibraryStatus
    let draft: JournalDraft?
    let editDraft: JournalDraft?
    let media: MediaDirectory
    let captureDirectory: URL
}

struct JournalLibraryStatus {
    var total = 0
    var todayCount = 0
    var canUndoRestore = false
}

struct RestoreResult {
    var additions: Int
    var conflicts: Int
    var favorites: Int
    var changed: Bool
}

private struct DeletedRecord: Codable {
    var record: JournalRecord
    var until: Date
}

private struct RestorePlan {
    let contents: JournalContents
    let result: RestoreResult
}

private struct LegacyConflictGroup: Hashable {
    let timestamp: Int64
    let mood: Mood
    let note: String
    let photoCount: Int

    init(_ record: JournalRecord) {
        timestamp = record.createdAt
        mood = record.mood
        note = record.note
        photoCount = record.imageFileNames.count
    }
}

actor JournalStore {
    static let archivePageSize = 60
    private var database: EncryptedDatabase?
    private var media: MediaDirectory?
    private var root: URL?
    private var draftRevision = 0
    private var editRevision = 0
    private var pendingFiles = Set<String>()
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    func open(today: Date = .now) throws -> LoadedJournal {
        if database == nil {
            let support = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                                       appropriateFor: nil, create: true)
            let folder = support.appending(path: "Journal", directoryHint: .isDirectory)
            try SecureDirectory.make(folder)
            let file = folder.appending(path: "Journal.sqlite")
            let exists = FileManager.default.fileExists(atPath: file.path)
            let key = try DeviceSecrets.randomKey("journal-database", existingStore: exists)
            let mediaKey = try DeviceSecrets.randomKey("journal-attachments", existingStore: exists)
            let mediaFolder = folder.appending(path: "Attachments", directoryHint: .isDirectory)
            try SecureDirectory.make(mediaFolder)
            let db = try EncryptedDatabase(file, key: key)
            try migrateSearchIndex(db)
            let temporary = folder.appending(path: "Capture", directoryHint: .isDirectory)
            let staging = folder.appending(path: "Restore", directoryHint: .isDirectory)
            for url in [temporary, staging] where FileManager.default.fileExists(atPath: url.path) {
                try FileManager.default.removeItem(at: url)
            }
            try SecureDirectory.make(temporary, protection: .complete)
            try SecureDirectory.make(staging)
            database = db
            media = MediaDirectory(root: mediaFolder, key: mediaKey)
            root = folder
            try? prune(db, media: requireMedia())
        }
        let db = try requireDatabase()
        return LoadedJournal(library: try libraryStatus(today: today),
                             draft: try state(JournalDraft.self, "draft", db),
                             editDraft: try state(JournalDraft.self, "edit", db),
                             media: try requireMedia(),
                             captureDirectory: try requireRoot().appending(path: "Capture"))
    }

    func libraryStatus(today: Date) throws -> JournalLibraryStatus {
        let db = try requireDatabase()
        let total = try db.firstInteger("SELECT count(*) FROM journal_entry") ?? 0
        let todayCount: Int64
        if let day = Calendar.xike.dateInterval(of: .day, for: today) {
            todayCount = try db.firstInteger("SELECT count(*) FROM journal_entry WHERE created_at >= ? AND created_at < ?",
                                             [.integer(day.start.epochMillis), .integer(day.end.epochMillis)]) ?? 0
        } else { todayCount = 0 }
        return JournalLibraryStatus(total: Int(total), todayCount: Int(todayCount),
                                    canUndoRestore: try hasState("restore-undo", db))
    }

    func entry(_ id: String) throws -> JournalRecord? {
        try record(id: id, requireDatabase())
    }

    func entries(in interval: DateInterval? = nil) throws -> [JournalRecord] {
        let db = try requireDatabase()
        let (clause, args) = archiveCondition(ArchiveFilter(), interval: interval)
        return try readRecords(db, clause: clause + " ORDER BY created_at DESC, id", arguments: args)
    }

    func archivePage(filter: ArchiveFilter, today: Date, calendar: Calendar, offset: Int) throws -> ArchivePage {
        let db = try requireDatabase()
        let (clause, args) = archiveCondition(filter, interval: filter.interval(today: today, calendar: calendar))
        let records = try readRecords(db, clause: clause + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                                      arguments: args + [.integer(Int64(Self.archivePageSize)), .integer(Int64(offset))])
        let total = try db.firstInteger("SELECT count(*) FROM journal_entry" + clause, args) ?? 0
        return ArchivePage(records: records, total: Int(total))
    }

    func review(period: InsightsPeriod, today: Date, calendar: Calendar) throws -> JournalReview {
        let ranges = period.ranges(today: today, calendar: calendar)
        func slice(_ interval: DateInterval) throws -> ReviewSlice {
            ReviewSlice(start: interval.start, end: interval.end, entries: try entries(in: interval))
        }
        return try JournalReview(current: slice(ranges.current), previous: slice(ranges.previous), period: period, calendar: calendar)
    }

    func calendarCounts(filter: ArchiveFilter, month: Date, calendar: Calendar) throws -> [Date: Int] {
        guard let interval = calendar.dateInterval(of: .month, for: month) else { return [:] }
        let (clause, args) = archiveCondition(filter, interval: interval)
        return try dayCounts(clause: clause, arguments: args, calendar: calendar)
    }

    func memoryDays(today: Date, calendar: Calendar) throws -> [MemoryDay] {
        let monthAgo = calendar.date(byAdding: .month, value: -1, to: today)
        let current = calendar.dateComponents([.year, .month, .day], from: today)
        let counts = try dayCounts(clause: " WHERE created_at < ?", arguments: [.integer(today.epochMillis)], calendar: calendar)
        return counts.compactMap { date, count -> MemoryDay? in
            let day = calendar.dateComponents([.year, .month, .day], from: date)
            let previousYear = (day.year ?? 0) < (current.year ?? 0) && day.month == current.month && day.day == current.day
            let isMonthAgo = date == monthAgo
            return previousYear || isMonthAgo ? MemoryDay(date: date, count: count, isMonthAgo: isMonthAgo) : nil
        }.sorted { $0.date > $1.date }
    }

    private func dayCounts(clause: String, arguments: [BindValue], calendar: Calendar) throws -> [Date: Int] {
        let db = try requireDatabase()
        var counts: [Date: Int] = [:]
        try db.forEachRow("SELECT created_at FROM journal_entry" + clause, arguments) { row in
            let date = Date(timeIntervalSince1970: Double(sqlite3_column_int64(row, 0)) / 1_000)
            counts[calendar.startOfDay(for: date), default: 0] += 1
        }
        return counts
    }

    private func archiveCondition(_ filter: ArchiveFilter, interval: DateInterval?) -> (String, [BindValue]) {
        var conditions: [String] = []
        var arguments: [BindValue] = []
        if let interval {
            conditions.append("created_at >= ? AND created_at < ?")
            arguments += [.integer(interval.start.epochMillis), .integer(interval.end.epochMillis)]
        }
        if filter.hasSearchText {
            guard let expression = JournalSearch.expression(filter.text) else { return (" WHERE 0", []) }
            conditions.append("rowid IN (SELECT docid FROM journal_search WHERE journal_search MATCH ?)")
            arguments.append(.text(expression))
        }
        let moods = Mood.choices.filter { filter.moods.contains($0.score) }
        if !moods.isEmpty {
            let placeholders = Array(repeating: "?", count: moods.count).joined(separator: ",")
            conditions.append("mood IN (\(placeholders))")
            arguments += moods.map { .text($0.rawValue) }
        }
        if filter.photos != .any {
            conditions.append("has_photos = ?")
            arguments.append(.integer(filter.photos == .present ? 1 : 0))
        }
        if filter.favorites { conditions.append("is_favorite = 1") }
        return (conditions.isEmpty ? "" : " WHERE " + conditions.joined(separator: " AND "), arguments)
    }

    func saveDraft(_ draft: JournalDraft?, revision: Int, editing: Bool) throws {
        let db = try requireDatabase()
        let field = editing ? "edit" : "draft"
        guard revision > (editing ? editRevision : draftRevision) else { return }
        if let draft { try setState(draft, field, db) } else { try removeState(field, db) }
        if editing { editRevision = revision } else { draftRevision = revision }
        if let draft { pendingFiles.subtract(draft.files) }
    }

    func save(_ draft: JournalDraft, revision: Int, editing: Bool) throws -> JournalRecord {
        let db = try requireDatabase()
        let original = try record(id: draft.id, db)
        guard !editing || original != nil, let record = draft.record(replacing: editing ? original : nil)
        else { throw JournalError.changed }
        let media = try requireMedia()
        for name in record.attachments.keys { _ = try media.reader(name) }
        try db.transaction {
            if original != record {
                try upsert(record, db)
                try removeState("restore-undo", db)
            }
            try removeState(editing ? "edit" : "draft", db)
        }
        if editing { editRevision = max(editRevision, revision) } else { draftRevision = max(draftRevision, revision) }
        pendingFiles.subtract(draft.files)
        try? prune(db, media: media)
        return record
    }

    func favorite(_ id: String, desired: Bool) throws {
        let db = try requireDatabase()
        guard var record = try record(id: id, db) else { throw JournalError.changed }
        guard record.isFavorite != desired else { return }
        record.isFavorite = desired
        try db.transaction {
            try upsert(record, db)
            try removeState("restore-undo", db)
        }
        try? prune(db, media: requireMedia())
    }

    func delete(_ id: String) throws {
        let db = try requireDatabase()
        guard let record = try record(id: id, db) else { throw JournalError.changed }
        try db.transaction {
            try db.execute("DELETE FROM journal_search WHERE docid=(SELECT rowid FROM journal_entry WHERE id=?)", [.text(id)])
            try db.execute("DELETE FROM journal_entry WHERE id=?", [.text(id)])
            try setState(DeletedRecord(record: record, until: .now.addingTimeInterval(7)), "delete-undo", db)
            try removeState("restore-undo", db)
            if try state(JournalDraft.self, "edit", db)?.id == id { try removeState("edit", db) }
        }
        try? prune(db, media: requireMedia())
    }

    func undoDelete() throws {
        let db = try requireDatabase()
        guard let deleted = try state(DeletedRecord.self, "delete-undo", db), deleted.until > .now,
              try record(id: deleted.record.id, db) == nil else { throw JournalError.changed }
        try db.transaction {
            try upsert(deleted.record, db)
            try removeState("delete-undo", db)
            try removeState("restore-undo", db)
        }
    }

    func tidyAttachments() {
        if let database, let media { try? prune(database, media: media) }
    }

    func importPhoto(_ url: URL) throws -> String {
        let name = try JournalMedia.importPhoto(url, in: requireMedia())
        pendingFiles.insert(name)
        return name
    }

    func importRecording(_ url: URL, duration: Int64) throws -> JournalAudio {
        let audio = try JournalMedia.importRecording(url, duration: duration, in: requireMedia())
        pendingFiles.insert(audio.fileName)
        return audio
    }

    func importVideo(_ url: URL) async throws -> JournalVideo {
        let destination = try requireMedia()
        let stage = try SecureDirectory.temporary(under: requireRoot().appending(path: "Restore"))
        defer { try? FileManager.default.removeItem(at: stage) }
        let media = MediaDirectory(root: stage, key: destination.key)
        let video = try await JournalMedia.importVideo(url, in: media)
        try Task.checkCancellation()
        let manager = FileManager.default
        try manager.moveItem(at: media.path(video.fileName), to: destination.path(video.fileName))
        do {
            try manager.moveItem(at: media.path(video.coverFileName), to: destination.path(video.coverFileName))
        } catch {
            destination.remove(video.fileName)
            throw error
        }
        pendingFiles.formUnion([video.fileName, video.coverFileName])
        return video
    }

    func prepareBackup(_ source: URL, password: String) async throws -> PreparedBackup {
        let _ = try open()
        let stage = try SecureDirectory.temporary(under: requireRoot().appending(path: "Restore"))
        do {
            return try await JournalBackup.inspect(source, password: password, staging: stage,
                                                   mediaKey: requireMedia().key)
        } catch {
            try? FileManager.default.removeItem(at: stage)
            throw error
        }
    }

    func export(password: String?, language: XikeLanguage, verify: Bool = false) throws -> URL {
        let _ = try open()
        let base = try requireRoot().appending(path: "Capture")
        let protection: FileProtectionType = password == nil ? .complete : .completeUntilFirstUserAuthentication
        let folder = try SecureDirectory.temporary(under: base, protection: protection)
        let url = folder.appending(path: password == nil ? "Xike.zip" : "Xike.xike")
        do {
            try JournalBackup.write(contents(requireDatabase()), media: requireMedia(), destination: url,
                                    password: password, language: language, verify: verify)
            return url
        } catch {
            try? FileManager.default.removeItem(at: folder)
            throw error
        }
    }

    func previewMerge(_ prepared: PreparedBackup) throws -> RestoreResult {
        let db = try requireDatabase()
        return try planRestore(prepared, mode: .merge, previous: contents(db), media: requireMedia()).result
    }

    func restore(_ prepared: PreparedBackup, mode: RestoreMode) throws -> RestoreResult {
        let db = try requireDatabase()
        let media = try requireMedia()
        let previous = try contents(db)
        let plan = try planRestore(prepared, mode: mode, previous: previous, media: media)
        guard plan.result.changed else { return plan.result }
        let files = plan.contents.files.intersection(prepared.contents.files)
        var moved: [String] = []
        do {
            for name in files.sorted() {
                try Task.checkCancellation()
                try FileManager.default.moveItem(at: prepared.media.path(name), to: media.path(name))
                moved.append(name)
            }
            try Task.checkCancellation()
            try db.transaction {
                try setState(previous, "restore-undo", db)
                try setState(plan.contents.labelCatalog, "labels", db)
                try replaceEntries(plan.contents.entries, catalog: plan.contents.labelCatalog, db)
                try removeState("delete-undo", db)
                if mode == .replace { try removeState("edit", db) }
            }
        } catch {
            for name in moved {
                if let from = try? media.path(name), let to = try? prepared.media.path(name) {
                    try? FileManager.default.moveItem(at: from, to: to)
                }
            }
            throw error
        }
        try? prune(db, media: media)
        return plan.result
    }

    private func planRestore(_ prepared: PreparedBackup, mode: RestoreMode, previous: JournalContents,
                             media: MediaDirectory) throws -> RestorePlan {
        var incoming = prepared.contents
        let catalog = try combineCatalog(local: previous.labelCatalog, incoming: &incoming, mode: mode)
        let records: [JournalRecord]
        var result: RestoreResult
        switch mode {
        case .merge:
            (records, result) = try merged(previous.entries, incoming.entries, localMedia: media, importedMedia: prepared.media)
        case .replace:
            var seen = Set<String>()
            records = incoming.entries.filter { seen.insert($0.id).inserted }
            result = RestoreResult(additions: records.count, conflicts: 0, favorites: 0, changed: true)
        }
        result.changed = result.changed || catalog != previous.labelCatalog
        return RestorePlan(contents: JournalContents(entries: records, labelCatalog: catalog), result: result)
    }

    func undoRestore() throws {
        let db = try requireDatabase()
        guard let snapshot = try state(JournalContents.self, "restore-undo", db) else { throw JournalError.changed }
        let media = try requireMedia()
        for name in snapshot.files { _ = try media.reader(name) }
        try db.transaction {
            try setState(snapshot.labelCatalog, "labels", db)
            try replaceEntries(snapshot.entries, catalog: snapshot.labelCatalog, db)
            try removeState("restore-undo", db)
            try removeState("edit", db)
        }
        try? prune(db, media: media)
    }

    private func requireDatabase() throws -> EncryptedDatabase {
        guard let database else { throw JournalError.storage }
        return database
    }

    private func requireMedia() throws -> MediaDirectory {
        guard let media else { throw JournalError.storage }
        return media
    }

    private func requireRoot() throws -> URL {
        guard let root else { throw JournalError.storage }
        return root
    }

    private func contents(_ db: EncryptedDatabase) throws -> JournalContents {
        let records = try readRecords(db, clause: " ORDER BY created_at DESC, id")
        return JournalContents(entries: records, labelCatalog: try labelCatalog(db))
    }

    private func readRecords(_ db: EncryptedDatabase, clause: String, arguments: [BindValue] = []) throws -> [JournalRecord] {
        try db.rows("SELECT payload FROM journal_entry" + clause, arguments) {
            try decoder.decode(JournalRecord.self, from: EncryptedDatabase.data($0))
        }
    }

    private func record(id: String, _ db: EncryptedDatabase) throws -> JournalRecord? {
        try readRecords(db, clause: " WHERE id=?", arguments: [.text(id)]).first
    }

    private func labelCatalog(_ db: EncryptedDatabase) throws -> [JournalLabel] {
        try state([JournalLabel].self, "labels", db) ?? JournalLabel.builtin
    }

    private func migrateSearchIndex(_ db: EncryptedDatabase) throws {
        guard try db.firstInteger("PRAGMA user_version") == 1 else { return }
        try db.transaction {
            try db.execute("""
                ALTER TABLE journal_entry ADD COLUMN mood TEXT NOT NULL DEFAULT '';
                ALTER TABLE journal_entry ADD COLUMN has_photos INTEGER NOT NULL DEFAULT 0;
                ALTER TABLE journal_entry ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0;
                CREATE VIRTUAL TABLE journal_search USING fts4(searchable_text, tokenize=unicode61);
                """)
            let labels = Dictionary(uniqueKeysWithValues: try labelCatalog(db).map { ($0.id, $0) })
            var lastRow: Int64 = 0
            while true {
                let page = try db.rows("SELECT payload, rowid FROM journal_entry WHERE rowid > ? ORDER BY rowid LIMIT ?",
                                       [.integer(lastRow), .integer(Int64(Self.archivePageSize))]) { row in
                    (try decoder.decode(JournalRecord.self, from: EncryptedDatabase.data(row)), sqlite3_column_int64(row, 1))
                }
                for (record, rowID) in page {
                    try db.execute("UPDATE journal_entry SET mood=?, has_photos=?, is_favorite=? WHERE id=?",
                                   [.text(record.mood.rawValue), .integer(record.imageFileNames.isEmpty ? 0 : 1),
                                    .integer(record.isFavorite ? 1 : 0), .text(record.id)])
                    try index(record, labels: labels, db)
                    lastRow = rowID
                }
                if page.count < Self.archivePageSize { break }
            }
            try db.execute("PRAGMA user_version=2")
        }
    }

    private func upsert(_ record: JournalRecord, labels: [String: JournalLabel]? = nil, _ db: EncryptedDatabase) throws {
        try db.execute("""
            INSERT INTO journal_entry(id, created_at, payload, mood, has_photos, is_favorite) VALUES(?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET created_at=excluded.created_at, payload=excluded.payload,
                mood=excluded.mood, has_photos=excluded.has_photos, is_favorite=excluded.is_favorite
            """, [.text(record.id), .integer(record.createdAt), .blob(try encoder.encode(record)),
                  .text(record.mood.rawValue), .integer(record.imageFileNames.isEmpty ? 0 : 1), .integer(record.isFavorite ? 1 : 0)])
        let definitions = try labels ?? Dictionary(uniqueKeysWithValues: labelCatalog(db).map { ($0.id, $0) })
        try index(record, labels: definitions, db)
    }

    private func index(_ record: JournalRecord, labels: [String: JournalLabel], _ db: EncryptedDatabase) throws {
        try db.execute("DELETE FROM journal_search WHERE docid=(SELECT rowid FROM journal_entry WHERE id=?)", [.text(record.id)])
        try db.execute("INSERT INTO journal_search(docid, searchable_text) SELECT rowid, ? FROM journal_entry WHERE id=?",
                       [.text(JournalSearch.document(record, labels: labels)), .text(record.id)])
    }

    private func replaceEntries(_ records: [JournalRecord], catalog: [JournalLabel], _ db: EncryptedDatabase) throws {
        try db.execute("DELETE FROM journal_entry")
        try db.execute("DELETE FROM journal_search")
        let labels = Dictionary(uniqueKeysWithValues: catalog.map { ($0.id, $0) })
        for record in records { try upsert(record, labels: labels, db) }
    }

    private func state<T: Decodable>(_ type: T.Type, _ name: String, _ db: EncryptedDatabase) throws -> T? {
        try db.rows("SELECT payload FROM journal_state WHERE name=?", [.text(name)]) {
            try decoder.decode(type, from: EncryptedDatabase.data($0))
        }.first
    }

    private func hasState(_ name: String, _ db: EncryptedDatabase) throws -> Bool {
        try db.rows("SELECT 1 FROM journal_state WHERE name=?", [.text(name)]) { _ in true }.first ?? false
    }

    private func setState<T: Encodable>(_ value: T, _ name: String, _ db: EncryptedDatabase) throws {
        try db.execute("""
            INSERT INTO journal_state(name, payload) VALUES(?, ?)
            ON CONFLICT(name) DO UPDATE SET payload=excluded.payload
            """, [.text(name), .blob(try encoder.encode(value))])
    }

    private func removeState(_ name: String, _ db: EncryptedDatabase) throws {
        try db.execute("DELETE FROM journal_state WHERE name=?", [.text(name)])
    }

    private func prune(_ db: EncryptedDatabase, media: MediaDirectory) throws {
        var references = pendingFiles
        if let deleted = try state(DeletedRecord.self, "delete-undo", db) {
            if deleted.until > .now { references.formUnion(deleted.record.attachments.keys) }
            else { try removeState("delete-undo", db) }
        }
        let names = try FileManager.default.contentsOfDirectory(atPath: media.root.path)
        if names.isEmpty { return }
        try db.forEachRow("SELECT payload FROM journal_entry") { row in
            let record = try decoder.decode(JournalRecord.self, from: EncryptedDatabase.data(row))
            references.formUnion(record.attachments.keys)
        }
        if let previous = try state(JournalContents.self, "restore-undo", db) { references.formUnion(previous.files) }
        for key in ["draft", "edit"] {
            if let draft = try state(JournalDraft.self, key, db) { references.formUnion(draft.files) }
        }
        for name in names where !references.contains(name) { media.remove(name) }
    }

    private func combineCatalog(local: [JournalLabel], incoming: inout JournalContents,
                                mode: RestoreMode) throws -> [JournalLabel] {
        let known = Dictionary(uniqueKeysWithValues: local.map { ($0.id, $0) })
        var definitions = mode == .merge ? known : [:]
        var ids: [String: String] = [:]
        for var label in incoming.labelCatalog {
            if let existing = known[label.id], existing.kind != label.kind {
                let original = label.id
                label.id = "import:" + nameUUID("\(original):\(label.kind.rawValue)")
                ids[original] = label.id
            }
            if let existing = definitions[label.id], existing.kind != label.kind { throw JournalError.invalidBackup }
            if definitions[label.id] == nil { definitions[label.id] = label }
        }
        for builtin in JournalLabel.builtin where definitions[builtin.id] == nil { definitions[builtin.id] = builtin }
        if !ids.isEmpty {
            for index in incoming.entries.indices {
                for labelIndex in incoming.entries[index].labels.indices {
                    let original = incoming.entries[index].labels[labelIndex].id
                    if let id = ids[original] { incoming.entries[index].labels[labelIndex].id = id }
                }
            }
        }
        return definitions.values.sorted(by: JournalLabel.ordered)
    }

    private func merged(_ local: [JournalRecord], _ incoming: [JournalRecord], localMedia: MediaDirectory,
                        importedMedia: MediaDirectory) throws -> ([JournalRecord], RestoreResult) {
        var known = Dictionary(uniqueKeysWithValues: local.map { ($0.id, $0) })
        let originalIds = Set(known.keys)
        var contentKeys: [String: String] = [:]
        var localDigests: [String: String] = [:]
        var incomingDigests: [String: String] = [:]
        var result = RestoreResult(additions: 0, conflicts: 0, favorites: 0, changed: false)
        let marker = "xike-restore-version:"
        var versions: [String: [String]] = [:]
        for id in known.keys where id.hasPrefix(marker) {
            let pieces = id.split(separator: ":", omittingEmptySubsequences: false)
            if pieces.count == 4 { versions[String(pieces[1]), default: []].append(id) }
        }
        let legacyCandidates = Dictionary(grouping: local.filter {
            guard let id = UUID(uuidString: $0.id) else { return false }
            return id.uuid.6 >> 4 == 3
        }, by: LegacyConflictGroup.init)
        var legacyProbeBudget = 1_000_000
        func key(_ entry: JournalRecord) throws -> String {
            if let known = contentKeys[entry.id] { return known }
            let value = try digest(entry, media: localMedia, cache: &localDigests)
            contentKeys[entry.id] = value
            return value
        }
        func mergeFavorite(_ id: String, from entry: JournalRecord) {
            if entry.isFavorite && known[id]?.isFavorite == false {
                known[id]?.isFavorite = true
                if originalIds.contains(id) { result.favorites += 1 }
            }
        }
        func legacyMatch(_ entry: JournalRecord, contentKey: String) throws -> String? {
            let candidates = try (legacyCandidates[LegacyConflictGroup(entry)] ?? []).filter { try key($0) == contentKey }
            if candidates.isEmpty { return nil }
            let ids = Set(candidates.map(\.id))
            // Old Android versions allocated UUID v3 slots within the 16 MiB manifest limit.
            let lastSlot = Int(JournalBackup.manifestLimit / 36)
            for slot in 0...lastSlot {
                guard legacyProbeBudget > 0 else { throw JournalError.limit }
                legacyProbeBudget -= 1
                if slot.isMultiple(of: 1_024) { try Task.checkCancellation() }
                let id = nameUUID("xike-restore-conflict:\(entry.id):\(contentKey):\(slot)")
                if ids.contains(id) { return id }
            }
            return nil
        }
        for record in incoming {
            try Task.checkCancellation()
            let incomingKey = try digest(record, media: importedMedia, cache: &incomingDigests)
            if let existing = known[record.id], try key(existing) == incomingKey {
                mergeFavorite(existing.id, from: record)
                continue
            }
            let origin = Data(SHA256.hash(data: Data(record.id.utf8))).base64EncodedString()
            if let matching = try versions[origin]?.first(where: {
                guard let existing = known[$0] else { return false }
                return try key(existing) == incomingKey
            }) {
                mergeFavorite(matching, from: record)
                continue
            }
            if let matching = try legacyMatch(record, contentKey: incomingKey) {
                mergeFavorite(matching, from: record)
                continue
            }
            var copy = record
            if known[copy.id] != nil {
                var slot = 0
                repeat {
                    copy.id = "\(marker)\(origin):\(incomingKey):\(slot)"
                    slot += 1
                } while known[copy.id] != nil
                result.conflicts += 1
            }
            known[copy.id] = copy
            contentKeys[copy.id] = incomingKey
            if copy.id.hasPrefix(marker) {
                let pieces = copy.id.split(separator: ":", omittingEmptySubsequences: false)
                if pieces.count == 4 { versions[String(pieces[1]), default: []].append(copy.id) }
            }
            result.additions += 1
        }
        result.changed = result.additions > 0 || result.favorites > 0
        return (Array(known.values), result)
    }

    private func digest(_ record: JournalRecord, media: MediaDirectory, cache: inout [String: String]) throws -> String {
        func attachment(_ name: String) throws -> String {
            if let hash = cache[name] { return hash }
            let hash = try media.digest(name)
            cache[name] = hash
            return hash
        }
        var payload: [Any] = [record.createdAt, record.mood.legacyValue, record.tags, record.note]
        payload.append(try record.imageFileNames.map(attachment))
        if let audio = record.audio {
            payload.append([try attachment(audio.fileName), audio.durationMillis, audio.mimeType] as [Any])
        } else { payload.append(NSNull()) }
        if let video = record.video {
            payload.append([try attachment(video.fileName), try attachment(video.coverFileName),
                            video.durationMillis, video.sizeBytes, video.mimeType] as [Any])
        } else { payload.append(NSNull()) }
        if let weather = record.outdoor {
            var row: [Any] = [weather.placeName, weather.temperatureCelsius, weather.weatherCode, weather.capturedAt,
                              weather.source, weather.fullPlaceName.map { $0 as Any } ?? NSNull()]
            if let source = weather.locationSource { row.append(source) }
            payload.append(row)
        } else { payload.append(NSNull()) }
        if record.labels.contains(where: { $0.kind == .activity || $0.id != SavedLabel.legacyTopic($0.savedName).id }) {
            payload.append(record.labels.map { [$0.id, $0.kind.rawValue, $0.savedName] })
        }
        if !record.feelings.isEmpty || record.moodScaleVersion != 0 {
            payload.append(record.feelings)
            payload.append(record.moodScaleVersion)
        }
        let data = try JSONSerialization.data(withJSONObject: payload)
        return Data(SHA256.hash(data: data)).base64EncodedString()
    }
}
