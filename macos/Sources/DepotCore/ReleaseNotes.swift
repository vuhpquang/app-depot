import Foundation

/// Minimal Markdown for release notes: `#`..`######` headings and `- `/`* ` bullets; everything else
/// (HTML too) is plain text, never interpreted. Same rules as Android and the portfolio.
public enum NoteItem: Equatable, Sendable {
    case heading(String)
    case bullet(String)
    case text(String)
}

public func parseReleaseNotes(_ md: String) -> [NoteItem] {
    md.replacingOccurrences(of: "\r\n", with: "\n").split(separator: "\n", omittingEmptySubsequences: false).compactMap { raw in
        let line = raw.trimmingCharacters(in: .whitespaces)
        if line.isEmpty { return nil }
        if let m = line.firstMatch(of: /^#{1,6}\s+(.*)$/) { return .heading(String(m.1).trimmingCharacters(in: .whitespaces)) }
        if let m = line.firstMatch(of: /^[-*]\s+(.*)$/) { return .bullet(String(m.1).trimmingCharacters(in: .whitespaces)) }
        return .text(line)
    }
}
