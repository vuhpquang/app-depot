# App Depot Android: Material 3 Expressive redesign

Date: 2026-11-24 · Owner asked to apply M3 Expressive "very strongly" now that Compose Material3 1.5.0
is stable (BOM `2026.11.01`, released 2026-11-18). Android only; the Mac app keeps its native look.

## Decisions (owner)

- Level: **full Expressive** — expressive theme + motion scheme, shape morphing, wavy progress,
  connected button groups, flexible app bars, emphasized type. Layout changes where Expressive asks for it.
- Direction **A · Update Stage** (picked over "Shape Shelf" grid-first and "Calm Expressive"
  components-only): Home is update-first.
- Colour: depot green stays the brand, with a bolder tonal palette; **dynamic colour (wallpaper)** is
  an option in Settings → Appearance, **off by default** (`appdepot.ui.dynamicColor`).
- Scope: every screen (Home list + grid, App detail, History, Settings/About, What's New dialog).

## Design

### Theme (`ui/theme/Theme.kt`)
- `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(), shapes, typography)`.
- Depot-green scheme with stronger containers (primaryContainer is a clear green, not a wash);
  `dynamicLight/DarkColorScheme(context)` when the setting is on (minSdk 34, always available).
- Shapes: Expressive corner scale (extraLarge 28, plus a 40 dp "stage" token for the hero card).
- Motion follows the motion scheme's springs; Compose already honours the system "Remove animations"
  (animator duration scale), which is the single motion switch.

### Home (Update Stage)
- `LargeFlexibleTopAppBar`, collapses on scroll. Title = the state in words: "2 updates" with the
  subtitle "Ready to install", or "App Depot" / "Everything is up to date". Actions: Search, Sort,
  ⋮ (List/Grid view, History, Settings).
- Platform filter = connected `ToggleButton` group "This phone · 3 | All · 10".
- **Stage**: when updates exist and no search is active, one hero card (primaryContainer, stage
  shape) holds every app with an update — icon, name, `0.9.2 → 0.9.3`, its own progress — and a big
  shape-morphing **Update all** button with the download size. Those apps are not repeated below
  (`stageSplit`, pure + tested). An app leaves the stage with an item animation when it is updated.
- The rest is a **connected list**: cards in a section share one rounded group (large outer corners,
  small inner ones, 2 dp gaps; `groupCorners`, pure + tested). Grid layout keeps the stage and shows
  the rest as tiles.
- Progress: `LinearWavyProgressIndicator` (determinate while downloading, indeterminate otherwise).
- First load: `LoadingIndicator`; pull-to-refresh uses the Expressive loading indicator.

### App detail
- Hero header: app icon (96 dp) on a soft-burst `MaterialShapes` backdrop in primaryContainer,
  name in an emphasized headline, tagline, platform badges.
- Primary action = **split button**: Update / Install / Open, trailing menu with Skip version /
  Undo skip, Uninstall, Share, Release page (replaces the row of text buttons).
- Facts as a connected row of tiles: Installed · Latest · Size.
- What's New in a large-shape surface card; the app's history as a connected list.

### History, Settings, About
- `MediumFlexibleTopAppBar` collapsing on scroll.
- History grouped by day ("Today", "Yesterday", date; `historyDays`, pure + tested), each day a
  connected group.
- Settings sections as connected groups; new **Appearance** section with "Use wallpaper colours".
  Switches show a check icon in the thumb. About content and order unchanged (app-shell rule),
  launcher icon on a cookie-shaped backdrop.

## Kept as is
- All logic, states as words (`cardModel`), App Depot updates itself last, persisted keys.

## Install notification (added at the owner's request mid-build)
- New: one ongoing notification per install batch, driven by `InstallCoordinator.progress` through a
  pure `DownloadBatch` (remembers the batch so finished apps keep their segment after the UI clears them).
- Android 16+: `Notification.ProgressStyle`, one 100-unit segment per app with points between them,
  the current app's cached icon as the tracker icon, title "Downloading ZenTask", text "App 2 of 3 · 48%";
  promoted as a **Live Update** (`setRequestPromotedOngoing`, API 36.1+) with the "48%" short critical text.
- Android 14–15: classic `NotificationCompat` progress bar with the same words.
- Channel `installs`: default importance (sits with other notifications), no sound, no vibration.
  Gone as soon as nothing is queued or installing.

## Verify
`./gradlew :app:testDebugUnitTest :app:assembleDebug`; emulator renders of every screen in light and
dark, with dynamic colour on and off (`design-check`).
