# App Depot — Phase 1: catalog server + hub skills — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** vuhpquang.com holds the single catalog of the owner's apps and releases (read by both App Depots and by the site), ingests device events, has a private `/admin/depot` page, and the hub's skills publish every release to it.

**Architecture:** New Prisma models in the portfolio's Supabase DB; Route Handlers under `src/app/api/depot/*` (public/device) and `src/app/api/admin/depot/*` (admin bearer); pure logic in `src/lib/depot/*` (unit-tested); server components for `/apps`, the project-page release block and `/admin/depot`. In the hub, a `depot-publish` skill backed by `scripts/depot-publish.sh` (gh + curl) is called by `release-project`, `ship-check`, `sync-project`, `add-project`.

**Tech Stack:** Next.js 16.2 (read `node_modules/next/dist/docs/01-app` for Route Handlers, caching/`revalidate`, cookies before writing code — this Next has breaking changes), Prisma 7 (`prisma-client` generator, output `src/generated/prisma`), Supabase Postgres, Vitest (Prisma mocked like `src/__tests__/integration/api-admin-projects.test.ts`), bash + `gh` + `curl` + `jq` in the hub.

**Spec:** `docs/superpowers/specs/2026-11-15-app-depot-design.md` (§1 server, §4 hub skills).

## Global Constraints

- All portfolio work in `projects/portfolio`, branch `master`. **Pushing deploys to Vercel → ask the owner before `git push`.** Commit freely (personal email, conventional messages, Claude co-author line).
- Response envelope: existing `ok/created/badRequest/unauthorized/notFound/serverError` from `src/lib/api.ts`. Admin auth: existing `requireAdmin` (`ADMIN_SECRET`).
- Tables `@@map` snake_case, columns `@map` snake_case, like `projects`.
- Public pages: light-only notebook palette, CSS custom properties from `globals.css` only, lucide icons, **every string EN + VI** in `src/data/strings.ts`, works from 360 px. `/admin/depot` is English-only, `robots: noindex`.
- Never put tokens in a response, a log or the repo. Device tokens stored as SHA-256 hex only. New env vars: `GITHUB_RELEASES_TOKEN` (fine-grained, Contents: read on `vuhpquang/personal-wiki`, `vuhpquang/devkit`), `DEPOT_SESSION_SECRET` (cookie HMAC). Add to `.env.example` (empty values).
- Latest release = highest semver among an app's releases, never insertion/`publishedAt` order.
- Event types (closed list): `check`, `install`, `update`, `install_failed`, `open`, `crash`, `depot_started`, `self_update`. Events older than 180 days are deleted.
- Catalog `installMode`: `managed` | `selfUpdates` | `none`. `platform`: `android` | `macos`.
- Hub: admin token read from `~/.config/app-depot/admin-token` (chmod 600), never echoed. Base URL `https://vuhpquang.com` (override `DEPOT_URL` for local `http://localhost:3000`).

## Review Focus

1. Versions like `1.10.0` vs `1.9.3`, `0.15.2` vs `0.15.10`, and tag prefixes (`v`, `wisp-v`, `android-v`) — latest must be numeric-correct and prefixes stripped → tests in Task 1 and Task 7.
2. Re-publishing the same release (skill re-run, ship-check after office) must update, not duplicate or 500 → Task 2 upsert test.
3. App with no releases yet (DevKit) appears in the catalog with `latest: null` and the site shows "No release yet", not a crash → Task 3 + Task 6 tests.
4. Event batch from a device that is unpaired/revoked, oversized (> 200) or with an unknown type → 401 / 400, nothing written → Task 4 tests.
5. Private-repo download when GitHub token is missing or the asset is gone → 502 with a readable error, never a redirect to an API URL that would leak or fail silently → Task 3 test.

---

### Task 1: Depot schema + pure helpers

**Files:**
- Modify: `prisma/schema.prisma` (append models)
- Create: `prisma/migrations/<timestamp>_app_depot/migration.sql` (via `prisma migrate dev --create-only --name app_depot`)
- Create: `src/lib/depot/version.ts`, `src/lib/depot/types.ts`
- Test: `src/__tests__/unit/depot-version.test.ts`

**Interfaces:**
- Produces (Prisma, client names `depotApp`, `depotRelease`, `depotDevice`, `depotEvent`, `depotPairingCode`):
  - `DepotApp { id cuid, slug unique, name, tagline, platform DepotPlatform, appId, repo, privateRepo Bool @default(false), iconUrl?, projectSlug?, signer?, relaunchArgs String[] @default([]), installMode DepotInstallMode @default(managed), order Int @default(0), enabled Bool @default(true), createdAt, updatedAt, releases DepotRelease[] }` — table `depot_apps`
  - `DepotRelease { id, appId → DepotApp (onDelete Cascade), version, build?, notes Text, assetName, downloadUrl, assetApiUrl?, sha256, size Int, publishedAt, minOs?, createdAt, updatedAt, @@unique([appId, version]) }` — `depot_releases`
  - `DepotDevice { id String @id (client UUID), name, platform DepotPlatform, depotVersion, tokenHash unique, revoked Bool @default(false), lastSeenAt, createdAt }` — `depot_devices`
  - `DepotEvent { id, deviceId → DepotDevice (Cascade), appSlug?, type, fromVersion?, toVersion?, message? Text, at DateTime, createdAt, @@index([at]), @@index([deviceId, at]) }` — `depot_events`
  - `DepotPairingCode { code String @id (8 chars), expiresAt, usedAt?, createdAt }` — `depot_pairing_codes`
  - enums `DepotPlatform { android macos }`, `DepotInstallMode { managed selfUpdates none }`
- Produces (TS): `stripTagPrefix(tag: string): string` (`"wisp-v0.3.2"→"0.3.2"`, `"android-v0.1.11"→"0.1.11"`, `"v2.5.0"→"2.5.0"`), `compareVersions(a: string, b: string): number` (numeric per dot segment, missing segment = 0), `latestRelease<T extends { version: string }>(rs: T[]): T | null`, `EVENT_TYPES` readonly tuple + `type DepotEventType`, `isEventType(x: unknown): x is DepotEventType`. `types.ts`: `CatalogApp`, `CatalogRelease`, `CatalogResponse` (shape in Task 3).

- [x] **Step 1: Write the failing tests** — `compareVersions("1.10.0","1.9.3") > 0`, `compareVersions("0.15.10","0.15.2") > 0`, `compareVersions("1.0","1.0.0") === 0`; `stripTagPrefix` three cases above; `latestRelease([{version:"0.15.2"},{version:"0.15.10"},{version:"0.9.0"}]).version === "0.15.10"`; `latestRelease([]) === null`; `isEventType("crash")` true, `isEventType("boom")` false.
- [x] **Step 2: Run** `npx vitest run src/__tests__/unit/depot-version.test.ts` → FAIL (module not found).
- [x] **Step 3: Implement** `version.ts` / `types.ts`; add the models; `npx prisma migrate dev --create-only --name app_depot`, review the SQL is additive only (CREATE TYPE/TABLE/INDEX, no ALTER/DROP on `projects`/`about`); `npx prisma generate`.
- [x] **Step 4: Run** the unit test → PASS; `npx tsc --noEmit` → no errors; `npm test` → all pass.
- [x] **Step 5: Commit** `feat(depot): add App Depot schema and version helpers`.

### Task 2: Admin API — apps, releases, pairing codes

**Files:**
- Create: `src/app/api/admin/depot/apps/route.ts` (GET list, POST create), `src/app/api/admin/depot/apps/[slug]/route.ts` (PATCH), `src/app/api/admin/depot/releases/route.ts` (POST upsert), `src/app/api/admin/depot/pairing-codes/route.ts` (POST)
- Create: `src/lib/depot/validate.ts`
- Test: `src/__tests__/integration/api-admin-depot.test.ts`

**Interfaces:**
- Consumes: Task 1 models, `requireAdmin`.
- Produces:
  - `POST /api/admin/depot/apps` body `{ slug, name, tagline, platform, appId, repo, privateRepo?, iconUrl?, projectSlug?, signer?, relaunchArgs?, installMode?, order?, enabled? }` → 201 app; 409 on duplicate slug.
  - `PATCH /api/admin/depot/apps/[slug]` partial of the same (not `slug`) → 200; 404 unknown.
  - `POST /api/admin/depot/releases` body `{ app: slug, version (prefix stripped server-side too), build?, notes, assetName, downloadUrl, assetApiUrl?, sha256 (64 hex), size (>0), publishedAt (ISO), minOs? }` → upsert on (appId, version) → 200 `{ release, created: boolean }`; 404 unknown app.
  - `POST /api/admin/depot/pairing-codes` → 201 `{ code, expiresAt }` (8 chars `A-Z2-9`, 15 min).
  - `validate.ts`: `parseAppInput(body: unknown, partial: boolean): { ok: true; value } | { ok: false; error: string }`, `parseReleaseInput(body: unknown)` same result type.

- [x] **Step 1: Failing tests** — each route: 401 without/with wrong bearer; apps POST 400 on bad `platform` ("ios") and on missing `appId`, 201 happy, 409 when `findUnique` returns existing; PATCH 404; releases POST: 400 on `sha256` not 64-hex, 400 on `size: 0`, `version: "v1.2.0"` stored as `"1.2.0"`, upsert called with `where: { appId_version: … }`, second identical POST returns 200 `created: false` (mock `findUnique` existing); pairing code matches `/^[A-Z2-9]{8}$/` and `expiresAt` ≈ now + 15 min.
- [x] **Step 2: Run** `npx vitest run src/__tests__/integration/api-admin-depot.test.ts` → FAIL.
- [x] **Step 3: Implement** routes + validators (Prisma `upsert`; `created` = whether a row existed before, via `findUnique` first).
- [x] **Step 4: Run** → PASS; `npm test`, `npx tsc --noEmit`.
- [x] **Step 5: Commit** `feat(depot): admin API for apps, releases and pairing codes`.

### Task 3: Public catalog + private download redirect

**Files:**
- Create: `src/lib/depot/catalog.ts`, `src/app/api/depot/catalog/route.ts`, `src/app/api/depot/download/[slug]/[version]/route.ts`
- Test: `src/__tests__/unit/depot-catalog.test.ts`, `src/__tests__/integration/api-depot-public.test.ts`

**Interfaces:**
- Produces:
  - `getCatalog(platform?: "android" | "macos"): Promise<CatalogResponse>` — enabled apps ordered by `order`, each with `latest` from `latestRelease`. For `privateRepo` apps, `latest.downloadUrl` = `${origin}/api/depot/download/${slug}/${version}` with `origin = process.env.DEPOT_PUBLIC_ORIGIN ?? "https://vuhpquang.com"`. Never exposes `assetApiUrl`.
  - `CatalogResponse = { generatedAt: string; apps: CatalogApp[] }`, `CatalogApp = { slug, name, tagline, platform, appId, repo, iconUrl, projectSlug, signer, relaunchArgs, installMode, latest: CatalogRelease | null }`, `CatalogRelease = { version, build, notes, assetName, downloadUrl, sha256, size, publishedAt, minOs }`.
  - `GET /api/depot/catalog?platform=` → `ok(CatalogResponse)` with header `Cache-Control: public, s-maxage=300, stale-while-revalidate=600`; 400 on other platform values.
  - `GET /api/depot/download/[slug]/[version]` → for private apps: `fetch(assetApiUrl, { headers: { Authorization: "Bearer " + GITHUB_RELEASES_TOKEN, Accept: "application/octet-stream" }, redirect: "manual" })`, respond `302` to its `Location`; public apps → `302` to stored `downloadUrl`. 404 unknown app/version; 502 `"Download unavailable: …"` when token missing, GitHub non-302, or no `Location`.

- [x] **Step 1: Failing tests** — `getCatalog`: app with releases `0.15.2`/`0.15.10` → `latest.version "0.15.10"`; app with no releases → `latest: null`; private app → `downloadUrl` starts `https://vuhpquang.com/api/depot/download/personal-wiki-android/`; disabled app omitted; response JSON has no `assetApiUrl` key. Route: `platform=ios` → 400; cache header present. Download: private + mocked `fetch` 302 `Location: https://objects.githubusercontent.com/x` → 302 same; token unset → 502; GitHub 404 → 502; unknown version → 404.
- [x] **Step 2: Run** both test files → FAIL.
- [x] **Step 3: Implement.**
- [x] **Step 4: Run** → PASS; `npm test`.
- [x] **Step 5: Commit** `feat(depot): public catalog and private-repo download redirect`.

### Task 4: Device pairing + event ingest

**Files:**
- Create: `src/lib/depot/devices.ts`, `src/app/api/depot/devices/route.ts`, `src/app/api/depot/events/route.ts`
- Test: `src/__tests__/integration/api-depot-devices.test.ts`

**Interfaces:**
- Produces:
  - `hashToken(token: string): string` (SHA-256 hex, `node:crypto`), `newDeviceToken(): string` (32 random bytes base64url), `authenticateDevice(req: NextRequest): Promise<DepotDevice | null>` (bearer → `tokenHash` lookup, not revoked; bumps `lastSeenAt`).
  - `POST /api/depot/devices` body `{ code, deviceId (uuid), name, platform, depotVersion }` → code must exist, unused, unexpired → mark used, upsert device (re-pairing same `deviceId` rotates token, clears `revoked`) → 201 `{ deviceId, token }`. 400 invalid body, 403 bad/used/expired code.
  - `POST /api/depot/events` bearer device token, body `{ events: [{ type, appSlug?, fromVersion?, toVersion?, message? (≤ 2000 chars, truncated), at (ISO) }] }`, 1–200 events → `createMany` → 201 `{ accepted: n }`; then `deleteMany({ where: { at: { lt: now − 180d } } })`. 401 no/unknown/revoked token, 400 empty/oversized/unknown type (whole batch rejected).

- [x] **Step 1: Failing tests** — pairing: valid → 201 and stored `tokenHash === hashToken(returned token)`; used code → 403; expired → 403. Events: no auth 401; revoked device 401; 201 events → 400; `type: "boom"` → 400 and `createMany` not called; valid batch → 201 `accepted` = length and `deleteMany` called with cutoff ≈ now − 180 d; message of 5000 chars stored as 2000.
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement.**
- [x] **Step 4: Run** → PASS; `npm test`.
- [x] **Step 5: Commit** `feat(depot): device pairing and event ingest`.

### Task 5: `/admin/depot` page

**Files:**
- Create: `src/lib/depot/session.ts`, `src/app/api/admin/depot/session/route.ts` (POST login, DELETE logout), `src/app/admin/depot/page.tsx`, `src/app/admin/depot/login/page.tsx`, `src/app/admin/depot/actions.ts` (server action: create pairing code, revoke device), `src/app/admin/depot/admin.module.css` if needed (tokens only)
- Test: `src/__tests__/unit/depot-session.test.ts`

**Interfaces:**
- Produces: `signSession(expiresAt: number, secret: string): string`, `verifySession(value: string | undefined, secret: string, now: number): boolean` (HMAC-SHA256, `timingSafeEqual`), cookie `depot_session`, httpOnly, secure, sameSite strict, 30 days. Login POST `{ password }` compared to `ADMIN_SECRET` (constant-time) → sets cookie → 204; 401 otherwise.
- Page (server component, `export const dynamic = "force-dynamic"`, `metadata.robots = { index: false, follow: false }`): redirects to `/admin/depot/login` without a valid cookie. Sections: **Apps** (name, platform, latest version + date, installMode, enabled), **Devices** (name, platform, App Depot version, last seen, Revoke), **Events** (last 200, filters `?device=&app=&type=` via search params, newest first), **Pair a device** (button → shows code + expiry).

- [x] **Step 1: Failing tests** — sign→verify true; tampered value false; expired false; wrong secret false; `undefined` false.
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement** session lib, route, pages, actions.
- [x] **Step 4: Verify** `npm test`; `npm run build` succeeds; `npm run dev` against the DB → log in, see apps after Task 8 backfill (or empty state "No apps yet"), create a pairing code; screenshot the page (light only).
- [x] **Step 5: Commit** `feat(depot): private admin page for catalog, devices and events`.

### Task 6: Site shows catalog data

**Files:**
- Create: `src/app/apps/page.tsx`, `src/components/depot/AppReleaseCard.tsx`, `src/components/depot/ReleaseNotes.tsx` (minimal Markdown: `##` headings + `- ` bullets → elements, no HTML injection)
- Modify: `src/app/projects/[slug]/page.tsx` (fetch matching catalog apps by `projectSlug`, pass down), `src/components/ProjectDetail.tsx` (render "Latest release" block when given), `src/data/strings.ts` (EN+VI keys under `depot`), `src/app/sitemap.ts` (+ `/apps`), header/nav component that lists sections (add "Apps" link, EN "Apps" / VI "Ứng dụng")
- Test: `src/__tests__/unit/depot-release-notes.test.ts`, extend `src/__tests__/unit/depot-catalog.test.ts`

**Interfaces:**
- Consumes: `getCatalog()` (Task 3). Pages use `export const revalidate = 300`.
- Produces: `parseReleaseNotes(md: string): Array<{ kind: "heading"; text: string } | { kind: "bullet"; text: string } | { kind: "text"; text: string }>`; `AppReleaseCard({ app: CatalogApp })`.
- Copy (EN / VI): "Apps" / "Ứng dụng"; "Every app I build, with the latest version" / "Tất cả ứng dụng tôi làm, kèm phiên bản mới nhất"; "Version {v} · {date}" / "Phiên bản {v} · {date}"; "Download for macOS" / "Tải cho macOS"; "Download for Android" / "Tải cho Android"; "What's new" / "Có gì mới"; "No release yet" / "Chưa có bản phát hành"; "Latest release" / "Bản phát hành mới nhất". Dates via `Intl.DateTimeFormat(language === "vi" ? "vi-VN" : "en-GB", { dateStyle: "medium" })`.
- `/apps`: grouped macOS then Android, card per app (icon if `iconUrl`, name, tagline, version line, download button, collapsible notes). The DB being unreachable must not break the page: catch → render the empty state.

- [x] **Step 1: Failing tests** — `parseReleaseNotes("## What's new\n- A\n- B\n## Fixes\n- C")` → 5 items in order; `<script>` text returned as plain text item (no HTML parsing); `getCatalog` rejection path is handled by a `safeGetCatalog()` returning `{ apps: [] }` (add to `catalog.ts`, test it).
- [x] **Step 2: Run** → FAIL.
- [x] **Step 3: Implement.**
- [x] **Step 4: Verify** `npm test`, `npm run lint`, `npm run build`; `npm run dev` → `/apps` and `/projects/personal-wiki` at 360 px and desktop, EN and VI; screenshots.
- [x] **Step 5: Commit** `feat(depot): show catalog apps and latest releases on the site`.

### Task 7: Hub — `depot-publish` script, skill, rule, hooks

**Files (hub repo):**
- Create: `scripts/depot-publish.sh`, `scripts/test-depot-publish.sh`, `.claude/skills/depot-publish/SKILL.md`, `.claude/rules/app-depot.md`, `docs/app-depot-apps.json` (the registry of app metadata the script sends with `--meta`)
- Modify: `.claude/skills/release-project/SKILL.md`, `.claude/skills/ship-check/SKILL.md`, `.claude/skills/sync-project/SKILL.md`, `.claude/skills/add-project/SKILL.md`, `.claude/skills/projects-status/SKILL.md`, `CLAUDE.md` (skills list + rules line)

**Interfaces:**
- `scripts/depot-publish.sh <app-slug> [--tag <tag>] [--meta] [--dry-run]`:
  - Reads `docs/app-depot-apps.json` entry for `<app-slug>`: `{ slug, name, tagline, platform, appId, repo, privateRepo, assetPattern (regex), tagPattern (regex, e.g. "^v" / "^android-v"), iconUrl?, projectSlug?, signer?, relaunchArgs?, installMode, order }`.
  - `--meta`: `POST /api/admin/depot/apps`, on 409 `PATCH …/apps/<slug>` with the entry (minus `assetPattern`, `tagPattern`).
  - Release: tag = `--tag` or newest `gh release list -R <repo>` tag matching `tagPattern` (non-draft); asset = first matching `assetPattern`; `gh release download` to a temp dir → `shasum -a 256`, size; notes = release body; publishedAt; `downloadUrl` = asset `browser_download_url`, `assetApiUrl` = asset API `url`; POST `/api/admin/depot/releases`. Prints one line: `depot: <slug> <version> published (created|updated)`. Exit non-zero with a clear message on: no token file, no matching tag/asset, HTTP ≠ 2xx.
  - `--dry-run`: prints the JSON it would send (token never printed).
  - Pure helpers inside (`version_from_tag`, `pick_asset`) testable by `scripts/test-depot-publish.sh` with fixtures (no network).
- Initial registry entries: `padlet` (macos, `com.vuhpquang.padlet`? → read real id from Info.plist), `launch-app`, `quickcast`, `wisp` (`installMode: selfUpdates`, tagPattern `^wisp-v`), `devkit` (`none`, private), `zentask-macos` (asset `^ZenTask-[0-9.]+\.zip$`), `zentask-android` (asset `-android\.apk$`), `personal-wiki-android` (private, tagPattern `^android-v`, asset `\.apk$`, `projectSlug: personal-wiki`), `padlet` → `projectSlug: gesturekit`. Bundle ids/package names taken from each project's Info.plist / `build.gradle.kts` / `tauri.conf.json`, not guessed. `signer`: macOS `codesign -d -r- /Applications/<App>.app` designated requirement; Android `apksigner verify --print-certs` SHA-256 of the latest release APK (fill what's available; empty stays null, App Depot then relies on Android's own same-key check for updates).
- Skill hooks (one short step each): `release-project` → after a published release: `scripts/depot-publish.sh <slug>` (office `PUBLISH=0`: skip, note "catalog pending"); `ship-check` → after pushing an office release: publish it; add a row check "catalog version = latest tag"; `sync-project` → after release: publish; `add-project` → add registry entry + `--meta`; `projects-status` → "Catalog" column from `GET /api/depot/catalog`.
- Rule `app-depot.md`: which apps are in which App Depot, "a release is done only when `depot-publish` printed published", registry file is the source for metadata (edit it + `--meta` when name/tagline/icon change), office flow, token location, never print the token.

- [x] **Step 1: Failing test** `scripts/test-depot-publish.sh`: `version_from_tag wisp-v0.3.2` → `0.3.2`; `version_from_tag android-v0.1.11` → `0.1.11`; `pick_asset '-android\.apk$'` over `ZenTask-0.15.2-android.apk ZenTask-0.15.2-wear.apk ZenTask-0.15.2.zip` → the android APK; `pick_asset '^ZenTask-[0-9.]+\.zip$'` → the zip.
- [x] **Step 2: Run** `bash scripts/test-depot-publish.sh` → FAIL.
- [x] **Step 3: Implement** script, registry, skill, rule, hooks, CLAUDE.md lines.
- [x] **Step 4: Run** the test → PASS; `scripts/depot-publish.sh zentask-android --dry-run` prints JSON with `version: "0.15.2"`, 64-hex sha256, no token.
- [x] **Step 5: Commit** (hub) `feat: depot-publish skill and app-depot rule`, then `npx gitnexus analyze`.

### Task 8: Migrate, deploy, backfill

- [x] **Step 1:** In portfolio, `npx prisma migrate deploy` against the Supabase DB from `.env` (additive tables only, reviewed in Task 1). Expected: "1 migration applied".
- [x] **Step 2:** Ask the owner to approve `git push` of portfolio (deploy). Set Vercel env `GITHUB_RELEASES_TOKEN`, `DEPOT_SESSION_SECRET` (owner creates the GitHub token; Claude can set Vercel env with `vercel env add` if the CLI is logged in, otherwise give the owner the exact steps). Push; wait for the deploy; `curl -s https://vuhpquang.com/api/depot/catalog | jq '.data.apps | length'` → `0`.
- [x] **Step 3:** Owner puts the admin secret in `~/.config/app-depot/admin-token` (Claude may copy it from `projects/portfolio/.env` `ADMIN_SECRET` with `chmod 600`, without printing). For each registry slug: `scripts/depot-publish.sh <slug> --meta` then `scripts/depot-publish.sh <slug>` (skip DevKit release: none yet).
- [x] **Step 4: Verify** `curl …/api/depot/catalog?platform=android | jq '.data.apps[] | {slug, v: .latest.version}'` → `zentask-android 0.15.2`, `personal-wiki-android 0.1.11`; macOS lists Padlet 2.5.0, LaunchApp 1.5.1, Quickcast 1.4.1, Wisp 0.3.2, ZenTask 0.15.2, DevKit `null`. `curl -sI …/api/depot/download/personal-wiki-android/0.1.11` → `302` to `objects.githubusercontent.com`. `/apps` renders on the live site.
- [x] **Step 5:** Hub: bump `projects/portfolio` pointer, commit `chore: bump portfolio submodule`, `npx gitnexus analyze`. Update portfolio `README`/`CLAUDE.md` (depot API + env vars) in the portfolio commit before pushing.
