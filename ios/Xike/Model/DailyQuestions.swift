import Foundation

enum DailyQuestions {
    private struct Question: Decodable { var zh: String; var en: String }
    private static let questions: [[Question]] = {
        guard let url = Bundle.main.url(forResource: "DailyQuestions", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let collection = try? JSONDecoder().decode([[Question]].self, from: data) else { return [] }
        return collection
    }()

    static func question(style: Int, language: XikeLanguage, date: Date = .now) -> String {
        guard questions.indices.contains(style), !questions[style].isEmpty else { return "" }
        let options = questions[style]
        let civil = Calendar.xike.dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        let epoch = Int((utc.date(from: civil)?.timeIntervalSince1970 ?? 0) / 86_400)
        let index = (epoch % options.count + options.count) % options.count
        return language.text(options[index].zh, options[index].en)
    }
}
