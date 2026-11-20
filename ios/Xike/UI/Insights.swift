import Charts
import SwiftUI

struct InsightsScreen: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Bindable var model: JournalAppModel
    @State private var drilldown: ReviewDetail?
    @State private var local = false
    @State private var ai = false

    var body: some View {
        let review = JournalReview(records: model.records, period: model.insightsPeriod)
        PageScroll {
            VStack(alignment: .leading, spacing: 7) {
                Text(language.text("看看心情的轨迹", "YOUR PATTERNS")).font(.caption.weight(.semibold))
                    .tracking(1.4).foregroundStyle(palette.primary)
                XikeTitle(title: language.text("轨迹", "Patterns"))
                Text(language.text("看看心情如何变化，不急着找原因。", "Notice how your mood changes. There is no rush to find causes."))
                    .font(.subheadline).foregroundStyle(palette.muted)
            }
            Picker(language.text("统计周期", "Period"), selection: $model.insightsPeriod) {
                ForEach(InsightsPeriod.allCases) { Text($0.title(language)).tag($0) }
            }.pickerStyle(.segmented)
            summary(review)
            if !review.current.entries.isEmpty {
                trend(review)
                distribution(review)
                comparison(review)
                dayTypes(review)
                XikeCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Button(language.text("本地回顾", "Local review"), systemImage: "text.alignleft") { local = true }
                        Divider()
                        Button(language.text("AI 回顾", "AI review"), systemImage: "sparkles") { ai = true }
                    }
                }
            } else {
                XikeCard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(language.text("这段时间还没有记录", "No entries in this period")).font(.headline)
                        Text(language.text("记录几次心情后，轨迹会在这里出现。", "Save some moments to begin seeing your patterns."))
                            .font(.subheadline).foregroundStyle(palette.muted)
                    }
                }
            }
        }
        .accessibilityIdentifier("insights-page")
        .sheet(item: $drilldown) { detail in
            RecordListPanel(title: detail.title, model: model, entries: detail.records)
        }
        .sheet(isPresented: $local) { localReview(review) }
        .sheet(isPresented: $ai) { AIReviewPanel(model: model, review: review) }
    }

    private func summary(_ review: JournalReview) -> some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 13) {
                Text(periodText(review.current)).font(.subheadline).foregroundStyle(palette.muted)
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
            }
        }
    }

    private func trend(_ review: JournalReview) -> some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 12) {
                XikeTitle(title: language.text("心情趋势", "Mood trend"), large: false)
                Chart {
                    ForEach(review.trend) { bucket in
                        if let average = bucket.average {
                            LineMark(x: .value("Date", bucket.start), y: .value("Mood", average))
                                .foregroundStyle(palette.primary)
                            PointMark(x: .value("Date", bucket.start), y: .value("Mood", average))
                                .foregroundStyle(palette.primary)
                        }
                    }
                }
                .chartYScale(domain: 1...5)
                .chartYAxis { AxisMarks(values: [1, 2, 3, 4, 5]) }
                .frame(height: 160)
                ScrollView(.horizontal) {
                    HStack(spacing: 11) {
                        ForEach(review.trend.filter { !$0.entries.isEmpty }) { bucket in
                            Button(formatMomentDay(bucket.start, language)) {
                                drilldown = ReviewDetail(title: periodText(bucket), records: bucket.entries)
                            }.font(.caption).frame(minHeight: 44)
                        }
                    }
                }.scrollIndicators(.hidden)
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
                if review.canCompare, let current = review.current.average, let previous = review.previous.average {
                    LabeledContent(language.text("当前 · 上一周期", "Current · Previous"), value: "\(score(current)) · \(score(previous))")
                } else {
                    Text(language.text("两个周期各有至少 3 条记录后，可以对照平均心情。", "Each period needs at least 3 entries to compare average moods."))
                        .font(.footnote).foregroundStyle(palette.muted)
                }
                Button(periodText(review.previous)) {
                    drilldown = ReviewDetail(title: periodText(review.previous), records: review.previous.entries)
                }.font(.footnote).disabled(review.previous.entries.isEmpty)
            }
        }
    }

    private func dayTypes(_ review: JournalReview) -> some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 10) {
                XikeTitle(title: language.text("工作日 / 周末", "Weekdays / Weekends"), large: false)
                dayType(language.text("工作日", "Weekdays"), review.weekdayEntries, days: review.weekdayDays)
                Divider()
                dayType(language.text("周末", "Weekends"), review.weekendEntries, days: review.weekendDays)
            }
        }
    }

    private func dayType(_ name: String, _ entries: [JournalRecord], days: Int) -> some View {
        Button { drilldown = ReviewDetail(title: name, records: entries) } label: {
            let active = Set(entries.map { Calendar.xike.startOfDay(for: $0.date) }).count
            HStack {
                Text(name)
                Spacer()
                Text(language.text("\(entries.count) 条 · \(active)/\(days) 天", "\(entries.count) entries · \(active)/\(days) days"))
                    .foregroundStyle(palette.muted)
            }.font(.subheadline).frame(minHeight: 39)
        }.buttonStyle(.plain).disabled(entries.isEmpty)
    }

    private func localReview(_ review: JournalReview) -> some View {
        let amount = review.current.entries.count
        let text = language.text("这段时间，你在 \(review.recordedDays) 天里留下了 \(amount) 条记录。",
                                 "You saved \(amount) entries across \(review.recordedDays) recorded days.")
        let frequent = Mood.allCases.filter { review.moodCounts[$0] != nil }.sorted {
            review.moodCounts[$0, default: 0] > review.moodCounts[$1, default: 0]
        }
        let distribution = frequent.map {
            let count = review.moodCounts[$0, default: 0]
            let fraction = Double(count) / Double(max(1, amount))
            return language.text("\($0.label(language)) \(count) 条（\(fraction.formatted(.percent.precision(.fractionLength(0))))）",
                                 "\($0.label(language)): \(count) (\(fraction.formatted(.percent.precision(.fractionLength(0)))))")
        }.joined(separator: " · ")
        let report = periodText(review.current) + "\n" + text + "\n" + distribution
        return ModalPage(title: language.text("本地回顾", "Local review")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 13) {
                    Text(periodText(review.current)).font(.footnote).foregroundStyle(palette.muted)
                    Text(text).font(.body)
                    Text(distribution).font(.callout).foregroundStyle(palette.primary)
                    if review.canCompare {
                        Text(language.text("平均心情 \(score(review.current.average))，上一周期 \(score(review.previous.average))。",
                                           "Average mood was \(score(review.current.average)), compared with \(score(review.previous.average)) in the previous period."))
                            .font(.callout)
                    }
                }.textSelection(.enabled)
            }
            ShareLink(item: report) { Label(language.text("分享回顾", "Share review"), systemImage: "square.and.arrow.up") }
        }
    }

    private func metric(_ value: String, _ title: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(value).font(.title2.monospacedDigit()).foregroundStyle(palette.primary)
            Text(title).font(.footnote).foregroundStyle(palette.muted)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }

    private func periodText(_ slice: ReviewSlice) -> String {
        let end = Calendar.xike.date(byAdding: .day, value: -1, to: slice.end) ?? slice.end
        let format = Date.FormatStyle.dateTime.month().day().locale(language.locale)
        return "\(slice.start.formatted(format)) – \(end.formatted(format))"
    }

    private func score(_ value: Double?) -> String { value.map { $0.formatted(.number.precision(.fractionLength(1))) } ?? "—" }
}

private struct ReviewDetail: Identifiable {
    let id = UUID()
    var title: String
    var records: [JournalRecord]
}
