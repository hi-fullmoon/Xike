import SwiftUI

struct AMapSettingsPanel: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @State private var draft = AMapPreferences()
    @State private var feedback: String?
    @State private var testing: Task<Void, Never>?
    @State private var revealKey = false

    var body: some View {
        ModalPage(title: language.text("高德定位配置", "AMap location settings")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text(language.text("高德会处理经纬度及提供定位所需的设备、网络信息。只有主动定位或测试时才会发起请求。", "AMap processes location and relevant device and network information when you request location or run a test."))
                        .font(.footnote).foregroundStyle(palette.muted)
                    Link(language.text("高德隐私政策", "AMap privacy policy"), destination: URL(string: "https://lbs.amap.com/pages/privacy/")!)
                    Text(Bundle.main.bundleIdentifier ?? "com.xike.app").font(.caption.monospaced()).textSelection(.enabled)
                    if XikeAMapClient.available {
                        Group {
                            if revealKey { TextField(" iOS SDK Key", text: $draft.key) }
                            else { SecureField("iOS SDK Key", text: $draft.key) }
                        }.autocorrectionDisabled().textInputAutocapitalization(.never)
                        HStack {
                            Button(revealKey ? language.text("隐藏", "Hide") : language.text("显示", "Show")) { revealKey.toggle() }
                            Spacer()
                            Button(language.text("清除", "Clear"), role: .destructive) { draft.key = ""; draft.enabled = false }
                        }
                        Toggle(language.text("已阅读并同意高德隐私政策", "I agree to AMap’s privacy policy"), isOn: $draft.consent)
                            .onChange(of: draft.consent) { _, consent in if !consent { draft.enabled = false } }
                        Toggle(language.text("使用高德定位", "Use AMap location"), isOn: $draft.enabled)
                            .disabled(!draft.consent || key.isEmpty)
                    } else {
                        Text(language.text("在 iPhone 真机上可配置高德定位。", "AMap location is available on a physical iPhone."))
                            .font(.footnote).foregroundStyle(palette.muted)
                    }
                }
            }
            if XikeAMapClient.available {
                if let testing {
                    ProgressView(language.text("正在测试…", "Testing…"))
                    Button(language.text("取消", "Cancel")) { testing.cancel(); self.testing = nil }
                } else {
                    Button(language.text("测试定位", "Test location"), action: test)
                        .disabled(!draft.consent || key.isEmpty).frame(minHeight: 44)
                    XikeAction(title: language.text("保存", "Save")) {
                        do {
                            draft.key = key
                            if draft.key.isEmpty { draft.enabled = false }
                            try draft.save()
                            dismiss()
                        } catch { feedback = JournalError.key.message(language) }
                    }
                }
            }
            if let feedback { Text(feedback).font(.footnote).foregroundStyle(palette.muted) }
        }
        .task { do { draft = try AMapPreferences.read() } catch { feedback = JournalError.key.message(language) } }
        .onDisappear { testing?.cancel() }
        .onChange(of: phase) { _, phase in if phase == .background { testing?.cancel(); testing = nil } }
    }

    private var key: String { draft.key.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func test() {
        let secret = key
        feedback = nil
        testing = Task {
            do {
                let result = try await OneTimeAMap().locate(key: secret, language: language)
                if !Task.isCancelled { feedback = result.place }
            } catch {
                if !Task.isCancelled { feedback = language.text("定位失败，请检查 Key、定位权限和网络。", "Check the key, location permission and connection.") }
            }
            if !Task.isCancelled { testing = nil }
        }
    }
}
