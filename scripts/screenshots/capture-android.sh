#!/usr/bin/env bash
# Capture Google Play / README screenshots of the DEBUG Android build against the
# demo server (see .github/workflows/android-screenshots.yml). Android twin of
# capture.sh (iOS). Needs adb (and emulator when AVD is set), python3, curl.
#
#   capture-android.sh <device>   device = phone | tablet-7 | tablet-10
#
#   APK_PATH     debug APK (composeApp-publicTrack-debug.apk)
#   OUT_DIR      output root; writes <device>/1-sessions.png …
#   SESSION_ID   session to open for the terminal shot
#   AUTOMATON_ID optional; Automaton to open for the detail shot
#   DW_URL       demo server URL as seen from this host   (for tidy_alerts)
#   DW_APP_URL   demo server URL as seen from the emulator (default https://10.0.2.2:18443)
#   DW_TOKEN     demo token
#   AVD          optional: boot this AVD headless (read-only, sized for <device>)
#                and kill it at the end. Unset = use the already-running emulator
#                (android-emulator-runner boots it in CI).
#   SETTLE       seconds to wait after each launch (default 15)
set -euo pipefail

DEVICE="${1:?usage: capture-android.sh phone|tablet-7|tablet-10}"
: "${APK_PATH:?}" "${OUT_DIR:?}" "${SESSION_ID:?}"
DW_URL="${DW_URL:-https://127.0.0.1:18443}"
DW_APP_URL="${DW_APP_URL:-https://10.0.2.2:18443}"
DW_TOKEN="${DW_TOKEN:-dw-test-token-12345}"
SETTLE="${SETTLE:-15}"
PKG=com.dmzs.datawatchclient.debug
ACTIVITY=com.dmzs.datawatchclient.MainActivity

# Play: PNG/JPEG, each side 320–3840 px, long side ≤ 2× short side.
# Density picks the dp width: phone ≈ 411 dp; tablets ≥ 600 dp → two-pane layout.
case "$DEVICE" in
  phone)     SIZE=1080x2160; DENSITY=420 ;;  # 411×823 dp, 2:1 portrait
  tablet-7)  SIZE=1200x1920; DENSITY=240 ;;  # 800×1280 dp portrait
  tablet-10) SIZE=2560x1600; DENSITY=320 ;;  # 1280×800 dp landscape
  *) echo "unknown device '$DEVICE' (phone|tablet-7|tablet-10)" >&2; exit 2 ;;
esac
W=${SIZE%x*}; H=${SIZE#*x}
# Tablets (two-pane): keep the terminal session open in the detail pane on
# every tab after the plain Sessions shot, instead of an empty pane.
detail=()
[ "$DEVICE" = phone ] || detail=(--es dwOpenSession "$SESSION_ID")
DIR="$OUT_DIR/$DEVICE"
mkdir -p "$DIR" "$OUT_DIR/logs"

emu_pid=""
cleanup() {
  [ -n "$emu_pid" ] || return 0
  adb emu kill >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do kill -0 "$emu_pid" 2>/dev/null || return 0; sleep 1; done
  kill "$emu_pid" 2>/dev/null || true  # never registered with adb
}
trap cleanup EXIT

if [ -n "${AVD:-}" ]; then
  # Own console port + ANDROID_SERIAL so adb never talks to another device.
  port="${EMU_PORT:-5580}"
  export ANDROID_SERIAL="emulator-$port"
  # -read-only: leaves the AVD's own disk untouched (throwaway state).
  "${ANDROID_HOME:?}/emulator/emulator" -avd "$AVD" -port "$port" -no-window -no-audio -no-boot-anim \
    -no-snapshot -read-only -gpu swiftshader_indirect -skin "$SIZE" \
    > "$OUT_DIR/logs/emulator-$DEVICE.log" 2>&1 &
  emu_pid=$!
fi
# Not `adb wait-for-device`: it fails at once if the serial isn't listed yet.
for _ in $(seq 1 180); do
  adb get-state >/dev/null 2>&1 && break
  sleep 2
done
for _ in $(seq 1 180); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
  sleep 2
done
[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] || { echo "::error::emulator did not boot" >&2; exit 1; }

adbq() { adb shell "$@" >/dev/null; }

echo "== $DEVICE ($SIZE @ ${DENSITY}dpi) -> $DIR"
adb shell wm size | tr -d '\r'
# The emulator's own size must already match (-skin); only the density is set here.
adbq wm density "$DENSITY"
adbq cmd uimode night yes
for a in window_animation_scale transition_animation_scale animator_duration_scale; do
  adbq settings put global "$a" 0
done
adbq settings put system screen_off_timeout 1800000
adbq svc power stayon true
adbq input keyevent KEYCODE_WAKEUP
adbq wm dismiss-keyguard || true

# Status bar demo mode: 09:41, full battery, Wi-Fi, no notification icons.
# Re-applied before every shot: SystemUI drops it if it restarts, and it is
# not listening yet right after sys.boot_completed.
adbq settings put global sysui_demo_allowed 1
demo() { adbq am broadcast -a com.android.systemui.demo -e command "$@"; }
statusbar_demo() {
  demo enter
  demo clock -e hhmm 0941
  demo battery -e level 100 -e plugged false -e powersave false
  demo network -e wifi show -e level 4 -e fully true
  demo network -e mobile hide
  demo network -e airplane hide
  demo notifications -e visible false
  demo status -e volume hide -e bluetooth hide -e location hide -e alarm hide -e speakerphone hide -e mute hide
}
sleep "${BOOT_SETTLE:-20}"  # let SystemUI / the launcher finish starting

# Fresh install: no profiles or state from an earlier run on this device.
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r -g "$APK_PATH" >/dev/null
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

launch() { # tab [extra am args...]
  local tab=$1; shift
  # -S: cold start, so the debug hooks run before the first composition.
  adb shell am start -S -W -n "$PKG/$ACTIVITY" \
    --es dwTheme dark --ez dwSkipNotifPrompt true --es dwTab "$tab" "$@" >/dev/null
}

# keep-active.py's watcher can make the daemon log a no-op "running → running"
# state alert; mark those read so the Alerts shot shows only meaningful ones
# (same as capture.sh).
tidy_alerts() {
  curl -sk -H "Authorization: Bearer $DW_TOKEN" "$DW_URL/api/alerts" | python3 -c '
import json, sys
for a in json.load(sys.stdin).get("alerts", []):
    if not a.get("read") and "running → running" in a.get("title", ""):
        print(a["id"])' | while read -r id; do
    curl -sk -o /dev/null -X POST -H "Authorization: Bearer $DW_TOKEN" \
      -H 'Content-Type: application/json' --data "{\"id\":\"$id\"}" "$DW_URL/api/alerts"
  done
}

png_size() { # file -> "WxH" from the PNG IHDR
  python3 -c 'import struct,sys; d=open(sys.argv[1],"rb").read(24); assert d[:8]==b"\x89PNG\r\n\x1a\n", "not a PNG"; print("%dx%d" % struct.unpack(">II", d[16:24]))' "$1"
}

shot() { # file
  statusbar_demo
  sleep 2
  adb exec-out screencap -p > "$1"
  echo "$(png_size "$1") $1"
}

# First launch only: seed the "workstation" profile (trust-all; demo server).
launch sessions --es dwSeedURL "$DW_APP_URL" --es dwSeedToken "$DW_TOKEN" --es dwSeedName workstation
sleep $((SETTLE * 2))
adb logcat -d -s DebugLaunchHooks:* | tail -3 || true

launch sessions
sleep "$SETTLE"
shot "$DIR/1-sessions.png"

launch sessions --es dwOpenSession "$SESSION_ID"
sleep $((SETTLE * 2))
shot "$DIR/2-terminal.png"

launch automata "${detail[@]}"
sleep "$SETTLE"
shot "$DIR/3-automata.png"

if [ -n "${AUTOMATON_ID:-}" ]; then
  launch automata --es dwOpenAutomaton "$AUTOMATON_ID" "${detail[@]}"
  sleep "$SETTLE"
  shot "$DIR/4-automaton-detail.png"
fi

tidy_alerts
launch alerts "${detail[@]}"
sleep "$SETTLE"
shot "$DIR/5-alerts.png"

n=6
for tab in observer dashboard settings; do
  launch "$tab" "${detail[@]}"
  sleep "$SETTLE"
  shot "$DIR/$n-$tab.png"
  n=$((n + 1))
done
adb shell am force-stop "$PKG" || true

# Validate: expected size, Play limits (320–3840 px per side, long ≤ 2× short).
bad=0
for f in "$DIR"/*.png; do
  sz=$(png_size "$f"); w=${sz%x*}; h=${sz#*x}
  short=$((w < h ? w : h)); long=$((w < h ? h : w))
  if [ "$sz" != "${W}x${H}" ]; then
    echo "::error::$f is $sz, expected ${W}x${H}"; bad=1
  fi
  if [ "$short" -lt 320 ] || [ "$long" -gt 3840 ] || [ "$long" -gt $((short * 2)) ]; then
    echo "::error::$f ($sz) violates Google Play screenshot limits"; bad=1
  fi
done
count=$(find "$DIR" -name '*.png' | wc -l)
[ "$count" -le 8 ] || { echo "::error::$DIR has $count images (Play allows 8)"; bad=1; }
exit $bad
