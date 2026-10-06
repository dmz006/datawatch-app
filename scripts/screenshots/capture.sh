#!/usr/bin/env bash
# Capture App Store screenshots of the DEBUG simulator build against the demo
# server (see .github/workflows/ios-screenshots.yml). macOS only.
#
#   APP_PATH     path to DatawatchClient.app (Debug-iphonesimulator)
#   OUT_DIR      output root; per-device folders iphone-6.9/ and ipad-13/
#   DW_URL       demo server URL reachable from the simulator
#   DW_TOKEN     demo token
#   SESSION_ID   session to open for ios-terminal.png
#   AUTOMATON_ID optional; Automaton to open for ios-automaton-detail.png
#   SETTLE       seconds to wait after each launch (default 12)
set -euo pipefail

: "${APP_PATH:?}" "${OUT_DIR:?}" "${SESSION_ID:?}"
DW_URL="${DW_URL:-https://127.0.0.1:18443}"
DW_TOKEN="${DW_TOKEN:-dw-test-token-12345}"
SETTLE="${SETTLE:-12}"
HERE="$(cd "$(dirname "$0")" && pwd)"
BID=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP_PATH/Info.plist")

launch() { # udid tab [extra args...]
  local udid=$1 tab=$2; shift 2
  xcrun simctl terminate "$udid" "$BID" >/dev/null 2>&1 || true
  mkdir -p "$OUT_DIR/logs"
  xcrun simctl launch --terminate-running-process \
    --stdout="$OUT_DIR/logs/$udid-$tab.log" --stderr="$OUT_DIR/logs/$udid-$tab.log" "$udid" "$BID" \
    -dwTheme dark -dwSkipNotifPrompt -dwTab "$tab" "$@" >/dev/null
}

shot() { # udid file
  xcrun simctl io "$1" screenshot --type=png "$2" >/dev/null
  sips -g pixelWidth -g pixelHeight "$2" | tail -2 | tr -s ' ' | tr '\n' ' '
  echo " $2"
}

capture_device() { # name folder expected-WxH
  local name=$1 folder=$2 expect=$3
  local udid dir
  udid=$(python3 "$HERE/sim-device.py" "$name")
  dir="$OUT_DIR/$folder"
  mkdir -p "$dir"
  echo "== $name ($udid) -> $dir"

  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b >/dev/null
  xcrun simctl ui "$udid" appearance dark
  xcrun simctl status_bar "$udid" override --time 9:41 --dataNetwork wifi \
    --wifiMode active --wifiBars 3 --cellularMode active --cellularBars 4 \
    --operatorName '' --batteryState charged --batteryLevel 100
  xcrun simctl install "$udid" "$APP_PATH"
  # Pre-grant notifications so no permission sheet can cover content.
  xcrun simctl privacy "$udid" grant notifications "$BID" 2>/dev/null || true

  # First launch only: seed the "workstation" profile (passing the seed args on
  # every launch raced the profile store's load and added duplicates).
  launch "$udid" sessions -dwSeedURL "$DW_URL" -dwSeedToken "$DW_TOKEN" -dwSeedName workstation
  sleep $((SETTLE * 2))

  for tab in sessions automata alerts observer settings; do
    launch "$udid" "$tab"
    sleep "$SETTLE"
    shot "$udid" "$dir/ios-$tab.png"
  done

  if [ -n "${AUTOMATON_ID:-}" ]; then
    launch "$udid" automata -dwOpenAutomaton "$AUTOMATON_ID"
    sleep "$SETTLE"
    shot "$udid" "$dir/ios-automaton-detail.png"
  fi

  # Last: opening a session makes later launches restore it.
  launch "$udid" sessions -dwOpenSession "$SESSION_ID"
  sleep $((SETTLE * 2 + 6))
  shot "$udid" "$dir/ios-terminal.png"

  # Size check.
  local bad=0 f w h
  for f in "$dir"/*.png; do
    w=$(sips -g pixelWidth "$f" | awk '/pixelWidth/{print $2}')
    h=$(sips -g pixelHeight "$f" | awk '/pixelHeight/{print $2}')
    if [ "${w}x${h}" != "$expect" ]; then
      echo "::error::$f is ${w}x${h}, expected $expect"; bad=1
    fi
  done
  xcrun simctl shutdown "$udid" || true
  return $bad
}

rc=0
capture_device "iPhone 17 Pro Max" iphone-6.9 1320x2868 || rc=1
capture_device "iPad Pro 13-inch (M5)" ipad-13 2064x2752 || rc=1
exit $rc
