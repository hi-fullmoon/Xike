import Charts
import SwiftUI

private struct InsightsRequest: Equatable {
    let period: InsightsPeriod
    let day: Date
    let calendar: Calendar
    let revision: Int
    let retry: Int
}

struct InsightsScreen: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Bindable var model: JournalAppModel
    @State private var drilldown: ReviewDetail?
    @State private var local = false
    @State private var ai = false
    @State private var review: JournalReview?
    @State private var loadedRequest: InsightsRequest?
    @State private var readError: String?
    @State private var retry = 0

    var body: some View {
        let request = InsightsRequest(period: model.insightsPeriod, day: model.today, calendar: .xike,
                                      revision: model.libraryRevision, retry: retry)
        PageScroll {
            VStack(alignment: .leading, spacing: 7) {
                Text(language.text("细小的痕迹", "THE LITTLE TRACES")).font(.caption.weight(.semibold))
                    .tracking(1.4).foregroundStyle(palette.primary)
                HiddenSettingsTitle(title: language.text("轨迹", "Patterns")) {
                    if loadedRequest == request && review != nil { ai = true }
                }.accessibilityIdentifier("ai-review-entry")
                Text(language.text("看看心情如何变化，不急着找原因。", "Notice how your mood changes. There is no rush to find causes."))
                    .font(.subheadline).foregroundStyle(palette.muted)
            }
            Picker(language.text("统计周期", "Period"), selection: $model.insightsPeriod) {
                ForEach(InsightsPeriod.allCases) { Text($0.title(language)).tag($0) }
            }.pickerStyle(.segmented)
            if loadedRequest != request {
                ProgressView(language.text("正在读取记录…", "Loading entries…"))
            } else if let review {
                VStack(alignment: .leading, spacing: palette.gap) {
                    summary(review)
                    if !review.current.entries.isEmpty {
                        trend(review)
                        distribution(review)
                        comparison(review)
                        dayTypes(review)
                    } else {
                        XikeCard {
                            Text(language.text("这段时间还没有记录", "No entries in this period")).font(.headline)
                        }
                    }
                    XikeCard {
                        Button(language.text("本地回顾", "Local review"), systemImage: "text.alignleft") { local = true }
                            .disabled(review.current.entries.isEmpty)
                            .accessibilityIdentifier("local-review")
                    }
                }
            } else if let readError {
                Text(readError).font(.footnote).foregroundStyle(palette.muted)
                Button(language.text("重试", "Retry")) { retry += 1 }
            }
        }
        .accessibilityIdentifier("insights-page")
        .task(id: request) {
            do {
                let result = try await model.store.review(period: request.period, today: request.day, calendar: request.calendar)
                try Task.checkCancellation()
                review = result
                readError = nil
                loadedRequest = request
            } catch {
                if !Task.isCancelled {
                    review = nil
                    readError = model.message(for: error)
                    loadedRequest = request
                }
            }
        }
        .sheet(item: $drilldown) { detail in
            RecordListPanel(title: detail.title, model: model, entries: detail.records)
        }
        .sheet(isPresented: $local) {
            if let review {
                LocalReviewPanel(report: LocalJournalReview(review, period: model.insightsPeriod, language: language), model: model)
            }
        }
        .sheet(isPresented: $ai) {
            if let review { AIReviewPanel(model: model, review: review) }
        }
    }

    private func summary(_ review: JournalReview) -> some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 13) {
                Text(review.current.dateRange(language)).font(.subheadline).foregroundStyle(palette.muted)
                ViewThatFits {
                    HStack(alignment: .top, spacing: 17) {
                        metric(String(review.current.entries.count), language.text("记录", "Entries"))
                        metric("\(review.recordedDays)/\(review.elapsedDays)", language.text("记录天数", "Days recorded"))
                        metric(score(review.current.average), language.text("平均心情", "Average mood"))
                    }
                    VStack(alignment: .leading, spacing: 10) {
                        metric(String(review.current.entries.count), language.text("记录", "Entries"))
                        metric("\(review.recordedDays)/\(review.elapsedDays)", language.text("记录天数", "Days recorded"))
                        metric(score(review.current.average), language.text("平均心情", "Average mood"))
                    }
                }
                ProgressView(value: review.coverage).accessibilityLabel(language.text("记录覆盖度", "Recording coverage"))
                Text(language.text("统计反映已记录的日子。", "These patterns reflect the days you recorded."))
                    .font(.footnote).foregroundStyle(palette.muted)
                Button(language.text("查看本期记录", "View this period’s entries")) {
                    drilldown = ReviewDetail(title: review.current.dateRange(language), records: review.current.entries)
                }.frame(minHeight: 44).disabled(review.current.entries.isEmpty)
            }
        }
    }

    private func trend(_ review: JournalReview) -> some View {
        let segments = review.trendSegments
        return XikeCard {
            VStack(alignment: .leading, spacing: 12) {
                XikeTitle(title: language.text("心情趋势", "Mood trend"), large: false)
                if !review.canDescribePatterns {
                    Text(language.text("目前记录较少，只展示已记录的心情。", "There are few entries. The chart shows recorded moods only."))
                        .font(.footnote).foregroundStyle(palette.muted)
                }
                Chart {
                    ForEach(segments.indices, id: \.self) { index in
                        ForEach(segments[index]) { bucket in
                            if let average = bucket.average {
                                LineMark(x: .value("Date", bucket.start), y: .value("Mood", average), series: .value("Segment", index))
                                    .foregroundStyle(palette.primary)
                                PointMark(x: .value("Date", bucket.start), y: .value("Mood", average))
                                    .foregroundStyle(palette.primary)
                            }
                        }
                    }
                }
                .chartXScale(domain: review.current.start...(review.trend.last?.end ?? review.current.end))
                .chartYScale(domain: 1.0...5.0)
                .chartYAxis { AxisMarks(values: [1.0, 3.0, 5.0]) { value in
                    AxisGridLine()
                    AxisValueLabel {
                        if let score = value.as(Double.self), let mood: Mood = [1: .low, 3: .calm, 5: .joyful][score] {
                            Text(mood.label(language))
                        }
                    }
                } }
                .frame(height: 160)
                ScrollView(.horizontal) {
                    HStack(spacing: 11) {
                        ForEach(review.trend.filter { !$0.entries.isEmpty }) { bucket in
                            Button(formatMomentDay(bucket.start, language)) {
                                drilldown = ReviewDetail(title: bucket.dateRange(language), records: bucket.entries)
                            }.font(.caption).frame(minHeight: 44)
                        }
                    }
                }.scrollIndicators(.hidden)
                Text(language.text("没有记录的时段会断开。点按日期可查看原始记录。", "Gaps mark periods with no entries. Tap a date to see its entries."))
                    .font(.footnote).foregroundStyle(palette.muted)
            }
        }
    }

    private func distribution(_ review: JournalReview) -> some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 9) {
                XikeTitle(title: language.text("心情分布", "Mood distribution"), large: false)
                ForEach(Mood.allCases.filter { mood in
                    review.moodCounts[mood] != nil || review.previous.entries.contains(where: { $0.mood == mood })
                }) { mood in
                    let group = review.current.entries.filter { $0.mood == mood }
                    Button {
                        drilldown = ReviewDetail(title: mood.label(language), records: group)
                    } label: {
                        HStack(spacing: 10) {
                            MoodArtwork(mood: mood, size: 24)
                            Text(mood.label(language)).font(.footnote).frame(minWidth: 60, alignment: .leading)
                            ProgressView(value: Double(group.count) / Double(review.current.entries.count))
                            Text(String(group.count)).font(.footnote.monospacedDigit()).foregroundStyle(palette.muted)
                        }.frame(minHeight: 42).contentShape(Rectangle())
                    }.buttonStyle(.plain).disabled(group.isEmpty)
                }
            }
        }
    }

    private func comparison(_ review: JournalReview) -> some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 10) {
                XikeTitle(title: language.text("与上一周期对照", "Previous period"), large: false)
                comparisonPeriod(review.current, title: language.text("本期", "Current period"), showAverage: review.canCompare)
                comparisonPeriod(review.previous, title: language.text("前期", "Previous period"), showAverage: review.canCompare)
                if review.canCompare, let current = review.current.average, let previous = review.previous.average {
                    let change = (current - previous).formatted(.number.sign(strategy: .always()).precision(.fractionLength(1)))
                    Text(language.text("平均心情变化 \(change)", "Change in average mood: \(change)"))
                        .font(.footnote).foregroundStyle(palette.muted)
                } else {
                    Text(language.text("两个周期各有至少 3 条记录后，可以对照平均心情。", "Each period needs at least 3 entries to compare average moods."))
                        .font(.footnote).foregroundStyle(palette.muted)
                }
            }
        }
    }

    private func comparisonPeriod(_ slice: ReviewSlice, title: String, showAverage: Bool) -> some View {
        let days = Set(slice.entries.map { Calendar.xike.startOfDay(for: $0.date) }).count
        return VStack(alignment: .leading, spacing: 4) {
            Button("\(title) · \(slice.dateRange(language))") {
                drilldown = ReviewDetail(title: slice.dateRange(language), records: slice.entries)
            }.disabled(slice.entries.isEmpty)
            Text(language.text("\(slice.entries.count) 条 · \(days) 天", "\(slice.entries.count) entries · \(days) days"))
            if showAverage { Text(language.text("平均心情 \(score(slice.average))", "Average mood \(score(slice.average))")) }
        }.font(.footnote)
    }

    private func dayTypes(_ review: JournalReview) -> some View {
        let enough = review.weekdayEntries.count >= 3 && review.weekendEntries.count >= 3
        return XikeCard {
            VStack(alignment: .leading, spacing: 10) {
                XikeTitle(title: language.text("工作日 / 周末", "Weekdays / Weekends"), large: false)
                dayType(language.text("工作日", "Weekdays"), review.weekdayEntries, days: review.weekdayDays, showAverage: enough)
                Divider()
                dayType(language.text("周末", "Weekends"), review.weekendEntries, days: review.weekendDays, showAverage: enough)
                if !enough {
                    Text(language.text("工作日和周末各有至少 3 条记录后，再比较均值。", "At least 3 entries in each group are needed to compare averages."))
                        .font(.footnote).foregroundStyle(palette.muted)
                }
            }
        }
    }

    private func dayType(_ name: String, _ entries: [JournalRecord], days: Int, showAverage: Bool) -> some View {
        Button { drilldown = ReviewDetail(title: name, records: entries) } label: {
            let active = Set(entries.map { Calendar.xike.startOfDay(for: $0.date) }).count
            VStack(alignment: .leading, spacing: 4) {
                Text(name)
                Text(language.text("\(entries.count) 条 · \(active)/\(days) 天", "\(entries.count) entries · \(active)/\(days) days"))
                    .foregroundStyle(palette.muted)
                if showAverage {
                    let average = Double(entries.reduce(0) { $0 + $1.mood.score }) / Double(entries.count)
                    Text(language.text("平均心情 \(score(average))", "Average mood \(score(average))")).foregroundStyle(palette.muted)
                }
            }.font(.subheadline).frame(minHeight: 39)
        }.buttonStyle(.plain).disabled(entries.isEmpty)
    }

    private func metric(_ value: String, _ title: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(value).font(.title2.monospacedDigit()).foregroundStyle(palette.primary)
            Text(title).font(.footnote).foregroundStyle(palette.muted)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }

    private func score(_ value: Double?) -> String { value.map { $0.formatted(.number.precision(.fractionLength(1))) } ?? "—" }
}

private struct LocalReviewPanel: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    let report: LocalJournalReview
    let model: JournalAppModel
    @State private var source: LocalReviewSource?

    var body: some View {
        ModalPage(title: language.text("本地回顾", "Local review")) {
            XikeTitle(title: report.title, large: false)
            Text(report.range).font(.footnote).foregroundStyle(palette.muted)
            ForEach(report.sections) { section in
                XikeCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(section.title).font(.headline)
                        Text(section.body).textSelection(.enabled)
                        ForEach(section.sources) { item in
                            Button(item.title) { source = item }.frame(minHeight: 44)
                                .accessibilityIdentifier("local-review-source")
                        }
                    }
                }
            }
            ShareLink(item: report.text) { Label(language.text("分享回顾", "Share review"), systemImage: "square.and.arrow.up") }
        }.sheet(item: $source) { item in
            RecordListPanel(title: item.title, model: model, entries: item.entries)
        }
    }
}

private struct ReviewDetail: Identifiable {
    let id = UUID()
    var title: String
    var records: [JournalRecord]
}
