// Data/DepotPaths.swift — where App Depot keeps files. Debug and snapshot runs use their own folders.
import Foundation

enum DepotPaths {
    static var supportFolderName: String {
        if ProcessInfo.processInfo.environment["APPDEPOT_SNAPSHOT"] != nil { return "com.vuhpquang.appdepot.snapshot" }
        #if DEBUG
        return "com.vuhpquang.appdepot.debug"
        #else
        return "com.vuhpquang.appdepot"
        #endif
    }

    static var support: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(supportFolderName, isDirectory: true)
    }

    static var catalogCache: URL { support.appendingPathComponent("catalog.json") }

    static var iconCache: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("com.vuhpquang.appdepot/icons", isDirectory: true)
    }
}
