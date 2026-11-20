import Foundation
import XCTest
@testable import Xike

final class AndroidBackupInteropTests: XCTestCase {
    func testRestoreAndReExportAndroidBackup() async throws {
        let environment = ProcessInfo.processInfo.environment
        guard let password = environment["XIKE_INTEROP_PASSWORD"], !password.isEmpty else {
            throw XCTSkip("Provide the password of a backup exported by the Android app on an isolated device.")
        }
        let documents = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask,
                                                     appropriateFor: nil, create: false)
        let source = documents.appending(path: "AndroidInterop.xike")
        guard FileManager.default.fileExists(atPath: source.path) else {
            throw XCTSkip("Place the actual Android export in the isolated test app’s Documents directory.")
        }

        let store = XikeServices.store
        _ = try await store.open()
        let before = try await store.entries()
        do {
            _ = try await store.prepareBackup(source, password: password + "-incorrect")
            XCTFail("An incorrect password was accepted.")
        } catch let error as JournalError {
            XCTAssertEqual(error, .password)
        }

        let truncated = try SecureDirectory.temporary(under: FileManager.default.temporaryDirectory)
        defer { try? FileManager.default.removeItem(at: truncated) }
        let shortFile = truncated.appending(path: "Incomplete.xike")
        try FileManager.default.copyItem(at: source, to: shortFile)
        let writer = try FileHandle(forWritingTo: shortFile)
        let length = try writer.seekToEnd()
        try writer.truncate(atOffset: length - 1)
        try writer.close()
        do {
            _ = try await store.prepareBackup(shortFile, password: password)
            XCTFail("A truncated authenticated file was accepted.")
        } catch let error as JournalError {
            XCTAssertTrue(error == .password || error == .invalidBackup)
        }
        let unchanged = try await store.entries()
        XCTAssertTrue(unchanged == before)

        let inspected = try await store.prepareBackup(source, password: password)
        XCTAssertFalse(inspected.contents.entries.isEmpty)
        if environment["XIKE_INTEROP_EXPECT_MEDIA"] == "1" {
            XCTAssertGreaterThan(inspected.photoCount, 0)
            XCTAssertGreaterThan(inspected.videoCount, 0)
            if let image = inspected.contents.entries.lazy.flatMap(\.imageFileNames).first {
                let thumbnail = try JournalMedia.thumbnail(image, in: inspected.media, pixels: 320)
                XCTAssertGreaterThan(thumbnail.size.width, 0)
            }
        }
        let preview = try await store.previewMerge(inspected)
        let restore = try await store.restore(inspected, mode: .merge)
        XCTAssertEqual(preview.additions, restore.additions)
        XCTAssertEqual(preview.conflicts, restore.conflicts)
        XCTAssertEqual(preview.favorites, restore.favorites)
        XCTAssertTrue(restore.changed, "Use a dedicated validation device with the fixture not yet imported.")
        let loaded = try await store.entries()
        let library = try await store.libraryStatus(today: .now)
        XCTAssertTrue(library.canUndoRestore)
        XCTAssertGreaterThanOrEqual(loaded.count, inspected.contents.entries.count)

        let duplicate = try await store.prepareBackup(source, password: password)
        let repeated = try await store.restore(duplicate, mode: .merge)
        XCTAssertFalse(repeated.changed)
        let afterRepeat = try await store.entries()
        let repeatedLibrary = try await store.libraryStatus(today: .now)
        XCTAssertTrue(repeatedLibrary.canUndoRestore)
        XCTAssertTrue(loaded == afterRepeat)

        let output = try await store.export(password: password, language: .chinese, verify: true)
        defer { try? FileManager.default.removeItem(at: output.deletingLastPathComponent()) }
        let exchange = documents.appending(path: "iOSInterop.xike")
        if FileManager.default.fileExists(atPath: exchange.path) { try FileManager.default.removeItem(at: exchange) }
        try FileManager.default.copyItem(at: output, to: exchange)

        let roundTrip = try await store.prepareBackup(output, password: password)
        XCTAssertEqual(roundTrip.contents.entries.count, loaded.count)
        XCTAssertEqual(roundTrip.contents.entries.filter(\.isFavorite).count, loaded.filter(\.isFavorite).count)
        let repeatedRoundTrip = try await store.restore(roundTrip, mode: .merge)
        XCTAssertFalse(repeatedRoundTrip.changed)

        let unicodePassword = password + "迁移核验"
        let unicodeArchive = try await store.export(password: unicodePassword, language: .chinese)
        defer { try? FileManager.default.removeItem(at: unicodeArchive.deletingLastPathComponent()) }
        let unicodeRoundTrip = try await store.prepareBackup(unicodeArchive, password: unicodePassword)
        XCTAssertEqual(unicodeRoundTrip.contents.entries.count, loaded.count)

        let incoming = try XCTUnwrap(inspected.contents.entries.first)
        let local = try await store.entry(incoming.id)
        var edit = JournalDraft(try XCTUnwrap(local))
        edit.note += "\n\nReviewed after migration."
        _ = try await store.save(edit, revision: 1, editing: true)
        let conflict = try await store.prepareBackup(source, password: password)
        let estimate = try await store.previewMerge(conflict)
        XCTAssertEqual(estimate.additions, 1)
        XCTAssertEqual(estimate.conflicts, 1)
        let merged = try await store.restore(conflict, mode: .merge)
        XCTAssertEqual(merged.additions, estimate.additions)
        XCTAssertEqual(merged.conflicts, estimate.conflicts)
        let repeatConflict = try await store.prepareBackup(source, password: password)
        let duplicateConflict = try await store.restore(repeatConflict, mode: .merge)
        XCTAssertFalse(duplicateConflict.changed)
        try await store.undoRestore()
        let afterUndo = try await store.entries()
        XCTAssertEqual(afterUndo.count, loaded.count)
        XCTAssertTrue(afterUndo.first { $0.id == incoming.id }?.note == edit.note)
    }
}
