# End-to-end smoke tests (Maestro)

A repeatable e2e smoke layer for the Android phone app (plus optional Wear OS and
automotive launch smoke). It drives the **publicTrackDebug** build
(`com.dmzs.datawatchclient.debug`) on the `dw_test_phone` emulator against an
**isolated sandbox datawatch daemon** with real LLM nodes.

> **Local only.** These flows need KVM emulators, a local `datawatch` binary and the
> operator's LLM nodes. CI has no emulator, so CI does **not** run them — CI stays
> on `./gradlew test` (JVM/Robolectric). Run `scripts/e2e-sandbox.sh` before a release
> and paste the results table into the release notes / testing tracker.

## 1. Install Maestro (once)

Per AGENT.md › Dev Tool Install Location, Maestro lives under the workspace, not `$HOME`:

```bash
mkdir -p /home/dmz/workspace/maestro && cd /home/dmz/workspace/maestro
curl -sSL -o maestro.zip https://github.com/mobile-dev-inc/maestro/releases/latest/download/maestro.zip
unzip -qo maestro.zip           # → /home/dmz/workspace/maestro/maestro/bin/maestro
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 MAESTRO_CLI_NO_ANALYTICS=1 \
  /home/dmz/workspace/maestro/maestro/bin/maestro --version     # 2.11.0 at time of writing
```

The runner puts `maestro/bin` on `PATH` itself (override with `MAESTRO_HOME`) and sets
`MAESTRO_CLI_NO_ANALYTICS=1`. Maestro still writes its own logs under `~/.maestro/`.
To upgrade, re-run the download over the same directory.

Other prerequisites: Android SDK at `/home/dmz/workspace/Android/Sdk` with the
`dw_test_phone` (Pixel 6, API 35), `dw_test_watch` (Wear OS, API 33) and `dw_test_auto`
(automotive, API 33) AVDs; `tmux`; `python3` with Pillow; a datawatch binary
(default `/home/dmz/.local/bin/datawatch`, override `DATAWATCH_BIN`).

## 2. LLM nodes (local file, never committed)

The council flow needs real LLMs. Node names/URLs stay in a local env file outside
every repo (see test-isolation-guide.md › Real LLMs in the Sandbox):

```
# /home/dmz/workspace/.datawatch-test-llm-nodes.env
DW_TEST_LLM_NODES="nodeA=http://<host-a>:11434,nodeB=http://<host-b>:11434"
DW_TEST_LLM_MODEL="qwen3:1.7b"
```

Override the path with `LLM_ENV_FILE`. Without it the council flow fails (no backend).

## 3. Run

```bash
scripts/e2e-sandbox.sh                 # phone flows
scripts/e2e-sandbox.sh --wear --auto   # + Wear OS and automotive launch smoke
scripts/e2e-sandbox.sh --flows "01_add_server 02_sessions_list"   # subset
scripts/e2e-sandbox.sh --skip-build    # reuse built APKs
scripts/e2e-sandbox.sh --no-phone --wear                          # watch only
```

What it does, in order:

1. Picks free ports (18180/18543 first, never 8080/8443) and writes a fresh config
   into `/home/dmz/workspace/.datawatch-test-e2e-<runid>/` — hostname
   `dw-app-e2e-<runid>` (so its tmux sessions `cs-dw-app-e2e-<runid>-<id>` can never
   collide with production or another sandbox), shell backend, `autonomous.enabled:
   true`, and whisper `backend: openai_compat`, `endpoint: http://127.0.0.1:9/v1`,
   `api_key: dummy` (a dead port: the voice dialog can open, nothing is transcribed).
   Because whisper is in the config before first start, no daemon restart is needed.
2. Starts the daemon in the foreground from the test dir, records its PID, waits for
   `/api/health` (`hostname` must match).
3. Seeds LLMs with `scripts/sandbox-seed-llms.sh`, starts the `e2e-shell` shell
   session and prints 200 lines into it.
4. Builds `:composeApp:assemblePublicTrackDebug` (+ `:wear:assembleDebug` with `--wear`).
5. Boots `dw_test_phone` headless, `-read-only` on a dedicated console port
   (5580+), disables animations and heads-up notifications, `adb reverse`s the
   sandbox TLS port, installs the debug APK with runtime permissions granted.
6. Runs the flows below, with sandbox URL/token/session ids passed as Maestro
   `--env` values, plus runner-side assertions (tmux, REST, logcat).
7. Optional `--wear` / `--auto` stages.
8. **Always** cleans up on exit (trap): DND off, kills only the sandbox's tmux
   sessions (whose start dir is inside the test dir), stops the daemon **only** if its
   PID is the one listening on the sandbox HTTP port, `adb -s <serial> emu kill` for
   the emulators it booted, removes the test dir. `--keep` skips cleanup for debugging.

Artifacts go to `/home/dmz/workspace/tmp/datawatch-app/run/e2e-<runid>/`:
`run.log`, `results.tsv`, `daemon.log`, `logcat-*.txt`, `flows/<flow>/` (Maestro log,
per-step screenshots + hierarchy on failure, `takeScreenshot` images, `final.png`),
`wear_smoke.png`, `auto_smoke.png`. They can contain LLM node names in persona
replies/logs — scrub before copying anything into docs.

## 4. Flows (`e2e/maestro/`)

| Flow | Covers | Extra runner assertion |
|---|---|---|
| `01_add_server` | clear state → onboarding → Add server (URL/token from env, **Trust all certificates** switch on) → shell shows the server | — |
| `02_sessions_list` | Sessions tab lists `e2e-shell` + its id | — |
| `03_open_session` | open session: header, terminal toolbar, composer; "Connecting to session…" clears | `03_terminal_rendered`: terminal area of the screenshot has text pixels (xterm draws on a canvas Maestro can't read) |
| `04_scroll_mode_enter` | ⤒ → Page Up/Down strip | `tmux_pane_in_mode`: `tmux display -p -t <tmux> '#{pane_in_mode}'` is 0 before, 1 after enter… |
| `05_scroll_mode_exit` | ⏹ → strip gone, composer back | …and 0 after exit |
| `06_voice_dnd` | runner sets `adb shell cmd notification set_dnd on`; mic → "Recording…" dialog → Cancel | `06_voice_dnd_logcat`: no "Not allowed to change Do Not Disturb" in logcat |
| `07_settings_general` | Settings › General has **Raw config** (editor opens with a JSON field); no "Config Viewer" anywhere while scrolling to the end | — |
| `08_council_quick_run` | Settings › Automata › Council: proposal → Quick (1 round) → Run → `completed` + Consensus + persona ✓ | `08_council_rest`: latest `/api/council/runs` entry has a non-empty, non-error reply per persona and a consensus |
| `09_project_profile` | Project Profiles › + Add → fill form → YAML view (name in YAML) → Form view (values kept) → Save → row in list | `09_project_profile_rest`: `GET /api/profiles/projects/<name>` → 200 |
| `10_automata_tab` | bottom-nav Automata tab present and opens the list (Templates, ⚡) | `10_autonomous_enabled`: sandbox `/api/config` has `autonomous.enabled=true` |
| `11_deep_link` | `openLink datawatch://session/<full id>` → session opens and connects; types `echo <marker>` and sends | `11_deep_link_live`: the marker line appears in the tmux pane |
| (all) | — | `phone_no_fatal`: no `FATAL EXCEPTION` for the app in logcat |

Shared subflows in `e2e/maestro/common/`: `home.yaml` (foreground app, leave session
detail via system back, wait for the bottom nav), `open_session.yaml`,
`settings_automata.yaml` (Settings › Automata scrolled to `${TARGET}`).

Flows 02–11 assume 01 ran first (they keep app state). Each one navigates from
`common/home.yaml`, so any of them can be re-run on its own once a server exists.

### Wear / Auto smoke

Both stages **uninstall first**: the shared AVDs can carry an older debug install whose
saved server profile points at a real daemon, and `install -r` would keep it (an early
run of this script launched the automotive app with such state). A clean install
lands on the "No server" screen and talks to nothing.

- `--wear`: boots `dw_test_watch`, installs `wear/build/outputs/apk/debug/*.apk`
  (same `.debug` application id), launches it via the launcher intent, screenshots,
  and passes if the process is alive and logcat has no `FATAL EXCEPTION` for it.
  (The screenshot is often still the watch app's "Starting…" splash — no phone is
  paired, so this is a launch/no-crash check only.)
- `--auto`: there is **no separate automotive build** — `:auto` is a library inside
  the phone APK (Android Auto *projection*). The stage boots `dw_test_auto`
  (Android Automotive OS), installs the phone debug APK and does the same launch +
  no-crash check. It does not exercise the car-app (projected) UI — that still needs
  the DHU (see AGENT.md › Testing Requirements).

## 5. Writing / debugging flows

- Keep a sandbox + emulator up: `scripts/e2e-sandbox.sh --keep --flows none`, then
  `maestro --device emulator-5580 test e2e/maestro/<flow>.yaml --env ...` and
  `maestro --device emulator-5580 hierarchy --compact` to see selectors. Clean up as
  the `--keep` log line says.
- Maestro text matching is a **case-insensitive full-match regex**: "Raw config"
  also matches the card title "RAW CONFIG", "Do Not Disturb" matches the status-bar
  DND icon. Use `.*` / unique neighbours (`rightOf`, `below`, `leftOf`).
- Compose `Switch`es have no text; select them relative to their label
  (`leftOf: ".*Trust all certificates.*"`).
- Prefer `extendedWaitUntil` / `scrollUntilVisible` over sleeps. The Settings pager
  keeps its scroll offset across sub-tabs, so scroll **up** (optional) before down.
- No app test tags were added; every selector uses visible text or content
  descriptions.

## 6. Known issues found by these flows

- ~~**Profile editor Save is outside the accessibility tree (Form view).**~~ Fixed in
  v1.28.1 (the dialog pads for the system bars and keyboard); `09` taps Save by name.
- **Profile list refresh lags the save**: the new row can appear a moment after the
  dialog closes (flow scrolls/waits for it).
- ~~Council persona replies show a literal `_(via: <node>)_`~~ — fixed in v1.28.1
  (`_italic_` markdown).
