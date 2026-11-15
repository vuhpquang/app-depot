import DepotCore
import Foundation
import Testing
@testable import AppDepot

@MainActor
@Suite struct HistoryStoreTests {
    let tmp: TempDir
    init() throws { tmp = try TempDir() }

    func store(historyCap: Int = 2000, outboxCap: Int = 1000) throws -> HistoryStore {
        try HistoryStore(path: tmp.url.appendingPathComponent("history.sqlite3"), historyCap: historyCap, outboxCap: outboxCap)
    }

    @Test func insertWritesHistoryAndOutbox() throws {
        let s = try store()
        s.record(DepotEvent(type: "install", appSlug: "padlet", toVersion: "2.5.0"))
        #expect(s.recent(limit: 10).map(\.type) == ["install"])
        #expect(s.outboxRows().count == 1)
        #expect(s.outboxRows()[0].1.contains("\"appSlug\":\"padlet\""))
    }

    @Test func historyIsCapped() throws {
        let s = try store(historyCap: 5)
        for i in 0..<12 { s.record(DepotEvent(type: "check", message: "\(i)")) }
        let rows = s.recent(limit: 100)
        #expect(rows.count == 5)
        #expect(rows.first?.message == "11")
        #expect(rows.last?.message == "7")
    }

    @Test func outboxIsTrimmedOldestFirst() throws {
        let s = try store(outboxCap: 3)
        for i in 0..<7 { s.record(DepotEvent(type: "open", appSlug: "a\(i)")) }
        let rows = s.outboxRows()
        #expect(rows.count == 3)
        #expect(rows.map(\.1).allSatisfy { $0.contains("a4") || $0.contains("a5") || $0.contains("a6") })
    }

    @Test func localOnlyEventsStayOutOfTheOutbox() throws {
        let s = try store()
        s.record(DepotEvent(type: "uninstall", appSlug: "padlet"))
        #expect(s.recent(limit: 10).count == 1)
        #expect(s.outboxRows().isEmpty)
    }

    @Test func deleteOutboxAndFilterBySlug() throws {
        let s = try store()
        s.record(DepotEvent(type: "install", appSlug: "a"))
        s.record(DepotEvent(type: "install", appSlug: "b"))
        s.deleteOutbox(s.outboxRows().map(\.0).prefix(1).map { $0 })
        #expect(s.outboxRows().count == 1)
        #expect(s.recent(limit: 10, appSlug: "b").map(\.appSlug) == ["b"])
    }

    @Test func repeatedIdenticalChecksCollapse() throws {
        let s = try store()
        s.recordCheck("1 update")
        s.record(DepotEvent(type: "depot_started"))
        s.recordCheck("1 update")
        s.recordCheck("2 updates")
        #expect(s.recent(limit: 10).filter { $0.type == "check" }.map(\.message) == ["2 updates", "1 update"])
    }

    @Test func survivesReopen() throws {
        do { try store().record(DepotEvent(type: "install", appSlug: "x")) }
        #expect(try store().recent(limit: 10).count == 1)
    }
}

@MainActor
@Suite struct PairingTests {
    @Test func pairStoresTokenAndDeviceIdIsStable() async {
        let defaults = UserDefaults(suiteName: "appdepot.tests.\(UUID())")!
        let tokens = MemoryTokenStore()
        var calls: [(String, String)] = []
        let p = Pairing(defaults: defaults, tokens: tokens) { code, device in calls.append((code, device)); return .paired(token: "tok") }
        let id = p.deviceId
        #expect(id == p.deviceId)
        #expect(!p.isPaired)
        #expect(await p.pair(code: " 123456 ") == .paired(token: "tok"))
        #expect(calls.first?.0 == "123456")
        #expect(calls.first?.1 == id)
        #expect(p.isPaired)
        p.clearToken()
        #expect(!p.isPaired)
    }

    @Test func badCodeStoresNothing() async {
        let p = Pairing(defaults: UserDefaults(suiteName: "appdepot.tests.\(UUID())")!, tokens: MemoryTokenStore()) { _, _ in .badCode }
        #expect(await p.pair(code: "000000") == .badCode)
        #expect(!p.isPaired)
    }
}

@MainActor
@Suite struct EventUploaderTests {
    @Test func unpairedSendsNothingAndPairedFlushesInBatches() async throws {
        let tmp = try TempDir()
        let s = try HistoryStore(path: tmp.url.appendingPathComponent("h.sqlite3"))
        for i in 0..<450 { s.record(DepotEvent(type: "check", message: "\(i)")) }
        let tokens = MemoryTokenStore()
        let pairing = Pairing(defaults: UserDefaults(suiteName: "appdepot.tests.\(UUID())")!, tokens: tokens) { _, _ in .badCode }
        var bodies: [String] = []
        let up = EventUploader(store: s, pairing: pairing) { _, body in bodies.append(body); return .accepted }
        await up.flush()
        #expect(bodies.isEmpty)
        tokens.token = "t"
        await up.flush()
        #expect(bodies.count == 3)
        #expect(s.outboxRows().isEmpty)
    }

    @Test func unauthorizedForgetsTheToken() async throws {
        let tmp = try TempDir()
        let s = try HistoryStore(path: tmp.url.appendingPathComponent("h.sqlite3"))
        s.record(DepotEvent(type: "check"))
        let tokens = MemoryTokenStore()
        tokens.token = "t"
        let pairing = Pairing(defaults: UserDefaults(suiteName: "appdepot.tests.\(UUID())")!, tokens: tokens) { _, _ in .badCode }
        let up = EventUploader(store: s, pairing: pairing) { _, _ in .unauthorized }
        await up.flush()
        #expect(!pairing.isPaired)
        #expect(s.outboxRows().count == 1, "kept for after re-pairing")
    }
}
