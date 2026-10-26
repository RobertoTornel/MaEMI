#!/usr/bin/env bash
# Build every Minecraft version MaEMI supports and collect the jars into dist/<mc>/.
#
# Each version lives on its own branch. This script creates a throwaway worktree per branch,
# builds it, and copies the resulting jars out, so one machine can produce the whole matrix.
#
# Usage: scripts/build-all-versions.sh [mc-version ...]
#        scripts/build-all-versions.sh            # every version below
#        scripts/build-all-versions.sh 1.20.1     # just one
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="${DIST:-$REPO_ROOT/dist}"
WORK="${WORK:-$(mktemp -d)}"
: "${GRADLE_USER_HOME:=$HOME/.gradle}"
if [ -z "${JAVA_HOME:-}" ] && [ -d "/usr/lib/jvm/java-1.21.0-openjdk-amd64" ]; then
  export JAVA_HOME="/usr/lib/jvm/java-1.21.0-openjdk-amd64"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# branch:minecraft-version:loaders
ALL_VERSIONS=(
  "1.18.2:1.18.2:fabric"
  "1.19.2:1.19.2:fabric,forge"
  "1.19.3:1.19.3:fabric,forge"
  "1.19.4:1.19.4:fabric,forge"
  "1.20:1.20:fabric,forge"
  "1.20.1:1.20.1:fabric,forge"
  "1.20.2:1.20.2:fabric,forge"
  "1.20.4:1.20.4:fabric"
  "1.20.6:1.20.6:fabric"
  "1.21:1.21:fabric"
  "1.21.1:1.21.1:fabric"
)

selected=()
if [ "$#" -gt 0 ]; then
  for want in "$@"; do
    for entry in "${ALL_VERSIONS[@]}"; do
      if [ "${entry%%:*}" = "$want" ] || [ "$(echo "$entry" | cut -d: -f2)" = "$want" ]; then
        selected+=("$entry")
      fi
    done
  done
else
  selected=("${ALL_VERSIONS[@]}")
fi

mkdir -p "$DIST"
failures=()

for entry in "${selected[@]}"; do
  branch="${entry%%:*}"
  rest="${entry#*:}"
  mc="${rest%%:*}"
  loaders="${rest#*:}"
  worktree="$WORK/maemi-$mc"

  echo
  echo "=== $mc (branch $branch, loaders $loaders) ==="
  rm -rf "$worktree"
  if ! git -C "$REPO_ROOT" worktree add --quiet --force "$worktree" "$branch"; then
    echo "  !! could not check out $branch"
    failures+=("$mc")
    continue
  fi

  if ( cd "$worktree" && ./gradlew build --console=plain -q > "$WORK/$mc.log" 2>&1 ); then
    mkdir -p "$DIST/$mc"
    found=0
    for loader in fabric forge; do
      jar=$(ls "$worktree/$loader/build/libs/"*.jar 2>/dev/null | grep -v -e sources -e shadow | head -1 || true)
      if [ -n "$jar" ]; then
        cp "$jar" "$DIST/$mc/"
        found=1
      fi
    done
    if [ "$found" = 1 ]; then
      echo "  ok -> dist/$mc"
    else
      echo "  !! no jar produced"
      failures+=("$mc")
    fi
  else
    echo "  !! build failed (log: $WORK/$mc.log)"
    tail -20 "$WORK/$mc.log" | sed 's/^/     /'
    failures+=("$mc")
  fi

  git -C "$REPO_ROOT" worktree remove --force "$worktree" 2>/dev/null || true
done

echo
if [ "${#failures[@]}" -gt 0 ]; then
  echo "FAILED: ${failures[*]}"
  exit 1
fi
echo "All builds green. Jars in $DIST"
