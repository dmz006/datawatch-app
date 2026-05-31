# datawatch-app

[![CI](https://github.com/dmz006/datawatch-app/actions/workflows/ci.yml/badge.svg)](https://github.com/dmz006/datawatch-app/actions/workflows/ci.yml)
[![iOS Build](https://github.com/dmz006/datawatch-app/actions/workflows/ios-build.yml/badge.svg)](https://github.com/dmz006/datawatch-app/actions/workflows/ios-build.yml)

**datawatch** — the Android / Wear OS / Android Auto / iOS companion for
[dmz006/datawatch](https://github.com/dmz006/datawatch), the daemon that bridges
AI coding sessions (Claude Code, Aider, etc.) to messaging platforms.

**Current release: v1.0.34 (2026-05-31).** **Status:** [General Availability](https://github.com/dmz006/datawatch-app/releases/latest). Pairs with `datawatch v8.9.5+` (`v8.8.3+` for Chrome integration). **Production-ready** — full platform parity across Android phone, Wear OS, Android Automotive OS, and iOS with comprehensive testing and Play Store integration.

---

## 🤖 For Developers & AI Sessions

**Starting a new coding session on this codebase?** Before you begin:

1. **Read [`AGENT.md`](AGENT.md)** — Canonical rules and guardrails for all work on this codebase
   - Non-negotiable project invariants
   - Pre-execution checklist (load DATAWATCH-APP-CONTEXT.md + query memory)
   - Code quality, testing, versioning, and release discipline
   - Security, documentation, and dependency rules

2. **Read [`DATAWATCH-APP-CONTEXT.md`](DATAWATCH-APP-CONTEXT.md)** — Comprehensive context loader covering:
   - Project identity, platforms, and architecture
   - Module structure and build system with RTK token optimization
   - Testing strategy (JVM unit tests + live device validation)
   - Datawatch MCP tooling and memory system reference
   - Common task patterns and known issues with workarounds

3. **Query project memory** for recent changes and learnings:
   ```bash
   datawatch memory_recall "terminal scrolling" --top 5
   datawatch memory_recall "v1.0.0 changes" --top 3
   ```

---

## 🧪 Alpha Testing Program

**We're seeking alpha testers for Wear OS and Android apps!**

v1.0.0 is production-ready with full feature parity and comprehensive testing. If you're interested in testing the Wear OS companion or Android Auto (AAOS) functionality before the general release, we'd love your feedback.

**Interested?** Contact **[@dmz006](https://github.com/dmz006)** via:
- GitHub Issues: [datawatch-app/issues](https://github.com/dmz006/datawatch-app/issues)
- Direct message on GitHub

What we're testing:
- ✅ Wear OS session monitoring and voice reply
- ✅ Android Auto (AAOS) integration for vehicle displays
- ✅ Cross-platform data synchronization
- ✅ Real-world daemon connectivity (Tailscale, LAN, remote)
- ✅ Performance on various devices

---

## At a glance

| Phone | Watch | Auto (AAOS) | PWA reference |
|:---:|:---:|:---:|:---:|
| ![phone slideshow v1.0.0](docs/media/phone-slideshow-v1.0.0.gif) | ![watch slideshow v1.0.0](docs/media/watch-slideshow-v1.0.0.gif) | ![auto slideshow v1.0.0](docs/media/auto-slideshow-v1.0.0.gif) | ![pwa slideshow v1.0.0](docs/media/pwa-slideshow-v1.0.0.gif) |

*Slideshows loop at ~2.5 s per frame showing all 6 core pages: Sessions, Automata, Alerts, Observer, Dashboard, Settings. Watch cards optimized for 1.4" round display. Android Auto full-width automotive layout. All tested and verified for v1.0.0 GA release.*

## What it does

Watch every AI coding session running on your datawatch daemon(s) from your
phone, watch, or car display:

- **Live session view** — WebSocket-streamed chat + terminal + state events,
  with reply / kill / state-override actions.
- **Per-session process stats** — CPU ring, RSS, net Rx/Tx, and GPU usage pulled
  from the observer's eBPF envelope; shown in the "stats" tab of session detail.
- **Push when attention is needed** — ntfy + Wear OS alert notification when a
  session enters waiting-input state; inline RemoteInput reply from the shade.
- **Voice reply** — tap, speak, confirm — no typing on a two-inch keyboard.
- **Multi-server** — Tailscale, LAN, and public hosts side-by-side; 3-finger
  swipe to switch; "All servers" fan-out via `/api/federation/sessions`.
- **Glance surfaces** — home-screen widget, Wear Tile, Wear complications (CPU /
  mem / session counts / server switch), and Android Auto list screen.
- **Foldable + tablet two-pane** — sessions list and session detail render
  side-by-side on screens ≥ 600 dp (Pixel Fold, Galaxy Z Fold, tablets).
- **Secure at rest** — SQLCipher-backed storage + Android Keystore for bearer
  tokens + optional biometric unlock.

Full feature matrix: [docs/parity-status.md](docs/parity-status.md).

## Android

<table>
<tr>
<td align="center"><img src="docs/media/phone/01-splash.png" width="180"/><br/><sub>Splash</sub></td>
<td align="center"><img src="docs/media/phone/02-sessions.png" width="180"/><br/><sub>Sessions list</sub></td>
<td align="center"><img src="docs/media/phone/11-session-running.png" width="180"/><br/><sub>Live session</sub></td>
<td align="center"><img src="docs/media/phone/04-alerts.png" width="180"/><br/><sub>Alerts</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/media/phone/03-prds.png" width="180"/><br/><sub>Automata</sub></td>
<td align="center"><img src="docs/media/phone/10-new-session.png" width="180"/><br/><sub>New session</sub></td>
<td align="center"><img src="docs/media/phone/05-settings-monitor.png" width="180"/><br/><sub>Settings — Monitor</sub></td>
<td align="center"><img src="docs/media/phone/09-settings-about.png" width="180"/><sub>About</sub></td>
</tr>
</table>

The session detail view streams chat and terminal output with a compact tab switcher
(tmux / channel / stats). The **stats tab** shows live CPU, RSS, and network throughput
from the observer's process envelope when eBPF is active. The composer row gives you
arrow keys, PgUp/PgDn, and a saved-commands picker — no need to type `\033[A` by hand.

## Wear OS

<table>
<tr>
<td align="center"><img src="docs/media/watch/00-splash.png" width="160"/><br/><sub>Splash</sub></td>
<td align="center"><img src="docs/media/watch/01-monitor.png" width="160"/><br/><sub>Monitor</sub></td>
<td align="center"><img src="docs/media/watch/02-sessions.png" width="160"/><br/><sub>Sessions</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/media/watch/03-prds.png" width="160"/><br/><sub>Automata</sub></td>
<td align="center"><img src="docs/media/watch/04-servers.png" width="160"/><br/><sub>Servers</sub></td>
<td align="center"><img src="docs/media/watch/05-about.png" width="160"/><br/><sub>About</sub></td>
</tr>
</table>

Tap a session to see its live status and send a voice reply — the watch
transcribes on-device and shows "Processing…" while the server handles it.
Haptic confirmation on send.

## Android Auto / AAOS

The app runs natively on **Android Automotive OS** (AAOS) — no phone required.
Install the APK directly on any AAOS head unit and connect to your datawatch
daemon over Tailscale or local Wi-Fi.

| Night mode (official release) | Day mode (debug build) |
|:---:|:---:|
| ![auto dark](docs/media/auto-slideshow.gif) | ![auto debug](docs/media/auto-slideshow-debug.gif) |

*Dark mode activates automatically when the vehicle sets night mode (ambient
light sensor or time-of-day). Day/night is AAOS-controlled, not app-controlled.*

<table>
<tr>
<td align="center"><img src="docs/media/auto/01-splash.png" width="270"/><br/><sub>Splash</sub></td>
<td align="center"><img src="docs/media/auto/02-sessions.png" width="270"/><br/><sub>Sessions</sub></td>
<td align="center"><img src="docs/media/auto/03-alerts.png" width="270"/><br/><sub>Alerts</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/media/auto/04-settings-monitor.png" width="270"/><br/><sub>Monitor stats</sub></td>
<td align="center"><img src="docs/media/auto/05-settings-about.png" width="270"/><br/><sub>About</sub></td>
<td></td>
</tr>
</table>

Surfaces available on AAOS: **Sessions**, **Alerts** (grouped by session, inline reply/schedule/open), and **Settings** (Monitor · General · Comms · LLM · About). The eye watermark and server-selector dropdown carry over from the phone layout.

## Platforms

- Android phone / tablet / foldable (minSdk 29 — Android 10 — target 35; two-pane layout on ≥ 600 dp)
- Wear OS 3+ (minSdk 30)
- Android Auto (Messaging category — runs on any Auto-enabled head unit)
- iOS 16.0+ (iPhone and iPad; SwiftUI native; Keychain + Secure Enclave; Face ID / Touch ID)

## Install

See [docs/installation.md](docs/installation.md) for the full walkthrough.
Quick version (fetch the APKs from the [latest release](https://github.com/dmz006/datawatch-app/releases/latest)):

```bash
# Phone — always use `install -r`. NEVER `adb uninstall` to upgrade:
# it wipes the SQLCipher DB + Android Keystore key for this app and
# your server profiles + bearer tokens are unrecoverable.
adb install -r composeApp-publicTrack-release.apk

# Wear OS — pair via companion app or enable Wi-Fi debug bridge.
adb -s <watch-serial> install -r wear-release.apk
```

First launch:
1. Onboarding → Add server.
2. Enter your datawatch server URL (e.g. `https://host.taila1234.ts.net:8080`),
   bearer token, and the self-signed-TLS toggle if applicable.
3. Sessions tab shows a live view of every running session on that server.

## Documentation

- 📖 [Installation guide](docs/installation.md) — detailed walkthrough
  (phone, Wear, Auto, troubleshooting)
- 🧭 [Architecture](docs/architecture.md) — module layout + dependency graph
- 🔌 [Data flow](docs/data-flow.md) — REST + WebSocket + FCM/ntfy pipes
- 🎬 [Usage guide](docs/usage.md) — how every screen behaves
- 🛡 [Security model](docs/security-model.md) · [Threat model](docs/threat-model.md)
- 🧩 [Architecture decisions (ADRs)](docs/decisions/README.md)
- 🔄 [Parity status vs. the PWA](docs/parity-status.md)
- 🗺 [Sprint plan](docs/sprint-plan.md)
- 🤝 [AGENT.md](AGENT.md) — operating rules for contributors (human + AI)
- 🔐 [SECURITY.md](SECURITY.md)

## Build

Requires **JDK 21** (AGP 8.5.2's bundled Kotlin compiler rejects JDK 25+)
and the Android SDK.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :composeApp:assemblePublicTrackDebug    # phone debug
./gradlew :composeApp:assemblePublicTrackRelease  # phone release (needs keystore)
./gradlew :wear:assembleDebug                     # Wear
./gradlew :auto:assemblePublicMessagingDebug      # Auto Messaging
./gradlew :shared:testDebugUnitTest               # shared unit tests (33)
./gradlew detekt ktlintCheck lintDebug            # linters
```

Gradle wrapper is committed — no bootstrap step on clone.

## Project layout

```
composeApp/   phone app — Compose UI, WebView terminal, push, gestures
wear/         Wear OS app + Tile + complications
auto/         Android Auto (publicMessaging + devPassenger flavors)
shared/       KMP: transport (REST + WS + MCP-SSE), DTOs, storage, domain
iosApp/       iOS skeleton
docs/         design package + ADRs + runbooks
gradle/       Gradle wrapper + version catalog
```

## Server requirements

- **datawatch** daemon >= v3.0.0 (for `/api/devices/register`,
  `/api/voice/transcribe`, `/api/federation/sessions`). Earlier versions
  still work for basic REST + WebSocket flows; voice and FCM wake degrade
  to the server's ntfy fallback.
- Reachable over one of: Tailscale, LAN, public DNS + TLS, or the
  datawatch channel relay.

## License

[Polyform Noncommercial 1.0.0](LICENSE). Free for personal, educational,
open-source, and non-commercial use. Matches the parent project.

## Contact

- Issues: https://github.com/dmz006/datawatch-app/issues
- Security: see [SECURITY.md](SECURITY.md)
- Brand: https://dmzs.com
