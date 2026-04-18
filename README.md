# datawatch-app

**Display name:** `datawatch` — the mobile companion for
[datawatch](https://github.com/dmz006/datawatch), the daemon that bridges AI coding
sessions to messaging platforms.

> The source repo is `dmz006/datawatch-app`; the Play Store listing, launcher label,
> Wear watch face, and Android Auto surfaces all read **datawatch** (lowercase) per
> ADR-0041, matching the parent project's brand.

**Status:** Pre-MVP scaffold (v0.1.0-pre). Design-complete; implementation begins
Sprint 1 (2026-05-02).

- Android phone (min SDK 29 / target SDK 35)
- Wear OS (watchface complication, notification reply, rich app)
- Android Auto (public Messaging template; internal passenger build)
- iOS app skeleton (content phase follows Android production)

## Quick links

- 📘 [Docs index](docs/README.md)
- 🧭 [Architecture](docs/architecture.md)
- 🧩 [Decisions (ADRs)](docs/decisions/README.md)
- 🛡 [Security model](docs/security-model.md) · [Threat model](docs/threat-model.md)
- 🗺 [Sprint plan](docs/sprint-plan.md)
- 🤝 [AGENT.md](AGENT.md) — operating rules for contributors (human and AI)
- 🔐 [SECURITY.md](SECURITY.md)

## Build (Sprint 0+)

Requires JDK 17 or 21 (AGP 8.5.2 ceiling; JDK 25+ is incompatible as of this writing)
and the Android SDK. iOS build additionally requires Xcode on a Mac.

```bash
./gradlew :composeApp:assembleDebug            # Android phone debug
./gradlew :composeApp:assembleDevDebug         # Internal dev-flavor debug
./gradlew :wear:assembleDebug                  # Wear OS debug
./gradlew :auto:assemblePublicMessagingDebug   # Android Auto — Messaging template
./gradlew :auto:assembleDevPassengerDebug      # Android Auto — internal passenger UI
./gradlew test                                 # Shared + common tests
./gradlew detekt ktlintCheck lintDebug         # Linters
```

Gradle wrapper is committed — no bootstrap step needed on clone.

## Project layout

```
.
├── AGENT.md                    # Operating rules (must-read for contributors)
├── LICENSE                     # Polyform Noncommercial 1.0.0 (matches datawatch)
├── SECURITY.md
├── README.md
├── CHANGELOG.md
├── CODEOWNERS
├── .github/                    # CI workflows, issue/PR templates
├── gradle/libs.versions.toml   # Version catalog
├── composeApp/                 # Android phone app (com.dmzs.datawatchclient)
├── wear/                       # Wear OS module
├── auto/                       # Android Auto (public + dev variants)
├── shared/                     # KMP shared core (transport, MCP, storage, voice)
├── iosApp/                     # iOS skeleton (content post-v1)
└── docs/                       # Design package, ADRs, plans
```

## Upstream dependencies

This app targets the datawatch server API. Three endpoints are proposed upstream:

- [dmz006/datawatch#1](https://github.com/dmz006/datawatch/issues/1) — `/api/devices/register`
- [dmz006/datawatch#2](https://github.com/dmz006/datawatch/issues/2) — `/api/voice/transcribe`
- [dmz006/datawatch#3](https://github.com/dmz006/datawatch/issues/3) — `/api/federation/sessions`

Each has a documented client-side workaround (see [api-parity.md](docs/api-parity.md)), so
mobile MVP does not block on upstream.

## License

[Polyform Noncommercial 1.0.0](LICENSE). Free for personal, educational, open-source, and
non-commercial use. Not for commercial redistribution. Matches the parent project.

## Contributing

Solo dev + Claude for now. Read [AGENT.md](AGENT.md) before opening an issue or PR —
the decision-making + security rules bind every change.

## Contact

- Issues: https://github.com/dmz006/datawatch-app/issues
- Security: see [SECURITY.md](SECURITY.md)
- Brand: https://dmzs.com
