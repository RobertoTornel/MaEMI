#!/usr/bin/env bash
# Publish every jar produced by scripts/build-all-versions.sh to Modrinth.
#
# Expects dist/<minecraft-version>/maemi-<loader>-<mod_version>.jar. One Modrinth version is created
# per (minecraft version, loader) pair, tagged with the right game_versions so the Modrinth site
# offers the file to the correct game version.
#
# The token is read from MODRINTH_TOKEN or ~/.config/maemi/modrinth-token and never written here.
#
# Usage: scripts/upload-dist.sh [--dry-run] [mc-version ...]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="${DIST:-$ROOT/dist}"
PROJECT_ID="${MODRINTH_PROJECT_ID:-kU7DtD47}"
TOKEN_FILE="${MODRINTH_TOKEN_FILE:-$HOME/.config/maemi/modrinth-token}"
CHANGELOG_FILE="${CHANGELOG_FILE:-$ROOT/docs/CHANGELOG.md}"
VERSION="$(sed -n 's/^mod_version=//p' "$ROOT/gradle.properties")"

DRY_RUN=0
args=()
for a in "$@"; do
  if [ "$a" = "--dry-run" ]; then DRY_RUN=1; else args+=("$a"); fi
done

TOKEN="${MODRINTH_TOKEN:-}"
if [ "$DRY_RUN" = 0 ] && [ -z "$TOKEN" ] && [ -r "$TOKEN_FILE" ]; then
  IFS= read -r TOKEN < "$TOKEN_FILE" || true
fi
if [ "$DRY_RUN" = 0 ] && [ -z "$TOKEN" ]; then
  echo "No Modrinth token. Set MODRINTH_TOKEN or put it in $TOKEN_FILE" >&2
  exit 1
fi
if [ ! -f "$CHANGELOG_FILE" ]; then
  echo "Missing changelog: $CHANGELOG_FILE" >&2
  exit 1
fi
if [ ! -d "$DIST" ]; then
  echo "Missing $DIST; run scripts/build-all-versions.sh first" >&2
  exit 1
fi

existing() {
  curl -sS -H "Authorization: $TOKEN" "https://api.modrinth.com/v2/project/$PROJECT_ID/version" |
    python3 -c 'import json,sys; print(" ".join(v.get("version_number","") for v in json.load(sys.stdin)))'
}

EXISTING=""
if [ "$DRY_RUN" = 0 ]; then
  EXISTING=" $(existing) "
fi

versions=("${args[@]}")
if [ "${#versions[@]}" -eq 0 ]; then
  while IFS= read -r dir; do versions+=("$(basename "$dir")"); done < <(find "$DIST" -mindepth 1 -maxdepth 1 -type d | sort -V)
fi

failures=()
for mc in "${versions[@]}"; do
  for jar in "$DIST/$mc"/maemi-*-"$VERSION".jar; do
    [ -e "$jar" ] || continue
    base="$(basename "$jar")"
    case "$base" in
      *-fabric-*) loader=fabric ;;
      *-forge-*)  loader=forge ;;
      *) echo "skip $base (unknown loader)"; continue ;;
    esac
    number="$VERSION-$loader-$mc"

    if [ "$DRY_RUN" = 0 ] && [[ "$EXISTING" == *" $number "* ]]; then
      echo "$number already on Modrinth; skipping"
      continue
    fi

    echo "=== $mc $loader -> $number ($(du -h "$jar" | cut -f1)) ==="
    if [ "$DRY_RUN" = 1 ]; then
      continue
    fi

    payload="$(mktemp)"
    python3 - "$PROJECT_ID" "$mc" "$loader" "$number" "$CHANGELOG_FILE" > "$payload" <<'PY'
import json, sys
project_id, mc, loader, number, changelog_file = sys.argv[1:6]
deps = [
    {"project_id": "fRiHVvU7", "dependency_type": "required"},  # EMI
    {"project_id": "9s6osm5g", "dependency_type": "optional"},  # Cloth Config
    {"project_id": "XxWD5pD3", "dependency_type": "optional"},  # AE2
]
if loader == "fabric":
    deps += [
        {"project_id": "P7dR8mSH", "dependency_type": "required"},  # Fabric API
        {"project_id": "mOgUt4GM", "dependency_type": "optional"},  # Mod Menu
    ]
print(json.dumps({
    "project_id": project_id,
    "name": f"MaEMI for {mc} ({loader})",
    "version_number": number,
    "changelog": open(changelog_file, encoding="utf-8").read(),
    "dependencies": deps,
    "game_versions": [mc],
    "version_type": "release",
    "loaders": [loader],
    "featured": False,
    "file_parts": ["file"],
    "primary_file": "file",
    "environment": "client_only",
}))
PY

    response="$(mktemp)"
    status="$(curl -sS -o "$response" -w '%{http_code}' -X POST \
      "https://api.modrinth.com/v2/version" \
      -H "Authorization: $TOKEN" \
      -F "data=<$payload;type=application/json" \
      -F "file=@$jar;type=application/java-archive")"
    rm -f "$payload"
    if [ "$status" != "200" ]; then
      echo "  !! upload failed ($status)" >&2
      head -c 400 "$response" >&2; echo >&2
      failures+=("$number")
    else
      python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print("  ok", d.get("id"), d.get("status"))' "$response"
    fi
    rm -f "$response"
  done
done

if [ "${#failures[@]}" -gt 0 ]; then
  echo "FAILED: ${failures[*]}" >&2
  exit 1
fi
echo "Done."
