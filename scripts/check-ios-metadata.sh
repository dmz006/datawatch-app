#!/usr/bin/env bash
# Validate App Store / TestFlight metadata lengths under iosApp/fastlane/metadata.
# Limits are App Store Connect's (characters, trailing newline ignored).
# Usage: scripts/check-ios-metadata.sh   — exits non-zero if any file is over its limit.
set -euo pipefail
cd "$(dirname "$0")/../iosApp/fastlane/metadata"

fail=0
check() { # file limit
  local f=$1 limit=$2 n
  if [[ ! -f $f ]]; then printf '%-34s MISSING\n' "$f"; fail=1; return; fi
  n=$(python3 -c 'import sys;print(len(open(sys.argv[1],encoding="utf-8").read().rstrip("\n")))' "$f")
  local status=ok
  (( n > limit )) && { status=OVER; fail=1; }
  printf '%-34s %5d / %-5d %s\n' "$f" "$n" "$limit" "$status"
}

check en-US/name.txt              30
check en-US/subtitle.txt          30
check en-US/description.txt       4000
check en-US/keywords.txt          100
check en-US/promotional_text.txt  170
check en-US/release_notes.txt     4000
check en-US/support_url.txt       255
check en-US/marketing_url.txt     255
check en-US/privacy_url.txt       255
# Localized App Store listings (no release_notes: en-US only).
LOCALES=(de-DE es-ES fr-FR ja)
for loc in "${LOCALES[@]}"; do
  check "$loc/name.txt"              30
  check "$loc/subtitle.txt"          30
  check "$loc/description.txt"       4000
  check "$loc/keywords.txt"          100
  check "$loc/promotional_text.txt"  170
  check "$loc/support_url.txt"       255
  check "$loc/marketing_url.txt"     255
  check "$loc/privacy_url.txt"       255
done

check copyright.txt               255
check review_information/notes.txt 4000
check beta/description.txt        4000
check beta/what_to_test.txt       4000
check beta/review_notes.txt       4000
check beta/feedback_email.txt     255

# Keywords: comma-separated, no empty entries.
for loc in en-US "${LOCALES[@]}"; do
  [[ -f $loc/keywords.txt ]] || continue
  if grep -q ',,\|^,\|,$' "$loc/keywords.txt"; then echo "$loc/keywords.txt: empty keyword"; fail=1; fi
  if grep -q ', ' "$loc/keywords.txt"; then echo "$loc/keywords.txt: space after comma"; fail=1; fi
done
# Terminology rule: user-facing copy never says PRD.
if grep -rnwE 'PRDs?' . ; then echo "PRD found in user-facing metadata"; fail=1; fi

exit $fail
