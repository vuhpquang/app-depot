import Foundation

public let maxEventMessage = 2000

private func jsonString(_ s: String) -> String {
    let data = try! JSONEncoder().encode(s)
    return String(decoding: data, as: UTF8.self).replacingOccurrences(of: "\\/", with: "/")
}

/// ISO-8601 UTC, like Kotlin's `Instant.toString()`: no fraction for whole seconds, milliseconds otherwise.
public func isoUTC(_ date: Date) -> String {
    let f = ISO8601DateFormatter()
    let whole = date.timeIntervalSince1970.rounded(.down) == date.timeIntervalSince1970
    f.formatOptions = whole ? [.withInternetDateTime] : [.withInternetDateTime, .withFractionalSeconds]
    return f.string(from: date)
}

extension DepotEvent {
    /// The wire form of `POST /api/depot/events`: fixed key order, nulls omitted, message ≤ 2000 chars.
    public func wireJSON() -> String {
        var parts = ["\"type\":\(jsonString(type))"]
        if let appSlug { parts.append("\"appSlug\":\(jsonString(appSlug))") }
        if let fromVersion { parts.append("\"fromVersion\":\(jsonString(fromVersion))") }
        if let toVersion { parts.append("\"toVersion\":\(jsonString(toVersion))") }
        if let message { parts.append("\"message\":\(jsonString(String(message.prefix(maxEventMessage))))") }
        parts.append("\"at\":\(jsonString(isoUTC(at)))")
        return "{" + parts.joined(separator: ",") + "}"
    }
}

/// Splits outbox rows (id, json) into server-sized batches, keeping order.
public func batchesOf(_ rows: [(Int64, String)], max: Int = 200) -> [[(Int64, String)]] {
    stride(from: 0, to: rows.count, by: max).map { Array(rows[$0..<min($0 + max, rows.count)]) }
}

public func eventsBody(_ jsonEvents: [String]) -> String { "{\"events\":[\(jsonEvents.joined(separator: ","))]}" }

/// Rows to drop (oldest first) so the outbox never grows past `cap`.
public func trimOutbox(_ count: Int, cap: Int = 1000) -> Int { Swift.max(count - cap, 0) }

public enum EventsOutcome: Equatable, Sendable { case accepted, unauthorized, badBatch, retry }

public func parseEventsStatus(_ status: Int) -> EventsOutcome {
    switch status {
    case 200...299: return .accepted
    case 401: return .unauthorized
    case 400: return .badBatch
    default: return .retry
    }
}

/// A debug-only server override: a usable http(s) base URL without the trailing slash, or nil.
public func normalizeServerUrl(_ input: String) -> String? {
    var s = input.trimmingCharacters(in: .whitespaces)
    while s.hasSuffix("/") { s.removeLast() }
    return s.range(of: #"^https?://[^\s/]+(/\S*)?$"#, options: .regularExpression) != nil ? s : nil
}
