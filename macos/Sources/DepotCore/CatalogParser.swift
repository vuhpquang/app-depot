import Foundation

public struct CatalogError: LocalizedError, Equatable {
    public let errorDescription: String?
    public init(_ message: String) { errorDescription = message }
}

/// Parses the `/api/depot/catalog` envelope (`data.apps`).
public enum CatalogParser {
    private struct Envelope: Decodable {
        struct Payload: Decodable { let apps: [CatalogApp]? }
        let data: Payload?
    }

    public static func parse(_ data: Data) throws -> [CatalogApp] {
        let envelope = try JSONDecoder().decode(Envelope.self, from: data)
        guard let payload = envelope.data else { throw CatalogError("Catalog response has no data") }
        return payload.apps ?? []
    }
}
