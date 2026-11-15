# App Depot

Installs and updates the owner's apps from the live catalog (`GET /api/depot/catalog`, served by the
portfolio). Phase 2 = Android app in `android/`; phase 3 = macOS app (SwiftUI).

- Specs/plans: `docs/superpowers/specs/2026-11-15-app-depot-design.md`, `docs/superpowers/plans/`.
- Gradle project in `android/`, module `:app`, package `com.vuhpquang.appdepot` (debug suffix `.dev`).
  Run Gradle there with `JAVA_HOME=/Users/quangvu/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`.
- Version = `android/VERSION`; `versionCode = a*10000 + b*100 + c` (in `app/build.gradle.kts` and
  `core/VersionCode.kt`; keep both in sync).
- Release key: `~/.config/app-depot-signing/` (`android/scripts/make-keystore.sh`). Never commit or print it.
- Design tokens: `ui/theme/Theme.kt` (depot green `#2E7D5B` / `#6FCF9F`, dynamic colour off).
- Tags `android-vX.Y.Z`, GitHub releases not marked Latest, asset `AppDepot-X.Y.Z-android.apk`.
- Author/About strings follow the hub's `.claude/rules/app-shell.md`.
- UI: `ui/` (Compose + navigation-compose; `AppDepotViewModel` is the one state holder). Card states are a
  pure mapping in `ui/HomeState.kt` (`cardModel`), tested in `HomeStateTest`. Settings prefs in `android/Settings.kt`.
- Home list logic is pure in `ui/HomeList.kt` (`HomeListTest`): sort (`SortOrder`), layout (`HomeLayout`), sizes,
  release-page URL (private repos served via `vuhpquang.com/api/depot/download/` → `vuhpquang.com/apps`),
  uninstall eligibility (installed, never App Depot itself), card "⋮" menu. Search folding (accent-insensitive,
  Vietnamese đ) in `core/Search.kt`.
- Android prefs added in 0.2.0: `appdepot.ui.layout` (`list`|`grid`), `appdepot.ui.sort` (`updates`|`name`|`recent`),
  `appdepot.skipped` (`slug=version` lines). A skipped version plans as `AppState.Skipped` (chip "Skipped"):
  no badge, not in Update all or background notify/install, until a newer version is published.
- Full catalog (0.3.0): `CatalogRepository` fetches `/api/depot/catalog` with no `platform` query and caches every
  platform in `catalog.json`. `core/Products.kt` groups entries by `projectSlug` (else `slug`) into products and
  `localApps` keeps the `android` ones: install, open, uninstall, Update all, `reloadInstalled` and `CheckWorker`
  only ever use those (app ids collide across platforms: `app-depot-macos` is also `com.vuhpquang.appdepot`).
  Home list = `ui/ProductList.kt` (`ProductListTest`): filter `appdepot.ui.platformFilter` (`device` default | `all`)
  with counts, this phone's products first then "Other platforms"; non-Android products get "Not on this phone",
  no Install, "Open on the web" (`vuhpquang.com/projects/<projectSlug>`) and "Share link". Platform badges show
  on cards when the filter is All and always in the detail page ("Also on macOS · X" for products on both).
- Uninstall: `Intent.ACTION_DELETE` (`REQUEST_DELETE_PACKAGES`); the `uninstall` history row is local-only
  (`LOCAL_ONLY_EVENT_TYPES` in `core/DepotEvent.kt` keeps it out of the outbox: the server's event types are closed).
- Icons: catalog `iconUrl`, cached on disk in `cacheDir/icons/<sha256(url)>.png` (refetched after 7 days),
  letter tile only while loading or on failure. About shows App Depot's own launcher icon.
- Debug-only update testing: `adb shell "run-as com.vuhpquang.appdepot.dev sh -c 'echo zentask-android=0.0.1 > files/pretend.txt'"`
  makes an installed app look outdated (`slug=version` lines; ignored in release builds). Remove the file afterwards.

## Android: commands, release, testing

```bash
cd android && export JAVA_HOME=/Users/quangvu/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home
./gradlew :app:testDebugUnitTest :app:assembleDebug     # tests + debug APK (.dev)
android/scripts/release.sh X.Y.Z notes.md               # tests, signed APK, signer check, tag android-vX.Y.Z, GitHub release (not Latest)
# release.sh runs on `main` or on a branch whose HEAD equals origin/main (worktrees), pushing HEAD:main
PUBLISH=0 android/scripts/release.sh X.Y.Z notes.md     # office: commit + tag locally only
scripts/depot-publish.sh app-depot-android              # from the hub, after the release: puts it in the catalog
```

- Signing key: `~/.config/app-depot-signing/app-depot-release.jks` + `keystore.properties`; the expected
  signer fingerprint is committed in `android/scripts/release-signer.sha256` and checked by `release.sh`.
  Lose the keystore and installed copies can never update (a new key = reinstall everywhere), so keep
  a backup of that folder (password manager or encrypted disk), outside the repo.
- Emulator (`emulator-5554`): install the debug build (`.dev`) next to the release one. The debug build
  has a **Server** field in Settings (debuggable builds only; blank = vuhpquang.com) to point at a
  preview catalog. To fake an outdated app write `slug=version` into `files/pretend.txt` (see above) and
  remove it afterwards.
- Other apps hand over updates to App Depot: ZenTask Android checks whether `com.vuhpquang.appdepot`
  is installed and, if so, shows "Updates are managed by App Depot" instead of its own updater.
  Personal Wiki Android publishes itself to the catalog from its CI (see the hub rule `app-depot.md`).

## macOS: commands, release, testing

Swift Package in `macos/` (macOS 26+, Swift 6): `DepotCore` (pure rules ported from Android `core/`, with its
test cases) + `AppDepot` (SwiftUI/AppKit). app-shell is a submodule at `macos/Vendor/app-shell` (About,
Settings shell, What's New, self-update from the catalog entry `app-depot-macos` via `CatalogTransport`).

```bash
cd macos && git submodule update --init --recursive
swift build && swift test
SIGN_IDENTITY=- ./build.sh                                  # dev build, ad-hoc (release.sh refuses these)
APPDEPOT_SNAPSHOT=/tmp/shots .build/debug/AppDepot          # every screen → PNG, light + dark (fixture catalog)
APPDEPOT_INSTALL_ROOT=/tmp/root .build/debug/AppDepot --install-slug padlet   # DEBUG: real verified install into /tmp/root
INSTALL=1 macos/scripts/release.sh X.Y.Z docs/release-notes/macos-X.Y.Z.md    # tag macos-vX.Y.Z, release (not Latest), install
scripts/depot-publish.sh app-depot-macos                    # from the hub, after the release
```

- Version = `macos/VERSION` (build number `a*10000+b*100+c`, written into Info.plist by build.sh/release.sh).
  Asset `AppDepot-X.Y.Z-macos.zip`. Icon: `macos/scripts/make-icon.swift` renders `assets/make-icon.swift`.
- Signing: "App Depot Self-Signed" (`macos/scripts/make-signing-identity.sh`, backup `~/.config/app-depot-macos-signing/`,
  never committed or printed). Its designated requirement is pinned in `macos/scripts/release-requirement.txt`
  (= the catalog `signer` of `app-depot-macos`); release.sh refuses any other signer.
- release.sh runs on `main` or on a branch whose HEAD equals `origin/main` (worktrees), pushing `HEAD:main`.
- Install path (`Install/MacInstaller.swift`): size + SHA-256 → app-shell `ExternalInstaller.stage` (bundle id,
  `codesign --strict`) → requirement = installed copy's designated requirement, else catalog `signer`, else
  refused ("This app can't be installed safely yet.") → `replace` quits only the running copy at that exact path.
  `ReplaceGuard` (DepotCore) is the gate: Wisp (`selfUpdates`), DevKit (`none`), local builds, skipped and
  up-to-date apps never reach the installer. App Depot itself goes through app-shell's `Updater`, last.
- `APPDEPOT_INSTALL_ROOT` confines every lookup and install to that folder (tests, dev). Tests use fakes
  (`AppProcessControlling`) and ad-hoc bundles built in temp folders; they never touch `/Applications`.
- Background (`Background/`): 10 s after launch, then every 6 h while auto-check is on; auto-install (off by
  default) only updates managed apps that aren't running; running ones are announced. Settings keys:
  `appShell.updates.autoCheck/autoInstall`, `appdepot.ui.layout|sort`, `appdepot.skipped`, `appdepot.background.*`.
  Debug runs use the `com.vuhpquang.appdepot.debug` defaults domain and support folder; DEBUG
  `APPDEPOT_PRETEND=slug=version,…` fakes installed versions.
- Rows: `versionLine` (DepotCore/CardModel.swift) is a tested label mapping; self-updating and local-build rows show
  `Version X` and `· Latest Y` only when the catalog is newer. Icons: `iconSource` picks the installed app's own icon
  (`NSWorkspace.icon(forFile:)`), else the catalog `iconUrl` via `IconCache` (loaded from `.task`, never in `body`).
- Data: SQLite history (2000) + outbox (1000) in Application Support; pairing token in the Keychain
  (`com.vuhpquang.appdepot.pairing`; debug builds keep it in their defaults domain).
- Full catalog (0.2.0): `DepotAPI.catalogURL` has no `platform` query; `DepotModel.allApps` is every platform and
  `apps` = `localApps` (the `macos` ones). Rows, install, open, skip, uninstall, Update all, the menu bar extra and
  `BackgroundRun` only use those; `ReplaceGuard.canInstall` also refuses any non-`macos` entry (bundle ids collide:
  `app-depot-android` is `com.vuhpquang.appdepot` too). Products = `DepotCore/Products.swift` (`ProductTests`):
  grouped by `projectSlug` (else `slug`), filter `appdepot.ui.platformFilter` (`device` default | `all`, picker
  "This Mac · N" / "All · M" at the top of the list, ⌘1/⌘2), this Mac's products first then "Other platforms".
  Android-only products: chip "Not for Mac", no Install, "Send to phone" (`UI/SendToPhoneSheet.swift`: CoreImage QR
  of the Android `downloadUrl`, nearest-neighbour, black on a white tile; Copy link; Esc/⌘W/Done close) and
  "Open on the web" (`vuhpquang.com/projects/<projectSlug>`). Platform badges show on rows/cards with "All" and
  always in detail ("Also on Android · X"). Selection is the product key.
- App Depot updates itself last (`InstallQueue.swift`: `planSelfUpdate`, `selfMayStart`, `selfWaitingLine`):
  Update on App Depot first queues the other available (not skipped) updates, then `InstallCoordinator.requestSelfUpdate`
  waits until no other slug is busy (an install started meanwhile goes first) before app-shell's `Updater`; its row
  shows "Updating N other apps first". `BackgroundRun` defers the self-update while anything else is busy.
