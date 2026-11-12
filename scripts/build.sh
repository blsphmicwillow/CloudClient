#!/usr/bin/env bash
#
# Builds the Cloud Client jar.
#
# 1.8.9 pins the toolchain: ForgeGradle 2.1 needs Gradle 2.14.x and a JDK 8.
# A newer JDK will fail in ForgeGradle's setup, not in this code.
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
cd "$ROOT"

red() { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

command -v gradle >/dev/null 2>&1 || {
  red "gradle not found."
  cat <<'EOF'

Install Gradle 2.14.1 (the version ForgeGradle 2.1 expects):

  curl -LO https://services.gradle.org/distributions/gradle-2.14.1-bin.zip
  unzip gradle-2.14.1-bin.zip -d ~/.local/opt
  export PATH="$HOME/.local/opt/gradle-2.14.1/bin:$PATH"

Then re-run this script.
EOF
  exit 1
}

if command -v java >/dev/null 2>&1; then
  JAVA_MAJOR="$(java -version 2>&1 | head -n1 | sed -E 's/.*"1\.([0-9]+)\..*/\1/; s/.*"([0-9]+)\..*/\1/')"
  if [ "${JAVA_MAJOR:-0}" != "8" ]; then
    yellow "Warning: java major version is ${JAVA_MAJOR:-unknown}; ForgeGradle 2.1 expects 1.8."
    yellow "Set JAVA_HOME to a JDK 8 before building if the build fails."
  fi
fi

yellow "Running engine tests first (no Minecraft needed)..."
if command -v javac >/dev/null 2>&1; then
  rm -rf build/core-test
  mkdir -p build/core-test
  javac --release 8 -d build/core-test $(find src/core/java tools/test -name '*.java') 2>/dev/null \
    || javac -source 8 -target 8 -d build/core-test $(find src/core/java tools/test -name '*.java')
  java -cp build/core-test net.cloudclient.test.CoreTests
else
  yellow "javac not found; skipping engine tests."
fi

yellow "Building Forge jar..."
gradle build "$@"

JAR="$(find build/libs -maxdepth 1 -name 'cloud-client-*.jar' ! -name '*-sources.jar' | head -n1 || true)"
if [ -z "$JAR" ]; then
  red "Build finished but no cloud-client jar was produced."
  exit 1
fi

green "Built: $JAR"

# The standalone launcher has no Gradle dependency of its own.
if [ -x launcher/build.sh ]; then
  echo "Building the standalone launcher..."
  ./launcher/build.sh
fi
echo
echo "Next:"
echo "  ./install.sh --list"
echo "  ./install.sh --instance <your 1.8.9 forge instance>"
