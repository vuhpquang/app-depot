// App/DebugCLI.swift — DEBUG builds only: `APPDEPOT_INSTALL_ROOT=/tmp/root AppDepot --install-slug padlet`
// installs the real catalog release into that folder (fully verified) and exits. Refuses without a root,
// so a dev run can never touch /Applications. Matching running apps is by exact path, so the owner's
// running copies are never asked to quit.
#if DEBUG
import AppKit
import DepotCore
import Foundation
import UpdaterKit

@MainActor
enum DebugCLI {
    static var slug: String? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--install-slug"), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    static func run(_ slug: String) async -> Int32 {
        let location = InstallLocation.fromEnvironment
        guard location.root != nil else {
            print("refusing: set APPDEPOT_INSTALL_ROOT to a temp folder")
            return 2
        }
        do {
            let (_, apps) = try await DepotAPI(version: "debug").fetchCatalog()
            guard let app = apps.first(where: { $0.slug == slug }) else { print("no app \(slug) in the catalog"); return 1 }
            let installer = MacInstaller(location: location, downloader: URLSessionDownloader(userAgent: "AppDepot-macOS/debug"),
                                         control: WorkspaceAppProcessControl.live)
            let outcome = await installer.install(app, interactive: false) { step in print("  \(step)") }
            print("\(slug): \(outcome) → \(location.target(for: app).path)")
            if case .success = outcome { return 0 }
            return 1
        } catch {
            print("catalog failed: \(error.localizedDescription)")
            return 1
        }
    }
}
#endif
