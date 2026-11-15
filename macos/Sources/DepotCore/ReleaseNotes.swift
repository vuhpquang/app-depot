import Foundation

/// Minimal Markdown for release notes: `#`..`######` headings and `- `/`* ` bullets; everything else
/// (HTML too) is plain text, never interpreted. Same rules as Android and the portfolio.
public enum NoteItem: Equatable, Sendable {
    case heading(String)
    case bullet(String)
    case text(String)
}

/// An indented line right after a bullet continues that bullet (notes wrapped at 100 columns).
public func parseReleaseNotes(_ md: String) -> [NoteItem] {
    var items: [NoteItem] = []
    var previousRawWasBullet = false
    for raw in md.replacingOccurrences(of: "\r\n", with: "\n").split(separator: "\n", omittingEmptySubsequences: false) {
        let line = raw.trimmingCharacters(in: .whitespaces)
        if line.isEmpty { previousRawWasBullet = false; continue }
        if let m = line.firstMatch(of: /^#{1,6}\s+(.*)$/) {
            items.append(.heading(String(m.1).trimmingCharacters(in: .whitespaces)))
            previousRawWasBullet = false
        } else if let m = line.firstMatch(of: /^[-*]\s+(.*)$/) {
            items.append(.bullet(String(m.1).trimmingCharacters(in: .whitespaces)))
            previousRawWasBullet = true
        } else if previousRawWasBullet, raw.first?.isWhitespace == true, case let .bullet(text)? = items.last {
            items[items.count - 1] = .bullet(text + " " + line)
        } else {
            items.append(.text(line))
            previousRawWasBullet = false
        }
    }
    return items
}
