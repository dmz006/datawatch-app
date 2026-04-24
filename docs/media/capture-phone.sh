#!/usr/bin/env bash
# Capture Android phone screenshots for the README slideshow.
# Assumes `composeApp-publicTrack-debug` is installed and seeded with
# at least one server profile + session. Output lands in
# docs/media/phone/NN-*.png.
set -euo pipefail

DEVICE="${DEVICE:-R5CXB186B6L}"
PKG="${PKG:-com.dmzs.datawatchclient.debug}"
# applicationId has a `.debug` suffix (composeApp/build.gradle.kts),
# but the Java package (manifest `package=`) stays the same. So the
# component name is appId / fully-qualified-class.
ACT="${ACT:-com.dmzs.datawatchclient.debug/com.dmzs.datawatchclient.MainActivity}"
OUT="$(cd "$(dirname "$0")" && pwd)/phone"
mkdir -p "$OUT"

shot() {
  local name="$1"
  local path="$OUT/${name}.png"
  adb -s "$DEVICE" shell screencap -p /sdcard/_dw_shot.png
  adb -s "$DEVICE" pull /sdcard/_dw_shot.png "$path" > /dev/null
  adb -s "$DEVICE" shell rm /sdcard/_dw_shot.png
  echo "wrote $path"
}

tap() { adb -s "$DEVICE" shell input tap "$1" "$2"; sleep "${3:-1.2}"; }
key() { adb -s "$DEVICE" shell input keyevent "$1"; sleep "${2:-0.8}"; }

# Launch (cold start).
adb -s "$DEVICE" shell am force-stop "$PKG" || true
sleep 0.5
adb -s "$DEVICE" shell am start -n "$ACT" > /dev/null
sleep 4
shot "01-sessions"

# Bottom nav positions (3-tab, 1440 wide, nav at ~y=3000).
# Sessions / Alerts / Settings roughly at x = 240, 720, 1200.
tap 720 3000  # Alerts
sleep 2
shot "02-alerts"

tap 1200 3000  # Settings
sleep 2
shot "03-settings-monitor"

# Settings has 5 sub-tabs; tap successive x offsets along the
# ScrollableTabRow at y~210. Tabs: Monitor, General, Comms, LLM, About.
tap 430 210  # General (approx)
sleep 2
shot "04-settings-general"

tap 720 210  # Comms
sleep 2
shot "05-settings-comms"

tap 1000 210  # LLM
sleep 2
shot "06-settings-llm"

tap 1260 210  # About
sleep 2
shot "07-settings-about"

# Back to Sessions + open New Session via FAB.
tap 240 3000  # Sessions
sleep 2
# FAB bottom-right
tap 1300 2820
sleep 3
shot "08-new-session"

# Back to list.
key KEYCODE_BACK 2
sleep 2

# Open first session detail — tap near top of list.
tap 720 700
sleep 4
shot "09-session-detail"

key KEYCODE_BACK 2
sleep 1

# Home screen (widgets, if installed).
key KEYCODE_HOME 2
sleep 2
shot "10-home-widgets"

echo "done"
