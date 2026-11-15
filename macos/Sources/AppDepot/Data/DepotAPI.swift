// Data/DepotAPI.swift — vuhpquang.com: the catalog (public), device pairing and event upload.
import DepotCore
import Foundation

struct DepotAPI: Sendable {
    var baseURL: URL = DepotEndpoints.base
    var version: String

    private var userAgent: String { "AppDepot-macOS/\(version)" }

    var catalogURL: URL {
        // Every platform: the Mac filters on its own (Android-only apps show as "Not for Mac").
        baseURL.appendingPathComponent("api/depot/catalog")
    }

    /// The raw catalog JSON (cached by the caller) and the parsed apps.
    func fetchCatalog() async throws -> (Data, [CatalogApp]) {
        var request = URLRequest(url: catalogURL)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 20
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return (data, try CatalogParser.parse(data))
    }

    private func post(_ path: String, json: Data, bearer: String?) async throws -> (Int, String) {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let bearer { request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
        request.httpBody = json
        let (data, response) = try await URLSession.shared.data(for: request)
        return ((response as? HTTPURLResponse)?.statusCode ?? 0, String(decoding: data, as: UTF8.self))
    }

    func pair(code: String, deviceId: String, name: String) async -> PairResult {
        let body: [String: String] = ["code": code, "deviceId": deviceId, "name": name, "platform": "macos", "depotVersion": version]
        do {
            let (status, text) = try await post("api/depot/devices", json: try JSONEncoder().encode(body), bearer: nil)
            return parsePairResponse(status: status, body: text)
        } catch {
            return .failed("Couldn't reach vuhpquang.com. Check your connection and try again.")
        }
    }

    /// Network errors count as `.retry`.
    func postEvents(token: String, body: String) async -> EventsOutcome {
        do {
            return parseEventsStatus(try await post("api/depot/events", json: Data(body.utf8), bearer: token).0)
        } catch {
            return .retry
        }
    }
}
