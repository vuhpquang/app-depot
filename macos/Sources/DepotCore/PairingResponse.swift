import Foundation

public enum PairResult: Equatable, Sendable {
    case paired(token: String)
    case badCode
    case failed(String)
}

/// `POST /api/depot/devices` reply → result. 201 carries `data.token`; 403 = wrong or used code.
public func parsePairResponse(status: Int, body: String) -> PairResult {
    switch status {
    case 201:
        guard let obj = try? JSONSerialization.jsonObject(with: Data(body.utf8)) as? [String: Any] else {
            return .failed("Pairing reply was not understood")
        }
        guard let token = (obj["data"] as? [String: Any])?["token"] as? String, !token.isEmpty else {
            return .failed("Pairing reply had no token")
        }
        return .paired(token: token)
    case 403:
        return .badCode
    default:
        return .failed("Pairing failed (HTTP \(status))")
    }
}
