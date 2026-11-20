import Foundation

struct LocalReviewSource: Identifiable {
    var id: String { title }
    let title: String
    let entries: [JournalRecord]
}

struct LocalReviewSection: Identifiable {
    var id: String { title }
    let title: String
    let body: String
    var sources: [LocalReviewSource] = []
}

struct LocalJournalReview {
    let title: String
    let range: String
    let sections: [LocalReviewSection]

    var text: String {
        ([title + "\n" + range] + sections.map { $0.title + "\n" + $0.body }).joined(separator: "\n\n")
    }

    init(_ review: JournalReview, period: InsightsPeriod, language: XikeLanguage, calendar: Calendar = .xike) {
        let periodName = period.title(language)
        title = language.text("息刻 · \(periodName)本地回顾", "Xike · Local review: \(periodName)")
        range = review.current.dateRange(language, includeYear: true, calendar: calendar)
        let count = review.current.entries.count
        let moods = Mood.allCases.filter { review.moodCounts[$0, default: 0] > 0 }
            .sorted { review.moodCounts[$0, default: 0] > review.moodCounts[$1, default: 0] }
        let leading = moods.filter { review.moodCounts[$0] == moods.first.flatMap { review.moodCounts[$0] } }
        let coverage = Self.percent(review.coverage)
        var overview = language.text(
            "这段时间留下了 \(count) 条记录，涉及 \(review.recordedDays) 天，占已过去 \(review.elapsedDays) 天的 \(coverage)%。",
            "You kept \(count) entries across \(review.recordedDays) days, covering \(coverage)% of the \(review.elapsedDays) elapsed days.")
        if !review.canDescribePatterns {
            overview += language.text("目前记录较少或集中在一天，以下只整理已记录的事实。",
                                      " Entries are few or concentrated on one day. This review describes recorded facts only.")
        } else if leading.count == moods.count && moods.count > 1 {
            overview += language.text("各类心情出现的次数相同。", " All recorded moods occur equally often.")
        } else {
            let names = leading.map { $0.label(language) }.joined(separator: language.text("、", ", "))
            overview += language.text("最常出现的心情是\(names)。", " Most frequently recorded: \(names).")
        }
        let sources = count > 0 ? [LocalReviewSource(title: language.text("查看本期记录", "View this period’s entries"),
                                                     entries: review.current.entries)] : []
        var report = [LocalReviewSection(title: language.text("这段时间的概况", "Period overview"), body: overview, sources: sources)]
        if count > 0 {
            let distribution = moods.map { mood in
                let amount = review.moodCounts[mood, default: 0]
                let share = Self.percent(Double(amount) / Double(count))
                return language.text("\(mood.label(language)) \(amount) 次（\(share)%）", "\(mood.label(language)): \(amount) (\(share)%)")
            }.joined(separator: " · ")
            let explanation = review.canDescribePatterns
                ? language.text("回看片段以每天的心情均值为依据。", "Moments to revisit are selected using recorded daily mood averages.")
                : language.text("目前仅展示已记录的心情分布。", "The distribution describes recorded moods only.")
            report.append(LocalReviewSection(title: language.text("心情怎样变化", "Mood over time"), body: distribution + "\n" + explanation))
            report.append(Self.comparison(review, language: language, calendar: calendar))
            if review.canDescribePatterns { report.append(Self.highlights(review, language: language, calendar: calendar)) }
            report.append(LocalReviewSection(title: language.text("留给自己的一个问题", "A question for reflection"),
                body: language.text("回看其中一条记录：当时发生了什么，哪些细节是你现在仍想记住的？",
                                    "Revisit one entry. What was happening, and which details would you still like to remember?")))
        }
        report.append(LocalReviewSection(title: language.text("这份回顾的范围", "Scope of this review"),
            body: language.text("回顾依据本机记录的日期和心情生成。没有记录的日期不参与统计。",
                                "This on-device review uses recorded dates and moods. Days without entries are excluded.")))
        sections = report
    }

    private static func comparison(_ review: JournalReview, language: XikeLanguage, calendar: Calendar) -> LocalReviewSection {
        let previous = review.previous.entries
        let previousDays = Set(previous.map { calendar.startOfDay(for: $0.date) }).count
        let range = review.previous.dateRange(language, includeYear: true, calendar: calendar)
        let currentCount = review.current.entries.count
        var text = language.text(
            "对照时段 \(range)。本期 \(currentCount) 条、\(review.recordedDays) 天，前期 \(previous.count) 条、\(previousDays) 天。",
            "Comparison window \(range). Current: \(currentCount) entries across \(review.recordedDays) days. Previous: \(previous.count) across \(previousDays) days.")
        if review.canDescribePatterns && review.canCompare && previousDays >= 2 {
            let previousCounts = Dictionary(grouping: previous, by: \.mood).mapValues(\.count)
            let changes = Mood.allCases.map { mood in
                let currentShare = Double(review.moodCounts[mood, default: 0]) / Double(currentCount)
                let previousShare = Double(previousCounts[mood, default: 0]) / Double(previous.count)
                return (mood, percent(currentShare) - percent(previousShare))
            }.filter { $0.1 != 0 }.sorted { abs($0.1) > abs($1.1) }.prefix(2)
            if changes.isEmpty {
                text += language.text("两期心情占比按整数百分比看没有变化。", " Mood shares are unchanged at whole-percentage precision.")
            } else {
                text += "\n" + changes.map { mood, delta in
                    let direction = delta > 0 ? language.text("增加", "increased") : language.text("减少", "decreased")
                    return language.text("\(mood.label(language))占比\(direction) \(abs(delta)) 个百分点。",
                                         "\(mood.label(language)) share \(direction) by \(abs(delta)) percentage points.")
                }.joined(separator: language.text("", " "))
            }
        } else {
            text += language.text("两期各有至少 3 条记录且覆盖 2 天后，再解读周期变化。",
                                  "Each period needs at least three entries across two days to interpret changes.")
        }
        let sources = previous.isEmpty ? [] : [LocalReviewSource(title: language.text("查看对照时段记录", "View comparison entries"), entries: previous)]
        return LocalReviewSection(title: language.text("与上一阶段相比", "Compared with the previous period"), body: text, sources: sources)
    }

    private static func highlights(_ review: JournalReview, language: XikeLanguage, calendar: Calendar) -> LocalReviewSection {
        let days = Dictionary(grouping: review.current.entries, by: { calendar.startOfDay(for: $0.date) })
        let scores = days.mapValues { entries in Double(entries.reduce(0) { $0 + $1.mood.score }) / Double(entries.count) }
        let title = language.text("值得回看的片段", "Moments to revisit")
        guard let high = scores.values.max(), let low = scores.values.min(), high > low else {
            return LocalReviewSection(title: title, body: language.text("有记录日期的日均心情相同，可以从本期记录中选择想回看的片段。",
                "The recorded days have equal mood averages. Choose a moment from this period to revisit."))
        }
        let higher = scores.keys.filter { scores[$0] == high }.sorted()
        let lower = scores.keys.filter { scores[$0] == low }.sorted()
        let highDate = formatMomentDay(higher[0], language)
        let lowDate = formatMomentDay(lower[0], language)
        let text = language.text(
            "按有记录日期的日均心情，\(highDate)是较高的一天（共 \(higher.count) 天），\(lowDate)是较低的一天（共 \(lower.count) 天）。点开可查看这些日期的全部记录。",
            "By recorded daily mood averages, \(highDate) was a higher day (\(higher.count) days), and \(lowDate) was a lower day (\(lower.count) days). Open either group to see all entries from those dates.")
        return LocalReviewSection(title: title, body: text, sources: [
            LocalReviewSource(title: language.text("回看较高的日子", "Revisit higher days"), entries: higher.flatMap { days[$0] ?? [] }),
            LocalReviewSource(title: language.text("回看较低的日子", "Revisit lower days"), entries: lower.flatMap { days[$0] ?? [] }),
        ])
    }

    private static func percent(_ ratio: Double) -> Int { Int((100 * ratio).rounded()) }
}
