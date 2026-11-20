import SwiftUI

struct WeatherPanel: View {
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    var onApply: (OutdoorSnapshot) -> Void
    @State private var city = ""
    @State private var cities: [WeatherCity] = []
    @State private var operation: Task<Void, Never>?
    @State private var feedback: String?

    var body: some View {
        ModalPage(title: language.text("此刻窗外", "Outside now")) {
            XikeCard {
                VStack(alignment: .leading, spacing: 14) {
                    Text(language.text("获取一次位置，查询此刻的天气。息刻保存地点名称和天气快照。", "Look up the weather using a one-time location. Xike saves the place name and weather snapshot."))
                        .font(.subheadline).foregroundStyle(palette.muted)
                    XikeAction(title: language.text("使用当前位置", "Use current location"), enabled: operation == nil) {
                        request {
                            let amap = try AMapPreferences.read()
                            if amap.usable { return try await OneTimeAMap().weather(config: amap, language: language) }
                            return try await OneTimeLocation().weather(language)
                        }
                    }
                }
            }
            XikeCard {
                VStack(alignment: .leading, spacing: 10) {
                    XikeTitle(title: language.text("手动选择城市", "Choose a city"), large: false)
                    HStack {
                        TextField(language.text("城市名称", "City name"), text: $city)
                            .submitLabel(.search).onSubmit(search)
                        Button(language.text("搜索", "Search"), action: search).disabled(operation != nil || query.isEmpty)
                    }
                    ForEach(cities) { candidate in
                        Button(candidate.title) {
                            request {
                                try await WeatherService.weather(latitude: candidate.latitude, longitude: candidate.longitude,
                                    location: candidate.name, fullLocation: candidate.title, source: "manual")
                            }
                        }.frame(minHeight: 36).disabled(operation != nil)
                    }
                }
            }
            if operation != nil {
                HStack {
                    ProgressView(language.text("正在查询…", "Looking up…"))
                    Spacer()
                    Button(language.text("取消", "Cancel")) { cancel() }
                }
            }
            if let feedback { Text(feedback).foregroundStyle(palette.muted).font(.footnote) }
        }
        .onDisappear { cancel() }
        .onChange(of: scenePhase) { _, phase in if phase == .background { cancel() } }
    }

    private var query: String { city.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func search() {
        guard operation == nil, !query.isEmpty else { return }
        let searchTerm = query
        feedback = nil
        operation = Task {
            do {
                let choices = try await WeatherService.cities(searchTerm, language: language)
                try Task.checkCancellation()
                cities = choices
                if choices.isEmpty { feedback = language.text("没有找到城市", "No cities found") }
            } catch { report(error) }
            if !Task.isCancelled { operation = nil }
        }
    }

    private func request(_ fetch: @escaping () async throws -> OutdoorSnapshot) {
        feedback = nil
        operation = Task {
            do {
                let weather = try await fetch()
                try Task.checkCancellation()
                onApply(weather)
                dismiss()
            } catch { report(error) }
            if !Task.isCancelled { operation = nil }
        }
    }

    private func cancel() { operation?.cancel(); operation = nil }

    private func report(_ error: Error) {
        if !Task.isCancelled && !(error is CancellationError) {
            feedback = language.text("查询失败。检查网络和定位权限，或手动选择城市。", "Check your connection and location permission, or select a city.")
        }
    }
}
