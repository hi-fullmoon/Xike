import SwiftUI

struct ArchiveScreen: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Bindable var model: JournalAppModel
    @State private var showFilter = false
    @State private var showMemories = false
    @State private var selected: RecordLink?
    @State private var month = Date.now
    @State private var selectedDay: Date?

    private var filtered: [JournalRecord] {
        let filter = model.archiveFilter
        return model.records.filter(filter.matches)
    }

    var body: some View {
        let visible = filtered
        PageScroll {
            archiveHeader
            Button {
                showMemories = true
            } label: {
                Label {
                    ViewThatFits {
                        HStack(spacing: 5) {
                            Text(language.text("往日回望", "Past moments")).fontWeight(.medium)
                            Text(language.text("（一个月前 · 往年今天）", "(One month ago · On this day)"))
                                .font(.caption).foregroundStyle(palette.muted)
                        }
                        Text(language.text("往日回望", "Past moments")).fontWeight(.medium)
                    }
                } icon: { Image(systemName: "clock.arrow.circlepath") }
                .frame(maxWidth: .infinity, minHeight: 46)
                .background(palette.accentSurface, in: RoundedRectangle(cornerRadius: palette.buttonRadius))
            }.buttonStyle(.plain).foregroundStyle(palette.primary)
            searchControls
            HStack {
                Picker(language.text("回望方式", "View"), selection: $model.calendarMode) {
                    Text(language.text("月历", "Calendar")).tag(true)
                    Text(language.text("时间流", "Timeline")).tag(false)
                }.pickerStyle(.segmented)
                Button {
                    showFilter = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "slider.horizontal.3")
                        if model.archiveFilter.extraCount > 0 { Text(String(model.archiveFilter.extraCount)).font(.caption) }
                    }.frame(minWidth: 44, minHeight: 44)
                    .background(palette.surface, in: RoundedRectangle(cornerRadius: palette.buttonRadius))
                }.buttonStyle(.plain).foregroundStyle(palette.primary)
                 .accessibilityLabel(language.text("打开筛选", "Open filters"))
            }
            if model.calendarMode {
                MonthGrid(month: $month, selected: $selectedDay, entries: visible)
                if let selectedDay {
                    XikeTitle(title: formatMomentDay(selectedDay, language), large: false)
                    rows(visible.filter { Calendar.xike.isDate($0.date, inSameDayAs: selectedDay) })
                }
            } else {
                timeline(visible)
            }
        }
        .accessibilityIdentifier("archive-page")
        .sheet(item: $selected) { link in JournalDetail(model: model, recordID: link.id) }
        .sheet(isPresented: $showFilter) { ArchiveFilterPanel(filter: $model.archiveFilter) }
        .sheet(isPresented: $showMemories) {
            RecordListPanel(title: language.text("往日回望", "On this day"), model: model,
                            entries: ArchiveFilter.memories(model.records))
        }
    }

    private var searchControls: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(palette.muted)
            TextField(language.text("搜索记录", "Search entries"), text: $model.archiveFilter.text)
                .submitLabel(.search)
                .accessibilityIdentifier("archive-search")
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
                countLabel(model.records.count)
            }
            Text(language.text("翻一翻走过的日子，看见当时的自己。", "Look through past days and meet who you were then."))
                .font(.subheadline).foregroundStyle(palette.muted)
        }
        .padding(.top, 7).padding(.bottom, 5)
    }

    @ViewBuilder
    private func timeline(_ entries: [JournalRecord]) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                XikeTitle(title: language.text("最近的时刻", "Recent moments"), large: false)
                Text(language.text("最新在前", "Newest first")).font(.footnote).foregroundStyle(palette.muted)
            }
            Spacer()
            countLabel(entries.count)
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
                    Text(model.records.isEmpty ? language.text("还没有记录", "No entries yet") : language.text("没有符合条件的记录", "No matching entries"))
                        .font(.headline)
                    if model.records.isEmpty {
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

    var body: some View {
        if let record = model.records.first(where: { $0.id == recordID }) {
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
                Text(language.text("这条记录已移除。", "This entry was removed."))
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

private struct ArchiveFilterPanel: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.dismiss) private var dismiss
    @Binding var filter: ArchiveFilter
    @State private var draft = ArchiveFilter()

    var body: some View {
        ModalPage(title: language.text("筛选记录", "Filters")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 10) {
                    XikeTitle(title: language.text("心情", "Mood"), large: false)
                    MoodSelector(selected: draft.moods) { mood in
                        if draft.moods.contains(mood.score) { draft.moods.remove(mood.score) }
                        else { draft.moods.insert(mood.score) }
                    }
                }
            }
            XikeCard {
                VStack(alignment: .leading, spacing: 7) {
                    dateChoice(language.text("起始日期", "From"), value: $draft.from)
                    Divider()
                    dateChoice(language.text("结束日期", "To"), value: $draft.to)
                }
            }
            XikeCard {
                VStack {
                    Picker(language.text("照片", "Photos"), selection: $draft.photos) {
                        ForEach(PhotoFilter.allCases) { Text($0.label(language)).tag($0) }
                    }
                    Toggle(language.text("只看收藏", "Favorites only"), isOn: $draft.favorites)
                }
            }
            XikeAction(title: language.text("应用筛选", "Apply filters"), enabled: (draft.from ?? .distantPast) <= (draft.to ?? .distantFuture)) {
                filter = draft
                dismiss()
            }
            Button(language.text("清除筛选", "Clear filters")) { draft = ArchiveFilter(text: draft.text) }
        }.onAppear { draft = filter }
    }

    @ViewBuilder
    private func dateChoice(_ title: String, value: Binding<Date?>) -> some View {
        Toggle(title, isOn: Binding(get: { value.wrappedValue != nil }, set: { value.wrappedValue = $0 ? .now : nil }))
        if let date = value.wrappedValue {
            DatePicker(title, selection: Binding(get: { value.wrappedValue ?? date }, set: { value.wrappedValue = $0 }),
                       displayedComponents: .date).labelsHidden()
        }
    }
}

private struct MonthGrid: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Binding var month: Date
    @Binding var selected: Date?
    let entries: [JournalRecord]
    private var calendar: Calendar { .xike }

    var body: some View {
        let start = calendar.dateInterval(of: .month, for: month)?.start ?? month
        let blanks = (calendar.component(.weekday, from: start) + 5) % 7
        let days = calendar.range(of: .day, in: .month, for: month)?.count ?? 30
        let marks = Dictionary(grouping: entries, by: { calendar.startOfDay(for: $0.date) })
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
                            Button { selected = date } label: {
                                VStack(spacing: 3) {
                                    Text(String(offset - blanks + 1)).font(.footnote.monospacedDigit())
                                    Circle().fill(marks[date] == nil ? .clear : palette.primary).frame(width: 5, height: 5)
                                }.frame(maxWidth: .infinity, minHeight: 43)
                                 .background(active ? palette.accentSurface : Color.clear, in: RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain).accessibilityAddTraits(active ? .isSelected : [])
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
            if let next = calendar.date(byAdding: .month, value: value, to: month) { month = next; selected = nil }
        } label: { Image(systemName: name).frame(width: 38, height: 36) }
        .buttonStyle(.plain).foregroundStyle(palette.primary).accessibilityLabel(label)
    }
}
