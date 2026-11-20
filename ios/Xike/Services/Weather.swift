import CoreLocation
import Foundation

struct WeatherCity: Decodable, Identifiable {
    let id: Int
    let name: String
    let latitude: Double
    let longitude: Double
    let admin1: String?
    let country: String?

    var title: String { [name, admin1, country].compactMap { $0 }.joined(separator: " · ") }
}

enum WeatherService {
    private struct SearchResponse: Decodable { var results: [WeatherCity]? }
    private struct ForecastResponse: Decodable {
        struct Current: Decodable {
            var temperature_2m: Double
            var weather_code: Int
        }
        let current: Current
    }

    static func cities(_ text: String, language: XikeLanguage) async throws -> [WeatherCity] {
        let response: SearchResponse = try await fetch(host: "geocoding-api.open-meteo.com", path: "/v1/search",
            query: ["name": text, "count": "10", "language": language.isEnglish ? "en" : "zh", "format": "json"])
        return response.results ?? []
    }

    static func weather(latitude: Double, longitude: Double, location: String,
                        fullLocation: String?, source: String) async throws -> OutdoorSnapshot {
        let response: ForecastResponse = try await fetch(host: "api.open-meteo.com", path: "/v1/forecast", query: [
            "latitude": String((latitude * 1_000).rounded() / 1_000),
            "longitude": String((longitude * 1_000).rounded() / 1_000),
            "current": "temperature_2m,weather_code",
        ])
        let snapshot = OutdoorSnapshot(placeName: location, temperatureCelsius: response.current.temperature_2m,
                                       weatherCode: response.current.weather_code, capturedAt: Date.now.epochMillis,
                                       fullPlaceName: fullLocation, locationSource: source)
        guard let normalized = snapshot.normalized else { throw JournalError.unavailable }
        return normalized
    }

    static func place(for location: CLLocation, precise: Bool, language: XikeLanguage) async throws -> (name: String, full: String?) {
        let geocoder = CLGeocoder()
        let places = try await withTaskCancellationHandler {
            try await geocoder.reverseGeocodeLocation(location, preferredLocale: language.locale)
        } onCancel: { geocoder.cancelGeocode() }
        guard let place = places.first else { throw JournalError.unavailable }
        let region = place.subLocality ?? place.locality ?? place.administrativeArea ?? place.country
        guard let name = precise ? (place.thoroughfare ?? region) : region else { throw JournalError.unavailable }
        let parts = precise
            ? [place.administrativeArea, place.locality, place.subLocality, place.thoroughfare, place.subThoroughfare]
            : [place.administrativeArea, place.locality, place.subLocality]
        var seen = Set<String>()
        let full = parts.compactMap { $0 }.filter { seen.insert($0).inserted }.joined(separator: language.isEnglish ? ", " : "")
        return (name, full.isEmpty ? nil : full)
    }

    private static func fetch<T: Decodable>(host: String, path: String, query: [String: String]) async throws -> T {
        var components = URLComponents()
        components.scheme = "https"
        components.host = host
        components.path = path
        components.queryItems = query.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        guard let url = components.url else { throw JournalError.unavailable }
        let config = URLSessionConfiguration.ephemeral
        config.urlCache = nil
        config.timeoutIntervalForRequest = 15
        let session = URLSession(configuration: config)
        defer { session.invalidateAndCancel() }
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { throw JournalError.unavailable }
        return try JSONDecoder().decode(T.self, from: data)
    }
}

@MainActor
final class OneTimeLocation: NSObject, @preconcurrency CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var requested = false
    private var continuation: CheckedContinuation<CLLocation, Error>?
    private var timeout: Task<Void, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    func get() async throws -> CLLocation {
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                self.continuation = continuation
                if Task.isCancelled { complete(.failure(CancellationError())); return }
                timeout = Task { [weak self] in
                    try? await Task.sleep(for: .seconds(20))
                    if !Task.isCancelled { self?.complete(.failure(JournalError.unavailable)) }
                }
                resolvePermission()
            }
        } onCancel: {
            Task { @MainActor in self.complete(.failure(CancellationError())) }
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { resolvePermission() }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last,
              CLLocationCoordinate2DIsValid(location.coordinate), location.horizontalAccuracy >= 0
        else { complete(.failure(JournalError.unavailable)); return }
        complete(.success(location))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        complete(.failure(JournalError.unavailable))
    }

    func weather(_ language: XikeLanguage) async throws -> OutdoorSnapshot {
        let location = try await get()
        let precise = manager.accuracyAuthorization == .fullAccuracy && location.horizontalAccuracy <= 100
        let place = try await WeatherService.place(for: location, precise: precise, language: language)
        return try await WeatherService.weather(latitude: location.coordinate.latitude,
                                                longitude: location.coordinate.longitude, location: place.name,
                                                fullLocation: place.full, source: "system")
    }

    private func resolvePermission() {
        guard continuation != nil else { return }
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse:
            if !requested { requested = true; manager.requestLocation() }
        default: complete(.failure(JournalError.unavailable))
        }
    }

    private func complete(_ result: Result<CLLocation, Error>) {
        timeout?.cancel()
        timeout = nil
        manager.stopUpdatingLocation()
        continuation?.resume(with: result)
        continuation = nil
        requested = false
    }
}

extension OutdoorSnapshot {
    func condition(_ language: XikeLanguage) -> String {
        switch weatherCode {
        case 0: language.text("晴", "Clear")
        case 1, 2, 3: language.text("多云", "Cloudy")
        case 45, 48: language.text("雾", "Fog")
        case 51...57: language.text("毛毛雨", "Drizzle")
        case 61...67, 80...82: language.text("雨", "Rain")
        case 71...77, 85, 86: language.text("雪", "Snow")
        case 95...99: language.text("雷雨", "Thunderstorm")
        default: language.text("天气", "Weather")
        }
    }

    var weatherIcon: String {
        switch weatherCode {
        case 0: "sun.max"
        case 45, 48: "cloud.fog"
        case 51...67, 80...82: "cloud.rain"
        case 71...77, 85, 86: "cloud.snow"
        case 95...99: "cloud.bolt.rain"
        default: "cloud.sun"
        }
    }
}
