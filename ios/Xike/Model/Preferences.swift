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

enum MoodIconStyle: String, CaseIterable, Codable, Identifiable {
    case classic, duotone, line, ring
    var id: String { rawValue }
    func label(_ language: XikeLanguage) -> String {
        switch self {
        case .classic: language.text("经典样式", "Classic")
        case .duotone: language.text("柔和双色", "Soft duotone")
        case .line: language.text("极简细线", "Minimal line")
        case .ring: language.text("抽象光环", "Breathing rings")
        }
    }
    func description(_ language: XikeLanguage) -> String {
        switch self {
        case .classic: language.text("原有表情、纯文字感受与系统天气图标", "Original emoji, text-only feelings, and system weather icons")
        case .duotone: language.text("心情、感受与天气统一采用低饱和双色与圆润细线", "Muted duotone tint with smooth strokes across moods, feelings, and weather")
        case .line: language.text("与界面图标同源的克制单色线条，统一心情、感受与天气", "Quiet single-color lines matching app icons across moods, feelings, and weather")
        case .ring: language.text("去人脸化的静谧同心圆环，搭配轻柔感受与微光天气", "Face-free concentric rings paired with subtle feeling marks and soft weather")
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
    var pausedUntilDay: DateComponents?

    var pauseDeadline: Date? {
        pausedUntilDay.flatMap { Calendar.xike.date(from: $0) } ?? pausedUntil
    }

    mutating func pause(until date: Date?) {
        pausedUntil = date
        pausedUntilDay = date.map { Calendar.xike.dateComponents([.year, .month, .day], from: $0) }
    }

    func deliveredDate(_ day: Date, calendar: Calendar) -> Date? {
        guard weekdays.contains(calendar.component(.weekday, from: day)),
              let time = calendar.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: day),
              pauseDeadline.map({ time >= $0 }) ?? true else { return nil }
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
    var configuredOptions: AutoBackupOptions?
    var due: Date?
    var localDeadline: DateComponents?
    var success: Date?
    var retry: Date?
    var cleanupRetry: Date?
    var failed = false
    var files: [String] = []

    mutating func setDeadline(_ date: Date?) {
        due = date
        localDeadline = date.map { Calendar.xike.dateComponents([.year, .month, .day, .hour, .minute], from: $0) }
    }
}

struct LocalPreferences: Codable, Equatable {
    static let lockDelays = [0, 60, 300, 1_800]
    var accent = Accent.ocean
    var style = AppStyle.breathe
    var moodIconStyle = MoodIconStyle.classic
    var languageName = XikeLanguage.automatic.rawValue
    var lock = false
    var lockDelay = 0
    var dailyQuestion = false
    var promptStyle = 0
    var reminder = ReminderOptions()
    var backup = AutoBackupOptions()

    init() {}

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accent = try container.decodeIfPresent(Accent.self, forKey: .accent) ?? .ocean
        style = try container.decodeIfPresent(AppStyle.self, forKey: .style) ?? .breathe
        moodIconStyle = try container.decodeIfPresent(MoodIconStyle.self, forKey: .moodIconStyle) ?? .classic
        languageName = try container.decodeIfPresent(String.self, forKey: .languageName) ?? XikeLanguage.automatic.rawValue
        lock = try container.decodeIfPresent(Bool.self, forKey: .lock) ?? false
        lockDelay = try container.decodeIfPresent(Int.self, forKey: .lockDelay) ?? 0
        dailyQuestion = try container.decodeIfPresent(Bool.self, forKey: .dailyQuestion) ?? false
        promptStyle = try container.decodeIfPresent(Int.self, forKey: .promptStyle) ?? 0
        reminder = try container.decodeIfPresent(ReminderOptions.self, forKey: .reminder) ?? ReminderOptions()
        backup = try container.decodeIfPresent(AutoBackupOptions.self, forKey: .backup) ?? AutoBackupOptions()
    }

    var language: XikeLanguage { XikeLanguage(rawValue: languageName) ?? .automatic }

    static let storageKey = "xike-preferences-v1"
    static func read() -> Self {
        var preferences = UserDefaults.standard.data(forKey: storageKey)
            .flatMap { try? JSONDecoder().decode(Self.self, from: $0) } ?? Self()
        if preferences.lockDelay == 30 { preferences.lockDelay = 60 }
        if let options = AutomaticBackup.status().configuredOptions { preferences.backup = options }
        return preferences
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
    static let taskID = "com.xike.app.reminders"
    private static let prefix = "xike-reminder-"
    private var revision = 0
    private var pendingUpdate: Task<Void, Error>?
    private var currentOptions: ReminderOptions?

    func apply(_ options: ReminderOptions, language: XikeLanguage, requestPermission: Bool) async throws {
        try await enqueue {
            try await self.synchronize(options, language: language, requestPermission: requestPermission)
            self.currentOptions = options
        }
    }

    func reconcile() async -> Bool {
        do {
            try await enqueue {
                let prefs = LocalPreferences.read()
                let options = self.currentOptions ?? prefs.reminder
                do { try await self.synchronize(options, language: prefs.language, requestPermission: false) }
                catch {
                    if options.enabled, let deadline = options.pauseDeadline {
                        self.scheduleResume(after: max(deadline, .now.addingTimeInterval(900)))
                    }
                    throw error
                }
            }
            return true
        } catch { return false }
    }

    private func enqueue(_ update: @escaping () async throws -> Void) async throws {
        let pending = pendingUpdate
        revision += 1
        let operation = revision
        let task = Task {
            _ = await pending?.result
            try Task.checkCancellation()
            try await update()
        }
        pendingUpdate = task
        defer { if revision == operation { pendingUpdate = nil } }
        try await withTaskCancellationHandler { try await task.value } onCancel: { task.cancel() }
    }

    private func synchronize(_ options: ReminderOptions, language: XikeLanguage, requestPermission: Bool) async throws {
        let center = UNUserNotificationCenter.current()
        let existing = await center.pendingNotificationRequests().filter { $0.identifier.hasPrefix(Self.prefix) }
        try Task.checkCancellation()
        guard options.enabled else {
            center.removePendingNotificationRequests(withIdentifiers: existing.map(\.identifier))
            scheduleResume(after: nil)
            return
        }
        if requestPermission {
            guard try await center.requestAuthorization(options: [.alert, .sound]) else { throw JournalError.unavailable }
        }
        let settings = await center.notificationSettings()
        guard [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus)
        else { throw JournalError.unavailable }
        try Task.checkCancellation()

        let now = Date()
        let calendar = Calendar.xike
        let today = calendar.startOfDay(for: now)
        let content = UNMutableNotificationContent()
        content.title = language.text("息刻", "Xike")
        content.body = language.text("留一刻给自己，听听这一刻的感受。", "Take a moment to check in with yourself.")
        content.sound = .default
        content.userInfo = ["action": "record"]

        let pause = options.pauseDeadline.flatMap { $0 > now ? $0 : nil }
        let paused = pause != nil
        var effective = options
        if !paused { effective.pause(until: nil) }
        var patterns: Set<DateComponents> = []
        var dates: [DateComponents] = []
        for day in 0..<(paused ? 64 : 7) {
            guard let date = calendar.date(byAdding: .day, value: day, to: today),
                  let delivery = effective.deliveredDate(date, calendar: calendar),
                  !paused || delivery > now else { continue }
            let pattern: Set<Calendar.Component> = paused ? [.year, .month, .day, .hour, .minute] : [.weekday, .hour, .minute]
            let components = calendar.dateComponents(pattern, from: delivery)
            if patterns.insert(components).inserted { dates.append(components) }
        }
        // Reuse request IDs so switching schedules stays within the pending notification limit.
        let old = Dictionary(uniqueKeysWithValues: existing.map { ($0.identifier, $0) })
        var required = Set<String>()
        var touched: [String] = []
        do {
            for (index, components) in dates.enumerated() {
                try Task.checkCancellation()
                let id = Self.prefix + String(index)
                required.insert(id)
                if let existing = old[id], let trigger = existing.trigger as? UNCalendarNotificationTrigger,
                   trigger.repeats == !paused, trigger.dateComponents == components,
                   existing.content.body == content.body, existing.content.title == content.title { continue }
                let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: !paused)
                let request = UNNotificationRequest(identifier: id, content: content, trigger: trigger)
                touched.append(id)
                try await center.add(request)
            }
            try Task.checkCancellation()
        } catch {
            var remove: [String] = []
            for id in touched {
                if let request = old[id], let trigger = request.trigger as? UNCalendarNotificationTrigger,
                   trigger.nextTriggerDate() != nil {
                    try? await center.add(request)
                } else {
                    remove.append(id)
                }
            }
            center.removePendingNotificationRequests(withIdentifiers: remove)
            throw error
        }
        center.removePendingNotificationRequests(withIdentifiers: existing.map(\.identifier).filter { !required.contains($0) })
        scheduleResume(after: pause)
    }

    private func scheduleResume(after date: Date?) {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskID)
        guard let date else { return }
        let request = BGAppRefreshTaskRequest(identifier: Self.taskID)
        request.earliestBeginDate = date
        try? BGTaskScheduler.shared.submit(request)
    }
}

actor AutomaticBackup {
    static let taskID = "com.xike.app.backup"
    private static let statusKey = "xike-auto-backup-status-v1"
    private let store: JournalStore
    private var inProgress = false
    private var configurationRevision = 0

    init(store: JournalStore) { self.store = store }

    nonisolated static func status() -> AutoBackupStatus {
        var status = UserDefaults.standard.data(forKey: statusKey)
            .flatMap { try? JSONDecoder().decode(AutoBackupStatus.self, from: $0) } ?? AutoBackupStatus()
        if let localDeadline = status.localDeadline, let due = Calendar.xike.date(from: localDeadline) { status.due = due }
        return status
    }

    func configure(_ options: AutoBackupOptions, password: String?) throws {
        if options.enabled && options.encrypted {
            let bytes = try password.map { Data($0.utf8) } ?? DeviceSecrets.read("backup-password")
            guard let bytes, let value = String(data: bytes, encoding: .utf8), value.utf16.count >= 8 else {
                throw JournalError.password
            }
            if password != nil { try DeviceSecrets.write("backup-password", bytes) }
        }
        var status = Self.status()
        let previous = status.configuredOptions ?? LocalPreferences.read().backup
        let resetSchedule = options.enabled != previous.enabled || options.weekly != previous.weekly
            || options.weekday != previous.weekday || options.minutes != previous.minutes
        if resetSchedule || status.due == nil {
            status.setDeadline(options.enabled ? options.next(after: .now) : nil)
            status.retry = nil
        }
        if options.folder != previous.folder { status.files = [] }
        if options.folder != previous.folder || options.keep != previous.keep { status.cleanupRetry = nil }
        status.configuredOptions = options
        configurationRevision &+= 1
        save(status)
        schedule(options, status: status)
    }

    func runIfDue() async -> Bool {
        guard !inProgress else { return true }
        let prefs = LocalPreferences.read()
        var status = Self.status()
        let options = status.configuredOptions ?? prefs.backup
        guard options.enabled else { return true }
        let revision = configurationRevision
        guard let due = status.due else {
            status.configuredOptions = options
            status.setDeadline(options.next(after: .now))
            save(status)
            schedule(options, status: status)
            return true
        }
        inProgress = true
        defer { inProgress = false }
        if status.localDeadline == nil || status.configuredOptions == nil {
            status.configuredOptions = options
            status.setDeadline(due)
            save(status)
        }
        guard due <= .now, status.retry.map({ $0 <= .now }) ?? true else {
            cleanup(options, status: &status)
            schedule(options, status: status)
            return true
        }
        do {
            let key = try options.encrypted ? DeviceSecrets.read("backup-password") : nil
            let password = key.flatMap { String(data: $0, encoding: .utf8) }
            if options.encrypted && password == nil { throw JournalError.key }
            let local = try await store.export(password: password, language: prefs.language, verify: true)
            defer { try? FileManager.default.removeItem(at: local.deletingLastPathComponent()) }
            try Task.checkCancellation()
            guard revision == configurationRevision else { return true }
            let name = "Xike-\(Date.now.epochMillis)-\(UUID().uuidString.prefix(8)).\(local.pathExtension)"
            try withBackupFolder(options) { folder in
                let destination = folder.appending(path: name)
                let partial = folder.appending(path: "." + name + ".partial")
                var coordination: NSError?
                var result: Result<Void, Error>?
                NSFileCoordinator().coordinate(writingItemAt: folder, options: [], error: &coordination) { _ in
                    result = Result {
                        defer { try? FileManager.default.removeItem(at: partial) }
                        try FileManager.default.copyItem(at: local, to: partial)
                        guard try FileDigest.equals(local, partial) else { throw JournalError.io }
                        try Task.checkCancellation()
                        try FileManager.default.moveItem(at: partial, to: destination)
                    }
                }
                if let coordination { throw coordination }
                guard let result else { throw JournalError.io }
                try result.get()
            }
            status.files.append(name)
            status.success = .now
            status.failed = false
            status.retry = nil
            status.setDeadline(options.next(after: .now))
            status.cleanupRetry = status.files.count > max(1, options.keep) ? .now : nil
            save(status)
            cleanup(options, status: &status)
            schedule(options, status: status)
            return true
        } catch {
            if revision == configurationRevision {
                status.failed = true
                status.retry = Date.now.addingTimeInterval(900)
                save(status)
                schedule(options, status: status)
            }
            return false
        }
    }

    private func cleanup(_ options: AutoBackupOptions, status: inout AutoBackupStatus) {
        let keep = max(1, options.keep)
        guard status.files.count > keep, let retry = status.cleanupRetry, retry <= .now else { return }
        do {
            try withBackupFolder(options) { folder in
                while status.files.count > keep, let oldest = status.files.first {
                    try Task.checkCancellation()
                    let file = folder.appending(path: oldest)
                    do { try FileManager.default.removeItem(at: file) }
                    catch let error as NSError where error.domain == NSCocoaErrorDomain && error.code == NSFileNoSuchFileError {}
                    status.files.removeFirst()
                }
            }
            status.cleanupRetry = nil
        } catch {
            status.cleanupRetry = .now.addingTimeInterval(900)
        }
        save(status)
    }

    private func withBackupFolder<T>(_ options: AutoBackupOptions, _ work: (URL) throws -> T) throws -> T {
        let folder = try resolveFolder(options.folder)
        let accessed = folder.startAccessingSecurityScopedResource()
        defer { if accessed { folder.stopAccessingSecurityScopedResource() } }
        if options.folder != nil && !accessed { throw JournalError.io }
        return try work(folder)
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
        let nextBackup = max(due, status.retry ?? due)
        let nextCleanup = status.cleanupRetry ?? nextBackup
        request.earliestBeginDate = min(nextBackup, nextCleanup)
        try? BGTaskScheduler.shared.submit(request)
    }
}
