import Foundation
import Observation
import SwiftUI

enum XikeTab: String, CaseIterable, Identifiable {
    case moment, insights, archive, settings
    var id: Self { self }
    var icon: String {
        switch self {
        case .moment: "record.circle"
        case .insights: "chart.bar.xaxis"
        case .archive: "list.clipboard"
        case .settings: "slider.horizontal.3"
        }
    }
    func title(_ language: XikeLanguage) -> String {
        switch self {
        case .moment: language.text("此刻", "Now")
        case .insights: language.text("轨迹", "Patterns")
        case .archive: language.text("回望", "Memories")
        case .settings: language.text("设置", "Settings")
        }
    }
}

struct DisplayNotice: Identifiable {
    let id = UUID()
    var message: String
    var action: String?
    var perform: (() -> Void)?
}

@MainActor @Observable
final class JournalAppModel {
    let store: JournalStore
    let automaticBackup: AutomaticBackup
    let reminders = JournalReminders()
    var preferences: LocalPreferences {
        didSet { preferences.save() }
    }
    var tab = XikeTab.moment
    var records: [JournalRecord] = []
    var draft = JournalDraft()
    var editDraft: JournalDraft?
    var media: MediaDirectory?
    var captureDirectory: URL?
    var canUndoRestore = false
    var notice: DisplayNotice?
    var errorMessage: String?
    var blocked: String?
    var isLoaded = false
    var isLocked: Bool
    var isAuthenticating = false
    var busy = false
    var sceneIsActive = true
    var archiveFilter = ArchiveFilter()
    var calendarMode = false
    var insightsPeriod = InsightsPeriod.days30
    private var awaySince: Date?
    private var draftRevision = 0
    private var editRevision = 0
    private var noticeTask: Task<Void, Never>?

    var language: XikeLanguage { preferences.language }

    init(store: JournalStore) {
        self.store = store
        automaticBackup = XikeServices.automaticBackup
        let initial = LocalPreferences.read()
        preferences = initial
        isLocked = initial.lock
    }

    func start() async {
        guard !isLoaded else { return }
        blocked = nil
        do {
            let initial = try await store.open()
            records = initial.records
            draft = initial.draft ?? JournalDraft()
            editDraft = initial.editDraft
            media = initial.media
            captureDirectory = initial.captureDirectory
            canUndoRestore = initial.canUndoRestore
            isLoaded = true
        } catch {
            blocked = message(for: error)
        }
    }

    func show(_ text: String, action: String? = nil, perform: (() -> Void)? = nil) {
        noticeTask?.cancel()
        let current = DisplayNotice(message: text, action: action, perform: perform)
        notice = current
        noticeTask = Task {
            try? await Task.sleep(for: .seconds(6))
            if !Task.isCancelled, notice?.id == current.id { notice = nil }
        }
    }

    func message(for error: Error) -> String {
        (error as? JournalError ?? .storage).message(language)
    }

    func report(_ error: Error) {
        if !Task.isCancelled && !(error is CancellationError) { errorMessage = message(for: error) }
    }

    func phaseChanged(_ phase: ScenePhase) async {
        sceneIsActive = phase == .active
        switch phase {
        case .background:
            if awaySince == nil { awaySince = .now }
        case .active:
            if let awaySince, preferences.lock,
               Date.now.timeIntervalSince(awaySince) >= Double(preferences.lockDelay) {
                isLocked = true
                notice = nil
            }
            awaySince = nil
            if isLoaded {
                if preferences.reminder.enabled, preferences.reminder.pausedUntil.map({ $0 <= .now }) == true {
                    preferences.reminder.pausedUntil = nil
                    try? await reminders.apply(preferences.reminder, language: language, requestPermission: false)
                }
                _ = await automaticBackup.runIfDue()
            }
        default: break
        }
    }

    func unlock() async {
        guard !isAuthenticating else { return }
        isAuthenticating = true
        defer { isAuthenticating = false }
        do {
            try await ApplicationLock.authenticate(language)
            isLocked = false
        } catch {
            if (error as? JournalError) == .unavailable { report(error) }
        }
    }

    func setLock(_ enabled: Bool) async {
        do {
            try await ApplicationLock.authenticate(language)
            preferences.lock = enabled
            isLocked = false
        } catch {
            if (error as? JournalError) == .unavailable { report(error) }
        }
    }

    func setDraft(_ value: JournalDraft, editing: Bool) {
        let revision = nextRevision(editing)
        if editing { editDraft = value } else { draft = value }
        Task {
            do { try await store.saveDraft(value, revision: revision, editing: editing) }
            catch { report(error) }
        }
    }

    func draftValue(editing: Bool) -> JournalDraft {
        editing ? (editDraft ?? JournalDraft()) : draft
    }

    func beginEdit(_ record: JournalRecord) {
        if editDraft?.id == record.id { return }
        setDraft(JournalDraft(record), editing: true)
    }

    func discardEdit() async {
        let revision = nextRevision(true)
        do {
            try await store.saveDraft(nil, revision: revision, editing: true)
            editDraft = nil
            await store.tidyAttachments()
        } catch { report(error) }
    }

    func save(editing: Bool) async -> Bool {
        guard !busy else { return false }
        busy = true
        defer { busy = false }
        do {
            let snapshot = draftValue(editing: editing)
            _ = try await store.save(snapshot, revision: nextRevision(editing), editing: editing)
            if editing { editDraft = nil } else { draft = JournalDraft() }
            try await refresh()
            show(editing ? language.text("已保存修改", "Changes saved") : language.text("已留住这一刻", "Moment saved"))
            return true
        } catch {
            report(error)
            return false
        }
    }

    func favorite(_ record: JournalRecord) async {
        do {
            try await store.favorite(record.id, desired: !record.isFavorite)
            try await refresh()
        } catch { report(error) }
    }

    func delete(_ record: JournalRecord) async -> Bool {
        do {
            try await store.delete(record.id)
            try await refresh()
            show(language.text("已删除记录", "Entry deleted"), action: language.text("撤销", "Undo")) { [weak self] in
                Task { await self?.undoDelete() }
            }
            return true
        } catch {
            report(error)
            return false
        }
    }

    private func undoDelete() async {
        do {
            try await store.undoDelete()
            try await refresh()
            show(language.text("已恢复记录", "Entry restored"))
        } catch { report(error) }
    }

    func addPhotos(_ urls: [URL], editing: Bool) async throws {
        guard !busy else { throw JournalError.changed }
        busy = true
        defer { busy = false }
        var value = draftValue(editing: editing)
        for url in urls.prefix(max(0, 9 - value.photos.count)) {
            value.photos.append(try await store.importPhoto(url))
            try await persistMediaDraft(value, editing: editing)
        }
    }

    func addRecording(_ url: URL, duration: Int64, editing: Bool) async throws {
        guard !busy else { throw JournalError.changed }
        busy = true
        defer { busy = false }
        var value = draftValue(editing: editing)
        value.audio = try await store.importRecording(url, duration: duration)
        try await persistMediaDraft(value, editing: editing)
    }

    func addVideo(_ url: URL, editing: Bool) async throws {
        guard !busy else { throw JournalError.changed }
        busy = true
        defer { busy = false }
        var value = draftValue(editing: editing)
        value.video = try await store.importVideo(url)
        try await persistMediaDraft(value, editing: editing)
    }

    func applyBackup(_ backup: PreparedBackup, mode: RestoreMode) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        do {
            let result = try await store.restore(backup, mode: mode)
            try await refresh()
            if mode == .replace { editDraft = nil }
            if result.changed {
                show(language.text("已恢复备份", "Backup restored"), action: language.text("撤销", "Undo")) { [weak self] in
                    Task { await self?.undoRestore() }
                }
            } else {
                show(language.text("备份中的记录已在本机", "The backup is already on this device"))
            }
        } catch { report(error) }
    }

    func undoRestore() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        do {
            try await store.undoRestore()
            editDraft = nil
            try await refresh()
            show(language.text("已撤销恢复", "Restore undone"))
        } catch { report(error) }
    }

    func updateReminders(_ options: ReminderOptions, requestPermission: Bool = false) async {
        do {
            try await reminders.apply(options, language: language, requestPermission: requestPermission)
            preferences.reminder = options
        } catch { report(error) }
    }

    private func persistMediaDraft(_ value: JournalDraft, editing: Bool) async throws {
        try await store.saveDraft(value, revision: nextRevision(editing), editing: editing)
        if editing { editDraft = value } else { draft = value }
        await store.tidyAttachments()
    }

    private func refresh() async throws {
        let latest = try await store.current()
        records = latest.records
        canUndoRestore = latest.canUndoRestore
    }

    private func nextRevision(_ editing: Bool) -> Int {
        if editing {
            editRevision += 1
            return editRevision
        }
        draftRevision += 1
        return draftRevision
    }
}
