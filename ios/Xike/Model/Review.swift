import Foundation

enum PhotoFilter: String, CaseIterable, Identifiable {
    case any, present, absent
    var id: Self { self }
    func label(_ language: XikeLanguage) -> String {
        switch self {
        case .any: language.text("全部", "All")
        case .present: language.text("有照片", "With photos")
        case .absent: language.text("无照片", "Without photos")
        }
    }
}

struct ArchiveFilter: Equatable {
    var text = ""
    var moods = Set<Int>()
    var from: Date?
    var to: Date?
    var photos = PhotoFilter.any
    var favorites = false

    var extraCount: Int {
        [!moods.isEmpty, from != nil || to != nil, photos != .any, favorites].count(where: { $0 })
    }

    func matches(_ entry: JournalRecord) -> Bool {
        let terms = text.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        guard terms.allSatisfy({ term in
            entry.note.range(of: term, options: [.caseInsensitive, .diacriticInsensitive]) != nil
                || entry.labels.flatMap(\.searchNames).contains {
                    $0.range(of: term, options: [.caseInsensitive, .diacriticInsensitive]) != nil
                }
        }), moods.isEmpty || moods.contains(entry.mood.score), !favorites || entry.isFavorite else { return false }
        let calendar = Calendar.xike
        let date = calendar.startOfDay(for: entry.date)
        if let from, date < calendar.startOfDay(for: from) { return false }
        if let to, date > calendar.startOfDay(for: to) { return false }
        return photos == .any || (photos == .present) == !entry.imageFileNames.isEmpty
    }

    static func memories(_ records: [JournalRecord], today: Date = .now) -> [JournalRecord] {
        let calendar = Calendar.xike
        let oneMonthAgo = calendar.date(byAdding: .month, value: -1, to: today)
        let components = calendar.dateComponents([.year, .month, .day], from: today)
        return records.filter { record in
            let date = calendar.dateComponents([.year, .month, .day], from: record.date)
            let earlierYear = (date.year ?? 0) < (components.year ?? 0)
            return (earlierYear && date.month == components.month && date.day == components.day)
                || (oneMonthAgo.map { calendar.isDate($0, inSameDayAs: record.date) } ?? false)
        }
    }
}

enum InsightsPeriod: String, CaseIterable, Identifiable {
    case week, days30, days90, year
    var id: Self { self }
    func title(_ language: XikeLanguage) -> String {
        switch self {
        case .week: language.text("本周", "This week")
        case .days30: language.text("近30天", "30 days")
        case .days90: language.text("近90天", "90 days")
        case .year: language.text("今年", "This year")
        }
    }
}

struct ReviewSlice: Identifiable {
    var id: Date { start }
    var start: Date
    var end: Date
    var entries: [JournalRecord]
    var average: Double? {
        entries.isEmpty ? nil : Double(entries.reduce(0) { $0 + $1.mood.score }) / Double(entries.count)
    }
}

struct JournalReview {
    var current: ReviewSlice
    var previous: ReviewSlice
    var trend: [ReviewSlice]
    var weekdayEntries: [JournalRecord]
    var weekendEntries: [JournalRecord]
    var recordedDays: Int
    var elapsedDays: Int
    var weekdayDays: Int
    var weekendDays: Int
    var moodCounts: [Mood: Int]
    var canDescribePatterns: Bool { current.entries.count >= 3 && recordedDays >= 2 }

    var canCompare: Bool { current.entries.count >= 3 && previous.entries.count >= 3 }
    var coverage: Double { elapsedDays > 0 ? Double(recordedDays) / Double(elapsedDays) : 0 }

    init(records: [JournalRecord], period: InsightsPeriod, today: Date = .now) {
        let calendar = Calendar.xike
        let today = calendar.startOfDay(for: today)
        func day(_ day: Date, _ offset: Int) -> Date { calendar.date(byAdding: .day, value: offset, to: day) ?? day }
        let end = day(today, 1)
        let start: Date
        let previousStart: Date
        let previousEnd: Date
        switch period {
        case .week:
            let weekday = (calendar.component(.weekday, from: today) + 5) % 7
            start = day(today, -weekday)
            previousStart = day(start, -7)
            previousEnd = day(end, -7)
        case .days30, .days90:
            let amount = period == .days30 ? 30 : 90
            start = day(end, -amount)
            previousStart = day(start, -amount)
            previousEnd = start
        case .year:
            start = calendar.dateInterval(of: .year, for: today)?.start ?? today
            let previous = calendar.date(byAdding: .year, value: -1, to: today) ?? today
            previousStart = calendar.dateInterval(of: .year, for: previous)?.start ?? previous
            previousEnd = day(previous, 1)
        }
        func slice(_ start: Date, _ end: Date, records: [JournalRecord]) -> ReviewSlice {
            ReviewSlice(start: start, end: end, entries: records.filter { $0.date >= start && $0.date < end })
        }
        current = slice(start, end, records: records)
        previous = slice(previousStart, previousEnd, records: records)
        let currentRecords = current.entries
        trend = []
        var cursor = start
        let trendEnd = period == .week ? day(start, 7) : end
        while cursor < trendEnd {
            let next: Date
            switch period {
            case .week: next = day(cursor, 1)
            case .days30: next = day(cursor, 5)
            case .days90: next = day(cursor, 10)
            case .year: next = calendar.date(byAdding: .month, value: 1, to: cursor) ?? trendEnd
            }
            let bound = min(next, trendEnd)
            trend.append(slice(cursor, bound, records: currentRecords))
            cursor = bound
        }
        func weekend(_ date: Date) -> Bool { [1, 7].contains(calendar.component(.weekday, from: date)) }
        weekdayEntries = currentRecords.filter { !weekend($0.date) }
        weekendEntries = currentRecords.filter { weekend($0.date) }
        recordedDays = Set(currentRecords.map { calendar.startOfDay(for: $0.date) }).count
        elapsedDays = calendar.dateComponents([.day], from: start, to: end).day ?? 0
        weekendDays = (0..<max(0, elapsedDays)).count(where: { weekend(day(start, $0)) })
        weekdayDays = elapsedDays - weekendDays
        moodCounts = Dictionary(grouping: currentRecords, by: \.mood).mapValues(\.count)
    }
}
