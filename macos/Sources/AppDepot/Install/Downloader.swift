// Install/Downloader.swift — downloads a release asset to App Depot's cache with progress, refusing
// anything bigger than the catalog says (or than the size cap).
import Foundation

protocol Downloading: Sendable {
    /// Downloads `url` to `destination` (replaced if present). `progress` gets 0…100.
    func download(_ url: URL, to destination: URL, expectedSize: Int64,
                  progress: @escaping @Sendable (Int) -> Void) async throws
}

struct DownloadError: LocalizedError {
    let errorDescription: String?
    init(_ message: String) { errorDescription = message }
}

/// Never download more than this, whatever the catalog says.
let maxDownloadBytes: Int64 = 1 << 30

struct URLSessionDownloader: Downloading {
    let userAgent: String

    func download(_ url: URL, to destination: URL, expectedSize: Int64,
                  progress: @escaping @Sendable (Int) -> Void) async throws {
        guard url.scheme == "https" else { throw DownloadError("The download link isn't secure") }
        guard expectedSize > 0, expectedSize <= maxDownloadBytes else { throw DownloadError("The download is too big") }
        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 60
        let delegate = ProgressDelegate(expected: expectedSize, progress: progress)
        let (temp, response) = try await URLSession.shared.download(for: request, delegate: delegate)
        defer { try? FileManager.default.removeItem(at: temp) }
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw DownloadError("The server refused the download") }
        let fm = FileManager.default
        try fm.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? fm.removeItem(at: destination)
        try fm.moveItem(at: temp, to: destination)
        progress(100)
    }
}

/// Reports progress and cancels a transfer that grows past the expected size.
private final class ProgressDelegate: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    let expected: Int64
    let progress: @Sendable (Int) -> Void
    private var last = -1

    init(expected: Int64, progress: @escaping @Sendable (Int) -> Void) {
        self.expected = expected
        self.progress = progress
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64,
                    totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        if totalBytesWritten > expected || totalBytesWritten > maxDownloadBytes {
            downloadTask.cancel()
            return
        }
        let pct = Int(min(99, totalBytesWritten * 100 / max(expected, 1)))
        if pct != last {
            last = pct
            progress(pct)
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {}
}
