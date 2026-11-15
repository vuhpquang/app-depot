#!/usr/bin/env bash
# Release App Depot (Android): test, build the signed APK, verify its signer, tag, GitHub release.
#
#   android/scripts/release.sh X.Y.Z notes.md          # push main + tag, GitHub release (not Latest)
#   PUBLISH=0 android/scripts/release.sh X.Y.Z notes.md  # commit + tag locally only (office)
#
# Afterwards publish to the catalog from the hub: scripts/depot-publish.sh app-depot-android
set -euo pipefail

ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ANDROID_DIR/.." && pwd)"
REPO="vuhpquang/app-depot"
BRANCH="main"
PUBLISH="${PUBLISH:-1}"
export JAVA_HOME="${JAVA_HOME:-/Users/quangvu/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

die() { echo "release: $*" >&2; exit 1; }
step() { echo "==> $*"; }

VERSION="${1:-}"
NOTES_FILE="${2:-}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "usage: scripts/release.sh X.Y.Z notes.md"
[[ -f "$NOTES_FILE" ]] || die "notes file not found: ${NOTES_FILE:-<missing>}"
NOTES_FILE="$(cd "$(dirname "$NOTES_FILE")" && pwd)/$(basename "$NOTES_FILE")"
TAG="android-v${VERSION}"
APK_OUT="$ANDROID_DIR/build/AppDepot-${VERSION}-android.apk"
EXPECTED="$(tr -d '[:space:]' < "$ANDROID_DIR/scripts/release-signer.sha256")"
[[ "$EXPECTED" =~ ^[0-9a-f]{64}$ ]] || die "scripts/release-signer.sha256 is not a SHA-256 hex digest"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
[[ -x "$APKSIGNER" ]] || die "apksigner not found under $SDK/build-tools"

step "Preflight"
cd "$REPO_ROOT"
# Branch: `main`, or any branch whose HEAD is exactly origin/main (a worktree branch while main is checked out elsewhere).
CURRENT_BRANCH="$(git branch --show-current)"
[[ -z "$(git status --porcelain)" ]] || die "working tree not clean"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists"
if [[ "$PUBLISH" != "0" ]]; then
  gh auth status >/dev/null 2>&1 || die "gh is not logged in"
  git fetch -q origin "$BRANCH" --tags
  [[ "$(git rev-parse HEAD)" == "$(git rev-parse "origin/$BRANCH")" ]] \
    || die "HEAD is not origin/$BRANCH (on '$CURRENT_BRANCH'; pull, push or rebase first)"
else
  [[ "$CURRENT_BRANCH" == "$BRANCH" || "$(git rev-parse HEAD)" == "$(git rev-parse "origin/$BRANCH" 2>/dev/null)" ]] \
    || die "release from '$BRANCH' or a branch at origin/$BRANCH"
fi

CURRENT="$(tr -d '[:space:]' < "$ANDROID_DIR/VERSION")"
newest="$(printf '%s\n%s\n' "$CURRENT" "$VERSION" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)"
[[ "$newest" == "$VERSION" ]] || die "$VERSION is older than $CURRENT"
# The very first release is allowed to equal VERSION (no tag exists yet); later ones must be newer.
if [[ "$VERSION" == "$CURRENT" ]] && git tag -l 'android-v*' | grep -q .; then
  die "$VERSION must be newer than $CURRENT"
fi

undo() { git checkout -q -- android/VERSION 2>/dev/null || true; }
trap undo EXIT

step "Bumping VERSION to $VERSION"
echo "$VERSION" > "$ANDROID_DIR/VERSION"

step "Tests and release build"
cd "$ANDROID_DIR"
./gradlew -q :app:testDebugUnitTest :app:assembleRelease
BUILT="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
[[ -f "$BUILT" ]] || die "release APK not found at $BUILT"

step "Verifying the signer"
ACTUAL="$("$APKSIGNER" verify --print-certs "$BUILT" | sed -n 's/^.*certificate SHA-256 digest: //p' | head -n 1 | tr -d '[:space:]')"
[[ "$ACTUAL" == "$EXPECTED" ]] || die "APK signer $ACTUAL differs from the App Depot release cert $EXPECTED (is ~/.config/app-depot-signing present?)"
mkdir -p "$ANDROID_DIR/build"
cp "$BUILT" "$APK_OUT"
echo "    signer OK, $APK_OUT"

step "Committing and tagging $TAG"
cd "$REPO_ROOT"
git add android/VERSION
if ! git diff --cached --quiet; then
  git commit -q -m "release: $TAG

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
else
  echo "    VERSION already $VERSION, no release commit needed"
fi
trap - EXIT
git tag -a "$TAG" -m "App Depot (Android) $VERSION"

if [[ "$PUBLISH" != "0" ]]; then
  step "Pushing and creating GitHub release $TAG"
  git push -q origin "HEAD:$BRANCH" "$TAG"
  gh release create "$TAG" "$APK_OUT" -R "$REPO" --title "App Depot (Android) $VERSION" \
    --notes-file "$NOTES_FILE" --latest=false
  echo "    https://github.com/$REPO/releases/tag/$TAG"
fi

step "Released App Depot (Android) $VERSION"
echo "    Catalog: from the hub run  scripts/depot-publish.sh app-depot-android"
