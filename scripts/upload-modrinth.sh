#!/usr/bin/env bash
# Upload the already-built MaEMI jars to Modrinth.
# The token is read from MODRINTH_TOKEN or ~/.config/maemi/modrinth-token.
# It is never written into this repository.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ID="${MODRINTH_PROJECT_ID:-kU7DtD47}"
TOKEN_FILE="${MODRINTH_TOKEN_FILE:-$HOME/.config/maemi/modrinth-token}"
TOKEN="${MODRINTH_TOKEN:-}"
if [[ -z "$TOKEN" && -r "$TOKEN_FILE" ]]; then
  IFS= read -r TOKEN < "$TOKEN_FILE" || true
fi
if [[ -z "$TOKEN" ]]; then
  echo "No Modrinth token. Set MODRINTH_TOKEN or put it in $TOKEN_FILE" >&2
  exit 1
fi

VERSION="${1:-$(sed -n 's/^mod_version=//p' "$ROOT/gradle.properties")}"
if [[ -z "$VERSION" ]]; then
  echo "Could not determine mod_version" >&2
  exit 1
fi

FABRIC_JAR="$ROOT/fabric/build/libs/maemi-fabric-$VERSION.jar"
FORGE_JAR="$ROOT/forge/build/libs/maemi-forge-$VERSION.jar"
CHANGELOG_FILE="${CHANGELOG_FILE:-$ROOT/docs/CHANGELOG.md}"
for f in "$FABRIC_JAR" "$FORGE_JAR"; do
  if [[ ! -f "$f" ]]; then
    echo "Missing $f; run ./gradlew build first" >&2
    exit 1
  fi
done
if [[ ! -f "$CHANGELOG_FILE" ]]; then
  echo "Missing changelog: $CHANGELOG_FILE" >&2
  exit 1
fi

api_get() {
  curl -sS -H "Authorization: $TOKEN" "https://api.modrinth.com/v2/$1"
}

EXISTING="$(api_get "project/$PROJECT_ID/version" | python3 -c 'import json,sys; print(" ".join(v.get("version_number", "") for v in json.load(sys.stdin)))')"

upload() {
  local loader="$1" name="$2" number="$3" file="$4" payload response status
  if [[ " $EXISTING " == *" $number "* ]]; then
    echo "$number already exists; skipping"
    return 0
  fi
  payload="$(mktemp)"
  python3 - "$PROJECT_ID" "$name" "$number" "$loader" "$CHANGELOG_FILE" > "$payload" <<'PY'
import json, sys
project_id, name, number, loader, changelog_file = sys.argv[1:6]
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
    "name": name,
    "version_number": number,
    "changelog": open(changelog_file, encoding="utf-8").read(),
    "dependencies": deps,
    "game_versions": ["1.20.1"],
    "version_type": "release",
    "loaders": [loader],
    "featured": False,
    "file_parts": ["file"],
    "primary_file": "file",
    "environment": "client_only",
}))
PY
  response="$(mktemp)"
  status="$(curl -sS -o "$response" -w '%{http_code}' -X POST     "https://api.modrinth.com/v2/version"     -H "Authorization: $TOKEN"     -F "data=<$payload;type=application/json"     -F "file=@$file;type=application/java-archive")"
  rm -f "$payload"
  if [[ "$status" != "200" ]]; then
    echo "$loader upload failed ($status):" >&2
    cat "$response" >&2
    rm -f "$response"
    return 1
  fi
  python3 - "$loader" "$response" <<'PY'
import json, sys
d = json.load(open(sys.argv[2]))
print(f"{sys.argv[1]}: {d.get('id')} {d.get('version_number')} {d.get('status')}")
PY
  rm -f "$response"
}

upload fabric "MaEMI $VERSION (Fabric)" "$VERSION-fabric" "$FABRIC_JAR"
upload forge "MaEMI $VERSION (Forge)" "$VERSION-forge" "$FORGE_JAR"

# A new project is created as a draft. Put versions in first, then submit once.
status="$(api_get "project/$PROJECT_ID" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status", ""))')"
if [[ "$status" == "draft" ]]; then
  curl -sS -o /dev/null -X PATCH "https://api.modrinth.com/v2/project/$PROJECT_ID"     -H "Authorization: $TOKEN" -H 'Content-Type: application/json'     -d '{"status":"processing","requested_status":"approved"}'
  echo "Submitted project $PROJECT_ID for review"
else
  echo "Project status: $status (not re-submitted)"
fi
