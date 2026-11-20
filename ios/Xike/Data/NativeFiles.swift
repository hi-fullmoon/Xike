import CryptoKit
import Foundation
import Security

private let chunkSize = 64 * 1_024

enum FileDigest {
    static func equals(_ first: URL, _ second: URL) throws -> Bool {
        try digest(first) == digest(second)
    }

    private static func digest(_ url: URL) throws -> SHA256.Digest {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        var hash = SHA256()
        while let bytes = try file.read(upToCount: chunkSize), !bytes.isEmpty {
            try Task.checkCancellation()
            hash.update(data: bytes)
        }
        return hash.finalize()
    }
}

private func bridgeFailure(_ code: Int32) -> JournalError {
    switch code {
    case Int32(XIKE_AUTH): .password
    case Int32(XIKE_FORMAT): .invalidBackup
    case Int32(XIKE_LIMIT): .limit
    default: .io
    }
}

enum BackupFileKind {
    case zip, encrypted, legacy

    init(url: URL) throws {
        let code = xike_backup_kind(url.path)
        switch code {
        case Int32(XIKE_ZIP): self = .zip
        case Int32(XIKE_ENCRYPTED_ZIP): self = .encrypted
        case Int32(XIKE_LEGACY_JSON): self = .legacy
        default: throw bridgeFailure(code)
        }
    }
}

struct SecureDirectory {
    static func make(_ url: URL, protection: FileProtectionType = .completeUntilFirstUserAuthentication) throws {
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true,
                                                attributes: [.protectionKey: protection])
        var folder = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try folder.setResourceValues(values)
    }

    static func temporary(under parent: URL, protection: FileProtectionType = .completeUntilFirstUserAuthentication)
        throws -> URL {
        let folder = parent.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try make(folder, protection: protection)
        return folder
    }
}

enum DeviceSecrets {
    private static func query(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: Bundle.main.bundleIdentifier ?? "com.xike.app",
         kSecAttrAccount as String: account]
    }

    static func read(_ account: String) throws -> Data? {
        var lookup = query(account)
        lookup[kSecMatchLimit as String] = kSecMatchLimitOne
        lookup[kSecReturnData as String] = true
        var value: CFTypeRef?
        let result = SecItemCopyMatching(lookup as CFDictionary, &value)
        if result == errSecItemNotFound { return nil }
        guard result == errSecSuccess, let data = value as? Data else { throw JournalError.key }
        return data
    }

    static func write(_ account: String, _ value: Data) throws {
        let lookup = query(account)
        var attributes = lookup
        attributes[kSecValueData as String] = value
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let result = SecItemAdd(attributes as CFDictionary, nil)
        if result == errSecDuplicateItem {
            guard SecItemUpdate(lookup as CFDictionary, [kSecValueData as String: value] as CFDictionary)
                    == errSecSuccess else { throw JournalError.key }
        } else if result != errSecSuccess {
            throw JournalError.key
        }
    }

    static func randomKey(_ account: String, existingStore: Bool) throws -> Data {
        if let key = try read(account) {
            guard key.count == 32 else { throw JournalError.key }
            return key
        }
        guard !existingStore else { throw JournalError.key }
        var key = Data(count: 32)
        let result = key.withUnsafeMutableBytes { bytes in
            SecRandomCopyBytes(kSecRandomDefault, bytes.count, bytes.baseAddress!)
        }
        guard result == errSecSuccess else { throw JournalError.key }
        try write(account, key)
        return key
    }
}

final class ProtectedReader {
    private let handle: OpaquePointer
    let size: Int64

    init(url: URL, key: Data, name: String) throws {
        var code: Int32 = 0
        guard let handle = key.withUnsafeBytes({ bytes in
            xike_media_reader(url.path, bytes.bindMemory(to: UInt8.self).baseAddress, name, &code)
        }) else { throw JournalError.attachment }
        self.handle = handle
        size = xike_media_size(handle)
    }

    deinit { xike_media_reader_close(handle) }

    func read(offset: Int64, count: Int) throws -> Data {
        var data = Data(count: count)
        let result = data.withUnsafeMutableBytes { bytes in
            xike_media_read(handle, offset, bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count)
        }
        guard result >= 0 else { throw JournalError.attachment }
        data.count = Int(result)
        return data
    }

    func matroskaDuration() throws -> Int64 {
        let duration = xike_matroska_duration(handle)
        guard duration > 0 else { throw JournalError.attachment }
        return duration
    }

    func copy(to write: (Data) throws -> Void) throws {
        var offset: Int64 = 0
        while offset < size {
            try Task.checkCancellation()
            let data = try read(offset: offset, count: chunkSize)
            guard !data.isEmpty else { throw JournalError.attachment }
            try write(data)
            offset += Int64(data.count)
        }
    }
}

final class ProtectedWriter {
    private var handle: OpaquePointer?

    init(url: URL, key: Data, name: String) throws {
        var code: Int32 = 0
        handle = key.withUnsafeBytes { bytes in
            xike_media_writer(url.path, bytes.bindMemory(to: UInt8.self).baseAddress, name, &code)
        }
        guard handle != nil else { throw bridgeFailure(code) }
    }

    deinit { if let handle { _ = xike_media_writer_close(handle, 0) } }

    func write(_ data: Data) throws {
        guard let handle else { throw JournalError.io }
        let code = data.withUnsafeBytes { bytes in
            xike_media_write(handle, bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count)
        }
        guard code == XIKE_OK else { throw bridgeFailure(code) }
    }

    func finish() throws {
        guard let handle else { throw JournalError.io }
        self.handle = nil
        let code = xike_media_writer_close(handle, 1)
        guard code == XIKE_OK else { throw bridgeFailure(code) }
    }
}

struct MediaDirectory: Sendable {
    let root: URL
    let key: Data

    static func safeName(_ value: String) -> Bool {
        !value.isEmpty && value.utf16.count <= 160 && !value.hasPrefix(".")
            && value.rangeOfCharacter(from: .controlCharacters) == nil
            && !value.contains("/") && !value.contains("\\") && !value.contains(":")
    }

    func path(_ name: String) throws -> URL {
        guard Self.safeName(name) else { throw JournalError.attachment }
        return root.appending(path: name, directoryHint: .notDirectory)
    }

    func reader(_ name: String) throws -> ProtectedReader {
        try ProtectedReader(url: path(name), key: key, name: name)
    }

    func writer(_ name: String) throws -> ProtectedWriter {
        try ProtectedWriter(url: path(name), key: key, name: name)
    }

    func remove(_ name: String) {
        if let url = try? path(name) { try? FileManager.default.removeItem(at: url) }
    }

    func digest(_ name: String) throws -> String {
        var hash = SHA256()
        try reader(name).copy { hash.update(data: $0) }
        return Data(hash.finalize()).base64EncodedString()
    }

    func store(_ input: URL, as name: String, limit: Int64) throws -> Int64 {
        let source = try FileHandle(forReadingFrom: input)
        defer { try? source.close() }
        let destination = try writer(name)
        var size: Int64 = 0
        while let data = try source.read(upToCount: chunkSize), !data.isEmpty {
            try Task.checkCancellation()
            size += Int64(data.count)
            guard size <= limit else { throw JournalError.limit }
            try destination.write(data)
        }
        guard size > 0 else { throw JournalError.attachment }
        try destination.finish()
        return size
    }

    func store(_ data: Data, as name: String, limit: Int64) throws {
        guard !data.isEmpty, data.count <= limit else { throw JournalError.limit }
        let destination = try writer(name)
        try destination.write(data)
        try destination.finish()
    }
}

final class ArchiveReader {
    private let handle: OpaquePointer

    init(_ url: URL, password: String) throws {
        var code: Int32 = 0
        guard let handle = Data(password.utf8).withUnsafeBytes({ bytes in
            xike_archive_reader(url.path, bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count, &code)
        }) else { throw bridgeFailure(code) }
        self.handle = handle
    }

    deinit { xike_archive_reader_close(handle) }

    func next() throws -> String? {
        var name: UnsafePointer<CChar>?
        let code = xike_archive_next(handle, &name)
        if code == XIKE_END { return nil }
        guard code == XIKE_OK, let name, let value = String(validatingCString: name) else {
            throw bridgeFailure(code)
        }
        return value
    }

    @discardableResult
    func copy(limit: Int64, to write: (Data) throws -> Void) throws -> Int64 {
        var total: Int64 = 0
        var buffer = Data(count: chunkSize)
        while true {
            try Task.checkCancellation()
            let count = buffer.withUnsafeMutableBytes { bytes in
                xike_archive_read(handle, bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count)
            }
            guard count >= 0 else { throw bridgeFailure(Int32(count)) }
            if count == 0 { return total }
            total += count
            guard total <= limit else { throw JournalError.limit }
            try write(buffer.prefix(Int(count)))
        }
    }

    func data(limit: Int64) throws -> Data {
        var value = Data()
        try copy(limit: limit) { value.append($0) }
        return value
    }

    func verifyEnd() throws {
        let code = xike_archive_verify_end(handle)
        guard code == XIKE_OK else { throw bridgeFailure(code) }
    }
}

final class ArchiveWriter {
    private var handle: OpaquePointer?

    init(_ url: URL, password: String?) throws {
        var code: Int32 = 0
        handle = Data((password ?? "").utf8).withUnsafeBytes { bytes in
            xike_archive_writer(url.path, bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count,
                                password == nil ? 0 : 1, &code)
        }
        guard handle != nil else { throw bridgeFailure(code) }
    }

    deinit { if let handle { _ = xike_archive_writer_close(handle, 0) } }

    func add(_ name: String, data: Data) throws {
        try add(name, size: Int64(data.count)) { write in try write(data) }
    }

    func add(_ name: String, size: Int64, writeContents: ((Data) throws -> Void) throws -> Void) throws {
        guard let handle, xike_archive_add(handle, name, size) == XIKE_OK else { throw JournalError.io }
        try writeContents { data in
            let code = data.withUnsafeBytes { bytes in
                xike_archive_write(handle, bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count)
            }
            guard code == XIKE_OK else { throw bridgeFailure(code) }
        }
    }

    func finish() throws {
        guard let handle else { throw JournalError.io }
        self.handle = nil
        let code = xike_archive_writer_close(handle, 1)
        guard code == XIKE_OK else { throw bridgeFailure(code) }
    }
}
