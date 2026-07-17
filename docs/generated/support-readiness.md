# Support Readiness

Generated from [`support-matrix.json`](../../support-matrix.json) and `verification/evidence/`.
A target can be marked `supported` only when every gate below is `pass`.

| Target | Status | Artifact Build | Protocol Fixtures | Startup Smoke | Core Scenario | Next Missing Gate |
|--------|--------|----------------|-------------------|---------------|---------------|-------------------|
| spigot-1.8.8 (ZeusGateway) | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| spigot-1.13.2 (ZeusGateway) | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| spigot-1.14.4 (ZeusGateway) | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| paper-1.21.7 (ZeusGateway) | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| paper-1.21.11 (ZeusGateway) | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| spigot-26.2 (ZeusGateway) | `build-verifiable` | invalid | invalid | missing | missing | artifact-build |
| paper-26.2 (ZeusGateway) | `build-verifiable` | invalid | invalid | invalid | missing | artifact-build |
| ZeusFabric-1.21.2 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.3 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.4 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.5 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.6 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.7 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.8 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.9 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.10 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |
| ZeusFabric-1.21.11 | `build-verifiable` | invalid | invalid | invalid | invalid | artifact-build |

## Evidence Paths

### spigot-1.8.8 (ZeusGateway)
- `artifact-build`: verification/evidence/artifact-build/gateway-spigot-1.8.8.json (spigot-1.8.8 evidence.artifact-build artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/gateway-spigot-1.8.8.json (spigot-1.8.8 evidence.protocol-golden-fixtures wireContract mismatch; spigot-1.8.8 evidence.protocol-golden-fixtures must list all required fixture tokens; spigot-1.8.8 evidence.protocol-golden-fixtures must list core packet IDs; spigot-1.8.8 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/gateway-spigot-1.8.8.json (spigot-1.8.8 evidence.server-startup-smoke artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/gateway-spigot-1.8.8.json (spigot-1.8.8 evidence.core-scenario-smoke artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar; spigot-1.8.8 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2d, 0x30; spigot-1.8.8 evidence.core-scenario-smoke capability profile mismatch)

### spigot-1.13.2 (ZeusGateway)
- `artifact-build`: verification/evidence/artifact-build/gateway-spigot-1.13.2.json (spigot-1.13.2 evidence.artifact-build artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/gateway-spigot-1.13.2.json (spigot-1.13.2 evidence.protocol-golden-fixtures wireContract mismatch; spigot-1.13.2 evidence.protocol-golden-fixtures must list all required fixture tokens; spigot-1.13.2 evidence.protocol-golden-fixtures must list core packet IDs; spigot-1.13.2 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/gateway-spigot-1.13.2.json (spigot-1.13.2 evidence.server-startup-smoke artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/gateway-spigot-1.13.2.json (spigot-1.13.2 evidence.core-scenario-smoke must have result=passed; spigot-1.13.2 evidence.core-scenario-smoke artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar; spigot-1.13.2 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2d, 0x30; spigot-1.13.2 evidence.core-scenario-smoke capability profile mismatch; spigot-1.13.2 evidence.core-scenario-smoke must have no missingPacketIds)

### spigot-1.14.4 (ZeusGateway)
- `artifact-build`: verification/evidence/artifact-build/gateway-spigot-1.14.4.json (spigot-1.14.4 evidence.artifact-build artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/gateway-spigot-1.14.4.json (spigot-1.14.4 evidence.protocol-golden-fixtures wireContract mismatch; spigot-1.14.4 evidence.protocol-golden-fixtures must list all required fixture tokens; spigot-1.14.4 evidence.protocol-golden-fixtures must list core packet IDs; spigot-1.14.4 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/gateway-spigot-1.14.4.json (spigot-1.14.4 evidence.server-startup-smoke artifact is missing: ZeusGateway/target/ZeusGateway-modern-1.0-SNAPSHOT.jar; spigot-1.14.4 evidence.server-startup-smoke must prove external PacketEvents)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/gateway-spigot-1.14.4.json (spigot-1.14.4 evidence.core-scenario-smoke artifact is missing: ZeusGateway/target/ZeusGateway-modern-1.0-SNAPSHOT.jar; spigot-1.14.4 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2d, 0x2e, 0x30; spigot-1.14.4 evidence.core-scenario-smoke capability profile mismatch; spigot-1.14.4 evidence.core-scenario-smoke must prove external PacketEvents)

### paper-1.21.7 (ZeusGateway)
- `artifact-build`: verification/evidence/artifact-build/gateway-paper-1.21.7.json (paper-1.21.7 evidence.artifact-build artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/gateway-paper-1.21.7.json (paper-1.21.7 evidence.protocol-golden-fixtures wireContract mismatch; paper-1.21.7 evidence.protocol-golden-fixtures must list all required fixture tokens; paper-1.21.7 evidence.protocol-golden-fixtures must list core packet IDs; paper-1.21.7 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/gateway-paper-1.21.7.json (paper-1.21.7 evidence.server-startup-smoke artifact is missing: ZeusGateway/target/ZeusGateway-modern-1.0-SNAPSHOT.jar; paper-1.21.7 evidence.server-startup-smoke must prove external PacketEvents)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/gateway-paper-1.21.7.json (paper-1.21.7 evidence.core-scenario-smoke artifact is missing: ZeusGateway/target/ZeusGateway-modern-1.0-SNAPSHOT.jar; paper-1.21.7 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x2e, 0x30; paper-1.21.7 evidence.core-scenario-smoke capability profile mismatch; paper-1.21.7 evidence.core-scenario-smoke must prove external PacketEvents)

### paper-1.21.11 (ZeusGateway)
- `artifact-build`: verification/evidence/artifact-build/gateway-paper-1.21.11.json (paper-1.21.11 evidence.artifact-build artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/gateway-paper-1.21.11.json (paper-1.21.11 evidence.protocol-golden-fixtures wireContract mismatch; paper-1.21.11 evidence.protocol-golden-fixtures must list all required fixture tokens; paper-1.21.11 evidence.protocol-golden-fixtures must list core packet IDs; paper-1.21.11 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/gateway-paper-1.21.11.json (paper-1.21.11 evidence.server-startup-smoke artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/gateway-paper-1.21.11.json (paper-1.21.11 evidence.core-scenario-smoke must have result=passed; paper-1.21.11 evidence.core-scenario-smoke artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar; paper-1.21.11 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x2d, 0x2e, 0x30; paper-1.21.11 evidence.core-scenario-smoke capability profile mismatch; paper-1.21.11 evidence.core-scenario-smoke must have no missingPacketIds)

### spigot-26.2 (ZeusGateway)
- `artifact-build`: verification/evidence/artifact-build/gateway-spigot-26.2.json (spigot-26.2 evidence.artifact-build artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/gateway-spigot-26.2.json (spigot-26.2 evidence.protocol-golden-fixtures wireContract mismatch; spigot-26.2 evidence.protocol-golden-fixtures must list all required fixture tokens; spigot-26.2 evidence.protocol-golden-fixtures must list core packet IDs; spigot-26.2 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/gateway-spigot-26.2.json (evidence file is missing)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/gateway-spigot-26.2.json (evidence file is missing)

### paper-26.2 (ZeusGateway)
- `artifact-build`: verification/evidence/artifact-build/gateway-paper-26.2.json (paper-26.2 evidence.artifact-build artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/gateway-paper-26.2.json (paper-26.2 evidence.protocol-golden-fixtures wireContract mismatch; paper-26.2 evidence.protocol-golden-fixtures must list all required fixture tokens; paper-26.2 evidence.protocol-golden-fixtures must list core packet IDs; paper-26.2 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/gateway-paper-26.2.json (paper-26.2 evidence.server-startup-smoke artifact is missing: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/gateway-paper-26.2.json (evidence file is missing)

### ZeusFabric-1.21.2
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.2.json (1.21.2 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.2-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.2.json (1.21.2 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.2 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.2 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.2 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.2.json (1.21.2 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.2-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.2.json (1.21.2 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.2-1.0-SNAPSHOT.jar; 1.21.2 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.2 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.3
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.3.json (1.21.3 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.3-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.3.json (1.21.3 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.3 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.3 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.3 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.3.json (1.21.3 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.3-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.3.json (1.21.3 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.3-1.0-SNAPSHOT.jar; 1.21.3 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.3 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.4
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.4.json (1.21.4 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.4-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.4.json (1.21.4 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.4 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.4 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.4 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.4.json (1.21.4 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.4-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.4.json (1.21.4 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.4-1.0-SNAPSHOT.jar; 1.21.4 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.4 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.5
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.5.json (1.21.5 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.5-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.5.json (1.21.5 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.5 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.5 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.5 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.5.json (1.21.5 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.5-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.5.json (1.21.5 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.5-1.0-SNAPSHOT.jar; 1.21.5 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.5 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.6
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.6.json (1.21.6 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.6-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.6.json (1.21.6 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.6 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.6 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.6 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.6.json (1.21.6 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.6-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.6.json (1.21.6 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.6-1.0-SNAPSHOT.jar; 1.21.6 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.6 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.7
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.7.json (1.21.7 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.7-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.7.json (1.21.7 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.7 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.7 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.7 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.7.json (1.21.7 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.7-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.7.json (1.21.7 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.7-1.0-SNAPSHOT.jar; 1.21.7 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.7 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.8
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.8.json (1.21.8 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.8-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.8.json (1.21.8 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.8 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.8 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.8 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.8.json (1.21.8 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.8-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.8.json (1.21.8 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.8-1.0-SNAPSHOT.jar; 1.21.8 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.8 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.9
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.9.json (1.21.9 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.9-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.9.json (1.21.9 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.9 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.9 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.9 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.9.json (1.21.9 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.9-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.9.json (1.21.9 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.9-1.0-SNAPSHOT.jar; 1.21.9 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.9 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.10
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.10.json (1.21.10 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.10-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.10.json (1.21.10 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.10 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.10 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.10 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.10.json (1.21.10 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.10-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.10.json (1.21.10 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.10-1.0-SNAPSHOT.jar; 1.21.10 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.10 evidence.core-scenario-smoke capability profile mismatch)

### ZeusFabric-1.21.11
- `artifact-build`: verification/evidence/artifact-build/fabric-1.21.11.json (1.21.11 evidence.artifact-build artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.11-1.0-SNAPSHOT.jar)
- `protocol-golden-fixtures`: verification/evidence/protocol-golden-fixtures/fabric-1.21.11.json (1.21.11 evidence.protocol-golden-fixtures wireContract mismatch; 1.21.11 evidence.protocol-golden-fixtures must list all required fixture tokens; 1.21.11 evidence.protocol-golden-fixtures must list core packet IDs; 1.21.11 evidence.protocol-golden-fixtures protocolArtifact is missing: ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar)
- `server-startup-smoke`: verification/evidence/startup-smoke/fabric-1.21.11.json (1.21.11 evidence.server-startup-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.11-1.0-SNAPSHOT.jar)
- `core-scenario-smoke`: verification/evidence/core-scenario-smoke/fabric-1.21.11.json (1.21.11 evidence.core-scenario-smoke artifactSha256 is stale for ZeusFabric/build/libs/ZeusFabric-1.21.11-1.0-SNAPSHOT.jar; 1.21.11 evidence.core-scenario-smoke must require target compatibility-core packets 0x03, 0x09, 0x22, 0x25, 0x26, 0x27, 0x2b, 0x2c, 0x30; 1.21.11 evidence.core-scenario-smoke capability profile mismatch)
