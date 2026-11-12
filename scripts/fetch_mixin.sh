#!/usr/bin/env bash
#
# Downloads the Mixin runtime jar that Cloud Client needs on 1.8.9.
#
# Forge 1.8.9 predates Mixin being bundled, so it must sit in mods/ next to the
# client. This fetches the official SpongePowered artifact and prints its
# SHA-1 so you can verify it against
#   https://repo.spongepowered.org/repository/maven-public/org/spongepowered/mixin/0.7.11-SNAPSHOT/
#
# Deliberately not run automatically by install.sh: downloading executable jars
# should be an explicit action.
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
DEST="$ROOT/libs"
BASE="https://repo.spongepowered.org/repository/maven-public/org/spongepowered/mixin"

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red() { printf '\033[31m%s\033[0m\n' "$*" >&2; }

command -v curl >/dev/null 2>&1 || { red "curl is required"; exit 1; }

mkdir -p "$DEST"

# SNAPSHOT metadata lists the timestamped build; resolve it rather than guessing.
META="$BASE/0.7.11-SNAPSHOT/maven-metadata.xml"
echo "Resolving latest 0.7.11-SNAPSHOT from SpongePowered maven..."
XML="$(curl -fsSL "$META")" || {
  red "Could not reach $META"
  exit 1
}

TIMESTAMP="$(printf '%s' "$XML" | sed -n 's:.*<timestamp>\(.*\)</timestamp>.*:\1:p' | head -n1)"
BUILD="$(printf '%s' "$XML" | sed -n 's:.*<buildNumber>\(.*\)</buildNumber>.*:\1:p' | head -n1)"

if [ -z "${TIMESTAMP:-}" ] || [ -z "${BUILD:-}" ]; then
  red "Could not parse snapshot metadata."
  exit 1
fi

FILE="mixin-0.7.11-${TIMESTAMP}-${BUILD}.jar"
URL="$BASE/0.7.11-SNAPSHOT/$FILE"
OUT="$DEST/mixin-0.7.11-SNAPSHOT.jar"

echo "Downloading $URL"
curl -fsSL -o "$OUT" "$URL" || { red "Download failed"; exit 1; }

if command -v sha1sum >/dev/null 2>&1; then
  echo
  echo "SHA-1: $(sha1sum "$OUT" | cut -d' ' -f1)"
  echo "Verify against the .sha1 published next to the artifact:"
  echo "  $URL.sha1"
fi

green "Saved: $OUT"
echo "install.sh will pick this up automatically."
