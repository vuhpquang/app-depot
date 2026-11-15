// Data/EventUploader.swift — sends the outbox to vuhpquang.com in batches of 200 once paired.
// Safe to call often: nothing happens unpaired, offline rows wait, a 401 forgets the token.
import DepotCore
import Foundation
import OSLog

@MainActor
final class EventUploader {
    typealias Post = @MainActor (_ token: String, _ body: String) async -> EventsOutcome

    private let store: HistoryStore
    private let pairing: Pairing
    private let post: Post
    private var running = false
    private let log = Logger(subsystem: "com.vuhpquang.appdepot", category: "events")

    init(store: HistoryStore, pairing: Pairing, post: @escaping Post) {
        self.store = store
        self.pairing = pairing
        self.post = post
    }

    func flush() async {
        guard !running else { return }
        running = true
        defer { running = false }
        pairing.reload()
        guard let token = pairing.token else { return }
        for batch in batchesOf(store.outboxRows()) {
            switch await post(token, eventsBody(batch.map(\.1))) {
            case .accepted:
                store.deleteOutbox(batch.map(\.0))
            case .badBatch:
                log.warning("server rejected a batch of \(batch.count) events; dropping it")
                store.deleteOutbox(batch.map(\.0))
            case .unauthorized:
                pairing.clearToken()
                return
            case .retry:
                return
            }
        }
    }
}
