# Unified cluster monitoring — spec + mobile integration plan

*Filed 2026-04-22 against user request (2026-04-22) to consolidate B5 / B10 / B11 into
a single, composable monitoring subsystem.*

## Problem statement

Today `/api/stats` returns a flat `StatsDto` with `cpu_pct / mem_pct / disk_pct /
gpu_pct / sessions_{total,running,waiting} / uptime_seconds`. That's enough for a
one-host dashboard but hits four walls as the user's fleet grows:

1. **Per-process detail.** eBPF per-process network + per-session CPU / mem / net
   wheels that PWA aspires to (B5 / B11) need kernel-level probes the current
   Go process doesn't carry. Running those probes in the main daemon requires
   root or CAP_BPF, which we don't want on every host.
2. **GPU telemetry.** GPU usage on an Ollama / inference host is of direct
   interest to mobile admins; the current `gpu_pct` is a single scalar. PWA
   has floated a richer GPU monitoring feature; mobile should consume whatever
   wire format ships there rather than reinvent.
3. **Monitoring agents that don't run a datawatch session manager.** Users want
   to aim at an Ollama box, a Kubernetes master, or a bare CPU host without
   installing the full datawatch daemon (no tmux, no sessions).
4. **Cluster-level roll-up.** When the user runs datawatch on their workstation
   but Ollama on a separate box, `/api/stats` only covers the workstation. PWA
   (and mobile) needs a way to surface the Ollama box's stats alongside.

## Proposal — three deployment shapes for one stats agent

One new binary — `datawatch-stats` — packaged three ways. Wire shape and metric
surface are identical across all three; only the transport and privilege model
differ.

### Shape A — in-process plugin

Runs inside the existing daemon as a plugin under the
`plugins.*` framework landed upstream in `dmz006/datawatch#19`. Registers a
collector, server publishes the metrics via WS `MsgStats` and REST
`/api/stats`.

Privilege: same as daemon — unprivileged, fine for CPU / mem / disk /
session counts + per-process reads from `/proc`.

Default; runs on every datawatch host automatically.

### Shape B — standalone daemon

Systemd unit (`datawatch-stats.service`) on a host that isn't running a session
manager — e.g. a dedicated Ollama box. Exposes the same HTTP interface on its
own port (`:9001` default); the parent datawatch daemon aggregates via
federation protocol (same shape used for the existing server peers).

Privilege: still unprivileged. No eBPF.

### Shape C — containerized cluster monitor

OCI container, runs with `CAP_BPF` + `CAP_SYS_RESOURCE` + `--privileged` (or
host-PID). Adds eBPF per-process network taps, per-cgroup CPU/mem, GPU via
`nvidia-smi` or DCGM exporter, and optional k8s metrics-server scrape. Same
wire shape, extended `eBPF` + `cluster` payload sub-objects.

Privilege: root / CAP_BPF — intentionally isolated in its own container so the
main datawatch daemon can remain unprivileged.

Deploy: `datawatch-stats-cluster` image, published to ghcr.io/dmz006 alongside
the main daemon image. Compose / Helm snippets shipped with the container.

## Wire contract (proposed)

```
GET /api/stats → StatsResponse v2
{
  "host": { "name": "ring", "uptime_seconds": 12345, "os": "linux",
            "kernel": "6.8.0", "arch": "x86_64" },

  "cpu":  { "pct": 14.2, "cores": 16, "load1": 0.42, "load5": 0.38,
            "per_core_pct": [ 12.1, 18.3, ... ] },

  "mem":  { "pct": 41.0, "used_bytes": 6800000000, "total_bytes": 16500000000,
            "swap_used_bytes": 0, "swap_total_bytes": 8589934592 },

  "disk": [ { "mount": "/", "pct": 62.0, "used_bytes": ..., "total_bytes": ... } ],

  "gpu":  [ { "name": "NVIDIA RTX 4090", "util_pct": 78.5, "mem_used_bytes": ...,
              "mem_total_bytes": ..., "power_w": 310, "temp_c": 68 } ],

  "net":  { "rx_bytes_per_sec": 125000, "tx_bytes_per_sec": 78000,
            "per_process": [    // SHAPE C only (eBPF)
              { "pid": 3401, "comm": "ollama", "rx_bps": 120000, "tx_bps": 75000 }
            ] },

  "sessions": { "total": 5, "running": 2, "waiting": 1, "rate_limited": 0,
                "per_backend": { "claude-code": 3, "openwebui": 1 } },

  "backends": [ { "name": "ollama", "reachable": true, "last_ok_unix_ms": ...,
                  "latency_ms": 12 } ],

  "cluster": {      // SHAPE C only
    "nodes": [ { "name": "worker-1", "ready": true, "cpu_pct": 45, ... } ]
  }
}
```

Older clients parse the top-level scalars they already use (`cpu.pct`,
`mem.pct`, etc.) — no breaking change because today's `StatsDto` flat fields
become top-level aliases inside each sub-object.

## Live streaming (WS)

WS `MsgStats` broadcast already exists upstream (`internal/server/ws.go:42 —
BroadcastStats`). Extend the payload to match `StatsResponse v2`. Emit at 1 s
from the in-process plugin; remote daemons push on a 5 s cadence and the
aggregator re-broadcasts.

## Mobile integration plan

*Lands in datawatch-app v0.34.x — gated on the upstream spec landing.*

### Phase 1 — Consume v2 response

- **`StatsDto`** grows structured sub-objects (host, cpu, mem, disk[], gpu[],
  net, sessions, backends, cluster?). Kept backward-compatible via @SerialName
  defaults so v1 servers still parse.
- **StatsScreenContent** renders:
  - CPU / mem / disk / GPU tiles with threshold-colored progress rings (amber
    >70 %, red >90 %) — same as today, prettier values.
  - Per-core CPU strip (grouped by 4, horizontal scroll).
  - GPU rows — one per card, with power / temp.
  - Per-backend session count chips.
  - Backend-health dots (green / red) with last-ok timestamp.

### Phase 2 — Live WS stream

- New `StatsRepository` subscribes to `MsgStats` via WebSocketTransport.
- StatsViewModel collects the flow; replaces the 5 s `/api/stats` poll.
- Fallback to polling when WS isn't wired yet (server < v2 stats).
- Closes B10.

### Phase 3 — eBPF + cluster detail

- Per-process network card (read-only, post-1.0 per ADR-0019) appears only
  when `net.per_process` is non-null.
- `cluster.nodes` row list appears only when present. Tap → drills into that
  node's stats.
- Closes B11 (as much as we ever plan to on mobile).

### Phase 4 — Agent health badge

- "Stats agent: ✓ plugin on ring · ✓ standalone on ollama · ✗ cluster not
  deployed" on the Monitor tab header so admins see coverage at a glance.

## Mobile also benefits indirectly

- **B5** (stats density + eBPF) — Phase 1 + 3.
- **B10** (live stats streaming) — Phase 2.
- **B11** (per-session eBPF wheels) — Phase 3.

## Open questions

- GPU detection on AMD (ROCm) / Intel (level_zero) — part of the server spec,
  not mobile's problem.
- Aggregation when multiple datawatch hosts + multiple standalone agents all
  register. Current `/api/servers` federation concept probably suffices if we
  tag agents as `role: stats-only` so the UI can filter.
- Cluster agent + session manager on the same host: can the main daemon
  detect the container sibling and defer collection to it? Likely yes with a
  config flag.

## Next steps

1. File upstream issue on dmz006/datawatch referencing this doc.
2. Wait for the server spec to land in a parent release.
3. Bump mobile `StatsDto`, merge Phase 1.
4. WS stream (Phase 2) as v0.34.
5. eBPF surface (Phase 3) post-1.0 per ADR-0019.
