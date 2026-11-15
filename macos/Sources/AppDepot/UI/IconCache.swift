// UI/IconCache.swift — app icons from the catalog `iconUrl`, cached on disk
// (`Caches/com.vuhpquang.appdepot/icons/<sha256(url)>.png`, refetched after 7 days). A letter tile shows
// while loading or when there is no icon.
import AppKit
import DepotCore
import Observation
import SwiftUI

@MainActor
@Observable
final class IconCache {
    private(set) var images: [String: NSImage] = [:]
    @ObservationIgnored private var loading: Set<String> = []
    @ObservationIgnored let dir: URL
    @ObservationIgnored let maxAge: TimeInterval = 7 * 24 * 3600

    init(dir: URL = DepotPaths.iconCache) { self.dir = dir }

    func image(for url: String?) -> NSImage? {
        guard let url else { return nil }
        if let img = images[url] { return img }
        load(url)
        return nil
    }

    func load(_ url: String) {
        guard !loading.contains(url), images[url] == nil else { return }
        loading.insert(url)
        let file = dir.appendingPathComponent(iconFileName(url))
        let attrs = try? FileManager.default.attributesOfItem(atPath: file.path)
        let fresh = ((attrs?[.modificationDate] as? Date).map { Date().timeIntervalSince($0) < maxAge }) ?? false
        if let img = NSImage(contentsOf: file) {
            images[url] = img
            if fresh { loading.remove(url); return }
        }
        Task {
            defer { loading.remove(url) }
            guard let remote = URL(string: url), remote.scheme == "https" else { return }
            guard let (data, response) = try? await URLSession.shared.data(from: remote),
                  (response as? HTTPURLResponse)?.statusCode == 200, data.count < 2_000_000,
                  let img = NSImage(data: data) else { return }
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try? data.write(to: file, options: .atomic)
            images[url] = img
        }
    }

    /// Waits until the given icons are loaded (snapshot runs), at most `timeout`.
    func preload(_ urls: [String], timeout: Duration = .seconds(8)) async {
        urls.forEach(load)
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline, urls.contains(where: { images[$0] == nil }) {
            try? await Task.sleep(for: .milliseconds(100))
        }
    }
}

/// The app's icon, or a letter tile in the accent colour.
struct AppIconView: View {
    let app: CatalogApp
    let size: CGFloat
    @Environment(IconCache.self) private var icons

    var body: some View {
        Group {
            if let img = icons.image(for: app.iconUrl) {
                Image(nsImage: img).resizable().interpolation(.high).aspectRatio(contentMode: .fit)
            } else {
                RoundedRectangle(cornerRadius: size * 0.225, style: .continuous)
                    .fill(Theme.accent.gradient)
                    .overlay {
                        Text(String(app.name.prefix(1)).uppercased())
                            .font(.system(size: size * 0.48, weight: .semibold, design: .rounded))
                            .foregroundStyle(.white)
                    }
                    .padding(size * 0.08)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
