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

enum ArchiveDatePreset: String, CaseIterable, Identifiable {
    case all, last7, last30, year
    var id: Self { self }

    func label(_ language: XikeLanguage) -> String {
        switch self {
        case .all: language.text("全部日期", "All dates")
        case .last7: language.text("近 7 天", "Last 7 days")
        case .last30: language.text("近 30 天", "Last 30 days")
        case .year: language.text("今年", "This year")
        }
    }

    func interval(today: Date, calendar: Calendar) -> DateInterval? {
        let start: Date?
        switch self {
        case .all: return nil
        case .last7: start = calendar.date(byAdding: .day, value: -6, to: today)
        case .last30: start = calendar.date(byAdding: .day, value: -29, to: today)
        case .year: start = calendar.dateInterval(of: .year, for: today)?.start
        }
        guard let start, let end = calendar.date(byAdding: .day, value: 1, to: today) else { return nil }
        return DateInterval(start: start, end: end)
    }
}

struct ArchiveFilter: Equatable {
    var text = ""
    var moods = Set<Int>()
    var datePreset = ArchiveDatePreset.all
    var day: Date?
    var photos = PhotoFilter.any
    var favorites = false

    var extraCount: Int {
        [!moods.isEmpty, datePreset != .all || day != nil, photos != .any, favorites].count(where: { $0 })
    }

    var hasSearchText: Bool { !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    func interval(today: Date, calendar: Calendar) -> DateInterval? {
        if let day { return calendar.dateInterval(of: .day, for: day) }
        return datePreset.interval(today: today, calendar: calendar)
    }
}

enum JournalSearch {
    private static let cjk = #/[\u{3400}-\u{9FFF}]+/#
    private static let word = #/[\p{L}\p{N}]+/#
    private static let translatedTopics = Dictionary(uniqueKeysWithValues:
        zip(JournalLabel.topicsInEnglish.map { $0.lowercased() }, JournalLabel.icons))

    static func document(_ record: JournalRecord, labels: [String: JournalLabel]) -> String {
        let names = record.labels.flatMap { label in
            label.searchNames + [labels[label.id]?.name].compactMap { $0 }
        }
        let source = ([record.note] + names).joined(separator: " ")
        let tokens = source.matches(of: cjk).flatMap { run -> [String] in
            let characters = run.output.map(String.init)
            return characters + zip(characters, characters.dropFirst()).map { first, second in first + second }
        }
        return ([source] + tokens).joined(separator: " ")
    }

    static func expression(_ text: String) -> String? {
        func prefix(_ term: String) -> String { "\"\(term)*\"" }
        let terms = text.matches(of: word).map { match -> String in
            let word = String(match.output)
            let translated = JournalLabel.translatedAliases[word.lowercased()] ?? translatedTopics[word.lowercased()] ?? word
            let canonical = JournalLabel.topicAliases[translated] ?? translated
            let aliases = JournalLabel.topicAliases.filter { $0.value == canonical }.keys.sorted()
            let expansions = [canonical] + aliases.filter { $0 != word }
            let alternatives = [word] + expansions.filter { $0 != word }
            if alternatives.count > 1 { return "(" + alternatives.map(prefix).joined(separator: " OR ") + ")" }
            if word.count > 2 && word.wholeMatch(of: cjk) != nil {
                return word.map { prefix(String($0)) }.joined(separator: " AND ")
            }
            return prefix(word)
        }
        return terms.isEmpty ? nil : terms.joined(separator: " AND ")
    }
}

struct ArchivePage {
    var records: [JournalRecord]
    var total: Int
}

struct MemoryDay: Identifiable {
    var id: Date { date }
    let date: Date
    let count: Int
    let isMonthAgo: Bool
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

    func ranges(today: Date, calendar: Calendar) -> (current: DateInterval, previous: DateInterval) {
        let today = calendar.startOfDay(for: today)
        func addDays(_ amount: Int, to date: Date) -> Date {
            calendar.date(byAdding: .day, value: amount, to: date) ?? date
        }
        let end = addDays(1, to: today)
        let start: Date
        let previousStart: Date
        let previousEnd: Date
        switch self {
        case .week:
            let weekday = (calendar.component(.weekday, from: today) + 5) % 7
            start = addDays(-weekday, to: today)
            previousStart = addDays(-7, to: start)
            previousEnd = addDays(-7, to: end)
        case .days30, .days90:
            let amount = self == .days30 ? 30 : 90
            start = addDays(-amount, to: end)
            previousStart = addDays(-amount, to: start)
            previousEnd = start
        case .year:
            start = calendar.dateInterval(of: .year, for: today)?.start ?? today
            let equivalent = calendar.date(byAdding: .year, value: -1, to: today) ?? today
            previousStart = calendar.dateInterval(of: .year, for: equivalent)?.start ?? equivalent
            previousEnd = addDays(1, to: equivalent)
        }
        return (DateInterval(start: start, end: end), DateInterval(start: previousStart, end: previousEnd))
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

    func dateRange(_ language: XikeLanguage, includeYear: Bool = false, calendar: Calendar = .xike) -> String {
        let lastDay = calendar.date(byAdding: .day, value: -1, to: end) ?? end
        var format = Date.FormatStyle.dateTime.month().day().locale(language.locale)
        if includeYear { format = format.year() }
        return "\(start.formatted(format)) – \(lastDay.formatted(format))"
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

    var trendSegments: [[ReviewSlice]] {
        var segments: [[ReviewSlice]] = []
        var contiguous = false
        for bucket in trend {
            if bucket.entries.isEmpty {
                contiguous = false
            } else if contiguous {
                segments[segments.count - 1].append(bucket)
            } else {
                segments.append([bucket])
                contiguous = true
            }
        }
        return segments
    }

    init(current: ReviewSlice, previous: ReviewSlice, period: InsightsPeriod, calendar: Calendar) {
        self.current = current
        self.previous = previous
        let start = current.start
        let end = current.end
        func day(_ day: Date, _ offset: Int) -> Date { calendar.date(byAdding: .day, value: offset, to: day) ?? day }
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
            let entries = currentRecords.filter { $0.date >= cursor && $0.date < bound }
            trend.append(ReviewSlice(start: cursor, end: bound, entries: entries))
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
