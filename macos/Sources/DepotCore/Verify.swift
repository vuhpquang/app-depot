import CryptoKit
import Foundation

public enum VerifyResult: Equatable, Sendable { case ok, wrongSize, wrongHash }

/// Streams the file through SHA-256.
public func sha256Hex(of file: URL) throws -> String {
    let handle = try FileHandle(forReadingFrom: file)
    defer { try? handle.close() }
    var hasher = SHA256()
    while let chunk = try handle.read(upToCount: 1 << 16), !chunk.isEmpty { hasher.update(data: chunk) }
    return hasher.finalize().map { String(format: "%02x", $0) }.joined()
}

/// Size first (cheap), then the hash, against the catalog values.
public func verifyDownload(_ file: URL, expectedSize: Int64, expectedSha256: String) -> VerifyResult {
    let size = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? NSNumber)?.int64Value
    guard size == expectedSize else { return .wrongSize }
    guard let actual = try? sha256Hex(of: file) else { return .wrongHash }
    return actual.caseInsensitiveCompare(expectedSha256) == .orderedSame ? .ok : .wrongHash
}
