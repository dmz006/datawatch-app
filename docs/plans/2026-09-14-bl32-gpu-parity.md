# BL32 — GPU display info parity

**Status:** ✅ SHIPPED v1.6.0 (2026-09-14)  
**Tracks:** [datawatch-app#173](https://github.com/dmz006/datawatch-app/issues/173)

---

## Surfaces and changes

### 1. Observer card (`PeerResourcesCard.kt`) — already complete, multi-GPU fix

The card already showed all 4 GPU metrics (util%, temp °C, power W, VRAM used/total GB)
from `ComputeNodeDetailDto`. Gap: no per-GPU index label on multi-GPU nodes.

**Fix:** `PeerResourceRow` now prefixes chip labels with "GPU N " when
`detail.gpu.size > 1`. Single-GPU nodes are unchanged.

### 2. Session stats (`SessionStatsPanel.kt` + `SessionStatsViewModel.kt`) — major fix

The `ComputeNodeCard` previously used `gpuPct` and `gpuMemBytes` from the stats
envelope (`/api/observer/envelopes?session_id=…`). These come from the local process
monitor, not the bound remote compute node.

**Fix:**
- `SessionStatsViewModel` now accepts `updateComputeNodeRef(ref: String?)` called via
  `LaunchedEffect(session?.computeNodeRef)` in `SessionStatsPanel`.
- Each poll cycle also calls `getComputeNodeDetail(computeNodeRef)` and stores the
  result in `UiState.computeNodeDetail`.
- `ComputeNodeCard` renders per-GPU rows (util, temp, power, VRAM) from the detail
  when available; falls back to the envelope fields for sessions without a bound node.
- Same multi-GPU index prefix as surface 1.

### 3. Locale key consistency

All GPU metrics now use the `obs_cn_gpu_*` string keys across both surfaces.
The old hardcoded "GPU Mem" label in the session stats fallback is replaced with
`obs_cn_gpu_vram`.

## No server changes

`GET /api/compute/nodes/{name}/detail` already existed (`ComputeNodeDetailDto`).
`getComputeNodeDetail()` was already implemented in `RestTransport`.

## Tests added

`RestTransportTest`:
- `getComputeNodeDetailFetchesGpuStats` — single GPU, all 5 numeric fields
- `getComputeNodeDetailMultiGpuReturnsAllEntries` — 2 GPUs, count verified
