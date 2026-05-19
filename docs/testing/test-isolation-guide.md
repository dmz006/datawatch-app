# Mobile App Test Isolation Guide

Adapted from datawatch v7.0.0 `docs/testing/v7.0.0/test-isolation-guide.md`.
**The #1 rule: tests must never touch the production datawatch daemon (port 8443).**

---

## Architecture

```
Production Daemon (8443)
├─ Production data at ~/.datawatch/
├─ Production session johnnyjohnny-604c
└─ NEVER touched by app tests (except hooks + comms)

Test Daemon (18443, or OS-assigned free port)
├─ Isolated data at ../datawatch-soak-<run_id>/.datawatch-test-<pid>/
├─ Working dir is OUTSIDE the repo at $REPO_PARENT/datawatch-soak-<run_id>/
├─ Foreground mode (start --foreground --config)
├─ hostname: johnnyjohnny-test
└─ root_path: /home/dmz/workspace (NEVER /home/dmz)

Emulator (emulator-5554)
├─ ADB reverse: tcp:18443 → localhost:18443
└─ Mobile app points to https://10.0.2.2:18443
```

---

## PID Validation (Critical — Learned the Hard Way)

**Problem**: Both production and test daemons appear as `datawatch start --foreground`
in `ps` output. A grep-based kill will kill production if the test daemon has already exited.

### WRONG ❌ (never do this)
```bash
pkill -f "datawatch"
kill $(ps aux | grep "datawatch start --foreground" | awk '{print $2}')
kill $(pgrep -f "datawatch")
```

### CORRECT ✓ (always do this)
```bash
# At startup — save PID immediately:
/home/dmz/.local/bin/datawatch start --foreground --config .datawatch-test/config.yaml \
  > /tmp/test-server.log 2>&1 &
TEST_DAEMON_PID=$!
echo "$TEST_DAEMON_PID" > /tmp/test-daemon.pid

# At cleanup — validate before killing:
TEST_DAEMON_PID=$(cat /tmp/test-daemon.pid 2>/dev/null)
if [ -n "$TEST_DAEMON_PID" ] && kill -0 "$TEST_DAEMON_PID" 2>/dev/null; then
  if ss -tlnp 2>/dev/null | grep -q "18080.*pid=$TEST_DAEMON_PID"; then
    kill "$TEST_DAEMON_PID"
    echo "Test daemon stopped."
  else
    echo "ERROR: PID $TEST_DAEMON_PID is NOT on port 18080 — refusing to kill (would be wrong process)"
    exit 1
  fi
fi
rm -f /tmp/test-daemon.pid
```

---

## CLI Isolation

Any `datawatch` command without `--config` targets the **production** daemon.

### WRONG ❌
```bash
datawatch session list              # hits production
datawatch config get                # hits production
```

### CORRECT ✓
```bash
datawatch --config .datawatch-test/config.yaml session list
datawatch --config .datawatch-test/config.yaml config get
```

---

## Root Path Constraint

All test instance sessions must be confined to `/home/dmz/workspace`. 
Config must always set:
```yaml
session:
  root_path: /home/dmz/workspace
  default_project_dir: /home/dmz/workspace/datawatch-test-workspace
```

**Never use `root_path: /home/dmz`** — that gives test sessions access to the entire home directory.

---

## What Production Is Allowed to Do During Testing

Production is touched ONLY for:
1. **Stop hook** (`~/.datawatch/hooks/datawatch_save_hook.sh`) — episodic memory saves to production memory store
2. **PreCompact hook** (`~/.datawatch/hooks/datawatch_precompact_hook.sh`) — memory saves before context compression
3. **Session send** to johnnyjohnny-604c for test status communication
4. **Dashboard smoke-run tracking** — `GET /api/smoke/progress` on production dashboard

Production is **never** used for:
- Running test sessions
- Storing test data
- Issuing test API calls
- Any test cleanup

---

## Health Check Before Testing

Always verify both daemons before starting a test run:
```bash
# Production still healthy?
curl -sk https://localhost:8443/api/health | python3 -c "import sys,json; d=json.load(sys.stdin); assert d['status']=='ok' and d['hostname']=='johnnyjohnny', 'PRODUCTION UNHEALTHY'"

# Test instance healthy?
curl -sk https://localhost:18443/api/health | python3 -c "import sys,json; d=json.load(sys.stdin); assert d['status']=='ok' and d['hostname']=='johnnyjohnny-test', 'TEST INSTANCE UNHEALTHY'"

echo "Both daemons healthy — safe to proceed"
```

---

## Post-Update Checklist

After any `datawatch update && datawatch stop && datawatch start`:
1. `curl -sk https://localhost:8443/api/health` — must return `{"status":"ok"}`
2. Check MCP tools respond (test with `memory_recall "test"`)
3. Verify hooks still work: `curl -sk -X POST https://localhost:8443/api/test/message -H "Content-Type: application/json" -d '{"text":"post-update hook test"}'`
4. If anything fails — check `tail -30 ~/.datawatch/daemon.log` and fix before continuing

---

## Environment Variables (for test scripts)

| Variable | Value | Purpose |
|---|---|---|
| `TEST_BASE` | `https://127.0.0.1:$TEST_SERVER_TLS_PORT` | Test daemon HTTPS (default 18443, OS-free fallback) |
| `TEST_HTTP` | `http://127.0.0.1:$TEST_SERVER_HTTP_PORT` | Test daemon HTTP (default 18080, OS-free fallback) |
| `TEST_TOKEN` | `dw-test-token-12345` | Bearer token |
| `TEST_DATA` | `../datawatch-soak-<id>/.datawatch-test-<pid>/` | Data directory — always OUTSIDE the repo |
| `SOAK_RUN_ID` | auto (6-char hex) | Reuse with `SOAK_RUN_ID=abc123` to resume a failed run |
| `KEEP_TEST_DIR` | unset | Set to `1` to keep working dir on success |
| `DATAWATCH_BIN` | `/home/dmz/.local/bin/datawatch` | Daemon binary |
| `EMULATOR_URL` | `https://10.0.2.2:$TEST_SERVER_TLS_PORT` | URL as seen from emulator |
