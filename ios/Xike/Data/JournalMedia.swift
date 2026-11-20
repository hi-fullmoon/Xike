import AVFoundation
import ImageIO
import UIKit
import UniformTypeIdentifiers

final class EncryptedPlaybackAsset: NSObject, AVAssetResourceLoaderDelegate {
    let asset: AVURLAsset
    private let reader: ProtectedReader
    private let mimeType: String
    private let queue = DispatchQueue(label: "com.xike.media-reader", qos: .userInitiated)

    init(_ name: String, in media: MediaDirectory) throws {
        reader = try media.reader(name)
        let ext = (name as NSString).pathExtension
        mimeType = UTType(filenameExtension: ext)?.identifier ?? UTType.data.identifier
        var url = URLComponents()
        url.scheme = "xike-media"
        url.host = "local"
        url.path = "/" + name
        guard let address = url.url else { throw JournalError.attachment }
        asset = AVURLAsset(url: address)
        super.init()
        asset.resourceLoader.setDelegate(self, queue: queue)
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader,
                        shouldWaitForLoadingOfRequestedResource request: AVAssetResourceLoadingRequest) -> Bool {
        request.contentInformationRequest?.contentLength = reader.size
        request.contentInformationRequest?.contentType = mimeType
        request.contentInformationRequest?.isByteRangeAccessSupported = true
        if let data = request.dataRequest {
            var offset = max(data.currentOffset, data.requestedOffset)
            let end = data.requestsAllDataToEndOfResource ? reader.size
                : min(reader.size, data.requestedOffset + Int64(data.requestedLength))
            do {
                while offset < end && !request.isCancelled {
                    let bytes = try reader.read(offset: offset, count: Int(min(64 * 1_024, end - offset)))
                    guard !bytes.isEmpty else { throw JournalError.attachment }
                    data.respond(with: bytes)
                    offset += Int64(bytes.count)
                }
            } catch {
                request.finishLoading(with: error)
                return true
            }
        }
        if !request.isCancelled { request.finishLoading() }
        return true
    }
}

enum JournalMedia {
    static func importPhoto(_ source: URL, in media: MediaDirectory) throws -> String {
        let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size > 0 && size <= AttachmentKind.photo.limit else { throw JournalError.limit }
        guard let image = CGImageSourceCreateWithURL(source as CFURL, nil),
              CGImageSourceGetCount(image) > 0, let id = CGImageSourceGetType(image) as String?,
              let type = UTType(id), let ext = type.preferredFilenameExtension else { throw JournalError.attachment }
        let name = UUID().uuidString.lowercased() + "." + ext
        _ = try media.store(source, as: name, limit: AttachmentKind.photo.limit)
        return name
    }

    static func importRecording(_ source: URL, duration: Int64, in media: MediaDirectory) throws -> JournalAudio {
        guard (1...300_000).contains(duration) else { throw JournalError.limit }
        let name = UUID().uuidString.lowercased() + ".m4a"
        _ = try media.store(source, as: name, limit: AttachmentKind.audio.limit)
        return JournalAudio(fileName: name, durationMillis: duration)
    }

    static func importVideo(_ source: URL, in media: MediaDirectory) async throws -> JournalVideo {
        let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size > 0 && size <= AttachmentKind.video.limit else { throw JournalError.limit }
        let ext = source.pathExtension.lowercased()
        guard let mime = AttachmentKind.videoTypes[ext] else { throw JournalError.attachment }
        let asset = AVURLAsset(url: source)
        let duration = try await durationMillis(asset)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 640, height: 640)
        let frame = try await generator.image(at: .zero).image
        guard let jpeg = UIImage(cgImage: frame).jpegData(compressionQuality: 0.83)
        else { throw JournalError.attachment }
        let id = UUID().uuidString.lowercased()
        let name = id + "." + ext
        let cover = id + "-cover.jpg"
        let written = try media.store(source, as: name, limit: AttachmentKind.video.limit)
        do {
            try media.store(jpeg, as: cover, limit: 2 * 1_048_576)
        } catch {
            media.remove(name)
            throw error
        }
        return JournalVideo(fileName: name, coverFileName: cover, durationMillis: duration,
                            sizeBytes: written, mimeType: mime)
    }

    static func validate(_ video: JournalVideo, in media: MediaDirectory) async throws {
        let cover = try media.reader(video.coverFileName)
        guard cover.size <= 2 * 1_048_576,
              let image = CGImageSourceCreateWithData(try cover.read(offset: 0, count: Int(cover.size)) as CFData, nil),
              CGImageSourceGetType(image) as String? == UTType.jpeg.identifier,
              let properties = CGImageSourceCopyPropertiesAtIndex(image, 0, nil) as? [String: Any],
              let width = properties[kCGImagePropertyPixelWidth as String] as? Int,
              let height = properties[kCGImagePropertyPixelHeight as String] as? Int,
              (1...640).contains(width), (1...640).contains(height) else { throw JournalError.attachment }
        let extensionName = (video.fileName as NSString).pathExtension
        let duration: Int64
        if extensionName == "webm" || extensionName == "mkv" {
            duration = try media.reader(video.fileName).matroskaDuration()
        } else {
            let playback = try EncryptedPlaybackAsset(video.fileName, in: media)
            duration = try await durationMillis(playback.asset)
        }
        guard abs(duration - video.durationMillis) <= 1_000 else { throw JournalError.invalidBackup }
    }

    static func thumbnail(_ name: String, in media: MediaDirectory, pixels: Int) throws -> UIImage {
        let source = try media.reader(name)
        guard source.size <= AttachmentKind.photo.limit else { throw JournalError.limit }
        let data = try source.read(offset: 0, count: Int(source.size))
        guard let image = CGImageSourceCreateWithData(data as CFData, nil),
              let result = CGImageSourceCreateThumbnailAtIndex(image, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: pixels,
              ] as CFDictionary) else { throw JournalError.attachment }
        return UIImage(cgImage: result)
    }

    private static func durationMillis(_ asset: AVURLAsset) async throws -> Int64 {
        let duration = try await asset.load(.duration).seconds
        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard duration.isFinite, duration > 0, duration <= 300, !tracks.isEmpty else { throw JournalError.limit }
        return Int64((duration * 1_000).rounded())
    }
}
