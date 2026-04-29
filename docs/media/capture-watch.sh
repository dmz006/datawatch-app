#!/usr/bin/env bash
# Capture Wear OS pager pages for the README slideshow.
# Watch must have v0.35.2+ installed (card-border scaffold).
set -euo pipefail

DEVICE="${DEVICE:-192.168.15.52:34533}"
OUT="$(cd "$(dirname "$0")" && pwd)/watch"
mkdir -p "$OUT"

shot() {
  local name="$1"
  adb -s "$DEVICE" shell screencap -p /sdcard/_dw_shot.png
  adb -s "$DEVICE" pull /sdcard/_dw_shot.png "$OUT/${name}.png" > /dev/null
  adb -s "$DEVICE" shell rm /sdcard/_dw_shot.png
  echo "wrote $OUT/${name}.png"
}

# Swipe right-to-left advances HorizontalPager by one page.
swipe_next() {
  adb -s "$DEVICE" shell input swipe 400 220 40 220 250
  sleep 1.5
}

# Cold-launch so we start on page 0 (Monitor).
adb -s "$DEVICE" shell am force-stop com.dmzs.datawatchclient.debug || true
sleep 0.5
adb -s "$DEVICE" shell am start -n com.dmzs.datawatchclient.debug/com.dmzs.datawatchclient.wear.WearMainActivity > /dev/null
sleep 1.2
shot "00-splash"
sleep 3

shot "01-monitor"
swipe_next
shot "02-sessions"
swipe_next
shot "03-prds"
swipe_next
shot "04-servers"
swipe_next
shot "05-about"

echo "done"
