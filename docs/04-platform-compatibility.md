# Platform Compatibility And Release Gate

Compatibility claims are controlled by [`../support-matrix.json`](../support-matrix.json).
The rendered public table is [`generated/support-matrix.md`](generated/support-matrix.md).
The generated gate status report is [`generated/support-readiness.md`](generated/support-readiness.md).
A target may be changed to `supported` only after its artifact builds, protocol
golden tests pass, a server startup smoke test passes, and core capture scenarios
pass on that exact target.

`scripts/verify_support_matrix.py` enforces the lightweight gate used by build
scripts: generated docs must match the manifest, build-verifiable artifacts must
have exact-version metadata, and no public docs may reintroduce broad support
claims without manifest evidence. `scripts/verify_release_gate.sh` adds the
heavy protocol/build checks that should run before publishing artifacts, then
writes artifact-build and protocol-fixture evidence with exact SHA-256 digests.
Those non-smoke gates are required to pass for every buildable target.
When
`ZEUS_GATEWAY_SMOKE_DIR` or `ZEUS_FABRIC_SMOKE_DIR` is set, the release gate
also runs `scripts/run_startup_smoke.py` against those pre-provisioned server
directories and writes startup evidence JSON. `scripts/run_core_scenario_smoke.py`
extends that gate with a local UDP listener and required packet ID assertions,
so scenario evidence proves the artifact emitted the expected Zeus packets. The
matrix runner `scripts/run_smoke_matrix.py` can execute those two smoke gates for
every configured build-verifiable target and can fail when any buildable target
is missing from the matrix.

The
support verifier requires the `compatibility-core` packet set (`0x09`, `0x22`,
`0x26`, `0x27`) for `core-scenario-smoke`; a join-only scenario cannot
promote a target to supported. Dry-run evidence is rejected.

For `ZeusGateway-legacy`, the verifier also enforces a Java 8 and legacy API
boundary: no Paper/Folia/ProtocolLib direct references, no `api-version`, no
modern Bukkit APIs such as `BoundingBox` or `BlockData`, and no classfile above
Java 8 in the shaded artifact.

## Artifact Model

| Artifact | Runtime | Purpose | Current State |
|----------|---------|---------|---------------|
| `ZeusGateway` | Java 8 | Unified Paper/Spigot/Folia adapter with legacy runtime module | `build-verifiable`; target smoke tests outstanding |
| `ZeusFabric-<mc>` | Java 21 | One Yarn/mixin adapter per exact Minecraft release | `1.21.2`–`1.21.11` are build-verifiable; smoke tests outstanding |

The shared `ZeusProtocolJava` codec is Java 8-compatible so a future legacy
adapter can emit the same bytes. The modern Gateway module must not be deployed
as a replacement for the missing legacy artifact.

## Frozen Wire Contract

This compatibility effort does not alter packet IDs, framing, field order or
semantics. In particular:

| ID | Packet | Preserved Meaning |
|----|--------|-------------------|
| `0x09` | `PacketPlayerAttackEntity` | Captured attack target state |
| `0x22` | `PacketPlayerVelocity` | Server velocity state |
| `0x26` | `PacketPlayerInventoryTransaction` | Container state/cursor/changed slots |
| `0x27` | `PacketPlayerExternalForce` | Classified force and flags |

`WireContractGoldenTest` pins representative encoded payloads for these
packets. Changes to those fixtures require a coordinated Java/Rust protocol
migration, not a compatibility patch.

## Gateway Capture Capabilities

`ProtocolLib` is optional. Listener registration is capability-based:
successful raw listeners suppress only their corresponding Bukkit fallback.
When an adapter cannot register a raw capability, the plugin logs it and leaves
available event fallbacks enabled.

| Capture | With matching ProtocolLib capability | Without capability |
|---------|--------------------------------------|--------------------|
| Attack | Raw `USE_ENTITY` captures timestamp/entity ID, then resolves entity state on the scheduler/region task | Bukkit/Paper damage or pre-attack event |
| Swing/place/dig/block face | Raw listener | Bukkit event fallback |
| Position/vehicle input | Raw listener | Degraded; no equivalent continuous Bukkit stream |
| Inventory snapshot/external force | Event/state snapshot collection | Same event/state collection |

Raw attack processing never resolves Bukkit entity/world state on the packet
thread: it stores the receive timestamp and target ID, then constructs the
existing packet on the platform scheduler.

## Fabric Version Boundary

Fabric source depends on mapped Minecraft internals and mixin method names.
`ZeusFabric/build.gradle` reads `support-matrix.json`; a requested `-PmcTarget`
fails unless an exact adapter is listed as buildable in the manifest. The mod
metadata uses an exact Minecraft dependency for that target rather than an
open-ended version declaration.

The movement mixin reads player/world snapshots on the server packet handling
thread. It does not dispatch block reads to an asynchronous executor.

## Preserved Representation Differences

Compatibility does not silently normalise platform-originated values:

| Data | Gateway Representation | Fabric Representation |
|------|------------------------|-----------------------|
| Block state | Bukkit block-data string/fallback material key | mapped `BlockState.toString()` output |
| Effect identifier | Bukkit effect-key mapping | registry-derived mapped value |

Downstream consumers must continue to handle these existing representations.
Any future normalisation is a separately versioned protocol/platform change.
