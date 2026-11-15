// DesignSystem/Theme.swift — App Depot's one source of design tokens (same accent as Android's Theme.kt).
import AppKit
import SwiftUI

enum Theme {
    /// Depot green: #2E7D5B in light, #6FCF9F in dark.
    static let accent = Color(nsColor: NSColor(name: "depotGreen") { appearance in
        appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
            ? NSColor(srgbRed: 0x6F / 255, green: 0xCF / 255, blue: 0x9F / 255, alpha: 1)
            : NSColor(srgbRed: 0x2E / 255, green: 0x7D / 255, blue: 0x5B / 255, alpha: 1)
    })

    enum Spacing {
        static let xxs: CGFloat = 2
        static let xs: CGFloat = 4
        static let s: CGFloat = 8
        static let m: CGFloat = 12
        static let l: CGFloat = 16
        static let xl: CGFloat = 24
        static let xxl: CGFloat = 32
    }

    enum Radius {
        static let chip: CGFloat = 6
        static let icon: CGFloat = 12
        static let card: CGFloat = 16
    }

    enum Size {
        static let rowIcon: CGFloat = 40
        static let gridIcon: CGFloat = 64
        static let detailIcon: CGFloat = 80
        static let menuIcon: CGFloat = 18
        static let gridCardWidth: CGFloat = 176
        static let gridCardHeight: CGFloat = 196
        static let sidebarMin: CGFloat = 320
        static let windowMinWidth: CGFloat = 760
        static let windowMinHeight: CGFloat = 520
        static let menuWidth: CGFloat = 320
    }

    enum Duration {
        static let quick: Double = 0.15
    }

    /// Fills that resolve per appearance (never a fixed white/grey in a view).
    enum Fill {
        static let card = Color(nsColor: .controlBackgroundColor)
        static let chip = Color.secondary.opacity(0.14)
        static let separator = Color(nsColor: .separatorColor)
    }
}
