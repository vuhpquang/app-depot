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
