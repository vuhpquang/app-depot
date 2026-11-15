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
