import CryptoKit
import Foundation
import ImageIO

struct JournalContents: Codable, Equatable {
    var entries: [JournalRecord]
    var labelCatalog: [JournalLabel]

    var files: Set<String> { Set(entries.flatMap { $0.attachments.keys }) }
}

private struct ArchiveManifest: Codable {
    var format = "xike"
    var version = 11
    var entries: [JournalRecord]
    var labelCatalog: [JournalLabel]?
}

final class PreparedBackup: Identifiable, @unchecked Sendable {
    let id = UUID()
    let contents: JournalContents
    let media: MediaDirectory
    let photoCount: Int
    let audioCount: Int
    let videoCount: Int

    init(contents: JournalContents, media: MediaDirectory,
         photoCount: Int, audioCount: Int, videoCount: Int) {
        self.contents = contents
        self.media = media
        self.photoCount = photoCount
        self.audioCount = audioCount
        self.videoCount = videoCount
    }

    deinit { try? FileManager.default.removeItem(at: media.root) }
}

enum RestoreMode { case merge, replace }

enum JournalBackup {
    static let manifestLimit: Int64 = 16 * 1_048_576
    private static let htmlLimit: Int64 = 64 * 1_048_576
    private static let legacyLimit: Int64 = 320 * 1_048_576
    private static let marker = Data("XIKE_BACKUP_COMPLETE".utf8)

    static func inspect(_ source: URL, password: String, staging: URL, mediaKey: Data) async throws -> PreparedBackup {
        let kind = try BackupFileKind(url: source)
        if kind == .legacy { return try readLegacy(source, password: password, staging: staging, mediaKey: mediaKey) }
        let archive = try ArchiveReader(source, password: password)
        guard try archive.next() == "manifest.json" else { throw JournalError.invalidBackup }
        let bytes = try archive.data(limit: manifestLimit)
        let manifest: ArchiveManifest
        do { manifest = try JSONDecoder().decode(ArchiveManifest.self, from: bytes) }
        catch { throw JournalError.invalidBackup }
        guard manifest.format == "xike", (4...11).contains(manifest.version),
              kind != .zip || manifest.version >= 8 else { throw JournalError.unsupportedBackup }
        if manifest.version >= 10 {
            guard let object = try JSONSerialization.jsonObject(with: bytes) as? [String: Any],
                  let entries = object["entries"] as? [[String: Any]],
                  entries.allSatisfy({ $0["labels"] is [Any] && (manifest.version < 11 || $0["isFavorite"] != nil) })
            else { throw JournalError.invalidBackup }
        }
        let refs = try references(manifest.entries, allowVideo: manifest.version >= 8)
        let media = MediaDirectory(root: staging, key: mediaKey)
        let expected = Dictionary(uniqueKeysWithValues: refs.map { ("\($0.value.kind.rawValue)/\($0.key)", $0.key) })
        var installed: [String: String] = [:]
        var counts: [AttachmentKind: Int64] = [:]
        var htmlSeen = false
        var finished = false

        while let name = try archive.next() {
            guard !finished else { throw JournalError.invalidBackup }
            if manifest.version >= 7 && name == "index.html" && !htmlSeen {
                try archive.copy(limit: htmlLimit) { _ in }
                htmlSeen = true
            } else if manifest.version >= 7 && name == "complete.txt" {
                guard try archive.data(limit: 64) == marker else { throw JournalError.invalidBackup }
                finished = true
            } else {
                guard let oldName = expected[name], let reference = refs[oldName], installed[oldName] == nil
                else { throw JournalError.invalidBackup }
                let target = newName(for: oldName, kind: reference.kind)
                let writer = try media.writer(target)
                let groupLimit: Int64 = reference.kind == .video ? 10 * 1_073_741_824 : 1_073_741_824
                let remaining = groupLimit - (counts[reference.kind] ?? 0)
                let length = try archive.copy(limit: min(reference.limit, remaining), to: writer.write)
                guard length > 0, reference.expectedSize == nil || length == reference.expectedSize
                else { throw JournalError.invalidBackup }
                try writer.finish()
                counts[reference.kind, default: 0] += length
                installed[oldName] = target
            }
        }
        try archive.verifyEnd()
        guard manifest.version < 7 || (finished && htmlSeen && installed.count == refs.count)
        else { throw JournalError.invalidBackup }
        let backup = try normalized(manifest: manifest, installed: installed, media: media, refs: refs)
        var verifiedVideos = Set<String>()
        for video in backup.contents.entries.compactMap(\.video) where verifiedVideos.insert(video.fileName).inserted {
            try Task.checkCancellation()
            try await JournalMedia.validate(video, in: media)
        }
        return backup
    }

    static func write(_ contents: JournalContents, media: MediaDirectory, destination: URL,
                      password: String?, language: XikeLanguage, verify: Bool) throws {
        if let password, password.utf16.count < 8 { throw JournalError.password }
        let manifest = ArchiveManifest(entries: contents.entries, labelCatalog: contents.labelCatalog)
        let manifestData = try JSONEncoder().encode(manifest)
        guard manifestData.count <= manifestLimit else { throw JournalError.limit }
        let refs = try references(contents.entries, allowVideo: true)
        let html = Data(html(contents.entries, language: language).utf8)
        guard html.count <= htmlLimit else { throw JournalError.limit }
        let archive = try ArchiveWriter(destination, password: password)
        try archive.add("manifest.json", data: manifestData)
        try archive.add("index.html", data: html)
        var total: [AttachmentKind: Int64] = [:]
        var written: [(name: String, size: Int64)] = []
        for (name, reference) in refs.sorted(by: { ($0.value.kind.rawValue, $0.key) < ($1.value.kind.rawValue, $1.key) }) {
            let source = try media.reader(name)
            total[reference.kind, default: 0] += source.size
            let groupLimit: Int64 = reference.kind == .video ? 10 * 1_073_741_824 : 1_073_741_824
            guard source.size <= reference.limit, total[reference.kind, default: 0] <= groupLimit,
                  reference.expectedSize == nil || source.size == reference.expectedSize
            else { throw JournalError.limit }
            let path = "\(reference.kind.rawValue)/\(name)"
            try archive.add(path, size: source.size, writeContents: source.copy)
            if verify { written.append((path, source.size)) }
        }
        try archive.add("complete.txt", data: marker)
        try archive.finish()
        if verify {
            try verifyExport(destination, password: password ?? "", manifest: manifestData,
                             htmlSize: Int64(html.count), files: written)
        }
    }

    private static func verifyExport(_ url: URL, password: String, manifest: Data, htmlSize: Int64,
                                     files: [(name: String, size: Int64)]) throws {
        let archive = try ArchiveReader(url, password: password)
        guard try archive.next() == "manifest.json", try archive.data(limit: manifestLimit) == manifest,
              try archive.next() == "index.html", try archive.copy(limit: htmlSize, to: { _ in }) == htmlSize else {
            throw JournalError.invalidBackup
        }
        for (name, size) in files {
            guard try archive.next() == name, try archive.copy(limit: size, to: { _ in }) == size else {
                throw JournalError.invalidBackup
            }
        }
        guard try archive.next() == "complete.txt", try archive.data(limit: 64) == marker, try archive.next() == nil else {
            throw JournalError.invalidBackup
        }
        try archive.verifyEnd()
    }

    private struct Reference: Equatable {
        var kind: AttachmentKind
        var limit: Int64
        var expectedSize: Int64?
    }

    private static func references(_ entries: [JournalRecord], allowVideo: Bool) throws -> [String: Reference] {
        guard entries.count <= 100_000 else { throw JournalError.limit }
        var result: [String: Reference] = [:]
        func add(_ name: String, _ kind: AttachmentKind, _ limit: Int64, size: Int64? = nil) throws {
            guard MediaDirectory.safeName(name) else { throw JournalError.invalidBackup }
            let value = Reference(kind: kind, limit: limit, expectedSize: size)
            guard result[name] == nil || result[name] == value else { throw JournalError.invalidBackup }
            result[name] = value
        }
        for entry in entries {
            guard !entry.id.isEmpty, entry.imageFileNames.count <= 9,
                  Set(entry.imageFileNames).count == entry.imageFileNames.count,
                  entry.labels.allSatisfy({ !$0.id.isEmpty && !$0.savedName.isEmpty }) else { throw JournalError.invalidBackup }
            for name in entry.imageFileNames { try add(name, .photo, AttachmentKind.photo.limit) }
            if let audio = entry.audio {
                guard (1...300_000).contains(audio.durationMillis),
                      audio.fileName.hasSuffix(".m4a") || audio.fileName.hasSuffix(".xike-audio")
                else { throw JournalError.invalidBackup }
                try add(audio.fileName, .audio, AttachmentKind.audio.limit)
            }
            if let video = entry.video {
                let ext = (video.fileName as NSString).pathExtension
                guard allowVideo, (1...300_000).contains(video.durationMillis),
                      (1...AttachmentKind.video.limit).contains(video.sizeBytes),
                      let expectedType = AttachmentKind.videoTypes[ext], expectedType == video.mimeType,
                      video.coverFileName.hasSuffix(".jpg"), video.fileName != video.coverFileName
                else { throw JournalError.invalidBackup }
                try add(video.fileName, .video, AttachmentKind.video.limit, size: video.sizeBytes)
                try add(video.coverFileName, .video, 2 * 1_048_576)
            }
        }
        guard result.values.lazy.filter({ $0.kind == .photo }).count <= 10_000 else { throw JournalError.limit }
        return result
    }

    private static func newName(for name: String, kind: AttachmentKind) -> String {
        let extensions = ["jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "tiff"]
        let ext = (name as NSString).pathExtension.lowercased()
        let suffix: String
        switch kind {
        case .photo: suffix = extensions.contains(ext) ? ext : "jpg"
        case .audio: suffix = "m4a"
        case .video: suffix = ext
        }
        return UUID().uuidString.lowercased() + "." + suffix
    }

    private static func normalized(manifest: ArchiveManifest, installed: [String: String],
                                   media: MediaDirectory, refs: [String: Reference]) throws -> PreparedBackup {
        var entries = manifest.entries.map { entry in
            var copy = entry
            copy.imageFileNames = entry.imageFileNames.compactMap { installed[$0] }
            if var audio = entry.audio, let name = installed[audio.fileName] {
                audio.fileName = name
                copy.audio = audio
            } else {
                copy.audio = nil
            }
            if var video = entry.video, let name = installed[video.fileName], let cover = installed[video.coverFileName] {
                video.fileName = name
                video.coverFileName = cover
                copy.video = video
            }
            return copy
        }
        // Android normalizes legacy mood/feeling names on read and stores stable label IDs.
        for index in entries.indices {
            var seen = Set<String>()
            entries[index].feelings = entries[index].feelings.filter {
                !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && seen.insert($0).inserted
            }
        }
        var catalog: [String: JournalLabel] = [:]
        for label in manifest.labelCatalog ?? JournalLabel.builtin {
            guard label.isValid, catalog.updateValue(label, forKey: label.id) == nil else { throw JournalError.invalidBackup }
        }
        for label in entries.flatMap(\.labels) {
            if let definition = catalog[label.id] {
                guard definition.kind == label.kind && definition.isBuiltin == label.isBuiltin
                else { throw JournalError.invalidBackup }
            } else {
                guard manifest.version < 10 else { throw JournalError.invalidBackup }
                catalog[label.id] = label.definition(sortOrder: catalog.count)
            }
        }
        guard manifest.version < 10 || manifest.labelCatalog != nil else { throw JournalError.invalidBackup }
        let contents = JournalContents(
            entries: entries.sorted { $0.createdAt > $1.createdAt },
            labelCatalog: catalog.values.sorted(by: JournalLabel.ordered))
        let copied = installed.filter { refs[$0.key] != nil }
        return PreparedBackup(contents: contents, media: media,
                              photoCount: copied.keys.filter { refs[$0]?.kind == .photo }.count,
                              audioCount: copied.keys.filter { refs[$0]?.kind == .audio }.count,
                              videoCount: Set(entries.compactMap { $0.video?.fileName }).count)
    }

    private struct LegacyEnvelope: Decodable {
        var format: String
        var version: Int
        var salt: Data
        var nonce: Data
        var ciphertext: Data
    }

    private struct LegacyContents: Decodable {
        var format: String
        var version: Int?
        var entries: [JournalRecord]
        var images: [String: String]?
    }

    private static func readLegacy(_ source: URL, password: String, staging: URL,
                                   mediaKey: Data) throws -> PreparedBackup {
        let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size > 0, size <= legacyLimit else { throw JournalError.limit }
        let envelope = try JSONDecoder().decode(LegacyEnvelope.self, from: Data(contentsOf: source))
        guard envelope.format == "xike-encrypted", envelope.version == 1, envelope.salt.count == 16,
              envelope.nonce.count == 12, envelope.ciphertext.count > 16 else { throw JournalError.invalidBackup }
        let secret = Array(password.utf8)
        let salt = Array(envelope.salt)
        let nonce = Array(envelope.nonce)
        var length = envelope.ciphertext.count - 16
        var plaintext = Data(count: length)
        let code = plaintext.withUnsafeMutableBytes { output in
            envelope.ciphertext.withUnsafeBytes { input in
                xike_legacy_decrypt(secret, secret.count, salt, nonce,
                                    input.bindMemory(to: UInt8.self).baseAddress, input.count,
                                    output.bindMemory(to: UInt8.self).baseAddress, &length)
            }
        }
        guard code == XIKE_OK else { throw JournalError.password }
        plaintext.count = length
        let payload = try JSONDecoder().decode(LegacyContents.self, from: plaintext)
        guard payload.format == "xike", (1...3).contains(payload.version ?? 1) else { throw JournalError.unsupportedBackup }
        let media = MediaDirectory(root: staging, key: mediaKey)
        let refs = try references(payload.entries, allowVideo: false)
        var installed: [String: String] = [:]
        var count: Int64 = 0
        for (name, encoded) in payload.images ?? [:] where refs[name]?.kind == .photo {
            try Task.checkCancellation()
            guard encoded.utf8.count <= 4 * (AttachmentKind.photo.limit / 3 + 1),
                  let bytes = Data(base64Encoded: encoded) else { throw JournalError.invalidBackup }
            count += Int64(bytes.count)
            guard count <= 1_073_741_824 else { throw JournalError.limit }
            let target = newName(for: name, kind: .photo)
            try media.store(bytes, as: target, limit: AttachmentKind.photo.limit)
            installed[name] = target
        }
        return try normalized(manifest: ArchiveManifest(version: payload.version ?? 1, entries: payload.entries),
                              installed: installed, media: media, refs: refs)
    }

    private static func html(_ entries: [JournalRecord], language: XikeLanguage) -> String {
        func escape(_ value: String) -> String {
            value.replacingOccurrences(of: "&", with: "&amp;")
                .replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;")
                .replacingOccurrences(of: "\"", with: "&quot;").replacingOccurrences(of: "'", with: "&#39;")
        }
        func link(_ name: String) -> String {
            escape(name.addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "-._"))) ?? "")
        }
        let title = language.text("息刻 · 离线日记", "Xike · Offline Journal")
        let cards = entries.map { record -> String in
            let date = escape(record.date.formatted(.dateTime.year().month().day().hour().minute().locale(language.locale)))
            let labels = (record.feelings.map { Feeling(rawValue: $0)?.label(language) ?? $0 }
                + record.labels.map(\.savedName)).map(escape).joined(separator: " · ")
            let photos = record.imageFileNames.map {
                "<a href=\"images/\(link($0))\"><img loading=\"lazy\" src=\"images/\(link($0))\" alt=\"\(escape(language.text("照片", "Photo")))\"></a>"
            }.joined()
            let audio = record.audio.map { "<audio controls preload=\"none\" src=\"audios/\(link($0.fileName))\"></audio>" } ?? ""
            let video = record.video.map {
                "<video controls preload=\"none\" poster=\"videos/\(link($0.coverFileName))\" src=\"videos/\(link($0.fileName))\"></video><a href=\"videos/\(link($0.fileName))\">\(escape(language.text("打开原文件", "Open original")))</a>"
            } ?? ""
            let outdoor = record.outdoor.map { "<p>\(escape($0.placeName)) · \(Int($0.temperatureCelsius.rounded())) °C</p>" } ?? ""
            let favorite = record.isFavorite ? " · " + language.text("已收藏", "Favorite") : ""
            return "<article><time>\(date)</time><h2>\(escape(record.displayMood.label(language) + favorite))</h2><p>\(labels)</p><p class=\"note\">\(escape(record.note))</p>\(outdoor)<div class=\"photos\">\(photos)</div>\(audio)\(video)</article>"
        }.joined(separator: "\n")
        return """
        <!doctype html><html lang="\(language.isEnglish ? "en" : "zh-Hans")"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; media-src 'self'">
        <title>\(title)</title><style>
        :root{color-scheme:light dark}body{max-width:44rem;margin:2rem auto;padding:0 1rem;background:Canvas;color:CanvasText;font:1rem/1.65 system-ui}
        article{border:1px solid GrayText;border-radius:1rem;margin:1rem 0;padding:1rem;overflow-wrap:anywhere}
        time{color:GrayText}.note{white-space:pre-wrap}.photos{display:grid;grid-template-columns:repeat(auto-fit,minmax(7rem,1fr));gap:0.5rem}
        img,video,audio{max-width:100%;display:block;margin-block:0.5rem}
        </style></head><body><h1>\(title)</h1>\(cards)</body></html>
        """
    }
}
