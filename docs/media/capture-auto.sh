#!/usr/bin/env bash
# Capture Android Auto (DHU) screenshots for the README slideshow.
# Requirements:
#   - DHU at $ANDROID_SDK/extras/google/auto/desktop-head-unit (v2.0)
#   - Phone: dev/debug flavor installed, Android Auto developer mode enabled
#     (Settings → Apps → Android Auto → version, tap 10× → Developer settings)
#   - python3 + python-xlib: apt install python3-xlib
#   - imagemagick (import): apt install imagemagick
set -euo pipefail

DEVICE="${DEVICE:-R5CXB186B6L}"
SDK_DIR="${ANDROID_SDK_ROOT:-/home/dmz/workspace/Android/Sdk}"
DHU="$SDK_DIR/extras/google/auto/desktop-head-unit"
DHU_PORT="${DHU_PORT:-5277}"
OUT="$(cd "$(dirname "$0")" && pwd)/auto"
mkdir -p "$OUT"

command -v python3 >/dev/null || { echo "❌ python3 required"; exit 1; }
command -v import  >/dev/null || { echo "❌ imagemagick required"; exit 1; }
[ -x "$DHU" ]      || { echo "❌ DHU not found at $DHU"; exit 1; }

# ── helpers ────────────────────────────────────────────────────────────────

# Python helper: inject a click at window-relative coords.
x_click() {
  local wid=$1 x=$2 y=$3
  python3 - "$wid" "$x" "$y" <<'PYEOF'
import sys, time
from Xlib import X, display as xdisplay
from Xlib.ext import xtest

wid, cx, cy = int(sys.argv[1],0), int(sys.argv[2]), int(sys.argv[3])
d = xdisplay.Display()
root = d.screen().root
win  = d.create_resource_object('window', wid)

# Move pointer relative to window origin then click.
geom = win.get_geometry()
rx   = geom.x + cx
ry   = geom.y + cy
xtest.fake_input(d, X.MotionNotify, False, X.CurrentTime, root, rx, ry, 0)
xtest.fake_input(d, X.ButtonPress,  1,     X.CurrentTime, X.NONE, 0, 0, 0)
xtest.fake_input(d, X.ButtonRelease,1,     X.CurrentTime, X.NONE, 0, 0, 0)
d.flush()
PYEOF
  sleep "${4:-1.5}"
}

# Python helper: send a key event to the focused window.
x_key() {
  local keysym=$1
  python3 - "$keysym" <<'PYEOF'
import sys
from Xlib import X, display as xdisplay, XK
from Xlib.ext import xtest

d = xdisplay.Display()
ks = getattr(XK, 'XK_' + sys.argv[1], None)
if ks is None:
    ks = XK.string_to_keysym(sys.argv[1])
kc = d.keysym_to_keycode(ks)
xtest.fake_input(d, X.KeyPress,   kc, X.CurrentTime, X.NONE, 0, 0, 0)
xtest.fake_input(d, X.KeyRelease, kc, X.CurrentTime, X.NONE, 0, 0, 0)
d.flush()
PYEOF
  sleep "${2:-1}"
}

shot() {
  local name=$1 wid=$2
  local path="$OUT/${name}.png"
  sleep "${3:-1.5}"
  import -window "$wid" "$path"
  echo "wrote $path"
}

# ── DHU startup ────────────────────────────────────────────────────────────

echo "Forwarding ADB port $DHU_PORT …"
adb -s "$DEVICE" forward tcp:$DHU_PORT tcp:$DHU_PORT

echo "Starting DHU …"
DISPLAY=:0 "$DHU" &
DHU_PID=$!
trap 'kill $DHU_PID 2>/dev/null; adb -s "$DEVICE" forward --remove tcp:$DHU_PORT 2>/dev/null' EXIT

echo "Waiting for DHU window + phone handshake (up to 20 s) …"
WID=""
for i in $(seq 1 20); do
  sleep 1
  WID=$(python3 -c "
from Xlib import display as xdisplay, X
d = xdisplay.Display()
root = d.screen().root
def search(w):
    try:
        name = w.get_wm_name() or ''
        if 'Android Auto' in name or 'Desktop Head Unit' in name:
            print(hex(w.id))
            return True
        for c in w.query_tree().children:
            if search(c):
                return True
    except Exception:
        pass
search(root)
" 2>/dev/null | head -1 || true)
  [ -n "$WID" ] && break
done

if [ -z "$WID" ]; then
  echo "❌ DHU window not found after 20 s — is the phone connected and Android Auto running?"
  exit 1
fi
echo "DHU window: $WID"

# Extra dwell after window appears — the phone/DHU protocol handshake
# takes a few more seconds before the first template renders.
sleep 5

# ── Capture screens ────────────────────────────────────────────────────────
# DHU 2.0 default config (default.ini): 800 × 480 px car display.
# Action strip runs across the top at y ≈ 28.
# Main content area starts at y ≈ 58.
# Row height ≈ 52 px (5 rows max per ListTemplate).

shot "01-monitor" "$WID" 2

# Action strip: Sessions (leftmost) ~ x=90, Monitor ~ x=200,
# Server ~ x=310, About ~ x=420 — approximate; actual positions
# depend on the template the app sends. Adjust if rows are off.

x_click "$WID" 90 28   # "Sessions"
shot "02-sessions" "$WID"

# Tap "Waiting input" row (row 3, y ≈ 58+52+52+26 = 188)
x_click "$WID" 400 188
shot "03-waiting-sessions" "$WID"
x_key "Escape"

# Tap "PRDs to review" row (row 4, y ≈ 58+52*3+26 = 240)
x_click "$WID" 400 240
shot "04-waiting-prds" "$WID"
x_key "Escape"

# Server picker via action strip.
x_click "$WID" 310 28   # "Server"
shot "05-server-picker" "$WID"
x_key "Escape"

# About via action strip.
x_click "$WID" 420 28   # "About"
shot "06-about" "$WID"
x_key "Escape"

echo "done — screenshots in $OUT/"
