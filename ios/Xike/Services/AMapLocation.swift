import CoreLocation
import Foundation

struct AMapPreferences: Codable {
    var key = ""
    var enabled = false
    var consent = false

    var usable: Bool { XikeAMapClient.available && enabled && consent && !key.isEmpty }

    static func read() throws -> Self {
        guard let value = try DeviceSecrets.read("amap-location") else { return Self() }
        return try JSONDecoder().decode(Self.self, from: value)
    }

    func save() throws {
        guard key.utf8.count <= 256 && key.utf8.allSatisfy({ (33...126).contains($0) }),
              !enabled || (consent && !key.isEmpty) else { throw JournalError.unavailable }
        try DeviceSecrets.write("amap-location", JSONEncoder().encode(self))
    }
}

@MainActor
final class OneTimeAMap {
    struct Result {
        let latitude: Double
        let longitude: Double
        let place: String
        let address: String?
    }

    private struct Position {
        let coordinate: CLLocationCoordinate2D
        let place: String?
        let address: String?
        let precise: Bool
    }

    private let client = XikeAMapClient()
    private var continuation: CheckedContinuation<Position, Error>?
    private var timeout: Task<Void, Never>?

    func locate(key: String, language: XikeLanguage) async throws -> Result {
        guard XikeAMapClient.available, !key.isEmpty else { throw JournalError.unavailable }
        let result: Position = try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                self.continuation = continuation
                if Task.isCancelled { cancel(); return }
                let started = client.request(key: key, english: language.isEnglish) { [weak self] location, parts, precise in
                    guard let self else { return }
                    self.complete(location: location, parts: parts, precise: precise, language: language)
                }
                if started {
                    timeout = Task { [weak self] in
                        try? await Task.sleep(for: .seconds(24))
                        if !Task.isCancelled { self?.cancel(error: JournalError.unavailable) }
                    }
                } else {
                    cancel(error: JournalError.unavailable)
                }
            }
        } onCancel: {
            Task { @MainActor in self.cancel() }
        }
        let coordinate = result.coordinate
        if let name = result.place {
            return Result(latitude: coordinate.latitude, longitude: coordinate.longitude, place: name, address: result.address)
        }
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        let fallback = try await WeatherService.place(for: location, precise: result.precise, language: language)
        return Result(latitude: coordinate.latitude, longitude: coordinate.longitude, place: fallback.name, address: fallback.full)
    }

    func weather(config: AMapPreferences, language: XikeLanguage) async throws -> OutdoorSnapshot {
        let result = try await locate(key: config.key, language: language)
        return try await WeatherService.weather(latitude: result.latitude, longitude: result.longitude, location: result.place,
                                                fullLocation: result.address, source: "amap")
    }

    private func complete(location: CLLocation?, parts: [String: String], precise: Bool, language: XikeLanguage) {
        guard let location, CLLocationCoordinate2DIsValid(location.coordinate), location.horizontalAccuracy >= 0 else {
            cancel(error: JournalError.unavailable)
            return
        }
        let region = ["district", "city", "province", "country"]
        let available = precise ? ["poi", "aoi", "street"] + region : region
        let name = available.compactMap { parts[$0] }.first { !$0.isEmpty }
        var seen = Set<String>()
        let coarseAddress = ["province", "city", "district"].compactMap { parts[$0] }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
            .joined(separator: language.isEnglish ? ", " : "")
        let full = precise ? (parts["address"] ?? "") : coarseAddress
        let address = name.flatMap { name in full.isEmpty ? nil : full.contains(name) ? full : "\(full) · \(name)" }
        let country = parts["country", default: ""].lowercased()
        let gcj = country.isEmpty || ["中国", "china", "香港", "hong kong", "澳门", "macao", "macau", "台灣", "台湾", "taiwan"]
            .contains { country.contains($0) }
        let coordinate = gcj ? Self.weatherCoordinate(location.coordinate) : location.coordinate
        timeout?.cancel()
        timeout = nil
        continuation?.resume(returning: Position(coordinate: coordinate, place: name, address: address, precise: precise))
        continuation = nil
    }

    private func cancel(error: Error = CancellationError()) {
        client.stop()
        timeout?.cancel()
        timeout = nil
        continuation?.resume(throwing: error)
        continuation = nil
    }

    // AMap uses GCJ-02 within mainland coverage. The approximate inverse matches Android's
    // weather-grid conversion. The caller rounds and keeps this value only for the request.
    private static func weatherCoordinate(_ value: CLLocationCoordinate2D) -> CLLocationCoordinate2D {
        let lat = value.latitude
        let lon = value.longitude
        guard (0.8293...55.8271).contains(lat), (72.004...137.8347).contains(lon) else { return value }
        let x = lon - 105
        let y = lat - 35
        let p = Double.pi
        let common = (20 * sin(6 * x * p) + 20 * sin(2 * x * p)) * 2 / 3
        let baseLat = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        let baseLon = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        let latWaves = (20 * sin(y * p) + 40 * sin(y * p / 3) + 160 * sin(y * p / 12) + 320 * sin(y * p / 30)) * 2 / 3
        let lonWaves = (20 * sin(x * p) + 40 * sin(x * p / 3) + 150 * sin(x * p / 12) + 300 * sin(x * p / 30)) * 2 / 3
        let radians = lat / 180 * p
        let eccentricity = 0.00669342162296594323
        let magic = 1 - eccentricity * sin(radians) * sin(radians)
        let root = sqrt(magic)
        let latDelta = (baseLat + common + latWaves) * 180
            / (6_378_245 * (1 - eccentricity) / (magic * root) * p)
        let lonDelta = (baseLon + common + lonWaves) * 180 / (6_378_245 / root * cos(radians) * p)
        return CLLocationCoordinate2D(latitude: lat - latDelta, longitude: lon - lonDelta)
    }
}
