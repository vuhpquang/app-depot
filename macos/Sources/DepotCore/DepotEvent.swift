import Foundation

/// One history row / server event. `type` is one of `DepotEvent.serverTypes`, or a local-only type.
public struct DepotEvent: Equatable, Sendable {
    public var type: String
    public var appSlug: String?
    public var fromVersion: String?
    public var toVersion: String?
    public var message: String?
    public var at: Date

    public init(type: String, appSlug: String? = nil, fromVersion: String? = nil, toVersion: String? = nil,
                message: String? = nil, at: Date = Date()) {
        self.type = type
        self.appSlug = appSlug
        self.fromVersion = fromVersion
        self.toVersion = toVersion
        self.message = message
        self.at = at
    }

    /// The server's closed list of event types.
    public static let serverTypes = ["check", "install", "update", "install_failed", "open", "crash", "depot_started", "self_update"]
}

/// Where events go (history + outbox in the app, a recorder in tests).
public protocol EventSink: AnyObject {
    func record(_ e: DepotEvent)
}

/// Event types that stay in local history only: the server rejects types it doesn't know.
public let localOnlyEventTypes: Set<String> = ["uninstall"]

public func isUploaded(_ type: String) -> Bool { !localOnlyEventTypes.contains(type) }
