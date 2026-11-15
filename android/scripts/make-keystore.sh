#!/bin/bash
# Creates the App Depot release keystore + keystore.properties in ~/.config/app-depot-signing/.
# Random password, chmod 600, never printed. Refuses to overwrite an existing key.
set -euo pipefail
DIR="$HOME/.config/app-depot-signing"
JKS="$DIR/app-depot-release.jks"
PROPS="$DIR/keystore.properties"
ALIAS="appdepot"

if [ -e "$JKS" ] || [ -e "$PROPS" ]; then
  echo "Refusing to overwrite existing key in $DIR" >&2
  exit 1
fi
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
mkdir -p "$DIR"; chmod 700 "$DIR"
PASS="$(openssl rand -hex 24)"
"$KEYTOOL" -genkeypair -keystore "$JKS" -storetype PKCS12 -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=App Depot, O=Ha Phung Quang Vu" \
  -storepass "$PASS" -keypass "$PASS" >/dev/null 2>&1
printf 'storePassword=%s\nkeyPassword=%s\nkeyAlias=%s\n' "$PASS" "$PASS" "$ALIAS" > "$PROPS"
chmod 600 "$JKS" "$PROPS"
echo "Created $JKS and $PROPS. Back up $DIR."
