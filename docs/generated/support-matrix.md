# Verified Support Matrix

Generated from [`support-matrix.json`](../../support-matrix.json). Do not edit this table manually.

A target is public **Supported** only when its manifest status is `supported`, after all release gates pass:
`artifact-build`, `protocol-golden-fixtures`, `server-startup-smoke`, `core-scenario-smoke`.

## Gateway Artifacts

| Artifact | Platforms | Java | Intended Range | Status |
|----------|-----------|------|----------------|--------|
| ZeusGateway | Spigot/Paper/Folia | 8 | 1.8.8 through current exact verification targets | `build-verifiable` |

## Gateway Exact-Version Verification Targets

| Target | Artifact | Platform | Minecraft | Required Simulation Profile | Status |
|--------|----------|----------|-----------|-----------------------------|--------|
| spigot-1.8.8 | ZeusGateway | Spigot | 1.8.8 | 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2B, 0x2D, 0x30 | `build-verifiable` |
| spigot-1.13.2 | ZeusGateway | Spigot | 1.13.2 | 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2B, 0x2D, 0x30 | `build-verifiable` |
| spigot-1.14.4 | ZeusGateway | Spigot | 1.14.4 | 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2B, 0x2D, 0x2E, 0x30 | `build-verifiable` |
| paper-1.21.7 | ZeusGateway | Paper | 1.21.7 | 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2B, 0x2C, 0x2E, 0x30 | `build-verifiable` |
| paper-1.21.11 | ZeusGateway | Paper | 1.21.11 | 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2B, 0x2C, 0x2D, 0x2E, 0x30 | `build-verifiable` |
| spigot-26.2 | ZeusGateway | Spigot | 26.2 | 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2B, 0x2C, 0x2D, 0x2E, 0x30 | `build-verifiable` |
| paper-26.2 | ZeusGateway | Paper | 26.2 | 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2B, 0x2C, 0x2D, 0x2E, 0x30 | `build-verifiable` |

## Fabric Exact-Version Artifacts

| Minecraft | Artifact | Evidence Capabilities | Status |
|-----------|----------|-----------------------|--------|
| 1.21 | - | - | `adapter-required` |
| 1.21.1 | - | - | `adapter-required` |
| 1.21.2 | ZeusFabric-1.21.2 | trusted-input | `build-verifiable` |
| 1.21.3 | ZeusFabric-1.21.3 | trusted-input | `build-verifiable` |
| 1.21.4 | ZeusFabric-1.21.4 | trusted-input | `build-verifiable` |
| 1.21.5 | ZeusFabric-1.21.5 | trusted-input | `build-verifiable` |
| 1.21.6 | ZeusFabric-1.21.6 | trusted-input | `build-verifiable` |
| 1.21.7 | ZeusFabric-1.21.7 | trusted-input | `build-verifiable` |
| 1.21.8 | ZeusFabric-1.21.8 | trusted-input | `build-verifiable` |
| 1.21.9 | ZeusFabric-1.21.9 | trusted-input | `build-verifiable` |
| 1.21.10 | ZeusFabric-1.21.10 | trusted-input | `build-verifiable` |
| 1.21.11 | ZeusFabric-1.21.11 | trusted-input | `build-verifiable` |

## Current Publication State

No target is currently marked `supported`. `build-verifiable` identifies source/build wiring only; it is not a server compatibility claim.

The shared wire contract is `zeus-udp-v1-packet-ids-0x01-through-0x30`. Golden fixtures protect attack, velocity, inventory-transaction and external-force payloads.
