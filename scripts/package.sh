#!/usr/bin/env bash
#
# Packages a distributable tarball: mod jar, Mixin jar, installer and docs.
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
VERSION="$(sed -n 's/^mod_version=//p' "$ROOT/gradle.properties" | tr -d '\r')"
DIST="$ROOT/dist"
NAME="cloud-client-${VERSION}-linux"

cd "$ROOT"

JAR="$(find build/libs -maxdepth 1 -name 'cloud-client-*.jar' ! -name '*-sources.jar' 2>/dev/null | head -n1 || true)"
[ -n "$JAR" ] || { echo "No built jar. Run scripts/build.sh first." >&2; exit 1; }

MIXIN="$(find libs -maxdepth 1 -name 'mixin-*.jar' 2>/dev/null | head -n1 || true)"

rm -rf "$DIST/$NAME"
mkdir -p "$DIST/$NAME/build/libs" "$DIST/$NAME/scripts" "$DIST/$NAME/docs"

cp "$JAR" "$DIST/$NAME/build/libs/"
[ -n "$MIXIN" ] && mkdir -p "$DIST/$NAME/libs" && cp "$MIXIN" "$DIST/$NAME/libs/"

cp install.sh "$DIST/$NAME/"
cp scripts/fetch_mixin.sh "$DIST/$NAME/scripts/"
cp README.md ROADMAP.md "$DIST/$NAME/" 2>/dev/null || true
cp docs/FAIRPLAY.md docs/CLOUDPACK.md "$DIST/$NAME/docs/" 2>/dev/null || true

chmod +x "$DIST/$NAME/install.sh" "$DIST/$NAME/scripts/fetch_mixin.sh"

tar -C "$DIST" -czf "$DIST/$NAME.tar.gz" "$NAME"
echo "Created $DIST/$NAME.tar.gz"
