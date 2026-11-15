#!/usr/bin/env bash
# build.sh — builds App Depot.app (universal), signs it and zips it for a release.
#
#   ./build.sh                     # signed with "App Depot Self-Signed" when that identity is in the
#                                  # keychain, otherwise ad-hoc (works on this Mac only)
#   SIGN_IDENTITY=- ./build.sh     # force ad-hoc (dev builds; release.sh refuses these)
#
# Output: build/App Depot.app and build/AppDepot-<VERSION>-macos.zip (the catalog asset).
# Why a self-signed identity: installed copies only accept an update whose signature satisfies their own
# designated requirement ("certificate leaf = H…"); an ad-hoc signature changes with every build.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="$(tr -d '[:space:]' < VERSION)"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "error: VERSION must be X.Y.Z, is '$VERSION'" >&2; exit 1; }
IFS=. read -r MAJ MIN PAT <<< "$VERSION"
BUILD_NUMBER=$((MAJ * 10000 + MIN * 100 + PAT))

BINARY_NAME="AppDepot"
APP_PATH="build/App Depot.app"
ZIP_PATH="build/AppDepot-${VERSION}-macos.zip"
IDENTITY_NAME="App Depot Self-Signed"
if [[ -z "${SIGN_IDENTITY:-}" ]]; then
    if security find-identity -p codesigning 2>/dev/null | grep -q "\"${IDENTITY_NAME}\""; then
        SIGN_IDENTITY="${IDENTITY_NAME}"
    else
        SIGN_IDENTITY="-"
    fi
fi

echo "==> Preparing build/ (App Depot ${VERSION}, build ${BUILD_NUMBER})"
git submodule update --init --recursive Vendor/app-shell
rm -rf build && mkdir -p build

echo "==> Building universal release binary (arm64 + x86_64)"
swift build -c release --arch arm64 --arch x86_64
BINARY_PATH="$(swift build -c release --arch arm64 --arch x86_64 --show-bin-path)/${BINARY_NAME}"
[[ -f "$BINARY_PATH" ]] || { echo "error: binary not found at $BINARY_PATH" >&2; exit 1; }

echo "==> Assembling ${APP_PATH}"
mkdir -p "${APP_PATH}/Contents/MacOS" "${APP_PATH}/Contents/Resources"
cp "$BINARY_PATH" "${APP_PATH}/Contents/MacOS/${BINARY_NAME}"
cp Sources/AppDepot/Resources/Info.plist "${APP_PATH}/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString ${VERSION}" "${APP_PATH}/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion ${BUILD_NUMBER}" "${APP_PATH}/Contents/Info.plist"

echo "==> Building AppIcon.icns from ../assets/make-icon.swift"
swift scripts/make-icon.swift "${APP_PATH}/Contents/Resources/AppIcon.icns" >/dev/null

echo "==> Signing with: ${SIGN_IDENTITY}"
codesign --deep --force --timestamp=none --sign "${SIGN_IDENTITY}" "${APP_PATH}"
codesign --verify --deep --strict "${APP_PATH}"
echo "==> Signature OK"

echo "==> Zipping ${ZIP_PATH}"
ditto -c -k --sequesterRsrc --keepParent "${APP_PATH}" "${ZIP_PATH}"
echo "    sha256 $(shasum -a 256 "${ZIP_PATH}" | awk '{print $1}')  size $(stat -f %z "${ZIP_PATH}")"
echo "==> Done: ${APP_PATH}"
