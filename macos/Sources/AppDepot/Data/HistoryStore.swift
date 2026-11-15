// Data/HistoryStore.swift — local history of every check / install (capped at 2000 rows) and the upload
// outbox (capped at 1000, oldest dropped), in one SQLite file. Writing a history row and its outbox row is
// one transaction; local-only types (uninstall) never reach the outbox.
import DepotCore
import Foundation
import OSLog
import SQLite3

struct HistoryRow: Identifiable, Equatable, Sendable {
    let id: Int64
    let type: String
    let appSlug: String?
    let fromVersion: String?
    let toVersion: String?
    let message: String?
    let at: Date
}

@MainActor
final class HistoryStore: EventSink {
    private var db: OpaquePointer?
    private let historyCap: Int
    private let outboxCap: Int
    private let log = Logger(subsystem: "com.vuhpquang.appdepot", category: "history")
    /// Called after each recorded event (the app kicks an upload).
    var onRecorded: (() -> Void)?
    /// Bumped on every write so views can refresh.
    private(set) var revision = 0

    static var defaultPath: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(DepotPaths.supportFolderName, isDirectory: true)
        return dir.appendingPathComponent("history.sqlite3")
    }

    init(path: URL, historyCap: Int = 2000, outboxCap: Int = 1000) throws {
        self.historyCap = historyCap
        self.outboxCap = outboxCap
        try FileManager.default.createDirectory(at: path.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard sqlite3_open(path.path, &db) == SQLITE_OK else { throw CatalogError("Couldn't open the history file") }
        exec("""
        CREATE TABLE IF NOT EXISTS history(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, app_slug TEXT,
          from_version TEXT, to_version TEXT, message TEXT, at REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS outbox(id INTEGER PRIMARY KEY AUTOINCREMENT, json TEXT NOT NULL, created_at REAL NOT NULL);
        """)
    }

    isolated deinit { sqlite3_close(db) }

    @discardableResult
    private func exec(_ sql: String) -> Bool {
        var err: UnsafeMutablePointer<CChar>?
        if sqlite3_exec(db, sql, nil, nil, &err) != SQLITE_OK {
            log.error("sqlite: \(String(cString: err!), privacy: .public)")
            sqlite3_free(err)
            return false
        }
        return true
    }

    private let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private func run(_ sql: String, _ args: [Any?]) -> Bool {
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return false }
        defer { sqlite3_finalize(stmt) }
        bind(stmt, args)
        return sqlite3_step(stmt) == SQLITE_DONE
    }

    private func bind(_ stmt: OpaquePointer?, _ args: [Any?]) {
        for (i, a) in args.enumerated() {
            let idx = Int32(i + 1)
            switch a {
            case let s as String: sqlite3_bind_text(stmt, idx, s, -1, transient)
            case let d as Double: sqlite3_bind_double(stmt, idx, d)
            case let n as Int64: sqlite3_bind_int64(stmt, idx, n)
            case let n as Int: sqlite3_bind_int64(stmt, idx, Int64(n))
            default: sqlite3_bind_null(stmt, idx)
            }
        }
    }

    private func count(_ table: String) -> Int {
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM \(table)", -1, &stmt, nil) == SQLITE_OK else { return 0 }
        defer { sqlite3_finalize(stmt) }
        return sqlite3_step(stmt) == SQLITE_ROW ? Int(sqlite3_column_int64(stmt, 0)) : 0
    }

    func record(_ e: DepotEvent) {
        exec("BEGIN")
        let at = e.at.timeIntervalSince1970
        var ok = run("INSERT INTO history(type, app_slug, from_version, to_version, message, at) VALUES(?,?,?,?,?,?)",
                     [e.type, e.appSlug, e.fromVersion, e.toVersion, e.message, at])
        if ok && isUploaded(e.type) {
            ok = run("INSERT INTO outbox(json, created_at) VALUES(?,?)", [e.wireJSON(), at])
        }
        if ok {
            exec("DELETE FROM history WHERE id <= (SELECT MAX(id) FROM history) - \(historyCap)")
            let drop = trimOutbox(count("outbox"), cap: outboxCap)
            if drop > 0 { exec("DELETE FROM outbox WHERE id IN (SELECT id FROM outbox ORDER BY id LIMIT \(drop))") }
            exec("COMMIT")
            revision += 1
            onRecorded?()
        } else {
            exec("ROLLBACK")
            log.error("could not record \(e.type, privacy: .public)")
        }
    }

    /// A `check` row only when it says something new; `depot_started` rows don't count as "in between".
    func recordCheck(_ message: String) {
        let last = recent(limit: 20).first { $0.type != "depot_started" }
        if let last, last.type == "check", last.message == message { return }
        record(DepotEvent(type: "check", message: message))
    }

    func outboxRows() -> [(Int64, String)] {
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, "SELECT id, json FROM outbox ORDER BY id", -1, &stmt, nil) == SQLITE_OK else { return [] }
        defer { sqlite3_finalize(stmt) }
        var rows: [(Int64, String)] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            rows.append((sqlite3_column_int64(stmt, 0), String(cString: sqlite3_column_text(stmt, 1))))
        }
        return rows
    }

    func deleteOutbox(_ ids: [Int64]) {
        guard !ids.isEmpty else { return }
        exec("DELETE FROM outbox WHERE id IN (\(ids.map(String.init).joined(separator: ",")))")
    }

    func recent(limit: Int, appSlug: String? = nil) -> [HistoryRow] {
        let sql = "SELECT id, type, app_slug, from_version, to_version, message, at FROM history"
            + (appSlug != nil ? " WHERE app_slug = ?" : "") + " ORDER BY id DESC LIMIT \(limit)"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }
        defer { sqlite3_finalize(stmt) }
        if let appSlug { bind(stmt, [appSlug]) }
        func text(_ i: Int32) -> String? { sqlite3_column_text(stmt, i).map { String(cString: $0) } }
        var rows: [HistoryRow] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            rows.append(HistoryRow(id: sqlite3_column_int64(stmt, 0), type: text(1) ?? "", appSlug: text(2), fromVersion: text(3),
                                   toVersion: text(4), message: text(5), at: Date(timeIntervalSince1970: sqlite3_column_double(stmt, 6))))
        }
        return rows
    }
}
