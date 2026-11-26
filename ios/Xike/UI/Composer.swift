import AVFoundation
import Photos
import PhotosUI
import SwiftUI

private enum ComposerPanel: String, Identifiable {
    case feelings, date, weather, voice, video, pictures, amap
    var id: Self { self }
}

struct JournalComposer: View {
    private static let noteLimit = 280
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Environment(\.dismiss) private var dismiss
    let model: JournalAppModel
    var editing = false
    @State private var panel: ComposerPanel?
    @State private var photoChoices: [PhotosPickerItem] = []
    @State private var videoChoice: PhotosPickerItem?
    @State private var pickPhotos = false
    @State private var pickVideo = false
    @State private var camera: Bool?
    @State private var photoIndex = 0
    @State private var confirmDiscard = false
    @State private var showTimeOptions = false
    @State private var confirmNow = false
    @State private var mediaLoading = false
    @FocusState private var noteFocused: Bool

    private var draft: JournalDraft { model.draftValue(editing: editing) }

    var body: some View {
        PageScroll {
            if !editing { momentHeader }
            if model.preferences.dailyQuestion && !editing { dailyQuestion }
            moodCard
            noteCard
            if !editing {
                Label(language.text("草稿自动加密保存在本机", "Drafts are automatically encrypted on this device"), systemImage: "shield")
                    .font(.footnote).foregroundStyle(palette.muted)
            }
            mediaContent
            weatherCard
        }
        .accessibilityIdentifier(editing ? "entry-editor" : "moment-page")
        .safeAreaInset(edge: .bottom, spacing: 0) { saveFooter }
        .sheet(item: $panel, content: presented)
        .photosPicker(isPresented: $pickPhotos, selection: $photoChoices,
                      maxSelectionCount: max(1, 9 - draft.photos.count), matching: .images, preferredItemEncoding: .current)
        .photosPicker(isPresented: $pickVideo, selection: $videoChoice, matching: .videos, preferredItemEncoding: .current)
        .onChange(of: photoChoices) { _, selection in
            if !selection.isEmpty { Task { await importPhotos(selection) } }
        }
        .onChange(of: videoChoice) { _, selection in
            if let selection { Task { await importMovie(selection) } }
        }
        .fullScreenCover(isPresented: Binding(get: { camera != nil }, set: { if !$0 { camera = nil } })) {
            if let video = camera {
                CameraPicker(video: video) { result in
                    camera = nil
                    Task { await captured(result, video: video) }
                }.ignoresSafeArea()
            }
        }
        .confirmationDialog(editing ? language.text("放弃本次编辑？", "Discard these changes?")
                            : language.text("放弃这份草稿？", "Discard this draft?"), isPresented: $confirmDiscard,
                            titleVisibility: .visible) {
            Button(editing ? language.text("放弃修改", "Discard changes") : language.text("放弃草稿", "Discard draft"),
                   role: .destructive) {
                Task {
                    if await model.discardDraft(editing: editing), editing { dismiss() }
                }
            }
            Button(editing ? language.text("继续编辑", "Keep editing") : language.text("继续保留", "Keep draft"), role: .cancel) {}
        } message: {
            if !editing {
                Text(language.text("将清空这份未保存的草稿。已保存的记录不会受影响。",
                                   "This clears the unsaved draft. Saved entries are unaffected."))
            }
        }
        .confirmationDialog(language.text("记录时间", "Entry time"), isPresented: $showTimeOptions, titleVisibility: .visible) {
            Button(language.text("修改日期时间", "Change date and time")) { panel = .date }
            Button(language.text("改为现在", "Use current time")) {
                if let weatherDate, !Calendar.xike.isDateInToday(weatherDate) { confirmNow = true }
                else { applyDate(nil) }
            }
            Button(language.text("取消", "Cancel"), role: .cancel) {}
        }
        .confirmationDialog(language.text("改为现在？", "Use current time?"), isPresented: $confirmNow, titleVisibility: .visible) {
            Button(language.text("改为现在", "Use current time")) { applyDate(nil) }
            Button(language.text("取消", "Cancel"), role: .cancel) {}
        } message: {
            Text(language.text("改变日期会移除这条记录的窗外信息。", "Changing the day clears the weather snapshot."))
        }
    }

    func withEditorNavigation() -> some View {
        NavigationStack {
            self
                .navigationTitle(language.text("编辑记录", "Edit entry"))
                .navigationBarTitleDisplayMode(.inline)
        }
        .interactiveDismissDisabled()
    }

    private var momentHeader: some View {
        VStack(alignment: .leading, spacing: palette.gap) {
            HStack(spacing: 9) {
                BrandMark()
                Text(language.text("息刻", "Xike")).font(.headline)
                Spacer()
                if !draft.isEmpty {
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { headerDate; discardButton }
                        VStack(alignment: .trailing, spacing: 0) { headerDate; discardButton }
                    }
                } else { headerDate }
            }
            Text(language.text("留一刻给自己", "A MOMENT FOR YOURSELF"))
                .font(.caption.weight(.semibold)).tracking(1.6).foregroundStyle(palette.primary)
            XikeTitle(title: language.text("给这一刻，\n留个位置。", "Make room\nfor this moment."))
            let todayCount = model.library.todayCount
            Text(todayCount == 0 ? language.text("不用解释，先感受自己。", "No explanation needed. Just notice how you feel.")
                 : language.text("今天已经停下来听见自己 \(todayCount) 次。", "You have paused to listen to yourself \(todayCount) times today."))
                .font(.subheadline).foregroundStyle(palette.muted)
        }
        .padding(.bottom, 3)
    }

    private var headerDate: some View {
        Text(formatMomentDay(model.today, language)).font(.footnote.weight(.medium)).foregroundStyle(palette.muted)
    }

    private var discardButton: some View {
        Button {
            noteFocused = false
            confirmDiscard = true
        } label: {
            Text(language.text("放弃草稿", "Discard draft")).font(.footnote.weight(.semibold))
                .frame(minHeight: 44).foregroundStyle(.red)
        }
        .buttonStyle(.plain)
        .disabled(model.busy || mediaLoading)
        .accessibilityIdentifier("discard-draft")
    }

    private var dailyQuestion: some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 8) {
                XikeTitle(title: language.text("每日一问", "Daily question"), large: false)
                Text(DailyQuestions.question(style: model.preferences.promptStyle, language: language, date: model.today))
                    .foregroundStyle(palette.muted)
            }
        }
    }

    private var moodCard: some View {
        VStack(alignment: .leading, spacing: 9) {
            XikeTitle(title: language.text("此刻的心情", "How you feel now"), large: false)
            XikeCard {
                VStack(spacing: 11) {
                    MoodSelector(selected: draft.mood.map { [$0.score] } ?? []) { mood in change { $0.mood = mood } }
                    Button {
                        noteFocused = false
                        panel = .feelings
                    } label: {
                        HStack {
                            if !draft.feelings.isEmpty {
                                Text(draft.feelings.map { Feeling(rawValue: $0)?.label(language) ?? $0 }.joined(separator: " · "))
                                    .lineLimit(2)
                            } else { Text(language.text("添加具体感受", "Add feelings")) }
                            Spacer()
                            Image(systemName: "chevron.down").font(.caption.weight(.semibold))
                        }
                        .font(.subheadline.weight(.medium)).padding(.horizontal, 14)
                        .frame(minHeight: 44).foregroundStyle(palette.primary)
                        .background(palette.quiet, in: RoundedRectangle(cornerRadius: palette.buttonRadius))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var noteCard: some View {
        VStack(alignment: .leading, spacing: 9) {
            XikeTitle(title: language.text("写下一点点", "Write a little"), large: false)
            XikeCard {
                VStack(spacing: 0) {
                    ZStack(alignment: .topLeading) {
                        if draft.note.isEmpty {
                            Text(language.text("这一刻，有什么想留下？", "What would you like to keep from this moment?"))
                                .foregroundStyle(palette.muted.opacity(0.72))
                                .padding(.top, 8).padding(.leading, 4)
                                .allowsHitTesting(false).accessibilityHidden(true)
                        }
                        TextEditor(text: Binding(get: { draft.note }, set: changeNote))
                            .scrollContentBackground(.hidden).scrollDisabled(true)
                            .focused($noteFocused)
                            .frame(minHeight: 75)
                            .accessibilityLabel(language.text("这一刻的注脚", "A note for this moment"))
                            .accessibilityIdentifier(editing ? "edit-note" : "moment-note")
                    }.font(.body)
                    if !draft.note.isEmpty {
                        Text("\(draft.note.utf16.count) / \(Self.noteLimit)")
                            .font(.caption.monospacedDigit()).foregroundStyle(palette.muted)
                            .frame(maxWidth: .infinity, alignment: .trailing).padding(.bottom, 6)
                    }
                    Divider().overlay(palette.outline)
                    HStack {
                        Menu {
                            Button(language.text("拍照", "Take a photo"), systemImage: "camera") { openCamera(video: false) }
                                .disabled(draft.photos.count >= 9)
                            Button(language.text("从相册选择照片", "Choose photos"), systemImage: "photo.on.rectangle") { pickPhotos = true }
                                .disabled(draft.photos.count >= 9)
                            Button(language.text("录音", "Record audio"), systemImage: "mic") { panel = .voice }
                                .disabled(!editing && draft.audio != nil)
                            Button(language.text("拍摄视频", "Record video"), systemImage: "video") { openCamera(video: true) }
                            Button(language.text("从相册选择视频", "Choose a video"), systemImage: "film") { pickVideo = true }
                        } label: {
                            Image(systemName: "plus").font(.title3).frame(width: 44, height: 44)
                        }
                        .accessibilityLabel(language.text("添加内容", "Add content"))
                        Spacer()
                        Button {
                            noteFocused = false
                            if !editing && draft.date != nil { showTimeOptions = true }
                            else { panel = .date }
                        } label: {
                            Text(draft.date.map { date in
                                let time = date.formatted(.dateTime.hour().minute().locale(language.locale))
                                return "\(formatMomentDay(date, language)) · \(time)"
                            } ?? language.text("今天 · 修改时间", "Today · Change time"))
                                .font(.subheadline).padding(.vertical, 12).multilineTextAlignment(.trailing)
                        }.buttonStyle(.plain).foregroundStyle(palette.muted)
                            .accessibilityIdentifier("entry-time")
                    }
                    .padding(.bottom, -9)
                }
            }
        }
    }

    private var saveFooter: some View {
        VStack(spacing: 8) {
            if editing {
                Button(language.text("取消", "Cancel")) { confirmDiscard = true }.font(.footnote)
                    .disabled(model.busy || mediaLoading)
            }
            let title = editing ? language.text("保存修改", "Save changes")
                : (draft.mood == nil ? language.text("先选择一种心情", "Choose a mood first")
                   : (draft.date == nil ? language.text("记下此刻", "Save this moment")
                      : language.text("补记这一刻", "Save this past moment")))
            XikeAction(title: mediaLoading ? language.text("正在导入…", "Importing…") : title,
                       enabled: draft.mood != nil && !model.busy && !mediaLoading) {
                noteFocused = false
                Task { if await model.save(editing: editing) && editing { dismiss() } }
            }
            .accessibilityIdentifier(editing ? "save-edit" : "save-moment")
        }
        .frame(maxWidth: palette.maxWidth)
        .padding(.horizontal, palette.horizontal).padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(palette.background)
    }

    @ViewBuilder
    private var mediaContent: some View {
        if let media = model.media {
            if !draft.photos.isEmpty {
                XikeTitle(title: language.text("这一刻的照片", "Photos of this moment"), large: false)
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(Array(draft.photos.enumerated()), id: \.element) { index, name in
                            AttachmentImage(name: name, media: media)
                                .frame(width: 105, height: 105).clipShape(RoundedRectangle(cornerRadius: palette.buttonRadius))
                                .onTapGesture { photoIndex = index; panel = .pictures }
                                .overlay(alignment: .topTrailing) {
                                    Button {
                                        change { $0.photos.removeAll { $0 == name } }
                                    } label: {
                                        Image(systemName: "xmark.circle.fill").font(.title3).padding(6)
                                            .foregroundStyle(.white, .black.opacity(0.6))
                                    }
                                    .buttonStyle(.plain).accessibilityLabel(language.text("移除照片", "Remove photo"))
                                }
                        }
                    }
                }.scrollIndicators(.hidden)
            }
            if let audio = draft.audio {
                XikeCard {
                    HStack {
                        PlaybackPanel(file: audio.fileName, media: media, duration: audio.durationMillis)
                        removeButton(language.text("移除录音", "Remove audio")) { change { $0.audio = nil } }
                    }
                }
            }
            if let video = draft.video {
                XikeTitle(title: language.text("这一刻的视频", "Video of this moment"), large: false)
                XikeCard {
                    HStack(spacing: 12) {
                        Button { panel = .video } label: {
                            HStack {
                                AttachmentImage(name: video.coverFileName, media: media)
                                    .frame(width: 68, height: 53).clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay { Image(systemName: "play.circle.fill").foregroundStyle(.white).shadow(radius: 4) }
                                Text(mediaTime(Double(video.durationMillis) / 1_000)).font(.callout)
                            }
                        }.buttonStyle(.plain)
                        Spacer()
                        removeButton(language.text("移除视频", "Remove video")) { change { $0.video = nil } }
                    }
                }
            }
        }
    }

    private var weatherCard: some View {
        VStack(alignment: .leading, spacing: 9) {
            if editing {
                XikeTitle(title: language.text("此刻窗外", "Outside now"), large: false)
            } else {
                HiddenSettingsTitle(title: language.text("此刻窗外", "Outside now"), large: false) {
                    noteFocused = false
                    panel = .amap
                }.accessibilityIdentifier("amap-settings-entry")
            }
            XikeCard {
                HStack(spacing: 12) {
                    Group {
                        if let weather = draft.outdoor {
                            WeatherArtwork(weatherCode: weather.weatherCode, fallbackSymbol: weather.weatherIcon, size: 22)
                        } else {
                            WeatherArtwork(weatherCode: 1, fallbackSymbol: "cloud.sun", size: 22)
                        }
                    }
                    .frame(width: 42, height: 42).background(palette.quiet, in: Circle())
                    VStack(alignment: .leading, spacing: 3) {
                        Text(draft.outdoor?.placeName ?? language.text("地点和天气", "Location and weather")).font(.subheadline.weight(.medium))
                        if let weather = draft.outdoor {
                            Text("\(Int(weather.temperatureCelsius.rounded()))° · \(weather.condition(language))").font(.footnote)
                        } else { Text(language.text("地点与天气 · 可选", "Place and weather · Optional")).font(.footnote) }
                    }.foregroundStyle(palette.muted)
                    Spacer()
                    if draft.outdoor == nil {
                        Button(language.text("添加", "Add")) { panel = .weather }.font(.subheadline.weight(.medium))
                            .disabled(editing ? !Calendar.xike.isDateInToday(draft.date ?? .now) : draft.date != nil)
                    } else {
                        removeButton(language.text("移除天气", "Remove weather")) { change { $0.outdoor = nil } }
                    }
                }
            }
        }
    }

    private func removeButton(_ name: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: "xmark").font(.caption.weight(.medium)).frame(width: 44, height: 44)
        }.buttonStyle(.plain).foregroundStyle(palette.muted).accessibilityLabel(name)
    }

    @ViewBuilder
    private func presented(_ panel: ComposerPanel) -> some View {
        switch panel {
        case .feelings:
            FeelingsPanel(selection: draft.feelings) { values in change { $0.feelings = values } }
        case .date:
            ChooseEntryDate(date: draft.date ?? .now, weatherDate: weatherDate, onApply: { applyDate($0) })
        case .weather:
            WeatherPanel { weather in change { $0.outdoor = weather } }
        case .amap:
            AMapSettingsPanel()
        case .voice:
            if let folder = model.captureDirectory {
                VoiceCapturePanel(folder: folder) { url, duration in
                    do { try await model.addRecording(url, duration: duration, editing: editing); return true }
                    catch { model.report(error); return false }
                }
            }
        case .video:
            if let video = draft.video, let media = model.media {
                ModalPage(title: language.text("视频", "Video")) {
                    PlaybackPanel(file: video.fileName, media: media, duration: video.durationMillis, video: true)
                }
            }
        case .pictures:
            if let media = model.media, !draft.photos.isEmpty {
                PhotoGallery(names: draft.photos, media: media, index: photoIndex)
            }
        }
    }

    private func change(_ update: (inout JournalDraft) -> Void) {
        guard !model.busy && !mediaLoading else { return }
        var value = draft
        update(&value)
        model.setDraft(value, editing: editing)
    }

    private func changeNote(_ note: String) {
        let limit = max(Self.noteLimit, draft.note.utf16.count)
        if note.utf16.count <= limit {
            change { $0.note = note }
        } else {
            var length = 0
            let prefix = note.prefix { character in
                length += String(character).utf16.count
                return length <= limit
            }
            change { $0.note = String(prefix) }
        }
    }

    private var weatherDate: Date? {
        guard let weather = draft.outdoor else { return nil }
        if editing { return draft.date }
        return Date(timeIntervalSince1970: Double(weather.capturedAt) / 1_000)
    }

    private func applyDate(_ selected: Date?) {
        let source = weatherDate
        change {
            if let source, !Calendar.xike.isDate(source, inSameDayAs: selected ?? .now) { $0.outdoor = nil }
            $0.date = selected
        }
    }

    private func openCamera(video: Bool) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            model.report(JournalError.unavailable)
            return
        }
        camera = video
    }

    private func importPhotos(_ selection: [PhotosPickerItem]) async {
        mediaLoading = true
        defer { mediaLoading = false; photoChoices = [] }
        var urls: [URL] = []
        defer { for url in urls { discardSelection(url) } }
        do {
            for item in selection {
                guard let unit = try await item.loadTransferable(type: PickedPhoto.self) else { throw JournalError.attachment }
                urls.append(unit.url)
            }
            try await model.addPhotos(urls, editing: editing)
        } catch { model.report(error) }
    }

    private func importMovie(_ selection: PhotosPickerItem) async {
        mediaLoading = true
        defer { mediaLoading = false; videoChoice = nil }
        do {
            guard let movie = try await selection.loadTransferable(type: PickedMovie.self) else { throw JournalError.attachment }
            defer { discardSelection(movie.url) }
            try await model.addVideo(movie.url, editing: editing)
        } catch { model.report(error) }
    }

    private func captured(_ result: Result<URL?, Error>, video: Bool) async {
        mediaLoading = true
        defer { mediaLoading = false }
        do {
            guard let url = try result.get() else { return }
            defer { discardSelection(url) }
            if video { try await model.addVideo(url, editing: editing) }
            else { try await model.addPhotos([url], editing: editing) }
            if await PHPhotoLibrary.requestAuthorization(for: .addOnly) == .authorized {
                try await PHPhotoLibrary.shared().performChanges {
                    if video { PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url) }
                    else { PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: url) }
                }
            }
        } catch { model.report(error) }
    }

    private func discardSelection(_ url: URL) { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
}

struct MoodSelector: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Environment(\.dynamicTypeSize) private var dynamicType
    let selected: Set<Int>
    let action: (Mood) -> Void

    var body: some View {
        if dynamicType.isAccessibilitySize {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 102))], spacing: 12) { choices }
        } else {
            HStack(alignment: .top, spacing: 3) { choices }
        }
    }

    private var choices: some View {
        ForEach(Mood.choices) { mood in
            let active = selected.contains(mood.score)
            Button { action(mood) } label: {
                VStack(spacing: 4) {
                    MoodArtwork(mood: mood, size: 31, emphasized: active)
                        .frame(width: 47, height: 47)
                        .background(active ? palette.accentSurface : palette.quiet, in: Circle())
                        .overlay { Circle().strokeBorder(active ? palette.primary : palette.outline, lineWidth: active ? 2 : 1) }
                    Text(mood.label(language)).font(.caption.weight(active ? .semibold : .regular))
                        .lineLimit(2).minimumScaleFactor(0.75).multilineTextAlignment(.center)
                    Circle().fill(active ? palette.primary : Color.clear).frame(width: 4, height: 4)
                }.frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            .foregroundStyle(active ? palette.primary : palette.muted)
            .accessibilityAddTraits(active ? .isSelected : [])
            .accessibilityIdentifier("mood-" + mood.rawValue)
        }
    }
}

struct ModalPage<Content: View>: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Environment(\.dismiss) private var dismiss
    var title: String
    @ViewBuilder let content: Content
    var body: some View {
        NavigationStack {
            PageScroll { content }.background(palette.background)
                .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(language.text("关闭", "Close")) { dismiss() } } }
        }
    }
}

private struct FeelingsPanel: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss
    @State var selection: [String]
    var onApply: ([String]) -> Void

    var body: some View {
        ModalPage(title: language.text("具体感受", "Feelings")) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: 8)], spacing: 9) {
                ForEach(Feeling.allCases) { feeling in
                    let active = selection.contains(feeling.rawValue)
                    Button {
                        if active { selection.removeAll { $0 == feeling.rawValue } }
                        else { selection.append(feeling.rawValue) }
                    } label: {
                        HStack(spacing: 5) {
                            if palette.moodIconStyle != .classic {
                                FeelingArtwork(feeling: feeling, size: 14, color: active ? palette.primary : palette.muted)
                            }
                            Text(feeling.label(language)).font(.callout)
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(active ? palette.accentSurface : palette.surface,
                                    in: RoundedRectangle(cornerRadius: palette.buttonRadius))
                    }.buttonStyle(.plain)
                      .accessibilityAddTraits(active ? .isSelected : [])
                }
            }
            XikeAction(title: language.text("完成", "Done")) { onApply(selection); dismiss() }
        }
    }
}

private struct ChooseEntryDate: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.dismiss) private var dismiss
    @State private var date: Date
    let latest: Date
    let weatherDate: Date?
    let onApply: (Date) -> Void
    @State private var confirm = false

    init(date: Date, weatherDate: Date?, onApply: @escaping (Date) -> Void) {
        _date = State(initialValue: date)
        latest = max(.now, date)
        self.weatherDate = weatherDate
        self.onApply = onApply
    }

    var body: some View {
        ModalPage(title: language.text("修改日期时间", "Change date and time")) {
            XikeCard {
                DatePicker(language.text("记录时间", "Entry time"), selection: $date,
                           in: ...latest, displayedComponents: [.date, .hourAndMinute]).datePickerStyle(.graphical)
            }
            XikeAction(title: language.text("完成", "Done")) {
                if let weatherDate, !Calendar.xike.isDate(date, inSameDayAs: weatherDate) { confirm = true }
                else { onApply(date); dismiss() }
            }
        }
        .confirmationDialog(language.text("修改记录日期？", "Change the entry date?"), isPresented: $confirm, titleVisibility: .visible) {
            Button(language.text("修改日期", "Change date")) { onApply(date); dismiss() }
            Button(language.text("取消", "Cancel"), role: .cancel) {}
        } message: { Text(language.text("改变日期会移除这条记录的窗外信息。", "Changing the day clears the weather snapshot.")) }
    }
}

func formatMomentDay(_ day: Date, _ language: XikeLanguage) -> String {
    let formatter = DateFormatter()
    formatter.locale = language.locale
    formatter.dateFormat = language.isEnglish ? "MMM d · EEE" : "M月d日 · EEE"
    return formatter.string(from: day)
}
