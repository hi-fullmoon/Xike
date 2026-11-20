import BackgroundTasks
import Foundation
import LocalAuthentication
import UIKit
import UserNotifications

enum Accent: String, CaseIterable, Codable, Identifiable {
    case pine, violet, ocean, terra, amber, rose
    var id: String { rawValue }
    func label(_ language: XikeLanguage) -> String {
        switch self {
        case .pine: language.text("雾松", "Pine")
        case .violet: language.text("暮紫", "Violet")
        case .ocean: language.text("潮汐", "Ocean")
        case .terra: language.text("陶日", "Terra")
        case .amber: language.text("麦芒", "Amber")
        case .rose: language.text("山茶", "Rose")
        }
    }

    func description(_ language: XikeLanguage) -> String {
        switch self {
        case .pine: language.text("森林绿", "Forest green")
        case .violet: language.text("暮色紫", "Dusk purple")
        case .ocean: language.text("雾蓝绿", "Misty teal")
        case .terra: language.text("赤陶色", "Terracotta")
        case .amber: language.text("琥珀金", "Amber gold")
        case .rose: language.text("柔雾红", "Soft rose")
        }
    }
}

enum AppStyle: String, CaseIterable, Codable, Identifiable {
    case breathe, paper, focus
    var id: String { rawValue }
    func label(_ language: XikeLanguage) -> String {
        switch self {
        case .breathe: language.text("呼吸", "Breathe")
        case .paper: language.text("纸页", "Paper")
        case .focus: language.text("专注", "Focus")
        }
    }
    func description(_ language: XikeLanguage) -> String {
        switch self {
        case .breathe: language.text("圆润留白，像一段缓慢的停顿", "Soft curves and space for a gentle pause")
        case .paper: language.text("克制线条，像翻开一册手记", "Quiet lines, like opening a journal")
        case .focus: language.text("紧凑清晰，让内容更快抵达", "Compact and clear, with content close at hand")
        }
    }
}

struct ReminderOptions: Codable, Equatable {
    var enabled = false
    var weekdays = Set(1...7)
    var minutes = 20 * 60 + 30
    var quiet = true
    var quietStart = 22 * 60
    var quietEnd = 8 * 60
    var pausedUntil: Date?

    func deliveredDate(_ day: Date, calendar: Calendar) -> Date? {
        guard weekdays.contains(calendar.component(.weekday, from: day)),
              let time = calendar.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: day),
              pausedUntil.map({ time >= $0 }) ?? true else { return nil }
        guard quiet, quietStart != quietEnd else { return time }
        let crossesMidnight = quietStart > quietEnd
        let quietTime = crossesMidnight ? minutes >= quietStart || minutes < quietEnd
            : minutes >= quietStart && minutes < quietEnd
        guard quietTime else { return time }
        let quietDay = crossesMidnight && minutes >= quietStart ? calendar.date(byAdding: .day, value: 1, to: day) : day
        return quietDay.flatMap {
            calendar.date(bySettingHour: quietEnd / 60, minute: quietEnd % 60, second: 0, of: $0)
        }
    }
}

struct AutoBackupOptions: Codable, Equatable {
    var enabled = false
    var encrypted = true
    var weekly = true
    var weekday = 1
    var minutes = 0
    var keep = 5
    var folder: Data?
    var folderLabel: String?

    func next(after date: Date) -> Date? {
        var components = DateComponents(hour: minutes / 60, minute: minutes % 60, second: 0)
        if weekly { components.weekday = weekday }
        return Calendar.xike.nextDate(after: date, matching: components, matchingPolicy: .nextTime)
    }
}

struct AutoBackupStatus: Codable {
    var due: Date?
    var success: Date?
    var retry: Date?
    var failed = false
    var files: [String] = []
}

struct LocalPreferences: Codable, Equatable {
    var accent = Accent.ocean
    var style = AppStyle.breathe
    var languageName = XikeLanguage.automatic.rawValue
    var lock = false
    var lockDelay = 0
    var dailyQuestion = false
    var promptStyle = 0
    var reminder = ReminderOptions()
    var backup = AutoBackupOptions()

    var language: XikeLanguage { XikeLanguage(rawValue: languageName) ?? .automatic }

    static let storageKey = "xike-preferences-v1"
    static func read() -> Self {
        UserDefaults.standard.data(forKey: storageKey)
            .flatMap { try? JSONDecoder().decode(Self.self, from: $0) } ?? Self()
    }
    func save() {
        UserDefaults.standard.set(try? JSONEncoder().encode(self), forKey: Self.storageKey)
    }
}

enum ApplicationLock {
    static func authenticate(_ language: XikeLanguage) async throws {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            throw JournalError.unavailable
        }
        guard try await context.evaluatePolicy(.deviceOwnerAuthentication,
                localizedReason: language.text("解锁保存在这台设备上的记录", "Unlock journals on this device"))
        else { throw JournalError.key }
    }
}

actor JournalReminders {
    private static let prefix = "xike-reminder-"
    private var revision = 0

    func apply(_ options: ReminderOptions, language: XikeLanguage, requestPermission: Bool) async throws {
        revision += 1
        let operation = revision
        let center = UNUserNotificationCenter.current()
        let existing = await center.pendingNotificationRequests()
        guard operation == revision else { return }
        center.removePendingNotificationRequests(withIdentifiers: existing.map(\.identifier).filter { $0.hasPrefix(Self.prefix) })
        guard options.enabled else { return }
        if requestPermission {
            guard try await center.requestAuthorization(options: [.alert, .sound]) else { throw JournalError.unavailable }
        }
        let settings = await center.notificationSettings()
        guard [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus)
        else { throw JournalError.unavailable }
        guard operation == revision else { return }

        let now = Date()
        let calendar = Calendar.xike
        let today = calendar.startOfDay(for: now)
        let content = UNMutableNotificationContent()
        content.title = language.text("息刻", "Xike")
        content.body = language.text("留一刻给自己，听听这一刻的感受。", "Take a moment to check in with yourself.")
        content.sound = .default
        content.userInfo = ["action": "record"]

        let paused = options.pausedUntil.map { $0 > now } ?? false
        var dates = Set<DateComponents>()
        for day in 0..<(paused ? 64 : 7) {
            guard let date = calendar.date(byAdding: .day, value: day, to: today),
                  let delivery = options.deliveredDate(date, calendar: calendar),
                  !paused || delivery > now else { continue }
            let pattern: Set<Calendar.Component> = paused ? [.year, .month, .day, .hour, .minute] : [.weekday, .hour, .minute]
            dates.insert(calendar.dateComponents(pattern, from: delivery))
        }
        for (index, components) in dates.enumerated() {
            guard operation == revision else { return }
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: !paused)
            let request = UNNotificationRequest(identifier: Self.prefix + String(index), content: content, trigger: trigger)
            try await center.add(request)
        }
    }
}

actor AutomaticBackup {
    static let taskID = "com.xike.app.backup"
    private static let statusKey = "xike-auto-backup-status-v1"
    private let store: JournalStore
    private var inProgress = false

    init(store: JournalStore) { self.store = store }

    nonisolated static func status() -> AutoBackupStatus {
        UserDefaults.standard.data(forKey: statusKey)
            .flatMap { try? JSONDecoder().decode(AutoBackupStatus.self, from: $0) } ?? AutoBackupStatus()
    }

    func configure(_ options: AutoBackupOptions, resetSchedule: Bool, resetFolder: Bool) {
        var status = Self.status()
        if resetSchedule || status.due == nil {
            status.due = options.enabled ? options.next(after: .now) : nil
            status.retry = nil
        }
        if resetFolder { status.files = [] }
        save(status)
        schedule(options, status: status)
    }

    func runIfDue() async -> Bool {
        guard !inProgress else { return true }
        let prefs = LocalPreferences.read()
        let options = prefs.backup
        var status = Self.status()
        guard options.enabled else { return true }
        guard let due = status.due else {
            configure(options, resetSchedule: true, resetFolder: false)
            return true
        }
        guard due <= .now, status.retry.map({ $0 <= .now }) ?? true else {
            schedule(options, status: status)
            return true
        }
        inProgress = true
        defer { inProgress = false }
        do {
            let key = try options.encrypted ? DeviceSecrets.read("backup-password") : nil
            let password = key.flatMap { String(data: $0, encoding: .utf8) }
            if options.encrypted && password == nil { throw JournalError.key }
            let local = try await store.export(password: password, language: prefs.language)
            defer { try? FileManager.default.removeItem(at: local.deletingLastPathComponent()) }
            try Task.checkCancellation()
            guard LocalPreferences.read().backup == options else { return true }
            let folder = try resolveFolder(options.folder)
            let accessed = folder.startAccessingSecurityScopedResource()
            defer { if accessed { folder.stopAccessingSecurityScopedResource() } }
            if options.folder != nil && !accessed { throw JournalError.io }
            let name = "Xike-\(Date.now.epochMillis)-\(UUID().uuidString.prefix(8)).\(local.pathExtension)"
            let destination = folder.appending(path: name)
            let partial = folder.appending(path: "." + name + ".partial")
            var coordination: NSError?
            var result: Result<Void, Error>?
            NSFileCoordinator().coordinate(writingItemAt: folder, options: [], error: &coordination) { _ in
                result = Result {
                    defer { try? FileManager.default.removeItem(at: partial) }
                    try FileManager.default.copyItem(at: local, to: partial)
                    guard try FileDigest.equals(local, partial) else { throw JournalError.io }
                    try FileManager.default.moveItem(at: partial, to: destination)
                }
            }
            if let coordination { throw coordination }
            guard let result else { throw JournalError.io }
            try result.get()
            status.files.append(name)
            status.success = .now
            status.failed = false
            status.retry = nil
            status.due = options.next(after: .now)
            save(status)
            while status.files.count > max(1, options.keep), let oldest = status.files.first {
                let file = folder.appending(path: oldest)
                if FileManager.default.fileExists(atPath: file.path) {
                    do { try FileManager.default.removeItem(at: file) }
                    catch { break }
                }
                status.files.removeFirst()
            }
            save(status)
            schedule(options, status: status)
            return true
        } catch {
            if LocalPreferences.read().backup == options {
                status.failed = true
                status.retry = Date.now.addingTimeInterval(900)
                save(status)
                schedule(options, status: status)
            }
            return false
        }
    }

    private func resolveFolder(_ bookmark: Data?) throws -> URL {
        if let bookmark {
            var stale = false
            let url = try URL(resolvingBookmarkData: bookmark, bookmarkDataIsStale: &stale)
            guard !stale else { throw JournalError.io }
            return url
        }
        let documents = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask,
                                                     appropriateFor: nil, create: true)
        let folder = documents.appending(path: "Backups", directoryHint: .isDirectory)
        try SecureDirectory.make(folder)
        return folder
    }

    private func save(_ status: AutoBackupStatus) {
        UserDefaults.standard.set(try? JSONEncoder().encode(status), forKey: Self.statusKey)
    }

    private func schedule(_ options: AutoBackupOptions, status: AutoBackupStatus) {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskID)
        guard options.enabled, let due = status.due else { return }
        let request = BGProcessingTaskRequest(identifier: Self.taskID)
        request.earliestBeginDate = max(due, status.retry ?? due)
        try? BGTaskScheduler.shared.submit(request)
    }
}
