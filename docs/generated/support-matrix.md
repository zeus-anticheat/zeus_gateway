# Verified Support Matrix

Generated from [`support-matrix.json`](../../support-matrix.json). Do not edit this table manually.

A target is public **Supported** only when its manifest status is `supported`, after all release gates pass:
`artifact-build`, `protocol-golden-fixtures`, `server-startup-smoke`, `core-scenario-smoke`.

## Gateway Artifacts

| Artifact | Platforms | Java | Intended Range | Status |
|----------|-----------|------|----------------|--------|
| ZeusGateway-legacy | Paper/Spigot | 8 | 1.8-1.13.x | `build-verifiable` |
| ZeusGateway-modern | Paper/Spigot/Folia | 8+ | No released supported targets yet | `build-verifiable` |

## Gateway Exact-Version Verification Targets

| Target | Artifact | Platform | Minecraft | Status |
|--------|----------|----------|-----------|--------|
| spigot-1.8.8 | ZeusGateway-legacy | Spigot | 1.8.8 | `supported` |
| spigot-1.13.2 | ZeusGateway-legacy | Spigot | 1.13.2 | `supported` |
| spigot-1.14.4 | ZeusGateway-modern | Spigot | 1.14.4 | `supported` |
| paper-1.21.7 | ZeusGateway-modern | Paper | 1.21.7 | `supported` |

## Fabric Exact-Version Artifacts

| Minecraft | Artifact | Status |
|-----------|----------|--------|
| 1.21 | - | `adapter-required` |
| 1.21.1 | - | `adapter-required` |
| 1.21.2 | ZeusFabric-1.21.2 | `supported` |
| 1.21.3 | ZeusFabric-1.21.3 | `supported` |
| 1.21.4 | ZeusFabric-1.21.4 | `supported` |
| 1.21.5 | ZeusFabric-1.21.5 | `supported` |
| 1.21.6 | ZeusFabric-1.21.6 | `supported` |
| 1.21.7 | ZeusFabric-1.21.7 | `supported` |
| 1.21.8 | ZeusFabric-1.21.8 | `supported` |
| 1.21.9 | ZeusFabric-1.21.9 | `supported` |
| 1.21.10 | ZeusFabric-1.21.10 | `supported` |
| 1.21.11 | ZeusFabric-1.21.11 | `supported` |

## Current Publication State

Targets marked `supported` in the tables above have passed all publication gates (artifact-build, protocol-golden-fixtures, server-startup-smoke, core-scenario-smoke).

The shared wire contract is `zeus-udp-v1-packet-ids-0x01-through-0x2F`. Golden fixtures protect attack, velocity, surrounding-block, inventory-transaction, external-force, update-attributes, and physics-capture-sample payloads.
