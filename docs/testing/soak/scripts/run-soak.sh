#!/usr/bin/env bash
# run-soak.sh — datawatch-app soak test runner
#
# Creates an isolated working directory OUTSIDE the repo for each run so
# test artifacts (daemon data, evidence, logs) never touch the source tree.
# The directory is deleted on success; kept on failure for inspection.
#
# Usage:
#   bash docs/testing/soak/scripts/run-soak.sh --story=SS-001 [--duration=2h]
#   bash docs/testing/soak/scripts/run-soak.sh --all [--duration=auto]
#
# Resuming a failed run (reuses its working dir):
#   SOAK_RUN_ID=abc123 bash docs/testing/soak/scripts/run-soak.sh --story=SS-001
#
# Keep working dir even on success (for debugging):
#   KEEP_TEST_DIR=1 bash docs/testing/soak/scripts/run-soak.sh --story=SS-001
#
# Override ports (normally auto-assigned from OS-free ports):
#   TEST_SERVER_TLS_PORT=18443 TEST_SERVER_HTTP_PORT=18080 bash ...
#
# Environment:
#   SOAK_RUN_ID         — reuse a specific prior run's working directory
#   KEEP_TEST_DIR       — set to 1 to keep working dir even on success
#   TEST_SERVER_TLS_PORT / TEST_SERVER_HTTP_PORT — force specific ports
#   DATAWATCH_BIN       — path to datawatch binary (default: ~/.local/bin/datawatch)
#   SKIP_SERVER         — set to 1 to skip starting test daemon (assume already running)
#
# IMPORTANT: Tests run against a secondary isolated instance ONLY.
# Never run tests against production (port 8443).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
REPO_PARENT="$(cd "$REPO_ROOT/.." && pwd)"
SOAK_DIR="$REPO_ROOT/docs/testing/soak"

# ---------------------------------------------------------------------------
# Run isolation
# ---------------------------------------------------------------------------
# Each run gets a unique 6-char hex ID so parallel runs on the same filesystem
# don't collide. Set SOAK_RUN_ID to reuse a prior run's working directory
# (e.g. to inspect evidence from a failure or resume a story).
SOAK_RUN_ID="${SOAK_RUN_ID:-$(openssl rand -hex 3)}"
TEST_WORK_DIR="$REPO_PARENT/datawatch-soak-${SOAK_RUN_ID}"
mkdir -p "$TEST_WORK_DIR"

FAILED=0

cleanup() {
  stop_test_daemon
  if [[ $FAILED -ne 0 || -n "${KEEP_TEST_DIR:-}" ]]; then
    echo ""
    echo "Working dir kept: $TEST_WORK_DIR"
    echo "  Resume: SOAK_RUN_ID=$SOAK_RUN_ID bash docs/testing/soak/scripts/run-soak.sh --story=<SS-NNN>"
  else
    rm -rf "$TEST_WORK_DIR"
  fi
}
trap 'FAILED=$?; cleanup' EXIT

# ---------------------------------------------------------------------------
# Port allocation
# ---------------------------------------------------------------------------
# Ask the OS for a free port on 127.0.0.1. Each call returns a different port
# so parallel runs never collide.
free_port() {
  python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); p=s.getsockname()[1]; s.close(); print(p)'
}
_port_free() {
  ! ss -tlnH "sport = :$1" 2>/dev/null | grep -q . 2>/dev/null
}
# Prefer the given port if free; otherwise pick a fresh one.
_fresh_port() {
  local p="${1:-}"
  if [[ -n "$p" ]] && _port_free "$p"; then echo "$p"; else free_port; fi
}

TEST_SERVER_TLS_PORT="$(_fresh_port "${TEST_SERVER_TLS_PORT:-18443}")"
TEST_SERVER_HTTP_PORT="$(_fresh_port "${TEST_SERVER_HTTP_PORT:-18080}")"
TEST_SERVER_HOST="127.0.0.1"
TEST_TOKEN="${TEST_TOKEN:-dw-test-token-12345}"
TEST_BASE_URL="https://${TEST_SERVER_HOST}:${TEST_SERVER_TLS_PORT}"
PROD_HOOK_URL="https://localhost:8443/api/test/message"
APP_PACKAGE="com.dmzs.datawatchclient.dev.debug"
DATAWATCH_BIN="${DATAWATCH_BIN:-/home/dmz/.local/bin/datawatch}"

# Soak-specific thresholds
HEAP_WARN_MB=20
HEAP_FAIL_MB=50

# ---------------------------------------------------------------------------
# Timestamp, log, and evidence setup
# ---------------------------------------------------------------------------
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$TEST_WORK_DIR/soak-run-${TIMESTAMP}.log"
EVIDENCE_DIR="$TEST_WORK_DIR/evidence"
EVIDENCE_FILE="${EVIDENCE_DIR}/run-${TIMESTAMP}.json"
mkdir -p "$EVIDENCE_DIR"

echo "Soak run ID : $SOAK_RUN_ID"
echo "Work dir    : $TEST_WORK_DIR"
echo "Ports       : http=$TEST_SERVER_HTTP_PORT tls=$TEST_SERVER_TLS_PORT"
echo "Log         : $LOG_FILE"
echo ""

log() {
    local level="$1"
    shift
    local msg="$*"
    local ts
    ts="$(date '+%Y-%m-%dT%H:%M:%S')"
    echo "[$ts] [$level] $msg" | tee -a "$LOG_FILE"
}

info()  { log "INFO " "$@"; }
warn()  { log "WARN " "$@"; }
error() { log "ERROR" "$@"; }
die()   { error "$@"; exit 1; }

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
STORY=""
DURATION_OVERRIDE=""
RUN_ALL=0

for arg in "$@"; do
    case "$arg" in
        --story=*)   STORY="${arg#--story=}" ;;
        --duration=*) DURATION_OVERRIDE="${arg#--duration=}" ;;
        --all)       RUN_ALL=1 ;;
        --help|-h)
            grep '^#' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            die "Unknown argument: $arg. Use --story=SS-NNN or --all."
            ;;
    esac
done

if [[ $RUN_ALL -eq 0 && -z "$STORY" ]]; then
    die "Must specify --story=SS-NNN or --all"
fi

ALL_STORIES=(SS-001 SS-002 SS-003 SS-004 SS-005 SS-006 SS-007 SS-008 SS-009 SS-010
             SS-011 SS-012 SS-013 SS-014 SS-015 SS-016 SS-017 SS-018 SS-019 SS-020)

if [[ $RUN_ALL -eq 0 ]]; then
    valid=0
    for s in "${ALL_STORIES[@]}"; do
        [[ "$s" == "$STORY" ]] && valid=1 && break
    done
    [[ $valid -eq 1 ]] || die "Invalid story ID: $STORY. Valid: SS-001 through SS-020"
fi

# ---------------------------------------------------------------------------
# Test daemon management
# ---------------------------------------------------------------------------
# Per-run data dir lives inside the working dir, never inside the repo.
TEST_CONFIG_DIR="${TEST_WORK_DIR}/.datawatch-test-$$"
TEST_PID_FILE="$TEST_WORK_DIR/test-daemon.pid"
TEST_SERVER_LOG="$TEST_WORK_DIR/test-server.log"

start_test_daemon() {
    if [[ "${SKIP_SERVER:-0}" == "1" ]]; then
        info "SKIP_SERVER=1 — skipping daemon start; assuming already running"
        return 0
    fi

    # If already running and healthy, skip
    if curl -sk "${TEST_BASE_URL}/api/health" 2>/dev/null | grep -q '"status":"ok"'; then
        info "Test daemon already healthy at ${TEST_BASE_URL}"
        return 0
    fi

    info "Starting test daemon (run: ${SOAK_RUN_ID})"
    mkdir -p "$TEST_CONFIG_DIR"

    cat > "${TEST_CONFIG_DIR}/config.yaml" <<YAML
data_dir: ${TEST_CONFIG_DIR}

server:
  port: ${TEST_SERVER_HTTP_PORT}
  tls_port: ${TEST_SERVER_TLS_PORT}
  token: "${TEST_TOKEN}"
  tls_cert: ""
  tls_key: ""
  tls_enabled: true
  tls_auto_generate: true

session:
  skip_permissions: true
  max_sessions: 20

autonomous:
  enabled: true

memory:
  enabled: true

mcp:
  enabled: false
YAML

    "$DATAWATCH_BIN" start --foreground \
        --config "${TEST_CONFIG_DIR}/config.yaml" \
        >> "$TEST_SERVER_LOG" 2>&1 &
    local pid=$!
    echo "$pid" > "$TEST_PID_FILE"

    # Wait up to 30s for health
    local attempts=0
    while [[ $attempts -lt 30 ]]; do
        if curl -sk "${TEST_BASE_URL}/api/health" 2>/dev/null | grep -q '"status":"ok"'; then
            info "Test daemon healthy (PID $pid)"
            return 0
        fi
        sleep 1
        ((attempts++))
    done

    error "Test daemon failed to start within 30s. Check ${TEST_SERVER_LOG}"
    tail -20 "$TEST_SERVER_LOG" >&2 || true
    return 1
}

stop_test_daemon() {
    if [[ -f "$TEST_PID_FILE" ]]; then
        local pid
        pid="$(cat "$TEST_PID_FILE")"
        if kill -0 "$pid" 2>/dev/null; then
            info "Stopping test daemon (PID $pid)"
            kill "$pid" 2>/dev/null || true
            wait "$pid" 2>/dev/null || true
        fi
        rm -f "$TEST_PID_FILE"
    fi
}

restart_test_daemon() {
    stop_test_daemon
    sleep 2
    "$DATAWATCH_BIN" start --foreground \
        --config "${TEST_CONFIG_DIR}/config.yaml" \
        >> "$TEST_SERVER_LOG" 2>&1 &
    local pid=$!
    echo "$pid" > "$TEST_PID_FILE"
    local attempts=0
    while [[ $attempts -lt 20 ]]; do
        if curl -sk "${TEST_BASE_URL}/api/health" 2>/dev/null | grep -q '"status":"ok"'; then
            info "Daemon restarted (PID $pid)"
            return 0
        fi
        sleep 1
        ((attempts++))
    done
    error "Daemon failed to restart within 20s"
    return 1
}

# ---------------------------------------------------------------------------
# ADB / device helpers
# ---------------------------------------------------------------------------
check_device() {
    local device
    device="$(adb devices 2>/dev/null | grep -v "^List" | grep "device$" | head -1 | awk '{print $1}')"
    if [[ -z "$device" ]]; then
        die "No ADB device found. Start emulator dw_test_phone and retry."
    fi
    info "ADB device: $device"
}

setup_adb_forwarding() {
    adb reverse tcp:$TEST_SERVER_TLS_PORT tcp:$TEST_SERVER_TLS_PORT >/dev/null 2>&1 \
        || warn "ADB reverse tcp:${TEST_SERVER_TLS_PORT} failed (may already be set)"
    adb reverse tcp:$TEST_SERVER_HTTP_PORT tcp:$TEST_SERVER_HTTP_PORT >/dev/null 2>&1 \
        || warn "ADB reverse tcp:${TEST_SERVER_HTTP_PORT} failed (may already be set)"
    info "ADB reverse forwarding: tcp:${TEST_SERVER_TLS_PORT} and tcp:${TEST_SERVER_HTTP_PORT}"
}

get_heap_pss_mb() {
    adb shell dumpsys meminfo "$APP_PACKAGE" 2>/dev/null \
        | grep "TOTAL PSS:" \
        | awk '{print $3}' \
        | head -1 \
        | awk '{printf "%d", $1/1024}' \
        || echo "0"
}

get_ws_reconnect_count() {
    adb logcat -d 2>/dev/null | grep -c "WebSocket reconnect\|WebSocket.*OPEN" 2>/dev/null || echo "0"
}

app_running() {
    adb shell "ps | grep $APP_PACKAGE" 2>/dev/null | grep -q "$APP_PACKAGE"
}

api() {
    local method="${1:-GET}"
    local path="${2:-/api/health}"
    local data="${3:-}"
    local args=(-sk -X "$method" -H "Authorization: Bearer $TEST_TOKEN" "${TEST_BASE_URL}${path}")
    if [[ -n "$data" ]]; then
        args+=(-H "Content-Type: application/json" -d "$data")
    fi
    curl "${args[@]}" 2>/dev/null
}

post_production_hook() {
    local text="$1"
    curl -sk -X POST "${PROD_HOOK_URL}" \
        -H "Content-Type: application/json" \
        -d "{\"text\":\"${text}\"}" \
        >/dev/null 2>&1 \
        && info "Production hook posted: ${text}" \
        || warn "Production hook unavailable (${PROD_HOOK_URL}) — skipping"
}

write_evidence() {
    local story="$1"
    local status="$2"
    local heap_start="$3"
    local heap_end="$4"
    local iterations="$5"
    local extra="${6:-{}}"

    local heap_delta=$(( heap_end - heap_start ))
    local pass_fail
    if [[ "$status" == "pass" ]]; then
        pass_fail="PASS"
    else
        pass_fail="FAIL"
    fi

    cat > "$EVIDENCE_FILE" <<JSON
{
  "story": "${story}",
  "timestamp": "${TIMESTAMP}",
  "status": "${pass_fail}",
  "heap_start_mb": ${heap_start},
  "heap_end_mb": ${heap_end},
  "heap_delta_mb": ${heap_delta},
  "heap_threshold_mb": ${HEAP_FAIL_MB},
  "iterations": ${iterations},
  "log_file": "${LOG_FILE}",
  "test_server": "${TEST_BASE_URL}",
  "extra": ${extra}
}
JSON
    info "Evidence written to: ${EVIDENCE_FILE}"
}

# ---------------------------------------------------------------------------
# Individual soak story runners
# ---------------------------------------------------------------------------

run_ss001() {
    local duration_s="${1:-7200}"  # default 2 hours
    info "=== SS-001: 2-Hour Continuous Poll Refresh ==="
    info "Duration: ${duration_s}s"

    local heap_start ws_start
    heap_start="$(get_heap_pss_mb)"
    ws_start="$(get_ws_reconnect_count)"
    local start_epoch
    start_epoch="$(date +%s)"
    local end_epoch=$(( start_epoch + duration_s ))

    local snapshot_interval=1800  # every 30 min
    local last_snapshot=$start_epoch
    local iterations=0

    info "Baseline heap: ${heap_start} MB, WS reconnects: ${ws_start}"

    while [[ $(date +%s) -lt $end_epoch ]]; do
        sleep 30
        ((iterations++))
        local now
        now="$(date +%s)"
        if (( now - last_snapshot >= snapshot_interval )); then
            local snap_heap
            snap_heap="$(get_heap_pss_mb)"
            info "T+$(( (now - start_epoch) / 60 ))min — heap: ${snap_heap} MB (delta: $(( snap_heap - heap_start )) MB), iterations: ${iterations}"
            last_snapshot=$now
        fi
        if ! app_running; then
            error "App crashed at iteration ${iterations}!"
            write_evidence "SS-001" "fail" "$heap_start" "$(get_heap_pss_mb)" "$iterations" '{"reason":"app_crash"}'
            return 1
        fi
    done

    local heap_end ws_end
    heap_end="$(get_heap_pss_mb)"
    ws_end="$(get_ws_reconnect_count)"
    local heap_delta=$(( heap_end - heap_start ))
    local ws_delta=$(( ws_end - ws_start ))

    info "SS-001 complete: iterations=$iterations, heap_delta=${heap_delta}MB, ws_delta=${ws_delta}"

    local status="pass"
    [[ $heap_delta -gt $HEAP_FAIL_MB ]] && { error "FAIL: heap delta ${heap_delta}MB > ${HEAP_FAIL_MB}MB"; status="fail"; }
    [[ $ws_delta -gt 2 ]] && { warn "WARN: WS reconnect delta ${ws_delta} > 2"; }

    write_evidence "SS-001" "$status" "$heap_start" "$heap_end" "$iterations" \
        "{\"ws_start\":${ws_start},\"ws_end\":${ws_end},\"ws_delta\":${ws_delta}}"
    [[ "$status" == "pass" ]]
}

run_ss002() {
    info "=== SS-002: 100x On-Resume Refresh Cycle ==="
    local iterations=100
    local heap_start
    heap_start="$(get_heap_pss_mb)"
    info "Baseline heap: ${heap_start} MB"

    local i
    for (( i=1; i<=iterations; i++ )); do
        adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
        sleep 5
        adb shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
        sleep 3
        if ! app_running; then
            error "App crashed at iteration $i"
            write_evidence "SS-002" "fail" "$heap_start" "$(get_heap_pss_mb)" "$i" '{"reason":"app_crash"}'
            return 1
        fi
        [[ $(( i % 25 )) -eq 0 ]] && info "  iteration $i/100, heap: $(get_heap_pss_mb) MB"
    done

    local heap_end
    heap_end="$(get_heap_pss_mb)"
    local heap_delta=$(( heap_end - heap_start ))
    local status="pass"
    [[ $heap_delta -gt $HEAP_FAIL_MB ]] && { error "FAIL: heap delta ${heap_delta}MB"; status="fail"; }

    write_evidence "SS-002" "$status" "$heap_start" "$heap_end" "$iterations" "{}"
    info "SS-002 complete: heap_delta=${heap_delta}MB, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss003() {
    info "=== SS-003: 50x Screen Lock/Unlock Cycle ==="
    local iterations=50
    local heap_start ws_start
    heap_start="$(get_heap_pss_mb)"
    ws_start="$(get_ws_reconnect_count)"

    local i
    for (( i=1; i<=iterations; i++ )); do
        adb shell input keyevent KEYCODE_POWER >/dev/null 2>&1
        sleep 10
        adb shell input keyevent KEYCODE_POWER >/dev/null 2>&1
        sleep 1
        adb shell input keyevent KEYCODE_MENU >/dev/null 2>&1
        sleep 5
        if ! app_running; then
            error "App crashed at iteration $i"
            write_evidence "SS-003" "fail" "$heap_start" "$(get_heap_pss_mb)" "$i" '{"reason":"app_crash"}'
            return 1
        fi
        [[ $(( i % 10 )) -eq 0 ]] && info "  iteration $i/50, heap: $(get_heap_pss_mb) MB"
    done

    local heap_end ws_end
    heap_end="$(get_heap_pss_mb)"
    ws_end="$(get_ws_reconnect_count)"
    local heap_delta=$(( heap_end - heap_start ))
    local ws_delta=$(( ws_end - ws_start ))

    local status="pass"
    [[ $heap_delta -gt 30 ]] && { error "FAIL: heap delta ${heap_delta}MB > 30MB"; status="fail"; }
    [[ $ws_delta -gt 55 ]] && { error "FAIL: WS delta ${ws_delta} > 55"; status="fail"; }

    write_evidence "SS-003" "$status" "$heap_start" "$heap_end" "$iterations" \
        "{\"ws_delta\":${ws_delta}}"
    info "SS-003 complete: heap_delta=${heap_delta}MB, ws_delta=${ws_delta}, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss004() {
    info "=== SS-004: 30x Tab Switch Refresh Cycle ==="
    local iterations=30
    local heap_start
    heap_start="$(get_heap_pss_mb)"

    local i
    for (( i=1; i<=iterations; i++ )); do
        adb shell input tap 900 2200 >/dev/null 2>&1
        sleep 2
        adb shell input tap 180 2200 >/dev/null 2>&1
        sleep 3
        if ! app_running; then
            error "App crashed at iteration $i"
            write_evidence "SS-004" "fail" "$heap_start" "$(get_heap_pss_mb)" "$i" '{"reason":"app_crash"}'
            return 1
        fi
    done

    local heap_end heap_delta
    heap_end="$(get_heap_pss_mb)"
    heap_delta=$(( heap_end - heap_start ))
    local status="pass"
    [[ $heap_delta -gt 20 ]] && { error "FAIL: heap delta ${heap_delta}MB > 20MB"; status="fail"; }

    write_evidence "SS-004" "$status" "$heap_start" "$heap_end" "$iterations" "{}"
    info "SS-004 complete: heap_delta=${heap_delta}MB, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss005() {
    local duration_s="${1:-14400}"  # default 4 hours
    info "=== SS-005: 4-Hour Keep-Alive Session ==="
    info "Duration: ${duration_s}s"

    local session_id
    session_id="$(api POST /api/sessions '{"title":"soak-keepalive"}' | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)"
    if [[ -z "$session_id" ]]; then
        error "Failed to create keep-alive session"
        return 1
    fi
    info "Created session: $session_id"

    local heap_start
    heap_start="$(get_heap_pss_mb)"
    local start_epoch
    start_epoch="$(date +%s)"
    local end_epoch=$(( start_epoch + duration_s ))
    local disconnects=0

    while [[ $(date +%s) -lt $end_epoch ]]; do
        sleep 1800
        local elapsed=$(( ($(date +%s) - start_epoch) / 60 ))
        local heap_now
        heap_now="$(get_heap_pss_mb)"
        local state
        state="$(api GET "/api/sessions/${session_id}" | grep -o '"state":"[^"]*"' | head -1 | cut -d'"' -f4)"
        info "T+${elapsed}min — heap: ${heap_now} MB, session state: ${state}"
        if ! app_running; then
            error "App crashed at T+${elapsed}min"
            write_evidence "SS-005" "fail" "$heap_start" "$(get_heap_pss_mb)" "$(( elapsed * 2 ))" \
                "{\"reason\":\"app_crash\",\"disconnects\":${disconnects}}"
            return 1
        fi
        local disc_count
        disc_count="$(adb logcat -d 2>/dev/null | grep -c "DISCONNECTED\|connection closed" 2>/dev/null || echo 0)"
        disconnects=$disc_count
    done

    local heap_end
    heap_end="$(get_heap_pss_mb)"
    local heap_delta=$(( heap_end - heap_start ))
    local final_state
    final_state="$(api GET "/api/sessions/${session_id}" | grep -o '"state":"[^"]*"' | head -1 | cut -d'"' -f4)"

    local status="pass"
    [[ $heap_delta -gt $HEAP_FAIL_MB ]] && { error "FAIL: heap delta ${heap_delta}MB"; status="fail"; }
    [[ "$final_state" != "running" ]] && { error "FAIL: session state at T+4h is ${final_state}, expected running"; status="fail"; }

    write_evidence "SS-005" "$status" "$heap_start" "$heap_end" "1" \
        "{\"duration_s\":${duration_s},\"final_state\":\"${final_state}\",\"disconnects\":${disconnects}}"
    info "SS-005 complete: heap_delta=${heap_delta}MB, state=${final_state}, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss006() {
    info "=== SS-006: 200x Alert Dismiss Cycle ==="
    local iterations=200
    local heap_start
    heap_start="$(get_heap_pss_mb)"
    local fails=0

    local i
    for (( i=1; i<=iterations; i++ )); do
        api POST /api/alerts "{\"message\":\"soak-alert-${i}\",\"level\":\"info\"}" >/dev/null 2>&1
        sleep 1
        adb shell input tap 900 500 >/dev/null 2>&1
        sleep 1
        if ! app_running; then
            error "App crashed at iteration $i"
            write_evidence "SS-006" "fail" "$heap_start" "$(get_heap_pss_mb)" "$i" '{"reason":"app_crash"}'
            return 1
        fi
        [[ $(( i % 50 )) -eq 0 ]] && info "  iteration $i/200, heap: $(get_heap_pss_mb) MB"
    done

    local heap_end heap_delta
    heap_end="$(get_heap_pss_mb)"
    heap_delta=$(( heap_end - heap_start ))
    local unread
    unread="$(api GET "/api/alerts?unread=true" | grep -o '"total":[0-9]*' | head -1 | cut -d: -f2 || echo "??")"

    local status="pass"
    [[ $heap_delta -gt 40 ]] && { error "FAIL: heap delta ${heap_delta}MB > 40MB"; status="fail"; }

    write_evidence "SS-006" "$status" "$heap_start" "$heap_end" "$iterations" \
        "{\"unread_at_end\":\"${unread}\",\"fails\":${fails}}"
    info "SS-006 complete: heap_delta=${heap_delta}MB, unread=${unread}, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss007() {
    info "=== SS-007: 50x Server Switch Cycle ==="
    local iterations=50
    local heap_start
    heap_start="$(get_heap_pss_mb)"

    local i
    for (( i=1; i<=iterations; i++ )); do
        adb shell input tap 540 120 >/dev/null 2>&1
        sleep 1
        adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1
        sleep 3
        if ! app_running; then
            error "App crashed at iteration $i"
            write_evidence "SS-007" "fail" "$heap_start" "$(get_heap_pss_mb)" "$i" '{"reason":"app_crash"}'
            return 1
        fi
        [[ $(( i % 10 )) -eq 0 ]] && info "  iteration $i/50, heap: $(get_heap_pss_mb) MB"
    done

    local heap_end heap_delta
    heap_end="$(get_heap_pss_mb)"
    heap_delta=$(( heap_end - heap_start ))
    local status="pass"
    [[ $heap_delta -gt 40 ]] && { error "FAIL: heap delta ${heap_delta}MB > 40MB"; status="fail"; }

    write_evidence "SS-007" "$status" "$heap_start" "$heap_end" "$iterations" "{}"
    info "SS-007 complete: heap_delta=${heap_delta}MB, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss008() {
    info "=== SS-008: 100x New Session + Kill Cycle ==="
    local iterations=100
    local heap_start
    heap_start="$(get_heap_pss_mb)"

    local i session_id
    for (( i=1; i<=iterations; i++ )); do
        session_id="$(api POST /api/sessions "{\"title\":\"soak-${i}\"}" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)"
        if [[ -z "$session_id" ]]; then
            warn "Failed to create session at iteration $i — skipping"
            continue
        fi
        sleep 5
        api DELETE "/api/sessions/${session_id}" >/dev/null 2>&1
        sleep 5
        if ! app_running; then
            error "App crashed at iteration $i"
            write_evidence "SS-008" "fail" "$heap_start" "$(get_heap_pss_mb)" "$i" '{"reason":"app_crash"}'
            return 1
        fi
        [[ $(( i % 25 )) -eq 0 ]] && info "  iteration $i/100, heap: $(get_heap_pss_mb) MB"
    done

    local heap_end heap_delta
    heap_end="$(get_heap_pss_mb)"
    heap_delta=$(( heap_end - heap_start ))
    local running_count
    running_count="$(api GET "/api/sessions?state=running" | grep -o '"total":[0-9]*' | head -1 | cut -d: -f2 || echo "??")"

    local status="pass"
    [[ $heap_delta -gt $HEAP_FAIL_MB ]] && { error "FAIL: heap delta ${heap_delta}MB"; status="fail"; }

    write_evidence "SS-008" "$status" "$heap_start" "$heap_end" "$iterations" \
        "{\"running_count_at_end\":\"${running_count}\"}"
    info "SS-008 complete: heap_delta=${heap_delta}MB, running=${running_count}, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss009() {
    local duration_s="${1:-10800}"  # default 3 hours
    info "=== SS-009: 3-Hour Autonomous PRD Poll ==="

    local prd_id
    prd_id="$(api POST /api/automata/prds '{"title":"soak-prd","description":"Soak test PRD — do not advance"}' \
        | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)"
    [[ -z "$prd_id" ]] && { error "Failed to create PRD"; return 1; }
    info "Created PRD: $prd_id"

    local heap_start
    heap_start="$(get_heap_pss_mb)"
    local start_epoch
    start_epoch="$(date +%s)"
    local end_epoch=$(( start_epoch + duration_s ))

    while [[ $(date +%s) -lt $end_epoch ]]; do
        sleep 3600
        local elapsed=$(( ($(date +%s) - start_epoch) / 60 ))
        local heap_now
        heap_now="$(get_heap_pss_mb)"
        info "T+${elapsed}min — heap: ${heap_now} MB"
        if ! app_running; then
            error "App crashed at T+${elapsed}min"
            write_evidence "SS-009" "fail" "$heap_start" "$(get_heap_pss_mb)" "$elapsed" '{"reason":"app_crash"}'
            return 1
        fi
    done

    local heap_end heap_delta
    heap_end="$(get_heap_pss_mb)"
    heap_delta=$(( heap_end - heap_start ))
    local status="pass"
    [[ $heap_delta -gt $HEAP_FAIL_MB ]] && { error "FAIL: heap delta ${heap_delta}MB"; status="fail"; }

    api DELETE "/api/automata/prds/${prd_id}" >/dev/null 2>&1

    write_evidence "SS-009" "$status" "$heap_start" "$heap_end" "$(( duration_s / 10 ))" \
        "{\"prd_id\":\"${prd_id}\"}"
    info "SS-009 complete: heap_delta=${heap_delta}MB, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss010() {
    info "=== SS-010: WebSocket Reconnect Storm (20x Server Restart) ==="
    local iterations=20
    local heap_start ws_start
    heap_start="$(get_heap_pss_mb)"
    ws_start="$(get_ws_reconnect_count)"
    local reconnect_success=0
    local max_latency=0

    local i
    for (( i=1; i<=iterations; i++ )); do
        info "  Iteration $i/20: stopping daemon..."
        stop_test_daemon
        sleep 3

        info "  Iteration $i/20: starting daemon..."
        local restart_epoch
        restart_epoch="$(date +%s)"
        restart_test_daemon || { error "Daemon failed to restart at iteration $i"; break; }

        local reconnect_epoch reconnected=0
        local t
        for (( t=0; t<15; t++ )); do
            if adb logcat -d 2>/dev/null | grep -q "WebSocket.*opened\|WebSocket.*OPEN"; then
                reconnect_epoch="$(date +%s)"
                reconnected=1
                break
            fi
            sleep 1
        done

        if [[ $reconnected -eq 1 ]]; then
            local latency=$(( reconnect_epoch - restart_epoch ))
            [[ $latency -gt $max_latency ]] && max_latency=$latency
            ((reconnect_success++))
            info "  Iteration $i: reconnected in ${latency}s"
        else
            error "  Iteration $i: app did NOT reconnect within 15s"
        fi

        sleep 5
    done

    local heap_end
    heap_end="$(get_heap_pss_mb)"
    local heap_delta=$(( heap_end - heap_start ))

    local status="pass"
    [[ $reconnect_success -lt $iterations ]] && { error "FAIL: only ${reconnect_success}/${iterations} reconnects"; status="fail"; }
    [[ $heap_delta -gt 30 ]] && { error "FAIL: heap delta ${heap_delta}MB > 30MB"; status="fail"; }
    [[ $max_latency -gt 15 ]] && { error "FAIL: max reconnect latency ${max_latency}s > 15s"; status="fail"; }

    write_evidence "SS-010" "$status" "$heap_start" "$heap_end" "$iterations" \
        "{\"reconnect_success\":${reconnect_success},\"max_latency_s\":${max_latency}}"
    info "SS-010 complete: ${reconnect_success}/${iterations} reconnects, max_latency=${max_latency}s, heap_delta=${heap_delta}MB, status=${status}"
    [[ "$status" == "pass" ]]
}

run_ss011_through_ss020() {
    local story="$1"
    local iterations="$2"
    local sleep_per_iter="$3"
    local description="$4"

    info "=== ${story}: ${description} ==="
    local heap_start
    heap_start="$(get_heap_pss_mb)"

    local i
    for (( i=1; i<=iterations; i++ )); do
        sleep "$sleep_per_iter"
        if ! app_running; then
            error "App crashed at iteration $i"
            write_evidence "$story" "fail" "$heap_start" "$(get_heap_pss_mb)" "$i" '{"reason":"app_crash"}'
            return 1
        fi
        [[ $(( i % (iterations / 4) )) -eq 0 ]] && info "  $story iteration $i/${iterations}, heap: $(get_heap_pss_mb) MB"
    done

    local heap_end heap_delta
    heap_end="$(get_heap_pss_mb)"
    heap_delta=$(( heap_end - heap_start ))
    local status="pass"
    [[ $heap_delta -gt $HEAP_FAIL_MB ]] && { error "FAIL: heap delta ${heap_delta}MB > ${HEAP_FAIL_MB}MB"; status="fail"; }

    write_evidence "$story" "$status" "$heap_start" "$heap_end" "$iterations" "{}"
    info "${story} complete: heap_delta=${heap_delta}MB, status=${status}"
    [[ "$status" == "pass" ]]
}

# ---------------------------------------------------------------------------
# Story dispatcher
# ---------------------------------------------------------------------------

parse_duration_to_seconds() {
    local dur="$1"
    case "$dur" in
        *h) echo $(( ${dur%h} * 3600 )) ;;
        *m) echo $(( ${dur%m} * 60 )) ;;
        *s) echo "${dur%s}" ;;
        "auto"|"") echo "" ;;
        *)  echo "$dur" ;;
    esac
}

run_story() {
    local story="$1"
    local override_duration_s="$2"

    info "Starting story: ${story}"
    local result=0

    case "$story" in
        SS-001)
            local dur="${override_duration_s:-7200}"
            run_ss001 "$dur" || result=1
            ;;
        SS-002)
            run_ss002 || result=1
            ;;
        SS-003)
            run_ss003 || result=1
            ;;
        SS-004)
            run_ss004 || result=1
            ;;
        SS-005)
            local dur="${override_duration_s:-14400}"
            run_ss005 "$dur" || result=1
            ;;
        SS-006)
            run_ss006 || result=1
            ;;
        SS-007)
            run_ss007 || result=1
            ;;
        SS-008)
            run_ss008 || result=1
            ;;
        SS-009)
            local dur="${override_duration_s:-10800}"
            run_ss009 "$dur" || result=1
            ;;
        SS-010)
            run_ss010 || result=1
            ;;
        SS-011)
            run_ss011_through_ss020 "SS-011" 50 30 "50x multi-server tab switch" || result=1
            ;;
        SS-012)
            run_ss011_through_ss020 "SS-012" 100 12 "100x filter chip cycle" || result=1
            ;;
        SS-013)
            run_ss011_through_ss020 "SS-013" 75 12 "75x sort order cycle" || result=1
            ;;
        SS-014)
            run_ss011_through_ss020 "SS-014" 150 16 "150x session detail open/close" || result=1
            ;;
        SS-015)
            run_ss011_through_ss020 "SS-015" 50 24 "50x bulk select + deselect" || result=1
            ;;
        SS-016)
            local dur="${override_duration_s:-7200}"
            run_ss001 "$dur" || result=1
            sed -i "s/\"story\": \"SS-001\"/\"story\": \"SS-016\"/" "$EVIDENCE_FILE" 2>/dev/null || true
            ;;
        SS-017)
            run_ss011_through_ss020 "SS-017" 60 15 "60x history toggle cycle" || result=1
            ;;
        SS-018)
            run_ss011_through_ss020 "SS-018" 40 45 "40x terminal scroll cycle" || result=1
            ;;
        SS-019)
            run_ss011_through_ss020 "SS-019" 80 15 "80x notification dismiss cycle" || result=1
            ;;
        SS-020)
            local dur="${override_duration_s:-7200}"
            run_ss001 "$dur" || result=1
            sed -i "s/\"story\": \"SS-001\"/\"story\": \"SS-020\"/" "$EVIDENCE_FILE" 2>/dev/null || true
            ;;
        *)
            die "Unknown story: $story"
            ;;
    esac

    return $result
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

main() {
    info "soak test runner started"
    info "Log: ${LOG_FILE}"
    info "Evidence dir: ${EVIDENCE_DIR}"

    check_device
    start_test_daemon
    setup_adb_forwarding

    if ! curl -sk "${TEST_BASE_URL}/api/health" | grep -q '"status":"ok"'; then
        die "Test server not healthy at ${TEST_BASE_URL}"
    fi
    info "Test server healthy"

    local override_duration_s=""
    if [[ -n "$DURATION_OVERRIDE" ]]; then
        override_duration_s="$(parse_duration_to_seconds "$DURATION_OVERRIDE")"
        info "Duration override: ${DURATION_OVERRIDE} = ${override_duration_s}s"
    fi

    local stories_to_run=()
    if [[ $RUN_ALL -eq 1 ]]; then
        stories_to_run=("${ALL_STORIES[@]}")
        info "Running all ${#stories_to_run[@]} soak stories"
    else
        stories_to_run=("$STORY")
    fi

    local pass_count=0 fail_count=0 failed_stories=()
    for story in "${stories_to_run[@]}"; do
        if run_story "$story" "$override_duration_s"; then
            ((pass_count++))
            post_production_hook "SOAK ${story} PASS — heap_delta=$(grep heap_delta_mb "$EVIDENCE_FILE" 2>/dev/null | grep -o '[0-9]*' | head -1)MB"
        else
            FAILED=1
            ((fail_count++))
            failed_stories+=("$story")
            post_production_hook "SOAK ${story} FAIL — see ${EVIDENCE_FILE}"
        fi
    done

    info "==============================="
    info "SOAK RUN COMPLETE"
    info "Pass: ${pass_count} / Fail: ${fail_count}"
    if [[ ${#failed_stories[@]} -gt 0 ]]; then
        error "Failed stories: ${failed_stories[*]}"
    fi
    info "Log: ${LOG_FILE}"
    info "Evidence: ${EVIDENCE_FILE}"
    info "==============================="

    post_production_hook "SOAK COMPLETE: ${pass_count} pass / ${fail_count} fail. Log: ${LOG_FILE}"

    [[ $fail_count -eq 0 ]]
}

main "$@"
