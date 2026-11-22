---
name: App Depot
platform: macos+android
appearance: light+dark
tokens: macos/Sources/AppDepot/DesignSystem/Theme.swift; android/app/src/main/kotlin/com/vuhpquang/appdepot/ui/theme/Theme.kt
design_doc: docs/superpowers/specs/2026-11-15-app-depot-design.md
dials:
  variance: 2              # a stock sidebar/detail Mac app and a stock Material 3 Android app
  motion: 1                # progress bars and system transitions only
  density: 5               # list rows with icon, chip and version line; a roomier grid layout
fonts: system
accent: "#2E7D5B / #6FCF9F"
---

# App Depot design

## 1. The look

App Depot is the owner's own small app store, and it looks like a **plain, trustworthy utility**: a
standard Mac window with a sidebar of apps (list or grid) and a detail pane, a native toolbar (Update
all, layout, sort, history, refresh), and a menu bar extra that is an ordinary menu. Each app shows
its **own icon**, name, a **state chip** (Update available, Up to date, Skipped, Updates itself, No
release yet, Not for Mac) and a version line; the detail shows the release notes as "What's New".
The only colour of its own is **depot green** (`#2E7D5B` light / `#6FCF9F` dark): the accent, the
"Update available" chip and the grid selection ring. Android is the same app in Material 3, green
primary, dynamic colour off.

Load-bearing ideas:

1. **The apps are the colour.** App icons carry the visual interest; App Depot's chrome stays native
   and neutral.
2. **State is a word, not just a colour**: every card has a chip with text (`DepotCore/CardModel.swift`,
   Android `ui/HomeState.kt`), tested as pure mappings.
3. **Fills resolve per appearance** (`Theme.Fill`), never a fixed white/grey in a view — except the QR
   tile, on purpose.
4. **Native everywhere**: system toolbar, `NavigationSplitView`, system menu, Material 3 components.

## 2. Identity and references

- **Mac App Store / Google Play**: the idea of a catalog with Install/Update/Open and release notes.
  App Depot keeps its own name, green, icon (`assets/make-icon.swift`: a green depot box) and copy;
  nothing from either store's look.
- Family surfaces on the Mac (About, Updates, What's New, Settings shell) come from app-shell; the
  Android About follows `.claude/rules/app-shell.md` by hand.

## 3. Tokens

| Role | Value | Where |
|---|---|---|
| Accent | depot green `#2E7D5B` light / `#6FCF9F` dark (dynamic `NSColor`) | `Theme.accent`; Android `GreenLight/GreenDark` |
| Android scheme | primaryContainer `#D3EEDF`, secondary `#4D6357`, tertiary `#8A5A00`, … | `ui/theme/Theme.kt` |
| Spacing | xxs 2 · xs 4 · s 8 · m 12 · l 16 · xl 24 · xxl 32 | `Theme.Spacing` |
| Radii | chip 6, icon 12, card 16 | `Theme.Radius` |
| Sizes | row icon 40, grid icon 64, detail icon 80, grid card 176 × 196, sidebar min 320, window min 760 × 520, QR 200 | `Theme.Size` |
| Fills | card = `controlBackgroundColor`, chip = `secondary` 14 %, separator = `separatorColor`, QR tile white / ink black | `Theme.Fill` |
| Duration | quick 0.15 s | `Theme.Duration` |

## 4. Typography

System text styles: app names `.headline` (detail `.largeTitle`-ish bold), chips `.caption`,
version lines `.callout` secondary, release notes rendered as headed bullet lists. Android: Material 3
type scale.

## 5. Components

Mac (`macos/Sources/AppDepot/UI/`):

- `AppRow` (list) and grid card — icon, name, `ChipView`, version line, `PrimaryActionView`
  (Install / Update / Open / progress bar).
- `HomeView` — sidebar with search, platform filter "This Mac · N | All · M" (⌘1/⌘2), list or grid,
  "N updates available" footer; toolbar actions.
- `AppDetailView` — big icon, name + chip, tagline, `PlatformBadges` ("Also on Android · X"), Open /
  More menu, Installed/Latest table, What's New.
- `RemoteActionView`, `SendToPhoneSheet` (QR + Copy link), `HistoryView`, `MenuBarView`, Settings
  panes inside app-shell's `SettingsShell`.

Android (`android/app/.../ui/`): home list/grid cards (`cardModel`), product list with "Other
platforms", detail page, settings, update notifications.

## 6. Layout and density

Mac: `NavigationSplitView`, sidebar ≥ 320 pt, window ≥ 760 × 520; list rows ~56 pt; grid two columns
of 176 × 196 cards. This Mac's products first, then "Other platforms". Android: single-column list or
grid, top app bar with search.

## 7. Depth, materials, glass

System only: the window's sidebar material, toolbar glass on macOS 26, cards on
`controlBackgroundColor`. No custom shadows or glass.

## 8. Motion

Next to none: install progress bars, system list/navigation transitions, the 0.15 s quick duration.
Nothing decorative to switch off.

## 9. States

- Per app: Update available, Up to date, Skipped, Updates itself (Wisp), No release yet (DevKit),
  Not for Mac / Not on this phone, installing (progress), "Updating N other apps first" (self-update
  waits — App Depot always updates itself last).
- Offline: `home-offline` snapshot (cached catalog, notice).
- Empty search: n/a in this pass.
- Unsafe install: "This app can't be installed safely yet."

## 10. Do's and don'ts

- Do keep every state as a tested pure mapping (`CardModel`, `HomeState`) and show it as a word.
- Do keep App Depot's own update last in every new install path (hub rule `app-depot.md`).
- Do load catalog icons from `.task` through `IconCache`, never in `body`.
- Don't add colour of its own beyond depot green; let app icons be the colour.

**Allowed exceptions:**

- *`Color.white` / `Color.black`* for the QR code tile and modules (`Theme.Fill.qrTile/qrInk`):
  scanners need dark modules on a light ground in both appearances.

## 11. Render recipe

```bash
cd macos && swift build
APPDEPOT_SNAPSHOT=<dir> .build/debug/AppDepot
```

Renders every screen, light and dark, from a fixture catalog with made-up installed versions; installs,
launches and quits nothing. Files: `home-{list,grid}[-all]`, `home-offline`,
`detail-{android-only,self-waiting,skipped,wisp}`, `menubar`, `send-to-phone`,
`settings-{general,updates,about}`, each `-light/-dark`.

Android: build the debug APK in `android/` (Corretto 17), install the `.dev` build on the emulator
(`emulator-5554`), `adb shell cmd uimode night no|yes`, `adb exec-out screencap -p > <file>.png`.

## 12. Known gaps

- **Render not verified: Android** (no emulator run in this pass).
- Offscreen Mac renders show list titles in the inactive-window grey; check active colours on a live
  debug build.
- The Mac app has no animation of its own, so no Reduce Motion handling is needed yet; add one through
  a single switch if motion is introduced.
- One fixed `.font(.system(size:))` in the Mac UI.
