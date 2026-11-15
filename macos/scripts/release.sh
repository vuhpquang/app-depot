#!/usr/bin/env bash
# Release App Depot (macOS): tests, signed universal build, signer check, tag macos-vX.Y.Z, GitHub release
# (NOT marked Latest: one repo, two platforms; both App Depots update from the catalog), optional install.
#
#   macos/scripts/release.sh X.Y.Z notes.md            # push main + tag, GitHub release
#   INSTALL=1 macos/scripts/release.sh X.Y.Z notes.md  # also install /Applications/App Depot.app and open it
#   PUBLISH=0 INSTALL=1 macos/scripts/release.sh ...   # office: commit + tag locally, install, no push
#   DRY_RUN=1 macos/scripts/release.sh X.Y.Z notes.md  # build + verify only, nothing committed
#
# Branch: `main`, or any branch whose HEAD is exactly origin/main (e.g. a worktree branch while main is
# checked out elsewhere); the release commit is pushed to main.
# Afterwards publish to the catalog from the hub: scripts/depot-publish.sh app-depot-macos
set -euo pipefail

MAC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$MAC_DIR/.." && pwd)"
REPO="vuhpquang/app-depot"
BRANCH="main"
PUBLISH="${PUBLISH:-1}"
DRY_RUN="${DRY_RUN:-0}"
IDENTITY="App Depot Self-Signed"
APP="$MAC_DIR/build/App Depot.app"
PLIST="$MAC_DIR/Sources/AppDepot/Resources/Info.plist"

die()  { echo "release: $*" >&2; exit 1; }
step() { echo; echo "==> $*"; }

VERSION="${1:-}"
NOTES_FILE="${2:-}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "usage: macos/scripts/release.sh X.Y.Z notes.md"
[[ -f "$NOTES_FILE" ]] || die "notes file not found: ${NOTES_FILE:-<missing>}"
NOTES_FILE="$(cd "$(dirname "$NOTES_FILE")" && pwd)/$(basename "$NOTES_FILE")"
TAG="macos-v${VERSION}"
ZIP="$MAC_DIR/build/AppDepot-${VERSION}-macos.zip"
EXPECTED_DR="$(head -n 1 "$MAC_DIR/scripts/release-requirement.txt")"
IFS=. read -r MAJ MIN PAT <<< "$VERSION"
BUILD_NUMBER=$((MAJ * 10000 + MIN * 100 + PAT))

step "Preflight"
cd "$REPO_ROOT"
[[ -z "$(git status --porcelain)" ]] || die "working tree not clean"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists"
CURRENT_BRANCH="$(git branch --show-current)"
if [[ "$PUBLISH" != "0" && "$DRY_RUN" != "1" ]]; then
    gh auth status >/dev/null 2>&1 || die "gh is not logged in"
    git fetch -q origin "$BRANCH" --tags
    git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists on origin"
    [[ "$(git rev-parse HEAD)" == "$(git rev-parse "origin/$BRANCH")" ]] \
        || die "HEAD is not origin/$BRANCH (on '$CURRENT_BRANCH'; pull, push or rebase first)"
else
    [[ "$CURRENT_BRANCH" == "$BRANCH" || "$(git rev-parse HEAD)" == "$(git rev-parse "origin/$BRANCH" 2>/dev/null)" ]] \
        || die "release from '$BRANCH' or a branch at origin/$BRANCH"
fi
security find-identity -p codesigning | grep -q "\"$IDENTITY\"" \
    || die "signing identity '$IDENTITY' not in the keychain (macos/scripts/make-signing-identity.sh, or import ~/.config/app-depot-macos-signing/)"

CURRENT="$(tr -d '[:space:]' < "$MAC_DIR/VERSION")"
newest="$(printf '%s\n%s\n' "$CURRENT" "$VERSION" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)"
[[ "$newest" == "$VERSION" ]] || die "$VERSION is older than $CURRENT"
# The very first release may equal VERSION (no macos tag yet); later ones must be newer.
if [[ "$VERSION" == "$CURRENT" ]] && git tag -l 'macos-v*' | grep -q .; then
    die "$VERSION must be newer than $CURRENT"
fi

COMMITTED=0
undo() { [[ "$COMMITTED" == "1" ]] || git checkout -q -- macos/VERSION "$PLIST" 2>/dev/null || true; }
trap undo EXIT

step "Bumping to $VERSION (build $BUILD_NUMBER)"
echo "$VERSION" > "$MAC_DIR/VERSION"
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION" "$PLIST"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $BUILD_NUMBER" "$PLIST"

step "Tests"
cd "$MAC_DIR"
git submodule update --init --recursive Vendor/app-shell
swift test >/dev/null 2>&1 || die "tests failed (run 'swift test' in macos/)"
echo "    swift test passed"

step "Building"
SIGN_IDENTITY="$IDENTITY" ./build.sh

step "Verifying the release"
codesign --verify --deep --strict "$APP"
built="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist")"
[[ "$built" == "$VERSION" ]] || die "bundle says $built, expected $VERSION"
[[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP/Contents/Info.plist")" == "com.vuhpquang.appdepot" ]] || die "wrong bundle id"
DR="$(codesign -d -r- "$APP" 2>&1 | sed -n 's/^designated => //p')"
[[ "$DR" == "$EXPECTED_DR" ]] || die "designated requirement differs from scripts/release-requirement.txt:
    got      $DR
    expected $EXPECTED_DR
installed copies (and the catalog signer) would reject this build"
archs="$(lipo -archs "$APP/Contents/MacOS/AppDepot")"
[[ "$archs" == *arm64* && "$archs" == *x86_64* ]] || die "not universal: $archs"
[[ -f "$ZIP" ]] || die "missing $ZIP"
echo "    signer OK ($IDENTITY), universal, $(basename "$ZIP") sha256 $(shasum -a 256 "$ZIP" | awk '{print $1}')"

if [[ "$DRY_RUN" == "1" ]]; then
    step "Dry run: stopping before commit; bump reverted. Artifacts are in macos/build/."
    exit 0
fi

step "Committing and tagging $TAG"
cd "$REPO_ROOT"
git add macos/VERSION "$PLIST"
if ! git diff --cached --quiet; then
    git commit -q -m "release: $TAG

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fi
COMMITTED=1
trap - EXIT
git tag -a "$TAG" -m "App Depot (macOS) $VERSION"

if [[ "$PUBLISH" != "0" ]]; then
    step "Pushing and creating GitHub release $TAG"
    git push -q origin "HEAD:$BRANCH" "$TAG"
    gh release create "$TAG" "$ZIP" -R "$REPO" --title "App Depot (macOS) $VERSION" \
        --notes-file "$NOTES_FILE" --latest=false
    echo "    https://github.com/$REPO/releases/tag/$TAG"
fi

if [[ "${INSTALL:-0}" == "1" ]]; then
    step "Installing /Applications/App Depot.app"
    # Only App Depot itself is touched: quit a running copy of it (by bundle id), replace, open.
    osascript -e 'quit app id "com.vuhpquang.appdepot"' >/dev/null 2>&1 || true
    sleep 1
    rm -rf "/Applications/App Depot.app"
    ditto "$APP" "/Applications/App Depot.app"
    xattr -dr com.apple.quarantine "/Applications/App Depot.app" 2>/dev/null || true
    open "/Applications/App Depot.app"
fi

step "Released App Depot (macOS) $VERSION"
[[ "$PUBLISH" != "0" ]] || echo "    local only: commit + tag $TAG not pushed, no GitHub release yet"
echo "    Catalog: from the hub run  scripts/depot-publish.sh app-depot-macos"
