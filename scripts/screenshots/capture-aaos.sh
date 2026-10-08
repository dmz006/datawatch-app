#!/usr/bin/env bash
# Capture README screenshots of the car app (the :auto Car App Library screens)
# on an Android Automotive OS emulator against the demo server
# (.github/workflows/android-screenshots.yml, step "Capture Android Automotive").
#
#   capture-aaos.sh
#
#   APK_PATH    debug APK (composeApp-publicTrack-debug.apk). Its DEBUG manifest
#               adds androidx.car.app.activity.CarAppActivity, which renders the
#               CarAppService through the system Templates Host; release builds
#               have no such activity.
#   OUT_DIR     output root; writes auto/1-home.png …
#   SESSION_ID  session for the session-detail (conversation) shot; AUTO_SESSION_ID
#               (the seeded session waiting on a prompt) wins when set
#   DW_URL      demo server URL as seen from this host (session title lookup)
#   DW_APP_URL  demo server URL as seen from the emulator (default https://10.0.2.2:18443)
#   DW_TOKEN    demo token
#   AVD         optional: boot this AVD headless and kill it at the end. Unset =
#               use the already-running emulator (android-emulator-runner in CI).
#   SETTLE      seconds to wait after each launch (default 12)
#
# Needs an AAOS image that ships the Templates Host
# (android.software.car.templates_host); exits 3 with an ::error:: when the
# image has none, rather than capturing empty screens.
set -euo pipefail

: "${APK_PATH:?}" "${OUT_DIR:?}" "${SESSION_ID:?}"
SESSION_ID="${AUTO_SESSION_ID:-$SESSION_ID}"
DW_URL="${DW_URL:-https://127.0.0.1:18443}"
DW_APP_URL="${DW_APP_URL:-https://10.0.2.2:18443}"
DW_TOKEN="${DW_TOKEN:-dw-test-token-12345}"
SETTLE="${SETTLE:-12}"
PKG=com.dmzs.datawatchclient.debug
PHONE_ACTIVITY=com.dmzs.datawatchclient.MainActivity
CAR_ACTIVITY=androidx.car.app.activity.CarAppActivity
DIR="$OUT_DIR/auto"
mkdir -p "$DIR" "$OUT_DIR/logs"

emu_pid=""
cleanup() {
  [ -n "$emu_pid" ] || return 0
  adb emu kill >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do kill -0 "$emu_pid" 2>/dev/null || return 0; sleep 1; done
  kill "$emu_pid" 2>/dev/null || true
}
trap cleanup EXIT

if [ -n "${AVD:-}" ]; then
  port="${EMU_PORT:-5582}"
  export ANDROID_SERIAL="emulator-$port"
  "${ANDROID_HOME:?}/emulator/emulator" -avd "$AVD" -port "$port" -no-window -no-audio -no-boot-anim \
    -no-snapshot -read-only -gpu swiftshader_indirect \
    > "$OUT_DIR/logs/emulator-aaos.log" 2>&1 &
  emu_pid=$!
fi
for _ in $(seq 1 180); do
  adb get-state >/dev/null 2>&1 && break
  sleep 2
done
for _ in $(seq 1 240); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
  sleep 2
done
[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] || { echo "::error::AAOS emulator did not boot" >&2; exit 1; }

adbq() { adb shell "$@" >/dev/null; }

echo "== aaos -> $DIR"
adb shell getprop ro.build.fingerprint | tr -d '\r'
adb shell wm size | tr -d '\r'
if ! adb shell pm has-feature android.software.car.templates_host | tr -d '\r' | grep -q true; then
  echo "::error::this AAOS image has no Templates Host (android.software.car.templates_host); car app screens cannot render"
  adb shell pm list features | tr -d '\r' | grep -i car || true
  exit 3
fi
adb shell pm list packages | tr -d '\r' | grep -i -E 'templates|car\.app' || true

for a in window_animation_scale transition_animation_scale animator_duration_scale; do
  adbq settings put global "$a" 0
done
adbq settings put system screen_off_timeout 1800000
adbq svc power stayon true
adbq input keyevent KEYCODE_WAKEUP
sleep "${BOOT_SETTLE:-30}"  # car launcher / SystemUI / Templates Host warm-up

# Status bar demo clock where CarSystemUI honours it (harmless where it doesn't:
# the API 34 "with Google Play" image is a user build — no adb root to restart
# CarSystemUI or set the time — and its status bar keeps the real clock).
adbq settings put global sysui_demo_allowed 1
statusbar_demo() {
  adbq am broadcast -a com.android.systemui.demo -e command enter
  adbq am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
  adbq am broadcast -a com.android.systemui.demo -e command notifications -e visible false
}

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r -g "$APK_PATH" >/dev/null
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell cmd package resolve-activity --brief -n "$PKG/$CAR_ACTIVITY" | tr -d '\r' | grep -q "$CAR_ACTIVITY" \
  || { echo "::error::$CAR_ACTIVITY missing from the APK (needs the DEBUG build)"; exit 1; }

# Seed the "workstation" profile through the phone activity's DEBUG hook (same
# package and database the car app reads). AAOS runs it like any parked app.
adb shell am start -S -W -n "$PKG/$PHONE_ACTIVITY" \
  --es dwSeedURL "$DW_APP_URL" --es dwSeedToken "$DW_TOKEN" --es dwSeedName workstation >/dev/null
sleep $((SETTLE * 2))
adb logcat -d -s DebugLaunchHooks:* | tail -3 || true
adb shell am force-stop "$PKG"

title=$(curl -sk -H "Authorization: Bearer $DW_TOKEN" "$DW_URL/api/sessions" | python3 -c '
import json, sys
sid = sys.argv[1]
d = json.load(sys.stdin)
for s in (d.get("sessions", d) if isinstance(d, dict) else d):
    if s.get("id") == sid or s.get("full_id") == sid:
        print(s.get("name") or s.get("task") or sid); break
' "$SESSION_ID" 2>/dev/null || true)
title="${title:-$SESSION_ID}"

png_size() {
  python3 -c 'import struct,sys; d=open(sys.argv[1],"rb").read(24); assert d[:8]==b"\x89PNG\r\n\x1a\n", "not a PNG"; print("%dx%d" % struct.unpack(">II", d[16:24]))' "$1"
}

car() { # [extra am args...] — cold start the car app, optionally at a debug screen
  adb shell am start -S -W -n "$PKG/$CAR_ACTIVITY" "$@" >/dev/null
}

# AAOS emulators have a second (cluster) display; screencap then prints a
# warning into its stdout unless a display id is given. Use port 0 (main).
DISPLAY_ID=$(adb shell dumpsys SurfaceFlinger --display-id | tr -d '\r' \
  | awk '/port=0/ {print $2; exit}')
cap=(screencap -p)
[ -n "$DISPLAY_ID" ] && cap+=(-d "$DISPLAY_ID")

shot() { # file
  statusbar_demo
  sleep 2
  adb exec-out "${cap[@]}" > "$1"
  echo "$(png_size "$1") $1"
}

car
sleep $((SETTLE * 2))
shot "$DIR/1-home.png"

car --es dwAutoScreen sessions
sleep "$SETTLE"
shot "$DIR/2-sessions.png"

# adb shell re-joins arguments into one device shell line: escape the title.
car --es dwAutoScreen session --es dwAutoSession "$SESSION_ID" --es dwAutoSessionTitle "$(printf '%q' "$title")"
sleep "$SETTLE"
shot "$DIR/3-session.png"

car --es dwAutoScreen automata
sleep "$SETTLE"
shot "$DIR/4-automata.png"

car --es dwAutoScreen monitor
sleep "$SETTLE"
shot "$DIR/5-monitor.png"

car --es dwAutoScreen about
sleep "$SETTLE"
shot "$DIR/6-about.png"

adb logcat -d -s AutoDebugLaunchHooks:* DatawatchMsgSvc:* CarApp:* | tail -20 || true
adb logcat -d > "$OUT_DIR/logs/logcat-aaos.txt" || true
adb shell am force-stop "$PKG" || true

# Sanity: the car activity must have been in front, and the shots must differ
# (identical frames = the host never rendered the templates).
adb shell dumpsys activity activities | tr -d '\r' | grep -m3 -E 'mResumedActivity|topResumedActivity' || true
uniq=$(python3 -c 'import hashlib,sys; print(len({hashlib.sha256(open(p,"rb").read()).hexdigest() for p in sys.argv[1:]}))' "$DIR"/*.png)
if [ "$uniq" -lt 4 ]; then
  echo "::error::only $uniq distinct AAOS screenshots — the Templates Host likely did not render the car app"
  exit 1
fi
if ! adb logcat -d -s AutoDebugLaunchHooks:* | grep -q opened; then
  echo "::warning::no AutoDebugLaunchHooks 'opened' log — screens 2-6 may all show the home screen"
fi
