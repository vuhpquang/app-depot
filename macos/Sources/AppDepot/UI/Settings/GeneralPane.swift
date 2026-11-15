// UI/Settings/GeneralPane.swift — Settings → General: launch at login and the connection to vuhpquang.com
// (pairing code → device token; App Depot works fully without it, events wait in the outbox).
import DepotCore
import SettingsKit
import SwiftUI

struct GeneralPane: View {
    @Environment(Pairing.self) private var pairing
    let onPaired: () -> Void
    @State private var code = ""
    @State private var working = false
    @State private var message: String?

    var body: some View {
        Form {
            Section {
                LaunchAtLoginRow(appName: "App Depot")
            } footer: {
                Text("App Depot checks for updates in the background while it's open, from the menu bar.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            Section("Connection") {
                if pairing.isPaired {
                    LabeledContent("vuhpquang.com") {
                        Label("Connected", systemImage: "checkmark.circle.fill").foregroundStyle(Theme.accent)
                    }
                    Button("Disconnect") {
                        pairing.clearToken()
                        message = nil
                    }
                } else {
                    TextField("Pairing code", text: $code, prompt: Text("From vuhpquang.com/admin/depot"))
                        .onSubmit(connect)
                    HStack {
                        if let message { Text(message).font(.callout).foregroundStyle(.secondary) }
                        Spacer()
                        if working { ProgressView().controlSize(.small) }
                        Button("Connect", action: connect)
                            .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty || working)
                    }
                }
            }
            Section {
                LabeledContent("This Mac", value: Host.current().localizedName ?? "Mac")
                LabeledContent("Device ID") { Text(pairing.deviceId).font(.caption.monospaced()).textSelection(.enabled) }
            } footer: {
                Text("Once connected, installs and update checks are sent to vuhpquang.com so they show up on the admin page. Without a connection they stay on this Mac.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
    }

    private func connect() {
        let c = code
        guard !c.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        working = true
        message = nil
        Task {
            let r = await pairing.pair(code: c)
            working = false
            switch r {
            case .paired:
                code = ""
                onPaired()
            case .badCode:
                message = "That code didn't work. Make a new one on the admin page and try again."
            case let .failed(m):
                message = m
            }
        }
    }
}
