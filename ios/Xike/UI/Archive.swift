import SwiftUI

private struct ArchiveRequest: Equatable {
    let filter: ArchiveFilter
    let date: Date
    let calendar: Calendar
    let revision: Int
    let retry: Int
}

struct ArchiveScreen: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Bindable var model: JournalAppModel
    @State private var showFilter = false
    @State private var showMemories = false
    @State private var memoryDays: [MemoryDay] = []
    @State private var selected: RecordLink?
    @State private var month = Date.now
    @State private var page: ArchivePage?
    @State private var loadedRequest: ArchiveRequest?
    @State private var counts: [Date: Int] = [:]
    @State private var error: String?
    @State private var calendarError: String?
    @State private var retry = 0
    @State private var more: Task<Void, Never>?

    private var request: ArchiveRequest {
        ArchiveRequest(filter: model.archiveFilter, date: model.today, calendar: .xike,
                       revision: model.libraryRevision, retry: retry)
    }

    private var monthRequest: ArchiveRequest? {
        guard model.calendarMode else { return nil }
        var filter = model.archiveFilter
        filter.day = nil
        filter.datePreset = .all
        return ArchiveRequest(filter: filter, date: month, calendar: .xike, revision: model.libraryRevision, retry: retry)
    }

    private var showMemoriesCard: Bool {
        !model.archiveFilter.hasSearchText && model.archiveFilter.extraCount == 0 && !memoryDays.isEmpty
    }

    var body: some View {
        let query = request
        let monthQuery = monthRequest
        PageScroll {
            archiveHeader
            searchControls
            HStack {
                Picker(language.text("回望方式", "View"), selection: $model.calendarMode) {
                    Text(language.text("月历", "Calendar")).tag(true)
                    Text(language.text("时间流", "Timeline")).tag(false)
                }.pickerStyle(.segmented)
                Button {
                    showFilter.toggle()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "slider.horizontal.3")
                        if model.archiveFilter.extraCount > 0 { Text(String(model.archiveFilter.extraCount)).font(.caption) }
                    }.frame(minWidth: 44, minHeight: 44)
                    .background(palette.surface, in: RoundedRectangle(cornerRadius: palette.buttonRadius))
                }.buttonStyle(.plain).foregroundStyle(palette.primary)
                 .accessibilityLabel(language.text("打开筛选", "Open filters"))
                 .accessibilityIdentifier("archive-filter-toggle")
            }
            if showFilter { ArchiveFilterControls(filter: $model.archiveFilter) }
            if model.calendarMode {
                MonthGrid(month: $month, selected: model.archiveFilter.day, counts: counts) { date in
                    model.archiveFilter.day = model.archiveFilter.day == date ? nil : date
                    model.archiveFilter.datePreset = .all
                }
            }
            if showMemoriesCard {
                memoriesCard
            }
            if query != loadedRequest {
                ProgressView(language.text("正在读取记录…", "Loading entries…"))
            } else {
                if let page { timeline(page) }
                if let error { readError(error) }
            }
            if let calendarError, model.calendarMode { readError(calendarError) }
        }
        .accessibilityIdentifier("archive-page")
        .task(id: query) { await load(query, offset: 0) }
        .task(id: [model.libraryRevision, Int(Calendar.xike.startOfDay(for: model.today).timeIntervalSince1970)]) {
            if let result = try? await model.store.memoryDays(today: model.today, calendar: .xike), !Task.isCancelled {
                memoryDays = result
            }
        }
        .task(id: monthQuery) {
            counts = [:]
            calendarError = nil
            guard let monthQuery else { return }
            do {
                let result = try await model.store.calendarCounts(filter: monthQuery.filter, month: monthQuery.date, calendar: monthQuery.calendar)
                if !Task.isCancelled { counts = result }
            } catch {
                if !Task.isCancelled { calendarError = model.message(for: error) }
            }
        }
        .onDisappear { more?.cancel(); more = nil }
        .sheet(item: $selected) { link in JournalDetail(model: model, recordID: link.id) }
        .sheet(isPresented: $showMemories) {
            PastMomentsPanel(model: model)
        }
    }

    private func load(_ query: ArchiveRequest, offset: Int) async {
        if offset == 0 {
            more?.cancel()
            more = nil
            page = nil
        }
        error = nil
        do {
            if offset == 0, query.filter.hasSearchText { try await Task.sleep(for: .milliseconds(200)) }
            let result = try await model.store.archivePage(filter: query.filter, today: query.date, calendar: query.calendar, offset: offset)
            guard !Task.isCancelled, query == request else { return }
            if offset > 0, let page {
                self.page = ArchivePage(records: page.records + result.records, total: result.total)
            } else { page = result }
            loadedRequest = query
        } catch {
            guard !Task.isCancelled, query == request else { return }
            self.error = model.message(for: error)
            loadedRequest = query
        }
    }

    private func readError(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message).font(.footnote).foregroundStyle(palette.muted)
            Button(language.text("重试", "Retry")) { retry += 1 }
        }
    }

    private var memoriesSummary: String {
        let total = memoryDays.reduce(0) { $0 + $1.count }
        let hasMonthAgo = memoryDays.contains { $0.isMonthAgo }
        let previousYears = memoryDays.filter { !$0.isMonthAgo }
        let currentYear = Calendar.xike.component(.year, from: model.today)
        if hasMonthAgo && !previousYears.isEmpty {
            return language.text("一个月前与往年今天 · 共 \(total) 条", "One month ago & past years · \(total) entries")
        }
        if hasMonthAgo {
            return language.text("一个月前的今天 · 共 \(total) 条", "One month ago · \(total) entries")
        }
        if previousYears.count == 1,
           let first = previousYears.first,
           Calendar.xike.component(.year, from: first.date) == currentYear - 1 {
            return language.text("去年的今天 · 共 \(total) 条", "One year ago · \(total) entries")
        }
        return language.text("往年的今天 · 共 \(total) 条", "On this day in past years · \(total) entries")
    }

    private var memoriesCard: some View {
        Button {
            showMemories = true
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.footnote)
                    .foregroundStyle(palette.primary)
                    .frame(width: 32, height: 32)
                    .background(palette.accentSurface, in: Circle())
                VStack(alignment: .leading, spacing: 1) {
                    Text(language.text("往日回望", "Past moments"))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(palette.ink)
                        .lineLimit(1)
                    Text(memoriesSummary)
                        .font(.caption)
                        .foregroundStyle(palette.muted)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(palette.muted)
            }
            .padding(.horizontal, palette.inset).padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
            .background(palette.surface, in: RoundedRectangle(cornerRadius: palette.radius))
            .overlay(
                RoundedRectangle(cornerRadius: palette.radius)
                    .stroke(palette.primary.opacity(0.16), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("archive-memories-entry")
    }

    private var searchControls: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(palette.muted)
            TextField(language.text("搜索记录", "Search entries"), text: $model.archiveFilter.text)
                .submitLabel(.search)
                .accessibilityIdentifier("archive-search")
                .onChange(of: model.archiveFilter.text) { _, value in
                    if value.count > 80 { model.archiveFilter.text = String(value.prefix(80)) }
                }
            if !model.archiveFilter.text.isEmpty {
                Button {
                    model.archiveFilter.text = ""
                } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(palette.muted) }
                .buttonStyle(.plain)
                .accessibilityLabel(language.text("清除搜索", "Clear search"))
            }
        }
        .frame(minHeight: 47).padding(.horizontal, 14)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: palette.buttonRadius))
    }

    private var archiveHeader: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(language.text("值得记住的时刻", "MOMENTS TO REMEMBER")).font(.caption.weight(.semibold))
                .tracking(1.5).foregroundStyle(palette.primary)
            HStack {
                XikeTitle(title: language.text("回望", "Memories"))
                Spacer()
                countLabel(model.library.total)
            }
            Text(language.text("翻一翻走过的日子，看见当时的自己。", "Look through past days and meet who you were then."))
                .font(.subheadline).foregroundStyle(palette.muted)
        }
        .padding(.top, 7).padding(.bottom, 5)
    }

    @ViewBuilder
    private func timeline(_ page: ArchivePage) -> some View {
        let entries = page.records
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                XikeTitle(title: model.archiveFilter.day.map { formatMomentDay($0, language) }
                          ?? language.text("最近的时刻", "Recent moments"), large: false)
                Text(language.text("最新在前", "Newest first")).font(.footnote).foregroundStyle(palette.muted)
            }
            Spacer()
            countLabel(page.total)
        }
        if entries.isEmpty {
            rows(entries)
        } else {
            let days = Dictionary(grouping: entries, by: { Calendar.xike.startOfDay(for: $0.date) })
            ForEach(days.keys.sorted(by: >), id: \.self) { date in
                let records = days[date, default: []]
                HStack {
                    Text(formatMomentDay(date, language)).font(.headline)
                    Spacer()
                    Text(language.text("\(records.count) 条", "\(records.count) entries"))
                        .font(.caption).foregroundStyle(palette.muted)
                }
                rows(records)
            }
            if entries.count < page.total {
                Button(language.text("加载更多", "Load more")) {
                    let query = request
                    more = Task {
                        await load(query, offset: entries.count)
                        if !Task.isCancelled { more = nil }
                    }
                }.frame(minHeight: 44).disabled(more != nil)
            }
        }
    }

    private func countLabel(_ count: Int) -> some View {
        Text(language.text("\(count) 条记录", "\(count) entries"))
            .font(.caption.weight(.medium)).foregroundStyle(palette.primary)
            .padding(.vertical, 6).padding(.horizontal, 10)
            .background(palette.accentSurface, in: Capsule())
    }

    @ViewBuilder
    private func rows(_ entries: [JournalRecord]) -> some View {
        if entries.isEmpty {
            XikeCard {
                VStack(alignment: .leading, spacing: 6) {
                    Text(model.library.total == 0 ? language.text("还没有记录", "No entries yet") : language.text("没有符合条件的记录", "No matching entries"))
                        .font(.headline)
                    if model.library.total == 0 {
                        Text(language.text("记下一个此刻，就能在这里回望。", "Save a moment to revisit it here."))
                            .font(.subheadline).foregroundStyle(palette.muted)
                    }
                }.padding(.vertical, 12)
            }
        } else {
            ForEach(entries) { record in
                Button { selected = RecordLink(id: record.id) } label: { EntryCard(record: record, media: model.media) }
                    .buttonStyle(.plain)
            }
        }
    }
}

struct RecordLink: Identifiable { var id: String }

struct EntryCard: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    let record: JournalRecord
    let media: MediaDirectory?

    var body: some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 9) {
                HStack {
                    MoodArtwork(mood: record.displayMood, size: 30)
                    Text(record.displayMood.label(language)).font(.headline)
                    Spacer()
                    if record.isFavorite { Image(systemName: "heart.fill").foregroundStyle(palette.primary) }
                    Text(record.date.formatted(.dateTime.hour().minute().locale(language.locale)))
                        .font(.footnote).foregroundStyle(palette.muted)
                }
                if !record.note.isEmpty {
                    Text(record.note).font(.subheadline).lineLimit(3)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if !record.feelings.isEmpty {
                    Text(record.feelings.map { Feeling(rawValue: $0)?.label(language) ?? $0 }.joined(separator: " · "))
                        .font(.footnote).foregroundStyle(palette.muted)
                }
                if let media, let first = record.imageFileNames.first {
                    AttachmentImage(name: first, media: media).frame(height: 110)
                        .clipShape(RoundedRectangle(cornerRadius: palette.buttonRadius))
                }
                HStack(spacing: 10) {
                    if record.audio != nil { Label(language.text("录音", "Audio"), systemImage: "waveform") }
                    if record.video != nil { Label(language.text("视频", "Video"), systemImage: "video") }
                    if let weather = record.outdoor { Label(weather.placeName, systemImage: weather.weatherIcon) }
                }.font(.caption).foregroundStyle(palette.muted)
            }
        }.contentShape(Rectangle())
    }
}

struct RecordListPanel: View {
    @Environment(\.xikeLanguage) private var language
    let title: String
    let model: JournalAppModel
    let entries: [JournalRecord]
    @State private var selected: RecordLink?

    var body: some View {
        ModalPage(title: title) {
            if entries.isEmpty {
                XikeCard { Text(language.text("这段时间还没有记录。", "There are no entries for this period.")) }
            }
            ForEach(entries) { record in
                Button { selected = RecordLink(id: record.id) } label: { EntryCard(record: record, media: model.media) }
                    .buttonStyle(.plain)
            }
        }
        .sheet(item: $selected) { JournalDetail(model: model, recordID: $0.id) }
    }
}

struct JournalDetail: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Environment(\.dismiss) private var dismiss
    let model: JournalAppModel
    let recordID: String
    @State private var editing = false
    @State private var delete = false
    @State private var photoIndex = 0
    @State private var gallery = false
    @State private var loaded = false
    @State private var record: JournalRecord?
    @State private var readError: String?
    @State private var retry = 0

    var body: some View {
        Group {
            details
        }.task(id: [model.libraryRevision, retry]) {
            do {
                let latest = try await model.store.entry(recordID)
                try Task.checkCancellation()
                record = latest
                loaded = true
                readError = nil
            } catch {
                if !Task.isCancelled { readError = model.message(for: error) }
            }
        }
    }

    @ViewBuilder
    private var details: some View {
        if let record {
            ModalPage(title: language.text("记录详情", "Entry details")) {
                XikeCard {
                    VStack(alignment: .leading, spacing: 13) {
                        HStack {
                            MoodArtwork(mood: record.displayMood, size: 44)
                            VStack(alignment: .leading, spacing: 4) {
                                XikeTitle(title: record.displayMood.label(language), large: false)
                                Text(record.date.formatted(.dateTime.year().month().day().hour().minute().locale(language.locale)))
                                    .font(.footnote).foregroundStyle(palette.muted)
                            }
                            Spacer()
                            Button {
                                Task { await model.favorite(record) }
                            } label: {
                                Image(systemName: record.isFavorite ? "heart.fill" : "heart").frame(width: 44, height: 44)
                            }
                            .accessibilityLabel(record.isFavorite ? language.text("取消收藏", "Remove favorite") : language.text("收藏", "Favorite"))
                            .accessibilityIdentifier("favorite-entry")
                        }
                        if !record.feelings.isEmpty {
                            Text(record.feelings.map { Feeling(rawValue: $0)?.label(language) ?? $0 }.joined(separator: " · "))
                                .font(.subheadline).foregroundStyle(palette.primary)
                        }
                        if !record.note.isEmpty { Text(record.note).textSelection(.enabled).font(.body) }
                    }
                }
                if let weather = record.outdoor {
                    XikeCard {
                        VStack(alignment: .leading, spacing: 6) {
                            Label("\(weather.placeName) · \(Int(weather.temperatureCelsius.rounded()))°", systemImage: weather.weatherIcon)
                                .font(.headline)
                            if let full = weather.fullPlaceName, full != weather.placeName { Text(full).font(.footnote) }
                            let location = switch weather.locationSource {
                            case "amap": language.text("高德定位", "AMap")
                            case "system": language.text("系统定位", "System location")
                            case "manual": language.text("手动选择", "Manually selected")
                            default: ""
                            }
                            Text([location, weather.source].filter { !$0.isEmpty }.joined(separator: " · "))
                                .font(.caption).foregroundStyle(palette.muted)
                        }
                    }
                }
                if let media = model.media {
                    photoGrid(record, media: media)
                    if let audio = record.audio {
                        XikeCard { PlaybackPanel(file: audio.fileName, media: media, duration: audio.durationMillis) }
                    }
                    if let video = record.video {
                        XikeCard { PlaybackPanel(file: video.fileName, media: media, duration: video.durationMillis, video: true) }
                    }
                }
                Button(language.text("编辑记录", "Edit entry"), systemImage: "square.and.pencil") {
                    model.beginEdit(record)
                    editing = true
                }.frame(minHeight: 44)
                Button(role: .destructive) { delete = true } label: {
                    Label(language.text("删除记录", "Delete entry"), systemImage: "trash")
                }.frame(minHeight: 44)
            }
            .sheet(isPresented: $editing) { JournalComposer(model: model, editing: true).withEditorNavigation() }
            .fullScreenCover(isPresented: $gallery) {
                if let media = model.media { PhotoGallery(names: record.imageFileNames, media: media, index: photoIndex) }
            }
            .confirmationDialog(language.text("删除这条记录？", "Delete this entry?"), isPresented: $delete, titleVisibility: .visible) {
                Button(language.text("删除", "Delete"), role: .destructive) {
                    Task { if await model.delete(record) { dismiss() } }
                }
                Button(language.text("取消", "Cancel"), role: .cancel) {}
            }
        } else {
            ModalPage(title: language.text("记录详情", "Entry details")) {
                if let readError {
                    Text(readError)
                    Button(language.text("重试", "Retry")) { retry += 1 }
                } else if loaded {
                    Text(language.text("这条记录已移除。", "This entry was removed."))
                } else { ProgressView() }
            }
        }
    }

    @ViewBuilder
    private func photoGrid(_ record: JournalRecord, media: MediaDirectory) -> some View {
        if !record.imageFileNames.isEmpty {
            let count = record.imageFileNames.count
            let columns = count == 1 ? 1 : (count == 2 || count == 4 ? 2 : 3)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: columns), spacing: 6) {
                ForEach(Array(record.imageFileNames.enumerated()), id: \.element) { index, name in
                    Button {
                        photoIndex = index
                        gallery = true
                    } label: {
                        AttachmentImage(name: name, media: media, pixels: 640)
                            .frame(height: count == 1 ? 220 : 105)
                            .clipShape(RoundedRectangle(cornerRadius: palette.buttonRadius))
                    }.buttonStyle(.plain)
                        .accessibilityLabel(language.text("照片 \(index + 1)", "Photo \(index + 1)"))
                }
            }
        }
    }
}

private struct ArchiveFilterControls: View {
    @Environment(\.xikeLanguage) private var language
    @Binding var filter: ArchiveFilter

    var body: some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    XikeTitle(title: language.text("筛选这段记忆", "Filter memories"), large: false)
                    Spacer()
                    Button(language.text("全部清除", "Clear all")) { filter = ArchiveFilter(text: filter.text) }
                }
                Toggle(language.text("仅看收藏", "Favorites only"), isOn: $filter.favorites)
                XikeTitle(title: language.text("心情", "Mood"), large: false)
                MoodSelector(selected: filter.moods) { mood in
                    if filter.moods.contains(mood.score) { filter.moods.remove(mood.score) }
                    else { filter.moods.insert(mood.score) }
                }
                if let day = filter.day {
                    Button(formatMomentDay(day, language), systemImage: "xmark.circle") { filter.day = nil }
                        .accessibilityLabel(language.text("清除指定日期", "Clear selected day"))
                }
                Picker(language.text("日期", "Date"), selection: Binding(get: { filter.day == nil ? filter.datePreset : nil }, set: { selected in
                    if let selected { filter.datePreset = selected; filter.day = nil }
                })) {
                    if let day = filter.day { Text(formatMomentDay(day, language)).tag(nil as ArchiveDatePreset?) }
                    ForEach(ArchiveDatePreset.allCases) { Text($0.label(language)).tag(Optional($0)) }
                }.accessibilityIdentifier("archive-date-preset")
                Picker(language.text("照片", "Photos"), selection: $filter.photos) {
                    ForEach(PhotoFilter.allCases) { Text($0.label(language)).tag($0) }
                }
            }
        }
    }
}

private struct PastMomentsPanel: View {
    @Environment(\.xikeLanguage) private var language
    let model: JournalAppModel
    @State private var days: [MemoryDay]?
    @State private var selected: MemoryDay?
    @State private var message: String?
    @State private var retry = 0

    var body: some View {
        ModalPage(title: language.text("往日回望", "Past moments")) {
            if let message {
                Text(message)
                Button(language.text("重试", "Retry")) { retry += 1 }
            } else if let days {
                if days.isEmpty { XikeCard { Text(language.text("这些日子还没有留下记录", "No entries from these dates yet")) } }
                ForEach(days) { day in
                    XikeCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(day.isMonthAgo ? language.text("一个月前", "One month ago") : language.text("往年今天", "On this day"))
                                .font(.headline)
                            Text(day.date.formatted(.dateTime.year().month().day().locale(language.locale)))
                            Text(language.text("\(day.count) 条记录", "\(day.count) entries")).font(.footnote)
                            Button(language.text("查看记录", "View entries")) { selected = day }.frame(minHeight: 44)
                        }
                    }
                }
            } else { ProgressView() }
        }
        .task(id: [model.libraryRevision, retry]) {
            do {
                let result = try await model.store.memoryDays(today: model.today, calendar: .xike)
                try Task.checkCancellation()
                days = result
                message = nil
            } catch {
                if !Task.isCancelled { message = model.message(for: error) }
            }
        }
        .sheet(item: $selected) { day in MemoryDayPanel(model: model, date: day.date) }
    }
}

private struct MemoryDayPanel: View {
    @Environment(\.xikeLanguage) private var language
    let model: JournalAppModel
    let date: Date
    @State private var entries: [JournalRecord]?
    @State private var message: String?

    var body: some View {
        let title = date.formatted(.dateTime.year().month().day().locale(language.locale))
        Group {
            if let entries { RecordListPanel(title: title, model: model, entries: entries) }
            else { ModalPage(title: title) { if let message { Text(message) } else { ProgressView() } } }
        }.task(id: model.libraryRevision) {
            guard let interval = Calendar.xike.dateInterval(of: .day, for: date) else { return }
            do {
                let result = try await model.store.entries(in: interval)
                if !Task.isCancelled { entries = result }
            } catch {
                if !Task.isCancelled { message = model.message(for: error) }
            }
        }
    }
}

private struct MonthGrid: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Binding var month: Date
    let selected: Date?
    let counts: [Date: Int]
    let select: (Date) -> Void
    private var calendar: Calendar { .xike }

    var body: some View {
        let start = calendar.dateInterval(of: .month, for: month)?.start ?? month
        let blanks = (calendar.component(.weekday, from: start) + 5) % 7
        let days = calendar.range(of: .day, in: .month, for: month)?.count ?? 30
        XikeCard {
            VStack(spacing: 8) {
                HStack {
                    arrow("chevron.left", label: language.text("上个月", "Previous month"), value: -1)
                    Spacer()
                    Text(month.formatted(.dateTime.year().month(.wide).locale(language.locale))).font(.headline)
                    Spacer()
                    arrow("chevron.right", label: language.text("下个月", "Next month"), value: 1)
                }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 1), count: 7), spacing: 2) {
                    ForEach(weekdays, id: \.self) { Text($0).font(.caption).foregroundStyle(palette.muted) }
                    ForEach(0..<(blanks + days), id: \.self) { offset in
                        if offset < blanks { Color.clear.frame(height: 1) }
                        else if let date = calendar.date(byAdding: .day, value: offset - blanks, to: start) {
                            let active = selected.map { calendar.isDate(date, inSameDayAs: $0) } ?? false
                            let count = counts[calendar.startOfDay(for: date), default: 0]
                            Button { select(date) } label: {
                                VStack(spacing: 3) {
                                    Text(String(offset - blanks + 1)).font(.footnote.monospacedDigit())
                                    Capsule().fill(count == 0 ? .clear : palette.primary).frame(width: count > 1 ? 10 : 4, height: 4)
                                }.frame(maxWidth: .infinity, minHeight: 43)
                                 .background(active ? palette.accentSurface : Color.clear, in: RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain).accessibilityAddTraits(active ? .isSelected : [])
                            .disabled(count == 0 && !active)
                            .accessibilityLabel(formatMomentDay(date, language))
                        }
                    }
                }
            }
        }
    }

    private var weekdays: [String] {
        language.isEnglish ? ["M", "Tu", "W", "Th", "F", "Sa", "Su"] : ["一", "二", "三", "四", "五", "六", "日"]
    }

    private func arrow(_ name: String, label: String, value: Int) -> some View {
        Button {
            if let next = calendar.date(byAdding: .month, value: value, to: month) { month = next }
        } label: { Image(systemName: name).frame(width: 38, height: 36) }
        .buttonStyle(.plain).foregroundStyle(palette.primary).accessibilityLabel(label)
    }
}
