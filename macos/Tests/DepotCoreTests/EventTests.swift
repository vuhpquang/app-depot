import DepotCore
import Foundation
import Testing

@Suite struct EventBatchTests {
    @Test func split450() {
        let rows = (1...450).map { (Int64($0), "{}") }
        #expect(batchesOf(rows).map(\.count) == [200, 200, 50])
        #expect(batchesOf(rows).first?.first?.0 == 1)
    }
    @Test func emptyGivesNoBatches() { #expect(batchesOf([]).isEmpty) }
    @Test func bodyWrapsEventsArray() {
        #expect(eventsBody([#"{"type":"a"}"#, #"{"type":"b"}"#]) == #"{"events":[{"type":"a"},{"type":"b"}]}"#)
    }
    @Test func trim() {
        #expect(trimOutbox(1005) == 5)
        #expect(trimOutbox(1000) == 0)
        #expect(trimOutbox(3) == 0)
    }
    @Test func wireJsonOmitsNullsAndUsesIsoUtc() {
        let at = ISO8601DateFormatter().date(from: "2026-11-15T10:00:00Z")!
        let json = DepotEvent(type: "check", message: "2 updates", at: at).wireJSON()
        #expect(json == #"{"type":"check","message":"2 updates","at":"2026-11-15T10:00:00Z"}"#)
        #expect(!json.contains("null"))
    }
    @Test func wireJsonKeepsFieldOrderAndEscapes() {
        let at = ISO8601DateFormatter().date(from: "2026-11-15T10:00:00Z")!.addingTimeInterval(0.25)
        let json = DepotEvent(type: "update", appSlug: "padlet", fromVersion: "2.4.0", toVersion: "2.5.0",
                              message: "a \"q\"\n", at: at).wireJSON()
        #expect(json == #"{"type":"update","appSlug":"padlet","fromVersion":"2.4.0","toVersion":"2.5.0","message":"a \"q\"\n","at":"2026-11-15T10:00:00.250Z"}"#)
    }
    @Test func wireJsonTruncatesLongMessageTo2000() throws {
        let json = DepotEvent(type: "install_failed", appSlug: "x", message: String(repeating: "a", count: 3000)).wireJSON()
        let obj = try JSONSerialization.jsonObject(with: Data(json.utf8)) as! [String: Any]
        #expect((obj["message"] as! String).count == 2000)
    }
    @Test func eventTypesAreTheServerList() {
        #expect(DepotEvent.serverTypes == ["check", "install", "update", "install_failed", "open", "crash", "depot_started", "self_update"])
    }
    @Test func uninstallStaysOnTheDevice() {
        #expect(!isUploaded("uninstall"))
        #expect(isUploaded("install") && isUploaded("update") && isUploaded("open"))
    }
}

@Suite struct PairingResponseTests {
    @Test func created201GivesToken() {
        #expect(parsePairResponse(status: 201, body: #"{"data":{"deviceId":"d","token":"t"},"error":null,"status":201}"#) == .paired(token: "t"))
    }
    @Test func forbidden403IsBadCode() {
        #expect(parsePairResponse(status: 403, body: #"{"data":null,"error":"bad","status":403}"#) == .badCode)
    }
    @Test func failures() {
        if case .failed = parsePairResponse(status: 500, body: "oops") {} else { Issue.record("500") }
        if case .failed = parsePairResponse(status: 201, body: "nope") {} else { Issue.record("garbage") }
        if case .failed = parsePairResponse(status: 400, body: "{}") {} else { Issue.record("400") }
        if case .failed = parsePairResponse(status: 201, body: #"{"data":{"token":""}}"#) {} else { Issue.record("empty token") }
    }
    @Test func eventsStatusMapping() {
        #expect(parseEventsStatus(201) == .accepted)
        #expect(parseEventsStatus(401) == .unauthorized)
        #expect(parseEventsStatus(400) == .badBatch)
        #expect(parseEventsStatus(503) == .retry)
    }
}

@Suite struct ServerUrlTests {
    @Test func keepsHttpAndHttpsAndDropsTrailingSlash() {
        #expect(normalizeServerUrl(" https://vuhpquang.com/ ") == "https://vuhpquang.com")
        #expect(normalizeServerUrl("http://localhost:3000") == "http://localhost:3000")
    }
    @Test func rejectsEmptyAndNonHttp() {
        for s in ["", "vuhpquang.com", "ftp://x", "https://", "https://a b"] { #expect(normalizeServerUrl(s) == nil) }
    }
}

@Suite struct VerifyTests {
    private let abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private func file(_ text: String) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("verify-\(UUID().uuidString)")
        try text.write(to: url, atomically: true, encoding: .utf8)
        return url
    }
    @Test func sha256OfAbc() throws { #expect(try sha256Hex(of: try file("abc")) == abc) }
    @Test func okWhenSizeAndHashMatch() throws { #expect(verifyDownload(try file("abc"), expectedSize: 3, expectedSha256: abc) == .ok) }
    @Test func hashIsCaseInsensitive() throws { #expect(verifyDownload(try file("abc"), expectedSize: 3, expectedSha256: abc.uppercased()) == .ok) }
    @Test func wrongSize() throws { #expect(verifyDownload(try file("abc"), expectedSize: 4, expectedSha256: abc) == .wrongSize) }
    @Test func wrongHash() throws { #expect(verifyDownload(try file("abd"), expectedSize: 3, expectedSha256: abc) == .wrongHash) }
    @Test func missingFileIsWrongSize() {
        #expect(verifyDownload(URL(fileURLWithPath: "/nonexistent-\(UUID())"), expectedSize: 3, expectedSha256: abc) == .wrongSize)
    }
}
