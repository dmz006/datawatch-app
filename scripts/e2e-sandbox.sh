#!/usr/bin/env bash
# End-to-end smoke run: isolated sandbox datawatch daemon + dw_test_phone emulator
# + Maestro flows under e2e/maestro/. Optional Wear / Automotive smoke stages.
#
# Runs LOCALLY only (needs KVM emulators, a datawatch binary and real LLM nodes);
# there is no emulator in CI. See docs/testing/e2e-maestro.md.
#
# Usage: scripts/e2e-sandbox.sh [options]
#   --wear            also boot dw_test_watch, install + launch the wear debug APK
#   --auto            also boot dw_test_auto (automotive), install + launch the phone
#                     debug APK (there is no separate automotive build)
#   --no-phone        skip the phone stage (use with --wear / --auto)
#   --flows "a b"     run only these flows (basenames under e2e/maestro/, no .yaml)
#   --skip-build      reuse the already-built debug APKs
#   --keep            do NOT clean up on exit (debugging only; prints how to clean)
#
# Env:
#   DATAWATCH_BIN   daemon binary            (default /home/dmz/.local/bin/datawatch)
#   LLM_ENV_FILE    local LLM node env file  (default /home/dmz/workspace/.datawatch-test-llm-nodes.env)
#   ANDROID_HOME    SDK                      (default /home/dmz/workspace/Android/Sdk)
#   MAESTRO_HOME    Maestro install          (default /home/dmz/workspace/maestro/maestro)
#   E2E_RUN_ID      reuse a run id           (default random 6-hex)
#
# Isolation (docs/testing/test-isolation-guide.md): the sandbox never uses ports
# 8443/8080, lives in /home/dmz/workspace/.datawatch-test-e2e-<runid>, and is only
# ever stopped by its own validated PID. Emulators get dedicated console ports and
# are killed by serial only. Nothing here writes hostnames/tokens into the repo.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${E2E_RUN_ID:-$(openssl rand -hex 3)}"
WS=/home/dmz/workspace
TEST_DIR="$WS/.datawatch-test-e2e-$RUN_ID"
OUT="$WS/tmp/datawatch-app/run/e2e-$RUN_ID"
DATAWATCH_BIN="${DATAWATCH_BIN:-/home/dmz/.local/bin/datawatch}"
LLM_ENV_FILE="${LLM_ENV_FILE:-$WS/.datawatch-test-llm-nodes.env}"
export ANDROID_HOME="${ANDROID_HOME:-$WS/Android/Sdk}"
MAESTRO_HOME="${MAESTRO_HOME:-$WS/maestro/maestro}"
export PATH="$MAESTRO_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
ADB="$ANDROID_HOME/platform-tools/adb"
TOKEN="dw-test-token-12345"          # sandbox-only test token (isolation guide)
PKG="com.dmzs.datawatchclient.debug"
LINK_MARKER="E2E_LINK_OK_$RANDOM"

DO_PHONE=1 DO_WEAR=0 DO_AUTO=0 SKIP_BUILD=0 KEEP=0 ONLY_FLOWS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --wear) DO_WEAR=1 ;;
    --auto) DO_AUTO=1 ;;
    --no-phone) DO_PHONE=0 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --keep) KEEP=1 ;;
    --flows) ONLY_FLOWS="$2"; shift ;;
    -h|--help) sed -n '2,27p' "$0"; exit 0 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done

mkdir -p "$TEST_DIR/work" "$OUT"
LOG="$OUT/run.log"
RESULTS="$OUT/results.tsv"
: > "$RESULTS"
START_TS=$(date +%s)
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }
result() { printf '%s\t%s\t%s\n' "$1" "$2" "${3:-}" >> "$RESULTS"; log "RESULT $1: $2 ${3:-}"; }

port_free() { ! ss -tln 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$1\$"; }
pick_port() { for p in "$@"; do port_free "$p" && { echo "$p"; return; }; done
  python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1])'; }
emu_port_free() { port_free "$1" && port_free "$(($1 + 1))"; }
pick_emu_port() { for p in 5580 5582 5584 5586 5588 5590; do emu_port_free "$p" && { echo "$p"; return; }; done; echo ""; }

HTTP_PORT=$(pick_port 18180 18280 18380)
TLS_PORT=$(pick_port 18543 18643 18743)
MCP_PORT=$(pick_port 18181 18281 18381)
case " $HTTP_PORT $TLS_PORT " in *" 8443 "*|*" 8080 "*) echo "refusing production port" >&2; exit 1 ;; esac
BASE="https://127.0.0.1:$TLS_PORT"
SANDBOX_HOST="dw-app-e2e-$RUN_ID"   # unique: tmux names are cs-<host>-<id>
DAEMON_PID=""
EMULATORS=()   # serials we booted

# ── cleanup (always) ────────────────────────────────────────────────────────
cleanup() {
  local rc=$?
  set +e
  if [ "$KEEP" = 1 ]; then
    log "--keep: leaving sandbox up. BASE=$BASE PID=$DAEMON_PID dir=$TEST_DIR emulators=${EMULATORS[*]}"
    log "clean up later: kill $DAEMON_PID (verify it listens on :$HTTP_PORT first); adb -s <serial> emu kill; rm -rf $TEST_DIR"
    return
  fi
  log "cleanup…"
  "$ADB" shell cmd notification set_dnd off >/dev/null 2>&1
  # 1. sandbox tmux sessions: ask the sandbox which tmux sessions it owns, and only
  #    kill those whose start directory is inside this run's test dir.
  if [ -n "$DAEMON_PID" ] && kill -0 "$DAEMON_PID" 2>/dev/null; then
    for t in $(curl -sk -m 10 -H "Authorization: Bearer $TOKEN" "$BASE/api/sessions" \
        | python3 -c 'import sys,json
try: d=json.load(sys.stdin)
except Exception: d=[]
d=d.get("sessions",d) if isinstance(d,dict) else d
print("\n".join(s.get("tmux_session","") for s in d if s.get("tmux_session")))' 2>/dev/null); do
      p=$(tmux display -p -t "$t" '#{session_path}' 2>/dev/null)
      case "$p" in "$TEST_DIR"*) tmux kill-session -t "$t" 2>/dev/null && log "killed tmux $t" ;; esac
    done
  fi
  # 2. daemon — validated PID only (must be listening on OUR http port).
  if [ -n "$DAEMON_PID" ] && kill -0 "$DAEMON_PID" 2>/dev/null; then
    if ss -tlnp 2>/dev/null | grep -q ":${HTTP_PORT} .*pid=$DAEMON_PID,"; then
      kill "$DAEMON_PID"; for _ in $(seq 1 20); do kill -0 "$DAEMON_PID" 2>/dev/null || break; sleep 0.5; done
      kill -0 "$DAEMON_PID" 2>/dev/null && kill -9 "$DAEMON_PID"
      log "sandbox daemon $DAEMON_PID stopped"
    else
      log "ERROR: PID $DAEMON_PID is not on :$HTTP_PORT — refusing to kill"
    fi
  fi
  # 3. emulators we booted, by serial.
  for s in "${EMULATORS[@]}"; do "$ADB" -s "$s" emu kill >/dev/null 2>&1 && log "emulator $s killed"; done
  # 4. test dir (keep a copy of the daemon log first).
  cp -f "$TEST_DIR/daemon.log" "$OUT/daemon.log" 2>/dev/null
  case "$TEST_DIR" in "$WS"/.datawatch-test-e2e-*) rm -rf "$TEST_DIR" ;; esac
  log "artifacts: $OUT  (elapsed $(( $(date +%s) - START_TS ))s)"
  exit $rc
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# ── 1. sandbox daemon ───────────────────────────────────────────────────────
start_sandbox() {
  log "run $RUN_ID: sandbox http=$HTTP_PORT tls=$TLS_PORT dir=$TEST_DIR bin=$DATAWATCH_BIN"
  cat > "$TEST_DIR/config.yaml" <<EOF
hostname: $SANDBOX_HOST
data_dir: $TEST_DIR/data
session:
  llm_backend: shell
  max_sessions: 10
  input_idle_timeout: 10
  default_project_dir: $TEST_DIR/work
  root_path: $WS
  claude_enabled: false
  auto_git_init: false
  auto_git_commit: false
  kill_sessions_on_exit: true
server:
  enabled: true
  host: 127.0.0.1
  port: $HTTP_PORT
  token: "$TOKEN"
  tls_enabled: true
  tls_port: $TLS_PORT
  tls_auto_generate: true
  channel_port: 0
  auto_restart_on_config: false
mcp:
  enabled: false
  sse_enabled: false
  sse_port: $MCP_PORT
memory:
  enabled: false
shell_backend:
  enabled: true
autonomous:
  enabled: true
whisper:
  enabled: true
  backend: openai_compat
  endpoint: http://127.0.0.1:9/v1
  api_key: dummy
  model: whisper-1
  language: en
update:
  enabled: false
EOF
  # cwd = test dir so shell sessions never start inside the repo.
  (cd "$TEST_DIR/work" && exec "$DATAWATCH_BIN" start --foreground --config "$TEST_DIR/config.yaml") >> "$TEST_DIR/daemon.log" 2>&1 &
  DAEMON_PID=$!
  echo "$DAEMON_PID" > "$TEST_DIR/test-daemon.pid"
  for _ in $(seq 1 60); do
    curl -sk -m 2 "$BASE/api/health" 2>/dev/null | grep -q '"ok"' && break
    kill -0 "$DAEMON_PID" 2>/dev/null || { log "daemon exited"; tail -20 "$TEST_DIR/daemon.log" | tee -a "$LOG"; return 1; }
    sleep 1
  done
  curl -sk -m 2 "$BASE/api/health" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["status"]=="ok" and d["hostname"]==sys.argv[1], d' "$SANDBOX_HOST" \
    || { log "sandbox unhealthy"; return 1; }
  log "sandbox healthy (pid $DAEMON_PID)"
}

api() { curl -sk -m 30 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$@"; }

seed_sandbox() {
  if [ -f "$LLM_ENV_FILE" ]; then
    "$REPO/scripts/sandbox-seed-llms.sh" "$BASE" "$TOKEN" "$LLM_ENV_FILE" 2>&1 | sed 's/^/  seed: /' | tee -a "$LOG" >/dev/null
  else
    log "WARN: $LLM_ENV_FILE missing — council flow will fail (no real LLM)"
  fi
  # A long-lived shell session for the terminal / scroll / deep-link flows.
  local resp
  resp=$(api -X POST "$BASE/api/sessions/start" -d "{\"task\":\"\",\"backend\":\"shell\",\"name\":\"e2e-shell\",\"project_dir\":\"$TEST_DIR/work\"}")
  SESSION_ID=$(echo "$resp" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("id") or d.get("session",{}).get("id",""))' 2>/dev/null)
  FULL_ID=$(echo "$resp" | python3 -c 'import sys,json; d=json.load(sys.stdin); s=d.get("session",d); print(s.get("full_id",""))' 2>/dev/null)
  TMUX_SESSION=$(echo "$resp" | python3 -c 'import sys,json; d=json.load(sys.stdin); s=d.get("session",d); print(s.get("tmux_session",""))' 2>/dev/null)
  [ -z "$FULL_ID" ] && FULL_ID="$SANDBOX_HOST-$SESSION_ID"
  [ -z "$TMUX_SESSION" ] && TMUX_SESSION="cs-$SESSION_ID"
  log "shell session id=$SESSION_ID full=$FULL_ID tmux=$TMUX_SESSION"
  echo "$resp" > "$OUT/session-start.json"
  # Some scrollback so the terminal has content to scroll.
  for i in $(seq 1 3); do
    api -X POST "$BASE/api/sessions/send" -d "{\"session_id\":\"$SESSION_ID\",\"text\":\"seq 1 200; echo E2E_READY_$i\"}" >/dev/null
  done
}

# ── 2. emulator helpers ─────────────────────────────────────────────────────
boot_avd() { # boot_avd <avd> → echoes serial
  local avd="$1" port serial
  port=$(pick_emu_port); [ -z "$port" ] && { log "no free emulator port"; return 1; }
  serial="emulator-$port"
  log "booting $avd on $serial (headless, read-only)"
  "$ANDROID_HOME/emulator/emulator" -avd "$avd" -port "$port" -read-only -no-window -no-audio \
    -no-boot-anim -no-snapshot -gpu swiftshader_indirect >> "$OUT/emulator-$avd.log" 2>&1 &
  EMULATORS+=("$serial")
  "$ADB" -s "$serial" wait-for-device
  for _ in $(seq 1 180); do
    [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
    sleep 2
  done
  [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] || { log "$avd failed to boot"; return 1; }
  "$ADB" -s "$serial" shell settings put global window_animation_scale 0
  "$ADB" -s "$serial" shell settings put global transition_animation_scale 0
  "$ADB" -s "$serial" shell settings put global animator_duration_scale 0
  # Session-alert heads-up notifications drop over the app's header and eat taps.
  "$ADB" -s "$serial" shell settings put global heads_up_notifications_enabled 0
  BOOTED_SERIAL="$serial"
}

fatal_check() { # fatal_check <serial> <pkg> <label>
  local n
  "$ADB" -s "$1" logcat -d > "$OUT/logcat-$3.txt" 2>/dev/null
  n=$(grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $2" "$OUT/logcat-$3.txt" | grep -c "$2\|FATAL" || true)
  if grep -B2 -A20 "FATAL EXCEPTION" "$OUT/logcat-$3.txt" | grep -q "$2"; then return 1; fi
  return 0
}

build_apks() {
  [ "$SKIP_BUILD" = 1 ] && return 0
  local tasks=()
  [ "$DO_PHONE" = 1 ] || [ "$DO_AUTO" = 1 ] && tasks+=(":composeApp:assemblePublicTrackDebug")
  [ "$DO_WEAR" = 1 ] && tasks+=(":wear:assembleDebug")
  [ ${#tasks[@]} -eq 0 ] && return 0
  log "building ${tasks[*]}"
  (cd "$REPO" && ./gradlew "${tasks[@]}" -q) >> "$OUT/gradle.log" 2>&1 || { log "BUILD FAILED (see gradle.log)"; return 1; }
}
PHONE_APK() { ls "$REPO"/composeApp/build/outputs/apk/publicTrack/debug/*.apk 2>/dev/null | head -1; }
WEAR_APK() { ls "$REPO"/wear/build/outputs/apk/debug/*.apk 2>/dev/null | head -1; }

# ── 3. phone stage ──────────────────────────────────────────────────────────
selected() { [ -z "$ONLY_FLOWS" ] || [[ " $ONLY_FLOWS " == *" $1 "* ]]; }
tmux_in_mode() { tmux display -p -t "$TMUX_SESSION" '#{pane_in_mode}' 2>/dev/null; }

run_flow() { # run_flow <name> [extra maestro env...]
  local name="$1"; shift
  local f="$REPO/e2e/maestro/$name.yaml" t0 rc
  selected "$name" || return 3
  t0=$(date +%s)
  mkdir -p "$OUT/flows/$name"
  maestro --device "$PHONE" test "$f" \
    --env SERVER_URL="$BASE" --env SERVER_TOKEN="$TOKEN" --env SERVER_NAME="e2e-sandbox" \
    --env SESSION_ID="$SESSION_ID" --env FULL_ID="$FULL_ID" --env APP_ID="$PKG" \
    --env PROFILE_NAME="e2e-profile-$RUN_ID" --env LINK_MARKER="$LINK_MARKER" "$@" \
    --test-output-dir "$OUT/flows/$name" --debug-output "$OUT/flows/$name" \
    > "$OUT/flows/$name/maestro.log" 2>&1
  rc=$?
  LAST_FLOW_RC=$rc
  if [ $rc -eq 0 ]; then result "$name" PASS "$(( $(date +%s) - t0 ))s"
  else result "$name" FAIL "$(( $(date +%s) - t0 ))s $(grep -E 'FAILED' "$OUT/flows/$name/maestro.log" | grep -v -E 'Run |Repeat ' | tail -1 | tr -s ' ' | cut -c1-160)"; fi
  "$ADB" -s "$PHONE" exec-out screencap -p > "$OUT/flows/$name/final.png" 2>/dev/null
  return $rc
}

# Remove EVERY datawatch app from a test AVD before installing the build under test:
# old release / dev builds kept a saved profile for a real (production) server, and a
# stray tap could open them. Emulators boot -read-only, so this runs every time.
purge_dw_apps() {
  local serial="$1" p
  for p in $("$ADB" -s "$serial" shell pm list packages 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep '^com\.dmzs\.'); do
    "$ADB" -s "$serial" uninstall "$p" >/dev/null 2>&1 && log "removed stale $p from $serial"
  done
}

phone_stage() {
  boot_avd dw_test_phone || { result phone-boot FAIL; return 1; }
  PHONE="$BOOTED_SERIAL"
  "$ADB" -s "$PHONE" reverse "tcp:$TLS_PORT" "tcp:$TLS_PORT"
  purge_dw_apps "$PHONE"
  "$ADB" -s "$PHONE" install -r -g "$(PHONE_APK)" >> "$LOG" 2>&1 || { result phone-install FAIL; return 1; }
  "$ADB" -s "$PHONE" shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null
  "$ADB" -s "$PHONE" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null
  "$ADB" -s "$PHONE" logcat -c

  run_flow 01_add_server
  run_flow 02_sessions_list
  if run_flow 03_open_session; then
    # xterm renders on a canvas: check the terminal area of the screenshot has text
    # pixels (light glyphs on the dark terminal background).
    if python3 - "$OUT/flows/03_open_session/final.png" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("L")
w, h = im.size
box = im.crop((0, int(h * 0.25), w, int(h * 0.80)))
px = list(box.getdata())
lit = sum(1 for v in px if v > 140) / len(px)
print(f"terminal lit-pixel ratio {lit:.4f}")
sys.exit(0 if lit > 0.003 else 1)
PY
    then result 03_terminal_rendered PASS "terminal area has rendered text"
    else result 03_terminal_rendered FAIL "terminal area blank"; fi
  fi

  # Scroll mode: the flow enters, the runner asserts tmux, then exits.
  if selected 04_scroll_mode_enter; then
    local before after_enter after_exit
    before=$(tmux_in_mode)
    run_flow 04_scroll_mode_enter
    after_enter=""; for _ in $(seq 1 10); do after_enter=$(tmux_in_mode); [ "$after_enter" = 1 ] && break; sleep 1; done
    run_flow 05_scroll_mode_exit
    after_exit=""; for _ in $(seq 1 10); do after_exit=$(tmux_in_mode); [ "$after_exit" = 0 ] && break; sleep 1; done
    if [ "$before" = 0 ] && [ "$after_enter" = 1 ] && [ "$after_exit" = 0 ]; then
      result tmux_pane_in_mode PASS "before=$before enter=$after_enter exit=$after_exit"
    else
      result tmux_pane_in_mode FAIL "before=$before enter=$after_enter exit=$after_exit (tmux=$TMUX_SESSION)"
    fi
  fi

  "$ADB" -s "$PHONE" shell cmd notification set_dnd on
  if run_flow 06_voice_dnd; then
    if "$ADB" -s "$PHONE" logcat -d | grep -q "Not allowed to change Do Not Disturb"; then
      result 06_voice_dnd_logcat FAIL "'Not allowed to change Do Not Disturb' in logcat"
    else result 06_voice_dnd_logcat PASS "no DND SecurityException"; fi
  fi
  "$ADB" -s "$PHONE" shell cmd notification set_dnd off

  run_flow 07_settings_general
  if selected 08_council_quick_run; then
    run_flow 08_council_quick_run
    # Latest council run: every persona replied with real (non-empty, non-error) text.
    if api "$BASE/api/council/runs" > "$OUT/council-runs.json" && python3 - "$OUT/council-runs.json" <<'PY'
import sys, json
runs = json.load(open(sys.argv[1]))
runs = runs.get("runs", runs) if isinstance(runs, dict) else runs
assert runs, "no council runs"
r = sorted(runs, key=lambda r: r.get("started_at", ""))[-1]
resp = {}
for rnd in r.get("rounds") or []:
    resp.update(rnd.get("responses") or {})
bad = [k for k, v in resp.items() if len((v or "").strip()) < 20 or (v or "").lower().startswith("error")]
print(f"mode={r.get('mode')} personas={len(r.get('personas') or [])} replies={len(resp)} bad={bad} consensus={len(r.get('consensus') or '')}ch")
sys.exit(0 if resp and not bad and r.get("consensus") and r.get("finished_at") else 1)
PY
    then result 08_council_rest PASS "$(api "$BASE/api/council/runs" | python3 -c 'import sys,json;r=json.load(sys.stdin);r=r.get("runs",r) if isinstance(r,dict) else r;r=sorted(r,key=lambda x:x.get("started_at",""))[-1];print(len([1 for x in r["rounds"] for _ in x["responses"]]),"persona replies + consensus")')"
    else result 08_council_rest FAIL "latest run missing replies/consensus (council-runs.json)"; fi
  fi
  if selected 09_project_profile; then
    run_flow 09_project_profile
    # Independent of the UI outcome: did the save reach the server?
    local code
    code=$(curl -sk -m 10 -o "$OUT/profile.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/profiles/projects/e2e-profile-$RUN_ID")
    if [ "$code" = 200 ] && grep -q "\"e2e-profile-$RUN_ID\"" "$OUT/profile.json"; then
      result 09_project_profile_rest PASS "GET /api/profiles/projects/<name> 200"
    else
      result 09_project_profile_rest FAIL "GET /api/profiles/projects/<name> -> $code"
    fi
  fi
  if selected 10_automata_tab; then
    if [ "$(api "$BASE/api/config" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("autonomous",{}).get("enabled"))')" = True ]; then
      result 10_autonomous_enabled PASS "server autonomous.enabled=true"
    else result 10_autonomous_enabled FAIL "server autonomous.enabled is not true"; fi
    run_flow 10_automata_tab
  fi
  if run_flow 11_deep_link; then
    local seen=""
    for _ in $(seq 1 15); do
      tmux capture-pane -p -t "$TMUX_SESSION" -S -50 2>/dev/null | grep -q "^$LINK_MARKER\$" && { seen=1; break; }
      sleep 1
    done
    if [ -n "$seen" ]; then result 11_deep_link_live PASS "composer input reached tmux"
    else result 11_deep_link_live FAIL "marker $LINK_MARKER not in tmux pane"; fi
  fi

  if fatal_check "$PHONE" "$PKG" phone; then result phone_no_fatal PASS
  else result phone_no_fatal FAIL "FATAL EXCEPTION in logcat-phone.txt"; fi
}

# ── 4. wear / auto smoke ────────────────────────────────────────────────────
smoke_surface() { # smoke_surface <avd> <apk> <label>
  local avd="$1" apk="$2" label="$3" serial
  [ -f "$apk" ] || { result "$label" FAIL "apk missing"; return 1; }
  boot_avd "$avd" || { result "$label" FAIL "boot"; return 1; }
  serial="$BOOTED_SERIAL"
  # Uninstall first: the AVDs may carry an older install whose saved server profile
  # points at a real (production) daemon — never launch the app with that state.
  purge_dw_apps "$serial"
  "$ADB" -s "$serial" install -g "$apk" >> "$LOG" 2>&1 || { result "$label" FAIL "install"; return 1; }
  "$ADB" -s "$serial" logcat -c
  "$ADB" -s "$serial" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  local pid=""
  for _ in $(seq 1 30); do pid=$("$ADB" -s "$serial" shell pidof "$PKG" 2>/dev/null | tr -d '\r'); [ -n "$pid" ] && break; sleep 1; done
  sleep 8   # let the first frames render / any startup crash surface
  "$ADB" -s "$serial" exec-out screencap -p > "$OUT/$label.png" 2>/dev/null
  pid=$("$ADB" -s "$serial" shell pidof "$PKG" 2>/dev/null | tr -d '\r')
  if ! fatal_check "$serial" "$PKG" "$label"; then result "$label" FAIL "FATAL EXCEPTION (logcat-$label.txt)"
  elif [ -z "$pid" ]; then result "$label" FAIL "process not running after launch"
  else result "$label" PASS "pid $pid"; fi
  "$ADB" -s "$serial" emu kill >/dev/null 2>&1
}

# ── main ────────────────────────────────────────────────────────────────────
command -v maestro >/dev/null || { echo "maestro not found under $MAESTRO_HOME (see docs/testing/e2e-maestro.md)"; exit 1; }
start_sandbox || exit 1
seed_sandbox
build_apks || exit 1
[ "$DO_PHONE" = 1 ] && phone_stage
[ "$DO_WEAR" = 1 ] && smoke_surface dw_test_watch "$(WEAR_APK)" wear_smoke
[ "$DO_AUTO" = 1 ] && smoke_surface dw_test_auto "$(PHONE_APK)" auto_smoke

log "──── results ($(( $(date +%s) - START_TS ))s) ────"
column -t -s $'\t' "$RESULTS" | tee -a "$LOG"
! grep -q $'\tFAIL' "$RESULTS"
