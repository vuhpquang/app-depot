// Background/Notifications.swift — the few notifications App Depot posts (asks for permission once).
// A run from `.build` has no bundle id, and UNUserNotificationCenter needs one: then nothing is posted.
import AppKit
import Foundation
@preconcurrency import UserNotifications

@MainActor
protocol Notifying: AnyObject {
    /// "n updates available"; false when it couldn't be shown (no permission), so it's tried again next run.
    func updatesAvailable(_ names: [String]) async -> Bool
    func updated(_ name: String, version: String) async
    /// Updates that need the user (the app is open); false when it couldn't be shown.
    func finishUpdating(_ names: [String]) async -> Bool
}

@MainActor
final class SystemNotifier: NSObject, Notifying, UNUserNotificationCenterDelegate {
    static let shared = SystemNotifier()
    /// Opens the main window (set by the app once its scenes exist).
    var onOpen: (@MainActor () -> Void)?
    private let defaults = DepotDefaults.store

    private var center: UNUserNotificationCenter? {
        Bundle.main.bundleIdentifier == nil ? nil : UNUserNotificationCenter.current()
    }

    func activate() { center?.delegate = self }

    private func allowed() async -> Bool {
        guard let center else { return false }
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional: return true
        case .notDetermined:
            guard !defaults.bool(forKey: "appdepot.notifications.asked") else { return false }
            defaults.set(true, forKey: "appdepot.notifications.asked")
            return (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        default: return false
        }
    }

    private func post(_ id: String, title: String, body: String) async -> Bool {
        guard await allowed(), let center else { return false }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        do {
            try await center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
            return true
        } catch {
            return false
        }
    }

    static func list(_ names: [String]) -> String {
        ListFormatter.localizedString(byJoining: names)
    }

    func updatesAvailable(_ names: [String]) async -> Bool {
        guard !names.isEmpty else { return true }
        let title = names.count == 1 ? "Update available" : "\(names.count) updates available"
        return await post("updates", title: title, body: "\(Self.list(names)). Open App Depot to update.")
    }

    func updated(_ name: String, version: String) async {
        _ = await post("updated-\(name)", title: "\(name) updated", body: "\(name) \(version) is installed.")
    }

    func finishUpdating(_ names: [String]) async -> Bool {
        guard !names.isEmpty else { return true }
        return await post("needs-you", title: "Finish updating",
                          body: "\(Self.list(names)) \(names.count == 1 ? "is" : "are") open. Open App Depot to quit and update.")
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        await MainActor.run { self.onOpen?() }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner]
    }
}
