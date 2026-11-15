// Data/Pairing.swift — this Mac's device id and the upload token. The token lives in the Keychain
// (service `com.vuhpquang.appdepot.pairing`); DEBUG/snapshot runs keep it in their own defaults domain so a
// rebuilt dev binary never triggers a Keychain prompt.
import DepotCore
import Foundation
import Security

protocol TokenStore: AnyObject {
    var token: String? { get set }
}

final class KeychainTokenStore: TokenStore {
    private let service = "com.vuhpquang.appdepot.pairing"
    private let account = "device-token"

    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }

    var token: String? {
        get {
            var q = query
            q[kSecReturnData as String] = true
            q[kSecMatchLimit as String] = kSecMatchLimitOne
            var out: CFTypeRef?
            guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
            return String(data: data, encoding: .utf8)
        }
        set {
            SecItemDelete(query as CFDictionary)
            guard let newValue, !newValue.isEmpty else { return }
            var q = query
            q[kSecValueData as String] = Data(newValue.utf8)
            q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(q as CFDictionary, nil)
        }
    }
}

final class DefaultsTokenStore: TokenStore {
    private let defaults: UserDefaults
    init(defaults: UserDefaults) { self.defaults = defaults }
    var token: String? {
        get { defaults.string(forKey: "appdepot.pairing.token") }
        set { defaults.set(newValue, forKey: "appdepot.pairing.token") }
    }
}

final class MemoryTokenStore: TokenStore {
    var token: String?
}

@MainActor
@Observable
final class Pairing {
    typealias PairCall = @MainActor (_ code: String, _ deviceId: String) async -> PairResult

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let tokens: TokenStore
    @ObservationIgnored private let call: PairCall
    /// Mirrors whether a token is stored (observable for the Settings pane).
    private(set) var isPaired: Bool

    init(defaults: UserDefaults, tokens: TokenStore, call: @escaping PairCall) {
        self.defaults = defaults
        self.tokens = tokens
        self.call = call
        isPaired = tokens.token != nil
    }

    var deviceId: String {
        if let id = defaults.string(forKey: "appdepot.deviceId") { return id }
        let id = UUID().uuidString.lowercased()
        defaults.set(id, forKey: "appdepot.deviceId")
        return id
    }

    var token: String? { tokens.token }

    func pair(code: String) async -> PairResult {
        let r = await call(code.trimmingCharacters(in: .whitespacesAndNewlines), deviceId)
        if case let .paired(token) = r {
            tokens.token = token
            isPaired = true
        }
        return r
    }

    func clearToken() {
        tokens.token = nil
        isPaired = false
    }

    /// Picks up a token written elsewhere (tests).
    func reload() { isPaired = tokens.token != nil }
}
