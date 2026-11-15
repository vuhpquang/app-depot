#!/usr/bin/env swift
// macOS app icon for App Depot. The drawing itself lives in ../assets/make-icon.swift (shared with the
// catalog/website icon and the Android launcher icon); this script renders it and packs AppIcon.icns.
// Usage (from macos/): swift scripts/make-icon.swift build/AppIcon.icns
import Foundation

let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "build/AppIcon.icns"
let fm = FileManager.default
let here = URL(fileURLWithPath: #filePath).deletingLastPathComponent()          // macos/scripts
let shared = here.appendingPathComponent("../../assets/make-icon.swift").standardizedFileURL
let work = fm.temporaryDirectory.appendingPathComponent("appdepot-icon-\(UUID().uuidString)")
let iconset = work.appendingPathComponent("AppIcon.iconset")
try fm.createDirectory(at: iconset, withIntermediateDirectories: true)
defer { try? fm.removeItem(at: work) }

@discardableResult
func run(_ tool: String, _ args: [String]) -> Int32 {
    let p = Process()
    p.executableURL = URL(fileURLWithPath: tool)
    p.arguments = args
    p.standardOutput = FileHandle.nullDevice
    try! p.run()
    p.waitUntilExit()
    return p.terminationStatus
}

let master = work.appendingPathComponent("AppIcon-1024.png")
guard run("/usr/bin/swift", [shared.path, master.path]) == 0 else { fatalError("rendering \(shared.path) failed") }
for s in [16, 32, 128, 256, 512] {
    run("/usr/bin/sips", ["-z", "\(s)", "\(s)", master.path, "--out", iconset.appendingPathComponent("icon_\(s)x\(s).png").path])
    run("/usr/bin/sips", ["-z", "\(s * 2)", "\(s * 2)", master.path, "--out", iconset.appendingPathComponent("icon_\(s)x\(s)@2x.png").path])
}
try fm.createDirectory(at: URL(fileURLWithPath: out).deletingLastPathComponent(), withIntermediateDirectories: true)
guard run("/usr/bin/iconutil", ["-c", "icns", iconset.path, "-o", out]) == 0 else { fatalError("iconutil failed") }
print(out)
