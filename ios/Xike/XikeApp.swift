import BackgroundTasks
import SwiftUI
import UIKit
import UserNotifications

@main
struct XikeApp: App {
    @UIApplicationDelegateAdaptor(XikeAppDelegate.self) private var delegate
    @State private var model = JournalAppModel(store: XikeServices.store)

    var body: some Scene {
        WindowGroup {
            XikeRoot(model: model)
                .environment(\.xikeLanguage, model.language)
                .environment(\.locale, model.language.locale)
        }
    }
}

enum XikeServices {
    static let store = JournalStore()
    static let automaticBackup = AutomaticBackup(store: store)
    static let reminders = JournalReminders()
}

extension Notification.Name {
    fileprivate static let quickRecord = Notification.Name("XikeQuickRecord")
}

final class XikeAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        BGTaskScheduler.shared.register(forTaskWithIdentifier: AutomaticBackup.taskID, using: nil) { task in
            let worker = Task {
                let success = await XikeServices.automaticBackup.runIfDue()
                task.setTaskCompleted(success: success && !Task.isCancelled)
            }
            task.expirationHandler = { worker.cancel() }
        }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: JournalReminders.taskID, using: nil) { task in
            let worker = Task {
                let success = await XikeServices.reminders.reconcile()
                task.setTaskCompleted(success: success && !Task.isCancelled)
            }
            task.expirationHandler = { worker.cancel() }
        }
        application.shortcutItems = [UIApplicationShortcutItem(type: "record",
            localizedTitle: LocalPreferences.read().language.text("记录此刻", "Record now"),
            localizedSubtitle: nil, icon: UIApplicationShortcutIcon(systemImageName: "square.and.pencil"), userInfo: nil)]
        return true
    }

    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        if options.shortcutItem?.type == "record" { XikeSceneDelegate.pendingQuickRecord = true }
        let configuration = UISceneConfiguration(name: nil, sessionRole: session.role)
        configuration.delegateClass = XikeSceneDelegate.self
        return configuration
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse) async {
        if response.notification.request.content.userInfo["action"] as? String == "record" {
            await MainActor.run { XikeSceneDelegate.quickRecord() }
        }
    }
}

final class XikeSceneDelegate: NSObject, UIWindowSceneDelegate {
    static var pendingQuickRecord = false
    private var snapshotCover: UIWindow?

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem,
                     completionHandler: @escaping (Bool) -> Void) {
        if shortcutItem.type == "record" { Self.quickRecord(); completionHandler(true) }
        else { completionHandler(false) }
    }

    static func quickRecord() {
        pendingQuickRecord = true
        NotificationCenter.default.post(name: .quickRecord, object: nil)
    }

    func sceneWillResignActive(_ scene: UIScene) {
        guard let windowScene = scene as? UIWindowScene, snapshotCover == nil else { return }
        let window = UIWindow(windowScene: windowScene)
        window.windowLevel = .alert + 1
        window.isUserInteractionEnabled = false
        let cover = UIViewController()
        cover.view.backgroundColor = .systemBackground
        window.rootViewController = cover
        window.isHidden = false
        snapshotCover = window
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        snapshotCover?.isHidden = true
        snapshotCover = nil
    }
}

private struct XikeRoot: View {
    @Environment(\.scenePhase) private var phase
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.xikeLanguage) private var language
    @Bindable var model: JournalAppModel

    private var palette: XikePalette {
        XikePalette(accent: model.preferences.accent, style: model.preferences.style, dark: colorScheme == .dark)
    }

    var body: some View {
        ZStack {
            palette.background.ignoresSafeArea()
            if model.isLocked {
                lockScreen
            } else if let error = model.blocked {
                VStack(spacing: 16) {
                    Label(error, systemImage: "exclamationmark.circle").multilineTextAlignment(.center)
                    XikeAction(title: language.text("重试", "Try again")) { Task { await model.start() } }
                }.padding(24)
            } else if !model.isLoaded {
                ProgressView(language.text("正在打开息刻…", "Opening Xike…"))
            } else {
                VStack(spacing: 0) {
                    page
                    XikeNavigation(selection: $model.tab)
                }
                .overlay(alignment: .top) {
                    if let notice = model.notice {
                        HStack {
                            Text(notice.message).font(.callout)
                            Spacer()
                            if let label = notice.action, let action = notice.perform {
                                Button(label, action: action).font(.callout.weight(.semibold))
                            }
                        }
                        .padding(13)
                        .background(palette.surface, in: RoundedRectangle(cornerRadius: palette.buttonRadius))
                        .shadow(color: .black.opacity(0.13), radius: 10, y: 2)
                        .padding(.horizontal, palette.horizontal).padding(.top, 8)
                        .accessibilityIdentifier("journal-notice")
                    }
                }
            }
            if phase != .active || !model.sceneIsActive {
                palette.background.ignoresSafeArea().overlay { BrandMark(diameter: 54) }
                    .accessibilityHidden(true)
            }
        }
        .foregroundStyle(palette.text)
        .tint(palette.primary)
        .environment(\.palette, palette)
        .onChange(of: phase, initial: true) { _, phase in
            model.phaseChanged(phase)
            consumeShortcut()
            if phase == .active { Task { await model.refreshForeground() } }
        }
        .onReceive(NotificationCenter.default.publisher(for: .quickRecord)) { _ in consumeShortcut() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            Task { await model.refreshForeground() }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.protectedDataWillBecomeUnavailableNotification)) { _ in
            model.lockNow()
        }
        .alert(language.text("操作提示", "Xike"), isPresented: Binding(get: { model.errorMessage != nil },
               set: { if !$0 { model.errorMessage = nil } })) {
            Button(language.text("好", "OK")) { model.errorMessage = nil }
        } message: { Text(model.errorMessage ?? "") }
    }

    @ViewBuilder
    private var page: some View {
        switch model.tab {
        case .moment: JournalComposer(model: model)
        case .archive: ArchiveScreen(model: model)
        case .insights: InsightsScreen(model: model)
        case .settings: SettingsScreen(model: model)
        }
    }

    private var lockScreen: some View {
        VStack(spacing: 16) {
            BrandMark(diameter: 60)
            XikeTitle(title: language.text("息刻已锁定", "Xike is locked"))
            Text(language.text("解锁后继续查看本机记录。", "Unlock to see your entries on this device."))
                .font(.subheadline).foregroundStyle(palette.muted)
            XikeAction(title: language.text("解锁息刻", "Unlock Xike"), enabled: !model.isAuthenticating) {
                Task { await model.unlock() }
            }
        }.padding(28)
    }

    private func consumeShortcut() {
        if XikeSceneDelegate.pendingQuickRecord {
            model.tab = .moment
            XikeSceneDelegate.pendingQuickRecord = false
        }
    }
}
