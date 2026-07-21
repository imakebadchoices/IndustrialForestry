#!/usr/bin/env bash
# generate.sh — build the whole wiki in one shot.
#
#   1. runs the mod's datagen to dump the live species registry
#      (build/wiki-data/species-dump.json)
#   2. runs the single build step (build.py): renders the markdown pages, generates
#      the genetics appendix tables from the dump + on-disk datapack species, and
#      bundles the breeding explorer (Mermaid fetched at a pinned version) into one
#      self-contained site.
#
# Run from anywhere:  wiki/generate.sh [--skip-datagen] [--out DIR]
#
# Requires: JDK 21 on PATH (for ./gradlew) and Python 3 with wiki/requirements.txt
# (`pip install -r wiki/requirements.txt`). Building the explorer fetches Mermaid
# from the npm CDN once (cached under build/wiki-data).
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WIKI="$REPO/wiki"
OUT="$WIKI/site"
SKIP_DATAGEN=0

while [ $# -gt 0 ]; do
  case "$1" in
    --skip-datagen) SKIP_DATAGEN=1; shift ;;
    --out) OUT="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

PYTHON="${PYTHON:-python3}"

if [ "$SKIP_DATAGEN" -eq 0 ]; then
  echo "==> Running datagen (species dump)…"
  (cd "$REPO" && ./gradlew runData --console=plain)
else
  echo "==> Skipping datagen (using existing build/wiki-data/species-dump.json)"
fi

echo "==> Building site + explorer…"
"$PYTHON" "$WIKI/build.py" --repo "$REPO" --out "$OUT"

echo "==> Done. Site in $OUT (open index.html; explorer.html is the breeding explorer)."
