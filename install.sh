#!/usr/bin/env bash
#
# Cloud Client — Linux installer.
#
# Finds Minecraft 1.8.9 Forge instances across the common launchers and drops
# the mod jars into their mods/ folder. Nothing is written outside the instance
# you choose, and every change can be undone with --uninstall.
#
#   ./install.sh --list                      show detected instances
#   ./install.sh --instance ~/.minecraft     install into a specific instance
#   ./install.sh                             install into every detected instance
#   ./install.sh --instance DIR --dry-run    print actions, change nothing
#   ./install.sh --instance DIR --uninstall  remove the jars again
#
set -euo pipefail

VERSION="0.1.1"
JAR_NAME="cloud-client-${VERSION}.jar"
MARKER=".cloudclient-installed"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

INSTANCE=""
UNINSTALL=0
DRY_RUN=0
LIST_ONLY=0
FORCE=0

# --------------------------------------------------------------------- output
if [ -t 1 ] && command -v tput >/dev/null 2>&1 && [ "$(tput colors 2>/dev/null || echo 0)" -ge 8 ]; then
  RED="$(tput setaf 1)"; GREEN="$(tput setaf 2)"; YELLOW="$(tput setaf 3)"
  BLUE="$(tput setaf 4)"; BOLD="$(tput bold)"; RESET="$(tput sgr0)"
else
  RED=""; GREEN=""; YELLOW=""; BLUE=""; BOLD=""; RESET=""
fi

info()  { printf '%s\n' "$*"; }
ok()    { printf '%s✓%s %s\n' "$GREEN" "$RESET" "$*"; }
warn()  { printf '%s!%s %s\n' "$YELLOW" "$RESET" "$*"; }
err()   { printf '%s✗ %s%s\n' "$RED" "$*" "$RESET" >&2; }
head2() { printf '\n%s%s%s\n' "$BOLD" "$*" "$RESET"; }

usage() {
  sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  cat <<EOF

Options:
  --instance DIR      install into this instance (skips auto-detection)
  --uninstall         remove Cloud Client from the target instance(s)
  --dry-run           show what would happen, change nothing
  --list              list detected 1.8.9 Forge instances and exit
  --force             install even if the jar is older than the sources
  -h, --help          this text

Cloud Client has no Mixin dependency: the built jar is the only file needed.
EOF
}

die() { err "$*"; exit 1; }

run() {
  if [ "$DRY_RUN" -eq 1 ]; then
    printf '   would: %s\n' "$*"
  else
    "$@"
  fi
}

# ---------------------------------------------------------------- arg parsing
while [ $# -gt 0 ]; do
  case "$1" in
    --instance)  [ $# -ge 2 ] || die "--instance needs a path"; INSTANCE="$2"; shift 2 ;;
    --instance=*) INSTANCE="${1#*=}"; shift ;;
    --uninstall) UNINSTALL=1; shift ;;
    --dry-run)   DRY_RUN=1; shift ;;
    --list)      LIST_ONLY=1; shift ;;
    --force)     FORCE=1; shift ;;
    -h|--help)   usage; exit 0 ;;
    *)           die "Unknown option: $1 (try --help)" ;;
  esac
done

# ------------------------------------------------------------- detection
is_official_instance() {
  local dir="$1"
  [ -d "$dir/versions" ] || return 1
  local v
  for v in "$dir"/versions/*; do
    [ -d "$v" ] || continue
    local name
    name="$(basename "$v" | tr '[:upper:]' '[:lower:]')"
    case "$name" in
      *1.8.9*forge*|*forge*1.8.9*) return 0 ;;
    esac
  done
  return 1
}

# Prism/MultiMC: mmc-pack.json lists components; look for minecraft 1.8.9 + forge.
is_prism_instance() {
  local dir="$1"
  local pack="$dir/mmc-pack.json"
  [ -f "$pack" ] || return 1
  grep -q '1\.8\.9' "$pack" 2>/dev/null || return 1
  grep -qi 'forge' "$pack" 2>/dev/null || return 1
  return 0
}

# CurseForge / GDLauncher style.
is_cf_instance() {
  local dir="$1"
  local meta="$dir/minecraftinstance.json"
  [ -f "$meta" ] || return 1
  grep -q '1\.8\.9' "$meta" 2>/dev/null || return 1
  grep -qi 'forge' "$meta" 2>/dev/null || return 1
  return 0
}

detect_instances() {
  local out=()
  local dir

  for dir in "$HOME/.minecraft" "$HOME/.var/app/com.mojang.Minecraft/.minecraft"; do
    if [ -d "$dir" ] && is_official_instance "$dir"; then out+=("$dir"); fi
  done

  local roots=(
    "$HOME/.local/share/PrismLauncher/instances"
    "$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances"
    "$HOME/.local/share/multimc/instances"
    "$HOME/.local/share/MultiMC/instances"
    "$HOME/.var/app/org.multimc.MultiMC/data/multimc/instances"
    "$HOME/.local/share/ATLauncher/instances"
    "$HOME/.local/share/TechnicLauncher/technic"
  )

  local root inst
  for root in "${roots[@]}"; do
    [ -d "$root" ] || continue
    while IFS= read -r -d '' inst; do
      [ -d "$inst" ] || continue
      if is_prism_instance "$inst"; then out+=("$inst"); fi
    done < <(find "$root" -mindepth 1 -maxdepth 1 -type d -print0 2>/dev/null)
  done

  # Deduplicate while keeping order.
  local seen=""
  for dir in "${out[@]:-}"; do
    [ -n "$dir" ] || continue
    case "$seen" in *"|$dir|"*) continue ;; esac
    seen="${seen}|${dir}|"
    printf '%s\n' "$dir"
  done
}

find_jar() {
  # A local build of the current version wins; otherwise use the released jar
  # shipped in prebuilt/, which lets a fresh checkout install without a build.
  local candidates=(
    "$SCRIPT_DIR/build/libs/$JAR_NAME"
    "$SCRIPT_DIR/prebuilt/$JAR_NAME"
    "$SCRIPT_DIR/build/libs/cloud-client-${VERSION}.jar"
    "$SCRIPT_DIR/libs/$JAR_NAME"
    "$SCRIPT_DIR/$JAR_NAME"
  )
  local c
  for c in "${candidates[@]}"; do
    if [ -f "$c" ]; then printf '%s\n' "$c"; return 0; fi
  done
  # Fall back to any cloud-client jar present.
  local any
  any="$(find "$SCRIPT_DIR/build/libs" "$SCRIPT_DIR/libs" -maxdepth 1 \( -name 'cloud-client-*.jar' -o -name 'cloudclient-*.jar' \) ! -name '*-sources.jar' 2>/dev/null | head -n1 || true)"
  if [ -n "$any" ]; then printf '%s\n' "$any"; return 0; fi
  return 1
}

verify_jar() {
  local jar="$1"
  if command -v unzip >/dev/null 2>&1; then
    if ! unzip -l "$jar" 2>/dev/null | grep -q 'mcmod.info'; then
      warn "$(basename "$jar") does not contain mcmod.info — is it the mod jar?"
      return 1
    fi
  elif command -v python3 >/dev/null 2>&1; then
    if ! python3 - "$jar" <<'PY'
import sys, zipfile
try:
    with zipfile.ZipFile(sys.argv[1]) as z:
        names = z.namelist()
except Exception:
    sys.exit(1)
sys.exit(0 if any(n.endswith("mcmod.info") for n in names) else 1)
PY
    then
      warn "$(basename "$jar") does not contain mcmod.info — is it the mod jar?"
      return 1
    fi
  else
    warn "no unzip or python3 available; skipping jar content check"
  fi
  return 0
}

# --------------------------------------------------------------- operations
check_stale() {
  local jar="$1"
  [ "$FORCE" -eq 1 ] && return 0
  # prebuilt/ holds the released jar, so it is expected to be older than any
  # local edits; only locally compiled jars are checked for staleness.
  case "$jar" in "$SCRIPT_DIR/prebuilt/"*) return 0 ;; esac
  [ -d "$SCRIPT_DIR/src" ] || return 0
  local newer
  newer="$(find "$SCRIPT_DIR/src" -name '*.java' -newer "$jar" -print -quit 2>/dev/null || true)"
  if [ -n "$newer" ]; then
    err "that jar is older than the source tree — it predates your latest fixes."
    info "   jar:   $jar"
    info "   newer: ${newer#"$SCRIPT_DIR"/}"
    info ""
    info "This is usually a forgotten rebuild, and an old jar keeps old crashes."
    info "   ./scripts/build.sh     rebuild the mod and the launcher"
    info "   --force                install this jar anyway"
    exit 1
  fi
}

install_into() {
  local instance="$1"
  local jar="$2"
  local mods="$instance/mods"

  head2 "Installing into $instance"
  run mkdir -p "$mods"

  # Replace any previous copy so upgrades do not leave two versions behind.
  if [ -d "$mods" ]; then
    local old
    while IFS= read -r -d '' old; do
      case "$(basename "$old")" in
        cloud-client-*.jar|cloudclient-*.jar)
          if [ "$(basename "$old")" != "$JAR_NAME" ]; then
            run rm -f "$old"
            info "   removed old $(basename "$old")"
          fi
          ;;
      esac
    done < <(find "$mods" -maxdepth 1 \( -name 'cloud-client-*.jar' -o -name 'cloudclient-*.jar' \) -print0 2>/dev/null)
  fi

  # Back up an identical existing jar rather than silently overwriting it.
  if [ -f "$mods/$JAR_NAME" ] && [ "$DRY_RUN" -eq 0 ]; then
    local backup="$mods/$JAR_NAME.bak"
    cp -f "$mods/$JAR_NAME" "$backup"
    info "   backed up existing jar to $(basename "$backup")"
  fi

  run cp -f "$jar" "$mods/$JAR_NAME"
  ok "installed $JAR_NAME"

  if [ "$DRY_RUN" -eq 0 ]; then
    {
      echo "cloud-client $VERSION"
      echo "installed $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
      echo "jar $JAR_NAME"
    } > "$mods/$MARKER"
  fi
}

uninstall_from() {
  local instance="$1"
  local mods="$instance/mods"
  head2 "Uninstalling from $instance"
  if [ ! -d "$mods" ]; then
    warn "no mods/ directory; nothing to do"
    return 0
  fi
  local removed=0
  local f
  while IFS= read -r -d '' f; do
    run rm -f "$f"
    info "   removed $(basename "$f")"
    removed=1
  done < <(find "$mods" -maxdepth 1 \( -name 'cloud-client-*.jar' -o -name 'cloudclient-*.jar' -o -name "$MARKER" \) -print0 2>/dev/null)

  ok "done"
}

# ---------------------------------------------------------------------- main
head2 "Cloud Client $VERSION — Linux installer"
[ "$DRY_RUN" -eq 1 ] && warn "dry run: no files will be changed"

if [ -n "$INSTANCE" ]; then
  [ -d "$INSTANCE" ] || die "Instance directory does not exist: $INSTANCE"
  TARGETS=("$INSTANCE")
else
  mapfile -t TARGETS < <(detect_instances)
fi

if [ "${#TARGETS[@]}" -eq 0 ] || [ -z "${TARGETS[0]:-}" ]; then
  warn "No Minecraft 1.8.9 Forge instance found."
  info ""
  info "Point the installer at your instance explicitly:"
  info "  $0 --instance /path/to/instance"
  info ""
  info "Typical locations:"
  info "  ~/.minecraft                                   (official launcher)"
  info "  ~/.local/share/PrismLauncher/instances/<name>  (Prism)"
  info "  ~/.local/share/multimc/instances/<name>        (MultiMC)"
  exit 1
fi

if [ "$LIST_ONLY" -eq 1 ]; then
  head2 "Detected instances"
  for t in "${TARGETS[@]}"; do printf '  %s\n' "$t"; done
  exit 0
fi

if [ "$UNINSTALL" -eq 1 ]; then
  for t in "${TARGETS[@]}"; do uninstall_from "$t"; done
  exit 0
fi

JAR="$(find_jar)" || die "Cloud Client jar not found. Build it first: scripts/build.sh"
check_stale "$JAR"

verify_jar "$JAR" || true
info "jar:   $JAR"

for t in "${TARGETS[@]}"; do
  install_into "$t" "$JAR"
done

head2 "Next steps"
info "1. Launch the 1.8.9 Forge profile for that instance."
info "2. Press ${BOLD}right-shift${RESET} in game to open the module menu."
info "3. Cosmetics default to a cloud cape; modules can be toggled individually."
info ""
info "Own launcher (no Mojang launcher involved):"
info "  ${BOLD}./cloudclient${RESET}                     open the Cloud Client window"
info "  ${BOLD}./cloudclient launch${RESET}              boot without a window"
info ""
info "Uninstall at any time:  $0 --instance <dir> --uninstall"
