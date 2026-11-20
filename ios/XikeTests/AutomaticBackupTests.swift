import Foundation
import XCTest
@testable import Xike

final class AutomaticBackupTests: XCTestCase {
    func testChangingRetentionWaitsForAVerifiedBackup() async throws {
        guard ProcessInfo.processInfo.environment["XIKE_VERIFY_AUTO_BACKUP"] == "1" else {
            throw XCTSkip("Run on a new, independent simulator. This test writes real automatic backups.")
        }
        let initial = AutomaticBackup.status()
        XCTAssertTrue(initial.files.isEmpty)
        guard initial.files.isEmpty else { return }
        let worker = XikeServices.automaticBackup
        let password = UUID().uuidString
        var options = AutoBackupOptions()
        options.enabled = true
        options.weekly = false
        options.keep = 2

        try await runNextBackup(&options, worker: worker, password: password)
        try await runNextBackup(&options, worker: worker)
        let verified = AutomaticBackup.status()
        XCTAssertEqual(verified.files.count, 2)

        let documents = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask,
                                                     appropriateFor: nil, create: false)
        let folder = documents.appending(path: "Backups", directoryHint: .isDirectory)
        func exists(_ name: String) -> Bool { FileManager.default.fileExists(atPath: folder.appending(path: name).path) }
        XCTAssertTrue(verified.files.allSatisfy(exists))

        options.keep = 1
        try await worker.configure(options, password: nil)
        XCTAssertEqual(AutomaticBackup.status().due, verified.due)
        XCTAssertEqual(LocalPreferences.read().backup, options)
        let checked = await worker.runIfDue()
        XCTAssertTrue(checked)
        XCTAssertEqual(AutomaticBackup.status().files.count, 2)
        XCTAssertTrue(verified.files.allSatisfy(exists))

        try await runNextBackup(&options, worker: worker)
        let retained = AutomaticBackup.status()
        XCTAssertEqual(retained.files.count, 1)
        XCTAssertTrue(verified.files.allSatisfy { !exists($0) })
        let newest = try XCTUnwrap(retained.files.first)
        let backup = try await XikeServices.store.prepareBackup(folder.appending(path: newest), password: password)
        let entries = try await XikeServices.store.entries()
        XCTAssertEqual(backup.contents.entries.count, entries.count)
    }

    private func runNextBackup(_ options: inout AutoBackupOptions, worker: AutomaticBackup,
                               password: String? = nil) async throws {
        let calendar = Calendar.xike
        let scheduled = calendar.dateComponents([.hour, .minute], from: .now.addingTimeInterval(65))
        options.minutes = 60 * (scheduled.hour ?? 0) + (scheduled.minute ?? 0)
        try await worker.configure(options, password: password)
        let due = try XCTUnwrap(AutomaticBackup.status().due)
        let delay = due.timeIntervalSinceNow
        XCTAssertLessThanOrEqual(delay, 70)
        guard delay <= 70 else { return }
        try await Task.sleep(for: .seconds(max(0, delay) + 0.2))
        let result = await worker.runIfDue()
        XCTAssertTrue(result)
    }
}
