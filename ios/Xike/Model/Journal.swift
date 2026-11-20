import CryptoKit
import Foundation

enum XikeLanguage: String, CaseIterable {
    case automatic, chinese, english

    var isEnglish: Bool {
        self == .english || (self == .automatic && !(Locale.preferredLanguages.first ?? "en").hasPrefix("zh"))
    }

    var locale: Locale { Locale(identifier: isEnglish ? "en_US" : "zh_Hans") }

    func text(_ chinese: String, _ english: String) -> String { isEnglish ? english : chinese }
}

enum Mood: String, CaseIterable, Codable, Identifiable {
    case low = "LOW", tired = "TIRED", calm = "CALM", good = "GOOD", joyful = "JOYFUL"
    case veryLow = "VERY_LOW", belowAverage = "BELOW_AVERAGE", neutral = "NEUTRAL"
    case aboveAverage = "ABOVE_AVERAGE", veryGood = "VERY_GOOD"

    static let choices: [Mood] = [.veryLow, .belowAverage, .neutral, .aboveAverage, .veryGood]
    private static let legacy: [Mood] = [.low, .tired, .calm, .good, .joyful]

    var id: String { rawValue }
    var score: Int { (Self.choices.firstIndex(of: self) ?? Self.legacy.firstIndex(of: self) ?? 2) + 1 }
    var overall: Mood { Self.choices[score - 1] }
    var legacyValue: String { Self.legacy[score - 1].rawValue }
    var emojiFile: String {
        [
            "mood_pensive_face", "mood_slightly_frowning_face", "mood_neutral_face",
            "mood_slightly_smiling_face", "mood_smiling_face_with_smiling_eyes",
        ][score - 1]
    }

    func label(_ language: XikeLanguage) -> String {
        switch self {
        case .veryLow: language.text("很不好", "Very low")
        case .belowAverage: language.text("不太好", "Not great")
        case .neutral: language.text("一般", "Okay")
        case .aboveAverage: language.text("还不错", "Positive")
        case .veryGood: language.text("很好", "Very good")
        case .low: language.text("低落", "Low")
        case .tired: language.text("疲惫", "Tired")
        case .calm: language.text("平静", "Calm")
        case .good: language.text("轻松", "Good")
        case .joyful: language.text("愉悦", "Joyful")
        }
    }
}

enum Feeling: String, CaseIterable, Identifiable {
    case happy = "HAPPY", peaceful = "PEACEFUL", relaxed = "RELAXED", hopeful = "HOPEFUL"
    case sad = "SAD", anxious = "ANXIOUS", irritated = "IRRITATED", tired = "TIRED"
    case content = "CONTENT", grateful = "GRATEFUL", safe = "SAFE", excited = "EXCITED"
    case angry = "ANGRY", hurt = "HURT", lonely = "LONELY", disappointed = "DISAPPOINTED"
    case bored = "BORED", energetic = "ENERGETIC"

    var id: String { rawValue }
    func label(_ language: XikeLanguage) -> String {
        switch self {
        case .happy: language.text("开心", "Happy")
        case .peaceful: language.text("平静", "Peaceful")
        case .relaxed: language.text("放松", "Relaxed")
        case .hopeful: language.text("期待", "Hopeful")
        case .sad: language.text("难过", "Sad")
        case .anxious: language.text("焦虑", "Anxious")
        case .irritated: language.text("烦躁", "Irritated")
        case .tired: language.text("疲惫", "Tired")
        case .content: language.text("满足", "Content")
        case .grateful: language.text("感激", "Grateful")
        case .safe: language.text("安心", "At ease")
        case .excited: language.text("兴奋", "Excited")
        case .angry: language.text("生气", "Angry")
        case .hurt: language.text("委屈", "Hurt")
        case .lonely: language.text("孤独", "Lonely")
        case .disappointed: language.text("失落", "Disappointed")
        case .bored: language.text("无聊", "Bored")
        case .energetic: language.text("充沛", "Energetic")
        }
    }
}

enum LabelKind: String, Codable {
    case topic = "TOPIC", activity = "ACTIVITY"
}

struct JournalLabel: Codable, Equatable, Identifiable {
    var id: String
    var kind: LabelKind
    var name: String
    var iconKey: String
    var sortOrder: Int
    var isArchived: Bool
    var isBuiltin: Bool

    static let icons = ["工作", "学习", "关系", "家庭", "身体", "睡眠", "饮食", "金钱", "自我", "兴趣", "出行", "居住", "其他"]
    static let topicsInEnglish = ["Work", "Learning", "Relationships", "Family", "Body", "Sleep", "Food", "Money",
                                  "Self", "Interests", "Travel", "Home", "Other"]
    static let topicAliases = ["社交": "关系", "运动": "身体", "创作": "兴趣"]
    static let translatedAliases = ["social": "关系", "exercise": "身体", "creating": "兴趣"]
    static let activities = [("散步", "身体", "Walking"), ("运动", "身体", "Exercise"), ("阅读", "学习", "Reading"),
                             ("休息", "睡眠", "Rest"), ("独处", "自我", "Alone time"), ("见朋友", "关系", "Seeing friends"),
                             ("加班", "工作", "Overtime"), ("创作", "兴趣", "Creating")]
    static let builtin: [JournalLabel] = icons.enumerated().map { index, name in
        JournalLabel(id: "builtin:topic:\(index)", kind: .topic, name: name, iconKey: name, sortOrder: index,
                     isArchived: !["工作", "学习", "关系", "身体", "兴趣", "自我"].contains(name), isBuiltin: true)
    } + activities.enumerated().map { index, item in
        JournalLabel(id: "builtin:activity:\(index)", kind: .activity, name: item.0, iconKey: item.1, sortOrder: index,
                     isArchived: false, isBuiltin: true)
    }

    static let builtinByID = Dictionary(uniqueKeysWithValues: builtin.map { ($0.id, $0) })

    static func ordered(_ first: Self, _ second: Self) -> Bool {
        (first.kind == .topic ? 0 : 1, first.sortOrder, first.id)
            < (second.kind == .topic ? 0 : 1, second.sortOrder, second.id)
    }

    var isValid: Bool {
        guard !id.isEmpty, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              Self.icons.contains(iconKey) else { return false }
        if let known = Self.builtinByID[id] {
            return isBuiltin && known.kind == kind && known.name == name
        }
        return !isBuiltin && !id.hasPrefix("builtin:")
    }
}

struct SavedLabel: Codable, Equatable, Identifiable {
    var id: String
    var kind: LabelKind
    var savedName: String
    var isBuiltin: Bool

    static func legacyTopic(_ name: String) -> Self {
        let topic = JournalLabel.topicAliases[name] ?? name
        if let index = JournalLabel.icons.firstIndex(of: topic) {
            return Self(id: "builtin:topic:\(index)", kind: .topic, savedName: name, isBuiltin: true)
        }
        return Self(id: "legacy:" + nameUUID(name), kind: .topic, savedName: name, isBuiltin: false)
    }

    var searchNames: [String] {
        guard let label = JournalLabel.builtinByID[id] else { return [savedName] }
        let english = label.kind == .topic
            ? JournalLabel.topicsInEnglish[label.sortOrder]
            : JournalLabel.activities[label.sortOrder].2
        return [savedName, label.name, english] + (label.kind == .topic
            ? JournalLabel.topicAliases.filter { $0.value == label.name }.keys.sorted()
                + JournalLabel.translatedAliases.filter { $0.value == label.name }.keys.sorted()
            : [])
    }

    func definition(sortOrder: Int) -> JournalLabel {
        JournalLabel.builtinByID[id] ?? JournalLabel(
            id: id, kind: kind, name: savedName, iconKey: "其他", sortOrder: sortOrder,
            isArchived: false, isBuiltin: false)
    }
}

func nameUUID(_ value: String) -> String {
    var bytes = Array(Insecure.MD5.hash(data: Data(value.utf8)))
    bytes[6] = (bytes[6] & 0x0f) | 0x30
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    return UUID(uuid: (bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
                       bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]))
        .uuidString.lowercased()
}

enum AttachmentKind: String {
    case photo = "images", audio = "audios", video = "videos"

    static let videoTypes = [
        "mp4": "video/mp4", "mov": "video/quicktime", "3gp": "video/3gpp", "webm": "video/webm",
        "mkv": "video/x-matroska",
    ]

    var limit: Int64 {
        switch self {
        case .photo: 20 * 1_048_576
        case .audio: 16 * 1_048_576
        case .video: 500 * 1_048_576
        }
    }
}

struct JournalAudio: Codable, Equatable {
    var fileName: String
    var durationMillis: Int64
    var mimeType = "audio/mp4"
}

struct JournalVideo: Codable, Equatable {
    var fileName: String
    var coverFileName: String
    var durationMillis: Int64
    var sizeBytes: Int64
    var mimeType: String
}

struct OutdoorSnapshot: Codable, Equatable {
    var placeName: String
    var temperatureCelsius: Double
    var weatherCode: Int
    var capturedAt: Int64
    var source = "Open-Meteo"
    var fullPlaceName: String?
    var locationSource: String?

    var normalized: Self? {
        var item = self
        item.placeName = String(placeName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(512))
        item.source = String(source.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40))
        item.fullPlaceName = fullPlaceName
            .map { String($0.trimmingCharacters(in: .whitespacesAndNewlines).prefix(512)) }
            .flatMap { $0.isEmpty ? nil : $0 }
        if let source = locationSource, !["amap", "system", "manual"].contains(source) { item.locationSource = nil }
        return !item.placeName.isEmpty && !item.source.isEmpty && (-100...70).contains(temperatureCelsius)
            && (0...99).contains(weatherCode) && capturedAt > 0 ? item : nil
    }
}

struct JournalRecord: Codable, Equatable, Identifiable {
    var id: String
    var createdAt: Int64
    var mood: Mood
    var note: String
    var imageFileNames: [String]
    var audio: JournalAudio?
    var video: JournalVideo?
    var outdoor: OutdoorSnapshot?
    var feelings: [String]
    var moodScaleVersion: Int
    var labels: [SavedLabel]
    var isFavorite: Bool

    var date: Date { Date(timeIntervalSince1970: Double(createdAt) / 1_000) }
    var tags: [String] { labels.filter { $0.kind == .topic }.map(\.savedName) }
    var displayMood: Mood { moodScaleVersion > 0 ? mood.overall : mood }
    var attachments: [String: AttachmentKind] {
        var files: [String: AttachmentKind] = [:]
        for photo in imageFileNames { files[photo] = .photo }
        if let audio { files[audio.fileName] = .audio }
        if let video {
            files[video.fileName] = .video
            files[video.coverFileName] = .video
        }
        return files
    }

    init(id: String, date: Date, mood: Mood, note: String, imageFileNames: [String],
         audio: JournalAudio?, video: JournalVideo?, outdoor: OutdoorSnapshot?, feelings: [String],
         moodScaleVersion: Int, labels: [SavedLabel], isFavorite: Bool) {
        self.id = id
        createdAt = date.epochMillis
        self.mood = mood
        self.note = note
        self.imageFileNames = imageFileNames
        self.audio = audio
        self.video = video
        self.outdoor = outdoor
        self.feelings = feelings
        self.moodScaleVersion = moodScaleVersion
        self.labels = labels
        self.isFavorite = isFavorite
    }

    enum CodingKeys: String, CodingKey {
        case id, createdAt, mood, note, imageFileNames, imageFileName, audio, video, outdoor, feelings
        case moodScaleVersion, labels, tags, isFavorite
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        createdAt = try values.decode(Int64.self, forKey: .createdAt)
        moodScaleVersion = try values.decodeIfPresent(Int.self, forKey: .moodScaleVersion) ?? 0
        let originalMood = Mood(rawValue: try values.decode(String.self, forKey: .mood)) ?? .calm
        mood = moodScaleVersion > 0 ? originalMood.overall : originalMood
        note = try values.decodeIfPresent(String.self, forKey: .note) ?? ""
        imageFileNames = try values.decodeIfPresent([String].self, forKey: .imageFileNames)
            ?? values.decodeIfPresent(String.self, forKey: .imageFileName).map { [$0] } ?? []
        audio = try values.decodeIfPresent(JournalAudio.self, forKey: .audio)
        video = try values.decodeIfPresent(JournalVideo.self, forKey: .video)
        outdoor = try values.decodeIfPresent(OutdoorSnapshot.self, forKey: .outdoor)?.normalized
        feelings = try values.decodeIfPresent([String].self, forKey: .feelings) ?? []
        labels = try values.decodeIfPresent([SavedLabel].self, forKey: .labels)
            ?? (values.decodeIfPresent([String].self, forKey: .tags) ?? []).map(SavedLabel.legacyTopic)
        isFavorite = values.contains(.isFavorite) ? try values.decode(Bool.self, forKey: .isFavorite) : false
    }

    func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encode(id, forKey: .id)
        try values.encode(createdAt, forKey: .createdAt)
        try values.encode(mood.rawValue, forKey: .mood)
        try values.encode(note, forKey: .note)
        try values.encode(imageFileNames, forKey: .imageFileNames)
        try values.encode(audio, forKey: .audio)
        try values.encode(video, forKey: .video)
        try values.encode(outdoor, forKey: .outdoor)
        try values.encode(feelings, forKey: .feelings)
        try values.encode(moodScaleVersion, forKey: .moodScaleVersion)
        try values.encode(labels, forKey: .labels)
        try values.encode(tags, forKey: .tags)
        try values.encode(isFavorite, forKey: .isFavorite)
    }
}

struct JournalDraft: Codable, Equatable, Identifiable {
    var id = UUID().uuidString.lowercased()
    var date: Date?
    var mood: Mood?
    var note = ""
    var photos: [String] = []
    var audio: JournalAudio?
    var video: JournalVideo?
    var outdoor: OutdoorSnapshot?
    var feelings: [String] = []

    var isEmpty: Bool {
        date == nil && mood == nil && note.isEmpty && photos.isEmpty && audio == nil && video == nil
            && outdoor == nil && feelings.isEmpty
    }

    init() {}

    init(_ record: JournalRecord) {
        id = record.id
        date = record.date
        mood = record.mood
        note = record.note
        photos = record.imageFileNames
        audio = record.audio
        video = record.video
        outdoor = record.outdoor
        feelings = record.feelings
    }

    func record(replacing original: JournalRecord? = nil) -> JournalRecord? {
        guard let mood else { return nil }
        let recorded = date ?? .now
        let retainedWeather = outdoor.flatMap { snapshot in
            let sourceDate = original?.date ?? Date(timeIntervalSince1970: Double(snapshot.capturedAt) / 1_000)
            return Calendar.xike.isDate(sourceDate, inSameDayAs: recorded) ? snapshot : nil
        }
        return JournalRecord(
            id: id, date: recorded, mood: mood, note: note.trimmingCharacters(in: .whitespacesAndNewlines),
            imageFileNames: photos, audio: audio, video: video, outdoor: retainedWeather,
            feelings: feelings, moodScaleVersion: Mood.choices.contains(mood) ? 1 : 0,
            labels: original?.labels ?? [], isFavorite: original?.isFavorite ?? false)
    }

    var files: Set<String> {
        Set(photos + [audio?.fileName, video?.fileName, video?.coverFileName].compactMap { $0 })
    }
}

extension Date {
    var epochMillis: Int64 { Int64((timeIntervalSince1970 * 1_000).rounded()) }
}

extension Calendar {
    static var xike: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .autoupdatingCurrent
        calendar.firstWeekday = 2
        return calendar
    }
}

enum JournalError: Error, Equatable {
    case storage, key, io, invalidBackup, unsupportedBackup, password, limit, attachment, unavailable, changed

    func message(_ language: XikeLanguage) -> String {
        switch self {
        case .storage: language.text("记录暂时无法读取或保存。请重试。", "Journal data couldn’t be read or saved. Try again.")
        case .key: language.text("暂时无法解锁本机数据。请解锁设备后重试。", "Unlock this device and try again.")
        case .io: language.text("无法读取或写入文件。请检查可用空间和文件访问权限。", "Check available space and file access.")
        case .invalidBackup: language.text("息刻备份内容不完整或格式有误。", "The Xike backup is incomplete or invalid.")
        case .unsupportedBackup: language.text("暂不支持这个版本的息刻备份。", "This Xike backup version isn’t supported.")
        case .password: language.text("密码错误或加密文件已损坏。", "The password is incorrect or the encrypted file is damaged.")
        case .limit: language.text("文件大小、时长或数量超出限制。", "The file size, duration, or count exceeds the limit.")
        case .attachment: language.text("附件无法读取或格式不受支持。", "The attachment couldn’t be read or its format isn’t supported.")
        case .unavailable: language.text("这个操作暂时不可用。", "This operation isn’t available right now.")
        case .changed: language.text("记录已变化，请重新确认。", "The journal changed. Review your selection again.")
        }
    }
}
