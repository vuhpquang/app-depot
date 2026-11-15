// swift-tools-version:6.0
// App Depot for macOS — installs and updates vuhpquang's apps from the vuhpquang.com catalog.
// `swift build` / `swift test`; ./build.sh makes the signed .app and the release zip.
import PackageDescription

let package = Package(
    name: "AppDepot",
    platforms: [.macOS("26.0")],
    dependencies: [
        // Shared updater / About / Settings shell, a git submodule (git submodule update --init).
        .package(path: "Vendor/app-shell"),
    ],
    targets: [
        // Pure rules (catalog, plan, queue, events, background policy): Foundation only, no AppKit.
        .target(name: "DepotCore", path: "Sources/DepotCore"),
        .executableTarget(
            name: "AppDepot",
            dependencies: [
                "DepotCore",
                .product(name: "AppShellCore", package: "app-shell"),
                .product(name: "UpdaterKit", package: "app-shell"),
                .product(name: "AboutKit", package: "app-shell"),
                .product(name: "SettingsKit", package: "app-shell"),
            ],
            path: "Sources/AppDepot",
            // Info.plist is copied into the .app by build.sh, not SwiftPM (no resource bundles).
            exclude: ["Resources"],
            linkerSettings: [.linkedLibrary("sqlite3")]
        ),
        .testTarget(name: "DepotCoreTests", dependencies: ["DepotCore"], path: "Tests/DepotCoreTests"),
        .testTarget(name: "AppDepotTests", dependencies: ["AppDepot", "DepotCore"], path: "Tests/AppDepotTests"),
    ],
    swiftLanguageModes: [.v6]
)
