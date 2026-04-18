# Security Policy

## Supported versions

Pre-MVP (v0.x): no security support commitment. Report issues but expect best-effort
response.

MVP (v1.0.0) onward: the latest released version receives patches. Older versions do not.

## Reporting a vulnerability

Prefer **private disclosure** before filing a public issue.

- Email: `davidzendzian@gmail.com` — subject line `SECURITY: datawatch-app`.
- PGP / GPG: if you want encryption, email first and we will exchange keys.

Please include:

1. Affected version and build flavor (`com.dmzs.datawatchclient` or `.dev`).
2. A minimal reproduction — device model, OS version, what you did, what you saw.
3. Impact assessment — what could an attacker do.
4. Suggested fix if you have one (optional).

Expect an acknowledgement within 72 hours. We aim to ship a patch within 14 days for
high-severity issues; lower-severity issues roll into the next minor release.

## Scope

In scope:

- This app (Android phone, Wear OS, Android Auto modules, shared KMP core).
- Interactions with the datawatch server that expose the mobile client to risk.
- Keystore / SQLCipher / FCM / Google Drive Auto Backup handling in this app.

Out of scope:

- The datawatch server itself — report to
  [dmz006/datawatch](https://github.com/dmz006/datawatch) directly.
- The user's own infrastructure, device security, and third-party messenger apps.
- Bugs that require a rooted / jailbroken device or installation of malicious apps
  alongside ours — standard Android sandbox trust boundary applies.
- Accessibility-service-based attacks — mitigated via `FLAG_SECURE` on critical screens;
  further hardening accepted but not treated as a vulnerability.

## Design references

- [docs/security-model.md](docs/security-model.md) — positive statement of how we
  protect data.
- [docs/threat-model.md](docs/threat-model.md) — STRIDE analysis and residual risks.

## Hall of fame

No reports yet. First reporter to find a valid issue gets credited here unless they
request anonymity.
