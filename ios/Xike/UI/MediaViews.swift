import AVFoundation
import AVKit
import Combine
import CoreTransferable
import Photos
import SwiftUI
import UIKit
import UniformTypeIdentifiers

private func copySelection(_ source: URL) throws -> URL {
    let folder = try SecureDirectory.temporary(under: FileManager.default.temporaryDirectory, protection: .complete)
    let url = folder.appending(path: "Attachment").appendingPathExtension(source.pathExtension)
    do {
        try FileManager.default.copyItem(at: source, to: url)
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: url.path)
        return url
    } catch {
        try? FileManager.default.removeItem(at: folder)
        throw error
    }
}

struct PickedPhoto: Transferable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .image) { PickedPhoto(url: try copySelection($0.file)) }
    }
}

struct PickedMovie: Transferable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .movie) { PickedMovie(url: try copySelection($0.file)) }
    }
}

struct CameraPicker: UIViewControllerRepresentable {
    var video: Bool
    var result: (Result<URL?, Error>) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(result) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        controller.delegate = context.coordinator
        if UIImagePickerController.isSourceTypeAvailable(.camera) {
            controller.sourceType = .camera
        }
        controller.mediaTypes = [video ? UTType.movie.identifier : UTType.image.identifier]
        if video {
            controller.videoMaximumDuration = 300
            controller.videoQuality = .typeHigh
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let result: (Result<URL?, Error>) -> Void
        init(_ result: @escaping (Result<URL?, Error>) -> Void) { self.result = result }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { result(.success(nil)) }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            do {
                if let source = info[.mediaURL] as? URL {
                    result(.success(try copySelection(source)))
                } else if let image = info[.originalImage] as? UIImage,
                          let data = image.jpegData(compressionQuality: 0.94) {
                    let folder = try SecureDirectory.temporary(under: FileManager.default.temporaryDirectory, protection: .complete)
                    let file = folder.appending(path: "Photo.jpg")
                    do {
                        try data.write(to: file, options: [.atomic, .completeFileProtection])
                        result(.success(file))
                    } catch {
                        try? FileManager.default.removeItem(at: folder)
                        throw error
                    }
                } else { result(.failure(JournalError.attachment)) }
            } catch { result(.failure(error)) }
        }
    }
}

struct AttachmentImage: View {
    @Environment(\.palette) private var palette
    let name: String
    let media: MediaDirectory
    var pixels = 320
    var fit = false
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        GeometryReader { geometry in
            Group {
                if let image {
                    Image(uiImage: image).resizable().aspectRatio(contentMode: fit ? .fit : .fill)
                } else {
                    ZStack {
                        palette.quiet
                        if failed { Image(systemName: "photo.badge.exclamationmark").foregroundStyle(palette.muted) }
                        else { ProgressView().controlSize(.small) }
                    }
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
        }
        .task(id: name) {
            image = nil
            failed = false
            let work = Task.detached(priority: .userInitiated) { try JournalMedia.thumbnail(name, in: media, pixels: pixels) }
            do {
                let value = try await work.value
                if !Task.isCancelled { image = value }
            } catch {
                if !Task.isCancelled { failed = true }
            }
        }
    }
}

struct PlaybackPanel: View {
    @Environment(\.scenePhase) private var phase
    @Environment(\.palette) private var palette
    @Environment(\.xikeLanguage) private var language
    let file: String
    let media: MediaDirectory
    let duration: Int64
    var video = false
    @State private var source: EncryptedPlaybackAsset?
    @State private var player: AVPlayer?
    @State private var position = 0.0
    @State private var playing = false
    @State private var error = false
    @State private var seeking = false
    private let checks = Timer.publish(every: 0.3, on: .main, in: .common).autoconnect()

    var body: some View {
        Group {
            if error {
                Label(language.text("无法播放这份附件", "This attachment can’t be played"), systemImage: "exclamationmark.circle")
                    .foregroundStyle(palette.muted)
            } else if let player {
                if video {
                    VideoPlayer(player: player).frame(minHeight: 240).clipShape(RoundedRectangle(cornerRadius: palette.buttonRadius))
                } else {
                    HStack(spacing: 12) {
                        Button {
                            if playing { player.pause() } else {
                                if position >= Double(duration) / 1000 - 0.2 { player.seek(to: .zero) }
                                player.play()
                            }
                            playing.toggle()
                        } label: {
                            Image(systemName: playing ? "pause.fill" : "play.fill")
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(playing ? language.text("暂停", "Pause") : language.text("播放录音", "Play audio"))
                        Slider(value: $position, in: 0...max(0.01, Double(duration) / 1000)) { active in
                            seeking = active
                            if !active { player.seek(to: CMTime(seconds: position, preferredTimescale: 1_000)) }
                        }
                        .accessibilityLabel(language.text("播放进度", "Playback position"))
                        Text(mediaTime(position)).font(.caption.monospacedDigit())
                    }
                }
            } else { ProgressView().frame(minHeight: 44) }
        }
        .task(id: file) {
            position = 0
            error = false
            do {
                let asset = try await Task.detached { try EncryptedPlaybackAsset(file, in: media) }.value
                guard !Task.isCancelled else { return }
                source = asset
                player = AVPlayer(playerItem: AVPlayerItem(asset: asset.asset))
            } catch { self.error = true }
        }
        .onReceive(checks) { _ in
            guard let player else { return }
            if player.currentItem?.status == .failed { error = true }
            if !seeking && player.currentTime().seconds.isFinite { position = player.currentTime().seconds }
            if playing && position >= Double(duration) / 1000 - 0.15 && player.timeControlStatus == .paused { playing = false }
        }
        .onChange(of: phase) { _, phase in
            if phase != .active {
                player?.pause()
                playing = false
            }
        }
        .onDisappear {
            player?.pause()
            player = nil
            source = nil
        }
    }
}

func mediaTime(_ seconds: Double) -> String {
    let total = Int(max(0, seconds))
    return String(format: "%d:%02d", total / 60, total % 60)
}

struct PhotoGallery: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.xikeLanguage) private var language
    let names: [String]
    let media: MediaDirectory
    @State var index: Int

    var body: some View {
        VStack {
            HStack {
                Text("\(index + 1) / \(names.count)").font(.callout.monospacedDigit())
                Spacer()
                Button(language.text("完成", "Done")) { dismiss() }.padding(.vertical, 12)
            }
            .padding(.horizontal)
            TabView(selection: $index) {
                ForEach(names.indices, id: \.self) { offset in
                    AttachmentImage(name: names[offset], media: media, pixels: 2_048, fit: true).tag(offset)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .automatic))
        }
        .background(Color.black.ignoresSafeArea()).foregroundStyle(.white)
        .preferredColorScheme(.dark)
    }
}

@MainActor @Observable
final class VoiceCapture {
    private(set) var recorder: AVAudioRecorder?
    private(set) var file: URL?
    private(set) var elapsed = 0.0
    private(set) var isPaused = false
    private(set) var finished = false
    private(set) var samples: [CGFloat] = []
    static let sampleCount = 40
    var preview: AVAudioPlayer?

    func begin(in folder: URL) async throws {
        guard await AVAudioApplication.requestRecordPermission() else { throw JournalError.unavailable }
        try Task.checkCancellation()
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try session.setActive(true)
        let url = folder.appending(path: "Voice-\(UUID().uuidString).m4a")
        do {
            let recording = try AVAudioRecorder(url: url, settings: [
                AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 44_100,
                AVNumberOfChannelsKey: 1, AVEncoderBitRateKey: 96_000,
            ])
            recording.isMeteringEnabled = true
            guard recording.record(forDuration: 300) else { throw JournalError.io }
            file = url
            recorder = recording
        } catch {
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
            try? FileManager.default.removeItem(at: url)
            throw error
        }
    }

    func tick() {
        guard let recorder, !isPaused, !finished else { return }
        if recorder.isRecording {
            elapsed = max(elapsed, recorder.currentTime)
            recorder.updateMeters()
            let level = CGFloat(recorder.peakPower(forChannel: 0))
            if samples.count >= Self.sampleCount { samples.removeFirst() }
            samples.append(level.isFinite ? min(1, max(0, (level + 50) / 50)) : 0)
        } else if elapsed > 0 { finish() }
    }

    func pause() {
        guard let recorder, recorder.isRecording, !finished else { return }
        elapsed = max(elapsed, recorder.currentTime)
        recorder.pause()
        isPaused = true
    }

    func resume() {
        guard let recorder, isPaused, !finished else { return }
        if recorder.record(forDuration: max(0, 300 - elapsed)) { isPaused = false }
    }

    func finish() {
        if let recorder {
            elapsed = min(300, max(elapsed, recorder.currentTime))
            recorder.stop()
        }
        isPaused = false
        finished = true
    }

    func previewAudio() throws {
        guard let file, finished else { throw JournalError.attachment }
        if preview?.isPlaying == true { preview?.pause(); return }
        let player = try AVAudioPlayer(contentsOf: file)
        player.play()
        preview = player
    }

    func clear() {
        preview?.stop()
        recorder?.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        if let file { try? FileManager.default.removeItem(at: file) }
        preview = nil
        recorder = nil
        file = nil
    }
}

struct VoiceCapturePanel: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.xikeLanguage) private var language
    @Environment(\.palette) private var palette
    @Environment(\.scenePhase) private var phase
    let folder: URL
    let apply: (URL, Int64) async -> Bool
    @State private var capture = VoiceCapture()
    @State private var error = false
    @State private var saving = false
    private let timer = Timer.publish(every: 0.075, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                waveform
                Text(mediaTime(capture.elapsed)).font(.largeTitle.monospacedDigit())
                if error {
                    Text(language.text("无法录音，请检查麦克风权限。", "Check microphone permission and try again."))
                        .foregroundStyle(palette.muted).multilineTextAlignment(.center)
                } else if capture.finished {
                    Button(language.text("试听", "Preview")) {
                        do { try capture.previewAudio() } catch { self.error = true }
                    }
                    XikeAction(title: language.text("使用这段录音", "Use recording"), enabled: !saving && capture.elapsed > 0) {
                        guard let url = capture.file else { return }
                        saving = true
                        Task {
                            if await apply(url, Int64((capture.elapsed * 1_000).rounded())) { dismiss() }
                            saving = false
                        }
                    }
                } else if capture.recorder != nil {
                    HStack {
                        Button(capture.isPaused ? language.text("继续", "Resume") : language.text("暂停", "Pause")) {
                            if capture.isPaused { capture.resume() } else { capture.pause() }
                        }
                        Spacer()
                        Button(language.text("完成录音", "Finish recording")) { capture.finish() }
                    }
                } else { ProgressView() }
            }
            .padding(24).frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(palette.background)
            .navigationTitle(language.text("录音", "Record audio"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(language.text("取消", "Cancel")) { dismiss() }.disabled(saving) } }
        }
        .task { do { try await capture.begin(in: folder) } catch { if !Task.isCancelled { self.error = true } } }
        .onReceive(timer) { _ in capture.tick() }
        .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.interruptionNotification)) { _ in
            if capture.recorder != nil && !capture.finished { capture.finish() }
        }
        .onChange(of: phase) { _, phase in
            if phase == .background && capture.recorder != nil && !capture.finished { capture.finish() }
        }
        .interactiveDismissDisabled(saving)
        .onDisappear { capture.clear() }
    }

    private var waveform: some View {
        let samples = capture.samples
        let count = VoiceCapture.sampleCount
        let color = palette.primary
        return Canvas { context, size in
            let step = size.width / CGFloat(count)
            let width = max(1, step * 0.55)
            for index in 0..<count {
                let level = index < count - samples.count ? 0 : samples[index - (count - samples.count)]
                let height = max(3, level * size.height)
                let bar = CGRect(x: CGFloat(index) * step + (step - width) / 2,
                                 y: (size.height - height) / 2, width: width, height: height)
                context.fill(Path(roundedRect: bar, cornerRadius: width / 2), with: .color(color))
            }
        }.frame(height: 56).accessibilityHidden(true)
    }
}
