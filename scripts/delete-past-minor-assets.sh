#!/usr/bin/env bash
# APK asset retention for dmz006/datawatch-app GitHub releases.
#
# Keep-set (mirrors datawatch parent rule from AGENT.md):
#   1. Every MAJOR release (X.0.0) — keep indefinitely.
#   2. The latest MINOR (highest X.Y.0, Y >= 1) — keep until superseded.
#   3. The latest PATCH on the latest minor (highest X.Y.Z, Z > 0, same X.Y) — keep until superseded.
#
# Everything else: delete binary assets (APKs, mapping files) from the GH release page.
# Release *notes* are never deleted.
#
# Usage:
#   DRY_RUN=1 ./scripts/delete-past-minor-assets.sh   # preview only
#   ./scripts/delete-past-minor-assets.sh              # live delete
#
# Run this as part of the post-`gh release create` step.

set -uo pipefail

DRY_RUN="${DRY_RUN:-0}"
REPO="${REPO:-dmz006/datawatch-app}"
total_deletes=0
total_releases=0

mapfile -t all_tags < <(gh release list --repo "$REPO" --limit 500 2>/dev/null \
  | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | sort -uV)

if [[ ${#all_tags[@]} -eq 0 ]]; then
  echo "[retention] no releases found"
  exit 0
fi

keep=()

# Every major (X.0.0).
for t in "${all_tags[@]}"; do
  if [[ "$t" =~ ^v[0-9]+\.0\.0$ ]]; then keep+=("$t"); fi
done

# Latest minor — highest X.Y.0 (Y >= 1).
latest_minor=""
for t in "${all_tags[@]}"; do
  if [[ "$t" =~ ^v[0-9]+\.[0-9]+\.0$ ]] && [[ ! "$t" =~ ^v[0-9]+\.0\.0$ ]]; then
    latest_minor="$t"
  fi
done
[[ -n "$latest_minor" ]] && keep+=("$latest_minor")

# Latest patch on the latest minor.
if [[ -n "$latest_minor" ]]; then
  xy_prefix="${latest_minor%.0}."
  latest_patch=""
  for t in "${all_tags[@]}"; do
    if [[ "$t" == "$xy_prefix"* && "$t" != "$latest_minor" ]]; then
      latest_patch="$t"
    fi
  done
  [[ -n "$latest_patch" ]] && keep+=("$latest_patch")
fi

keep_unique=$(printf '%s\n' "${keep[@]}" | sort -u | tr '\n' ' ')
echo "[retention] keep set: ${keep_unique}"

for tag in "${all_tags[@]}"; do
  if printf '%s\n' "${keep[@]}" | grep -qx "$tag"; then continue; fi
  total_releases=$((total_releases + 1))
  assets=$(gh release view "$tag" --repo "$REPO" --json assets --jq '.assets[].name' 2>/dev/null)
  [[ -z "$assets" ]] && continue
  while IFS= read -r asset; do
    if [[ "$DRY_RUN" == "1" ]]; then
      echo "[dry-run] gh release delete-asset $tag $asset --repo $REPO"
    else
      gh release delete-asset "$tag" "$asset" --repo "$REPO" --yes 2>&1 | head -1
    fi
    total_deletes=$((total_deletes + 1))
  done <<< "$assets"
done

echo "[summary] processed $total_releases releases, deleted $total_deletes assets"
