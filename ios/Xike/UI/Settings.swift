import SwiftUI
import UniformTypeIdentifiers

enum SettingsRoute: String, Identifiable {
    case language, lock, reminder, prompt, export, restore, automaticBackup, privacy
    var id: Self { self }
}

struct SettingsScreen: View {
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    @Environment(\.dynamicTypeSize) private var dynamicType
    @Bindable var model: JournalAppModel
    @State private var route: SettingsRoute?

    var body: some View {
        PageScroll {
            VStack(alignment: .leading, spacing: 7) {
                Text(language.text("自己的小小空间", "A SPACE OF YOUR OWN"))
                    .font(.caption.weight(.semibold)).tracking(1.4).foregroundStyle(palette.primary)
                XikeTitle(title: language.text("设置", "Settings"))
                Text(language.text("让记录跟着你的节奏。", "Let journaling follow your rhythm."))
                    .font(.subheadline).foregroundStyle(palette.muted)
            }
            XikeCard {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "shield").foregroundStyle(palette.primary)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(language.text("日记离线保存在本机，并已加密", "Your journal is offline and encrypted"))
                            .font(.subheadline.weight(.semibold))
                        Text(language.text("天气和可选 AI 回顾只在主动操作时联网。", "Weather and optional AI review connect when requested."))
                            .font(.footnote).foregroundStyle(palette.muted)
                    }
                }
            }
            section(language.text("语言", "Language")) {
                row(.language, icon: "globe", title: language.text("应用语言", "App language"), caption: languageLabel(language))
            }
            section(language.text("应用锁", "App lock")) {
                row(.lock, icon: "lock.shield", title: language.text("应用锁", "App lock"),
                    caption: model.preferences.lock ? lockDelayLabel(model.preferences.lockDelay, language) : language.text("已关闭", "Off"))
            }
            section(language.text("轻轻陪伴", "Gentle company")) {
                row(.reminder, icon: "bell", title: language.text("记录提醒", "Journal reminders"),
                    caption: model.preferences.reminder.enabled ? clock(model.preferences.reminder.minutes) : language.text("已关闭", "Off"))
                Divider()
                row(.prompt, icon: "questionmark.circle", title: language.text("每日一问", "Daily question"),
                    caption: model.preferences.dailyQuestion ? language.text("已开启", "On") : language.text("已关闭", "Off"))
            }
            appearance
            section(language.text("备份与迁移", "Backup & migration")) {
                row(.export, icon: "square.and.arrow.up", title: language.text("导出日记备份", "Export journal backup"),
                    caption: language.text("可加密，包含离线阅读页", "Optional password encryption and an offline reading page"))
                Divider()
                row(.restore, icon: "square.and.arrow.down", title: language.text("从备份恢复", "Restore from backup"),
                    caption: language.text("支持加密和未加密备份", "Supports encrypted and unencrypted backups"))
                Divider()
                row(.automaticBackup, icon: "clock.arrow.circlepath", title: language.text("自动备份", "Automatic backup"),
                    caption: model.preferences.backup.enabled ? language.text("已开启", "On") : language.text("已关闭", "Off"))
            }
            section(language.text("高级设置", "Advanced")) {
                row(.privacy, icon: "hand.raised", title: language.text("数据与隐私", "Data and privacy"),
                    caption: language.text("记录、天气与 AI 的数据范围", "Journal, weather, and AI data"))
            }
            Text("Xike \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")")
                .font(.footnote).foregroundStyle(palette.muted).frame(maxWidth: .infinity)
        }
        .accessibilityIdentifier("settings-page")
        .sheet(item: $route, content: present)
    }

    private func section<Content: View>(_ title: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            XikeTitle(title: title, large: false)
            XikeCard { VStack(alignment: .leading, spacing: 4, content: content) }
        }
    }

    private var appearance: some View {
        VStack(alignment: .leading, spacing: 9) {
            XikeTitle(title: language.text("界面风格", "Interface style"), large: false)
            ForEach(AppStyle.allCases) { style in
                let active = model.preferences.style == style
                Button {
                    model.preferences.style = style
                } label: {
                    XikeCard {
                        HStack(spacing: 13) {
                            let preview = XikePalette(accent: palette.accent, style: style, dark: palette.dark)
                            VStack(alignment: .leading, spacing: 4) {
                                Capsule().fill(preview.primary).frame(width: 27, height: 4)
                                RoundedRectangle(cornerRadius: preview.buttonRadius)
                                    .fill(preview.accentSurface).frame(width: 57, height: 13)
                                RoundedRectangle(cornerRadius: preview.buttonRadius)
                                    .fill(preview.outline).frame(width: 43, height: 4)
                            }
                            .frame(width: 72, height: 54)
                            .background(preview.background, in: RoundedRectangle(cornerRadius: preview.buttonRadius))
                            .accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(style.label(language)).font(.headline)
                                Text(style.description(language)).font(.footnote).foregroundStyle(palette.muted)
                            }
                            Spacer(minLength: 0)
                            if active { Image(systemName: "checkmark.circle.fill").foregroundStyle(palette.primary) }
                        }
                    }
                }.buttonStyle(.plain).accessibilityAddTraits(active ? .isSelected : [])
            }
            XikeTitle(title: language.text("主色", "Accent color"), large: false).padding(.top, 7)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: dynamicType.isAccessibilitySize ? 2 : 3), spacing: 8) {
                ForEach(Accent.allCases) { accent in
                    let apply = XikePalette(accent: accent, style: palette.style, dark: palette.dark)
                    let active = model.preferences.accent == accent
                    Button {
                        model.preferences.accent = accent
                    } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Circle().fill(apply.primary).frame(width: 17, height: 17)
                                Spacer()
                                if active { Image(systemName: "checkmark.circle.fill").foregroundStyle(apply.primary) }
                            }.accessibilityHidden(true)
                            Text(accent.label(language)).font(.subheadline.weight(.medium))
                            Text(accent.description(language)).font(.caption).foregroundStyle(palette.muted)
                        }
                        .frame(maxWidth: .infinity, minHeight: 59, alignment: .leading).padding(10)
                        .background(palette.surface, in: RoundedRectangle(cornerRadius: palette.buttonRadius))
                        .overlay { RoundedRectangle(cornerRadius: palette.buttonRadius)
                            .strokeBorder(active ? apply.primary : palette.outline, lineWidth: active ? 2 : 1) }
                    }.buttonStyle(.plain).accessibilityAddTraits(active ? .isSelected : [])
                }
            }
        }
    }

    private func row(_ target: SettingsRoute, icon: String, title: String, caption: String) -> some View {
        Button {
            route = target
        } label: {
            HStack(spacing: 11) {
                Image(systemName: icon).frame(width: 38, height: 38)
                    .foregroundStyle(palette.primary).background(palette.quiet, in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.subheadline.weight(.medium)).foregroundStyle(palette.text)
                    Text(caption).font(.footnote).foregroundStyle(palette.muted)
                }
                Spacer(minLength: 3)
                Image(systemName: "chevron.right").font(.caption.weight(.medium)).foregroundStyle(palette.muted)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: 52).contentShape(Rectangle())
        }
        .buttonStyle(.plain).multilineTextAlignment(.leading)
        .accessibilityIdentifier("settings-" + target.rawValue)
    }

    @ViewBuilder
    private func present(_ route: SettingsRoute) -> some View {
        switch route {
        case .language:
            ModalPage(title: language.text("应用语言", "App language")) {
                XikeCard {
                    Picker(language.text("应用语言", "App language"), selection: $model.preferences.languageName) {
                        ForEach(XikeLanguage.allCases, id: \.rawValue) { value in
                            Text(languageLabel(value)).tag(value.rawValue)
                        }
                    }.pickerStyle(.inline)
                }
            }
            .onChange(of: model.preferences.languageName) {
                if model.preferences.reminder.enabled {
                    Task { await model.updateReminders(model.preferences.reminder) }
                }
            }
        case .lock:
            ModalPage(title: language.text("应用锁", "App lock")) {
                XikeCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Toggle(language.text("应用锁", "App lock"), isOn: Binding(get: { model.preferences.lock },
                            set: { value in Task { await model.setLock(value) } }))
                            .disabled(model.isAuthenticating)
                        Picker(language.text("离开后锁定", "Lock after leaving"), selection: $model.preferences.lockDelay) {
                            ForEach(LocalPreferences.lockDelays, id: \.self) { second in
                                Text(lockDelayLabel(second, language)).tag(second)
                            }
                        }.disabled(!model.preferences.lock)
                        if model.preferences.lock {
                            Button(language.text("立即锁定", "Lock now"), systemImage: "lock") {
                                self.route = nil
                                model.lockNow()
                                Task { await model.promptForUnlockIfNeeded() }
                            }.disabled(model.isAuthenticating)
                        }
                        Text(language.text("使用面容 ID、触控 ID 或设备密码解锁。", "Unlock with Face ID, Touch ID, or the device passcode."))
                            .font(.footnote).foregroundStyle(palette.muted)
                    }
                }
            }
        case .reminder: ReminderPanel(model: model, draft: model.preferences.reminder)
        case .prompt:
            ModalPage(title: language.text("每日一问", "Daily question")) {
                XikeCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Toggle(language.text("打开每日一问", "Show a daily question"), isOn: $model.preferences.dailyQuestion)
                        Picker(language.text("问题风格", "Question style"), selection: $model.preferences.promptStyle) {
                            Text(language.text("温柔陪伴", "Gentle company")).tag(0)
                            Text(language.text("身体觉察", "Body awareness")).tag(1)
                            Text(language.text("回顾反思", "Reflection")).tag(2)
                        }
                        Text(DailyQuestions.question(style: model.preferences.promptStyle, language: language))
                            .font(.subheadline).foregroundStyle(palette.muted)
                    }
                }
            }
        case .export: BackupPanel(model: model, startingWithExport: true)
        case .restore: BackupPanel(model: model, startingWithExport: false)
        case .automaticBackup: AutoBackupPanel(model: model, draft: model.preferences.backup)
        case .privacy:
            ModalPage(title: language.text("数据与隐私", "Data and privacy")) {
                XikeCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(language.text("记录、草稿和附件在本机加密保存。通过备份导出可以带走记录。", "Entries, drafts and attachments are encrypted on your device. Export a backup to take your entries with you."))
                        Text(language.text("主动添加天气时，Open-Meteo 处理用于查询的坐标。息刻只保存地点文字和天气快照。", "When requested, Open-Meteo receives coordinates for the weather lookup. Xike saves the place name and weather snapshot."))
                        Text(language.text("使用 AI 回顾前，你可以查看接收地址、发送内容和范围。服务方按自己的政策处理数据。", "You can preview the destination, content and scope before sending an AI review. The endpoint operator handles that data under its own policy."))
                    }.font(.callout)
                }
            }
        }
    }

    private func languageLabel(_ value: XikeLanguage) -> String {
        switch value {
        case .automatic: language.text("跟随系统", "Follow system")
        case .chinese: "简体中文"
        case .english: "English"
        }
    }
}

private struct ReminderPanel: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss
    let model: JournalAppModel
    @State var draft: ReminderOptions
    @State private var saving = false

    var body: some View {
        ModalPage(title: language.text("记录提醒", "Journal reminders")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 12) {
                    Toggle(language.text("开启提醒", "Enable reminders"), isOn: $draft.enabled)
                    DatePicker(language.text("提醒时间", "Reminder time"), selection: minuteBinding($draft.minutes),
                               displayedComponents: .hourAndMinute)
                    weekdayChoices
                    Toggle(language.text("夜间勿扰", "Quiet hours"), isOn: $draft.quiet)
                    if draft.quiet {
                        DatePicker(language.text("开始", "From"), selection: minuteBinding($draft.quietStart), displayedComponents: .hourAndMinute)
                        DatePicker(language.text("结束", "To"), selection: minuteBinding($draft.quietEnd), displayedComponents: .hourAndMinute)
                    }
                    if draft.pauseDeadline.map({ $0 > .now }) == true {
                        Button(language.text("恢复提醒", "Resume reminders")) { draft.pause(until: nil) }
                    } else {
                        Button(language.text("暂停一周", "Pause for a week")) {
                            draft.pause(until: Calendar.xike.date(byAdding: .day, value: 7, to: Calendar.xike.startOfDay(for: .now)))
                        }.disabled(!draft.enabled)
                    }
                }
            }
            XikeAction(title: language.text("保存", "Save"), enabled: !saving && (!draft.enabled || !draft.weekdays.isEmpty)) {
                saving = true
                Task {
                    if await model.updateReminders(draft, requestPermission: draft.enabled && !model.preferences.reminder.enabled) {
                        dismiss()
                    }
                    saving = false
                }
            }
        }
    }

    private var weekdayChoices: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 43))], spacing: 6) {
            ForEach([2, 3, 4, 5, 6, 7, 1], id: \.self) { weekday in
                let selected = draft.weekdays.contains(weekday)
                Button {
                    if selected { draft.weekdays.remove(weekday) } else { draft.weekdays.insert(weekday) }
                } label: {
                    Text(weekdayLabel(weekday, language)).font(.caption).frame(maxWidth: .infinity, minHeight: 39)
                        .background(selected ? palette.accentSurface : palette.quiet,
                                    in: RoundedRectangle(cornerRadius: palette.buttonRadius))
                }.buttonStyle(.plain).accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

func lockDelayLabel(_ second: Int, _ language: XikeLanguage) -> String {
    switch second {
    case 0: language.text("立即锁定", "Immediately")
    default: language.text("\(second / 60) 分钟后", "After \(second / 60) min")
    }
}

func minuteBinding(_ value: Binding<Int>) -> Binding<Date> {
    Binding(get: {
        Calendar.xike.date(bySettingHour: value.wrappedValue / 60, minute: value.wrappedValue % 60, second: 0, of: .now) ?? .now
    }, set: { date in
        let components = Calendar.xike.dateComponents([.hour, .minute], from: date)
        value.wrappedValue = (components.hour ?? 0) * 60 + (components.minute ?? 0)
    })
}

func weekdayLabel(_ weekday: Int, _ language: XikeLanguage) -> String {
    let formatter = DateFormatter()
    formatter.locale = language.locale
    return formatter.shortWeekdaySymbols[max(0, min(6, weekday - 1))]
}

func clock(_ minutes: Int) -> String { String(format: "%02d:%02d", minutes / 60, minutes % 60) }
