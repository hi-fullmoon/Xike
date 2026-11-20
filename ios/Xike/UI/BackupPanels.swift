import SwiftUI
import UIKit
import UniformTypeIdentifiers

private enum BackupPrompt: String, Identifiable {
    case export, importPassword
    var id: Self { self }
}

private struct ExportedFile: Identifiable {
    let id = UUID()
    let url: URL
}

struct BackupPanel: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    let model: JournalAppModel
    let startingWithExport: Bool
    @State private var prompt: BackupPrompt?
    @State private var encrypted = true
    @State private var password = ""
    @State private var picker = false
    @State private var source: URL?
    @State private var inspected: PreparedBackup?
    @State private var export: ExportedFile?
    @State private var busy = false
    @State private var feedback: String?
    @State private var undo = false
    @State private var opened = false

    var body: some View {
        ModalPage(title: language.text("备份与迁移", "Backup & migration")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text(language.text("导出已保存的日记、收藏和附件。", "Export saved entries, favorites and attachments."))
                        .font(.subheadline).foregroundStyle(palette.muted)
                    XikeAction(title: language.text("导出备份", "Export backup"), enabled: !busy) {
                        password = ""
                        encrypted = true
                        prompt = .export
                    }.accessibilityIdentifier("export-backup")
                    Button(language.text("导入备份", "Import backup"), systemImage: "square.and.arrow.down") {
                        picker = true
                    }.disabled(busy)
                        .frame(minHeight: 44)
                        .accessibilityIdentifier("import-backup")
                    if model.canUndoRestore {
                        Button(language.text("撤销上次恢复", "Undo last restore")) { undo = true }
                            .disabled(busy).frame(minHeight: 44)
                    }
                }
            }
            if busy { ProgressView(language.text("正在处理备份…", "Processing backup…")) }
            if let feedback { Text(feedback).font(.footnote).foregroundStyle(palette.muted) }
        }
        .task {
            guard !opened else { return }
            opened = true
            if startingWithExport { prompt = .export } else { picker = true }
        }
        .interactiveDismissDisabled(busy)
        .fileImporter(isPresented: $picker, allowedContentTypes: [
            UTType(exportedAs: "com.xike.backup"), .zip, .json, .data,
        ]) { result in
            do {
                let url = try result.get()
                let accessed = url.startAccessingSecurityScopedResource()
                defer { if accessed { url.stopAccessingSecurityScopedResource() } }
                if try BackupFileKind(url: url) == .zip {
                    Task { await inspect(url, password: "") }
                } else {
                    source = url
                    password = ""
                    prompt = .importPassword
                }
            } catch { feedback = model.message(for: error) }
        }
        .sheet(item: $prompt, content: passwordPanel)
        .sheet(item: $inspected) { backup in
            RestoreConfirmation(backup: backup, model: model)
        }
        .sheet(item: $export, onDismiss: cleanExport) { file in
            ExportPicker(file: file.url) { saved in
                if saved { feedback = language.text("备份已导出", "Backup exported") }
                try? FileManager.default.removeItem(at: file.url.deletingLastPathComponent())
                export = nil
            }
            .interactiveDismissDisabled()
        }
        .confirmationDialog(language.text("撤销上次恢复？", "Undo last restore?"), isPresented: $undo,
                            titleVisibility: .visible) {
            Button(language.text("撤销恢复", "Undo restore"), role: .destructive) {
                Task {
                    await model.undoRestore()
                    if !model.canUndoRestore { feedback = language.text("已撤销恢复", "Restore undone") }
                }
            }
            Button(language.text("取消", "Cancel"), role: .cancel) {}
        }
    }

    private func passwordPanel(_ action: BackupPrompt) -> some View {
        ModalPage(title: action == .export ? language.text("导出备份", "Export backup") : language.text("输入备份密码", "Backup password")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 14) {
                    if action == .export {
                        Toggle(language.text("使用密码加密", "Encrypt with a password"), isOn: $encrypted)
                    }
                    if encrypted || action == .importPassword {
                        SecureField(language.text("密码", "Password"), text: $password)
                            .textContentType(action == .export ? .newPassword : .password)
                            .accessibilityIdentifier("backup-password")
                        if action == .export {
                            Text(language.text("至少 8 位。恢复时需要同一个密码。", "Use at least 8 characters. Restore requires the same password."))
                                .font(.footnote).foregroundStyle(palette.muted)
                        }
                    } else {
                        Text(language.text("ZIP 中的日记和附件可直接阅读。", "The ZIP contains journals and attachments that can be read directly."))
                            .font(.footnote).foregroundStyle(palette.muted)
                    }
                }
            }
            XikeAction(title: action == .export ? language.text("继续导出", "Continue export") : language.text("读取备份", "Read backup"),
                       enabled: action == .export ? (!encrypted || password.utf16.count >= 8) : !password.isEmpty) {
                let secret = password
                password = ""
                prompt = nil
                Task {
                    if action == .export { await makeExport(password: encrypted ? secret : nil) }
                    else if let source { await inspect(source, password: secret) }
                }
            }
            .accessibilityIdentifier("backup-password-confirm")
        }.onDisappear { password = "" }
    }

    private func inspect(_ url: URL, password: String) async {
        busy = true
        feedback = nil
        defer { busy = false }
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        do { inspected = try await model.store.prepareBackup(url, password: password) }
        catch {
            feedback = model.message(for: error)
            if (error as? JournalError) == .password {
                source = url
                prompt = .importPassword
            }
        }
    }

    private func makeExport(password: String?) async {
        busy = true
        feedback = nil
        defer { busy = false }
        do {
            let url = try await model.store.export(password: password, language: language)
            export = ExportedFile(url: url)
        } catch { feedback = model.message(for: error) }
    }

    private func cleanExport() {
        if let file = export { try? FileManager.default.removeItem(at: file.url.deletingLastPathComponent()) }
        export = nil
    }
}

private struct RestoreConfirmation: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    let backup: PreparedBackup
    let model: JournalAppModel
    @State private var replace = false
    @State private var working = false
    @State private var preview: RestoreResult?
    @State private var loadingPreview = true
    @State private var feedback: String?

    var body: some View {
        ModalPage(title: language.text("恢复备份", "Restore backup")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 9) {
                    LabeledContent(language.text("日记", "Entries"), value: String(backup.contents.entries.count))
                    LabeledContent(language.text("照片", "Photos"), value: String(backup.photoCount))
                    LabeledContent(language.text("录音", "Audio"), value: String(backup.audioCount))
                    LabeledContent(language.text("视频", "Videos"), value: String(backup.videoCount))
                    LabeledContent(language.text("收藏", "Favorites"), value: String(backup.contents.entries.filter(\.isFavorite).count))
                }
            }
            Text(language.text("合并保留本机记录，内容冲突时保留双方版本。", "Merge keeps local entries and preserves both versions of a conflict."))
                .font(.subheadline).foregroundStyle(palette.muted)
            if let preview {
                let added = preview.additions - preview.conflicts
                Text(language.text(
                    "预计新增 \(added) 条，保留冲突副本 \(preview.conflicts) 条，更新收藏 \(preview.favorites) 条。恢复时会重新核对。",
                    "Estimated: \(added) new entries, \(preview.conflicts) conflict copies, and \(preview.favorites) favorite updates. Checked again when restoring."))
                    .font(.footnote).foregroundStyle(palette.muted).accessibilityIdentifier("restore-merge-summary")
            } else if loadingPreview {
                ProgressView(language.text("正在估算合并结果…", "Estimating merge results…"))
            }
            if let feedback { Text(feedback).font(.footnote).foregroundStyle(palette.muted) }
            XikeAction(title: language.text("合并恢复", "Merge backup"), enabled: !working && !loadingPreview) { restore(.merge) }
                .accessibilityIdentifier("restore-merge")
            Button(language.text("替换全部内容", "Replace all entries"), role: .destructive) { replace = true }
                .disabled(working).frame(minHeight: 44)
            if working { ProgressView(language.text("正在恢复…", "Restoring…")) }
        }
        .interactiveDismissDisabled(working)
        .task(id: model.libraryRevision) {
            guard !working else { return }
            loadingPreview = true
            defer { loadingPreview = false }
            do {
                let counts = try await model.store.previewMerge(backup)
                if !Task.isCancelled { preview = counts }
            } catch {
                if !Task.isCancelled { feedback = model.message(for: error) }
            }
        }
        .confirmationDialog(language.text("替换本机的全部记录？", "Replace all local entries?"), isPresented: $replace, titleVisibility: .visible) {
            Button(language.text("确认替换", "Confirm replacement"), role: .destructive) { restore(.replace) }
            Button(language.text("取消", "Cancel"), role: .cancel) {}
        }
    }

    private func restore(_ mode: RestoreMode) {
        working = true
        feedback = nil
        Task {
            defer { working = false }
            do {
                try await model.applyBackup(backup, mode: mode)
                dismiss()
            } catch { feedback = model.message(for: error) }
        }
    }
}

private struct ExportPicker: UIViewControllerRepresentable {
    let file: URL
    let complete: (Bool) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(complete) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forExporting: [file], asCopy: true)
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ controller: UIDocumentPickerViewController, context: Context) {}

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let complete: (Bool) -> Void
        init(_ complete: @escaping (Bool) -> Void) { self.complete = complete }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { complete(!urls.isEmpty) }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { complete(false) }
    }
}

struct AutoBackupPanel: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss
    let model: JournalAppModel
    @State var draft: AutoBackupOptions
    @State private var password = ""
    @State private var hasPassword = false
    @State private var chooseFolder = false
    @State private var feedback: String?
    @State private var status = AutomaticBackup.status()
    @State private var saving = false

    var body: some View {
        ModalPage(title: language.text("自动备份", "Automatic backup")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 12) {
                    Toggle(language.text("自动备份", "Automatic backup"), isOn: $draft.enabled)
                    if draft.enabled {
                        Picker(language.text("频率", "Frequency"), selection: $draft.weekly) {
                            Text(language.text("每天", "Daily")).tag(false)
                            Text(language.text("每周", "Weekly")).tag(true)
                        }
                        if draft.weekly {
                            Picker(language.text("星期", "Day"), selection: $draft.weekday) {
                                ForEach([2, 3, 4, 5, 6, 7, 1], id: \.self) { Text(weekdayLabel($0, language)).tag($0) }
                            }
                        }
                        DatePicker(language.text("计划时间", "Scheduled time"), selection: minuteBinding($draft.minutes),
                                   displayedComponents: .hourAndMinute)
                        Stepper(language.text("保留 \(draft.keep) 份", "Keep \(draft.keep) backups"), value: $draft.keep, in: 1...100)
                        Toggle(language.text("使用密码加密", "Encrypt with a password"), isOn: $draft.encrypted)
                        if draft.encrypted {
                            SecureField(hasPassword ? language.text("留空沿用现有密码", "Leave blank to keep password")
                                                        : language.text("密码，至少 8 位", "Password, at least 8 characters"),
                                        text: $password)
                        }
                        Button(language.text("选择备份位置", "Choose backup folder")) { chooseFolder = true }
                        Text(draft.folderLabel ?? language.text("在我的 iPhone 上 / 息刻 / Backups", "On My iPhone / Xike / Backups"))
                            .font(.footnote).foregroundStyle(palette.muted)
                        if draft.folder != nil {
                            Button(language.text("使用默认目录", "Use default folder")) { draft.folder = nil; draft.folderLabel = nil }
                        }
                    }
                }
            }
            XikeCard {
                VStack(alignment: .leading, spacing: 8) {
                    LabeledContent(language.text("下次计划", "Next scheduled"), value: status.due.map(formatDate) ?? "—")
                    LabeledContent(language.text("上次成功", "Last success"), value: status.success.map(formatDate) ?? "—")
                    if status.failed {
                        Text(language.text("上次备份失败。息刻会重试。", "The last backup failed. Xike will retry."))
                            .font(.footnote)
                    }
                    if status.cleanupRetry != nil {
                        Text(language.text("旧备份暂时无法清理，息刻会重试。", "Older backups could not be removed. Xike will retry."))
                            .font(.footnote)
                    }
                    Text(language.text("后台执行时机会随系统条件变化，打开息刻时会检查到期备份。", "iOS chooses when background tasks run. Xike checks for due backups when opened."))
                        .font(.footnote).foregroundStyle(palette.muted)
                }
            }
            if let feedback { Text(feedback).font(.footnote).foregroundStyle(.red) }
            XikeAction(title: language.text("保存", "Save"), enabled: valid && !saving) { save() }
        }
        .interactiveDismissDisabled(saving)
        .task { hasPassword = ((try? DeviceSecrets.read("backup-password")) != nil) }
        .fileImporter(isPresented: $chooseFolder, allowedContentTypes: [.folder]) { result in
            do {
                let url = try result.get()
                guard url.startAccessingSecurityScopedResource() else { throw JournalError.io }
                defer { url.stopAccessingSecurityScopedResource() }
                draft.folder = try url.bookmarkData(options: .minimalBookmark)
                draft.folderLabel = url.lastPathComponent
            } catch { feedback = model.message(for: error) }
        }
    }

    private var valid: Bool {
        !draft.enabled || !draft.encrypted || (password.isEmpty && hasPassword) || password.utf16.count >= 8
    }

    private func save() {
        let options = draft
        let newPassword = password.isEmpty ? nil : password
        saving = true
        Task {
            defer { saving = false }
            do {
                try await model.automaticBackup.configure(options, password: newPassword)
                model.preferences.backup = options
                password = ""
                dismiss()
            } catch { feedback = model.message(for: error) }
        }
    }

    private func formatDate(_ date: Date) -> String {
        date.formatted(.dateTime.month().day().hour().minute().locale(language.locale))
    }
}
