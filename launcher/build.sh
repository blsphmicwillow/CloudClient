#!/usr/bin/env bash
# Builds the dependency-free launcher jar.
#
# Only needs a JDK (8 or newer); no Gradle, no network.
set -euo pipefail

cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-}"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
  JAR="$JAVA_HOME/bin/jar"
else
  JAVAC="$(command -v javac || true)"
  JAR="$(command -v jar || true)"
fi

if [ -z "${JAVAC:-}" ] || [ -z "${JAR:-}" ]; then
  echo "error: a JDK is required (set JAVA_HOME or install openjdk-8/17)" >&2
  exit 1
fi

OUT="build"
CLASSES="$OUT/classes"
rm -rf "$CLASSES"
mkdir -p "$CLASSES"

# The launcher shares the client's JSON reader; compile both from source so the
# jar stays dependency-free.
find src core -name '*.java' > "$OUT/sources.txt" 2>/dev/null || true
{
  find src -name '*.java'
  find ../src/core/java/net/cloudclient/core/json -name '*.java'
} > "$OUT/sources.txt"

"$JAVAC" -encoding UTF-8 -d "$CLASSES" @"$OUT/sources.txt"

cat > "$OUT/manifest.txt" <<'EOF'
Main-Class: net.cloudclient.launcher.LauncherMain
Implementation-Title: Cloud Client Launcher
Implementation-Version: 0.1.1
EOF

"$JAR" cfm "$OUT/cloud-launcher.jar" "$OUT/manifest.txt" -C "$CLASSES" .

echo "built $OUT/cloud-launcher.jar"
echo "run: java -jar $OUT/cloud-launcher.jar login"
