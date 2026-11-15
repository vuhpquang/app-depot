# App Depot — Phase 2: Android app — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android "App Depot" app (`com.vuhpquang.appdepot`) that lists the owner's Android apps from the live catalog, installs/updates one or all of them (silent after the first time), shows What's New and a history log, checks in the background, sends events to vuhpquang.com once paired, and updates itself.

**Architecture:** New repo `vuhpquang/app-depot` (public, empty today) added as submodule `projects/app-depot`; Android Gradle project in `android/` (module `:app`), modelled on ZenTask Android (`projects/zentask/apps/android`). Pure Kotlin logic (catalog parsing, version/plan, verification, event batching) in `com.vuhpquang.appdepot.core` with JVM unit tests; Android glue (PackageInstaller, PackageManager, WorkManager, SQLite, notifications) in `…appdepot.android`; Compose UI in `…appdepot.ui`. No DI framework, no Room (plain `SQLiteOpenHelper`), HTTP via `HttpURLConnection` like ZenTask's `AppUpdater.kt`, JSON via kotlinx-serialization.

**Tech Stack:** Gradle wrapper + versions copied from `projects/zentask` (`gradle/libs.versions.toml`: AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.08.00, compileSdk 37, targetSdk 36, minSdk 34, Java 17 / Corretto 17 at `~/Library/Java/JavaVirtualMachines/corretto-17.0.13`), kotlinx-serialization 1.12.0, coroutines 1.11.0, WorkManager (latest stable `androidx.work:work-runtime-ktx` 2.10.x — check Google Maven), JUnit 4. Android SDK at `~/Library/Android/sdk`, emulator AVD `Medium_API37_1`.

**Spec:** `docs/superpowers/specs/2026-11-15-app-depot-design.md` (§2 Android, §1 catalog API, "Self-update", §4 hub skills). Phase 1 server is live at https://vuhpquang.com.

## Global Constraints

- Identity: display name **App Depot**, `applicationId com.vuhpquang.appdepot` (debug suffix `.dev`), namespace `com.vuhpquang.appdepot`. Own accent colour: depot green `#2E7D5B` (light) / `#6FCF9F` (dark) as theme tokens — no other app's colours. Icon = adaptive vector drawable defined in code (a box/crate glyph), not copied art.
- Version from `android/VERSION` (start `0.1.0`), `versionCode = a*10000 + b*100 + c` (same formula as ZenTask). Tags `android-vX.Y.Z`; GitHub releases of this repo are **not** marked Latest (`--latest=false`); asset `AppDepot-X.Y.Z-android.apk`.
- Release signing: keystore `~/.config/app-depot-signing/app-depot-release.jks` + `~/.config/app-depot-signing/keystore.properties` (storePassword, keyPassword, keyAlias=appdepot), read by Gradle only if present; never committed, never printed. Debug builds use the debug key.
- Catalog: `GET https://vuhpquang.com/api/depot/catalog?platform=android` → `{data:{generatedAt, apps:[{slug,name,tagline,platform,appId,repo,iconUrl,projectSlug,signer,relaunchArgs,installMode,latest:{version,build,notes,assetName,downloadUrl,sha256,size,publishedAt,minOs}|null}]}, error, status}`. Base URL constant `https://vuhpquang.com`, overridable in debug via a hidden Settings field. Unknown JSON keys ignored.
- Device API: `POST /api/depot/devices {code, deviceId(uuid), name, platform:"android", depotVersion}` → 201 `{data:{deviceId, token}}`; `POST /api/depot/events` Bearer token, `{events:[{type, appSlug?, fromVersion?, toVersion?, message?(≤2000), at ISO}]}` 1–200 per batch; types exactly: `check, install, update, install_failed, open, crash, depot_started, self_update`.
- Verification before every install: downloaded file size == `latest.size` and SHA-256 == `latest.sha256`; for a first install (package not installed) the APK's signing cert SHA-256 (from `getPackageArchiveInfo(…, GET_SIGNING_CERTIFICATES)`) must equal catalog `signer` when `signer` is non-null; updates rely on Android's same-key check. Any mismatch → Failed with a clear message, file deleted, nothing installed.
- Install: `PackageInstaller` session, `MODE_FULL_INSTALL`, `setAppPackageName(appId)`, `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`; permissions `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `QUERY_ALL_PACKAGES` (sideloaded app; needed to read any catalog app's installed version), `INTERNET`, `POST_NOTIFICATIONS`, `ACCESS_NETWORK_STATE`. Update All installs one app at a time, App Depot itself last.
- An installed version **newer** than the catalog shows "Local build" and is never downgraded. `installMode` `none` → listed, no install button; `selfUpdates` → status + Open only.
- Updates conventions (`.claude/rules/app-shell.md`): background check every 6 h while "Check for updates automatically" (default on); "Install updates automatically" default off and disabled while auto-check is off; a background check never replaces an Available/Failed state the user hasn't acted on and never shows an error.
- About (in Settings): icon, "App Depot", tagline "Install and update my apps in one place", `Version X (code)`, Check for updates, What's New, Report issue (`https://github.com/vuhpquang/app-depot/issues/new`), divider, "Made by Ha Phung Quang Vu", links vuhpquang.com · GitHub · LinkedIn · Source code, `© 2026 Ha Phung Quang Vu`. Exact author strings from `projects/app-shell/Sources/AppShellCore/Author.swift`.
- Copy: sentence case, verbs on buttons ("Update all", "Install", "Open"), errors say what happened + what to do, no exclamation marks. UI strings in `res/values/strings.xml` (English).
- Light + dark both; Material 3 dynamic colour **off** (use own tokens). Comfortable touch targets ≥ 48dp.
- Never install anything on the owner's phone; test on the emulator only. Never push the hub. App-depot repo: push `main` allowed (spec "Repos"). ZenTask: push allowed + release via its `scripts/release.sh`.
- Commits: conventional, `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, git user.email `vuhpquang@gmail.com` (set locally in the new repo).

## Review Focus

1. Catalog fetch fails (offline, 5xx, malformed JSON) → Home shows the last cached catalog with a non-blocking "Couldn't refresh" line, never a crash or empty screen → test in Task 2 (cache + parse failure) and Task 5 (state).
2. Download interrupted or tampered (short file, wrong SHA-256, wrong signer on first install) → Failed state, temp file deleted, `install_failed` event logged → tests in Task 3.
3. Update All while one install needs user action / fails → the queue continues with the next app, ends with a summary, App Depot self-update last → test in Task 3.
4. Versions `0.15.10` vs `0.15.2`, installed newer than catalog, `latest: null`, `installMode none/selfUpdates` → correct per-app state → tests in Task 2.
5. Pairing with wrong/used code (403) or revoked token (401 on events) → clear message / token cleared, events stay queued (capped 1000, oldest dropped) → tests in Task 4.

---

### Task 1: Repo, Gradle project, signing, empty app

**Files:**
- Hub: `git submodule add https://github.com/vuhpquang/app-depot.git projects/app-depot`
- Create in `projects/app-depot`: `README.md`, `CLAUDE.md`, `.gitignore` (Gradle/Android/IDE, `local.properties`, `*.jks`, `keystore.properties`), `docs/superpowers/specs/2026-11-15-app-depot-design.md` + `docs/superpowers/plans/2026-11-15-app-depot-p{1,2}-*.md` (moved from the hub with `git mv`-equivalent: copy here, delete from hub in the hub commit), `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/gradle/wrapper/*` + `android/gradlew` (copied from zentask), `android/VERSION` (`0.1.0`), `android/local.properties` (sdk.dir, gitignored), `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/kotlin/com/vuhpquang/appdepot/MainActivity.kt`, `…/ui/theme/Theme.kt` (tokens: colours above, spacing 4/8/16/24, radius 12/20), `android/app/src/main/res/drawable/ic_launcher_foreground.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/values/strings.xml`, `android/scripts/make-keystore.sh` (creates the keystore + properties with a random password via `keytool`, chmod 600, refuses to overwrite), `android/app/src/test/kotlin/com/vuhpquang/appdepot/SmokeTest.kt`.
- Hub: `CLAUDE.md` projects table row (App Depot, `projects/app-depot`, Kotlin/Compose (Android) + SwiftUI (macOS, phase 3), `main`, `android/scripts/release.sh X.Y.Z notes.md`), `.claude/rules/git.md` push row (`app-depot | main | allowed`), `.claude/rules/app-shell.md` "Who uses what" row (App Depot Android: follows conventions by hand), `.claude/rules/app-depot.md` (App Depot apps themselves are catalog entries `app-depot-android` / later `app-depot-macos`).

**Interfaces:**
- Produces: buildable `:app` with `MainActivity` showing a placeholder Home in `AppDepotTheme`; `signingConfigs.release` loaded from `~/.config/app-depot-signing/keystore.properties` when present, else release falls back to debug signing with a Gradle warning.

- [ ] **Step 1:** `SmokeTest` asserting `versionCodeFor("0.1.0") == 100` and `versionCodeFor("1.2.3") == 10203` (helper `fun versionCodeFor(v: String): Int` in `buildSrc`-free form: put it in `android/app/src/main/kotlin/com/vuhpquang/appdepot/core/VersionCode.kt` and use the same formula in Gradle).
- [ ] **Step 2:** `cd projects/app-depot/android && ./gradlew :app:testDebugUnitTest` → FAIL (no sources).
- [ ] **Step 3:** Scaffold everything above; run `android/scripts/make-keystore.sh` once (creates the real release key — the only copy lives in `~/.config/app-depot-signing/`, tell the owner to back it up).
- [ ] **Step 4:** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` → BUILD SUCCESSFUL; `apksigner verify --print-certs` on the release APK shows a cert that is not the debug cert.
- [ ] **Step 5:** Commit in app-depot `chore: scaffold App Depot Android app`, push `main`; hub commit `feat: add app-depot submodule` (+ docs moves + rule rows), `npx gitnexus analyze`.

### Task 2: Catalog, installed state, per-app plan (pure core)

**Files:** Create `core/Catalog.kt` (serializable models mirroring the API), `core/CatalogParser.kt`, `core/Versions.kt`, `core/AppPlan.kt`, `android/CatalogRepository.kt` (fetch + cache JSON in `filesDir/catalog.json`), `android/InstalledApps.kt`. Tests: `core/CatalogParserTest.kt`, `core/VersionsTest.kt`, `core/AppPlanTest.kt`.

**Interfaces:**
- `fun parseCatalog(json: String): Result<List<CatalogApp>>` (ignores unknown keys; envelope `data.apps`).
- `fun compareVersions(a: String, b: String): Int` (numeric segments, missing = 0).
- `sealed interface AppState { NotInstalled; UpToDate(installed); UpdateAvailable(installed, latest); LocalBuild(installed, latest); NoRelease(installed?); SelfUpdates(installed?, latest?); NotInstallable }`
- `fun planFor(app: CatalogApp, installedVersion: String?): AppState` — `installMode none` → `NotInstallable` (or `NoRelease` when `latest == null`), `selfUpdates` → `SelfUpdates`.
- `class CatalogRepository(context, baseUrl)`: `suspend fun refresh(): Result<List<CatalogApp>>` (writes cache on success), `fun cached(): List<CatalogApp>`.
- `class InstalledApps(context)`: `fun versionOf(appId: String): String?`, `fun launchIntent(appId: String): Intent?`.

- [ ] **Step 1: Failing tests** — parse the real live catalog sample (save `curl https://vuhpquang.com/api/depot/catalog?platform=android` as `src/test/resources/catalog-android.json`) → 2 apps, `zentask-android` latest `0.15.2`; malformed JSON → `Result.failure`; unknown extra field ignored; `compareVersions("0.15.10","0.15.2") > 0`; `planFor` cases: not installed → `NotInstalled`; installed `0.15.1` latest `0.15.2` → `UpdateAvailable`; installed `0.15.3` → `LocalBuild`; latest null → `NoRelease`; `installMode=selfUpdates` → `SelfUpdates`.
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** `./gradlew :app:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit `feat: catalog parsing and per-app update state`, push.

### Task 3: Download, verify, install, Update All queue

**Files:** Create `core/Verify.kt` (sha256 of a stream, cert digest compare), `core/InstallQueue.kt` (pure ordering + progression state machine), `android/ApkDownloader.kt`, `android/ApkInstaller.kt` (+ `InstallResultReceiver` registered in the manifest), `android/InstallCoordinator.kt` (one place that runs a single install or the queue, exposes `StateFlow<Map<String, InstallProgress>>`). Tests: `core/VerifyTest.kt`, `core/InstallQueueTest.kt`.

**Interfaces:**
- `fun sha256Hex(input: InputStream): String`; `fun verifyDownload(file: File, expectedSize: Long, expectedSha256: String): VerifyResult` (`Ok | WrongSize | WrongHash`); `fun signerMatches(apkCertSha256: String?, catalogSigner: String?): Boolean` (true when catalogSigner null; case-insensitive hex compare).
- `sealed interface InstallProgress { Queued; Downloading(percent); Verifying; Installing; NeedsUserAction; Done(version); Failed(message) }`.
- `fun orderForUpdateAll(slugs: List<String>, selfSlug: String = "app-depot-android"): List<String>` (self last, rest in catalog order).
- `class InstallCoordinator`: `suspend fun install(app: CatalogApp)`, `suspend fun updateAll(apps: List<CatalogApp>)`; each outcome is written to History (Task 4 interface `History.record(event)`; in this task, depend on an interface `EventSink { fun record(e: DepotEvent) }` defined here in `core/DepotEvent.kt`: `data class DepotEvent(type: String, appSlug: String?, fromVersion: String?, toVersion: String?, message: String?, at: Instant)`).
- Messages (exact): wrong size/hash → "The download was damaged. Try again."; signer mismatch → "This file isn't signed like {name}. It wasn't installed."; conflict/incompatible from PackageInstaller → "Android refused the update: it isn't signed like the installed app."; needs "Install unknown apps" → state `NeedsPermission`, open `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for App Depot.

- [ ] **Step 1: Failing tests** — sha256 of `"abc"` = `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`; size mismatch → `WrongSize`; hash mismatch → `WrongHash`; `signerMatches("AB12…", "ab12…")` true, null catalog → true, different → false; `orderForUpdateAll(["app-depot-android","zentask-android","personal-wiki-android"])` → `[zentask-android, personal-wiki-android, app-depot-android]`; queue: second app `Failed` → third still runs (pure state machine test with a fake installer).
- [ ] **Step 2–4:** fail → implement → pass.
- [ ] **Step 5:** Emulator check (debug build): boot `Medium_API37_1` headless (`emulator -avd Medium_API37_1 -no-window -no-audio &`), `adb install` the debug App Depot, trigger install of `zentask-android` from a debug-only intent/extra or the Task 5 UI if already present (else defer this step to Task 5 Step 4) — expect the system confirmation the first time, ZenTask installed, `adb shell pm list packages com.vuhpquang.zentask` shows it.
- [ ] **Step 6:** Commit `feat: verified download and silent install with update-all queue`, push.

### Task 4: History, event queue, pairing

**Files:** Create `android/HistoryDb.kt` (`SQLiteOpenHelper`, tables `history(id, type, app_slug, from_version, to_version, message, at)` and `outbox(id, json, created_at)`), `android/History.kt` (implements `EventSink`: writes history row + outbox row), `core/EventBatch.kt` (JSON body building, 200 per batch, 1000 cap), `android/DepotApi.kt` (pair + post events), `android/Pairing.kt` (device UUID persisted, token in `SharedPreferences("appdepot.pairing")`), `android/EventUploader.kt` (flush outbox when paired: batches; 201 → delete sent rows; 401 → clear token, keep rows; network error → keep). Tests: `core/EventBatchTest.kt`, `core/PairingResponseTest.kt` (pure parsing of 201/403/401 bodies).

**Interfaces:**
- `fun batchesOf(events: List<Pair<Long, String>>, max: Int = 200): List<List<Pair<Long,String>>>`; `fun eventsBody(jsonEvents: List<String>): String`; `fun trimOutbox(count: Int, cap: Int = 1000): Int` (rows to drop, oldest first).
- `sealed interface PairResult { Paired(token); BadCode; Failed(message) }` from `fun parsePairResponse(status: Int, body: String): PairResult`.
- `History.recent(limit: Int = 200, appSlug: String? = null): List<HistoryRow>`.
- Events recorded: `depot_started` on app start (once per process), `check` after each catalog refresh (message = "n updates"), `install`/`update`/`install_failed`/`self_update` from InstallCoordinator.

- [ ] **Step 1: Failing tests** — 450 events → batches of 200/200/50; body has `"events":[…]`; 1005 rows → drop 5; parse 201 `{"data":{"deviceId":"…","token":"t"}}` → `Paired("t")`; 403 → `BadCode`; 500 → `Failed`.
- [ ] **Step 2–4:** fail → implement → pass.
- [ ] **Step 5:** Commit `feat: history log, event outbox and device pairing`, push.

### Task 5: UI — Home, App detail, History, Settings, About

**Files:** Create `ui/AppDepotViewModel.kt` (combines catalog, installed versions, coordinator progress, settings), `ui/HomeScreen.kt`, `ui/AppDetailScreen.kt`, `ui/HistoryScreen.kt`, `ui/SettingsScreen.kt` (Updates section, Connect to vuhpquang.com, About), `ui/components/*` (AppCard, StatusChip, UpdateAllBar), `ui/ReleaseNotes.kt` (`## ` headings + `- ` bullets → Text, no HTML), navigation with `navigation-compose`. Modify `MainActivity.kt`. Tests: `ui/ReleaseNotesTest.kt` (pure parser, same cases as the web one), `ui/HomeStateTest.kt` (pure mapping from AppState+InstallProgress to card label/button: e.g. `UpdateAvailable` → "Update", `LocalBuild` → "Local build" chip and no button).

**Interfaces / UX:**
- Home: top app bar "App Depot" + Settings icon; when ≥1 update: bar **"Update all (n)"**; list of cards (icon from `iconUrl` or a letter tile, name, tagline, "Installed X · Latest Y" or state chip, primary button Install/Update/Open per state, progress bar while downloading). Pull-to-refresh. "Couldn't refresh. Showing the last list." line on refresh failure.
- Detail: header, buttons, **What's new** (release notes), "History" (rows for this app), link "Source code" to `https://github.com/<repo>`.
- History: newest first, type icon + "ZenTask 0.15.1 → 0.15.2" + relative time; failed rows show the message.
- Settings: Updates (Check automatically [on], Install automatically [off, disabled when check off], "Last checked …", Check now), Connection (Connect to vuhpquang.com → enter 8-char code → Connect; when paired: device name + Disconnect), About block (Global Constraints text).
- What's New for App Depot itself: shown once after its own version changes (record last seen version; first run records silently).

- [ ] **Step 1: Failing tests** for ReleaseNotes + HomeState mapping. **Step 2–3:** fail → implement.
- [ ] **Step 4: Visual check** — emulator: `adb shell cmd uimode night no|yes`, launch, `adb exec-out screencap -p > shots/home-{light,dark}.png`, also detail + settings; Read the PNGs, fix contrast/spacing issues; then run the deferred install check from Task 3 Step 5 through the UI (Install ZenTask → confirm → Installed; then `adb install` an older ZenTask is not available — instead verify Update state by installing App Depot with `APPDEPOT_PRETEND_VERSIONS` debug override `zentask-android=0.0.1` that makes the planner treat it as outdated, and run Update all → second install is silent).
- [ ] **Step 5:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` pass; commit `feat: home, detail, history and settings screens`, push. Screenshots saved under the scratchpad, not the repo.

### Task 6: Background check, notifications, auto-install

**Files:** Create `android/CheckWorker.kt` (WorkManager `CoroutineWorker`, unique periodic work `appdepot.check`, 6 h, network constraint; first run delayed 10 s after app start via a one-time request), `android/Notifications.kt` (channel "Updates"; "n app updates" notification opening Home; on auto-install completion "Updated ZenTask to 0.15.2"), `core/BackgroundPolicy.kt` (pure: given previous state + new result → whether to notify / auto-install / keep state), `android/Settings.kt` (`autoCheck` default true, `autoInstall` default false; keys `appdepot.updates.autoCheck` / `appdepot.updates.autoInstall`). Request `POST_NOTIFICATIONS` at first launch (once). Tests: `core/BackgroundPolicyTest.kt`.

**Interfaces:** `fun decide(prev: Map<String, AppState>, next: Map<String, AppState>, autoInstall: Boolean): BackgroundDecision(notifySlugs: List<String>, installSlugs: List<String>)` — only newly available updates notify; failed/available states the user hasn't acted on are kept; autoInstall installs all updatable (self last); errors never notify.

- [ ] **Step 1–4:** failing tests (new update → notify; same update already notified → no notify; autoInstall on → install list in order, self last; check error → no notify) → implement → pass.
- [ ] **Step 5:** Emulator: `adb shell cmd jobscheduler run -f com.vuhpquang.appdepot.dev <jobId>` or WorkManager test helper in a debug build to force a run; observe the notification (`adb shell dumpsys notification | grep appdepot`).
- [ ] **Step 6:** Commit `feat: background update checks and notifications`, push.

### Task 7: Release + catalog entry + first release

**Files:** Create `android/scripts/release.sh` (usage `[PUBLISH=0] scripts/release.sh X.Y.Z notes.md`: require clean tree on `main`; write VERSION; `./gradlew :app:testDebugUnitTest :app:assembleRelease`; verify signer equals the App Depot release cert (apksigner, Corretto 17 JAVA_HOME); copy to `build/AppDepot-X.Y.Z-android.apk`; commit `release: android-vX.Y.Z`; tag; unless `PUBLISH=0`: push main + tag, `gh release create android-vX.Y.Z <apk> --title "App Depot (Android) X.Y.Z" --notes-file notes.md --latest=false`; then print the hub command to publish to the catalog). Modify hub `docs/app-depot-apps.json` (+ `app-depot-android`: android, `com.vuhpquang.appdepot`, repo `vuhpquang/app-depot`, tagPattern `^android-v`, assetPattern `^AppDepot-[0-9.]+-android\.apk$`, installMode managed, order 0, signer = release cert SHA-256), hub `.claude/skills/release-project/SKILL.md` (App Depot Android row: release script path + depot-publish slug).

- [ ] **Step 1:** Write `docs/release-notes/android-0.1.0.md` (user-facing, `## What's new` bullets: install and update Personal Wiki and ZenTask in one place; Update all; history; background checks).
- [ ] **Step 2:** `cd projects/app-depot/android && scripts/release.sh 0.1.0 ../docs/release-notes/android-0.1.0.md` → GitHub release `android-v0.1.0` with the APK.
- [ ] **Step 3:** Hub: `scripts/depot-publish.sh app-depot-android --meta` then `scripts/depot-publish.sh app-depot-android` → `published (created)`; `curl …/api/depot/catalog?platform=android | jq '.data.apps[].slug'` includes `app-depot-android`.
- [ ] **Step 4:** Emulator: install the release APK, confirm Home lists 3 apps and App Depot shows "Up to date" for itself.
- [ ] **Step 5:** Hub commits: registry + skill (`feat: App Depot Android in the catalog`), pointer bump `chore: bump app-depot submodule (App Depot Android 0.1.0)`, gitnexus analyze.

### Task 8: ZenTask steps aside + Personal Wiki CI publishes

**Files:** ZenTask: modify `projects/zentask/apps/android/src/main/kotlin/com/vuhpquang/zentask/android/AppUpdater.kt` (+ its Updates UI) — when package `com.vuhpquang.appdepot` is installed (`<queries><package android:name="com.vuhpquang.appdepot"/></queries>` in its manifest), `check()`/`install()` don't run; the Updates screen shows "Updates are managed by App Depot" + "Open App Depot" button (launch intent). Personal Wiki (`/Users/quangvu/Work/Repos/personal/personal-wiki`, not a submodule): modify `.github/workflows/android-release.yml` — after the GitHub release step, a step that runs only on `android-v*` tags: compute sha256/size of the APK, POST `https://vuhpquang.com/api/admin/depot/releases` with `Authorization: Bearer ${{ secrets.APPDEPOT_ADMIN_TOKEN }}` and body `{app:"personal-wiki-android", version, notes (release body), assetName, downloadUrl (browser URL), assetApiUrl (asset API url), sha256, size, publishedAt}`; fail the job on non-2xx. Set the secret with `gh secret set APPDEPOT_ADMIN_TOKEN -R vuhpquang/personal-wiki < ~/.config/app-depot/admin-token`.

- [ ] **Step 1:** ZenTask: unit test (pure helper `fun updatesManagedByDepot(installed: Set<String>): Boolean`) fails → implement → pass (`./gradlew :shared:allTests` unaffected; `:android:testDebugUnitTest` if present, else a JVM test in `apps/android/src/test`).
- [ ] **Step 2:** ZenTask build + release per its CLAUDE.md (`INSTALL=1 scripts/release.sh X.Y.Z notes.md` at home — bumps patch; release notes "## Fixes - When App Depot is installed, ZenTask leaves updates to it"), then hub `scripts/depot-publish.sh zentask-android` and `zentask-macos`, pointer bump.
- [ ] **Step 3:** Personal Wiki: edit workflow, `actionlint` if available (else `yq` parse) → commit `ci: publish Android releases to the App Depot catalog` → push its default branch (owner's repo; the change only adds a post-release step). Set the secret.
- [ ] **Step 4:** Docs: app-depot `CLAUDE.md` (layout, commands, release, signing, emulator testing), hub rule `app-depot.md` (Wiki publishes from CI; ZenTask defers to App Depot), project memory.
