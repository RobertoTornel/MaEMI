#!/usr/bin/env bash
# Replaces the MaEMI display brand with MaEMI in every text file and file name.
# Literal, case-sensitive replacement: "MaEMI" -> "MaEMI". Lowercase ids (dev.nei, nei-forge)
# and Java identifiers (NeiHooks, NeiKeybinds) are deliberately untouched.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

# Build outputs, run logs and VCS data are not source files.
mapfile -t files < <(
	grep -Il 'MaEMI' -r . \
		--exclude-dir=.git \
		--exclude-dir=.gradle \
		--exclude-dir=build \
		--exclude-dir=run \
		--exclude-dir=logs \
		--exclude='*.jar' \
		--exclude='*.png' \
		--exclude='*.ogg' \
		--exclude='*.bin' || true
)

for file in "${files[@]}"; do
	sed -i 's/MaEMI/MaEMI/g' "$file"
done

# Rename files and directories whose names contain the literal brand.
while IFS= read -r -d '' entry; do
	dir="$(dirname "$entry")"
	base="$(basename "$entry")"
	mv -v "$entry" "$dir/${base//MaEMI/MaEMI}"
done < <(find . -depth \( -path ./.git -o -path ./build -o -path ./.gradle \) -prune -o -name '*MaEMI*' -print0)

echo "Rewrote ${#files[@]} file(s)."
