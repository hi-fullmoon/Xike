import SwiftUI

struct AISettingsPanel: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    var onSave: (() -> Void)?
    @State private var settings = AISettings()
    @State private var initial = AISettings()
    @State private var feedback: String?
    @State private var testing: Task<Void, Never>?
    @State private var discard = false

    var body: some View {
        NavigationStack {
            PageScroll {
                XikeCard {
                    VStack(alignment: .leading, spacing: 12) {
                        TextField(language.text("HTTPS 服务地址", "HTTPS service URL"), text: $settings.address)
                            .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                            .onChange(of: settings.endpoint) { old, new in
                                if old != new && new != initial.endpoint {
                                    settings.key = ""
                                    settings.enabled = false
                                    settings.consent = false
                                }
                            }
                        Divider()
                        SecureField("API Key", text: $settings.key).textInputAutocapitalization(.never).autocorrectionDisabled()
                        Divider()
                        TextField(language.text("模型", "Model"), text: $settings.model).textInputAutocapitalization(.never)
                    }
                }
                XikeCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(language.text("发送时，所选记录内容会交给你配置的 AI 服务。你可以先在本机查看完整请求。", "Selected data goes to the AI service you configure. You can review the full request on this device before sending."))
                            .font(.footnote).foregroundStyle(palette.muted)
                        Toggle(language.text("同意将所选内容发送给该服务", "I agree to send selected data to this service"),
                               isOn: $settings.consent)
                            .onChange(of: settings.consent) { _, consent in settings.enabled = consent && settings.ready }
                        Toggle(language.text("启用 AI 回顾", "Enable AI review"), isOn: $settings.enabled)
                            .disabled(!settings.consent || !settings.ready)
                    }
                }
                HStack {
                    if let testing {
                        Button(language.text("取消测试", "Cancel test")) { testing.cancel(); self.testing = nil }
                    } else {
                        Button(language.text("测试连接", "Test connection")) { test() }.disabled(!settings.ready)
                    }
                    Spacer()
                    if testing != nil { ProgressView() }
                }
                if let feedback { Text(feedback).font(.footnote).foregroundStyle(palette.muted) }
                XikeAction(title: language.text("保存", "Save"), enabled: testing == nil && settings.validFields
                           && (settings.address.isEmpty || settings.endpoint != nil)) { save() }
            }.background(palette.background)
                .navigationTitle(language.text("AI 回顾配置", "AI review settings"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(language.text("关闭", "Close")) {
                            if settings != initial { discard = true } else { dismiss() }
                        }
                    }
                }
        }
        .interactiveDismissDisabled(settings != initial)
        .task {
            do {
                let value = try AISettings.load()
                initial = value
                settings = value
            } catch { feedback = JournalError.key.message(language) }
        }
        .onDisappear { testing?.cancel(); testing = nil }
        .confirmationDialog(language.text("放弃未保存的配置？", "Discard unsaved settings?"), isPresented: $discard, titleVisibility: .visible) {
            Button(language.text("放弃", "Discard"), role: .destructive) { dismiss() }
            Button(language.text("继续编辑", "Keep editing"), role: .cancel) {}
        }
    }

    private func save() {
        var value = settings
        value.address = value.address.trimmingCharacters(in: .whitespacesAndNewlines)
        value.key = value.key.trimmingCharacters(in: .whitespacesAndNewlines)
        value.model = value.model.trimmingCharacters(in: .whitespacesAndNewlines)
        if !value.ready { value.enabled = false }
        do {
            try value.save()
            onSave?()
            dismiss()
        } catch { feedback = JournalError.unavailable.message(language) }
    }

    private func test() {
        let snapshot = settings
        feedback = language.text("正在测试…", "Testing…")
        testing = Task {
            do {
                try await AIReview.test(snapshot)
                if !Task.isCancelled { feedback = language.text("连接成功", "Connection succeeded") }
            } catch {
                if !Task.isCancelled { feedback = language.text("测试失败，请检查配置。", "Test failed. Check the service settings.") }
            }
            if !Task.isCancelled { testing = nil }
        }
    }
}

struct AIReviewPanel: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    let model: JournalAppModel
    let review: JournalReview
    @State private var settings = AISettings()
    @State private var includeNotes = false
    @State private var preview: AIReviewPreview?
    @State private var report: AIReport?
    @State private var feedback: String?
    @State private var configuring = false
    @State private var confirmReset = false
    @State private var request: Task<Void, Never>?
    @State private var started: Date?
    @State private var reference: RecordLink?

    var body: some View {
        ModalPage(title: language.text("AI 回顾", "AI review")) {
            if let report, let preview {
                reportView(report, preview: preview)
            } else if let preview {
                reviewConfirmation(preview)
            } else {
                prepareView
            }
            if let feedback { Text(feedback).font(.footnote).foregroundStyle(palette.muted) }
        }
        .task { reloadSettings() }
        .interactiveDismissDisabled(request != nil || report != nil)
        .onChange(of: model.libraryRevision) { cancel(); dismiss() }
        .onChange(of: model.insightsPeriod) { cancel(); dismiss() }
        .onDisappear { cancel() }
        .sheet(isPresented: $configuring) { AISettingsPanel(onSave: reloadSettings) }
        .sheet(item: $reference) { JournalDetail(model: model, recordID: $0.id) }
        .confirmationDialog(language.text("重新准备回顾？", "Prepare another review?"), isPresented: $confirmReset, titleVisibility: .visible) {
            Button(language.text("重新准备", "Prepare again"), role: .destructive) { preview = nil; report = nil; feedback = nil }
            Button(language.text("取消", "Cancel"), role: .cancel) {}
        }
    }

    private var prepareView: some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 14) {
                Text(language.text("使用轨迹页选定的周期。先在本机准备和预览。", "Uses the period selected in Patterns. Prepare and preview on this device first."))
                    .font(.subheadline).foregroundStyle(palette.muted)
                LabeledContent(language.text("记录数", "Entries"), value: String(review.current.entries.count))
                if review.current.entries.isEmpty {
                    Text(language.text("当前周期没有可用于回顾的记录。", "There are no entries to review in this period."))
                        .font(.footnote).foregroundStyle(palette.muted)
                }
                Toggle(language.text("包含日记文字", "Include journal text"), isOn: $includeNotes)
                Button(language.text("配置 AI 服务", "Configure AI service"), systemImage: "gearshape") { configuring = true }
                XikeAction(title: language.text("预览发送内容", "Preview request"), enabled: settings.canPreview && !review.current.entries.isEmpty) {
                    do { preview = try AIReview.prepare(settings: settings, review: review, includeNotes: includeNotes, language: language) }
                    catch { feedback = model.message(for: error) }
                }
            }
        }
    }

    private func reviewConfirmation(_ preview: AIReviewPreview) -> some View {
        XikeCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(language.text("确认发送", "Confirm sending")).font(.headline)
                Text(preview.endpoint.absoluteString).font(.footnote).textSelection(.enabled)
                LabeledContent(language.text("模型", "Model"), value: preview.model)
                LabeledContent(language.text("周期", "Period"), value:
                    "\(preview.start.formatted(.dateTime.month().day().locale(language.locale))) – \(preview.end.formatted(.dateTime.month().day().locale(language.locale)))")
                LabeledContent(language.text("记录数", "Entries"), value: String(preview.sources.count))
                LabeledContent(language.text("日记原文", "Journal text"), value: preview.includeNotes ? language.text("包含", "Included") : language.text("本机保留", "Kept on device"))
                DisclosureGroup(language.text("完整请求", "Complete request")) {
                    Text(String(decoding: preview.body, as: UTF8.self)).font(.caption.monospaced()).textSelection(.enabled)
                }
                if let started, request != nil {
                    TimelineView(.periodic(from: started, by: 1)) { timeline in
                        ProgressView(language.text("已等待 \(Int(timeline.date.timeIntervalSince(started))) 秒",
                                                   "Waiting \(Int(timeline.date.timeIntervalSince(started))) s"))
                    }
                    Button(language.text("取消请求", "Cancel request")) { cancel() }
                } else {
                    Button(language.text("配置 AI 服务", "Configure AI service"), systemImage: "gearshape") { configuring = true }
                    XikeAction(title: language.text("同意并发送", "Agree and send"), enabled: settings.canSend) { send(preview) }
                    Button(language.text("返回修改范围", "Change scope")) { self.preview = nil; feedback = nil }
                }
            }
        }
    }

    @ViewBuilder
    private func reportView(_ report: AIReport, preview: AIReviewPreview) -> some View {
        ForEach(report.observations) { observation in
            XikeCard {
                VStack(alignment: .leading, spacing: 9) {
                    Text(observation.title).font(.headline).textSelection(.enabled)
                    Text(observation.text).font(.body).textSelection(.enabled)
                    ForEach(observation.references, id: \.self) { number in
                        let source = preview.sources[number - 1]
                        Button("#\(number) · \(formatMomentDay(source.date, language))") { reference = RecordLink(id: source.id) }
                            .font(.footnote).frame(minHeight: 35)
                    }
                }
            }
        }
        if !report.questions.isEmpty {
            XikeCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text(language.text("反思问题", "Questions for reflection")).font(.headline)
                    ForEach(Array(report.questions.enumerated()), id: \.offset) { _, text in Text(text).textSelection(.enabled) }
                }
            }
        }
        Button(language.text("重新准备", "Prepare again")) { confirmReset = true }
    }

    private func send(_ preview: AIReviewPreview) {
        feedback = nil
        started = .now
        request = Task {
            do {
                let result = try await AIReview.run(preview, settings: settings)
                if !Task.isCancelled { report = result }
            } catch {
                if !Task.isCancelled { feedback = language.text("分析失败，可以手动重试。", "The review failed. You can try again.") }
            }
            if !Task.isCancelled { request = nil; started = nil }
        }
    }

    private func cancel() { request?.cancel(); request = nil; started = nil }

    private func reloadSettings() {
        do {
            let current = try AISettings.load()
            if let preview, preview.model != current.model || preview.endpoint != current.endpoint { self.preview = nil }
            settings = current
        } catch { feedback = model.message(for: error) }
    }
}
