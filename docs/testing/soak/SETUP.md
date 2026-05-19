# Soak Test Setup

**Purpose**: Prepare the environment for running soak tests (SS-001–SS-020).
**Prerequisite**: v1.0.0 environment already set up. If not, complete `docs/testing/v1.0.0/SETUP.md` first.

---

## 1. Environment Prerequisites

Soak tests use the identical stack as v1.0.0. If you have already run any T14 stories, your environment is ready. If not:

1. Complete all phases in `docs/testing/v1.0.0/SETUP.md`
2. Verify the following before starting any soak story:

```bash
# Emulator running
adb devices | grep "emulator.*device"

# ADB reverse forwarding
adb reverse --list
# Must show: tcp:18443 (or dynamic port) and tcp:18080 (or dynamic port)

# Test server healthy (port is TEST_SERVER_TLS_PORT, default 18443)
curl -sk https://127.0.0.1:18443/api/health
# Expected: {"status":"ok", ...}

# App installed
adb shell pm list packages | grep datawatch
# Expected: package:com.dmzs.datawatchclient.dev.debug

# Production hook reachable (for result summaries only)
curl -sk -X POST https://localhost:8443/api/test/message \
  -H "Content-Type: application/json" \
  -d '{"text":"soak setup test"}'
# Expected: 200 OK or similar (not connection refused)
```

---

## 2. Test Server Configuration

The soak runner auto-creates an ephemeral, fully isolated working directory **outside the repo** on every run. The directory is a sibling to the repo root and is identified by a 6-character hex `SOAK_RUN_ID`:

```
../datawatch-soak-<SOAK_RUN_ID>/          ← working dir (outside repo)
    .datawatch-test-<pid>/                ← config/data dir
        config.yaml
    evidence/                             ← evidence JSON files
    soak-run-TIMESTAMP.log                ← full run log
    test-daemon.pid                       ← daemon PID file
```

To reuse a prior run directory (e.g., to resume or inspect), set `SOAK_RUN_ID` to the existing 6-char hex before running. Otherwise a new ID is generated automatically.

Config written at `../datawatch-soak-<id>/.datawatch-test-<pid>/config.yaml`:

```yaml
data_dir: /home/dmz/workspace/datawatch-soak-<id>/.datawatch-test-<pid>
server:
  port: 18080          # or dynamic fallback — set via TEST_SERVER_HTTP_PORT
  tls_port: 18443      # or dynamic fallback — set via TEST_SERVER_TLS_PORT
  token: "dw-test-token-12345"
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
```

Ports default to 18443 (TLS) and 18080 (HTTP). If either port is busy, the script auto-selects a free OS port and updates ADB reverse forwarding accordingly. Override with env vars `TEST_SERVER_TLS_PORT` and `TEST_SERVER_HTTP_PORT`.

---

## 3. Soak Pass Criteria

| Metric | Warning | Fail |
|--------|---------|------|
| Heap growth (any 2-hour window) | 20–50 MB | > 50 MB |
| Crash / ANR | — | Any occurrence |
| WS reconnect accumulation | > 5 above baseline | > 10 above baseline |
| Session list count drift | — | Any non-zero drift |
| Alert badge count drift | — | Any non-zero drift |
| Story-specific thresholds | See `plan.md` §8 | See `plan.md` §8 |

**Heap growth** is measured as: `dumpsys meminfo com.dmzs.datawatchclient.dev.debug` → TOTAL PSS at end minus at start. Values are in MB.

If heap delta is in the warning range (20–50 MB), mark the story `⚠️ Warning` in `cookbook.md` and investigate before declaring v1.0.0 soak-complete.

---

## 4. Running a Single Story

```bash
# From repo root
bash docs/testing/soak/scripts/run-soak.sh --story=SS-001
```

The script will:
1. Check for a running ADB device
2. Start the test daemon if not already running
3. Set up ADB reverse forwarding
4. Run the soak loop for the selected story
5. Write evidence to `../datawatch-soak-<ID>/evidence/run-TIMESTAMP.json`
6. Write a full log to `../datawatch-soak-<ID>/soak-run-TIMESTAMP.log`
7. POST a result summary to the production hook at `https://localhost:8443/api/test/message`

The working dir is deleted automatically on success. On failure it is preserved in place for diagnosis. Set `KEEP_TEST_DIR=1` to keep the working dir even on success.

To override the duration of a time-based story:

```bash
bash docs/testing/soak/scripts/run-soak.sh --story=SS-001 --duration=30m
```

To reuse a prior run directory or set a fixed run ID:

```bash
SOAK_RUN_ID=a1b2c3 bash docs/testing/soak/scripts/run-soak.sh --story=SS-001
```

---

## 5. Running All Stories

```bash
bash docs/testing/soak/scripts/run-soak.sh --all
```

Running all stories takes approximately 27 hours total (wall clock). Plan accordingly:

- Shorter iteration stories (SS-002–SS-004, SS-006–SS-008, SS-011–SS-015, SS-017–SS-019) can be batched
- Time-based stories (SS-001, SS-005, SS-009, SS-016, SS-020) must run serially unless multiple devices are available
- SS-010 (reconnect storm) requires exclusive access to the test daemon

Recommended run order:

1. Run short stories first (SS-002–SS-004, SS-006–SS-008) to catch crashes early
2. Run SS-010 next (server restart; needs no other test running concurrently)
3. Run overnight: SS-001, SS-005, SS-009, SS-016, SS-020

---

## 6. Production Hook Integration

Results are posted to the production johnnyjohnny instance via:

```
POST https://localhost:8443/api/test/message
Content-Type: application/json
{"text": "SOAK SS-001 PASS — heap_delta=12MB"}
```

This is the **only** interaction with production during soak tests. No sessions are created, no tests run, and no data is written to the production data directory. The hook is fire-and-forget; if it fails (e.g., production not running), the soak test continues and logs a warning.

---

## 7. Evidence Directory

Evidence files are saved to the run's working directory **outside the repo**, at `../datawatch-soak-<SOAK_RUN_ID>/evidence/`. The working dir is never inside the git tree, so no `.gitignore` entry is needed.

```bash
# List evidence from a run (substitute the actual SOAK_RUN_ID)
ls -lh ../datawatch-soak-<SOAK_RUN_ID>/evidence/

# View a run result
cat ../datawatch-soak-<SOAK_RUN_ID>/evidence/run-20260516-120000.json | jq .

# List all soak run dirs (if you have multiple)
ls -d ../datawatch-soak-*/
```

On failure the working dir is kept automatically — no manual copy is needed. The full log and evidence are already at `../datawatch-soak-<SOAK_RUN_ID>/`. If you want to archive a failed run elsewhere:

```bash
cp -r ../datawatch-soak-<SOAK_RUN_ID>/ ~/soak-fail-SS-NNN/
```

---

## 8. After Each Story

1. Update `cookbook.md` — fill in Status, Heap Delta, Notes for the story
2. If FAIL: preserve evidence files, file a bug, mark story `❌ Fail`
3. If WARNING: note heap delta in cookbook, monitor next run
4. Commit cookbook after each completed story batch:
   ```bash
   git add docs/testing/soak/cookbook.md
   git commit -m "test(soak): SS-NNN pass — heap_delta=XMB"
   ```

---

## 9. Stopping a Running Soak Test

```bash
# Ctrl-C in terminal — the EXIT trap stops the daemon and cleans up automatically

# Or kill the script by PID
kill $(pgrep -f "run-soak.sh")

# Force-stop the test daemon using the PID file (if the script trap did not run)
kill $(cat ../datawatch-soak-*/test-daemon.pid)

# Remove all soak working dirs manually (script auto-cleans on success;
# only needed if you want to discard leftover failure dirs)
rm -rf ../datawatch-soak-*/
```

---

**Setup guide created**: 2026-05-16
**References**: `docs/testing/v1.0.0/SETUP.md`, `docs/testing/soak/plan.md`
