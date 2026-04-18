# Configuration reference

Per [AGENT.md](../AGENT.md) Configuration Accessibility Rule, every user-settable value
appears here with:

1. The in-app Settings path (e.g. `Settings → Servers → Add server`).
2. The default.
3. The types accepted.
4. A round-trip description (export → import).

Pre-MVP scaffold — this document is the framework. Settings populate from Sprint 1
onward as screens land.

## Sections

- Servers + reachability profiles
- Channels (messaging backends on the server)
- Voice
- Notifications
- Appearance
- Backup
- Diagnostics

## Format

```markdown
### <setting key>

- **UI path:** Settings → <section> → <card>
- **Type:** String / Int / Boolean / Enum / JSON
- **Default:** <value>
- **Persisted in:** EncryptedSharedPreferences / SQLCipher DB / both
- **Export format:** <JSON key>
- **Server echo:** yes / no — sync behavior
- **Notes:** constraints, side-effects, migration history
```

No settings enumerated yet — populate as they are added. Every PR that touches
`shared/.../config/Settings.kt` must also touch this file.
