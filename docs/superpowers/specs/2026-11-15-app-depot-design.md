# App Depot — one place to install and update the owner's apps

Date: 2026-11-15 · Status: draft for review · Name **App Depot** (repo `vuhpquang/app-depot`)

> Lives in the hub for now because it spans portfolio, two new apps and the hub's own skills.
> When `projects/app-depot` is added as a submodule, this file moves into `projects/app-depot/docs/superpowers/specs/`.

## Why

The owner is tired of downloading and updating each app by hand: Personal Wiki Android has no in-app
update (APK from the CDN), ZenTask Android has its own updater, and the macOS apps each update
themselves. They want one manager app per platform, in the spirit of Morphe (a manager that lists
the apps, installs them and keeps them updated), plus a server that knows every app's latest release,
and the hub's skills keeping that server current on every release.

## Goals

1. **Android App Depot** — Personal Wiki + ZenTask (Android). Home screen of apps, open, install for the
   first time, update one, **Update All in one tap**, What's New, background check + notification,
   history log, self-update.
2. **macOS App Depot** — Padlet, LaunchApp, Quickcast, Wisp, DevKit, ZenTask macOS. Same features.
3. **Server on the portfolio (vuhpquang.com)** — the single catalog of apps and releases, event ingest
   (logs/tracking from both hubs), an admin page to read them.
4. **vuhpquang.com shows the same data** — project pages get latest version, release date, download
   button and changelog from the catalog (no hand-maintained version numbers).
5. **Hub skills/rules keep it current** — every release / new app / app edit pushes to the server.

## Non-goals

- Rollback to older versions (owner said no).
- Running mini apps *inside* the hub (no WebView/plugin super app): each app stays its own APK/`.app`.
- Replacing each app's own updater: they keep working; the hub is an extra, not a dependency.
- iOS, Windows, Wear OS (ZenTask's Wear APK is ignored).

## Anti-goals (failure even if it "works")

- App Depot **quits Wisp** (owner's main terminal) or any app with unsaved work without asking.
- Weakened verification: an APK/`.app` not signed by the same key/identity as the installed copy gets
  installed.
- Catalog drifts from reality (a release exists but the hub/site says "up to date"), because a skill
  forgot to publish.
- Secrets (admin token, keystores, signing identities) end up in a repo or in a log.

## Constraints found in the code

- ZenTask Android already has a working `AppUpdater.kt` (GitHub release → APK → `PackageInstaller`).
- Personal Wiki is Tauri (web bundle in an APK), repo **private**: GitHub release assets need a token;
  the CDN has fixed `personal-wiki-android-latest.apk` keys with no version info. Its CI
  (`android-release.yml`) uploads to Cloudflare R2.
- Latest assets today: Padlet `Padlet.dmg`, LaunchApp `LaunchApp.dmg`, Quickcast
  `Quickcast-X.Y.Z.{dmg,zip}`, Wisp `Wisp-X.Y.Z.zip` (tag `wisp-vX.Y.Z`), ZenTask
  `ZenTask-X.Y.Z{.zip,-android.apk}`, Wiki `android-vX.Y.Z`; **DevKit has no release yet**.
- Each macOS app has its **own self-signed identity**; the installed Wisp updates by rebuilding from its
  checkout (`WispSourcePath`).
- app-shell `Installer` already stages DMG/zip and verifies bundle id + `codesign --strict`, but
  `satisfiesOwnSigner` checks against the *running* process, and `relaunch` swaps the *running* app.
- Portfolio: Next.js 16 + Prisma 7 + Supabase on Vercel, has `Project` model, admin JSON API behind a
  bearer token (`requireAdmin`), no admin UI pages; light-only, EN/VI. Pushing deploys → ask first.
- Office network blocks `git push` to GitHub (office releases are `PUBLISH=0`, no GitHub release).

## Approaches considered

| | Approach | Verdict |
|---|---|---|
| A | Separate native manager apps (Kotlin/Compose, SwiftUI) + catalog on a server | **Chosen** — reuses ZenTask's updater and app-shell; each app stays independent |
| B | ZenTask doubles as the hub | Rejected — hub dies when ZenTask breaks or updates itself |
| C | Tauri/web hub | Rejected — install still needs native code, two layers for the core job |

Server location: **portfolio** (chosen by the owner, on Claude's recommendation) over the Wiki's
Fastify VPS API or a new service — it already has `Project`, an admin API and Supabase.

## Design

### 1. Catalog + server (portfolio)

New Prisma models (Supabase), all `@@map` snake_case like the existing ones:

- `DepotApp` — `slug` (unique, e.g. `zentask-android`), `name`, `tagline`, `platform` (`android`|`macos`),
  `appId` (Android package / macOS bundle id), `repo` (`vuhpquang/zentask`), `iconUrl`,
  `projectSlug?` (links the site page; the site's projects are static data in `src/data/projects.ts`, not the DB `Project` table), `privateRepo`, `signer` (Android: SHA-256 of signing cert; macOS:
  designated requirement string — used to verify a **first** install, when there's no installed copy
  to compare with), `relaunchArgs` (e.g. `["--background"]` for LaunchApp), `installMode`
  (`managed` | `selfUpdates` | `none` — see Wisp/DevKit below), `order`, `enabled`.
- `DepotRelease` — `appId` → `DepotApp`, `version`, `build?`, `notes` (Markdown, user-facing,
  `## What's new` / `## Fixes`), `downloadUrl`, `sha256`, `size`, `publishedAt`, `minOs?`.
  Unique (`appId`, `version`). Latest = highest semver, not insertion order.
- `DepotDevice` — `id` (UUID generated by the hub on first run), `name`, `platform`, `hubVersion`,
  `lastSeenAt`, `tokenHash`.
- `DepotEvent` — `deviceId`, `appSlug?`, `type` (`check`, `install`, `update`, `install_failed`,
  `open`, `crash`, `depot_started`, `self_update`…), `fromVersion?`, `toVersion?`, `message?`, `at`. Kept 180 days.

API (Route Handlers, same `ok/created/unauthorized` helpers):

- `GET /api/depot/catalog?platform=android|macos` — public, cached (`s-maxage=300`): apps + latest
  release each. Used by both hubs and the site.
- `POST /api/depot/devices` — register a device with a one-time **pairing code** shown on the admin
  page; returns a device token (stored hashed). Avoids baking a shared secret into the APK.
- `POST /api/depot/events` — device bearer token; batched events (the hub queues offline and flushes).
- `POST /api/admin/depot/apps`, `PATCH …/apps/[slug]`, `POST /api/admin/depot/releases` — admin bearer
  token; what the hub skills call. Release POST is idempotent on (`app`, `version`).
- `GET /api/depot/download/[slug]/[version]` — for **private repos** (Personal Wiki, DevKit): the server
  asks GitHub for the release asset with a read-only token (`GITHUB_RELEASES_TOKEN`, fine-grained,
  Contents: read on those repos) and answers `302` to GitHub's short-lived signed URL, so no large file
  passes through Vercel and no token reaches a device. Public repos use the GitHub asset URL directly.
  GitHub Releases stay the one place assets live; the CDN is not needed by App Depot. *(Claude's call.)*

Admin page `/admin/depot` (English only, `noindex`, private — the EN/VI rule is for public pages):
apps + latest release, devices + last seen, event log with filters (device, app, type), pairing
codes. Login: password → httpOnly signed cookie, checked against `ADMIN_SECRET` server-side.

Site: a new `/apps` page (EN/VI) lists every catalog app with version, download and notes; project pages with a linked `DepotApp` show "Version X · date", download button(s) per platform
and the release notes, from the catalog (server component, revalidate 5 min). EN/VI labels.

### 2. Android App Depot

- Kotlin/Compose, minSdk 34 (same floor as ZenTask), Material 3, own accent colour/icon.
- Starts from ZenTask's `AppUpdater.kt`, generalised to any catalog entry.
- **Installs** with `PackageInstaller` sessions + `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` and
  the `UPDATE_PACKAGES_WITHOUT_USER_ACTION` permission: Android asks once per app the first time
  (App Depot isn't yet its installer of record), then App Depot's updates are silent → real Update All.
- Verifies before installing: `sha256` from the catalog; for first installs, signing cert matches
  `signer`; for updates Android itself rejects a different key.
- Home: card per app — icon, name, installed vs latest version, state (Not installed / Up to date /
  Update / Downloading % / Failed), Open. Top bar: **Update All (n)**. Detail: What's New, history.
- Background: WorkManager every 6 h (auto-check on by default), notification "n updates"; optional
  auto-install (off by default), following `.claude/rules/app-shell.md` update conventions.
- Log: local Room/SQLite history of every check/install, and the same events queued to the server.
- Self-update: App Depot is a catalog entry like the others (see "Self-update" below).
- ZenTask's own updater steps aside when App Depot is installed (package query): its Updates screen says
  "Updates are managed by App Depot" with an Open App Depot button, so App Depot stays installer of record and
  updates stay silent. *(Claude's call; a small ZenTask Android change in phase 2.)*
- Works fully **unpaired** (install/update need no account). First run offers "Connect to
  vuhpquang.com" with a pairing code, skippable; events queue locally from the first run and flush once
  paired (queue capped). *(Claude's call.)*
- An installed version **newer** than the catalog (office `PUBLISH=0` build) shows "Local build" and is
  left alone. *(Claude's call.)*

### 3. macOS App Depot

- SwiftUI app, macOS 26+, uses app-shell (About, Settings shell, its own self-update) — a menu bar
  extra plus a main window, Launch at login.
- Installing other apps needs app-shell work (done in app-shell, tagged, per the app-shell rule):
  verify a candidate against **a given requirement** (the installed copy's, or the catalog `signer`
  for a first install), and a swap that quits **another** app, replaces it and reopens it with
  `relaunchArgs`.
- Per-app behaviour from `installMode`:
  - `managed` (Padlet, LaunchApp, Quickcast, ZenTask): download, verify, quit if running (polite
    `terminate`, then ask if it refuses), swap, relaunch if it was running.
  - **Wisp** `selfUpdates`: the installed Wisp updates from its checkout (new commits on `origin/main`),
    not from release tags, so App Depot shows the installed version, the latest `wisp-v` release and its
    notes, labelled "Updates itself", with an Open Wisp button. No install, no quit, no change to Wisp.
    *(Claude's call.)*
  - **DevKit** `none` until it has releases: listed as "No release yet".
- Same home/Update All/history/log/server events, pairing and "Local build" rules as Android.

### Self-update of both App Depots

One repo, two platforms, so GitHub's single "Latest" release can't serve both. Both App Depots update
themselves **from the catalog** (`app-depot-android`, `app-depot-macos` entries), not from GitHub "Latest".
macOS: app-shell's `UpdaterKit` gets a catalog feed source next to its GitHub one (value-injected, per
the app-shell rule); other apps keep the GitHub feed. Tags `android-vX.Y.Z` / `macos-vX.Y.Z`; neither
needs to be marked Latest. *(Claude's call.)*

### 4. Hub repo skills and rules

- Rule `.claude/rules/app-depot.md`: which apps are in which hub, the catalog fields, "a release isn't
  done until the catalog has it", how office releases queue.
- Skill `depot-publish`: reads a project's latest release (tag, notes, asset, sha256, size) and POSTs it
  (and `PATCH`es app metadata when name/tagline/icon changed). Admin token from
  `~/.config/app-depot/admin-token`, never committed.
- `release-project`, `ship-check`, `sync-project`: call `depot-publish` after the GitHub release exists.
  Office (`PUBLISH=0`): nothing to publish (asset not on GitHub), so `ship-check` publishes it at home.
- `add-project`: registers the `DepotApp` (and links the `Project`). Edits to name/tagline/icon go through
  `depot-publish --meta`.
- `projects-status`: adds a "catalog" column (catalog version vs latest tag) to catch drift.
- Personal Wiki (outside `projects/`): its `android-release.yml` POSTs the release to the catalog after
  publishing, with an `APPDEPOT_ADMIN_TOKEN` GitHub secret (download goes through the private-repo
  redirect above).

### 5. Mini-app tracking (phase 2)

Apps report `open` / `crash` / running version. Android: to App Depot through a signature-protected
`ContentProvider` (`knownSigner` permission listing the apps' cert digests); macOS: a small shared
helper writing to App Depot via a distributed notification / XPC. App Depot forwards to the server. Details
designed when phase 1 ships.

## Phases

1. Server + catalog + admin page + site data; `depot-publish` + rule; backfill current releases.
2. Android App Depot (the owner's main pain) + Wiki CI change.
3. macOS App Depot + app-shell installer changes.
4. Mini-app tracking. 5. Personal Wiki desktop (macOS) in the macOS App Depot — owner said yes, low
   priority (private repo → same download redirect; it keeps its Tauri updater). 6. Widget (today's tasks / recent notes), single sign-on, backup.

## Repos

- New repo `vuhpquang/app-depot` (submodule `projects/app-depot`): `android/` and `macos/`, tags
  `android-vX.Y.Z` / `macos-vX.Y.Z`, own `release.sh` per platform. Push policy: allowed on `main`
  (Claude's call, like zentask — confirm). Repo is public (already created, empty).
- Ids: Android `com.vuhpquang.appdepot`, macOS bundle `com.vuhpquang.appdepot`, display name
  "App Depot", own self-signed identity "App Depot Self-Signed" (macOS) and release keystore (Android).
- Portfolio changes in `projects/portfolio` (push = deploy → ask each time).

## Open

- Icon and accent colour (own identity, generated by the project's icon script).

## Left to the builder

Exact UI layout (follow `.claude/rules/design.md`), table/index details, WorkManager constraints,
pairing UX details, event batching size.
