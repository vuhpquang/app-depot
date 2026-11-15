// UI/SendToPhoneSheet.swift — "Send to phone" for an Android-only app: a QR code of its Android download
// link (scan with the phone's camera), the app's name, and Copy link. Esc, ⌘W and Done close it.
// The code is drawn black on a white tile in both appearances, so it scans in dark mode too.
import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins
import DepotCore
import SwiftUI

enum QRCode {
    /// One pixel per module (plus the generator's quiet zone); scale it up with `.interpolation(.none)`.
    static func image(for text: String) -> CGImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        return CIContext(options: [.useSoftwareRenderer: true]).createCGImage(output, from: output.extent)
    }
}

struct SendToPhoneSheet: View {
    let product: Product
    @Environment(\.dismiss) private var dismiss
    @State private var copied = false

    private var url: String? {
        for case let .sendToPhone(url) in remoteActions(product, local: macPlatform) { return url }
        return nil
    }

    var body: some View {
        VStack(spacing: Theme.Spacing.l) {
            HStack(spacing: Theme.Spacing.m) {
                AppIconView(app: product.display, size: Theme.Size.rowIcon)
                VStack(alignment: .leading, spacing: Theme.Spacing.xxs) {
                    Text(product.display.name).font(.headline)
                    Text(versionText).font(.callout).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            if let url, let qr = QRCode.image(for: url) {
                Image(decorative: qr, scale: 1)
                    .interpolation(.none)
                    .resizable()
                    .frame(width: Theme.Size.qrCode, height: Theme.Size.qrCode)
                    .padding(Theme.Spacing.l)
                    .background(Theme.Fill.qrTile, in: RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous).strokeBorder(Theme.Fill.separator))
                    .accessibilityLabel("QR code of the download link for \(product.display.name)")
                Text("Scan with your phone's camera")
                    .font(.body)
                Text(verbatim: url)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .truncationMode(.middle)
                    .multilineTextAlignment(.center)
                    .textSelection(.enabled)
            } else {
                Text("There is no Android download for \(product.display.name) yet.")
                    .foregroundStyle(.secondary)
            }
            HStack {
                if let url {
                    Button(copied ? "Copied" : "Copy link") {
                        NSPasteboard.general.clearContents()
                        NSPasteboard.general.setString(url, forType: .string)
                        copied = true
                    }
                }
                Spacer()
                Button("Done") { dismiss() }.keyboardShortcut(.defaultAction)
            }
        }
        .padding(Theme.Spacing.xl)
        .frame(width: Theme.Size.sheetWidth)
        .background {
            Button("") { dismiss() }.keyboardShortcut(.cancelAction).hidden()
            Button("") { dismiss() }.keyboardShortcut("w").hidden()
        }
    }

    private var versionText: String {
        guard let v = product.variants["android"]?.latest?.version else { return "Android" }
        return "Android · \(v)"
    }
}
