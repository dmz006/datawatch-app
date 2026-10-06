#!/usr/bin/env bash
# Seed the screenshot demo server with realistic, non-sensitive content.
#
#   DEMO_ROOT  per-run directory (same value substituted into demo-config.yaml)
#   DW_URL     demo server base URL   (default https://127.0.0.1:18443)
#   DW_TOKEN   bearer token           (default: documented test token)
#
# Writes $DEMO_ROOT/terminal-session-id (session for the terminal shot),
# $DEMO_ROOT/keep-active-ids (sessions for keep-active.py) and
# $DEMO_ROOT/automaton-id (Automaton for the detail shot).
set -euo pipefail

: "${DEMO_ROOT:?DEMO_ROOT must be set}"
DW_URL="${DW_URL:-https://127.0.0.1:18443}"
DW_TOKEN="${DW_TOKEN:-dw-test-token-12345}"
PROJ="$DEMO_ROOT/projects/weather-api"

api() { # api METHOD PATH [JSON] — prints the body; fails (with the body on stderr) on HTTP >= 400
  local method=$1 path=$2 body=${3:-} out code
  out=$(mktemp)
  if [ -n "$body" ]; then
    code=$(curl -sSk -o "$out" -w '%{http_code}' -X "$method" -H "Authorization: Bearer $DW_TOKEN" \
      -H 'Content-Type: application/json' --data "$body" "$DW_URL$path")
  else
    code=$(curl -sSk -o "$out" -w '%{http_code}' -X "$method" -H "Authorization: Bearer $DW_TOKEN" "$DW_URL$path")
  fi
  if [ "$code" -ge 400 ]; then
    echo "$method $path -> HTTP $code: $(head -c 500 "$out")" >&2
    rm -f "$out"; return 1
  fi
  cat "$out"; rm -f "$out"
}
jget() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1], {}, {"d": d}))' "$1"; }

# ── 1. Demo project: a tiny Python package with tests ────────────────────
mkdir -p "$PROJ/weather_api" "$PROJ/tests" "$PROJ/docs"
cat > "$PROJ/README.md" <<'EOF'
# weather-api

Small forecast service used for the datawatch demo.
EOF
cat > "$PROJ/weather_api/__init__.py" <<'EOF'
"""Tiny forecast service."""
from .forecast import get_forecast, normalize_city

__all__ = ["get_forecast", "normalize_city"]
EOF
cat > "$PROJ/weather_api/forecast.py" <<'EOF'
FORECASTS = {
    "lisbon": {"high": 24, "low": 16, "sky": "sunny"},
    "oslo": {"high": 9, "low": 2, "sky": "rain"},
    "tokyo": {"high": 19, "low": 12, "sky": "cloudy"},
}


def normalize_city(name: str) -> str:
    return " ".join(name.split()).lower()


def get_forecast(city: str) -> dict:
    key = normalize_city(city)
    if key not in FORECASTS:
        raise KeyError(f"unknown city: {city}")
    return {"city": key, **FORECASTS[key]}
EOF
cat > "$PROJ/tests/test_forecast.py" <<'EOF'
import unittest

from weather_api import get_forecast, normalize_city


class ForecastTests(unittest.TestCase):
    def test_normalize_trims_and_lowercases(self):
        self.assertEqual(normalize_city("  Lisbon "), "lisbon")

    def test_normalize_collapses_spaces(self):
        self.assertEqual(normalize_city("New   York"), "new york")

    def test_known_city(self):
        self.assertEqual(get_forecast("Oslo")["sky"], "rain")

    def test_forecast_has_range(self):
        f = get_forecast("tokyo")
        self.assertLess(f["low"], f["high"])

    def test_unknown_city_raises(self):
        with self.assertRaises(KeyError):
            get_forecast("Atlantis")


if __name__ == "__main__":
    unittest.main()
EOF
cat > "$PROJ/run_tests.py" <<'EOF'
"""Compact test runner: one short line per test (fits a phone terminal)."""
import sys
import time
import unittest


class Result(unittest.TextTestResult):
    def _line(self, tag, test):
        self.stream.writeln(f"  {tag}  {test._testMethodName}")

    def addSuccess(self, test):
        unittest.TestResult.addSuccess(self, test)
        self._line("PASS", test)

    def addFailure(self, test, err):
        unittest.TestResult.addFailure(self, test, err)
        self._line("FAIL", test)


start = time.time()
suite = unittest.defaultTestLoader.discover("tests")
print(f"weather-api: running {suite.countTestCases()} tests\n")
res = Result(unittest.runner._WritelnDecorator(sys.stdout), False, 0)
suite.run(res)
ok = len(res.failures) + len(res.errors) == 0
print(f"\n{res.testsRun - len(res.failures) - len(res.errors)} passed in {time.time() - start:.2f}s")
if "--watch" in sys.argv:
    print()
    while True:
        for c in "|/-\\":
            print(f"\rwatching weather_api/ for changes {c}", end="", flush=True)
            time.sleep(1)
sys.exit(0 if ok else 1)
EOF
cat > "$PROJ/serve_docs.py" <<'EOF'
"""Serve docs/ locally with a small status line."""
import functools
import http.server
import threading
import time

handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory="docs")
handler.log_message = lambda *a: None
srv = http.server.ThreadingHTTPServer(("127.0.0.1", 8000), handler)
threading.Thread(target=srv.serve_forever, daemon=True).start()
print("docs: 2 pages up to date\n")
while True:
    for c in "|/-\\":
        print(f"\rserving docs/ on http://127.0.0.1:8000 {c}", end="", flush=True)
        time.sleep(1)
EOF
cat > "$PROJ/docs/api.md" <<'EOF'
# API

GET /forecast?city=<name>  ->  {"city", "high", "low", "sky"}
GET /health                ->  {"status": "ok"}
EOF
cat > "$PROJ/deploy_check.py" <<'EOF'
"""Pre-deploy checklist, then ask before promoting."""
print("Deploy check: weather-api -> staging\n")
print("  [ok] unit tests      5 passed")
print("  [ok] lint            0 issues")
print("  [ok] image build     weather-api:1.4.2")
print("  [ok] health probe    200 OK (42 ms)\n")
answer = input("Promote weather-api:1.4.2 to staging? [y/N] ")
print("promoted" if answer.lower().startswith("y") else "skipped")
EOF

# ── 2. Shell sessions ─────────────────────────────────────────────────────
# demo-config.yaml points shell_backend.script_path at $DEMO_ROOT/bin/run-task,
# so the daemon runs `run-task '<task>' '<project dir>'` in each session's tmux
# pane instead of an interactive shell: no shell prompt / host / path noise,
# and no bare prompt for the idle detector to mistake for "waiting for input".
# Each task execs a Python program: the daemon reports a pane whose foreground
# process is a shell as a dead agent ("zombie"). The two long-running ones
# animate a spinner, which keep-active.py turns into "running" activity.
mkdir -p "$DEMO_ROOT/bin"
cat > "$DEMO_ROOT/bin/run-task" <<'EOF'
#!/usr/bin/env bash
# run-task <task> <project dir> — demo session commands, keyed by task text.
cd "$2" || exit 1
clear
case "$1" in
  "Run the weather-api unit tests") exec python3 run_tests.py --watch ;;
  "Pre-deploy checks for staging")  exec python3 deploy_check.py ;;
  "Refresh API docs")
    wc -l README.md docs/api.md weather_api/*.py
    echo
    exec python3 serve_docs.py ;;
esac
EOF
chmod +x "$DEMO_ROOT/bin/run-task"

start_session() { # name task -> prints session id
  api POST /api/sessions/start "$(python3 -c 'import json,sys; print(json.dumps({"name": sys.argv[1], "task": sys.argv[2], "project_dir": sys.argv[3], "backend": "shell"}))' "$1" "$2" "$PROJ")" \
    | jget 'd.get("full_id") or d.get("id") or d["session"]["full_id"]'
}

docs_id=$(start_session "docs refresh" "Refresh API docs")
tests_id=$(start_session "api tests" "Run the weather-api unit tests")
deploy_id=$(start_session "deploy check" "Pre-deploy checks for staging")

echo "$tests_id" > "$DEMO_ROOT/terminal-session-id"
echo "$docs_id $tests_id" > "$DEMO_ROOT/keep-active-ids"
echo "sessions: docs=$docs_id tests=$tests_id deploy=$deploy_id"

# ── 3. Automaton: create, plan (stub LLM), then add a story + task ────────
# Planner LLM on a compute node backed by ollama-stub.py (canned plan).
STUB_URL="${OLLAMA_STUB_URL:-http://127.0.0.1:11434}"
for _ in $(seq 1 20); do curl -sf "$STUB_URL/api/tags" >/dev/null && break; sleep 1; done
curl -sf "$STUB_URL/api/tags" >/dev/null || { echo "::error::ollama stub not reachable at $STUB_URL" >&2; exit 1; }
api POST /api/compute/nodes "{\"name\":\"demo-gpu\",\"kind\":\"ollama\",\"address\":\"$STUB_URL\"}" >/dev/null
api POST /api/llms '{"name":"demo-planner","kind":"ollama","model":"demo-planner","compute_nodes":["demo-gpu"],"output_mode":"chat","input_mode":"tmux"}' >/dev/null
prd_id=$(api POST /api/autonomous/prds "$(python3 -c 'import json,sys; print(json.dumps({"spec": "Add response caching to the weather API so repeated forecast lookups for the same city are served from memory, and report cache hit/miss counts on /health.", "project_dir": sys.argv[1], "backend": "shell"}))' "$PROJ")" \
  | jget 'd.get("id") or d["prd"]["id"]')
api POST "/api/autonomous/prds/$prd_id/decompose" '{}' >/dev/null
st=""
for _ in $(seq 1 60); do
  st=$(api GET "/api/autonomous/prds/$prd_id" | jget 'd.get("status") or d["prd"]["status"]')
  [ "$st" = "needs_review" ] && break
  sleep 1
done
echo "automaton $prd_id status=$st"
if [ "$st" != "needs_review" ]; then
  api GET "/api/autonomous/prds/$prd_id/decompose/status" || true
  echo "::error::Automaton planning did not reach needs_review" >&2
  exit 1
fi
story_id=$(api POST "/api/autonomous/prds/$prd_id/add_story" \
  '{"title":"Document the cache","description":"Describe cache behaviour and TTL settings in docs/api.md."}' \
  | jget '[s for s in (d.get("stories") or d["prd"]["stories"]) if s["title"]=="Document the cache"][0]["id"]')
api POST "/api/autonomous/prds/$prd_id/add_task" \
  "{\"story_id\":\"$story_id\",\"title\":\"Add caching section to docs/api.md\",\"spec\":\"Explain TTL, cache keys and the /health counters.\"}" >/dev/null
echo "$prd_id" > "$DEMO_ROOT/automaton-id"

# A second Automaton (planned by the stub's 7-day plan) so the list has two.
prd2_id=$(api POST /api/autonomous/prds "$(python3 -c 'import json,sys; print(json.dumps({"spec": "Add a /forecast/week endpoint that returns a 7-day forecast for a city, with input validation and tests.", "project_dir": sys.argv[1], "backend": "shell"}))' "$PROJ")" \
  | jget 'd.get("id") or d["prd"]["id"]')
api POST "/api/autonomous/prds/$prd2_id/decompose" '{}' >/dev/null || true
for _ in $(seq 1 30); do
  st2=$(api GET "/api/autonomous/prds/$prd2_id" | jget 'd.get("status") or d["prd"]["status"]')
  [ "$st2" = "needs_review" ] && break
  sleep 1
done
echo "automaton $prd2_id status=$st2"

echo "seed complete"
