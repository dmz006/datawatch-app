# Demo server (app review): rebuild runbook

The demo server is a small public datawatch instance with harmless seed content. App reviewers sign in to it: TestFlight Beta App Review, App Store review and, if needed, Play "App access".

It ran from 2026-10-06 to 2026-10-09 for the first TestFlight review. It was then **shut down to save cost**: Apple approved build 673 without ever signing in to it (per the access log). Rebuild it before the next review that needs a working sign-in, which will at least be the App Store submission.

The host name, IP address and token are never committed. The operator keeps them, plus a full file snapshot of the old VM (config, seed state, monitor scripts; binaries excluded), in a private backup outside the repo.

## What it was

| Piece | Setting |
|---|---|
| VPS | Ubuntu 26.04 LTS, 2 vCPU, 4 GB RAM, 80 GB disk |
| DNS | A record for the demo host name, which ACME needs |
| Firewall (ufw) | allow 22, 80 (ACME HTTP-01), and 8443 with `log` (the visitor monitor uses it) |
| datawatch | Latest release, built from source with `CGO_ENABLED=0` (run `make sync-docs docs-index` first). Runs as the unprivileged user `demo`, binary at `~demo/.local/bin/datawatch` |
| Service | systemd **user** unit `datawatch.service` with linger on. The drop-in `datawatch.service.d/foreground.conf` sets `ExecStart=… start --foreground` and `WorkingDirectory=/home/demo/projects` |
| TLS | datawatch's built-in ACME (Let's Encrypt) on port 8443. Port 80 must be bindable: **`setcap cap_net_bind_service=+ep` on the binary after every swap** |
| Token | Bearer token in `~demo/.datawatch/demo-token` (mode 600). Never print it; pipe it over SSH |
| LLM | Ollama (CPU) with `qwen2.5:1.5b` as the Automata planning backend. It sometimes returns bad JSON, so retry a decompose |
| Push | APNs key in `~demo/.datawatch/apns/AuthKey.p8` (600, owned by demo). `push.apns` is set through `PUT /api/config` |
| Logging | datawatch ≥ v8.73.41 access log (`<data_dir>/access.log`, `audit.access_log_enabled`) |

## Rebuild steps

1. **Create the VPS and point DNS at it.** Turn on ufw with the rules above.
2. **Set up the `demo` user:**
   - `useradd -m demo`;
   - `loginctl enable-linger demo`;
   - install the binary to `~demo/.local/bin/datawatch`;
   - run `setcap cap_net_bind_service=+ep` on it;
   - create the user unit and the `foreground.conf` drop-in.
3. **Configure datawatch.** Either:
   - restore `~demo/.datawatch/` from the private snapshot; this keeps the same token, so the review secrets stay valid; or
   - start fresh: initialise, enable ACME for the host name on 8443, generate a token, and set the APNs config.
4. **Start and check:**
   - `sudo -u demo XDG_RUNTIME_DIR=/run/user/$(id -u demo) systemctl --user restart datawatch`;
   - then `curl https://<host>:8443/api/health`.
5. **Install Ollama** and pull `qwen2.5:1.5b`. Register it as the planning backend.
6. **Seed the demo content.** Run `scripts/screenshots/seed-demo.sh` with `DEMO_ROOT=/home/demo` and `DW_URL=https://<host>:8443`. It creates:
   - the `weather-api` project;
   - 3 shell sessions in `waiting_input` ("docs refresh", "api tests", "deploy check");
   - 2 Automata in `needs_review`.

   A daemon restart marks the sessions failed. To re-seed:
   - delete them with `POST /api/sessions/delete {id}`;
   - start them again with `POST /api/sessions/start`, using the `shell` backend and project `/home/demo/projects/weather-api`.
7. **Point the review secrets at it** (repo secrets; never commit the values):
   - `BETA_REVIEW_DEMO_URL` and `BETA_REVIEW_DEMO_TOKEN`;
   - then run `ios-testflight-setup.yml` to push the review info to App Store Connect.

   If you restored the old token and kept the same host name, nothing changes here.
8. **Optional: visitor monitor** (BL417). It alerts the operator by email when someone outside our own machines signs in. It has three parts:
   - the ufw `log` rule;
   - the `dw-visitors-sample` timer and `dw-visitors-report` script on the VM;
   - local timers on the operator machine.

   All of it is in the private snapshot.

## Upkeep rules

- **Upgrades:** never upgrade while a build is in Beta App Review or App Review. Otherwise upgrade freely, and keep the previous binary as `datawatch.v<version>.bak`.
- **Before a review:** re-seed so that the sessions show `waiting_input`.
- **Screenshots:** take them only from CI demo data (`android-screenshots.yml` / `ios-screenshots.yml`), not from this server.
