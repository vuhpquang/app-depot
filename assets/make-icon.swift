#!/usr/bin/env swift
// App Depot icon: a crate on depot green. Writes a 1024 px PNG (default assets/AppIcon-1024.png).
// Source of truth for the catalog / website icon and the macOS app icon; the Android launcher icon is
// the same drawing as a vector drawable (android/app/src/main/res/drawable/ic_launcher_foreground.xml).
// Usage: swift assets/make-icon.swift [output.png]
import AppKit

let size: CGFloat = 1024
let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "assets/AppIcon-1024.png"

func color(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}

let cs = CGColorSpace(name: CGColorSpace.sRGB)!
let ctx = CGContext(data: nil, width: Int(size), height: Int(size), bitsPerComponent: 8, bytesPerRow: 0,
                    space: cs, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!

// macOS-style rounded square with a margin (Big Sur grid: 824 px tile on 1024 canvas).
let inset: CGFloat = 100
let tile = CGRect(x: inset, y: inset, width: size - 2 * inset, height: size - 2 * inset)
let tilePath = CGPath(roundedRect: tile, cornerWidth: 185, cornerHeight: 185, transform: nil)

ctx.saveGState()
ctx.setShadow(offset: CGSize(width: 0, height: -10), blur: 28, color: color(0x000000, 0.28))
ctx.addPath(tilePath); ctx.setFillColor(color(0x2E7D5B)); ctx.fillPath()
ctx.restoreGState()

ctx.saveGState()
ctx.addPath(tilePath); ctx.clip()
let grad = CGGradient(colorsSpace: cs, colors: [color(0x3FA37A), color(0x23674A)] as CFArray, locations: [0, 1])!
ctx.drawLinearGradient(grad, start: CGPoint(x: size / 2, y: tile.maxY), end: CGPoint(x: size / 2, y: tile.minY), options: [])
ctx.restoreGState()

// Crate: body + lid, cream on green, with slats and a handle cut-out.
let cream = color(0xF4EFE3)
let shade = color(0xD9CFB8)
let ink = color(0x1D5A40)

let body = CGRect(x: 262, y: 270, width: 500, height: 330)
ctx.addPath(CGPath(roundedRect: body, cornerWidth: 34, cornerHeight: 34, transform: nil))
ctx.setFillColor(cream); ctx.fillPath()

// Slats (two horizontal grooves).
ctx.setFillColor(shade)
for y in [372.0, 474.0] {
    ctx.addPath(CGPath(roundedRect: CGRect(x: body.minX + 34, y: y, width: body.width - 68, height: 16),
                       cornerWidth: 8, cornerHeight: 8, transform: nil))
    ctx.fillPath()
}

// Lid, slightly wider, sitting on top.
let lid = CGRect(x: 232, y: 612, width: 560, height: 120)
ctx.addPath(CGPath(roundedRect: lid, cornerWidth: 34, cornerHeight: 34, transform: nil))
ctx.setFillColor(cream); ctx.fillPath()

// Handle slot in the lid.
ctx.addPath(CGPath(roundedRect: CGRect(x: 432, y: 656, width: 160, height: 32), cornerWidth: 16, cornerHeight: 16, transform: nil))
ctx.setFillColor(ink); ctx.fillPath()

// Down arrow on the body = "get / install".
ctx.setFillColor(ink)
let arrow = CGMutablePath()
arrow.addRoundedRect(in: CGRect(x: 494, y: 400, width: 36, height: 130), cornerWidth: 18, cornerHeight: 18)
ctx.addPath(arrow); ctx.fillPath()
let head = CGMutablePath()
head.move(to: CGPoint(x: 430, y: 420)); head.addLine(to: CGPoint(x: 594, y: 420)); head.addLine(to: CGPoint(x: 512, y: 330)); head.closeSubpath()
ctx.addPath(head); ctx.fillPath()

let image = ctx.makeImage()!
let rep = NSBitmapImageRep(cgImage: image)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
print("wrote \(out)")
