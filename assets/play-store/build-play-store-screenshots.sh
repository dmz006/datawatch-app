#!/usr/bin/env bash
# Build the Play Store screenshot set from a connected device.
#
#   ./build-play-store-screenshots.sh
#
# Steps:
#   1. Re-uses docs/media/capture-phone.sh (home-screen-banned per
#      v0.35.6 security note) to drive the phone through Sessions →
#      Alerts → Settings sub-tabs → New Session → Session detail.
#   2. Validates each frame is a unique app screen (md5 dedup —
#      identical frames mean the script's tap missed and the same
#      screen was captured twice; those frames are pruned so we
#      never publish a duplicate).
#   3. Picks the first 6 unique screens for the Play Store phone
#      slot (Play Store accepts 2–8; 6 is the sweet spot).
#   4. Generates 7-inch and 10-inch tablet variants by scaling the
#      phone frames with a letterboxed dark surround. Play Store
#      validates dimensions, not capture-device — and our app has
#      no tablet-specific layout, so a faithful upscale matches
#      what a tablet user would actually see.
#
# Output layout:
#   assets/play-store/phone/      *.png at native 1440×3120
#   assets/play-store/tablet-7/   *.png at 1080×1920 portrait
#   assets/play-store/tablet-10/  *.png at 1600×2560 portrait
#
set -euo pipefail

DEVICE="${DEVICE:-R5CXB186B6L}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PLAY_DIR="$ROOT/assets/play-store"
PHONE_DIR="$ROOT/docs/media/phone"
PHONE_OUT="$PLAY_DIR/phone"
T7_OUT="$PLAY_DIR/tablet-7"
T10_OUT="$PLAY_DIR/tablet-10"

# Confirm device is online before doing anything.
if ! adb -s "$DEVICE" get-state >/dev/null 2>&1; then
  echo "device $DEVICE not connected — plug in or run \`adb connect …\`" >&2
  exit 1
fi

# Wake the phone (silently noop if already awake) — the capture
# script needs the screen on. If the phone is locked, the user
# must unlock manually; we don't bypass keyguard.
adb -s "$DEVICE" shell input keyevent KEYCODE_WAKEUP || true
showing="$(adb -s "$DEVICE" shell dumpsys window policy 2>/dev/null | grep -m1 mIsShowing | awk -F= '{print $2}' | tr -d '[:space:]')"
if [ "$showing" = "true" ]; then
  echo "phone keyguard is showing — unlock $DEVICE then re-run." >&2
  exit 2
fi

mkdir -p "$PHONE_OUT" "$T7_OUT" "$T10_OUT"
rm -f "$PHONE_OUT"/*.png "$T7_OUT"/*.png "$T10_OUT"/*.png

# Drive the phone through every captured screen.
DEVICE="$DEVICE" "$ROOT/docs/media/capture-phone.sh"

# Dedup — drop any frame whose md5 matches a prior frame. Identical
# frames mean a tap missed and the same screen was captured twice;
# never publish dupes to the Play Store.
declare -A seen
keepers=()
for f in $(ls "$PHONE_DIR"/*.png | sort); do
  h="$(md5sum "$f" | awk '{print $1}')"
  if [ -z "${seen[$h]:-}" ]; then
    seen[$h]=1
    keepers+=("$f")
  fi
done
echo "found ${#keepers[@]} unique app frames"

# Promote the first 6 unique frames to the Play Store phone slot.
i=0
for f in "${keepers[@]}"; do
  i=$((i + 1))
  if [ "$i" -gt 6 ]; then break; fi
  cp "$f" "$PHONE_OUT/$(printf 'phone-%02d.png' "$i")"
done

# 7-inch tablet — Play Store min 1080×1920 portrait. We scale the
# phone capture (1440×3120) down to 1080 wide and pad the height
# with the same dark gradient as the feature graphic so the result
# is a clean tablet-shaped frame, not a stretched phone.
for f in "$PHONE_OUT"/*.png; do
  name="$(basename "$f")"
  convert "$f" -resize 1080x \
    -background '#0F1117' -gravity Center -extent 1080x1920 \
    "$T7_OUT/$name"
done

# 10-inch tablet — 1600×2560 portrait, same letterbox treatment.
for f in "$PHONE_OUT"/*.png; do
  name="$(basename "$f")"
  convert "$f" -resize 1600x \
    -background '#0F1117' -gravity Center -extent 1600x2560 \
    "$T10_OUT/$name"
done

echo "wrote:"
echo "  $PHONE_OUT/  ($(ls "$PHONE_OUT" | wc -l) files)"
echo "  $T7_OUT/     ($(ls "$T7_OUT" | wc -l) files)"
echo "  $T10_OUT/    ($(ls "$T10_OUT" | wc -l) files)"
