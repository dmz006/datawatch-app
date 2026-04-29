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

# Launch (cold start). Splash screen (matrix-eye animation) lives
# ~3.2 s — capture it at the 1.4 s mark so the eye + matrix rain are
# fully painted but the navigation hasn't started yet. Then wait
# for the rest of the dwell + navigation to Sessions.
adb -s "$DEVICE" shell am force-stop "$PKG" || true
sleep 0.5
adb -s "$DEVICE" shell am start -n "$ACT" > /dev/null
sleep 1.4
shot "01-splash"
sleep 4
shot "02-sessions"

# v0.42.x bottom nav: 4 tabs (Sessions / PRDs / Alerts / Settings)
# on a 1440-wide phone, nav row centered at ~y=2780.
# Tab centers: Sessions ~180, PRDs ~540, Alerts ~900, Settings ~1260.
tap 540 2780  # PRDs
sleep 2
shot "03-prds"

# Open New PRD dialog via FAB (same position as Sessions FAB),
# screenshot it, then cancel back before moving to Alerts.
tap 1280 2620  # FAB
sleep 2
shot "03b-new-prd"
key KEYCODE_BACK 1.5

tap 900 2780  # Alerts
sleep 2
shot "04-alerts"

tap 1260 2780  # Settings
sleep 2
shot "05-settings-monitor"

# Settings sub-tabs are a ScrollableTabRow at y=556. Tab x-centers
# (verified via uiautomator dump 2026-04-29): Monitor=199,
# General=537, Comms=875, LLM=1213; About lives past the right edge
# until the tab row is scrolled.
tap 537 556  # General
sleep 2
shot "06-settings-general"

tap 875 556  # Comms
sleep 1
# v0.42.x — Comms tab opens with the server list at the top
# (sensitive: hostnames, base URLs). Scroll down past it before
# screenshotting so Authentication + WebServer + Proxy + Channels
# are what's framed.
adb -s "$DEVICE" shell input swipe 720 1800 720 400 400
sleep 2
shot "07-settings-comms"

tap 1213 556  # LLM
sleep 1
# v0.42.x — the Comms-page scroll above persists when we switch
# to LLM (each tab keeps its own ScrollState since the parent
# Column has its own verticalScroll). Scroll the LLM panel back
# to the top so the LLM Configuration card is the first thing
# in frame, not Episodic Memory.
adb -s "$DEVICE" shell input swipe 720 1000 720 2400 400
sleep 1
adb -s "$DEVICE" shell input swipe 720 1000 720 2400 400
sleep 2
shot "08-settings-llm"

# Scroll the tab row to expose About, then tap it.
adb -s "$DEVICE" shell input swipe 1200 556 100 556 200
sleep 1
tap 1240 556  # About
sleep 1
# Same scroll-reset for About.
adb -s "$DEVICE" shell input swipe 720 1000 720 2400 400
sleep 1
adb -s "$DEVICE" shell input swipe 720 1000 720 2400 400
sleep 2
shot "09-settings-about"

# Back to Sessions + open New Session via FAB.
tap 180 2780  # Sessions
sleep 2
# FAB bottom-right (above bottom nav)
tap 1280 2620
sleep 3
shot "10-new-session"

key KEYCODE_BACK 2
sleep 2

# Open first session detail — tap near top of list.
tap 720 540
sleep 4
shot "11-session-detail"

key KEYCODE_BACK 2
sleep 1

# NOTE: the home-screen / widget frame is deliberately NOT captured.
# The home screen may expose confidential user apps / notifications;
# per user direction (2026-04-24) it must never be committed. If
# widget documentation is needed, use a dedicated sandbox launcher
# setup rather than the user's live home screen.

echo "done"
